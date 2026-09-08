package com.meshroute.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.meshroute.app.data.database.AppDatabase
import com.meshroute.app.data.database.entity.QueuedPacketEntity
import com.meshroute.app.data.queue.ForwardStore
import com.meshroute.app.mesh.ble.BleMeshTransport
import com.meshroute.app.mesh.router.*
import com.meshroute.app.mesh.transport.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val selfNodeId = "MR-" + UUID.randomUUID().toString().take(6).uppercase()
    private lateinit var transport: BleMeshTransport
    private lateinit var forwardStore: ForwardStore
    private lateinit var seenSet: SeenSet
    private lateinit var router: MeshRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getInstance(applicationContext)
        forwardStore = ForwardStore(database.packetDao())
        seenSet = SeenSet(database.seenMessageDao())
        transport = BleMeshTransport(applicationContext, selfNodeId)
        router = MeshRouter(selfNodeId, transport, forwardStore, seenSet)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFF5252),
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B),
                    onPrimary = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MeshRouteTtlScreen(
                        selfNodeId = selfNodeId,
                        transport = transport,
                        router = router,
                        forwardStore = forwardStore
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        router.stop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshRouteTtlScreen(
    selfNodeId: String,
    transport: BleMeshTransport,
    router: MeshRouter,
    forwardStore: ForwardStore
) {
    val coroutineScope = rememberCoroutineScope()
    val neighbors by transport.neighbors.collectAsState()
    val health by transport.health.collectAsState()

    val receivedPackets = remember { mutableStateListOf<TestPacket>() }
    val relayEvents = remember { mutableStateListOf<RelayEvent>() }
    val duplicateEvents = remember { mutableStateListOf<DuplicateSuppressedEvent>() }
    val ttlEvents = remember { mutableStateListOf<TtlExhaustedEvent>() }
    val expiredEvents = remember { mutableStateListOf<PacketExpiredEvent>() }

    val storedPackets by forwardStore.queuedPacketsFlow.collectAsState(initial = emptyList())
    val pendingCount by forwardStore.pendingCountFlow.collectAsState(initial = 0)

    var isRunning by remember { mutableStateOf(false) }
    var packetCounter by remember { mutableIntStateOf(1) }
    var testMessageText by remember { mutableStateOf("Emergency Signal Delta") }
    var selectedTtl by remember { mutableIntStateOf(2) } // default 2 for multi-hop test
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Delivered, 1: TTL & Expiry, 2: Suppressed, 3: Room DB, 4: Relay, 5: Peers

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            coroutineScope.launch {
                transport.start()
                router.start()
                isRunning = true
            }
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(requiredPermissions)
    }

    LaunchedEffect(Unit) {
        router.deliveredPackets.collect { packet -> receivedPackets.add(0, packet) }
    }
    LaunchedEffect(Unit) {
        router.relayEvents.collect { event -> relayEvents.add(0, event) }
    }
    LaunchedEffect(Unit) {
        router.suppressedEvents.collect { event -> duplicateEvents.add(0, event) }
    }
    LaunchedEffect(Unit) {
        router.ttlExhaustedEvents.collect { event -> ttlEvents.add(0, event) }
    }
    LaunchedEffect(Unit) {
        router.expiredEvents.collect { event -> expiredEvents.add(0, event) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("MeshRoute", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "Phase 6: TTL & Expiry",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            "Node: $selfNodeId • Neighbors: ${neighbors.size} • TTL Bounded",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E293B),
                    titleContentColor = Color.White
                ),
                actions = {
                    val statusColor = when (health) {
                        TransportHealth.HEALTHY -> Color(0xFF4CAF50)
                        TransportHealth.DEGRADED -> Color(0xFFFFC107)
                        TransportHealth.UNAVAILABLE -> Color(0xFFFF5252)
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text(health.name, color = statusColor, fontSize = 10.sp) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF0F172A))
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Metrics Summary Bar with TTL & Expiry Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricCard(
                    title = "Delivered",
                    count = router.receivedCount.get(),
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFF81C784),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "TTL Stopped",
                    count = router.ttlExhaustedCount.get(),
                    icon = Icons.Default.Block,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Suppressed",
                    count = router.suppressedCount.get(),
                    icon = Icons.Default.FilterAlt,
                    color = Color(0xFFF43F5E),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Relayed",
                    count = router.relayedCount.get(),
                    icon = Icons.Default.AltRoute,
                    color = Color(0xFFFFB74D),
                    modifier = Modifier.weight(1f)
                )
            }

            // Packet Origination Card with Configurable TTL
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Originate Bounded Packet", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedTextField(
                        value = testMessageText,
                        onValueChange = { testMessageText = it },
                        label = { Text("Emergency Test Message") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // TTL Selector Chips
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Hop Limit (TTL):", fontSize = 11.sp, color = Color.LightGray)
                        listOf(1, 2, 4, 8).forEach { ttl ->
                            FilterChip(
                                selected = selectedTtl == ttl,
                                onClick = { selectedTtl = ttl },
                                label = { Text("$ttl Hops", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFF59E0B),
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                router.originate(
                                    message = "$testMessageText (#${packetCounter++})",
                                    ttl = selectedTtl
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Broadcast with TTL = $selectedTtl")
                    }
                }
            }

            // Tab navigation
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1E293B),
                contentColor = Color.White,
                edgePadding = 0.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Delivered (${receivedPackets.size})", fontSize = 11.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        val count = ttlEvents.size + expiredEvents.size
                        Text(
                            "TTL Bounded ($count)",
                            fontSize = 11.sp,
                            color = if (count > 0) Color(0xFFF59E0B) else Color.White
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Suppressed (${duplicateEvents.size})", fontSize = 11.sp) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Room DB (${storedPackets.size})", fontSize = 11.sp) }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("Relay (${relayEvents.size})", fontSize = 11.sp) }
                )
                Tab(
                    selected = selectedTab == 5,
                    onClick = { selectedTab = 5 },
                    text = { Text("Peers (${neighbors.size})", fontSize = 11.sp) }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> DeliveredPacketsListWithTtl(receivedPackets, selfNodeId)
                1 -> TtlBoundedList(ttlEvents, expiredEvents)
                2 -> SuppressedDuplicatesList(duplicateEvents)
                3 -> RoomStorageList(storedPackets, onClear = {
                    coroutineScope.launch {
                        forwardStore.clearDatabase()
                        seenSet.clear()
                    }
                })
                4 -> RelayActivityList(relayEvents, selfNodeId)
                5 -> NeighborsList(neighbors)
            }
        }
    }
}

