package com.meshroute.app.mesh.router

import com.meshroute.app.mesh.transport.Peer
import com.meshroute.app.mesh.transport.SosPacket

data class ForwardingDecision(
    val peer: Peer,
    val score: Int,
    val reason: String,
    val shouldForward: Boolean
)

data class RoutingContext(
    val selfNodeId: String,
    val localBatteryPercent: Int = 100,
    val isGatewayOnline: Boolean = false,
    val localQueueSize: Int = 0
)

enum class RoutingStrategyType {
    EPIDEMIC_FLOODING, // Baseline
    INTELLIGENT_EDS    // MeshRoute Intelligent Emergency Routing (IER)
}

interface RoutingStrategy {
    val type: RoutingStrategyType
    val displayName: String
    val description: String

    /**
     * Evaluates visible neighbor peers and determines which targets should receive the packet.
     */
    fun evaluateTargets(
        packet: SosPacket,
        neighbors: Set<Peer>,
        context: RoutingContext
    ): List<ForwardingDecision>
}
