package com.meshroute.app.mesh.router

import com.meshroute.app.mesh.transport.InboundPacket
import com.meshroute.app.mesh.transport.LocationData
import com.meshroute.app.mesh.transport.SosPacket
import com.meshroute.app.mesh.transport.TransportType
import com.meshroute.app.security.CryptoManager
import com.meshroute.app.security.EmergencyPayload
import com.meshroute.app.security.KeyManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DiamondRoutingTest {

    @Test
    fun testSeenSetBasicDedup() {
        val seenSet = SeenSet(seenMessageDao = null)

        // First sighting
        val isFirst = seenSet.add("SOS-100")
        assertTrue(isFirst)
        assertTrue(seenSet.contains("SOS-100"))

        // Duplicate sighting
        val isDuplicate = seenSet.add("SOS-100")
        assertFalse(isDuplicate)

        // Different packet
        val isDifferent = seenSet.add("SOS-200")
        assertTrue(isDifferent)
    }

    @Test
    fun testDiamondTopologySingleDelivery() = runBlocking {
        // Build 4 nodes: A, B, C, D
        val transportA = FakeTransport()
        val transportB = FakeTransport()
        val transportC = FakeTransport()
        val transportD = FakeTransport()

        val routerA = MeshRouter("MR-NODE-A", transportA, createMockForwardStore(), SeenSet())
        val routerB = MeshRouter("MR-NODE-B", transportB, createMockForwardStore(), SeenSet())
        val routerC = MeshRouter("MR-NODE-C", transportC, createMockForwardStore(), SeenSet())
        val routerD = MeshRouter("MR-NODE-D", transportD, createMockForwardStore(), SeenSet())

        routerA.start()
        routerB.start()
        routerC.start()
        routerD.start()

        // Wire Diamond Topology:
        // A -> B and A -> C
        transportA.onSendListener = { data, _ ->
            runBlocking {
                transportB.inboundFlow.emit(InboundPacket(data, "MR-NODE-A", TransportType.LOOPBACK))
                transportC.inboundFlow.emit(InboundPacket(data, "MR-NODE-A", TransportType.LOOPBACK))
            }
        }

        // B -> D
        transportB.onSendListener = { data, _ ->
            runBlocking {
                transportD.inboundFlow.emit(InboundPacket(data, "MR-NODE-B", TransportType.LOOPBACK))
            }
        }

        // C -> D
        transportC.onSendListener = { data, _ ->
            runBlocking {
                transportD.inboundFlow.emit(InboundPacket(data, "MR-NODE-C", TransportType.LOOPBACK))
            }
        }

        val collectedPacketsAtD = mutableListOf<SosPacket>()
        val job = launch {
            routerD.deliveredPackets.collect {
                collectedPacketsAtD.add(it)
            }
        }

        val gpsLocation = LocationData(latitude = 27.9881, longitude = 86.9250, accuracy = 5.0f) // Everest Base Camp

        // 1. Node A originates a single SOS packet with GPS and AES encryption
        val packetA = routerA.originateSos(
            message = "Diamond Multi-Path SOS Broadcast",
            location = gpsLocation,
            senderName = "Expedition Team",
            ttl = 8
        )

        // Allow coroutine dispatches to complete
        kotlinx.coroutines.delay(100L)

        // 2. Node D should have received both copies from B and C
        // but DELIVERED EXACTLY ONCE
        assertEquals(1, collectedPacketsAtD.size)
        val delivered = collectedPacketsAtD[0]
        assertEquals(packetA.messageId, delivered.messageId)
        assertEquals("MR-NODE-A", delivered.originatorId)
        assertEquals(gpsLocation, delivered.location)

        // Decrypt payload at destination
        val decrypted = CryptoManager.decryptString(delivered.payload, KeyManager.defaultEmergencyKey)
        val payloadObj = EmergencyPayload.fromJson(decrypted)
        assertEquals("Diamond Multi-Path SOS Broadcast", payloadObj?.message)

        // 3. Node D stats: 1 delivered, 1 suppressed
        assertEquals(1, routerD.receivedCount.get())
        assertEquals(1, routerD.suppressedCount.get())

        job.cancel()
        routerA.stop()
        routerB.stop()
        routerC.stop()
        routerD.stop()
    }
}
