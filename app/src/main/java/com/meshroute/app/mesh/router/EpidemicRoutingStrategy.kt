package com.meshroute.app.mesh.router

import com.meshroute.app.mesh.transport.Peer
import com.meshroute.app.mesh.transport.SosPacket

/**
 * Baseline: Epidemic Flooding
 * Opportunistically forwards inbound packets to ALL visible neighbors (excluding nodes in hop_path).
 */
class EpidemicRoutingStrategy : RoutingStrategy {

    override val type: RoutingStrategyType = RoutingStrategyType.EPIDEMIC_FLOODING
    override val displayName: String = "Baseline: Epidemic Flooding"
    override val description: String = "Broadcasts SOS packets to all visible peers without filtering."

    override fun evaluateTargets(
        packet: SosPacket,
        neighbors: Set<Peer>,
        context: RoutingContext
    ): List<ForwardingDecision> {
        return neighbors.map { peer ->
            val inHopPath = packet.hopPath.contains(peer.nodeId)
            val shouldForward = !inHopPath && peer.nodeId != context.selfNodeId
            val reason = when {
                inHopPath -> "Skipped (already in hop path)"
                peer.nodeId == context.selfNodeId -> "Skipped (self)"
                else -> "Forwarding to all visible peers (Epidemic)"
            }
            ForwardingDecision(
                peer = peer,
                score = 100, // Static score for epidemic
                reason = reason,
                shouldForward = shouldForward
            )
        }
    }
}
