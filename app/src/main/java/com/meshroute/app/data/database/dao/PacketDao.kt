package com.meshroute.app.data.database.dao

import androidx.room.*
import com.meshroute.app.data.database.entity.QueuedPacketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PacketDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(packet: QueuedPacketEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(packets: List<QueuedPacketEntity>)

    @Query("SELECT * FROM queued_packets WHERE packetId = :packetId LIMIT 1")
    suspend fun getById(packetId: String): QueuedPacketEntity?

    @Query("SELECT * FROM queued_packets WHERE status = :status ORDER BY timestamp ASC")
    suspend fun getByStatus(status: String): List<QueuedPacketEntity>

    @Query("SELECT * FROM queued_packets WHERE status = 'QUEUED' ORDER BY timestamp ASC")
    suspend fun getPendingQueuedPackets(): List<QueuedPacketEntity>

    @Query("SELECT * FROM queued_packets ORDER BY persistedAt DESC")
    fun observeAll(): Flow<List<QueuedPacketEntity>>

    @Query("SELECT COUNT(*) FROM queued_packets WHERE status = 'QUEUED'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM queued_packets")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT * FROM queued_packets WHERE status != 'UPLOADED' AND status != 'EXPIRED' ORDER BY timestamp ASC")
    suspend fun getPendingUploadPackets(): List<QueuedPacketEntity>

    @Query("SELECT COUNT(*) FROM queued_packets WHERE status = 'UPLOADED'")
    fun observeUploadedCount(): Flow<Int>

    @Query("UPDATE queued_packets SET status = :newStatus WHERE packetId = :packetId")
    suspend fun updateStatus(packetId: String, newStatus: String)

    @Query("DELETE FROM queued_packets WHERE packetId = :packetId")
    suspend fun delete(packetId: String)

    @Query("DELETE FROM queued_packets")
    suspend fun clearAll()
}
