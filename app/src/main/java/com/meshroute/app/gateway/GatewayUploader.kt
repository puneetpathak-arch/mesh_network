package com.meshroute.app.gateway

import android.util.Log
import com.meshroute.app.data.queue.ForwardStore
import com.meshroute.app.mesh.transport.SosPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

sealed class GatewayUploadEvent {
    data class Success(
        val packetId: String,
        val statusCode: Int,
        val responseBody: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : GatewayUploadEvent()

    data class Failure(
        val packetId: String,
        val error: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : GatewayUploadEvent()
}

/**
 * Phase 8 Gateway Engine:
 * When internet connectivity is detected, drains un-uploaded emergency packets
 * from Room disk storage and uploads them via HTTP POST to the backend API.
 */
class GatewayUploader(
    val forwardStore: ForwardStore,
    val networkMonitor: NetworkMonitor,
    initialBackendUrl: String = DEFAULT_BACKEND_URL
) {
    companion object {
        private const val TAG = "GatewayUploader"
        const val DEFAULT_BACKEND_URL = "http://10.0.2.2:3000" // Android Emulator host or LAN IP
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isDraining = AtomicBoolean(false)

    var backendUrl: String = initialBackendUrl

    private val _uploadEvents = MutableSharedFlow<GatewayUploadEvent>(replay = 16, extraBufferCapacity = 64)
    val uploadEvents: Flow<GatewayUploadEvent> = _uploadEvents.asSharedFlow()

    private var monitorJob: Job? = null

    fun start() {
        if (monitorJob?.isActive == true) return

        networkMonitor.start()

        // Watchdog: whenever connectivity becomes available, drain pending packets
        monitorJob = scope.launch {
            networkMonitor.isInternetAvailable.collect { available ->
                if (available) {
                    Log.i(TAG, "GATEWAY: Internet available detected. Triggering queue drain to $backendUrl...")
                    drainQueue()
                } else {
                    Log.d(TAG, "GATEWAY: Device offline (mesh relay mode active)")
                }
            }
        }
        Log.i(TAG, "GatewayUploader active with target: $backendUrl")
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        networkMonitor.stop()
        Log.i(TAG, "GatewayUploader stopped")
    }

    /** Trigger an immediate upload attempt (e.g. after receiving a packet in the mesh while online) */
    fun triggerUpload() {
        scope.launch {
            if (networkMonitor.isInternetAvailable.value) {
                drainQueue()
            }
        }
    }

    /** Drains all unsent packets from disk and uploads to backend */
    suspend fun drainQueue() = withContext(Dispatchers.IO) {
        if (!isDraining.compareAndSet(false, true)) {
            Log.d(TAG, "Drain already in progress, skipping concurrent call")
            return@withContext
        }

        try {
            val pending = forwardStore.getPendingUploadPackets()
            if (pending.isEmpty()) {
                Log.d(TAG, "No pending packets awaiting upload")
                return@withContext
            }

            Log.i(TAG, "GATEWAY: Found ${pending.size} packet(s) to upload to backend: $backendUrl")

            for (packet in pending) {
                if (packet.isExpired()) {
                    Log.d(TAG, "GATEWAY: Packet ${packet.messageId} is expired, skipping upload")
                    continue
                }

                uploadSinglePacket(packet)
            }
        } finally {
            isDraining.set(false)
        }
    }

    private suspend fun uploadSinglePacket(packet: SosPacket) = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val endpoint = URL("$backendUrl/api/sos")
            connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 6000
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            val jsonPayload = packet.toByteArray().decodeToString()
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val responseBody = BufferedReader(InputStreamReader(responseStream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }

            if (statusCode in 200..299) {
                Log.i(TAG, "GATEWAY: Successfully uploaded ${packet.messageId} (Status: $statusCode). Server: $responseBody")
                forwardStore.markUploaded(packet.messageId)
                _uploadEvents.emit(GatewayUploadEvent.Success(packet.messageId, statusCode, responseBody))
            } else {
                Log.e(TAG, "GATEWAY: Upload failed for ${packet.messageId} with HTTP $statusCode: $responseBody")
                _uploadEvents.emit(GatewayUploadEvent.Failure(packet.messageId, "HTTP $statusCode: $responseBody"))
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Connection failed"
            Log.e(TAG, "GATEWAY: Exception uploading ${packet.messageId} to $backendUrl: $errorMsg")
            _uploadEvents.emit(GatewayUploadEvent.Failure(packet.messageId, errorMsg))
        } finally {
            connection?.disconnect()
        }
    }
}
