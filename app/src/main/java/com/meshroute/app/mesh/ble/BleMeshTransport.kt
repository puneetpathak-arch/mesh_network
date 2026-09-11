package com.meshroute.app.mesh.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.meshroute.app.mesh.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class BleMeshTransport(
    private val context: Context,
    val selfNodeId: String
) : MeshTransport {

    companion object {
        private const val TAG = "BleMeshTransport"
        private const val CHUNK_HEADER_MAGIC: Byte = 0xBE.toByte()
        /** Conservative fallback if MTU negotiation fails or is not yet complete. */
        private const val DEFAULT_MTU = 23
        /** ATT protocol overhead per write (1 opcode + 2 handle bytes). */
        private const val ATT_HEADER_BYTES = 3
    }

    override val transportType: TransportType = TransportType.BLE

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _neighbors = MutableStateFlow<Set<Peer>>(emptySet())
    override val neighbors: StateFlow<Set<Peer>> = _neighbors.asStateFlow()

    private val _health = MutableStateFlow(TransportHealth.HEALTHY)
    override val health: StateFlow<TransportHealth> = _health.asStateFlow()

    private val _inbound = MutableSharedFlow<InboundPacket>(extraBufferCapacity = 64)
    override val inbound: Flow<InboundPacket> = _inbound.asSharedFlow()

    val powerManager = BlePowerManager(context)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private val peerMap = ConcurrentHashMap<String, Peer>()
    private val deviceAddressToNodeId = ConcurrentHashMap<String, String>()
    private val nodeIdToDeviceAddress = ConcurrentHashMap<String, String>()

    private var isRunning = false

    // ─── Scan Callback ──────────────────────────────────────────

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed with errorCode: $errorCode")
            _health.value = TransportHealth.DEGRADED
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val hasUuid = record.serviceUuids?.contains(ParcelUuid(BleConstants.SERVICE_UUID)) == true
        val serviceData = record.getServiceData(ParcelUuid(BleConstants.SERVICE_UUID))

        if (!hasUuid && serviceData == null) {
            return
        }

        var discoveredNodeId = ""
        var telemetry: NodeTelemetryBeacon? = null

        if (serviceData != null && serviceData.isNotEmpty()) {
            if (serviceData.size >= 5) {
                // If serviceData has trailing 4 bytes for telemetry
                val telemetryBytes = serviceData.takeLast(4).toByteArray()
                telemetry = NodeTelemetryBeacon.fromByteArray(telemetryBytes)
                val idBytes = serviceData.dropLast(4).toByteArray()
                discoveredNodeId = idBytes.decodeToString()
            } else {
                discoveredNodeId = serviceData.decodeToString()
            }
        } else {
            discoveredNodeId = result.device.name ?: result.device.address
        }

        if (discoveredNodeId.isBlank() || discoveredNodeId == selfNodeId) {
            // Do not discover self or empty id
            return
        }

        val deviceAddress = result.device.address
        val deviceName = result.device.name ?: record.deviceName ?: "Peer-${discoveredNodeId.take(6)}"

        val previousNodeId = deviceAddressToNodeId[deviceAddress]
        if (previousNodeId != null && previousNodeId != discoveredNodeId) {
            peerMap.remove(previousNodeId)
            nodeIdToDeviceAddress.remove(previousNodeId)
        }

        deviceAddressToNodeId[deviceAddress] = discoveredNodeId
        nodeIdToDeviceAddress[discoveredNodeId] = deviceAddress

        val peer = Peer(
            nodeId = discoveredNodeId,
            deviceName = deviceName,
            transportType = TransportType.BLE,
            rssi = result.rssi,
            lastSeenTimestamp = System.currentTimeMillis(),
            batteryLevel = telemetry?.batteryPercent ?: 100,
            isCharging = telemetry?.isCharging ?: false,
            mobilityCode = telemetry?.mobilityState?.code ?: 0,
            gatewayLikelihood = (telemetry?.gatewayLikelihoodPercent ?: 50) / 100f,
            queueLoad = telemetry?.queueLoad ?: 0
        )

        peerMap[discoveredNodeId] = peer
        updateNeighbors()
    }

    // ─── Advertise Callback ─────────────────────────────────────

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "BLE Advertising started successfully for node: $selfNodeId")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE Advertising failed with errorCode: $errorCode")
            _health.value = TransportHealth.DEGRADED
        }
    }

    private val chunkAssemblyMap = ConcurrentHashMap<String, MutableMap<Int, ByteArray>>()

    // ─── GATT Server Callback ───────────────────────────────────

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(TAG, "GATT Server ConnectionStateChange device: ${device.address} status: $status newState: $newState")
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (characteristic.uuid == BleConstants.CHAR_PACKET_WRITE_UUID && value != null) {
                val senderId = deviceAddressToNodeId[device.address] ?: device.address
                Log.d(TAG, "Received ${value.size} bytes from $senderId via GATT Write")

                val fullPayload: ByteArray? = if (value.size >= 4 && value[0] == CHUNK_HEADER_MAGIC) {
                    val packetHash = value[1]
                    val chunkIdx = value[2].toInt() and 0xFF
                    val totalChunks = value[3].toInt() and 0xFF
                    val payload = value.copyOfRange(4, value.size)
                    val key = "${device.address}_$packetHash"

                    val map = chunkAssemblyMap.computeIfAbsent(key) { ConcurrentHashMap() }
                    map[chunkIdx] = payload

                    if (map.size == totalChunks) {
                        chunkAssemblyMap.remove(key)
                        val assembled = java.io.ByteArrayOutputStream()
                        for (i in 0 until totalChunks) {
                            map[i]?.let { assembled.write(it) }
                        }
                        assembled.toByteArray()
                    } else {
                        null // Still waiting for remaining chunks
                    }
                } else {
                    value // Raw / legacy packet format
                }

                if (fullPayload != null) {
                    scope.launch {
                        _inbound.emit(
                            InboundPacket(
                                data = fullPayload,
                                senderNodeId = senderId,
                                transportType = TransportType.BLE,
                                receivedTimestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }

                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                }
            } else {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                }
            }
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────────

    override suspend fun start() = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext
        isRunning = true

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth is disabled or unavailable")
            _health.value = TransportHealth.UNAVAILABLE
            return@withContext
        }

        powerManager.start()
        setupGattServer()
        startAdvertising()
        startScanning()
        startPeerEvictionWatchdog()

        _health.value = TransportHealth.HEALTHY
        Log.i(TAG, "BleMeshTransport started successfully")
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        if (!isRunning) return@withContext
        isRunning = false

        powerManager.stop()
        stopScanning()
        stopAdvertising()
        closeGattServer()

        peerMap.clear()
        deviceAddressToNodeId.clear()
        nodeIdToDeviceAddress.clear()
        chunkAssemblyMap.clear()
        _neighbors.value = emptySet()

        scope.coroutineContext.cancelChildren()
        Log.i(TAG, "BleMeshTransport stopped")
    }

    private fun setupGattServer() {
        if (bluetoothManager == null) return
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        val service = BluetoothGattService(
            BleConstants.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val writeChar = BluetoothGattCharacteristic(
            BleConstants.CHAR_PACKET_WRITE_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val infoChar = BluetoothGattCharacteristic(
            BleConstants.CHAR_NODE_INFO_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        infoChar.value = selfNodeId.encodeToByteArray()

        service.addCharacteristic(writeChar)
        service.addCharacteristic(infoChar)
        gattServer?.addService(service)
    }

    private fun closeGattServer() {
        gattServer?.close()
        gattServer = null
    }

    var telemetryProvider: (() -> NodeTelemetryBeacon)? = null

    private fun startAdvertising() {
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.w(TAG, "BLE Advertiser not supported on this hardware")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        // 1. Primary Advertise Data (under 31 bytes)
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .build()

        // 2. Scan Response Data (contains Node ID + 4-byte Telemetry in Service Data, fits easily in 31-byte scan response)
        val idBytes = selfNodeId.encodeToByteArray()
        val telemetryBytes = telemetryProvider?.invoke()?.toByteArray() ?: ByteArray(0)
        val combinedServiceData = idBytes + telemetryBytes

        val scanResponseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceData(ParcelUuid(BleConstants.SERVICE_UUID), combinedServiceData)
            .build()

        advertiser?.startAdvertising(settings, advertiseData, scanResponseData, advertiseCallback)
    }

    private fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
        advertiser = null
    }

    private fun startScanning() {
        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BLE Scanner not available")
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                    setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                }
            }
            .build()

        runCatching {
            scanner?.startScan(listOf(filter), settings, scanCallback)
        }.onFailure {
            Log.w(TAG, "Filtered scan failed, starting general scan: ${it.message}")
            runCatching { scanner?.startScan(scanCallback) }
        }
    }

    private fun stopScanning() {
        scanner?.stopScan(scanCallback)
        scanner = null
    }

    private fun startPeerEvictionWatchdog() {
        scope.launch {
            while (isActive && isRunning) {
                delay(5000L)
                val now = System.currentTimeMillis()
                val expired = peerMap.filter { now - it.value.lastSeenTimestamp > BleConstants.DEFAULT_PEER_LINGER_MS }
                if (expired.isNotEmpty()) {
                    expired.keys.forEach { peerMap.remove(it) }
                    updateNeighbors()
                }
            }
        }
    }

    private fun updateNeighbors() {
        _neighbors.value = peerMap.values.toSet()
    }

    // ─── Send Implementation ────────────────────────────────────

    override suspend fun send(data: ByteArray, to: Peer?): Boolean = withContext(Dispatchers.IO) {
        if (!isRunning || bluetoothAdapter == null) return@withContext false

        val targets = if (to != null) {
            listOf(to)
        } else {
            _neighbors.value.toList()
        }

        if (targets.isEmpty()) {
            Log.w(TAG, "Send requested but no target peers reachable in neighbors list")
            return@withContext false
        }

        var anySuccess = false
        for (peer in targets) {
            val success = sendToPeerDevice(peer, data)
            if (success) {
                anySuccess = true
            }
        }
        return@withContext anySuccess
    }

    /**
     * Fragments [data] into BLE-safe chunks sized to fit within the negotiated [negotiatedMtu].
     * Each chunk frame is: [magic(1)] [hash(1)] [index(1)] [total(1)] [payload(n)]
     * Max payload per chunk = negotiatedMtu - ATT_HEADER_BYTES - 4 (chunk header).
     */
    private fun fragmentData(data: ByteArray, negotiatedMtu: Int): List<ByteArray> {
        val maxPayloadSize = (negotiatedMtu - ATT_HEADER_BYTES - 4).coerceAtLeast(1)
        val packetHash = (data.contentHashCode() and 0xFF).toByte()
        val chunks = mutableListOf<ByteArray>()
        val totalChunks = ((data.size + maxPayloadSize - 1) / maxPayloadSize).coerceAtLeast(1)

        for (i in 0 until totalChunks) {
            val start = i * maxPayloadSize
            val end = (start + maxPayloadSize).coerceAtMost(data.size)
            val chunkPayload = data.copyOfRange(start, end)

            val frame = ByteArray(4 + chunkPayload.size)
            frame[0] = CHUNK_HEADER_MAGIC
            frame[1] = packetHash
            frame[2] = i.toByte()
            frame[3] = totalChunks.toByte()
            System.arraycopy(chunkPayload, 0, frame, 4, chunkPayload.size)
            chunks.add(frame)
        }
        return chunks
    }

    private suspend fun sendToPeerDevice(peer: Peer, data: ByteArray): Boolean = withTimeoutOrNull(10000L) {
        suspendCancellableCoroutine { continuation ->
            val address = nodeIdToDeviceAddress[peer.nodeId] ?: peer.nodeId
            val device = runCatching { bluetoothAdapter?.getRemoteDevice(address) }.getOrNull()

            if (device == null) {
                Log.e(TAG, "Cannot resolve BluetoothDevice for peer: ${peer.nodeId} (address: $address)")
                if (continuation.isActive) continuation.resume(false) {}
                return@suspendCancellableCoroutine
            }

            // Chunks are built after MTU negotiation in onMtuChanged / onServicesDiscovered.
            var chunks = emptyList<ByteArray>()
            var currentChunkIndex = 0
            var gattClient: BluetoothGatt? = null
            var writeCharRef: BluetoothGattCharacteristic? = null
            var serviceDiscoveryStarted = false
            // Track the actual negotiated MTU; fall back to BLE default (23) if negotiation fails.
            var negotiatedMtu = DEFAULT_MTU

            val gattCallback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d(TAG, "GATT Client connected to ${peer.nodeId}, requesting MTU...")
                        val mtuOk = gatt.requestMtu(BleConstants.MAX_MTU)
                        if (!mtuOk && !serviceDiscoveryStarted) {
                            // MTU request not supported; proceed with default MTU.
                            Log.w(TAG, "requestMtu() returned false for ${peer.nodeId}, using default MTU $negotiatedMtu")
                            chunks = fragmentData(data, negotiatedMtu)
                            serviceDiscoveryStarted = true
                            gatt.discoverServices()
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        gatt.close()
                        if (continuation.isActive) {
                            continuation.resume(false) {}
                        }
                    }
                }

                override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                    // Capture the actual negotiated MTU and fragment using it.
                    negotiatedMtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else DEFAULT_MTU
                    Log.d(TAG, "GATT MTU negotiated to $negotiatedMtu for ${peer.nodeId}, discovering services...")
                    chunks = fragmentData(data, negotiatedMtu)
                    if (!serviceDiscoveryStarted) {
                        serviceDiscoveryStarted = true
                        gatt.discoverServices()
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val service = gatt.getService(BleConstants.SERVICE_UUID)
                        val writeChar = service?.getCharacteristic(BleConstants.CHAR_PACKET_WRITE_UUID)
                        writeCharRef = writeChar

                        if (writeChar != null) {
                            // Ensure chunks are built even if onMtuChanged was never called.
                            if (chunks.isEmpty()) {
                                Log.w(TAG, "Chunks not yet built for ${peer.nodeId}; using current negotiatedMtu=$negotiatedMtu")
                                chunks = fragmentData(data, negotiatedMtu)
                            }
                            scope.launch {
                                delay(200L)
                                currentChunkIndex = 0
                                Log.d(TAG, "Sending ${chunks.size} chunk(s) to ${peer.nodeId} (MTU=$negotiatedMtu, chunk frames: ${chunks.map { it.size }} bytes)")
                                val writeSuccess = attemptWrite(gatt, writeChar, chunks[0], peer.nodeId)
                                if (!writeSuccess) {
                                    Log.e(TAG, "Failed to initiate writeCharacteristic for chunk 0 to ${peer.nodeId}")
                                    gatt.disconnect()
                                    if (continuation.isActive) continuation.resume(false) {}
                                }
                            }
                        } else {
                            Log.e(TAG, "Mesh service/characteristic not found on peer ${peer.nodeId}")
                            gatt.disconnect()
                            if (continuation.isActive) continuation.resume(false) {}
                        }
                    } else {
                        gatt.disconnect()
                        if (continuation.isActive) continuation.resume(false) {}
                    }
                }

                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        currentChunkIndex++
                        if (currentChunkIndex < chunks.size) {
                            val writeChar = writeCharRef
                            if (writeChar != null) {
                                scope.launch {
                                    delay(30L)
                                    val nextSuccess = attemptWrite(gatt, writeChar, chunks[currentChunkIndex], peer.nodeId)
                                    if (!nextSuccess) {
                                        Log.e(TAG, "Failed to initiate chunk $currentChunkIndex to ${peer.nodeId}")
                                        gatt.disconnect()
                                        if (continuation.isActive) continuation.resume(false) {}
                                    }
                                }
                            } else {
                                gatt.disconnect()
                                if (continuation.isActive) continuation.resume(false) {}
                            }
                        } else {
                            Log.i(TAG, "All ${chunks.size} chunk(s) to ${peer.nodeId} completed successfully")
                            gatt.disconnect()
                            if (continuation.isActive) {
                                continuation.resume(true) {}
                            }
                        }
                    } else {
                        Log.e(TAG, "GATT Write failed for chunk $currentChunkIndex with status: $status")
                        gatt.disconnect()
                        if (continuation.isActive) {
                            continuation.resume(false) {}
                        }
                    }
                }
            }

            gattClient = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            continuation.invokeOnCancellation {
                gattClient?.disconnect()
                gattClient?.close()
            }
        }
    } ?: false

    /**
     * Attempts to initiate a GATT write up to [maxRetries] times, waiting [retryDelayMs]
     * between each attempt. Safely catches any platform IllegalArgumentExceptions.
     */
    private suspend fun attemptWrite(
        gatt: BluetoothGatt,
        writeChar: BluetoothGattCharacteristic,
        data: ByteArray,
        peerId: String,
        maxRetries: Int = 3,
        retryDelayMs: Long = 200L
    ): Boolean {
        repeat(maxRetries) { attempt ->
            val queued = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val result = gatt.writeCharacteristic(
                        writeChar,
                        data,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                    result == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    writeChar.value = data
                    @Suppress("DEPRECATION")
                    writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(writeChar)
                }
            }.getOrElse { ex ->
                Log.e(TAG, "writeCharacteristic threw exception: ${ex.message}")
                false
            }

            if (queued) {
                Log.d(TAG, "writeCharacteristic queued successfully to $peerId (chunk size: ${data.size} bytes, attempt ${attempt + 1})")
                return true
            }
            Log.w(TAG, "writeCharacteristic not ready for $peerId, attempt ${attempt + 1}/$maxRetries — retrying in ${retryDelayMs}ms")
            delay(retryDelayMs)
        }
        return false
    }
}
