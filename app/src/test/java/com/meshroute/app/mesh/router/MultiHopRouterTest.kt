package com.meshroute.app.mesh.router

import com.meshroute.app.mesh.transport.InboundPacket
import com.meshroute.app.mesh.transport.LocationData
import com.meshroute.app.mesh.transport.SosPacket
import com.meshroute.app.mesh.transport.TransportType
import com.meshroute.app.security.CryptoManager
import com.meshroute.app.security.EmergencyPayload
import com.meshroute.app.security.KeyManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MultiHopRouterTest {

    @Test
    fun testMultiHopRelayChainAtoBtoC() = runBlocking {
        // Create 3 Transports and Routers: Node A, Node B, Node C
        val transportA = FakeTransport()
        val routerA = MeshRouter("MR-NODE-A", transportA, createMockForwardStore())

        val transportB = FakeTransport()
        val routerB = MeshRouter("MR-NODE-B", transportB, createMockForwardStore())

        val transportC = FakeTransport()
        val routerC = MeshRouter("MR-NODE-C", transportC, createMockForwardStore())

        routerA.start()
        routerB.start()
        routerC.start()

        // Wire virtual topology:
        // A -> B (A can only reach B)
        // B -> C (B can reach C)
        transportA.onSendListener = { data, _ ->
            runBlocking {
                transportB.inboundFlow.emit(
                    InboundPacket(data, "MR-NODE-A", TransportType.LOOPBACK)
                )
            }
        }

        transportB.onSendListener = { data, _ ->
            runBlocking {
                transportC.inboundFlow.emit(
                    InboundPacket(data, "MR-NODE-B", TransportType.LOOPBACK)
                )
            }
        }

        val collectedPacketsAtC = mutableListOf<SosPacket>()
        val jobC = launch {
            routerC.deliveredPackets.collect {
                collectedPacketsAtC.add(it)
            }
        }

        val testLocation = LocationData(latitude = 45.8326, longitude = 6.8652, accuracy = 3.0f) // Mont Blanc coords

        // 1. Node A originates a real encrypted SOS packet with GPS location
        val origPacket = routerA.originateSos(
            message = "Avalanche warning on Sector 4, hiker trapped",
            location = testLocation,
            senderName = "Alpine Hiker",
            ttl = 8
        )

        kotlinx.coroutines.delay(100L)

        assertEquals("MR-NODE-A", origPacket.originatorId)
        assertEquals(0, origPacket.hops)
        assertEquals(listOf("MR-NODE-A"), origPacket.hopPath)
        assertEquals("SOS", origPacket.priority)
        assertEquals(testLocation, origPacket.location)

        // Verify the payload is ciphertext (NOT plaintext)
        assertFalse(origPacket.payload.contains("Avalanche warning"))

        // 2. Node B should have relayed it
        assertEquals(1, routerB.relayedCount.get())
        assertEquals(1, routerB.receivedCount.get())

        // 3. Node C should have delivered the SOS packet originated by Node A
        assertEquals(1, collectedPacketsAtC.size)
        val receivedByC = collectedPacketsAtC[0]

        assertEquals(origPacket.messageId, receivedByC.messageId)
        assertEquals("MR-NODE-A", receivedByC.originatorId)
        assertEquals("MR-NODE-B", receivedByC.senderId)
        assertEquals(1, receivedByC.hops)
        assertEquals(listOf("MR-NODE-A", "MR-NODE-B"), receivedByC.hopPath)
        assertEquals(testLocation, receivedByC.location)

        // 4. Node C (authorized recipient) decrypts payload
        val decryptedJson = CryptoManager.decryptString(receivedByC.payload, KeyManager.defaultEmergencyKey)
        val decryptedPayload = EmergencyPayload.fromJson(decryptedJson)
        assertNotNull(decryptedPayload)
        assertEquals("Avalanche warning on Sector 4, hiker trapped", decryptedPayload?.message)
        assertEquals("Alpine Hiker", decryptedPayload?.senderName)

        jobC.cancel()
        routerA.stop()
        routerB.stop()
        routerC.stop()
    }
}
