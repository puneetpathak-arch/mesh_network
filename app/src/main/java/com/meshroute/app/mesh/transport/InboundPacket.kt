package com.meshroute.app.mesh.transport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Geographic GPS coordinates captured at SOS creation time.
 */
@Serializable
data class LocationData(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("accuracy") val accuracy: Float = 0f
)

/**
 * Full MeshRoute SOS Packet Schema conforming to architecture.md §Packet Schema:
 * - message_id: Unique packet identifier
 * - sender_id: Current relay node transmitting over the radio link
 * - originator_id: Initial creator node that generated the SOS
 * - timestamp: Unix epoch millisecond timestamp of SOS generation
 * - location: Captured GPS coordinates (lat/lng/accuracy)
 * - priority: "SOS" emergency priority flag
 * - ttl: Maximum allowed hop count before propagation stops
 * - hops: Current hop count traversed
 * - payload: AES-256-GCM encrypted emergency payload (Base64 string)
 * - hop_path: List of relay node identifiers traversed
 * - expires_at: Wall-clock expiration timestamp
 */
@Serializable
data class SosPacket(
    @SerialName("message_id") val messageId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("originator_id") val originatorId: String = senderId,
    @SerialName("target_id") val targetId: String? = null,
    @SerialName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerialName("location") val location: LocationData? = null,
    @SerialName("priority") val priority: String = PRIORITY_SOS,
    @SerialName("ttl") val ttl: Int = DEFAULT_TTL,
    @SerialName("hops") val hops: Int = 0,
    @SerialName("payload") val payload: String,
    @SerialName("hop_path") val hopPath: List<String> = listOf(senderId),
    @SerialName("expires_at") val expiresAt: Long = timestamp + DEFAULT_LIFETIME_MS
) {
    val packetId: String get() = messageId

    companion object {
        const val PRIORITY_SOS = "SOS"
        const val DEFAULT_TTL = 8
        const val DEFAULT_LIFETIME_MS = 24 * 60 * 60 * 1000L // 24 hours

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromByteArray(bytes: ByteArray): SosPacket? = runCatching {
            json.decodeFromString(serializer(), bytes.decodeToString())
        }.getOrNull()
    }

    /** Returns true if the wall-clock expiration has passed */
    fun isExpired(currentTime: Long = System.currentTimeMillis()): Boolean {
        return currentTime > expiresAt
    }

    /** Returns true if this packet has reached or exceeded its maximum allowed hops */
    fun isHopLimitReached(): Boolean {
        return hops >= ttl
    }

    /** Creates a relayed copy with incremented hop count and appended relay node */
    fun relayedBy(relayNodeId: String): SosPacket {
        return this.copy(
            senderId = relayNodeId,
            hops = this.hops + 1,
            hopPath = this.hopPath + relayNodeId
        )
    }

    fun toByteArray(): ByteArray = json.encodeToString(serializer(), this).encodeToByteArray()
}

/** Backward-compatible typealias for test suites and previous phase references */
typealias TestPacket = SosPacket

data class InboundPacket(
    val data: ByteArray,
    val senderNodeId: String,
    val transportType: TransportType,
    val receivedTimestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as InboundPacket
        return data.contentEquals(other.data) && senderNodeId == other.senderNodeId && transportType == other.transportType
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + senderNodeId.hashCode()
        result = 31 * result + transportType.hashCode()
        return result
    }
}