@Composable
fun DeliveredPacketsListWithTtl(
    packets: List<TestPacket>,
    selfNodeId: String
) {
    if (packets.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141923)),
            contentAlignment = Alignment.Center
        ) {
            Text("No packets delivered yet.", color = Color.DarkGray, fontSize = 13.sp)
        }
    } else {
        val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(packets) { packet ->
                val isMaxHops = packet.hops >= packet.ttl
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (isMaxHops) Color(0xFFF59E0B) else Color(0xFF64B5F6),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "HOPS: ${packet.hops} / ${packet.ttl}" + (if (isMaxHops) " (MAX)" else ""),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                packet.packetId,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF93C5FD)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                timeFormat.format(Date(packet.timestamp)),
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "\"${packet.message}\"",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Path: " + packet.hopPath.joinToString(" ➔ ") + " ➔ $selfNodeId",
                            fontSize = 10.sp,
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TtlBoundedList(
    ttlEvents: List<TtlExhaustedEvent>,
    expiredEvents: List<PacketExpiredEvent>
) {
    if (ttlEvents.isEmpty() && expiredEvents.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141923)),
            contentAlignment = Alignment.Center
        ) {
            Text("No packets stopped by TTL or clock expiration yet.", color = Color.DarkGray, fontSize = 12.sp)
        }
    } else {
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
}
