package com.meshroute.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.meshroute.app.mesh.transport.TestPacket
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class PacketPersistenceStatus {
    QUEUED,
    RELAYED,
    DELIVERED,
    UPLOADED,
    TTL_EXHAUSTED,
    EXPIRED
}

@Entity(tableName = "queued_packets")
data class QueuedPacketEntity(
    @PrimaryKey
    val packetId: String,
    val originatorId: String,
    val senderId: String,
    val targetId: String?,
    val message: String,
    val ttl: Int = TestPacket.DEFAULT_TTL,
    val hops: Int = 0,
    val hopPathJson: String,
    val timestamp: Long,
    val expiresAt: Long = timestamp + TestPacket.DEFAULT_LIFETIME_MS,
    val persistedAt: Long = System.currentTimeMillis(),
    val status: String = PacketPersistenceStatus.QUEUED.name
) {
    fun toTestPacket(): TestPacket {
        val path = runCatching {
            Json.decodeFromString<List<String>>(hopPathJson)
        }.getOrDefault(listOf(originatorId))

        return TestPacket(
            packetId = packetId,
            senderId = senderId,
            originatorId = originatorId,
            targetId = targetId,
            message = message,
            ttl = ttl,
            hops = hops,
            hopPath = path,
            timestamp = timestamp,
            expiresAt = expiresAt
        )
    }

    companion object {
        fun fromTestPacket(packet: TestPacket, status: PacketPersistenceStatus = PacketPersistenceStatus.QUEUED): QueuedPacketEntity {
            val pathJson = Json.encodeToString(packet.hopPath)
            return QueuedPacketEntity(
                packetId = packet.packetId,
                originatorId = packet.originatorId,
                senderId = packet.senderId,
                targetId = packet.targetId,
                message = packet.message,
                ttl = packet.ttl,
                hops = packet.hops,
                hopPathJson = pathJson,
                timestamp = packet.timestamp,
                expiresAt = packet.expiresAt,
                persistedAt = System.currentTimeMillis(),
                status = status.name
            )
        }
    }
}
