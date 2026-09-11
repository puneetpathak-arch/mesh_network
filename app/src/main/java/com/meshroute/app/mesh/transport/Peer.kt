package com.meshroute.app.mesh.transport

enum class TransportType {
    BLE,
    WIFI_DIRECT,
    WIFI_AWARE,
    LOOPBACK
}

data class Peer(
    val nodeId: String,
    val deviceName: String = "Unknown",
    val transportType: TransportType = TransportType.BLE,
    val rssi: Int = 0,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val mobilityCode: Byte = 0, // 0: Stationary, 1: Walking, 2: High Speed
    val gatewayLikelihood: Float = 0.5f, // 0.0 to 1.0 based on uplink recency
    val queueLoad: Int = 0,
    val lastEdsScore: Int = 50
)

