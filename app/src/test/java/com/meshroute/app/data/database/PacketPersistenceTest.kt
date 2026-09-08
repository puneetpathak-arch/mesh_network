package com.meshroute.app.data.database

import com.meshroute.app.data.database.entity.PacketPersistenceStatus
import com.meshroute.app.data.database.entity.QueuedPacketEntity
import com.meshroute.app.mesh.transport.LocationData
import com.meshroute.app.mesh.transport.SosPacket
import com.meshroute.app.security.CryptoManager
import com.meshroute.app.security.EmergencyPayload
import com.meshroute.app.security.KeyManager
import org.junit.Assert.*
import org.junit.Test

class PacketPersistenceTest {

    @Test
    fun testQueuedPacketEntityMappingWithGpsAndEncryption() {
        val payload = EmergencyPayload(
            message = "Emergency SOS Beacon across mountains",
            senderName = "Rescue Team 1",
            medicalInfo = "Severe trauma",
            batteryPercent = 42
        )
        val encryptedCiphertext = CryptoManager.encryptString(payload.toJson(), KeyManager.defaultEmergencyKey)
        val location = LocationData(latitude = 46.2044, longitude = 6.1432, accuracy = 2.5f)

        val originalPacket = SosPacket(
            messageId = "SOS-TEST-001",
            senderId = "MR-NODE-B",
            originatorId = "MR-NODE-A",
            targetId = null,
            location = location,
            priority = "SOS",
            payload = encryptedCiphertext,
            ttl = 8,
            hops = 2,
            hopPath = listOf("MR-NODE-A", "MR-NODE-B"),
            timestamp = 1720000000000L
        )

        // Map to Room SQLite Entity
        val entity = QueuedPacketEntity.fromSosPacket(originalPacket, PacketPersistenceStatus.QUEUED)

        assertEquals("SOS-TEST-001", entity.packetId)
        assertEquals("MR-NODE-A", entity.originatorId)
        assertEquals("MR-NODE-B", entity.senderId)
        assertEquals("SOS", entity.priority)
        assertEquals(2, entity.hops)
        assertEquals("QUEUED", entity.status)
        assertEquals(46.2044, entity.latitude!!, 0.0001)
        assertEquals(6.1432, entity.longitude!!, 0.0001)
        assertEquals(2.5f, entity.accuracy!!, 0.01f)
        assertEquals(encryptedCiphertext, entity.payload)
        assertTrue(entity.hopPathJson.contains("MR-NODE-A"))
        assertTrue(entity.hopPathJson.contains("MR-NODE-B"))

        // Reconstruct from entity after simulated process restart
        val restoredPacket = entity.toSosPacket()

        assertEquals(originalPacket.messageId, restoredPacket.messageId)
        assertEquals(originalPacket.originatorId, restoredPacket.originatorId)
        assertEquals(originalPacket.senderId, restoredPacket.senderId)
        assertEquals(originalPacket.payload, restoredPacket.payload)
        assertEquals(originalPacket.hops, restoredPacket.hops)
        assertEquals(originalPacket.hopPath, restoredPacket.hopPath)
        assertEquals(originalPacket.timestamp, restoredPacket.timestamp)
        assertEquals(location, restoredPacket.location)

        // Decrypt restored packet
        val decryptedJson = CryptoManager.decryptString(restoredPacket.payload, KeyManager.defaultEmergencyKey)
        val restoredPayload = EmergencyPayload.fromJson(decryptedJson)
        assertEquals(payload.message, restoredPayload?.message)
        assertEquals(payload.senderName, restoredPayload?.senderName)
        assertEquals(payload.medicalInfo, restoredPayload?.medicalInfo)
        assertEquals(payload.batteryPercent, restoredPayload?.batteryPercent)
    }
}
