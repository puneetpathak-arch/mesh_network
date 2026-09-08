package com.meshroute.app.mesh.router

import com.meshroute.app.data.database.entity.QueuedPacketEntity
import com.meshroute.app.data.queue.ForwardStore
import com.meshroute.app.mesh.transport.InboundPacket
import com.meshroute.app.mesh.transport.TestPacket
import com.meshroute.app.mesh.transport.TransportType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MockForwardStore : ForwardStore(
    packetDao = object : com.meshroute.app.data.database.dao.PacketDao {
        val stored = mutableListOf<QueuedPacketEntity>()
        override suspend fun insert(packet: QueuedPacketEntity) { stored.add(packet) }
        override suspend fun insertAll(packets: List<QueuedPacketEntity>) { stored.addAll(packets) }
        override suspend fun getById(packetId: String): QueuedPacketEntity? = stored.find { it.packetId == packetId }
        override suspend fun getByStatus(status: String): List<QueuedPacketEntity> = stored.filter { it.status == status }
        override suspend fun getPendingQueuedPackets(): List<QueuedPacketEntity> = stored.filter { it.status == "QUEUED" }
        override fun observeAll() = kotlinx.coroutines.flow.flowOf(stored)
        override fun observePendingCount() = kotlinx.coroutines.flow.flowOf(stored.count { it.status == "QUEUED" })
        override fun observeTotalCount() = kotlinx.coroutines.flow.flowOf(stored.size)
        override suspend fun updateStatus(packetId: String, newStatus: String) {}
        override suspend fun delete(packetId: String) { stored.removeIf { it.packetId == packetId } }
        override suspend fun clearAll() { stored.clear() }
    }
)

class DiamondRoutingTest {

    @Test
    fun testSeenSetBasicDedup() {
        val seenSet = SeenSet(seenMessageDao = null)

        // First sighting
        val isFirst = seenSet.add("PKT-100")
        assertTrue(isFirst)
        assertTrue(seenSet.contains("PKT-100"))

        // Duplicate sighting
        val isDuplicate = seenSet.add("PKT-100")
        assertFalse(isDuplicate)

        // Different packet
        val isDifferent = seenSet.add("PKT-200")
        assertTrue(isDifferent)
    }

    @Test
    fun testDiamondTopologySingleDelivery() = runBlocking {
        // Build 4 nodes: A, B, C, D
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

        val collectedPacketsAtD = mutableListOf<TestPacket>()
        val job = launch {
            routerD.deliveredPackets.collect {
                collectedPacketsAtD.add(it)
            }
        }

        // 1. Node A originates a single packet
        val packetA = routerA.originate("Diamond Multi-Path SOS Broadcast")

        // Allow coroutine dispatches to complete
        kotlinx.coroutines.delay(100L)

        // 2. Node D should have received both copies from B and C
        // but DELIVERED EXACTLY ONCE
        assertEquals(1, collectedPacketsAtD.size)
        assertEquals(packetA.packetId, collectedPacketsAtD[0].packetId)
        assertEquals("MR-NODE-A", collectedPacketsAtD[0].originatorId)

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
