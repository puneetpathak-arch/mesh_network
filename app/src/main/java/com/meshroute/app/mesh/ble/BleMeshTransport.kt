package com.meshroute.app.mesh.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
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
        val serviceData = record.getServiceData(ParcelUuid(BleConstants.SERVICE_UUID))
        val discoveredNodeId = if (serviceData != null && serviceData.isNotEmpty()) {
            serviceData.decodeToString()
        } else {
            result.device.address
        }

        if (discoveredNodeId == selfNodeId) {
            // Do not discover self
            return
        }

        val deviceAddress = result.device.address
        val deviceName = result.device.name ?: record.deviceName ?: "Peer-${discoveredNodeId.take(6)}"

        deviceAddressToNodeId[deviceAddress] = discoveredNodeId
        nodeIdToDeviceAddress[discoveredNodeId] = deviceAddress

        val peer = Peer(
            nodeId = discoveredNodeId,
            deviceName = deviceName,
            transportType = TransportType.BLE,
            rssi = result.rssi,
            lastSeenTimestamp = System.currentTimeMillis()
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

                scope.launch {
                    _inbound.emit(
                        InboundPacket(
                            data = value,
                            senderNodeId = senderId,
                            transportType = TransportType.BLE,
                            receivedTimestamp = System.currentTimeMillis()
                        )
                    )
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

        stopScanning()
        stopAdvertising()
        closeGattServer()

        peerMap.clear()
        deviceAddressToNodeId.clear()
        nodeIdToDeviceAddress.clear()
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

    private fun startAdvertising() {
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.w(TAG, "BLE Advertiser not supported on this hardware")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .addServiceData(ParcelUuid(BleConstants.SERVICE_UUID), selfNodeId.encodeToByteArray())
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
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
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        scanner?.startScan(listOf(filter), settings, scanCallback)
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
            Log.w(TAG, "Send requested but no target peers reachable")
            return@withContext false
        }

        var overallSuccess = true
        for (peer in targets) {
            val success = sendToPeerDevice(peer, data)
            if (!success) {
                overallSuccess = false
            }
        }
        return@withContext overallSuccess
    }

    private suspend fun sendToPeerDevice(peer: Peer, data: ByteArray): Boolean = suspendCancellableCoroutine { continuation ->
        val address = nodeIdToDeviceAddress[peer.nodeId] ?: peer.nodeId
        val device = runCatching { bluetoothAdapter?.getRemoteDevice(address) }.getOrNull()

        if (device == null) {
            Log.e(TAG, "Cannot resolve BluetoothDevice for peer: ${peer.nodeId} (address: $address)")
            continuation.resume(false) {}
            return@suspendCancellableCoroutine
        }

        var gattClient: BluetoothGatt? = null

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "GATT Client connected to ${peer.nodeId}, requesting MTU...")
                    gatt.requestMtu(BleConstants.MAX_MTU)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.close()
                    if (continuation.isActive) {
                        continuation.resume(false) {}
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(TAG, "GATT MTU set to $mtu, discovering services...")
                gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(BleConstants.SERVICE_UUID)
                    val writeChar = service?.getCharacteristic(BleConstants.CHAR_PACKET_WRITE_UUID)

                    if (writeChar != null) {
                        writeChar.value = data
                        writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        val initiated = gatt.writeCharacteristic(writeChar)
                        if (!initiated) {
                            Log.e(TAG, "Failed to initiate writeCharacteristic to ${peer.nodeId}")
                            gatt.disconnect()
                        }
                    } else {
                        Log.e(TAG, "Mesh service/characteristic not found on peer ${peer.nodeId}")
                        gatt.disconnect()
                    }
                } else {
                    gatt.disconnect()
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                val success = (status == BluetoothGatt.GATT_SUCCESS)
                Log.i(TAG, "GATT Write to ${peer.nodeId} completed with status: $status (success: $success)")
                gatt.disconnect()
                if (continuation.isActive) {
                    continuation.resume(success) {}
                }
            }
        }

        gattClient = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        continuation.invokeOnCancellation {
            gattClient?.disconnect()
            gattClient?.close()
        }
    }
}
