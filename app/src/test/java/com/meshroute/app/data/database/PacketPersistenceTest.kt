package com.meshroute.app.data.database

import com.meshroute.app.data.database.entity.PacketPersistenceStatus
import com.meshroute.app.data.database.entity.QueuedPacketEntity
import com.meshroute.app.mesh.transport.TestPacket
import org.junit.Assert.*
import org.junit.Test

class PacketPersistenceTest {

    @Test
    fun testQueuedPacketEntityMapping() {
        val originalPacket = TestPacket(
            packetId = "PKT-TEST-001",
            senderId = "MR-NODE-B",
            originatorId = "MR-NODE-A",
            targetId = null,
            message = "Emergency SOS Beacon across mountains",
            hopCount = 2,
            hopPath = listOf("MR-NODE-A", "MR-NODE-B"),
            timestamp = 1720000000000L
        )

        // Map to Room SQLite Entity
        val entity = QueuedPacketEntity.fromTestPacket(originalPacket, PacketPersistenceStatus.QUEUED)

        assertEquals("PKT-TEST-001", entity.packetId)
        assertEquals("MR-NODE-A", entity.originatorId)
        assertEquals("MR-NODE-B", entity.senderId)
        assertEquals(2, entity.hopCount)
        assertEquals("QUEUED", entity.status)
        assertTrue(entity.hopPathJson.contains("MR-NODE-A"))
        assertTrue(entity.hopPathJson.contains("MR-NODE-B"))

        // Reconstruct from entity after simulated process restart
        val restoredPacket = entity.toTestPacket()

        assertEquals(originalPacket.packetId, restoredPacket.packetId)
        assertEquals(originalPacket.originatorId, restoredPacket.originatorId)
        assertEquals(originalPacket.senderId, restoredPacket.senderId)
        assertEquals(originalPacket.message, restoredPacket.message)
        assertEquals(originalPacket.hopCount, restoredPacket.hopCount)
        assertEquals(originalPacket.hopPath, restoredPacket.hopPath)
        assertEquals(originalPacket.timestamp, restoredPacket.timestamp)
    }
}
