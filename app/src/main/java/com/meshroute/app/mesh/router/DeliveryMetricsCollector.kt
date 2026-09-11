package com.meshroute.app.mesh.router

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class RoutingMetricsSnapshot(
    val strategyName: String,
    val totalTransmissions: Int,
    val duplicatePacketsSuppressed: Int,
    val selectiveSuppressions: Int, // Suppressed by EDS threshold
    val totalHopsCompleted: Int,
    val packetsDeliveredToGateway: Int,
    val averageDeliveryLatencyMs: Long,
    val packetsSavedEstimate: Int,
    val powerReductionPercent: Float
)

/**
 * Real-time metric aggregator for comparing Epidemic Flooding vs. Intelligent EDS Routing.
 */
class DeliveryMetricsCollector {

    val totalTransmissions = AtomicInteger(0)
    val duplicatePacketsSuppressed = AtomicInteger(0)
    val selectiveSuppressions = AtomicInteger(0)
    val totalHopsCompleted = AtomicInteger(0)
    val packetsDeliveredToGateway = AtomicInteger(0)

    private val totalLatencyAccumulator = AtomicLong(0L)
    private val deliveredCountForLatency = AtomicInteger(0)

    private val _metricsFlow = MutableStateFlow(createSnapshot("Initialized"))
    val metricsFlow: StateFlow<RoutingMetricsSnapshot> = _metricsFlow.asStateFlow()

    fun recordTransmission(count: Int = 1) {
        totalTransmissions.addAndGet(count)
        updateSnapshot()
    }

    fun recordDuplicateSuppressed() {
        duplicatePacketsSuppressed.incrementAndGet()
        updateSnapshot()
    }

    fun recordSelectiveSuppression(count: Int = 1) {
        selectiveSuppressions.addAndGet(count)
        updateSnapshot()
    }

    fun recordHopRelayed() {
        totalHopsCompleted.incrementAndGet()
        updateSnapshot()
    }

    fun recordGatewayDelivery(creationTimestamp: Long) {
        packetsDeliveredToGateway.incrementAndGet()
        val latency = (System.currentTimeMillis() - creationTimestamp).coerceAtLeast(0L)
        totalLatencyAccumulator.addAndGet(latency)
        deliveredCountForLatency.incrementAndGet()
        updateSnapshot()
    }

    fun reset() {
        totalTransmissions.set(0)
        duplicatePacketsSuppressed.set(0)
        selectiveSuppressions.set(0)
        totalHopsCompleted.set(0)
        packetsDeliveredToGateway.set(0)
        totalLatencyAccumulator.set(0L)
        deliveredCountForLatency.set(0)
        updateSnapshot()
    }

    fun updateSnapshot(strategyName: String = "Active Strategy") {
        _metricsFlow.value = createSnapshot(strategyName)
    }

    private fun createSnapshot(strategyName: String): RoutingMetricsSnapshot {
        val tx = totalTransmissions.get()
        val suppressed = selectiveSuppressions.get()
        val totalPotential = tx + suppressed

        val saved = suppressed
        val powerReduction = if (totalPotential > 0) {
            (suppressed.toFloat() / totalPotential.toFloat()) * 100f
        } else {
            0f
        }

        val delCount = deliveredCountForLatency.get()
        val avgLatency = if (delCount > 0) totalLatencyAccumulator.get() / delCount else 0L

        return RoutingMetricsSnapshot(
            strategyName = strategyName,
            totalTransmissions = tx,
            duplicatePacketsSuppressed = duplicatePacketsSuppressed.get(),
            selectiveSuppressions = suppressed,
            totalHopsCompleted = totalHopsCompleted.get(),
            packetsDeliveredToGateway = packetsDeliveredToGateway.get(),
            averageDeliveryLatencyMs = avgLatency,
            packetsSavedEstimate = saved,
            powerReductionPercent = powerReduction
        )
    }
}
