package com.meshroute.app.mesh.router

import android.util.Log
import com.meshroute.app.data.queue.ForwardStore
import com.meshroute.app.mesh.transport.LocationData
import com.meshroute.app.mesh.transport.MeshTransport
import com.meshroute.app.mesh.transport.SosPacket
import com.meshroute.app.security.CryptoManager
import com.meshroute.app.security.EmergencyPayload
import com.meshroute.app.security.KeyManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.SecretKey

data class RelayEvent(
    val packetId: String,
    val originatorId: String,
    val incomingHops: Int,
    val outgoingHops: Int,
    val ttl: Int,
    val hopPath: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)

data class DuplicateSuppressedEvent(
    val packetId: String,
    val originatorId: String,
    val duplicateSenderId: String,
    val hopPath: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)

data class TtlExhaustedEvent(
    val packetId: String,
    val originatorId: String,
    val currentHops: Int,
    val ttl: Int,
    val hopPath: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)

data class PacketExpiredEvent(
    val packetId: String,
    val originatorId: String,
    val createdTimestamp: Long,
    val expiredTimestamp: Long,
    val timestamp: Long = System.currentTimeMillis()
)

class MeshRouter(
    val selfNodeId: String,
    private val transport: MeshTransport,
    val forwardStore: ForwardStore,
    val seenSet: SeenSet = SeenSet()
) {
    companion object {
        private const val TAG = "MeshRouter"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _deliveredPackets = MutableSharedFlow<SosPacket>(replay = 16, extraBufferCapacity = 128)
    val deliveredPackets: Flow<SosPacket> = _deliveredPackets.asSharedFlow()

    private val _relayEvents = MutableSharedFlow<RelayEvent>(replay = 16, extraBufferCapacity = 128)
    val relayEvents: Flow<RelayEvent> = _relayEvents.asSharedFlow()

    private val _suppressedEvents = MutableSharedFlow<DuplicateSuppressedEvent>(replay = 16, extraBufferCapacity = 128)
    val suppressedEvents: Flow<DuplicateSuppressedEvent> = _suppressedEvents.asSharedFlow()

    private val _ttlExhaustedEvents = MutableSharedFlow<TtlExhaustedEvent>(replay = 16, extraBufferCapacity = 128)
    val ttlExhaustedEvents: Flow<TtlExhaustedEvent> = _ttlExhaustedEvents.asSharedFlow()

    private val _expiredEvents = MutableSharedFlow<PacketExpiredEvent>(replay = 16, extraBufferCapacity = 128)
    val expiredEvents: Flow<PacketExpiredEvent> = _expiredEvents.asSharedFlow()

    val originatedCount = AtomicInteger(0)
    val relayedCount = AtomicInteger(0)
    val receivedCount = AtomicInteger(0)
    val suppressedCount = AtomicInteger(0)
    val ttlExhaustedCount = AtomicInteger(0)
    val expiredCount = AtomicInteger(0)
    val restoredFromDiskCount = AtomicInteger(0)

    private var routingJob: Job? = null
    private var queueDrainJob: Job? = null

    fun start() {
        if (routingJob?.isActive == true) return

        // 1. Inbound packet listener
        routingJob = scope.launch {
            transport.inbound.collect { inboundPacket ->
                handleInbound(inboundPacket.data)
            }
        }

        // 2. Queue-draining watchdog: drain held packets when neighbors appear
        queueDrainJob = scope.launch {
            transport.neighbors.collect { currentNeighbors ->
                if (currentNeighbors.isNotEmpty()) {
                    drainPendingQueue()
                }
            }
        }

        // 3. Initial startup tasks: load seen cache and drain pending queue
        scope.launch {
            seenSet.loadFromStorage()
            drainPendingQueue()
        }

        Log.i(TAG, "MeshRouter started on node: $selfNodeId (SOS Encryption & GPS support active)")
    }

    fun stop() {
        routingJob?.cancel()
        queueDrainJob?.cancel()
        routingJob = null
        queueDrainJob = null
        scope.coroutineContext.cancelChildren()
        Log.i(TAG, "MeshRouter stopped on node: $selfNodeId")
    }

    /**
     * Originate a real SOS emergency packet with GPS location and AES-256-GCM encrypted payload.
     * The emergency payload is encrypted before touching local persistence or the transport layer.
     */
    suspend fun originateSos(
        message: String,
        location: LocationData? = null,
        senderName: String = "User",
        medicalInfo: String = "",
        batteryPercent: Int = -1,
        targetId: String? = null,
        ttl: Int = SosPacket.DEFAULT_TTL,
        lifetimeMs: Long = SosPacket.DEFAULT_LIFETIME_MS,
        encryptionKey: SecretKey = KeyManager.defaultEmergencyKey
    ): SosPacket = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // 1. Construct emergency payload structure
        val emergencyPayload = EmergencyPayload(
            message = message,
            senderName = senderName,
            medicalInfo = medicalInfo,
            batteryPercent = batteryPercent,
            timestamp = now
        )

        // 2. Encrypt payload with AES-256-GCM before transport
        val encryptedCiphertextBase64 = CryptoManager.encryptString(
            plaintext = emergencyPayload.toJson(),
            key = encryptionKey
        )

        val packet = SosPacket(
            messageId = "SOS-" + UUID.randomUUID().toString().take(8).uppercase(),
            senderId = selfNodeId,
            originatorId = selfNodeId,
            targetId = targetId,
            location = location,
            priority = SosPacket.PRIORITY_SOS,
            payload = encryptedCiphertextBase64,
            ttl = ttl,
            hops = 0,
            hopPath = listOf(selfNodeId),
            timestamp = now,
            expiresAt = now + lifetimeMs
        )

        seenSet.add(packet.messageId)
        originatedCount.incrementAndGet()

        // 3. Persist encrypted packet to SQLite disk before transmission
        forwardStore.persistOutbound(packet)

        Log.i(
            TAG,
            "Originating encrypted SOS packet: ${packet.messageId} (TTL: ${packet.ttl}, GPS: ${packet.location?.latitude ?: "N/A"}, ${packet.location?.longitude ?: "N/A"}, Payload bytes: ${encryptedCiphertextBase64.length})"
        )

        // 4. Transmit over radio transport
        val sent = transport.send(packet.toByteArray())
        if (sent) {
            forwardStore.markRelayed(packet.messageId)
        }

        return@withContext packet
    }

    /** Backward-compatible origination helper for test suites and standard broadcasts */
    suspend fun originate(
        message: String,
        targetId: String? = null,
        ttl: Int = SosPacket.DEFAULT_TTL,
        lifetimeMs: Long = SosPacket.DEFAULT_LIFETIME_MS
    ): SosPacket = originateSos(
        message = message,
        location = null,
        targetId = targetId,
        ttl = ttl,
        lifetimeMs = lifetimeMs
    )

    /** Process incoming packet bytes from direct transport */
    private suspend fun handleInbound(data: ByteArray) {
        val packet = SosPacket.fromByteArray(data) ?: run {
            Log.w(TAG, "Failed to decode inbound packet")
            return
        }

        // 1. Ignore self-originated echo
        if (packet.originatorId == selfNodeId) {
            Log.d(TAG, "Ignoring self-originated packet echo: ${packet.messageId}")
            return
        }

        // ─── GATE 1: Time-based Expiration Check ───
        if (packet.isExpired()) {
            expiredCount.incrementAndGet()
            Log.w(
                TAG,
                "PACKET EXPIRED: ${packet.messageId} from ${packet.senderId} (created: ${packet.timestamp}, expired: ${packet.expiresAt})"
            )
            val expEvent = PacketExpiredEvent(
                packetId = packet.messageId,
                originatorId = packet.originatorId,
                createdTimestamp = packet.timestamp,
                expiredTimestamp = packet.expiresAt
            )
            _expiredEvents.emit(expEvent)
            return
        }

        // ─── GATE 2: Atomic Duplicate Check via SeenSet ───
        val isFirstSighting = seenSet.add(packet.messageId)
        if (!isFirstSighting) {
            suppressedCount.incrementAndGet()
            Log.w(
                TAG,
                "DUPLICATE SUPPRESSED: ${packet.messageId} from ${packet.senderId} (Already seen!)"
            )
            val dupEvent = DuplicateSuppressedEvent(
                packetId = packet.messageId,
                originatorId = packet.originatorId,
                duplicateSenderId = packet.senderId,
                hopPath = packet.hopPath
            )
            _suppressedEvents.emit(dupEvent)
            return
        }

        // ─── GATE 3: Loop Prevention ───
        if (packet.hopPath.contains(selfNodeId)) {
            Log.d(TAG, "Dropping packet ${packet.messageId}: self $selfNodeId already in hop path ${packet.hopPath}")
            return
        }

        // Persist to durable SQLite disk
        forwardStore.persistInbound(packet)

        receivedCount.incrementAndGet()
        Log.i(
            TAG,
            "Delivering SOS packet ${packet.messageId} (Origin: ${packet.originatorId}, Hops: ${packet.hops}/${packet.ttl}, Priority: ${packet.priority})"
        )

        // Deliver locally (payload remains encrypted for privacy)
        _deliveredPackets.emit(packet)

        // ─── GATE 4: Hop-Limit / TTL Check ───
        if (packet.hops + 1 >= packet.ttl) {
            ttlExhaustedCount.incrementAndGet()
            Log.w(
                TAG,
                "TTL EXHAUSTED: Packet ${packet.messageId} reached hop limit (${packet.hops + 1}/${packet.ttl}). Halting propagation."
            )
            val ttlEvent = TtlExhaustedEvent(
                packetId = packet.messageId,
                originatorId = packet.originatorId,
                currentHops = packet.hops + 1,
                ttl = packet.ttl,
                hopPath = packet.hopPath
            )
            _ttlExhaustedEvents.emit(ttlEvent)
            return
        }

        // Prepare relayed copy with incremented hop count
        val relayed = packet.relayedBy(selfNodeId)
        relayedCount.incrementAndGet()

        val event = RelayEvent(
            packetId = packet.messageId,
            originatorId = packet.originatorId,
            incomingHops = packet.hops,
            outgoingHops = relayed.hops,
            ttl = packet.ttl,
            hopPath = relayed.hopPath
        )
        _relayEvents.emit(event)

        Log.i(
            TAG,
            "Relaying encrypted SOS ${relayed.messageId}: Hop ${packet.hops} ➔ ${relayed.hops}/${packet.ttl} (Path: ${relayed.hopPath.joinToString(" ➔ ")})"
        )

        // Forward to reachable neighbors
        val sent = transport.send(relayed.toByteArray())
        if (sent) {
            forwardStore.markRelayed(packet.messageId)
        }
    }

    /**
     * Drains unsent packets from disk that survived a process restart or disconnection.
     */
    suspend fun drainPendingQueue() = withContext(Dispatchers.IO) {
        val pending = forwardStore.getPendingUnsentPackets()
        if (pending.isEmpty()) return@withContext

        Log.i(TAG, "Found ${pending.size} pending packets in persistent storage across restart. Checking TTLs & dispatching...")

        for (packet in pending) {
            if (packet.isExpired()) {
                Log.d(TAG, "Dropping stale packet ${packet.messageId} from disk queue (expired)")
                continue
            }

            if (packet.isHopLimitReached()) {
                Log.d(TAG, "Skipping relay for packet ${packet.messageId} (TTL reached: ${packet.hops}/${packet.ttl})")
                continue
            }

            val isRelaying = !packet.hopPath.contains(selfNodeId)
            val toForward = if (isRelaying) {
                // Check if the next hop exceeds TTL
                if (packet.hops + 1 >= packet.ttl) {
                    ttlExhaustedCount.incrementAndGet()
                    Log.w(
                        TAG,
                        "TTL EXHAUSTED on drain: Packet ${packet.messageId} reached hop limit (${packet.hops + 1}/${packet.ttl})."
                    )
                    val ttlEvent = TtlExhaustedEvent(
                        packetId = packet.messageId,
                        originatorId = packet.originatorId,
                        currentHops = packet.hops + 1,
                        ttl = packet.ttl,
                        hopPath = packet.hopPath
                    )
                    _ttlExhaustedEvents.emit(ttlEvent)
                    continue
                }
                packet.relayedBy(selfNodeId)
            } else {
                packet
            }

            val sent = transport.send(toForward.toByteArray())
            if (sent) {
                restoredFromDiskCount.incrementAndGet()
                forwardStore.markRelayed(packet.messageId)
                if (isRelaying) {
                    relayedCount.incrementAndGet()
                    val event = RelayEvent(
                        packetId = toForward.messageId,
                        originatorId = toForward.originatorId,
                        incomingHops = packet.hops,
                        outgoingHops = toForward.hops,
                        ttl = toForward.ttl,
                        hopPath = toForward.hopPath
                    )
                    _relayEvents.emit(event)
                }
                Log.i(TAG, "Restored & successfully sent packet ${packet.messageId} from persistent disk storage (hops: ${toForward.hops})")
            }
        }
    }
}
