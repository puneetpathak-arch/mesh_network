package com.meshroute.app.gateway

import com.meshroute.app.data.database.entity.PacketPersistenceStatus
import com.meshroute.app.mesh.router.createMockForwardStore
import com.meshroute.app.mesh.transport.SosPacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FakeNetworkMonitor(initialAvailability: Boolean = false) : NetworkMonitor {
    val availabilityFlow = MutableStateFlow(initialAvailability)
    override val isInternetAvailable: StateFlow<Boolean> = availabilityFlow
    override fun start() {}
    override fun stop() {}
}

class GatewayUploaderTest {

    @Test
    fun testOfflineGatewayRetainsPacketsInQueue() = runBlocking {
        val forwardStore = createMockForwardStore()
        val networkMonitor = FakeNetworkMonitor(initialAvailability = false)
        val uploader = GatewayUploader(forwardStore, networkMonitor, "http://127.0.0.1:9999")

        val packet = SosPacket(
            messageId = "SOS-OFFLINE-01",
            senderId = "MR-NODE-A",
            payload = "TEST_CIPHERTEXT"
        )
        forwardStore.persistInbound(packet)

        val pendingBefore = forwardStore.getPendingUploadPackets()
        assertEquals(1, pendingBefore.size)

        // Offline drain attempt should not mark as uploaded
        uploader.drainQueue()

        val pendingAfter = forwardStore.getPendingUploadPackets()
        assertEquals(1, pendingAfter.size)
        assertEquals("SOS-OFFLINE-01", pendingAfter[0].messageId)
    }

    @Test
    fun testMarkUploadedTransitionsStatusInStore() = runBlocking {
        val forwardStore = createMockForwardStore()
        val packet = SosPacket(
            messageId = "SOS-UPLOAD-02",
            senderId = "MR-GATEWAY",
            payload = "TEST_CIPHERTEXT"
        )
        forwardStore.persistInbound(packet)

        val pendingBefore = forwardStore.getPendingUploadPackets()
        assertEquals(1, pendingBefore.size)

        // Simulate successful backend ACK
        forwardStore.markUploaded("SOS-UPLOAD-02")

        val pendingAfter = forwardStore.getPendingUploadPackets()
        assertEquals(0, pendingAfter.size)

        val uploadedCount = forwardStore.uploadedCountFlow.first()
        assertEquals(1, uploadedCount)
    }
}
