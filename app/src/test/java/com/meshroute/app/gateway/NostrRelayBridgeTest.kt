package com.meshroute.app.gateway

import com.meshroute.app.mesh.transport.SosPacket
import org.junit.Assert.*
import org.junit.Test

class NostrRelayBridgeTest {

    @Test
    fun testNostrEventCreationFromSosPacket() {
        val packet = SosPacket(
            messageId = "msg_test_1001",
            senderId = "Node_Test_1234",
            originatorId = "Node_Test_1234",
            payload = "Base64EncryptedDataSample",
            priority = SosPacket.PRIORITY_SOS,
            ttl = 5
        )

        val event = NostrEvent.createFromSosPacket(packet)

        assertNotNull(event.id)
        assertEquals(64, event.id.length) // 32-byte hex string
        assertEquals(NostrEvent.KIND_EMERGENCY_SOS, event.kind)
        assertTrue(event.tags.any { it[0] == "t" && it[1] == "meshroute_emergency" })
        assertTrue(event.tags.any { it[0] == "priority" && it[1] == SosPacket.PRIORITY_SOS })
        assertTrue(event.tags.any { it[0] == "message_id" && it[1] == packet.messageId })
        assertNotNull(event.sig)
    }

    @Test
    fun testNostrEventIdDeterminism() {
        val packet = SosPacket(
            messageId = "msg_test_1002",
            senderId = "Node_A",
            originatorId = "Node_A",
            payload = "PayloadXYZ",
            priority = SosPacket.PRIORITY_SOS,
            ttl = 3,
            timestamp = 1672531199000L
        )

        val event1 = NostrEvent.createFromSosPacket(packet)
        val event2 = NostrEvent.createFromSosPacket(packet)

        assertEquals(event1.id, event2.id)
        assertEquals(event1.createdAt, event2.createdAt)
    }
}
