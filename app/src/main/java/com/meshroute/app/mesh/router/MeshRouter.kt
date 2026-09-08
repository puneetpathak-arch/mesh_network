package com.meshroute.app.mesh.router

import android.util.Log
import com.meshroute.app.data.database.entity.PacketPersistenceStatus
import com.meshroute.app.data.queue.ForwardStore
import com.meshroute.app.mesh.transport.MeshTransport
import com.meshroute.app.mesh.transport.TestPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

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

    private val _deliveredPackets = MutableSharedFlow<TestPacket>(extraBufferCapacity = 128)
    val deliveredPackets: Flow<TestPacket> = _deliveredPackets.asSharedFlow()

    private val _relayEvents = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 128)
    val relayEvents: Flow<RelayEvent> = _relayEvents.asSharedFlow()

    private val _suppressedEvents = MutableSharedFlow<DuplicateSuppressedEvent>(extraBufferCapacity = 128)
    val suppressedEvents: Flow<DuplicateSuppressedEvent> = _suppressedEvents.asSharedFlow()

    private val _ttlExhaustedEvents = MutableSharedFlow<TtlExhaustedEvent>(extraBufferCapacity = 128)
    val ttlExhaustedEvents: Flow<TtlExhaustedEvent> = _ttlExhaustedEvents.asSharedFlow()

    private val _expiredEvents = MutableSharedFlow<PacketExpiredEvent>(extraBufferCapacity = 128)
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

        Log.i(TAG, "MeshRouter started on node: $selfNodeId (TTL & Expiration bounding active)")
    }

    fun stop() {
        routingJob?.cancel()
        queueDrainJob?.cancel()
        routingJob = null
        queueDrainJob = null
        scope.coroutineContext.cancelChildren()
        Log.i(TAG, "MeshRouter stopped on node: $selfNodeId")
    }

    /** Originate a brand new packet from this node with configurable TTL & lifetime */
    suspend fun originate(
        message: String,
        targetId: String? = null,
        ttl: Int = TestPacket.DEFAULT_TTL,
        lifetimeMs: Long = TestPacket.DEFAULT_LIFETIME_MS
    ): TestPacket = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val packet = TestPacket(
            packetId = "PKT-" + UUID.randomUUID().toString().take(8).uppercase(),
            senderId = selfNodeId,
            originatorId = selfNodeId,
            targetId = targetId,
            message = message,
            ttl = ttl,
            hops = 0,
            hopPath = listOf(selfNodeId),
            timestamp = now,
            expiresAt = now + lifetimeMs
        )

        seenSet.add(packet.packetId)
        originatedCount.incrementAndGet()

        // 1. Persist to SQLite disk first
        forwardStore.persistOutbound(packet)

        Log.i(TAG, "Originating packet: ${packet.packetId} (TTL: ${packet.ttl}, Hops: 0, Expires in: ${lifetimeMs / 1000}s)")

        // 2. Attempt radio transmission
        val sent = transport.send(packet.toByteArray())
        if (sent) {
            forwardStore.markRelayed(packet.packetId)
        }

        return@withContext packet
    }

    /** Process incoming packet bytes from direct transport */
    private suspend fun handleInbound(data: ByteArray) {
        val packet = TestPacket.fromByteArray(data) ?: run {
            Log.w(TAG, "Failed to decode inbound packet")
            return
        }

        // 1. Ignore self-originated echo
        if (packet.originatorId == selfNodeId) {
            Log.d(TAG, "Ignoring self-originated packet echo: ${packet.packetId}")
            return
        }

        // ─── GATE 1: Time-based Expiration Check ───
        if (packet.isExpired()) {
            expiredCount.incrementAndGet()
            Log.w(
                TAG,
                "PACKET EXPIRED: ${packet.packetId} from ${packet.senderId} (created: ${packet.timestamp}, expired: ${packet.expiresAt})"
            )
            val expEvent = PacketExpiredEvent(
                packetId = packet.packetId,
                originatorId = packet.originatorId,
                createdTimestamp = packet.timestamp,
                expiredTimestamp = packet.expiresAt
            )
            _expiredEvents.emit(expEvent)
            return
        }

        // ─── GATE 2: Atomic Duplicate Check via SeenSet ───
        val isFirstSighting = seenSet.add(packet.packetId)
        if (!isFirstSighting) {
            suppressedCount.incrementAndGet()
            Log.w(
                TAG,
                "DUPLICATE SUPPRESSED: ${packet.packetId} from ${packet.senderId} (Already seen!)"
            )
            val dupEvent = DuplicateSuppressedEvent(
                packetId = packet.packetId,
                originatorId = packet.originatorId,
                duplicateSenderId = packet.senderId,
                hopPath = packet.hopPath
            )
            _suppressedEvents.emit(dupEvent)
            return
        }

        // ─── GATE 3: Loop Prevention ───
        if (packet.hopPath.contains(selfNodeId)) {
            Log.d(TAG, "Dropping packet ${packet.packetId}: self $selfNodeId already in hop path ${packet.hopPath}")
            return
        }

        // Persist to durable SQLite disk
        forwardStore.persistInbound(packet)

        receivedCount.incrementAndGet()
        Log.i(
            TAG,
            "Delivering packet ${packet.packetId} (Origin: ${packet.originatorId}, Hops: ${packet.hops}/${packet.ttl})"
        )

        // Deliver locally
        _deliveredPackets.emit(packet)

        // ─── GATE 4: Hop-Limit / TTL Check ───
        if (packet.isHopLimitReached()) {
            ttlExhaustedCount.incrementAndGet()
            Log.w(
                TAG,
                "TTL EXHAUSTED: Packet ${packet.packetId} reached hop limit (${packet.hops}/${packet.ttl}). Halting propagation."
            )
            val ttlEvent = TtlExhaustedEvent(
                packetId = packet.packetId,
                originatorId = packet.originatorId,
                currentHops = packet.hops,
                ttl = packet.ttl,
                hopPath = packet.hopPath
            )
            _ttlExhaustedEvents.emit(ttlEvent)
            // Stop relaying
            return
        }

        // Prepare relayed copy with incremented hop count
        val relayed = packet.relayedBy(selfNodeId)
        relayedCount.incrementAndGet()

        val event = RelayEvent(
            packetId = packet.packetId,
            originatorId = packet.originatorId,
            incomingHops = packet.hops,
            outgoingHops = relayed.hops,
            ttl = packet.ttl,
            hopPath = relayed.hopPath
        )
        _relayEvents.emit(event)

        Log.i(
            TAG,
            "Relaying packet ${relayed.packetId}: Hop ${packet.hops} ➔ ${relayed.hops}/${packet.ttl} (Path: ${relayed.hopPath.joinToString(" ➔ ")})"
        )

        // Forward to reachable neighbors
        val sent = transport.send(relayed.toByteArray())
        if (sent) {
            forwardStore.markRelayed(packet.packetId)
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
            // Check expiry before relaying from storage
            if (packet.isExpired()) {
                Log.d(TAG, "Dropping stale packet ${packet.packetId} from disk queue (expired)")
                continue
            }

            if (packet.isHopLimitReached()) {
                Log.d(TAG, "Skipping relay for packet ${packet.packetId} (TTL reached: ${packet.hops}/${packet.ttl})")
                continue
            }

            val toForward = if (!packet.hopPath.contains(selfNodeId)) {
                packet.relayedBy(selfNodeId)
            } else {
                packet
            }

            val sent = transport.send(toForward.toByteArray())
            if (sent) {
                restoredFromDiskCount.incrementAndGet()
                forwardStore.markRelayed(packet.packetId)
                Log.i(TAG, "Restored & successfully relayed packet ${packet.packetId} from persistent disk storage")
            }
        }
    }
}
