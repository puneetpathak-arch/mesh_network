package com.meshroute.app.mesh.router

import com.meshroute.app.data.database.entity.QueuedPacketEntity
import com.meshroute.app.data.queue.ForwardStore
import com.meshroute.app.mesh.transport.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeTransport(override val transportType: TransportType = TransportType.LOOPBACK) : MeshTransport {
    private val _neighbors = MutableStateFlow<Set<Peer>>(emptySet())
    override val neighbors: StateFlow<Set<Peer>> = _neighbors

    private val _health = MutableStateFlow(TransportHealth.HEALTHY)
    override val health: StateFlow<TransportHealth> = _health

    val inboundFlow = MutableSharedFlow<InboundPacket>(extraBufferCapacity = 64)
    override val inbound: Flow<InboundPacket> = inboundFlow

    val sentPackets = mutableListOf<ByteArray>()
    var onSendListener: ((ByteArray, Peer?) -> Unit)? = null

    override suspend fun start() {}
    override suspend fun stop() {}

    override suspend fun send(data: ByteArray, to: Peer?): Boolean {
        sentPackets.add(data)
        onSendListener?.invoke(data, to)
        return true
    }
}

fun createMockForwardStore(): ForwardStore {
    val mockDao = object : com.meshroute.app.data.database.dao.PacketDao {
        val stored = mutableListOf<QueuedPacketEntity>()
        override suspend fun insert(packet: QueuedPacketEntity) { stored.add(packet) }
        override suspend fun insertAll(packets: List<QueuedPacketEntity>) { stored.addAll(packets) }
        override suspend fun getById(packetId: String): QueuedPacketEntity? = stored.find { it.packetId == packetId }
        override suspend fun getByStatus(status: String): List<QueuedPacketEntity> = stored.filter { it.status == status }
        override suspend fun getPendingQueuedPackets(): List<QueuedPacketEntity> = stored.filter { it.status == "QUEUED" }
        override suspend fun getPendingUploadPackets(): List<QueuedPacketEntity> = stored.filter { it.status != "UPLOADED" && it.status != "EXPIRED" }
        override fun observeAll() = kotlinx.coroutines.flow.flowOf(stored)
        override fun observePendingCount() = kotlinx.coroutines.flow.flowOf(stored.count { it.status == "QUEUED" })
        override fun observeTotalCount() = kotlinx.coroutines.flow.flowOf(stored.size)
        override fun observeUploadedCount() = kotlinx.coroutines.flow.flowOf(stored.count { it.status == "UPLOADED" })
        override suspend fun updateStatus(packetId: String, newStatus: String) {
            val idx = stored.indexOfFirst { it.packetId == packetId }
            if (idx >= 0) stored[idx] = stored[idx].copy(status = newStatus)
        }
        override suspend fun delete(packetId: String) { stored.removeIf { it.packetId == packetId } }
        override suspend fun clearAll() { stored.clear() }
    }
    return ForwardStore(mockDao)
}
