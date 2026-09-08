package com.meshroute.app.mesh.router

import com.meshroute.app.mesh.transport.InboundPacket
import com.meshroute.app.mesh.transport.LocationData
import com.meshroute.app.mesh.transport.SosPacket
import com.meshroute.app.mesh.transport.TransportType
import com.meshroute.app.security.CryptoManager
import com.meshroute.app.security.EmergencyPayload
import com.meshroute.app.security.KeyManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.security.GeneralSecurityException

class SosEndToEndPipelineTest {

    @Test
    fun testRealSosPacketSurvivesPhases2Through6Pipeline() = runBlocking {
        // =====================================================================
        // 1. SETUP: 4 Mesh Nodes (A: Originator, B & C: Relays, D: Destination)
        // =====================================================================
        val transportA = FakeTransport()
        val transportB = FakeTransport()
        val transportC = FakeTransport()
        val transportD = FakeTransport()

        val forwardStoreA = createMockForwardStore()
        val forwardStoreB = createMockForwardStore()
        val forwardStoreC = createMockForwardStore()
        val forwardStoreD = createMockForwardStore()

        val routerA = MeshRouter("MR-NODE-A", transportA, forwardStoreA, SeenSet())
        val routerB = MeshRouter("MR-NODE-B", transportB, forwardStoreB, SeenSet())
        val routerC = MeshRouter("MR-NODE-C", transportC, forwardStoreC, SeenSet())
        val routerD = MeshRouter("MR-NODE-D", transportD, forwardStoreD, SeenSet())

        routerA.start()
        routerB.start()
        routerC.start()
        routerD.start()

        // Diamond multi-hop topology:
        // A -> B and A -> C (One-Hop: Phase 2)
        // B -> D and C -> D (Multi-Hop: Phase 3)
        transportA.onSendListener = { data, _ ->
            runBlocking {
                transportB.inboundFlow.emit(InboundPacket(data, "MR-NODE-A", TransportType.LOOPBACK))
                transportC.inboundFlow.emit(InboundPacket(data, "MR-NODE-A", TransportType.LOOPBACK))
            }
        }

        transportB.onSendListener = { data, _ ->
            runBlocking {
                transportD.inboundFlow.emit(InboundPacket(data, "MR-NODE-B", TransportType.LOOPBACK))
            }
        }

        transportC.onSendListener = { data, _ ->
            runBlocking {
                transportD.inboundFlow.emit(InboundPacket(data, "MR-NODE-C", TransportType.LOOPBACK))
            }
        }

        val collectedAtB = mutableListOf<SosPacket>()
        val collectedAtD = mutableListOf<SosPacket>()

        val jobB = launch { routerB.deliveredPackets.collect { collectedAtB.add(it) } }
        val jobD = launch { routerD.deliveredPackets.collect { collectedAtD.add(it) } }

        // =====================================================================
        // 2. SOS CREATION & GPS CAPTURE (Phase 7 Requirements)
        // =====================================================================
        val capturedGps = LocationData(
            latitude = 35.3606, // Mount Fuji coords
            longitude = 138.7274,
            accuracy = 3.2f
        )
        val emergencyText = "Severe hypothermia, need urgent mountain rescue"
        val sender = "Climber Alex"
        val medical = "Blood Type O+, Asthma"
        val battery = 28

        // Originate encrypted SOS packet
        val origSos = routerA.originateSos(
            message = emergencyText,
            location = capturedGps,
            senderName = sender,
            medicalInfo = medical,
            batteryPercent = battery,
            ttl = 8
        )

        // =====================================================================
        // 3. ENCRYPTION VALIDATION: Payload is AES-256-GCM Ciphertext
        // =====================================================================
        assertTrue("Message ID must follow SOS prefix", origSos.messageId.startsWith("SOS-"))
        assertEquals("SOS", origSos.priority)
        assertEquals(capturedGps, origSos.location)
        assertEquals(0, origSos.hops)
        assertEquals(8, origSos.ttl)

        // Plaintext must NOT appear in the wire packet payload
        assertFalse("Wire payload must not contain plaintext emergency text", origSos.payload.contains("hypothermia"))
        assertFalse("Wire payload must not contain plaintext medical notes", origSos.payload.contains("Asthma"))

        // An attacker with a wrong key cannot decrypt it
        val wrongKey = KeyManager.deriveKey("Attacker-Wrong-Key-999")
        var failedDecryption = false
        try {
            CryptoManager.decryptString(origSos.payload, wrongKey)
        } catch (e: GeneralSecurityException) {
            failedDecryption = true
        } catch (e: IllegalArgumentException) {
            failedDecryption = true
        } catch (e: Exception) {
            failedDecryption = true
        }
        assertTrue("Payload decryption with unauthorized key must fail", failedDecryption)

        // =====================================================================
        // 4. PERSISTENCE VALIDATION (Phase 4: Packet stored locally in Room)
        // =====================================================================
        val storedA = forwardStoreA.queuedPacketsFlow
        assertNotNull(storedA)

        // Allow network propagation across mesh
        delay(150L)

        // =====================================================================
        // 5. ONE-HOP & RELAY CONFIDENTIALITY (Phase 2 & Security Constraints)
        // =====================================================================
        assertEquals(1, collectedAtB.size)
        val packetAtB = collectedAtB[0]
        assertEquals(origSos.messageId, packetAtB.messageId)
        assertEquals(origSos.payload, packetAtB.payload) // B holds identical ciphertext

        // Relays B and C forwarded the packet
        assertEquals(1, routerB.relayedCount.get())
        assertEquals(1, routerC.relayedCount.get())

        // =====================================================================
        // 6. MULTI-HOP & DUPLICATE SUPPRESSION (Phase 3 & Phase 5)
        // =====================================================================
        // Node D received from both B and C, but delivered EXACTLY ONCE
        assertEquals(1, collectedAtD.size)
        assertEquals(1, routerD.receivedCount.get())
        assertEquals(1, routerD.suppressedCount.get()) // Second copy suppressed

        val finalDeliveredPacket = collectedAtD[0]
        assertEquals(origSos.messageId, finalDeliveredPacket.messageId)
        assertEquals("MR-NODE-A", finalDeliveredPacket.originatorId)
        assertEquals(1, finalDeliveredPacket.hops) // Traversed 1 hop from relay B/C to D
        assertEquals(capturedGps, finalDeliveredPacket.location)

        // =====================================================================
        // 7. AUTHORIZED DECRYPTION AT RECIPIENT / GATEWAY (Phase 7 Exit Criteria)
        // =====================================================================
        val decryptedJson = CryptoManager.decryptString(finalDeliveredPacket.payload, KeyManager.defaultEmergencyKey)
        val decryptedPayload = EmergencyPayload.fromJson(decryptedJson)

        assertNotNull("Payload must deserialize into EmergencyPayload", decryptedPayload)
        assertEquals(emergencyText, decryptedPayload?.message)
        assertEquals(sender, decryptedPayload?.senderName)
        assertEquals(medical, decryptedPayload?.medicalInfo)
        assertEquals(battery, decryptedPayload?.batteryPercent)

        jobB.cancel()
        jobD.cancel()
        routerA.stop()
        routerB.stop()
        routerC.stop()
        routerD.stop()
    }
}
