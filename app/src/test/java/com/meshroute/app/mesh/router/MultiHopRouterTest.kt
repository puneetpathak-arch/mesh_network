package com.meshroute.app.mesh.router

import com.meshroute.app.mesh.transport.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FakeTransport(override val transportType: TransportType = TransportType.LOOPBACK) : MeshTransport {
    private val _neighbors = MutableStateFlow<Set<Peer>>(emptySet())
    override val neighbors: StateFlow<Set<Peer>> = _neighbors

    private val _health = MutableStateFlow(TransportHealth.HEALTHY)
    override val health: StateFlow<TransportHealth> = _health

    val inboundFlow = MutableSharedFlow<InboundPacket>(extraBufferCapacity = 64)
    override val inbound: Flow<InboundPacket> = inboundFlow

    val sentPackets = mutableListOf<ByteArray>()
    var onSendListener: ((ByteArray, Peer?) -> Unit)? = null

    override suspend fun start() {}
    override suspend fun stop() {}

    override suspend fun send(data: ByteArray, to: Peer?): Boolean {
        sentPackets.add(data)
        onSendListener?.invoke(data, to)
        return true
    }
}

class MultiHopRouterTest {

    @Test
    fun testMultiHopRelayChainAtoBtoC() = runBlocking {
        // Create 3 Transports and Routers: Node A, Node B, Node C
        val transportA = FakeTransport()
        val routerA = MeshRouter("MR-NODE-A", transportA)

        val transportB = FakeTransport()
        val routerB = MeshRouter("MR-NODE-B", transportB)

        val transportC = FakeTransport()
        val routerC = MeshRouter("MR-NODE-C", transportC)

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

        // 1. Node A originates a packet
        val origPacket = routerA.originate("Emergency Test Packet from A")

        assertEquals("MR-NODE-A", origPacket.originatorId)
        assertEquals(0, origPacket.hopCount)
        assertEquals(listOf("MR-NODE-A"), origPacket.hopPath)

        // 2. Node B should have relayed it
        assertEquals(1, routerB.relayedCount.get())
        assertEquals(1, routerB.receivedCount.get())

        // 3. Node C should have delivered the packet originated by Node A
        val receivedByC = routerC.deliveredPackets.first()

        assertEquals(origPacket.packetId, receivedByC.packetId)
        assertEquals("MR-NODE-A", receivedByC.originatorId)
        assertEquals("MR-NODE-B", receivedByC.senderId)
        assertEquals("Emergency Test Packet from A", receivedByC.message)
        assertEquals(1, receivedByC.hopCount)
        assertEquals(listOf("MR-NODE-A", "MR-NODE-B"), receivedByC.hopPath)

        routerA.stop()
        routerB.stop()
        routerC.stop()
    }
}
