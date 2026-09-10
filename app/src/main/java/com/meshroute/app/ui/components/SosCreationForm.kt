package com.meshroute.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshroute.app.mesh.transport.LocationData
import com.meshroute.app.ui.theme.*

@Composable
fun SosCreationForm(
    messageText: String,
    onMessageChange: (String) -> Unit,
    senderName: String,
    onSenderNameChange: (String) -> Unit,
    medicalNotes: String,
    onMedicalNotesChange: (String) -> Unit,
    selectedReachHops: Int,
    onReachHopsChange: (Int) -> Unit,
    currentLocation: LocationData?,
    isFetchingLocation: Boolean,
    onRefreshLocation: () -> Unit,
    onSendSos: () -> Unit,
    isBroadcasting: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, DarkBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: "Create SOS" + subtle "🔒 Encrypted" badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Emergency,
                            contentDescription = null,
                            tint = EmergencyRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Create SOS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "Your message will be encrypted before transmission.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Surface(
                    color = AccentGreenSubtle,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = AccentGreen,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Encrypted (AES-256)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentGreen
                        )
                    }
                }
            }

            // Emergency Situation Input (Multiline, large readable text)
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageChange,
                label = { Text("Emergency Situation", color = TextSecondary) },
                placeholder = { Text("Describe what happened and what help you need...", color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentEmerald,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkBackground,
                    unfocusedContainerColor = DarkBackground
                ),
                shape = RoundedCornerShape(10.dp)
            )

            // Sender Name and Medical Notes Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = senderName,
                    onValueChange = onSenderNameChange,
                    label = { Text("Sender Name", color = TextSecondary, fontSize = 12.sp) },
                    placeholder = { Text("Your name", color = TextMuted, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentEmerald,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = medicalNotes,
                    onValueChange = onMedicalNotesChange,
                    label = { Text("Medical / Notes", color = TextSecondary, fontSize = 12.sp) },
                    placeholder = { Text("Injuries, allergies...", color = TextMuted, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentEmerald,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkBackground,
                        unfocusedContainerColor = DarkBackground
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // Location Status Row
            Surface(
                color = if (currentLocation != null) GpsSkyBlueSubtle else WarningAmberSubtle,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (currentLocation != null) GpsSkyBlue.copy(alpha = 0.3f) else WarningAmber.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentLocation != null) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = GpsSkyBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "📍 Location ready",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GpsSkyBlue
                                )
                                Text(
                                    text = "%.4f, %.4f  (Accuracy ±%.0fm)".format(
                                        currentLocation.latitude,
                                        currentLocation.longitude,
                                        currentLocation.accuracy
                                    ),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimary
                                )
                            }
                        } else {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "⚠ Location unavailable",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningAmber
                                )
                                Text(
                                    text = "Move outdoors or retry GPS acquisition",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onRefreshLocation,
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isFetchingLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = GpsSkyBlue
                            )
                        } else {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = "Refresh GPS",
                                tint = GpsSkyBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Reach (TTL) Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reach (Relay distance):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Text(
                        text = "$selectedReachHops Hops max",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AccentEmerald
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(2 to "Close", 4 to "Medium", 8 to "Default", 12 to "Far").forEach { (hops, label) ->
                        val isSelected = selectedReachHops == hops
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) EmergencyRedSubtle else DarkBackground,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) EmergencyRed else DarkBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onReachHopsChange(hops) }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$hops Hops",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) EmergencyRed else TextPrimary,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    color = if (isSelected) EmergencyRed.copy(alpha = 0.8f) else TextMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Primary SOS Button (Full-width, prominent, unmistakable)
            Button(
                onClick = onSendSos,
                enabled = !isBroadcasting && messageText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmergencyRed,
                    contentColor = Color.White,
                    disabledContainerColor = DarkSurfaceElevated,
                    disabledContentColor = TextMuted
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                if (isBroadcasting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "ENCRYPTING & BROADCASTING...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Emergency,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "🚨 SEND SOS",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Broadcast encrypted emergency message",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }
    }
}
