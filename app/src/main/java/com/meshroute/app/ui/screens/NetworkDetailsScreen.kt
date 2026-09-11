package com.meshroute.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshroute.app.*
import com.meshroute.app.data.database.entity.QueuedPacketEntity
import com.meshroute.app.gateway.GatewayUploadEvent
import com.meshroute.app.gateway.GatewayUploader
import com.meshroute.app.mesh.router.*
import com.meshroute.app.mesh.transport.Peer
import com.meshroute.app.mesh.transport.TransportHealth
import com.meshroute.app.ui.components.MetricCard
import com.meshroute.app.ui.theme.*
import kotlinx.coroutines.launch

import com.meshroute.app.ui.components.RoutingBenchmarkCard

@OptIn(ExperimentalMaterial3Api::class)
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
    var isDeveloperModeEnabled by remember { mutableStateOf(false) }
    var backendUrlInput by remember { mutableStateOf(gatewayUploader.backendUrl) }
    var selectedDiagnosticTab by remember { mutableIntStateOf(0) }
    val metricsSnapshot by router.metricsCollector.metricsFlow.collectAsState()
    var currentStrategyType by remember { mutableStateOf(router.routingStrategy.type) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Network Details",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Diagnostics, Peers & Transport Telemetry",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ─── Section 0: Routing Strategy & Telemetry Benchmark ─────────────────
            RoutingBenchmarkCard(
                metrics = metricsSnapshot,
                currentStrategy = currentStrategyType,
                onSelectStrategy = { newType ->
                    currentStrategyType = newType
                    router.routingStrategy = when (newType) {
                        RoutingStrategyType.EPIDEMIC_FLOODING -> EpidemicRoutingStrategy()
                        RoutingStrategyType.INTELLIGENT_EDS -> EdsRoutingStrategy()
                    }
                }
            )

            // ─── Section 1: Packet Statistics (5 counters) ──────────────────────────
            Text(
                text = "PACKET STATISTICS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricCard(
                    title = "Delivered",
                    count = router.receivedCount.get(),
                    icon = Icons.Default.CheckCircle,
                    color = AccentGreen,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Relayed",
                    count = router.relayedCount.get(),
                    icon = Icons.AutoMirrored.Filled.AltRoute,
                    color = WarningAmber,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Uploaded",
                    count = uploadedCount,
                    icon = Icons.Default.CloudUpload,
                    color = AccentEmerald,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Suppressed",
                    count = router.suppressedCount.get(),
                    icon = Icons.Default.FilterAlt,
                    color = EmergencyRed,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Stopped",
                    count = router.ttlExhaustedCount.get(),
                    icon = Icons.Default.Block,
                    color = WarningAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            // ─── Section 2: Node & Security Telemetry Card ───────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Node Identity", fontSize = 11.sp, color = TextSecondary)
                        Text(
                            selfNodeId,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = GpsSkyBlue
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Encryption", fontSize = 11.sp, color = TextSecondary)
                        Text("AES-256-GCM (End-to-End)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentGreen)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("BLE Mesh Transceiver", fontSize = 11.sp, color = TextSecondary)
                        val statusColor = when (health) {
                            TransportHealth.HEALTHY -> AccentGreen
                            TransportHealth.DEGRADED -> WarningAmber
                            TransportHealth.UNAVAILABLE -> EmergencyRed
                        }
                        Text(health.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Gateway Reachability", fontSize = 11.sp, color = TextSecondary)
                        Text(
                            if (isInternetAvailable) "Connected (Rescue Uplink Ready)" else "Offline (Mesh Relay Only)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isInternetAvailable) AccentGreen else WarningAmber
                        )
                    }
                }
            }

            // ─── Section 3: Developer & Diagnostics Mode Toggle ─────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackgroundSecondary),
                border = BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                tint = AccentEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    "Developer Mode",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    "Configure custom endpoints and inspect raw packet internals",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = isDeveloperModeEnabled,
                            onCheckedChange = { isDeveloperModeEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AccentEmerald,
                                checkedTrackColor = AccentGreenSubtle
                            )
                        )
                    }

                    // Developer Settings (Gated)
                    if (isDeveloperModeEnabled) {
                        HorizontalDivider(color = DarkBorder)
                        Text(
                            "Gateway Backend Endpoint:",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = backendUrlInput,
                            onValueChange = {
                                backendUrlInput = it
                                gatewayUploader.backendUrl = it
                            },
                            label = { Text("Server URL", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentEmerald,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = DarkBackground,
                                unfocusedContainerColor = DarkBackground
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            FilledTonalButton(
                                onClick = { coroutineScope.launch { gatewayUploader.drainQueue() } },
                                enabled = isInternetAvailable,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Drain Upload Queue", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // ─── Section 4: Diagnostic Tabs & Detailed Lists ────────────────────────
            ScrollableTabRow(
                selectedTabIndex = selectedDiagnosticTab,
                containerColor = DarkSurface,
                contentColor = TextPrimary,
                edgePadding = 0.dp
            ) {
                Tab(
                    selected = selectedDiagnosticTab == 0,
                    onClick = { selectedDiagnosticTab = 0 },
                    text = { Text("Peers (${neighbors.size})", fontSize = 11.sp) }
                )
                Tab(
                    selected = selectedDiagnosticTab == 1,
                    onClick = { selectedDiagnosticTab = 1 },
                    text = { Text("Room DB (${storedPackets.size})", fontSize = 11.sp) }
                )
                Tab(
                    selected = selectedDiagnosticTab == 2,
                    onClick = { selectedDiagnosticTab = 2 },
                    text = { Text("Relay (${relayEvents.size})", fontSize = 11.sp) }
                )
                Tab(
                    selected = selectedDiagnosticTab == 3,
                    onClick = { selectedDiagnosticTab = 3 },
                    text = { Text("Gateway (${gatewayEvents.size})", fontSize = 11.sp) }
                )
                Tab(
                    selected = selectedDiagnosticTab == 4,
                    onClick = { selectedDiagnosticTab = 4 },
                    text = { Text("Suppressed (${duplicateEvents.size})", fontSize = 11.sp) }
                )
                Tab(
                    selected = selectedDiagnosticTab == 5,
                    onClick = { selectedDiagnosticTab = 5 },
                    text = { Text("TTL Drops (${ttlEvents.size + expiredEvents.size})", fontSize = 11.sp) }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedDiagnosticTab) {
                    0 -> NeighborsList(neighbors)
                    1 -> RoomStorageList(storedPackets, onClear = onClearDatabase)
                    2 -> RelayActivityList(relayEvents, selfNodeId)
                    3 -> GatewayUploadsList(gatewayEvents)
                    4 -> SuppressedDuplicatesList(duplicateEvents)
                    5 -> TtlBoundedList(ttlEvents, expiredEvents)
                }
            }
        }
    }
}
