package com.meshroute.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshroute.app.data.database.entity.QueuedPacketEntity
import com.meshroute.app.mesh.router.DuplicateSuppressedEvent
import com.meshroute.app.mesh.router.RelayEvent
import com.meshroute.app.mesh.transport.Peer
import com.meshroute.app.security.CryptoManager
import com.meshroute.app.security.EmergencyPayload
import com.meshroute.app.security.KeyManager
import java.text.SimpleDateFormat
import java.util.*

// ─── MetricCard ─────────────────────────────────────────────────────────────

@Composable
fun MetricCard(
    title: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = count.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = color
            )
            Text(
                text = title,
                fontSize = 9.sp,
                color = Color.LightGray,
                maxLines = 1
            )
        }
    }
}

// ─── SuppressedDuplicatesList ────────────────────────────────────────────────

@Composable
fun SuppressedDuplicatesList(events: List<DuplicateSuppressedEvent>) {
    if (events.isEmpty()) {
        EmptyStateBox("No duplicate packets suppressed yet.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(events) { event ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B2E)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFF43F5E),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "SUPPRESSED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            event.packetId,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFFFCA5A5)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Origin: ${event.originatorId}  •  Duplicate from: ${event.duplicateSenderId}",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Path: ${event.hopPath.joinToString(" ➔ ")}",
                        fontSize = 10.sp,
                        color = Color(0xFFFCA5A5).copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// ─── RoomStorageList ─────────────────────────────────────────────────────────

@Composable
fun RoomStorageList(
    packets: List<QueuedPacketEntity>,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Room DB — ${packets.size} record(s)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = Color.LightGray
            )
            OutlinedButton(
                onClick = onClear,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF43F5E)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear DB", fontSize = 11.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (packets.isEmpty()) {
            EmptyStateBox("No packets persisted to Room DB yet.")
            return
        }

        val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(packets) { entity ->
                val decrypted = remember(entity.payload) {
                    runCatching {
                        val json = CryptoManager.decryptString(entity.payload, KeyManager.defaultEmergencyKey)
                        EmergencyPayload.fromJson(json)
                    }.getOrNull()
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2535)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val statusColor = when (entity.status) {
                                "RELAYED" -> Color(0xFF4CAF50)
                                "DELIVERED" -> Color(0xFF64B5F6)
                                "TTL_EXHAUSTED" -> Color(0xFFF59E0B)
                                "EXPIRED" -> Color(0xFFEC4899)
                                else -> Color.Gray
                            }
                            Surface(
                                color = statusColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    entity.status,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                entity.packetId,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF93C5FD),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                timeFormat.format(Date(entity.persistedAt)),
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }

                        if (decrypted != null) {
                            Text(
                                "\"${decrypted.message}\"",
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                "Payload: ${entity.payload.take(36)}...",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray
                            )
                        }

                        if (entity.latitude != null && entity.longitude != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                                Text(
                                    "GPS: %.4f, %.4f (±%.0fm)".format(entity.latitude, entity.longitude, entity.accuracy ?: 0f),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }

                        Text(
                            "Hops: ${entity.hops}/${entity.ttl}  •  From: ${entity.senderId}",
                            fontSize = 10.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

// ─── RelayActivityList ───────────────────────────────────────────────────────

@Composable
fun RelayActivityList(events: List<RelayEvent>, selfNodeId: String) {
    if (events.isEmpty()) {
        EmptyStateBox("No relay activity yet.")
        return
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(events) { event ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2B1A)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFFFB74D).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "RELAYED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB74D),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            event.packetId,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFFFFD54F),
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            color = Color(0xFF263238),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Hop ${event.incomingHops} ➔ ${event.outgoingHops} / ${event.ttl}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF80CBC4),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Origin: ${event.originatorId}",
                        fontSize = 10.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Path: ${event.hopPath.joinToString(" ➔ ")}",
                        fontSize = 10.sp,
                        color = Color(0xFFFFD54F).copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// ─── NeighborsList ───────────────────────────────────────────────────────────

@Composable
fun NeighborsList(neighbors: Set<Peer>) {
    if (neighbors.isEmpty()) {
        EmptyStateBox("No peers discovered yet.\nMake sure Bluetooth is on and another device is running MeshRoute.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(neighbors.toList()) { peer ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = Color(0xFF0EA5E9).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = Color(0xFF0EA5E9),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            peer.nodeId,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            "${peer.deviceName}  •  ${peer.transportType.name}",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val rssiColor = when {
                            peer.rssi >= -60 -> Color(0xFF4CAF50)
                            peer.rssi >= -80 -> Color(0xFFFFC107)
                            else -> Color(0xFFFF5252)
                        }
                        Text(
                            "${peer.rssi} dBm",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = rssiColor
                        )
                        Icon(
                            Icons.Default.SignalCellularAlt,
                            contentDescription = "RSSI",
                            tint = rssiColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── TtlBoundedList ─────────────────────────────────────────────────────────

@Composable
fun TtlBoundedList(
    ttlEvents: List<com.meshroute.app.mesh.router.TtlExhaustedEvent>,
    expiredEvents: List<com.meshroute.app.mesh.router.PacketExpiredEvent>
) {
    if (ttlEvents.isEmpty() && expiredEvents.isEmpty()) {
        EmptyStateBox("No packets stopped by TTL or clock expiration yet.")
        return
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ttlEvents) { event ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3B2813)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFF59E0B),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "TTL EXHAUSTED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(event.packetId, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFFDE68A))
                        Spacer(modifier = Modifier.weight(1f))
                        Text(timeFormat.format(Date(event.timestamp)), fontSize = 10.sp, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Reached hop limit ${event.currentHops} / ${event.ttl}. Propagation terminated.", fontSize = 11.sp, color = Color(0xFFFEF3C7))
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Hop path: ${event.hopPath.joinToString(" ➔ ")}", fontSize = 10.sp, color = Color(0xFFFCD34D), fontFamily = FontFamily.Monospace)
                }
            }
        }

        items(expiredEvents) { event ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF351A24)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFEC4899),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "CLOCK EXPIRED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(event.packetId, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFFBCFE8))
                        Spacer(modifier = Modifier.weight(1f))
                        Text(timeFormat.format(Date(event.timestamp)), fontSize = 10.sp, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Stale packet dropped (created ${event.createdTimestamp}, expired ${event.expiredTimestamp}).", fontSize = 11.sp, color = Color.LightGray)
                }
            }
        }
    }
}

// ─── Shared empty state ──────────────────────────────────────────────────────

@Composable
private fun EmptyStateBox(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141923)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            color = Color.DarkGray,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
