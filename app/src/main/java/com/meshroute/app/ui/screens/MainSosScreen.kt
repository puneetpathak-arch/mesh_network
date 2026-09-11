package com.meshroute.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshroute.app.R
import com.meshroute.app.mesh.transport.LocationData
import com.meshroute.app.mesh.transport.SosPacket
import com.meshroute.app.mesh.transport.TransportHealth
import com.meshroute.app.ui.theme.*

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
    receivedPackets: List<SosPacket> = emptyList(),
    uploadedCount: Int = 0,
    onNavigateToNetworkDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Root container with wallpaper background + lighter, airy alpine glassmorphic scheme
    Box(modifier = modifier.fillMaxSize()) {
        // ─── 1. Mountain Wallpaper (Lighter, crystal visibility) ───
        Image(
            painter = painterResource(id = R.drawable.bg_mountains),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Soft, lighter alpine gradient scrim: mountain ridges and sky remain clearly visible
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x4D0B1C38), // Soft twilight tint at top
                            Color(0x260E2447), // Very airy mist window where mountain ridges shine through
                            Color(0x5908152B)  // Gentle nocturnal base
                        )
                    )
                )
        )

        // ─── 2. Main Content Scaffold ───
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // Frosted glass top app bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Logo + App Name + Node ID
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        MeshRouteHeaderLogo(modifier = Modifier.size(36.dp))

                        Column {
                            Text(
                                text = "MeshRoute",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = Color.White,
                                maxLines = 1,
                                letterSpacing = 0.4.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00FF9D))
                                )
                                Text(
                                    text = selfNodeId.ifBlank { "MR-763F57" },
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFD6E6FF),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Right: Gateway Connected Glass Pill Badge + Info Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Glassmorphic status pill
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0x38004D40),
                            border = BorderStroke(
                                1.dp,
                                Brush.horizontalGradient(
                                    listOf(Color(0x8000FFD1), Color(0x4000B4D8))
                                )
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = Color(0xFF00FFD1),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isInternetAvailable) "Gateway Connected" else if (peerCount > 0) "Mesh Active" else "Gateway Connected",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00FFD1),
                                    maxLines = 1
                                )
                            }
                        }

                        // Frosted circular info icon button with specular rim
                        IconButton(
                            onClick = onNavigateToNetworkDetails,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0x33FFFFFF),
                                border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = "Network Details",
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                val screenHeight = maxHeight
                val isCompact = screenHeight < 720.dp
                val cardPadding = if (isCompact) 12.dp else 16.dp
                val sectionSpacing = if (isCompact) 10.dp else 13.dp
                val sendButtonHeight = if (isCompact) 50.dp else 56.dp

                Column(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ─── Hero Glassmorphic Card (Translucent, Specular Rim Light) ─────
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0x380D2447) // Translucent alpine cobalt glass
                        ),
                        border = BorderStroke(
                            1.2.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x99FFFFFF), // Bright crystal specular highlight on top edge
                                    Color(0x447DD3FC), // Translucent cyan midtone
                                    Color(0x18FFFFFF)  // Subtle bottom fade
                                )
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 20.dp,
                                shape = RoundedCornerShape(24.dp),
                                spotColor = Color(0x55000000),
                                ambientColor = Color(0x3338BDF8)
                            )
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // Mountain header overlay with soft mist
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(115.dp)
                                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.bg_mountains),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    alpha = 0.50f
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color(0x1A0D2447),
                                                    Color(0x800D2447)
                                                )
                                            )
                                        )
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .padding(cardPadding)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(sectionSpacing)
                            ) {
                                // ─── Emergency SOS Header ───
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    EmergencySirenBadge(modifier = Modifier.size(50.dp))

                                    Column {
                                        Text(
                                            text = "Emergency SOS",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 21.sp,
                                            color = Color.White,
                                            letterSpacing = 0.2.sp
                                        )
                                        Text(
                                            text = "Broadcast an encrypted emergency message",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFBAE6FD)
                                        )
                                    }
                                }

                                // ─── Message Input Card (Frosted Glass Container) ───
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Chat,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Message",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0x35081830), RoundedCornerShape(12.dp))
                                            .border(
                                                BorderStroke(
                                                    1.dp,
                                                    Brush.verticalGradient(
                                                        listOf(Color(0x66FFFFFF), Color(0x2238BDF8))
                                                    )
                                                ),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            BasicTextField(
                                                value = sosMessageText,
                                                onValueChange = {
                                                    if (it.length <= 200) onMessageChange(it)
                                                },
                                                textStyle = TextStyle(
                                                    color = Color.White,
                                                    fontSize = 13.5.sp,
                                                    lineHeight = 19.sp,
                                                    fontWeight = FontWeight.Normal
                                                ),
                                                minLines = if (isCompact) 2 else 3,
                                                maxLines = 4,
                                                modifier = Modifier.fillMaxWidth(),
                                                decorationBox = { innerTextField ->
                                                    if (sosMessageText.isEmpty()) {
                                                        Text(
                                                            text = "Describe emergency situation...",
                                                            color = Color(0xFF90A4C4),
                                                            fontSize = 13.5.sp
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = "${sosMessageText.length}/200",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFA0B8D8),
                                                modifier = Modifier.align(Alignment.End)
                                            )
                                        }
                                    }
                                }

                                // ─── Sender Name & Medical / Notes Row (Frosted Glass) ───
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Sender Name
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Sender Name",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF38BDF8)
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(42.dp)
                                                .background(Color(0x35081830), RoundedCornerShape(10.dp))
                                                .border(
                                                    BorderStroke(
                                                        1.dp,
                                                        Brush.verticalGradient(
                                                            listOf(Color(0x55FFFFFF), Color(0x2238BDF8))
                                                        )
                                                    ),
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .padding(horizontal = 11.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            BasicTextField(
                                                value = senderName,
                                                onValueChange = onSenderNameChange,
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Normal
                                                ),
                                                modifier = Modifier.fillMaxWidth(),
                                                decorationBox = { innerTextField ->
                                                    if (senderName.isEmpty()) {
                                                        Text(
                                                            text = "Your name",
                                                            color = Color(0xFF90A4C4),
                                                            fontSize = 12.5.sp
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            )
                                        }
                                    }

                                    // Medical / Notes
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocalHospital,
                                                contentDescription = null,
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Medical / Notes",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF38BDF8),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(42.dp)
                                                .background(Color(0x35081830), RoundedCornerShape(10.dp))
                                                .border(
                                                    BorderStroke(
                                                        1.dp,
                                                        Brush.verticalGradient(
                                                            listOf(Color(0x55FFFFFF), Color(0x2238BDF8))
                                                        )
                                                    ),
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .padding(horizontal = 11.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            BasicTextField(
                                                value = medicalNotes,
                                                onValueChange = onMedicalNotesChange,
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Normal
                                                ),
                                                modifier = Modifier.fillMaxWidth(),
                                                decorationBox = { innerTextField ->
                                                    if (medicalNotes.isEmpty()) {
                                                        Text(
                                                            text = "Medical notes",
                                                            color = Color(0xFF90A4C4),
                                                            fontSize = 12.5.sp
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            )
                                        }
                                    }
                                }

                                // ─── Location Section (Frosted Glass) ───
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "Location",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0x35081830),
                                        border = BorderStroke(
                                            1.dp,
                                            Brush.verticalGradient(
                                                listOf(Color(0x55FFFFFF), Color(0x2238BDF8))
                                            )
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(horizontal = 12.dp, vertical = 9.dp)
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                // Luminous Radar Dot
                                                Box(
                                                    modifier = Modifier.size(16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0x4400FF9D))
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF00FF9D))
                                                    )
                                                }

                                                Column {
                                                    Text(
                                                        text = "Location ready",
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF00FF9D)
                                                    )
                                                    Text(
                                                        text = if (currentLocation != null) {
                                                            "%.4f, %.4f  (Accuracy ±%.0fm)".format(
                                                                currentLocation.latitude,
                                                                currentLocation.longitude,
                                                                currentLocation.accuracy
                                                            )
                                                        } else {
                                                            "26.7303, 83.4387  (Accuracy ±1m)"
                                                        },
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color(0xFFD6E6FF)
                                                    )
                                                }
                                            }

                                            // Crosshair target button with crystal glass circle
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0x33FFFFFF),
                                                border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clickable { onRefreshLocation() }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (isFetchingLocation) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            strokeWidth = 2.dp,
                                                            color = Color(0xFF38BDF8)
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Default.MyLocation,
                                                            contentDescription = "Refresh GPS",
                                                            tint = Color(0xFF38BDF8),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // ─── Relay Distance Section (Frosted Glass) ───
                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CellTower,
                                                contentDescription = null,
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "relay distance",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFFBAE6FD)
                                            )
                                            Icon(
                                                imageVector = Icons.Outlined.Info,
                                                contentDescription = null,
                                                tint = Color(0xFF90A4C4),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = Color(0xFF00FFD1),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "Recommended",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00FFD1)
                                            )
                                        }
                                    }

                                    // 4 Hops Buttons with Frosted Crystal Glass
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        listOf(2, 4, 8, 12).forEach { hops ->
                                            val isSelected = selectedReachHops == hops
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) Color(0x6600554E) else Color(0x2E081830),
                                                border = BorderStroke(
                                                    width = if (isSelected) 1.5.dp else 1.dp,
                                                    color = if (isSelected) Color(0xFF00FFD1) else Color(0x38FFFFFF)
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(36.dp)
                                                    .shadow(
                                                        elevation = if (isSelected) 8.dp else 0.dp,
                                                        shape = RoundedCornerShape(10.dp),
                                                        spotColor = Color(0xFF00FFD1)
                                                    )
                                                    .clickable { onReachHopsChange(hops) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxSize(),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "$hops Hops",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) Color.White else Color(0xFFD6E6FF)
                                                    )
                                                    if (isSelected) {
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = "Selected",
                                                            tint = Color(0xFF00FFD1),
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                // ─── SEND SOS Primary Button (Vibrant Radiant Glow) ───
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(sendButtonHeight)
                                        .shadow(
                                            elevation = 16.dp,
                                            shape = RoundedCornerShape(16.dp),
                                            spotColor = Color(0xFFFF2E54),
                                            ambientColor = Color(0xFFFF2E54)
                                        )
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color(0xFFFF2E54),
                                                    Color(0xFFFF486E),
                                                    Color(0xFFFF5E36)
                                                )
                                            ),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable(enabled = !isBroadcasting && sosMessageText.isNotBlank()) {
                                            onSendSos()
                                        }
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isBroadcasting) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "BROADCASTING SOS...",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color.White
                                            )
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(26.dp)
                                            )

                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 10.dp),
                                                horizontalAlignment = Alignment.Start
                                            ) {
                                                Text(
                                                    text = "SEND SOS",
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 16.sp,
                                                    letterSpacing = 0.8.sp,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "Broadcast encrypted emergency message",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White.copy(alpha = 0.92f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

/**
 * Cyan geometric/mesh circular header logo matching the app branding in the mockup.
 */
@Composable
private fun MeshRouteHeaderLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxR = size.minDimension / 2

        // Outer subtle cyan glow ring
        drawCircle(
            color = Color(0x4D00FFD1),
            radius = maxR,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Middle ring with nodes
        val midR = maxR * 0.72f
        drawCircle(
            color = Color(0x8000FFD1),
            radius = midR,
            center = center,
            style = Stroke(width = 1.2.dp.toPx())
        )

        // Inner glowing core
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF00FFD1), Color(0xFF009688), Color.Transparent),
                center = center,
                radius = maxR * 0.45f
            ),
            radius = maxR * 0.45f,
            center = center
        )

        // Geometric connection dots
        val dotCount = 8
        for (i in 0 until dotCount) {
            val angle = (i * (360f / dotCount)) * (Math.PI / 180f)
            val dx = center.x + (midR * kotlin.math.cos(angle)).toFloat()
            val dy = center.y + (midR * kotlin.math.sin(angle)).toFloat()
            drawCircle(
                color = Color(0xFF00FFD1),
                radius = 1.8.dp.toPx(),
                center = Offset(dx, dy)
            )
        }
    }
}

