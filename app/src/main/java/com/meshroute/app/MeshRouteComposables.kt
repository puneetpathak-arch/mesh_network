package com.meshroute.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshroute.app.data.database.entity.QueuedPacketEntity
import com.meshroute.app.mesh.router.DuplicateSuppressedEvent
import com.meshroute.app.mesh.router.PacketExpiredEvent
import com.meshroute.app.mesh.router.RelayEvent
import com.meshroute.app.mesh.router.TtlExhaustedEvent
import com.meshroute.app.mesh.transport.Peer
import com.meshroute.app.mesh.transport.SosPacket
import com.meshroute.app.security.CryptoManager
import com.meshroute.app.security.EmergencyPayload
import com.meshroute.app.security.KeyManager
import com.meshroute.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// ─── Shared Empty State Box ─────────────────────────────────────────────────

@Composable
fun EmptyStateBox(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceElevated.copy(alpha = 0.5f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ─── Peers / Neighbors List ─────────────────────────────────────────────────

@Composable
fun NeighborsList(neighbors: Set<Peer>) {
    if (neighbors.isEmpty()) {
        EmptyStateBox("No peer devices discovered in range yet.\nMake sure Bluetooth is enabled on nearby devices.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(neighbors.toList()) { peer ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = GpsSkyBlueSubtle,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = GpsSkyBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            peer.nodeId,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                        Text(
                            "${peer.deviceName} • ${peer.transportType.name}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val rssiColor = when {
                            peer.rssi >= -65 -> AccentGreen
                            peer.rssi >= -80 -> WarningAmber
                            else -> EmergencyRed
                        }
                        Text(
                            "${peer.rssi} dBm",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = rssiColor
                        )
                        Icon(
                            Icons.Default.SignalCellularAlt,
                            contentDescription = "Signal Strength",
                            tint = rssiColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Room Database Persisted Packets List ───────────────────────────────────

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
                "Persisted SOS Packets (${packets.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = TextSecondary
            )
            OutlinedButton(
                onClick = onClear,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
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
                var isDecrypted by remember(entity.packetId) { mutableStateOf(false) }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, DarkBorder),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val statusColor = when (entity.status) {
                                "RELAYED" -> AccentGreen
                                "DELIVERED" -> GpsSkyBlue
                                "TTL_EXHAUSTED" -> WarningAmber
                                "EXPIRED" -> EmergencyRed
                                else -> TextSecondary
                            }
                            Surface(
                                color = statusColor.copy(alpha = 0.15f),
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
                                color = GpsSkyBlue,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                timeFormat.format(Date(entity.persistedAt)),
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }

                        if (isDecrypted) {
                            val decrypted = remember(entity.payload) {
                                runCatching {
                                    val json = CryptoManager.decryptString(entity.payload, KeyManager.defaultEmergencyKey)
                                    EmergencyPayload.fromJson(json)
                                }.getOrNull()
                            }
                            Surface(
                                color = DarkBackground,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Decrypted:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                                        TextButton(
                                            onClick = { isDecrypted = false },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                        ) {
                                            Text("Hide", fontSize = 10.sp, color = TextSecondary)
                                        }
                                    }
                                    if (decrypted != null) {
                                        Text(
                                            "\"${decrypted.message}\"",
                                            fontSize = 12.sp,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (decrypted.senderName.isNotEmpty() || decrypted.medicalInfo.isNotEmpty()) {
                                            Text(
                                                "From: ${decrypted.senderName} • Notes: ${decrypted.medicalInfo}",
                                                fontSize = 10.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    } else {
                                        Text("Decryption failed", fontSize = 11.sp, color = EmergencyRed)
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Ciphertext: ${entity.payload.take(28)}...",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted
                                )
                                TextButton(
                                    onClick = { isDecrypted = true },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text("Decrypt", fontSize = 10.sp, color = AccentGreen)
                                }
                            }
                        }

                        if (entity.latitude != null && entity.longitude != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = GpsSkyBlue, modifier = Modifier.size(12.dp))
                                Text(
                                    "GPS: %.4f, %.4f (±%.0fm)".format(entity.latitude, entity.longitude, entity.accuracy ?: 0f),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = GpsSkyBlue
                                )
                            }
                        }

                        Text(
                            "Hops: ${entity.hops}/${entity.ttl} • Sender: ${entity.senderId}",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

// ─── Relay Activity List ────────────────────────────────────────────────────

@Composable
fun RelayActivityList(events: List<RelayEvent>, selfNodeId: String) {
    if (events.isEmpty()) {
        EmptyStateBox("No relay activity recorded yet.")
        return
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(events) { event ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = WarningAmberSubtle,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "RELAYED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarningAmber,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            event.packetId,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            color = DarkSurfaceElevated,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Hop ${event.incomingHops} ➔ ${event.outgoingHops} / ${event.ttl}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentEmerald,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        "Origin: ${event.originatorId}",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Text(
                        "Path: ${event.hopPath.joinToString(" ➔ ")}",
                        fontSize = 10.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// ─── TTL Bounded List ───────────────────────────────────────────────────────

@Composable
fun TtlBoundedList(
    ttlEvents: List<TtlExhaustedEvent>,
    expiredEvents: List<PacketExpiredEvent>
) {
    if (ttlEvents.isEmpty() && expiredEvents.isEmpty()) {
        EmptyStateBox("No packets bounded by TTL or clock expiration yet.")
        return
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ttlEvents) { event ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = WarningAmberSubtle,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "TTL EXHAUSTED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarningAmber,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(event.packetId, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(timeFormat.format(Date(event.timestamp)), fontSize = 10.sp, color = TextMuted)
                    }
                    Text("Reached max hop limit ${event.currentHops} / ${event.ttl}.", fontSize = 11.sp, color = TextSecondary)
                    Text("Path: ${event.hopPath.joinToString(" ➔ ")}", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                }
            }
        }

        items(expiredEvents) { event ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = EmergencyRedSubtle,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "CLOCK EXPIRED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmergencyRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(event.packetId, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(timeFormat.format(Date(event.timestamp)), fontSize = 10.sp, color = TextMuted)
                    }
                    Text("Stale packet dropped (created ${event.createdTimestamp}).", fontSize = 11.sp, color = TextSecondary)
                }
            }
        }
    }
}

// ─── Suppressed Duplicates List ─────────────────────────────────────────────

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
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = EmergencyRedSubtle,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "SUPPRESSED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmergencyRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            event.packetId,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                    }
                    Text(
                        "Origin: ${event.originatorId} • Duplicate from: ${event.duplicateSenderId}",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Text(
                        "Path: ${event.hopPath.joinToString(" ➔ ")}",
                        fontSize = 10.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// ─── Gateway Uploads List ───────────────────────────────────────────────────

@Composable
fun GatewayUploadsList(events: List<com.meshroute.app.gateway.GatewayUploadEvent>) {
    if (events.isEmpty()) {
        EmptyStateBox("No packets uploaded to rescue gateway yet.\nWhen internet is available, queued SOS packets upload automatically.")
        return
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(events) { event ->
            when (event) {
                is com.meshroute.app.gateway.GatewayUploadEvent.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = AccentGreenSubtle,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "UPLOADED (${event.statusCode})",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    event.packetId,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = AccentGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(timeFormat.format(Date(event.timestamp)), fontSize = 10.sp, color = TextMuted)
                            }
                            Text(
                                "Server ACK: ${event.responseBody}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary
                            )
                        }
                    }
                }
                is com.meshroute.app.gateway.GatewayUploadEvent.Failure -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = EmergencyRedSubtle,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "UPLOAD FAILED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmergencyRed,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    event.packetId,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = EmergencyRed,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(timeFormat.format(Date(event.timestamp)), fontSize = 10.sp, color = TextMuted)
                            }
                            Text(
                                "Error: ${event.error}",
                                fontSize = 10.sp,
                                color = EmergencyRed.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}
