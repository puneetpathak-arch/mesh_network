package com.meshroute.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.meshroute.app.mesh.transport.LocationData
import com.meshroute.app.mesh.transport.SosPacket
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
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    val priority: String = SosPacket.PRIORITY_SOS,
    val payload: String, // Base64 encrypted payload
    val ttl: Int = SosPacket.DEFAULT_TTL,
    val hops: Int = 0,
    val hopPathJson: String,
    val timestamp: Long,
    val expiresAt: Long = timestamp + SosPacket.DEFAULT_LIFETIME_MS,
    val persistedAt: Long = System.currentTimeMillis(),
    val status: String = PacketPersistenceStatus.QUEUED.name
) {
    // Backward compatibility property for any legacy references
    val message: String get() = payload

    fun toSosPacket(): SosPacket {
        val path = runCatching {
            Json.decodeFromString<List<String>>(hopPathJson)
        }.getOrDefault(listOf(originatorId))

        val loc = if (latitude != null && longitude != null) {
            LocationData(
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy ?: 0f
            )
        } else null

        return SosPacket(
            messageId = packetId,
            senderId = senderId,
            originatorId = originatorId,
            targetId = targetId,
            location = loc,
            priority = priority,
            payload = payload,
            ttl = ttl,
            hops = hops,
            hopPath = path,
            timestamp = timestamp,
            expiresAt = expiresAt
        )
    }

    // Alias for backward compatibility
    fun toTestPacket(): SosPacket = toSosPacket()

    companion object {
        fun fromSosPacket(packet: SosPacket, status: PacketPersistenceStatus = PacketPersistenceStatus.QUEUED): QueuedPacketEntity {
            val pathJson = Json.encodeToString(packet.hopPath)
            return QueuedPacketEntity(
                packetId = packet.messageId,
                originatorId = packet.originatorId,
                senderId = packet.senderId,
                targetId = packet.targetId,
                latitude = packet.location?.latitude,
                longitude = packet.location?.longitude,
                accuracy = packet.location?.accuracy,
                priority = packet.priority,
                payload = packet.payload,
                ttl = packet.ttl,
                hops = packet.hops,
                hopPathJson = pathJson,
                timestamp = packet.timestamp,
                expiresAt = packet.expiresAt,
                persistedAt = System.currentTimeMillis(),
                status = status.name
            )
        }

        fun fromTestPacket(packet: SosPacket, status: PacketPersistenceStatus = PacketPersistenceStatus.QUEUED): QueuedPacketEntity {
            return fromSosPacket(packet, status)
        }
    }
}
