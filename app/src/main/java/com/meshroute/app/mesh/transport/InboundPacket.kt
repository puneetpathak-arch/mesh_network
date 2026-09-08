package com.meshroute.app.mesh.transport

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TestPacket(
    val packetId: String,
    val senderId: String,
    val originatorId: String = senderId,
    val targetId: String? = null,
    val message: String,
    val ttl: Int = DEFAULT_TTL,
    val hops: Int = 0,
    val hopPath: List<String> = listOf(senderId),
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long = timestamp + DEFAULT_LIFETIME_MS
) {
    companion object {
        const val DEFAULT_TTL = 8
        const val DEFAULT_LIFETIME_MS = 24 * 60 * 60 * 1000L // 24 hours

        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromByteArray(bytes: ByteArray): TestPacket? = runCatching {
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
    fun relayedBy(relayNodeId: String): TestPacket {
        return this.copy(
            senderId = relayNodeId,
            hops = this.hops + 1,
            hopPath = this.hopPath + relayNodeId
        )
    }

    fun toByteArray(): ByteArray = Json.encodeToString(serializer(), this).encodeToByteArray()
}

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
