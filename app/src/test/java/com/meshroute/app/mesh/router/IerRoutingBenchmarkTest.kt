package com.meshroute.app.mesh.router

import com.meshroute.app.mesh.ble.NodeTelemetryBeacon
import com.meshroute.app.mesh.transport.Peer
import com.meshroute.app.mesh.transport.SosPacket
import com.meshroute.app.mesh.transport.TransportType
import com.meshroute.app.sensor.MobilityState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class IerRoutingBenchmarkTest {

    private fun createTestPeer(
        nodeId: String,
        battery: Int = 80,
        isCharging: Boolean = false,
        rssi: Int = -60,
        mobility: MobilityState = MobilityState.WALKING,
        gatewayLikelihood: Float = 0.5f
    ): Peer {
        return Peer(
            nodeId = nodeId,
            deviceName = "Phone-$nodeId",
            transportType = TransportType.BLE,
            rssi = rssi,
            batteryLevel = battery,
            isCharging = isCharging,
            mobilityCode = mobility.code,
            gatewayLikelihood = gatewayLikelihood,
            queueLoad = 0
        )
    }

    private fun createTestPacket(ttl: Int = 8, hops: Int = 0): SosPacket {
        val now = System.currentTimeMillis()
        return SosPacket(
            messageId = "SOS-TEST-001",
            senderId = "NODE-ORIGIN",
            originatorId = "NODE-ORIGIN",
            priority = SosPacket.PRIORITY_SOS,
            payload = "TEST_CIPHERTEXT",
            ttl = ttl,
            hops = hops,
            hopPath = listOf("NODE-ORIGIN"),
            timestamp = now,
            expiresAt = now + 86400000L
        )
    }

    @Test
    fun testBeaconSerializationAndDeserialization() {
        val original = NodeTelemetryBeacon(
            batteryPercent = 84,
            isCharging = true,
            mobilityState = MobilityState.HIGH_SPEED,
            gatewayLikelihoodPercent = 90,
            queueLoad = 3
        )

        val bytes = original.toByteArray()
        assertEquals(4, bytes.size)

        val parsed = NodeTelemetryBeacon.fromByteArray(bytes)
        assertNotNull(parsed)
        assertEquals(84, parsed?.batteryPercent)
        assertTrue(parsed?.isCharging == true)
        assertEquals(MobilityState.HIGH_SPEED, parsed?.mobilityState)
        assertEquals(90, parsed?.gatewayLikelihoodPercent)
        assertEquals(3, parsed?.queueLoad)
    }

    @Test
    fun testEdsScoringDifferentiatesHighVsLowUtilityCarriers() {
        val eds = EdsRoutingStrategy(random = Random(42))
        val packet = createTestPacket(ttl = 8)
        val context = RoutingContext(selfNodeId = "NODE-A")

        val highUtilityPeer = createTestPeer(
            nodeId = "NODE-HIGH",
            battery = 95,
            isCharging = true,
            rssi = -45,
            mobility = MobilityState.HIGH_SPEED,
            gatewayLikelihood = 0.9f
        )

        val lowUtilityPeer = createTestPeer(
            nodeId = "NODE-LOW",
            battery = 8, // Dying battery
            isCharging = false,
            rssi = -92,
            mobility = MobilityState.STATIONARY,
            gatewayLikelihood = 0.05f
        )

        val highScore = eds.computeScore(highUtilityPeer, packet, neighborCount = 4, context)
        val lowScore = eds.computeScore(lowUtilityPeer, packet, neighborCount = 4, context)

        assertTrue("High utility node score ($highScore) should exceed 80", highScore >= 80)
        assertTrue("Low utility node score ($lowScore) should be under 40", lowScore <= 40)
    }

    @Test
    fun testDenseClusterReducesTransmissionsComparedToEpidemicFlooding() = runTest {
        val epidemic = EpidemicRoutingStrategy()
        val deterministicEds = EdsRoutingStrategy(
            weightBattery = 0.25f,
            weightLink = 0.15f,
            weightMobility = 0.20f,
            weightGateway = 0.25f,
            weightDensity = 0.10f,
            weightUrgency = 0.05f,
            random = Random(12345) // Zero exploration seed
        )

        val packet = createTestPacket(ttl = 8)
        val context = RoutingContext(selfNodeId = "NODE-ORIGIN")

        // 6-node dense cluster with varied qualities
        val neighbors = setOf(
            createTestPeer("NODE-B1", battery = 90, rssi = -50, mobility = MobilityState.HIGH_SPEED, gatewayLikelihood = 0.85f), // Top
            createTestPeer("NODE-B2", battery = 85, rssi = -55, mobility = MobilityState.WALKING, gatewayLikelihood = 0.75f),    // Good
            createTestPeer("NODE-B3", battery = 40, rssi = -80, mobility = MobilityState.STATIONARY, gatewayLikelihood = 0.20f), // Weak
            createTestPeer("NODE-B4", battery = 12, rssi = -88, mobility = MobilityState.STATIONARY, gatewayLikelihood = 0.10f), // Dying
            createTestPeer("NODE-B5", battery = 35, rssi = -85, mobility = MobilityState.STATIONARY, gatewayLikelihood = 0.15f), // Weak
            createTestPeer("NODE-B6", battery = 10, rssi = -90, mobility = MobilityState.STATIONARY, gatewayLikelihood = 0.05f)  // Dying
        )

        val epidemicDecisions = epidemic.evaluateTargets(packet, neighbors, context)
        val edsDecisions = deterministicEds.evaluateTargets(packet, neighbors, context)

        val epidemicForwardCount = epidemicDecisions.count { it.shouldForward }
        val edsForwardCount = edsDecisions.count { it.shouldForward }

        assertEquals("Epidemic flooding should forward to 100% of neighbors (6/6)", 6, epidemicForwardCount)
        assertTrue("EDS should selectively forward to top carriers (<= 3/6)", edsForwardCount <= 3)

        val reduction = ((epidemicForwardCount - edsForwardCount).toFloat() / epidemicForwardCount.toFloat()) * 100f
        assertTrue("EDS should achieve >= 50% reduction in unnecessary transmissions in dense clusters (Actual: $reduction%)", reduction >= 50f)
    }

    @Test
    fun testLowTtlPerilRelaxesThresholdToGuaranteeDelivery() = runTest {
        val eds = EdsRoutingStrategy()
        val freshPacket = createTestPacket(ttl = 8)
        val perilPacket = createTestPacket(ttl = 1) // 1 hop remaining before death

        val freshThreshold = eds.calculateAdaptiveThreshold(freshPacket)
        val perilThreshold = eds.calculateAdaptiveThreshold(perilPacket)

        assertEquals("Fresh packet threshold should be strict (65)", 65, freshThreshold)
        assertEquals("Peril packet threshold should relax to minimum (20)", 20, perilThreshold)
        assertTrue("Peril threshold must be substantially lower than fresh threshold", perilThreshold < freshThreshold)
    }

    @Test
    fun testDeliveryMetricsCollectorComputesPowerReductionAccurately() {
        val collector = DeliveryMetricsCollector()

        // 10 transmissions, 10 selective suppressions -> 50% power reduction estimate
        collector.recordTransmission(10)
        collector.recordSelectiveSuppression(10)
        collector.recordDuplicateSuppressed()

        val snapshot = collector.metricsFlow.value
        assertEquals(10, snapshot.totalTransmissions)
        assertEquals(10, snapshot.selectiveSuppressions)
        assertEquals(1, snapshot.duplicatePacketsSuppressed)
        assertEquals(50.0f, snapshot.powerReductionPercent, 0.01f)
    }
}
