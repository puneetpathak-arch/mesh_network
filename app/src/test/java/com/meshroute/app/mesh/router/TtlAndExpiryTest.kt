package com.meshroute.app.mesh.router

import com.meshroute.app.mesh.transport.InboundPacket
import com.meshroute.app.mesh.transport.TestPacket
import com.meshroute.app.mesh.transport.TransportType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TtlAndExpiryTest {

    @Test
    fun testLowTtlStopsAtHopOne() = runBlocking {
        // Topology: A -> B -> C
        val transportA = FakeTransport()
        val transportB = FakeTransport()
        val transportC = FakeTransport()

        val routerA = MeshRouter("MR-NODE-A", transportA, MockForwardStore(), SeenSet())
        val routerB = MeshRouter("MR-NODE-B", transportB, MockForwardStore(), SeenSet())
        val routerC = MeshRouter("MR-NODE-C", transportC, MockForwardStore(), SeenSet())

        routerA.start()
        routerB.start()
        routerC.start()

        // A -> B
        transportA.onSendListener = { data, _ ->
            runBlocking {
                transportB.inboundFlow.emit(InboundPacket(data, "MR-NODE-A", TransportType.LOOPBACK))
            }
        }

        // B -> C (if B attempts to send)
        transportB.onSendListener = { data, _ ->
            runBlocking {
                transportC.inboundFlow.emit(InboundPacket(data, "MR-NODE-B", TransportType.LOOPBACK))
            }
        }

        val collectedAtB = mutableListOf<TestPacket>()
        val collectedAtC = mutableListOf<TestPacket>()

        val jobB = launch { routerB.deliveredPackets.collect { collectedAtB.add(it) } }
        val jobC = launch { routerC.deliveredPackets.collect { collectedAtC.add(it) } }

        // Node A originates with TTL = 1
        routerA.originate(message = "Single-Hop Restricted SOS", ttl = 1)

        delay(100L)

        // 1. Node B received and delivered locally
        assertEquals(1, collectedAtB.size)
        assertEquals(1, collectedAtB[0].hops)
        assertEquals(1, collectedAtB[0].ttl)

        // 2. Node B recognized TTL limit reached and HALTED relay
        assertEquals(1, routerB.ttlExhaustedCount.get())
        assertEquals(0, routerB.relayedCount.get()) // Did not relay to C

        // 3. Node C NEVER received the packet
        assertEquals(0, collectedAtC.size)
        assertEquals(0, routerC.receivedCount.get())

        jobB.cancel()
        jobC.cancel()
        routerA.stop()
        routerB.stop()
        routerC.stop()
    }

    @Test
    fun testMultiHopTtlTerminationAtHopTwo() = runBlocking {
        // Topology: A -> B -> C -> D
        val transportA = FakeTransport()
        val transportB = FakeTransport()
        val transportC = FakeTransport()
        val transportD = FakeTransport()

        val routerA = MeshRouter("MR-NODE-A", transportA, MockForwardStore(), SeenSet())
        val routerB = MeshRouter("MR-NODE-B", transportB, MockForwardStore(), SeenSet())
        val routerC = MeshRouter("MR-NODE-C", transportC, MockForwardStore(), SeenSet())
        val routerD = MeshRouter("MR-NODE-D", transportD, MockForwardStore(), SeenSet())

        routerA.start()
        routerB.start()
        routerC.start()
        routerD.start()

        transportA.onSendListener = { data, _ ->
            runBlocking { transportB.inboundFlow.emit(InboundPacket(data, "MR-NODE-A", TransportType.LOOPBACK)) }
        }
        transportB.onSendListener = { data, _ ->
            runBlocking { transportC.inboundFlow.emit(InboundPacket(data, "MR-NODE-B", TransportType.LOOPBACK)) }
        }
        transportC.onSendListener = { data, _ ->
            runBlocking { transportD.inboundFlow.emit(InboundPacket(data, "MR-NODE-C", TransportType.LOOPBACK)) }
        }

        val collectedAtC = mutableListOf<TestPacket>()
        val collectedAtD = mutableListOf<TestPacket>()

        val jobC = launch { routerC.deliveredPackets.collect { collectedAtC.add(it) } }
        val jobD = launch { routerD.deliveredPackets.collect { collectedAtD.add(it) } }

        // Node A originates with TTL = 2
        routerA.originate(message = "Two-Hop Restricted SOS", ttl = 2)

        delay(100L)

        // Node B relayed to Node C (hops: 1 -> 2)
        assertEquals(1, routerB.relayedCount.get())

        // Node C delivered locally (hops: 2 / 2)
        assertEquals(1, collectedAtC.size)
        assertEquals(2, collectedAtC[0].hops)
        assertEquals(1, routerC.ttlExhaustedCount.get())
        assertEquals(0, routerC.relayedCount.get()) // C did NOT forward to D

        // Node D received nothing
        assertEquals(0, collectedAtD.size)
        assertEquals(0, routerD.receivedCount.get())

        jobC.cancel()
        jobD.cancel()
        routerA.stop()
        routerB.stop()
        routerC.stop()
        routerD.stop()
    }

    @Test
    fun testTimeBasedExpirationDropsStalePacket() = runBlocking {
        val transportA = FakeTransport()
        val transportB = FakeTransport()

        val routerA = MeshRouter("MR-NODE-A", transportA, MockForwardStore(), SeenSet())
        val routerB = MeshRouter("MR-NODE-B", transportB, MockForwardStore(), SeenSet())

        routerA.start()
        routerB.start()

        transportA.onSendListener = { data, _ ->
            runBlocking { transportB.inboundFlow.emit(InboundPacket(data, "MR-NODE-A", TransportType.LOOPBACK)) }
        }

        val collectedAtB = mutableListOf<TestPacket>()
        val jobB = launch { routerB.deliveredPackets.collect { collectedAtB.add(it) } }

        // Originate packet with negative lifetime (already expired)
        routerA.originate(message = "Stale Packet", ttl = 8, lifetimeMs = -5000L)

        delay(100L)

        // Node B should drop it under time expiration
        assertEquals(1, routerB.expiredCount.get())
        assertEquals(0, collectedAtB.size)
        assertEquals(0, routerB.receivedCount.get())

        jobB.cancel()
        routerA.stop()
        routerB.stop()
    }
}
