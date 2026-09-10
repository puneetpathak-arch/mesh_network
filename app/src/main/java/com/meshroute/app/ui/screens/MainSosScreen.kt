package com.meshroute.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshroute.app.mesh.transport.LocationData
import com.meshroute.app.mesh.transport.SosPacket
import com.meshroute.app.mesh.transport.TransportHealth
import com.meshroute.app.ui.components.*
import com.meshroute.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSosScreen(
    selfNodeId: String,
    isInternetAvailable: Boolean,
    peerCount: Int,
    transportHealth: TransportHealth,
    currentLocation: LocationData?,
    isFetchingLocation: Boolean,
    sosMessageText: String,
    onMessageChange: (String) -> Unit,
    senderName: String,
    onSenderNameChange: (String) -> Unit,
    medicalNotes: String,
    onMedicalNotesChange: (String) -> Unit,
    selectedReachHops: Int,
    onReachHopsChange: (Int) -> Unit,
    onRefreshLocation: () -> Unit,
    onSendSos: () -> Unit,
    isBroadcasting: Boolean,
    receivedPackets: List<SosPacket>,
    uploadedCount: Int,
    onNavigateToNetworkDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "MeshRoute",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    // Single Unambiguous Network Status Badge in the header
                    NetworkStatusBadge(
                        isInternetAvailable = isInternetAvailable,
                        peerCount = peerCount,
                        transportHealth = transportHealth,
                        showSubtitle = false
                    )

                    IconButton(onClick = onNavigateToNetworkDetails) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Network Details",
                            tint = TextSecondary
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
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── 1. Network Summary Card (Scannable block) ──────────────────────────
            NetworkSummaryCard(
                isInternetAvailable = isInternetAvailable,
                peerCount = peerCount,
                transportHealth = transportHealth,
                onNavigateToDetails = onNavigateToNetworkDetails
            )

            // ─── 2. Primary SOS Creation Form ──────────────────────────────────────
            SosCreationForm(
                messageText = sosMessageText,
                onMessageChange = onMessageChange,
                senderName = senderName,
                onSenderNameChange = onSenderNameChange,
                medicalNotes = medicalNotes,
                onMedicalNotesChange = onMedicalNotesChange,
                selectedReachHops = selectedReachHops,
                onReachHopsChange = onReachHopsChange,
                currentLocation = currentLocation,
                isFetchingLocation = isFetchingLocation,
                onRefreshLocation = onRefreshLocation,
                onSendSos = onSendSos,
                isBroadcasting = isBroadcasting
            )

            // ─── 3. Active Emergency Transmissions & Live Delivery Pipeline ────────
            Text(
                text = "EMERGENCY TRANSMISSIONS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

            SosDeliveryFlowView(
                packets = receivedPackets,
                isInternetAvailable = isInternetAvailable,
                uploadedCount = uploadedCount,
                selfNodeId = selfNodeId
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
