package com.meshroute.app.security

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Structured emergency data payload that is encrypted at SOS-creation time.
 * Relay devices carry only ciphertext and cannot inspect this information.
 */
@Serializable
data class EmergencyPayload(
    @SerialName("message") val message: String,
    @SerialName("sender_name") val senderName: String = "Unknown User",
    @SerialName("medical_info") val medicalInfo: String = "",
    @SerialName("battery_percent") val batteryPercent: Int = -1,
    @SerialName("emergency_contact") val emergencyContact: String = "",
    @SerialName("timestamp") val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromJson(jsonStr: String): EmergencyPayload? = runCatching {
            json.decodeFromString(serializer(), jsonStr)
        }.getOrNull()

        fun createSimple(message: String, senderName: String = "User"): EmergencyPayload {
            return EmergencyPayload(
                message = message,
                senderName = senderName
            )
        }
    }
}
