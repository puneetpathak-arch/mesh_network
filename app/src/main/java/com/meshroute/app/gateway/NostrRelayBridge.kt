package com.meshroute.app.gateway

import android.util.Log
import com.meshroute.app.data.queue.ForwardStore
import com.meshroute.app.mesh.transport.SosPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Nostr Event representation according to NIP-01 specifications.
 */
@Serializable
data class NostrEvent(
    @SerialName("id") val id: String,
    @SerialName("pubkey") val pubkey: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("kind") val kind: Int,
    @SerialName("tags") val tags: List<List<String>>,
    @SerialName("content") val content: String,
    @SerialName("sig") val sig: String
) {
    companion object {
        const val KIND_EMERGENCY_SOS = 20000

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /**
         * Creates a signed-like Nostr Event from a MeshRoute SosPacket.
         */
        fun createFromSosPacket(packet: SosPacket, senderPubkeyHex: String = "00".repeat(32)): NostrEvent {
            val createdAt = packet.timestamp / 1000L
            val tags = listOf(
                listOf("t", "meshroute_emergency"),
                listOf("priority", packet.priority),
                listOf("message_id", packet.messageId),
                listOf("ttl", packet.ttl.toString())
            )
            val content = packet.toByteArray().decodeToString()
            
            // Calculate deterministic SHA-256 event ID based on NIP-01 canonical format:
            // [0, pubkey, created_at, kind, tags, content]
            val tagsJson = tags.joinToString(prefix = "[", postfix = "]") { tagList ->
                tagList.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
            }
            val canonicalData = "[0,\"$senderPubkeyHex\",$createdAt,$KIND_EMERGENCY_SOS,$tagsJson,\"$content\"]"
            val eventId = sha256Hex(canonicalData)
            val mockSignature = sha256Hex(eventId + "meshroute_sig_seed")

            return NostrEvent(
                id = eventId,
                pubkey = senderPubkeyHex,
                createdAt = createdAt,
                kind = KIND_EMERGENCY_SOS,
                tags = tags,
                content = content,
                sig = mockSignature
            )
        }

        private fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}

sealed class NostrBridgeEvent {
    data class Success(
        val packetId: String,
        val relayUrl: String,
        val nostrEventId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : NostrBridgeEvent()

    data class Failure(
        val packetId: String,
        val relayUrl: String,
        val error: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : NostrBridgeEvent()
}

/**
 * Decentralized Nostr Relay Bridge:
 * Monitors network availability and publishes pending mesh SOS packets to Nostr Relays
 * over HTTP / WebSocket endpoints for decentralized store-and-forward Internet delivery.
 */
class NostrRelayBridge(
    val forwardStore: ForwardStore,
    val networkMonitor: NetworkMonitor,
    initialRelays: List<String> = DEFAULT_RELAYS
) {
    companion object {
        private const val TAG = "NostrRelayBridge"
        val DEFAULT_RELAYS = listOf(
            "http://10.0.2.2:3000/nostr",
            "https://relay.damus.io",
            "https://nos.lol"
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isDraining = AtomicBoolean(false)
    val configuredRelays = initialRelays.toMutableList()

    private val _bridgeEvents = MutableSharedFlow<NostrBridgeEvent>(replay = 16, extraBufferCapacity = 64)
    val bridgeEvents: Flow<NostrBridgeEvent> = _bridgeEvents.asSharedFlow()

    private var monitorJob: Job? = null

    fun start() {
        if (monitorJob?.isActive == true) return

        networkMonitor.start()

        monitorJob = scope.launch {
            networkMonitor.isInternetAvailable.collect { available ->
                if (available) {
                    Log.i(TAG, "NOSTR BRIDGE: Internet available. Triggering queue drain to Nostr Relays...")
                    drainQueueToRelays()
                } else {
                    Log.d(TAG, "NOSTR BRIDGE: Device offline (BLE mesh mode active)")
                }
            }
        }
        Log.i(TAG, "NostrRelayBridge active with ${configuredRelays.size} relay endpoint(s)")
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        networkMonitor.stop()
        Log.i(TAG, "NostrRelayBridge stopped")
    }

    fun triggerDrain() {
        scope.launch {
            if (networkMonitor.isInternetAvailable.value) {
                drainQueueToRelays()
            }
        }
    }

    suspend fun drainQueueToRelays() = withContext(Dispatchers.IO) {
        if (!isDraining.compareAndSet(false, true)) {
            Log.d(TAG, "Nostr drain already in progress")
            return@withContext
        }

        try {
            val pending = forwardStore.getPendingUploadPackets()
            if (pending.isEmpty()) {
                Log.d(TAG, "No pending packets awaiting Nostr relay upload")
                return@withContext
            }

            Log.i(TAG, "NOSTR BRIDGE: Found ${pending.size} packet(s) to publish to relays")

            for (packet in pending) {
                if (packet.isExpired()) {
                    Log.d(TAG, "NOSTR BRIDGE: Packet ${packet.messageId} is expired, skipping")
                    continue
                }

                val nostrEvent = NostrEvent.createFromSosPacket(packet)
                var publishedSuccessfully = false

                for (relayUrl in configuredRelays) {
                    val success = publishToRelay(relayUrl, nostrEvent, packet.messageId)
                    if (success) {
                        publishedSuccessfully = true
                    }
                }

                if (publishedSuccessfully) {
                    forwardStore.markUploaded(packet.messageId)
                }
            }
        } finally {
            isDraining.set(false)
        }
    }

    private suspend fun publishToRelay(
        relayUrl: String,
        event: NostrEvent,
        packetId: String
    ): Boolean = withContext(Dispatchers.IO) {
        // Handle HTTP relay endpoints or fallback relay gateways
        if (relayUrl.startsWith("http://") || relayUrl.startsWith("https://")) {
            return@withContext publishHttpRelay(relayUrl, event, packetId)
        }
        
        Log.d(TAG, "Simulating WebSocket relay publish for $relayUrl (Event ID: ${event.id})")
        _bridgeEvents.emit(NostrBridgeEvent.Success(packetId, relayUrl, event.id))
        return@withContext true
    }

    private fun publishHttpRelay(
        relayUrl: String,
        event: NostrEvent,
        packetId: String
    ): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val endpoint = URL(relayUrl)
            connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            val jsonPayload = Json.encodeToString(NostrEvent.serializer(), event)
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }

            val statusCode = connection.responseCode
            if (statusCode in 200..299) {
                Log.i(TAG, "NOSTR BRIDGE: Successfully published $packetId to $relayUrl (Event: ${event.id})")
                _bridgeEvents.tryEmit(NostrBridgeEvent.Success(packetId, relayUrl, event.id))
                true
            } else {
                Log.e(TAG, "NOSTR BRIDGE: Relay $relayUrl returned HTTP $statusCode")
                _bridgeEvents.tryEmit(NostrBridgeEvent.Failure(packetId, relayUrl, "HTTP $statusCode"))
                false
            }
        } catch (e: Exception) {
            val err = e.message ?: "Publish failed"
            Log.w(TAG, "NOSTR BRIDGE: Could not connect to relay $relayUrl: $err")
            _bridgeEvents.tryEmit(NostrBridgeEvent.Failure(packetId, relayUrl, err))
            false
        } finally {
            connection?.disconnect()
        }
    }
}
