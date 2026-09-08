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
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)
