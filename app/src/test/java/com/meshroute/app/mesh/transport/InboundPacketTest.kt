package com.meshroute.app.mesh.transport

import org.junit.Assert.*
import org.junit.Test

class InboundPacketTest {

    @Test
    fun testPacketSerializationRoundTrip() {
        val packet = TestPacket(
            packetId = "PKT-001",
            senderId = "MR-NODE-A",
            targetId = "MR-NODE-B",
            message = "Emergency Beacon Test",
            timestamp = 1720000000000L
        )

        val bytes = packet.toByteArray()
        assertTrue(bytes.isNotEmpty())

        val decoded = TestPacket.fromByteArray(bytes)
        assertNotNull(decoded)
        assertEquals(packet.packetId, decoded?.packetId)
        assertEquals(packet.senderId, decoded?.senderId)
        assertEquals(packet.targetId, decoded?.targetId)
        assertEquals(packet.message, decoded?.message)
        assertEquals(packet.timestamp, decoded?.timestamp)
    }

    @Test
    fun testMalformedPacketReturnsNull() {
        val corruptedBytes = "corrupted-binary-noise".toByteArray()
        val decoded = TestPacket.fromByteArray(corruptedBytes)
        assertNull(decoded)
    }
}
