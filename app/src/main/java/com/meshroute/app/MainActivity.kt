package com.meshroute.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshroute.app.data.database.AppDatabase
import com.meshroute.app.data.database.entity.QueuedPacketEntity
import com.meshroute.app.data.queue.ForwardStore
import com.meshroute.app.gateway.AndroidNetworkMonitor
import com.meshroute.app.gateway.GatewayUploader
import com.meshroute.app.gateway.NetworkMonitor
import com.meshroute.app.location.AndroidGpsLocationProvider
import com.meshroute.app.location.LocationProvider
import com.meshroute.app.mesh.ble.BleMeshTransport
import com.meshroute.app.mesh.router.*
import com.meshroute.app.mesh.transport.*
import com.meshroute.app.security.CryptoManager
import com.meshroute.app.security.EmergencyPayload
import com.meshroute.app.security.KeyManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val selfNodeId = "MR-" + UUID.randomUUID().toString().take(6).uppercase()
    private lateinit var transport: BleMeshTransport
    private lateinit var forwardStore: ForwardStore
    private lateinit var seenSet: SeenSet
    private lateinit var router: MeshRouter
    private lateinit var locationProvider: LocationProvider
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var gatewayUploader: GatewayUploader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getInstance(applicationContext)
        forwardStore = ForwardStore(database.packetDao())
        seenSet = SeenSet(database.seenMessageDao())
        transport = BleMeshTransport(applicationContext, selfNodeId)
        router = MeshRouter(selfNodeId, transport, forwardStore, seenSet)
        locationProvider = AndroidGpsLocationProvider(applicationContext)
        networkMonitor = AndroidNetworkMonitor(applicationContext)
        gatewayUploader = GatewayUploader(forwardStore, networkMonitor)

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
                    MeshRouteSosScreen(
                        selfNodeId = selfNodeId,
                        transport = transport,
                        router = router,
                        forwardStore = forwardStore,
                        locationProvider = locationProvider,
                        gatewayUploader = gatewayUploader,
                        networkMonitor = networkMonitor
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        router.stop()
        gatewayUploader.stop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshRouteSosScreen(
    selfNodeId: String,
    transport: BleMeshTransport,
    router: MeshRouter,
    forwardStore: ForwardStore,
    locationProvider: LocationProvider,
    gatewayUploader: GatewayUploader,
    networkMonitor: NetworkMonitor
) {
    val coroutineScope = rememberCoroutineScope()
    val neighbors by transport.neighbors.collectAsState()
    val health by transport.health.collectAsState()
    val isInternetAvailable by networkMonitor.isInternetAvailable.collectAsState()
    val uploadedCount by forwardStore.uploadedCountFlow.collectAsState(initial = 0)

    val receivedPackets = remember { mutableStateListOf<SosPacket>() }
    val relayEvents = remember { mutableStateListOf<RelayEvent>() }
    val duplicateEvents = remember { mutableStateListOf<DuplicateSuppressedEvent>() }
    val ttlEvents = remember { mutableStateListOf<TtlExhaustedEvent>() }
    val expiredEvents = remember { mutableStateListOf<PacketExpiredEvent>() }
    val gatewayEvents = remember { mutableStateListOf<com.meshroute.app.gateway.GatewayUploadEvent>() }

    val storedPackets by forwardStore.queuedPacketsFlow.collectAsState(initial = emptyList())
    val pendingCount by forwardStore.pendingCountFlow.collectAsState(initial = 0)

    var isRunning by remember { mutableStateOf(false) }
    var sosMessageText by remember { mutableStateOf("Injured hiker on North Ridge trail, need immediate evacuation") }
    var senderName by remember { mutableStateOf("Puneet P.") }
    var medicalNotes by remember { mutableStateOf("Sprained ankle, low water") }
    var selectedTtl by remember { mutableIntStateOf(8) } // default 8 hops
    var currentLocation by remember { mutableStateOf<LocationData?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Delivered SOS, 1: Pipeline, 2: Gateway Uploads, 3: TTL, 4: Suppressed, 5: Room DB, 6: Relay, 7: Peers

    var backendUrlInput by remember { mutableStateOf(gatewayUploader.backendUrl) }
    var showBackendConfig by remember { mutableStateOf(false) }

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
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
                gatewayUploader.start()
                isRunning = true
                currentLocation = locationProvider.getLastKnownLocation()
            }
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(requiredPermissions)
    }

    LaunchedEffect(Unit) {
        router.deliveredPackets.collect { packet ->
            receivedPackets.add(0, packet)
            gatewayUploader.triggerUpload()
        }
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
    LaunchedEffect(Unit) {
        gatewayUploader.uploadEvents.collect { event -> gatewayEvents.add(0, event) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("MeshRoute", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isInternetAvailable) {
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(11.dp))
                                        Text("GATEWAY ONLINE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                    }
                                }
                            } else {
                                Surface(
                                    color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(11.dp))
                                        Text("RELAY (OFFLINE)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                                    }
                                }
                            }
                        }
                        Text(
                            "Node: $selfNodeId • AES-256-GCM • Hop Limit & Auto-Gateway",
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
            // Metrics Summary Bar with SOS Stats
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
                    title = "Relayed",
                    count = router.relayedCount.get(),
                    icon = Icons.AutoMirrored.Filled.AltRoute,
                    color = Color(0xFFFFB74D),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Uploaded",
                    count = uploadedCount,
                    icon = Icons.Default.CloudUpload,
                    color = Color(0xFF34D399),
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
                    title = "Stopped",
                    count = router.ttlExhaustedCount.get(),
                    icon = Icons.Default.Block,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
            }

            // Backend Gateway Target Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                Icons.Default.Dns,
                                contentDescription = null,
                                tint = if (isInternetAvailable) Color(0xFF10B981) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text("Gateway Backend Endpoint", fontSize = 10.sp, color = Color.Gray)
                                Text(gatewayUploader.backendUrl, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { showBackendConfig = !showBackendConfig }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Settings, contentDescription = "Config URL", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            }
                            FilledTonalButton(
                                onClick = { coroutineScope.launch { gatewayUploader.drainQueue() } },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                enabled = isInternetAvailable
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Drain", fontSize = 10.sp)
                            }
                        }
                    }
                    if (showBackendConfig) {
                        OutlinedTextField(
                            value = backendUrlInput,
                            onValueChange = {
                                backendUrlInput = it
                                gatewayUploader.backendUrl = it
                            },
                            label = { Text("Server URL (e.g. http://192.168.1.5:3000)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // Real SOS Origination Card (GPS Capture + AES Encryption)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Emergency, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                            Text("Create Encrypted SOS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(10.dp))
                                Text("AES-256-GCM", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = sosMessageText,
                        onValueChange = { sosMessageText = it },
                        label = { Text("Emergency Situation") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = senderName,
                            onValueChange = { senderName = it },
                            label = { Text("Sender Name") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = medicalNotes,
                            onValueChange = { medicalNotes = it },
                            label = { Text("Medical / Notes") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    // GPS Status & Capture Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (currentLocation != null) Color(0xFF38BDF8) else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            if (currentLocation != null) {
                                Text(
                                    "GPS: %.4f, %.4f (±%.0fm)".format(
                                        currentLocation!!.latitude,
                                        currentLocation!!.longitude,
                                        currentLocation!!.accuracy
                                    ),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF38BDF8)
                                )
                            } else {
                                Text(
                                    "GPS: Acquired at SOS trigger",
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    isFetchingLocation = true
                                    currentLocation = locationProvider.getCurrentLocation(3000L)
                                    isFetchingLocation = false
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            if (isFetchingLocation) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF38BDF8))
                            } else {
                                Icon(Icons.Default.MyLocation, contentDescription = "Refresh GPS", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // TTL Selector Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("TTL:", fontSize = 11.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold)
                        listOf(2, 4, 8, 12).forEach { ttl ->
                            FilterChip(
                                modifier = Modifier.weight(1f),
                                selected = selectedTtl == ttl,
                                onClick = { selectedTtl = ttl },
                                label = {
                                    Text(
                                        "$ttl Hops",
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFEF4444),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val loc = locationProvider.getCurrentLocation(2000L) ?: currentLocation
                                router.originateSos(
                                    message = sosMessageText,
                                    location = loc,
                                    senderName = senderName,
                                    medicalInfo = medicalNotes,
                                    ttl = selectedTtl
                                )
                                gatewayUploader.triggerUpload()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("BROADCAST ENCRYPTED SOS")
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
                    text = { Text("Delivery Flow", fontSize = 11.sp, color = Color(0xFF38BDF8)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Gateway (${gatewayEvents.size})", fontSize = 11.sp, color = Color(0xFF10B981)) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
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
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("Suppressed (${duplicateEvents.size})", fontSize = 11.sp) }
                )
                Tab(
                    selected = selectedTab == 5,
                    onClick = { selectedTab = 5 },
                    text = { Text("Room DB (${storedPackets.size})", fontSize = 11.sp) }
                )
                Tab(
                    selected = selectedTab == 6,
                    onClick = { selectedTab = 6 },
                    text = { Text("Relay (${relayEvents.size})", fontSize = 11.sp) }
                )
                Tab(
                    selected = selectedTab == 7,
                    onClick = { selectedTab = 7 },
                    text = { Text("Peers (${neighbors.size})", fontSize = 11.sp) }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> DeliveredSosList(receivedPackets, selfNodeId)
                1 -> DeliveryFlowView(receivedPackets, isInternetAvailable, uploadedCount)
                2 -> GatewayUploadsList(gatewayEvents)
                3 -> TtlBoundedList(ttlEvents, expiredEvents)
                4 -> SuppressedDuplicatesList(duplicateEvents)
                5 -> RoomStorageList(storedPackets, onClear = {
                    coroutineScope.launch {
                        forwardStore.clearDatabase()
                        router.seenSet.clear()
                    }
                })
                6 -> RelayActivityList(relayEvents, selfNodeId)
                7 -> NeighborsList(neighbors)
            }
        }
    }
}

@Composable
fun DeliveredSosList(
    packets: List<SosPacket>,
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
            Text("No SOS packets delivered yet.", color = Color.DarkGray, fontSize = 13.sp)
        }
    } else {
        val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(packets) { packet ->
                val isMaxHops = packet.hops >= packet.ttl
                var isDecryptedRevealed by remember(packet.messageId) { mutableStateOf(false) }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(0xFFEF4444),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    packet.priority,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = if (isMaxHops) Color(0xFFF59E0B) else Color(0xFF64B5F6),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "HOPS: ${packet.hops}/${packet.ttl}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                packet.messageId,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF93C5FD)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                timeFormat.format(Date(packet.timestamp)),
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }

                        // Payload View: Confidential Ciphertext by default, with Authorized Decrypt Action
                        if (isDecryptedRevealed) {
                            val decryptedPayload = remember(packet.payload) {
                                runCatching {
                                    val json = CryptoManager.decryptString(packet.payload, KeyManager.defaultEmergencyKey)
                                    EmergencyPayload.fromJson(json)
                                }.getOrNull()
                            }

                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                            Text("Decrypted Emergency Content:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                        }
                                        TextButton(
                                            onClick = { isDecryptedRevealed = false },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Text("Hide", fontSize = 10.sp, color = Color.LightGray)
                                        }
                                    }
                                    if (decryptedPayload != null) {
                                        Text("\"${decryptedPayload.message}\"", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        if (decryptedPayload.senderName.isNotEmpty() || decryptedPayload.medicalInfo.isNotEmpty()) {
                                            Text(
                                                "From: ${decryptedPayload.senderName}  •  Notes: ${decryptedPayload.medicalInfo}",
                                                fontSize = 10.sp,
                                                color = Color.LightGray
                                            )
                                        }
                                    } else {
                                        Text("Failed to decrypt payload with key", fontSize = 11.sp, color = Color(0xFFEF4444))
                                    }
                                }
                            }
                        } else {
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                                            Text("Encrypted Ciphertext (Relay Mode):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                                        }
                                        TextButton(
                                            onClick = { isDecryptedRevealed = true },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(11.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Decrypt", fontSize = 10.sp, color = Color(0xFF10B981))
                                        }
                                    }
                                    Text(
                                        packet.payload.take(54) + "...",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }

                        // GPS Location Row
                        packet.location?.let { loc ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                                Text(
                                    "Location: %.5f, %.5f (±%.0fm)".format(loc.latitude, loc.longitude, loc.accuracy),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }

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
