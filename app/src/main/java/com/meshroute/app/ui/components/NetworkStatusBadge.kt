package com.meshroute.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshroute.app.mesh.transport.TransportHealth
import com.meshroute.app.ui.theme.*

data class NetworkStateInfo(
    val title: String,
    val subtitle: String,
    val badgeColor: Color,
    val containerColor: Color,
    val icon: ImageVector
)

@Composable
fun getUnambiguousNetworkState(
    isInternetAvailable: Boolean,
    peerCount: Int,
    transportHealth: TransportHealth
): NetworkStateInfo {
    return when {
        // 1. Full gateway connectivity
        isInternetAvailable -> {
            NetworkStateInfo(
                title = "Gateway Connected",
                subtitle = if (peerCount > 0) "$peerCount nearby relay devices" else "Direct rescue gateway reachable",
                badgeColor = AccentGreen,
                containerColor = AccentGreenSubtle,
                icon = Icons.Default.CloudDone
            )
        }
        // 2. Active mesh with peer relays nearby
        peerCount > 0 && transportHealth != TransportHealth.UNAVAILABLE -> {
            NetworkStateInfo(
                title = "Mesh Network Active",
                subtitle = "$peerCount nearby relay device${if (peerCount == 1) "" else "s"}",
                badgeColor = AccentEmerald,
                containerColor = AccentGreenSubtle,
                icon = Icons.Default.Hub
            )
        }
        // 3. Mesh transceiver ready but searching for peers / gateway offline
        transportHealth == TransportHealth.HEALTHY || transportHealth == TransportHealth.DEGRADED -> {
            NetworkStateInfo(
                title = "Mesh Available · Gateway Offline",
                subtitle = "Searching for nearby relay devices",
                badgeColor = WarningAmber,
                containerColor = WarningAmberSubtle,
                icon = Icons.Default.WifiOff
            )
        }
        // 4. No transceiver / no relay devices available
        else -> {
            NetworkStateInfo(
                title = "No Relay Devices Available",
                subtitle = "SOS will be retained locally until a peer is found",
                badgeColor = EmergencyRed,
                containerColor = EmergencyRedSubtle,
                icon = Icons.Default.SensorsOff
            )
        }
    }
}

@Composable
fun NetworkStatusBadge(
    isInternetAvailable: Boolean,
    peerCount: Int,
    transportHealth: TransportHealth,
    modifier: Modifier = Modifier,
    showSubtitle: Boolean = true
) {
    val state = getUnambiguousNetworkState(
        isInternetAvailable = isInternetAvailable,
        peerCount = peerCount,
        transportHealth = transportHealth
    )

    Surface(
        color = state.containerColor,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(state.badgeColor)
            )
            Icon(
                imageVector = state.icon,
                contentDescription = null,
                tint = state.badgeColor,
                modifier = Modifier.size(14.dp)
            )
            Column {
                Text(
                    text = state.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = state.badgeColor
                )
                if (showSubtitle && state.subtitle.isNotEmpty()) {
                    Text(
                        text = state.subtitle,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
