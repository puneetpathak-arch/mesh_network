package com.meshroute.app.service

import com.meshroute.app.mesh.transport.LocationData
import com.meshroute.app.mesh.transport.SosPacket
import com.meshroute.app.security.CryptoManager
import com.meshroute.app.security.EmergencyPayload
import com.meshroute.app.security.KeyManager
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SosNotificationTest {

    @Test
    fun testSosPayloadDecryptionAndFormatting() {
        val messageText = "Injured climber with broken leg at Ridge Point"
        val sender = "Alice Walker"
        val medical = "Blood type O+, allergic to penicillin"
        val battery = 64
        val lat = 37.774929
        val lon = -122.419416
        val acc = 8.5f

        val payload = EmergencyPayload(
            message = messageText,
            senderName = sender,
            medicalInfo = medical,
            batteryPercent = battery,
            timestamp = System.currentTimeMillis()
        )

        val encryptedPayload = CryptoManager.encryptString(payload.toJson(), KeyManager.defaultEmergencyKey)

        val packet = SosPacket(
            messageId = "SOS-ALERT-9988",
            senderId = "Node_Alice_01",
            originatorId = "Node_Alice_01",
            location = LocationData(latitude = lat, longitude = lon, accuracy = acc),
            priority = SosPacket.PRIORITY_SOS,
            payload = encryptedPayload,
            ttl = 8,
            hops = 2,
            hopPath = listOf("Node_Alice_01", "Node_Bob_02", "Node_Charlie_03"),
            timestamp = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 3600000L
        )

        // Verify decryption matches
        val decryptedJson = CryptoManager.decryptString(packet.payload, KeyManager.defaultEmergencyKey)
        val extracted = EmergencyPayload.fromJson(decryptedJson)

        assertEquals(messageText, extracted.message)
        assertEquals(sender, extracted.senderName)
        assertEquals(medical, extracted.medicalInfo)
        assertEquals(battery, extracted.batteryPercent)

        // Verify Coordinate representation
        val latStr = String.format(Locale.US, "%.5f", packet.location!!.latitude)
        val lonStr = String.format(Locale.US, "%.5f", packet.location!!.longitude)
        assertEquals("37.77493", latStr)
        assertEquals("-122.41942", lonStr)
        assertEquals("📍 37.77493, -122.41942 (±8m)", "📍 $latStr, $lonStr (±${packet.location!!.accuracy!!.toInt()}m)")
    }

    @Test
    fun testSosPayloadWithNullLocation() {
        val payload = EmergencyPayload(
            message = "Trapped in cave, need search and rescue",
            senderName = "Bob"
        )
        val encryptedPayload = CryptoManager.encryptString(payload.toJson(), KeyManager.defaultEmergencyKey)

        val packet = SosPacket(
            messageId = "SOS-CAVE-001",
            senderId = "Node_Bob_01",
            originatorId = "Node_Bob_01",
            location = null,
            priority = SosPacket.PRIORITY_SOS,
            payload = encryptedPayload,
            ttl = 8,
            hops = 0,
            hopPath = listOf("Node_Bob_01"),
            timestamp = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 3600000L
        )

        assertNull(packet.location)
        val decryptedJson = CryptoManager.decryptString(packet.payload, KeyManager.defaultEmergencyKey)
        val extracted = EmergencyPayload.fromJson(decryptedJson)
        assertEquals("Bob", extracted.senderName)
        assertEquals("Trapped in cave, need search and rescue", extracted.message)
    }
}
