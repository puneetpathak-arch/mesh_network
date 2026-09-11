package com.meshroute.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshroute.app.R
import com.meshroute.app.data.database.entity.QueuedPacketEntity
import com.meshroute.app.gateway.GatewayUploadEvent
import com.meshroute.app.gateway.GatewayUploader
import com.meshroute.app.mesh.router.*
import com.meshroute.app.mesh.transport.Peer
import com.meshroute.app.mesh.transport.TransportHealth
import com.meshroute.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun NetworkDetailsScreen(
    selfNodeId: String,
    neighbors: Set<Peer>,
    health: TransportHealth,
    isInternetAvailable: Boolean,
    router: MeshRouter,
    gatewayUploader: GatewayUploader,
    uploadedCount: Int,
    storedPackets: List<QueuedPacketEntity>,
    relayEvents: List<RelayEvent>,
    duplicateEvents: List<DuplicateSuppressedEvent>,
    ttlEvents: List<TtlExhaustedEvent>,
    expiredEvents: List<PacketExpiredEvent>,
    gatewayEvents: List<GatewayUploadEvent>,
    onClearDatabase: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var isDeveloperExpanded by remember { mutableStateOf(false) }
    var backendUrlInput by remember { mutableStateOf(gatewayUploader.backendUrl) }

    // Root Box with nocturnal mountain wallpaper
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.bg_mountains),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Dark atmospheric alpine vignette for high contrast readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xDD040A14),
                            Color(0xB3061020),
                            Color(0xF502060C)
                        )
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // Frosted top app bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0x3310243E),
                        border = BorderStroke(1.dp, Color(0x3338BDF8)),
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onNavigateBack() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Network Details",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Diagnostics, mesh relays & security",
                            fontSize = 11.5.sp,
                            color = Color(0xFFA0B3D6)
                        )
                    }
                }
            }
        ) { innerPadding ->
            // Fully scrollable body container
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.widthIn(max = 480.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                        // ─── 1. Primary Network Status Hero Card ───────────────────
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xE0061224)),
                            border = BorderStroke(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0x6638BDF8),
                                        Color(0x221E3A5F)
                                    )
                                )
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = Color(0x66000000))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Status icon badge
                                    val (statusIcon, statusColor, statusTitle, statusSubtitle) = when {
                                        isInternetAvailable -> Quadruple(
                                            Icons.Default.CloudDone,
                                            Color(0xFF00E5BE),
                                            "Gateway Connected",
                                            "Direct rescue uplink active"
                                        )
                                        neighbors.isNotEmpty() -> Quadruple(
                                            Icons.Default.Hub,
                                            Color(0xFF38BDF8),
                                            "Mesh Network Active",
                                            "${neighbors.size} nearby relay device(s) connected"
                                        )
                                        else -> Quadruple(
                                            Icons.Default.Sensors,
                                            Color(0xFFF59E0B),
                                            "Mesh Searching",
                                            "Scanning for nearby relay peers"
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = statusColor.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = statusIcon,
                                                contentDescription = null,
                                                tint = statusColor,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = statusTitle,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = statusSubtitle,
                                            fontSize = 12.sp,
                                            color = Color(0xFFA0B3D6)
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color(0x2238BDF8))

                                // Quick status pills row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Node ID", fontSize = 10.5.sp, color = Color(0xFF7087A5))
                                        Text(
                                            selfNodeId,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Nearby Peers", fontSize = 10.5.sp, color = Color(0xFF7087A5))
                                        Text(
                                            "${neighbors.size} Online",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (neighbors.isNotEmpty()) Color(0xFF00E676) else Color(0xFFF59E0B)
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Encryption", fontSize = 10.5.sp, color = Color(0xFF7087A5))
                                        Text(
                                            "AES-256-GCM",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF00E5BE)
                                        )
                                    }
                                }
                            }
                        }

                        // ─── 2. Packet Statistics (Clean 2x2 Grid) ─────────────────
                        Text(
                            text = "TRANSMISSION METRICS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA0B3D6),
                            letterSpacing = 1.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatTile(
                                title = "Delivered",
                                count = router.receivedCount.get(),
                                icon = Icons.Default.CheckCircle,
                                color = Color(0xFF00E676),
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                title = "Relayed",
                                count = router.relayedCount.get(),
                                icon = Icons.AutoMirrored.Filled.AltRoute,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatTile(
                                title = "Uploaded",
                                count = uploadedCount,
                                icon = Icons.Default.CloudUpload,
                                color = Color(0xFF00E5BE),
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                title = "Buffered",
                                count = storedPackets.size,
                                icon = Icons.Default.Inventory2,
                                color = Color(0xFFF59E0B),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // ─── 3. Discovered Mesh Peers Section ───────────────────────
                        Text(
                            text = "DISCOVERED PEERS (${neighbors.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA0B3D6),
                            letterSpacing = 1.sp
                        )

                        if (neighbors.isEmpty()) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0x99061224)),
                                border = BorderStroke(1.dp, Color(0x2238BDF8)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sensors,
                                        contentDescription = null,
                                        tint = Color(0xFF7087A5),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "No peer devices in range yet.\nNearby MeshRoute nodes will automatically appear here.",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFFA0B3D6)
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                neighbors.forEach { peer ->
                                    PeerRowCard(peer = peer)
                                }
                            }
                        }

                        // ─── 4. Device & Telemetry Details Card ────────────────────
                        Text(
                            text = "SYSTEM & HARDWARE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA0B3D6),
                            letterSpacing = 1.sp
                        )

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xCC061224)),
                            border = BorderStroke(1.dp, Color(0x2638BDF8)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DetailRow(
                                    label = "BLE Mesh Transceiver",
                                    value = health.name,
                                    valueColor = when (health) {
                                        TransportHealth.HEALTHY -> Color(0xFF00E676)
                                        TransportHealth.DEGRADED -> Color(0xFFF59E0B)
                                        TransportHealth.UNAVAILABLE -> Color(0xFFFF453A)
                                    }
                                )
                                HorizontalDivider(color = Color(0x1438BDF8))
                                DetailRow(
                                    label = "Routing Algorithm",
                                    value = "Intelligent EDS (Multi-Hop)",
                                    valueColor = Color(0xFF38BDF8)
                                )
                                HorizontalDivider(color = Color(0x1438BDF8))
                                DetailRow(
                                    label = "Security Protocol",
                                    value = "End-to-End Encrypted (AES-GCM)",
                                    valueColor = Color(0xFF00E5BE)
                                )
                                HorizontalDivider(color = Color(0x1438BDF8))
                                DetailRow(
                                    label = "Local Storage Queue",
                                    value = "${storedPackets.size} packets cached",
                                    valueColor = Color.White
                                )
                            }
                        }

                        // ─── 5. Developer & Maintenance (Collapsible) ───────────────
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0x99061224)),
                            border = BorderStroke(1.dp, Color(0x2238BDF8)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isDeveloperExpanded = !isDeveloperExpanded },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            tint = Color(0xFF7087A5),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Advanced & Diagnostics",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isDeveloperExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = Color(0xFFA0B3D6),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                AnimatedVisibility(
                                    visible = isDeveloperExpanded,
                                    enter = expandVertically(),
                                    exit = shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(top = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Rescue Gateway URL:",
                                            fontSize = 11.sp,
                                            color = Color(0xFFA0B3D6)
                                        )

                                        OutlinedTextField(
                                            value = backendUrlInput,
                                            onValueChange = {
                                                backendUrlInput = it
                                                gatewayUploader.backendUrl = it
                                            },
                                            singleLine = true,
                                            textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF00E5BE),
                                                unfocusedBorderColor = Color(0x3338BDF8),
                                                focusedContainerColor = Color(0x99040B16),
                                                unfocusedContainerColor = Color(0x99040B16)
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        gatewayUploader.drainQueue()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0x3300E5BE),
                                                    contentColor = Color(0xFF00E5BE)
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Drain Queue", fontSize = 11.sp)
                                            }

                                            Button(
                                                onClick = onClearDatabase,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0x33FF453A),
                                                    contentColor = Color(0xFFFF453A)
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Clear Cache", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * 2x2 Metric Tile Component.
 */
