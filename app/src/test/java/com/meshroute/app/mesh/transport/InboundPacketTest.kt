package com.meshroute.app.mesh.transport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class InboundPacketTest {

    @Test
    fun testSosPacketSerializationRoundTrip() {
        val original = SosPacket(
            messageId = "SOS-A1B2C3D4",
            senderId = "MR-NODE-A",
            originatorId = "MR-NODE-A",
            timestamp = 1720000000000L,
            location = LocationData(
                latitude = 37.7749,
                longitude = -122.4194,
                accuracy = 4.5f
            ),
            priority = "SOS",
            ttl = 8,
            hops = 0,
            payload = "BASE64_CIPHERTEXT_AES_GCM_SAMPLE==",
            hopPath = listOf("MR-NODE-A")
        )

        val bytes = original.toByteArray()
        assertTrue(bytes.isNotEmpty())

        val decoded = SosPacket.fromByteArray(bytes)
        assertNotNull(decoded)
        assertEquals(original.messageId, decoded?.messageId)
        assertEquals(original.senderId, decoded?.senderId)
        assertEquals(original.originatorId, decoded?.originatorId)
        assertEquals(original.priority, decoded?.priority)
        assertEquals(original.ttl, decoded?.ttl)
        assertEquals(original.hops, decoded?.hops)
        assertEquals(original.payload, decoded?.payload)
        assertEquals(original.timestamp, decoded?.timestamp)
        assertNotNull(decoded?.location)
        assertEquals(37.7749, decoded!!.location!!.latitude, 0.0001)
        assertEquals(-122.4194, decoded.location!!.longitude, 0.0001)
        assertEquals(4.5f, decoded.location!!.accuracy, 0.01f)
    }

    @Test
    fun testJsonKeysMatchArchitectureSpec() {
        val packet = SosPacket(
            messageId = "SOS-SPEC-1234",
            senderId = "MR-NODE-A",
            timestamp = 1720000000000L,
            location = LocationData(34.0522, -118.2437, 5.0f),
            priority = "SOS",
            ttl = 8,
            hops = 0,
            payload = "encrypted-payload-sample"
        )

        val jsonStr = packet.toByteArray().decodeToString()
        val jsonElement = Json.parseToJsonElement(jsonStr).jsonObject

        // Verify exact keys per architecture.md §Packet Schema
        assertTrue("Must have message_id", jsonElement.containsKey("message_id"))
        assertTrue("Must have sender_id", jsonElement.containsKey("sender_id"))
        assertTrue("Must have timestamp", jsonElement.containsKey("timestamp"))
        assertTrue("Must have location", jsonElement.containsKey("location"))
        assertTrue("Must have priority", jsonElement.containsKey("priority"))
        assertTrue("Must have ttl", jsonElement.containsKey("ttl"))
        assertTrue("Must have hops", jsonElement.containsKey("hops"))
        assertTrue("Must have payload", jsonElement.containsKey("payload"))

        assertEquals("SOS-SPEC-1234", jsonElement["message_id"]?.jsonPrimitive?.content)
        assertEquals("SOS", jsonElement["priority"]?.jsonPrimitive?.content)
    }

    @Test
    fun testMalformedPacketReturnsNull() {
        val corruptedBytes = "corrupted-binary-noise".toByteArray()
        val decoded = SosPacket.fromByteArray(corruptedBytes)
        assertNull(decoded)
    }
}
