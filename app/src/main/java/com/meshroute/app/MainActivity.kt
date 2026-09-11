package com.meshroute.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.meshroute.app.data.database.AppDatabase
import com.meshroute.app.data.queue.ForwardStore
import com.meshroute.app.gateway.AndroidNetworkMonitor
import com.meshroute.app.gateway.GatewayUploadEvent
import com.meshroute.app.gateway.GatewayUploader
import com.meshroute.app.gateway.NetworkMonitor
import com.meshroute.app.location.AndroidGpsLocationProvider
import com.meshroute.app.location.LocationProvider
import com.meshroute.app.mesh.ble.BleMeshTransport
import com.meshroute.app.mesh.router.*
import com.meshroute.app.mesh.transport.LocationData
import com.meshroute.app.mesh.transport.SosPacket
import com.meshroute.app.service.SosNotificationHelper
import com.meshroute.app.ui.screens.MainSosScreen
import com.meshroute.app.ui.screens.NetworkDetailsScreen
import com.meshroute.app.ui.theme.DarkBackground
import com.meshroute.app.ui.theme.MeshRouteTheme
import kotlinx.coroutines.launch
import java.util.*

enum class AppScreen {
    MAIN,
    NETWORK_DETAILS
}

class MainActivity : ComponentActivity() {

    private lateinit var selfNodeId: String
    private lateinit var transport: BleMeshTransport
    private lateinit var forwardStore: ForwardStore
    private lateinit var seenSet: SeenSet
    private lateinit var router: MeshRouter
    private lateinit var locationProvider: LocationProvider
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var gatewayUploader: GatewayUploader

    private fun getOrCreateSelfNodeId(): String {
        val prefs = getSharedPreferences("meshroute_prefs", MODE_PRIVATE)
        var nodeId = prefs.getString("self_node_id", null)
        if (nodeId.isNullOrBlank()) {
            nodeId = "MR-" + UUID.randomUUID().toString().take(6).uppercase()
            prefs.edit().putString("self_node_id", nodeId).apply()
        }
        return nodeId
    }

    private lateinit var mobilityEstimator: com.meshroute.app.sensor.MobilityEstimator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selfNodeId = getOrCreateSelfNodeId()
        val database = AppDatabase.getInstance(applicationContext)
        forwardStore = ForwardStore(database.packetDao())
        seenSet = SeenSet(database.seenMessageDao())
        transport = BleMeshTransport(applicationContext, selfNodeId)
        router = MeshRouter(selfNodeId, transport, forwardStore, seenSet)
        locationProvider = AndroidGpsLocationProvider(applicationContext)
        networkMonitor = AndroidNetworkMonitor(applicationContext)
        gatewayUploader = GatewayUploader(forwardStore, networkMonitor)
        mobilityEstimator = com.meshroute.app.sensor.MobilityEstimator(applicationContext).apply { start() }

        transport.telemetryProvider = {
            com.meshroute.app.mesh.ble.NodeTelemetryBeacon(
                batteryPercent = transport.powerManager.batteryLevel.value,
                isCharging = false,
                mobilityState = mobilityEstimator.currentMobility.value,
                gatewayLikelihoodPercent = if (networkMonitor.isInternetAvailable.value) 95 else 35,
                queueLoad = 0
            )
        }

        setContent {
            MeshRouteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    MeshRouteApp(
                        selfNodeId = selfNodeId,
                        transport = transport,
                        router = router,
                        forwardStore = forwardStore,
                        locationProvider = locationProvider,
                        gatewayUploader = gatewayUploader,
                        networkMonitor = networkMonitor,
                        activity = this
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mobilityEstimator.stop()
        router.stop()
        gatewayUploader.stop()
    }
}

@Composable
fun MeshRouteApp(
    selfNodeId: String,
    transport: BleMeshTransport,
    router: MeshRouter,
    forwardStore: ForwardStore,
    locationProvider: LocationProvider,
    gatewayUploader: GatewayUploader,
    networkMonitor: NetworkMonitor,
    activity: ComponentActivity
) {
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { activity.getSharedPreferences("meshroute_prefs", Context.MODE_PRIVATE) }

    val neighbors by transport.neighbors.collectAsState()
    val health by transport.health.collectAsState()
    val isInternetAvailable by networkMonitor.isInternetAvailable.collectAsState()
    val uploadedCount by forwardStore.uploadedCountFlow.collectAsState(initial = 0)
    val storedPackets by forwardStore.queuedPacketsFlow.collectAsState(initial = emptyList())

    val receivedPackets = remember { mutableStateListOf<SosPacket>() }
    val relayEvents = remember { mutableStateListOf<RelayEvent>() }
    val duplicateEvents = remember { mutableStateListOf<DuplicateSuppressedEvent>() }
    val ttlEvents = remember { mutableStateListOf<TtlExhaustedEvent>() }
    val expiredEvents = remember { mutableStateListOf<PacketExpiredEvent>() }
    val gatewayEvents = remember { mutableStateListOf<GatewayUploadEvent>() }

    var currentScreen by remember { mutableStateOf(AppScreen.MAIN) }

    // User Profile & SOS Input state (saved in prefs)
    var sosMessageText by remember { mutableStateOf(prefs.getString("draft_sos_message", "") ?: "") }
    if (sosMessageText.isEmpty()) {
        sosMessageText = "Injured hiker on North Ridge trail, need immediate evacuation"
    }

    var senderName by remember { mutableStateOf(prefs.getString("user_profile_name", "Puneet P.") ?: "Puneet P.") }
    var medicalNotes by remember { mutableStateOf(prefs.getString("user_medical_notes", "Sprained ankle, low water") ?: "Sprained ankle, low water") }
    var selectedReachHops by remember { mutableIntStateOf(8) } // Default 8 hops
    var currentLocation by remember { mutableStateOf<LocationData?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var isBroadcasting by remember { mutableStateOf(false) }

    val requiredPermissions = remember {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
            list.add(Manifest.permission.ACCESS_FINE_LOCATION)
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION)
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            list.add(Manifest.permission.BLUETOOTH)
            list.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            coroutineScope.launch {
                transport.start()
                router.start()
                gatewayUploader.start()
                currentLocation = locationProvider.getLastKnownLocation()
            }
        }
    }