@Composable
private fun StatTile(
    title: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC061224)),
        border = BorderStroke(1.dp, Color(0x2638BDF8)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column {
                Text(
                    text = count.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = Color(0xFFA0B3D6)
                )
            }
        }
    }
}

/**
 * Connected Peer Row.
 */
@Composable
private fun PeerRowCard(peer: Peer) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC061224)),
        border = BorderStroke(1.dp, Color(0x2638BDF8)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0x3338BDF8),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = peer.nodeId,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (peer.lastEdsScore >= 60) Color(0x3300E676) else Color(0x33F59E0B)
                    ) {
                        Text(
                            text = "EDS: ${peer.lastEdsScore}",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (peer.lastEdsScore >= 60) Color(0xFF00E676) else Color(0xFFF59E0B),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Battery: ${peer.batteryLevel}% • Uplink: ${(peer.gatewayLikelihood * 100).toInt()}%",
                    fontSize = 10.5.sp,
                    color = Color(0xFFA0B3D6)
                )
            }

            val rssiColor = when {
                peer.rssi >= -65 -> Color(0xFF00E676)
                peer.rssi >= -80 -> Color(0xFFF59E0B)
                else -> Color(0xFFFF453A)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${peer.rssi} dBm",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = rssiColor
                )
                Icon(
                    imageVector = Icons.Default.SignalCellularAlt,
                    contentDescription = null,
                    tint = rssiColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Key-value detail row.
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.5.sp, color = Color(0xFFA0B3D6))
        Text(text = value, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
