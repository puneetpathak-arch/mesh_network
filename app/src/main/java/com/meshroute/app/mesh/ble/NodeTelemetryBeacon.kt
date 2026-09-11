package com.meshroute.app.mesh.ble

import com.meshroute.app.sensor.MobilityState

/**
 * Packs 4-byte telemetry payload into BLE advertisement service data.
 *
 * Byte 0: Battery % (0..100) | (isCharging ? 0x80 : 0x00)
 * Byte 1: Mobility Code (0: Stationary, 1: Walking, 2: High Speed)
 * Byte 2: Gateway Likelihood (0..100) (Calculated via uplink connectivity recency decay)
 * Byte 3: Queue Load (0..255 packets currently in forward store)
 */
data class NodeTelemetryBeacon(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val mobilityState: MobilityState,
    val gatewayLikelihoodPercent: Int, // 0..100
    val queueLoad: Int // 0..255
) {
    fun toByteArray(): ByteArray {
        val b0 = ((batteryPercent.coerceIn(0, 100)) or (if (isCharging) 0x80 else 0x00)).toByte()
        val b1 = mobilityState.code
        val b2 = gatewayLikelihoodPercent.coerceIn(0, 100).toByte()
        val b3 = queueLoad.coerceIn(0, 255).toByte()
        return byteArrayOf(b0, b1, b2, b3)
    }

    companion object {
        fun fromByteArray(bytes: ByteArray): NodeTelemetryBeacon? {
            if (bytes.size < 4) return null
            val rawB0 = bytes[0].toInt() and 0xFF
            val isCharging = (rawB0 and 0x80) != 0
            val batteryPercent = rawB0 and 0x7F

            val mobility = MobilityState.fromCode(bytes[1])
            val gatewayLikelihood = (bytes[2].toInt() and 0xFF).coerceIn(0, 100)
            val queueLoad = bytes[3].toInt() and 0xFF

            return NodeTelemetryBeacon(
                batteryPercent = batteryPercent,
                isCharging = isCharging,
                mobilityState = mobility,
                gatewayLikelihoodPercent = gatewayLikelihood,
                queueLoad = queueLoad
            )
        }
    }
}