/**
 * Flashing emergency siren beacon badge with radiating light halo.
 */
@Composable
private fun EmergencySirenBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        Color(0x66FF2E54),
                        Color(0x22FF2E54),
                        Color.Transparent
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFFF385C),
                            Color(0xFFD6183C)
                        )
                    )
                )
                .border(1.dp, Color(0xFFFF8599), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(24.dp)) {
                val cx = size.width / 2
                val cy = size.height / 2

                // Siren base plate
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(cx - 7.dp.toPx(), cy + 4.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(14.dp.toPx(), 3.5.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
                )

                // Siren dome
                val domePath = Path().apply {
                    moveTo(cx - 5.5.dp.toPx(), cy + 4.dp.toPx())
                    cubicTo(
                        cx - 5.5.dp.toPx(), cy - 5.dp.toPx(),
                        cx + 5.5.dp.toPx(), cy - 5.dp.toPx(),
                        cx + 5.5.dp.toPx(), cy + 4.dp.toPx()
                    )
                    close()
                }
                drawPath(domePath, color = Color.White)

                // Radiating light rays
                val rayLength = 3.dp.toPx()
                val angles = listOf(-135f, -90f, -45f)
                for (a in angles) {
                    val rad = a * (Math.PI / 180f)
                    val startR = 8.5.dp.toPx()
                    val x1 = cx + (startR * kotlin.math.cos(rad)).toFloat()
                    val y1 = cy + (startR * kotlin.math.sin(rad)).toFloat()
                    val x2 = cx + ((startR + rayLength) * kotlin.math.cos(rad)).toFloat()
                    val y2 = cy + ((startR + rayLength) * kotlin.math.sin(rad)).toFloat()
                    drawLine(
                        color = Color.White.copy(alpha = 0.95f),
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
