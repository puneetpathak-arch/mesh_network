package com.meshroute.app.mesh.router

import com.meshroute.app.mesh.transport.Peer
import com.meshroute.app.mesh.transport.SosPacket
import com.meshroute.app.sensor.MobilityState
import kotlin.random.Random

/**
 * MeshRoute Intelligent Emergency Routing (IER)
 * Utilizes the Emergency Delivery Score (EDS) to select high-utility carrier nodes
 * rather than blindly broadcasting packets across the entire mesh.
 */
class EdsRoutingStrategy(
    val weightBattery: Float = 0.20f,
    val weightLink: Float = 0.15f,
    val weightMobility: Float = 0.20f,
    val weightGateway: Float = 0.25f,
    val weightDensity: Float = 0.10f,
    val weightUrgency: Float = 0.10f,
    private val random: Random = Random.Default
) : RoutingStrategy {

    override val type: RoutingStrategyType = RoutingStrategyType.INTELLIGENT_EDS
    override val displayName: String = "MeshRoute IER (Emergency Delivery Score)"
    override val description: String = "Selective multi-factor forwarding targeting high-mobility, gateway-prone, high-battery carriers."

    companion object {
        const val BASE_THRESHOLD = 65
        const val MIN_CRITICAL_THRESHOLD = 20
        const val EXPLORATION_RATE = 0.10f // 10% epsilon exploration
    }

    /**
     * Computes the normalized Emergency Delivery Score (0..100) for a given peer.
     */
    fun computeScore(
        peer: Peer,
        packet: SosPacket,
        neighborCount: Int,
        context: RoutingContext
    ): Int {
        // 1. Battery Score (0..100)
        var batteryScore = peer.batteryLevel.toFloat()
        if (peer.isCharging) {
            batteryScore = (batteryScore + 15f).coerceAtMost(100f)
        } else if (peer.batteryLevel < 15) {
            batteryScore *= 0.4f // Heavily penalize dying nodes to prevent losing in-flight packets
        }

        // 2. Link Quality Score from RSSI (-100 dBm to -40 dBm -> 0..100)
        val clampedRssi = peer.rssi.coerceIn(-100, -40)
        val linkScore = ((clampedRssi + 100) / 60.0f) * 100f

        // 3. Mobility Score (0..100)
        val mobilityState = MobilityState.fromCode(peer.mobilityCode)
        val mobilityScore = mobilityState.scoreMultiplier * 100f

        // 4. Gateway Likelihood Score (0..100)
        val gatewayScore = peer.gatewayLikelihood.coerceIn(0f, 1f) * 100f

        // 5. Density Score (In sparse nets = 100, in dense nets = suppress to prevent storm)
        val densityScore = when {
            neighborCount <= 2 -> 100f
            neighborCount <= 4 -> 75f
            else -> 40f
        }

        // 6. Urgency Score (As TTL drops or packet ages, boost urgency)
        val ageHours = (System.currentTimeMillis() - packet.timestamp) / (1000f * 3600f)
        val ttlDeficit = (8 - packet.ttl).coerceAtLeast(0)
        val urgencyScore = ((ttlDeficit * 10f) + (ageHours * 15f)).coerceIn(0f, 100f)

        val compositeScore = (
            weightBattery * batteryScore +
            weightLink * linkScore +
            weightMobility * mobilityScore +
            weightGateway * gatewayScore +
            weightDensity * densityScore +
            weightUrgency * urgencyScore
        )

        return compositeScore.toInt().coerceIn(0, 100)
    }

    /**
     * Computes the adaptive threshold required for a packet based on its remaining TTL.
     */
    fun calculateAdaptiveThreshold(packet: SosPacket): Int {
        if (packet.ttl <= 1) return MIN_CRITICAL_THRESHOLD
        val ttlHopDecay = (8 - packet.ttl) * 6
        return (BASE_THRESHOLD - ttlHopDecay).coerceAtLeast(MIN_CRITICAL_THRESHOLD)
    }

    override fun evaluateTargets(
        packet: SosPacket,
        neighbors: Set<Peer>,
        context: RoutingContext
    ): List<ForwardingDecision> {
        if (neighbors.isEmpty()) return emptyList()

        val adaptiveThreshold = calculateAdaptiveThreshold(packet)

        val decisions = neighbors.map { peer ->
            val inHopPath = packet.hopPath.contains(peer.nodeId)
            if (inHopPath || peer.nodeId == context.selfNodeId) {
                return@map ForwardingDecision(
                    peer = peer.copy(lastEdsScore = 0),
                    score = 0,
                    reason = if (inHopPath) "Skipped (already in hop path)" else "Skipped (self)",
                    shouldForward = false
                )
            }

            val score = computeScore(peer, packet, neighbors.size, context)
            val updatedPeer = peer.copy(lastEdsScore = score)

            val meetsThreshold = score >= adaptiveThreshold
            val isExploratory = !meetsThreshold && score >= 35 && random.nextFloat() < EXPLORATION_RATE

            val shouldForward = meetsThreshold || isExploratory
            val reason = when {
                meetsThreshold -> "Forwarded (EDS $score >= Threshold $adaptiveThreshold)"
                isExploratory -> "Forwarded via Anti-Starvation Exploration (EDS $score)"
                else -> "Suppressed (EDS $score < Threshold $adaptiveThreshold)"
            }

            ForwardingDecision(
                peer = updatedPeer,
                score = score,
                reason = reason,
                shouldForward = shouldForward
            )
        }

        // Anti-starvation fallback: if eligible peers are present but none met the threshold,
        // forward to the highest-scoring candidate to guarantee off-grid SOS propagation.
        val validDecisions = decisions.filter { !it.shouldForward && !packet.hopPath.contains(it.peer.nodeId) && it.peer.nodeId != context.selfNodeId }
        if (decisions.none { it.shouldForward } && validDecisions.isNotEmpty()) {
            val bestCandidate = validDecisions.maxByOrNull { it.score }
            if (bestCandidate != null) {
                return decisions.map { d ->
                    if (d.peer.nodeId == bestCandidate.peer.nodeId) {
                        d.copy(
                            shouldForward = true,
                            reason = "Forwarded via Anti-Starvation Fallback (Best Available Candidate EDS ${d.score})"
                        )
                    } else d
                }
            }
        }

        return decisions
    }
}
