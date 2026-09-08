package com.meshroute.app.data.queue

import android.util.Log
import com.meshroute.app.data.database.dao.PacketDao
import com.meshroute.app.data.database.entity.PacketPersistenceStatus
import com.meshroute.app.data.database.entity.QueuedPacketEntity
import com.meshroute.app.mesh.transport.TestPacket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Durable store-and-forward queue backed by Room Database.
 * Guarantees packets are written to disk before radio forwarding.
 */
class ForwardStore(
    private val packetDao: PacketDao
) {
    companion object {
        private const val TAG = "ForwardStore"
    }

    val queuedPacketsFlow: Flow<List<QueuedPacketEntity>> = packetDao.observeAll()
    val pendingCountFlow: Flow<Int> = packetDao.observePendingCount()
    val totalCountFlow: Flow<Int> = packetDao.observeTotalCount()

    /**
     * Persist an incoming packet to disk immediately as QUEUED.
     * Must be called BEFORE any network transmission attempt.
     */
    suspend fun persistInbound(packet: TestPacket): QueuedPacketEntity = withContext(Dispatchers.IO) {
        val entity = QueuedPacketEntity.fromTestPacket(packet, PacketPersistenceStatus.QUEUED)
        packetDao.insert(entity)
        Log.i(TAG, "Persisted packet ${packet.packetId} to disk as QUEUED (Origin: ${packet.originatorId})")
        return@withContext entity
    }

    /**
     * Persist an outbound self-originated packet.
     */
    suspend fun persistOutbound(packet: TestPacket): QueuedPacketEntity = withContext(Dispatchers.IO) {
        val entity = QueuedPacketEntity.fromTestPacket(packet, PacketPersistenceStatus.QUEUED)
        packetDao.insert(entity)
        Log.i(TAG, "Persisted self-originated packet ${packet.packetId} to disk as QUEUED")
        return@withContext entity
    }

    /** Mark a packet as successfully relayed over the radio link */
    suspend fun markRelayed(packetId: String) = withContext(Dispatchers.IO) {
        packetDao.updateStatus(packetId, PacketPersistenceStatus.RELAYED.name)
        Log.d(TAG, "Updated packet $packetId status to RELAYED")
    }

    /** Mark a packet as delivered locally or confirmed */
    suspend fun markDelivered(packetId: String) = withContext(Dispatchers.IO) {
        packetDao.updateStatus(packetId, PacketPersistenceStatus.DELIVERED.name)
        Log.d(TAG, "Updated packet $packetId status to DELIVERED")
    }

    /** Retrieve all pending packets that need forwarding (e.g. on restart or reconnect) */
    suspend fun getPendingUnsentPackets(): List<TestPacket> = withContext(Dispatchers.IO) {
        val entities = packetDao.getPendingQueuedPackets()
        return@withContext entities.map { it.toTestPacket() }
    }

    suspend fun clearDatabase() = withContext(Dispatchers.IO) {
        packetDao.clearAll()
    }
}
