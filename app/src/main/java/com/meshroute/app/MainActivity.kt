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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshroute.app.data.database.AppDatabase
import com.meshroute.app.data.database.entity.QueuedPacketEntity
import com.meshroute.app.data.queue.ForwardStore
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getInstance(applicationContext)
        forwardStore = ForwardStore(database.packetDao())
        seenSet = SeenSet(database.seenMessageDao())
        transport = BleMeshTransport(applicationContext, selfNodeId)
        router = MeshRouter(selfNodeId, transport, forwardStore, seenSet)
        locationProvider = AndroidGpsLocationProvider(applicationContext)

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
                        locationProvider = locationProvider
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
fun MeshRouteSosScreen(
    selfNodeId: String,
    transport: BleMeshTransport,
    router: MeshRouter,
    forwardStore: ForwardStore,
    locationProvider: LocationProvider
) {
    val coroutineScope = rememberCoroutineScope()
    val neighbors by transport.neighbors.collectAsState()
    val health by transport.health.collectAsState()

    val receivedPackets = remember { mutableStateListOf<SosPacket>() }
    val relayEvents = remember { mutableStateListOf<RelayEvent>() }
    val duplicateEvents = remember { mutableStateListOf<DuplicateSuppressedEvent>() }
    val ttlEvents = remember { mutableStateListOf<TtlExhaustedEvent>() }
    val expiredEvents = remember { mutableStateListOf<PacketExpiredEvent>() }

    val storedPackets by forwardStore.queuedPacketsFlow.collectAsState(initial = emptyList())
    val pendingCount by forwardStore.pendingCountFlow.collectAsState(initial = 0)

    var isRunning by remember { mutableStateOf(false) }
    var sosMessageText by remember { mutableStateOf("Injured hiker on North Ridge trail, need immediate evacuation") }
    var senderName by remember { mutableStateOf("Puneet P.") }
    var medicalNotes by remember { mutableStateOf("Sprained ankle, low water") }
    var selectedTtl by remember { mutableIntStateOf(8) } // default 8 hops
    var currentLocation by remember { mutableStateOf<LocationData?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Delivered SOS, 1: TTL & Expiry, 2: Suppressed, 3: Room DB, 4: Relay, 5: Peers

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
                isRunning = true
                currentLocation = locationProvider.getLastKnownLocation()
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
                                color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "Phase 7: Real SOS Packet",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            "Node: $selfNodeId • AES-256 Encrypted • GPS Active",
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
                    title = "Suppressed",
                    count = router.suppressedCount.get(),
                    icon = Icons.Default.FilterAlt,
                    color = Color(0xFFF43F5E),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "TTL Stopped",
                    count = router.ttlExhaustedCount.get(),
                    icon = Icons.Default.Block,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("TTL:", fontSize = 11.sp, color = Color.LightGray)
                        listOf(2, 4, 8, 12).forEach { ttl ->
                            FilterChip(
                                selected = selectedTtl == ttl,
                                onClick = { selectedTtl = ttl },
                                label = { Text("$ttl Hops", fontSize = 11.sp) },
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
                    text = { Text("Delivered SOS (${receivedPackets.size})", fontSize = 11.sp) }
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
                0 -> DeliveredSosList(receivedPackets, selfNodeId)
                1 -> TtlBoundedList(ttlEvents, expiredEvents)
                2 -> SuppressedDuplicatesList(duplicateEvents)
                3 -> RoomStorageList(storedPackets, onClear = {
                    coroutineScope.launch {
                        forwardStore.clearDatabase()
                        router.seenSet.clear()
                    }
                })
                4 -> RelayActivityList(relayEvents, selfNodeId)
                5 -> NeighborsList(neighbors)
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
                // Attempt local decryption using emergency key to display authorized preview
                val decryptedPayload = remember(packet.payload) {
                    runCatching {
                        val json = CryptoManager.decryptString(packet.payload, KeyManager.defaultEmergencyKey)
                        EmergencyPayload.fromJson(json)
                    }.getOrNull()
                }

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

                        // Decrypted Payload or Ciphertext preview
                        if (decryptedPayload != null) {
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                        Text("Decrypted Emergency Content:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                    }
                                    Text("\"${decryptedPayload.message}\"", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    if (decryptedPayload.senderName.isNotEmpty() || decryptedPayload.medicalInfo.isNotEmpty()) {
                                        Text(
                                            "From: ${decryptedPayload.senderName}  •  Notes: ${decryptedPayload.medicalInfo}",
                                            fontSize = 10.sp,
                                            color = Color.LightGray
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                                        Text("Encrypted Ciphertext (Relay Mode):", fontSize = 10.sp, color = Color(0xFFF59E0B))
                                    }
                                    Text(packet.payload.take(48) + "...", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
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