    LaunchedEffect(Unit) {
        SosNotificationHelper.createNotificationChannel(activity)
        val hasAll = requiredPermissions.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
        if (hasAll) {
            transport.start()
            router.start()
            gatewayUploader.start()
            currentLocation = locationProvider.getLastKnownLocation()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    // Reactive packet and event collectors
    LaunchedEffect(Unit) {
        router.deliveredPackets.collect { packet ->
            receivedPackets.add(0, packet)
            SosNotificationHelper.showSosNotification(activity, packet)
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

    // Handle back button when in Network Details screen
    if (currentScreen == AppScreen.NETWORK_DETAILS) {
        BackHandler {
            currentScreen = AppScreen.MAIN
        }
    }

    when (currentScreen) {
        AppScreen.MAIN -> {
            MainSosScreen(
                selfNodeId = selfNodeId,
                isInternetAvailable = isInternetAvailable,
                peerCount = neighbors.size,
                transportHealth = health,
                currentLocation = currentLocation,
                isFetchingLocation = isFetchingLocation,
                sosMessageText = sosMessageText,
                onMessageChange = {
                    sosMessageText = it
                    prefs.edit().putString("draft_sos_message", it).apply()
                },
                senderName = senderName,
                onSenderNameChange = {
                    senderName = it
                    prefs.edit().putString("user_profile_name", it).apply()
                },
                medicalNotes = medicalNotes,
                onMedicalNotesChange = {
                    medicalNotes = it
                    prefs.edit().putString("user_medical_notes", it).apply()
                },
                selectedReachHops = selectedReachHops,
                onReachHopsChange = { selectedReachHops = it },
                onRefreshLocation = {
                    coroutineScope.launch {
                        isFetchingLocation = true
                        currentLocation = locationProvider.getCurrentLocation(3000L)
                        isFetchingLocation = false
                    }
                },
                onSendSos = {
                    coroutineScope.launch {
                        isBroadcasting = true
                        val loc = locationProvider.getCurrentLocation(2000L) ?: currentLocation
                        router.originateSos(
                            message = sosMessageText,
                            location = loc,
                            senderName = senderName,
                            medicalInfo = medicalNotes,
                            ttl = selectedReachHops
                        )
                        gatewayUploader.triggerUpload()
                        isBroadcasting = false
                    }
                },
                isBroadcasting = isBroadcasting,
                receivedPackets = receivedPackets,
                uploadedCount = uploadedCount,
                onNavigateToNetworkDetails = {
                    currentScreen = AppScreen.NETWORK_DETAILS
                }
            )
        }

        AppScreen.NETWORK_DETAILS -> {
            NetworkDetailsScreen(
                selfNodeId = selfNodeId,
                neighbors = neighbors,
                health = health,
                isInternetAvailable = isInternetAvailable,
                router = router,
                gatewayUploader = gatewayUploader,
                uploadedCount = uploadedCount,
                storedPackets = storedPackets,
                relayEvents = relayEvents,
                duplicateEvents = duplicateEvents,
                ttlEvents = ttlEvents,
                expiredEvents = expiredEvents,
                gatewayEvents = gatewayEvents,
                onClearDatabase = {
                    coroutineScope.launch {
                        forwardStore.clearDatabase()
                        router.seenSet.clear()
                    }
                },
                onNavigateBack = {
                    currentScreen = AppScreen.MAIN
                }
            )
        }
    }
}
