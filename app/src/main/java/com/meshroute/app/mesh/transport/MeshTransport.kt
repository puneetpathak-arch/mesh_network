package com.meshroute.app.mesh.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Radio-agnostic transport abstraction (inspired by Knit's MeshTransport).
 * Decouples discovery, connection lifecycle, and packet transmission from
 * the routing and SOS pipeline above.
 */
interface MeshTransport {
    val transportType: TransportType

    /** Current directly-reachable neighboring nodes */
    val neighbors: StateFlow<Set<Peer>>

    /** Health state of this radio transport */
    val health: StateFlow<TransportHealth>

    /** Stream of incoming raw packets received over this transport */
    val inbound: Flow<InboundPacket>

    /** Start radio discovery, advertising, and listening */
    suspend fun start()

    /** Stop radio operations and close active links */
    suspend fun stop()

    /**
     * Send packet data to a specific peer or broadcast to all direct neighbors.
     * @param data Raw byte payload
     * @param to Target peer (if null, broadcasts to all currently reachable neighbors)
     * @return true if successfully dispatched over the radio link
     */
    suspend fun send(data: ByteArray, to: Peer? = null): Boolean
}
