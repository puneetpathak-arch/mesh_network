package com.meshroute.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshroute.app.mesh.transport.SosPacket
import com.meshroute.app.security.CryptoManager
import com.meshroute.app.security.EmergencyPayload
import com.meshroute.app.security.KeyManager
import com.meshroute.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SosDeliveryFlowView(
    packets: List<SosPacket>,
    isInternetAvailable: Boolean,
    uploadedCount: Int,
    selfNodeId: String,
    modifier: Modifier = Modifier
) {
    if (packets.isEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, DarkBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "No active SOS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Your emergency transmissions and multi-hop relay status will appear here.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        packets.forEach { packet ->
            SosPacketDeliveryCard(
                packet = packet,
                isInternetAvailable = isInternetAvailable,
                isUploaded = uploadedCount > 0,
                selfNodeId = selfNodeId,
                timeFormatted = timeFormat.format(Date(packet.timestamp))
            )
        }
    }
}

@Composable
private fun SosPacketDeliveryCard(
    packet: SosPacket,
    isInternetAvailable: Boolean,
    isUploaded: Boolean,
    selfNodeId: String,
    timeFormatted: String
) {
    var isDecryptedRevealed by remember(packet.messageId) { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, DarkBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with Packet ID, TTL Badge, and Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = EmergencyRedSubtle,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "SOS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = EmergencyRed,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = packet.messageId,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = GpsSkyBlue
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = AccentGreenSubtle,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Hop ${packet.hops} / ${packet.ttl}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = timeFormatted,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            // Decrypted Content or Encrypted Payload Card
            if (isDecryptedRevealed) {
                val decryptedPayload = remember(packet.payload) {
                    runCatching {
                        val json = CryptoManager.decryptString(packet.payload, KeyManager.defaultEmergencyKey)
                        EmergencyPayload.fromJson(json)
                    }.getOrNull()
                }

                Surface(
                    color = DarkBackground,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "Emergency Content",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen
                                )
                            }
                            TextButton(
                                onClick = { isDecryptedRevealed = false },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("Hide", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                        if (decryptedPayload != null) {
                            Text(
                                text = "\"${decryptedPayload.message}\"",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            if (decryptedPayload.senderName.isNotEmpty() || decryptedPayload.medicalInfo.isNotEmpty()) {
                                Text(
                                    text = "Sender: ${decryptedPayload.senderName} • Notes: ${decryptedPayload.medicalInfo}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        } else {
                            Text("Failed to decrypt payload", fontSize = 11.sp, color = EmergencyRed)
                        }
                    }
                }
            } else {
                Surface(
                    color = DarkBackground,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                "AES-256-GCM Encrypted Payload",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        TextButton(
                            onClick = { isDecryptedRevealed = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("View", fontSize = 11.sp, color = AccentGreen)
                        }
                    }
                }
            }

            // Location if available
            packet.location?.let { loc ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = GpsSkyBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "GPS: %.4f, %.4f (±%.0fm)".format(loc.latitude, loc.longitude, loc.accuracy),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = GpsSkyBlue
                    )
                }
            }

            HorizontalDivider(color = DarkBorder)

            // Section Title: Live Multi-Hop Delivery Flow
            Text(
                text = "DELIVERY FLOW",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

            // Vertical Node-Path & State Visualization
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Step 1: Origin & Prepared
                DeliveryStepItem(
                    title = "Origin (YOU)",
                    subtitle = "Message encrypted with AES-256-GCM and stored locally in Room DB.",
                    icon = Icons.Default.PersonPinCircle,
                    state = StepState.COMPLETED
                )

                // Step 2: Multi-Hop Propagation
                val hopCount = packet.hops
                val pathString = if (packet.hopPath.isNotEmpty()) {
                    packet.hopPath.joinToString(" ➔ ") + " ➔ $selfNodeId"
                } else {
                    "Direct 1-Hop Broadcast"
                }
                DeliveryStepItem(
                    title = if (hopCount > 0) "Relaying ($hopCount Hop${if (hopCount == 1) "" else "s"})" else "Broadcasting (Direct)",
                    subtitle = "Relay Path: $pathString",
                    icon = Icons.Default.ShareLocation,
                    state = StepState.COMPLETED
                )

                // Step 3: Gateway Detection
                val gatewayReached = isInternetAvailable || isUploaded
                DeliveryStepItem(
                    title = "Rescue Gateway",
                    subtitle = if (gatewayReached) {
                        "Rescue gateway connected and processing emergency packet."
                    } else {
                        "Your SOS is retained locally and will relay automatically when a gateway is reachable."
                    },
                    icon = Icons.Default.Dns,
                    state = if (gatewayReached) StepState.COMPLETED else StepState.IN_PROGRESS
                )

                // Step 4: Delivered to Rescue Service
                DeliveryStepItem(
                    title = "Delivered to Rescue Services",
                    subtitle = if (isUploaded) {
                        "✓ Confirmed: Rescue gateway received your emergency message."
                    } else {
                        "Pending gateway uplink transmission."
                    },
                    icon = Icons.Default.CheckCircle,
                    state = if (isUploaded) StepState.COMPLETED else StepState.PENDING
                )
            }
        }
    }
}

enum class StepState {
    COMPLETED,
    IN_PROGRESS,
    PENDING
}

@Composable
private fun DeliveryStepItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    state: StepState
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val (badgeColor, containerColor) = when (state) {
            StepState.COMPLETED -> AccentGreen to AccentGreenSubtle
            StepState.IN_PROGRESS -> WarningAmber to WarningAmberSubtle
            StepState.PENDING -> TextMuted to DarkSurfaceElevated
        }

        Surface(
            color = containerColor,
            shape = CircleShape,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (state == StepState.COMPLETED) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (state == StepState.IN_PROGRESS) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = WarningAmber
                    )
                } else {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (state == StepState.PENDING) TextMuted else TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = if (state == StepState.PENDING) TextMuted else TextSecondary
            )
        }
    }
}
