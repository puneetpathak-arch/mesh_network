package com.meshroute.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshroute.app.mesh.router.RoutingMetricsSnapshot
import com.meshroute.app.mesh.router.RoutingStrategyType
import com.meshroute.app.ui.theme.AccentEmerald
import com.meshroute.app.ui.theme.AccentGreen
import com.meshroute.app.ui.theme.CardBackground
import com.meshroute.app.ui.theme.DarkBorder
import com.meshroute.app.ui.theme.DarkSurface
import com.meshroute.app.ui.theme.GpsSkyBlue
import com.meshroute.app.ui.theme.TextMuted
import com.meshroute.app.ui.theme.WarningAmber

@Composable
fun RoutingBenchmarkCard(
    metrics: RoutingMetricsSnapshot,
    currentStrategy: RoutingStrategyType,
    onSelectStrategy: (RoutingStrategyType) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = GpsSkyBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Routing Strategy & Telemetry",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (currentStrategy == RoutingStrategyType.INTELLIGENT_EDS) {
                        AccentEmerald.copy(alpha = 0.2f)
                    } else {
                        WarningAmber.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        text = if (currentStrategy == RoutingStrategyType.INTELLIGENT_EDS) "IER ACTIVE" else "BASELINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentStrategy == RoutingStrategyType.INTELLIGENT_EDS) AccentEmerald else WarningAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Strategy Selector Segmented Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .padding(4.dp)
            ) {
                val isEpidemic = currentStrategy == RoutingStrategyType.EPIDEMIC_FLOODING
                Button(
                    onClick = { onSelectStrategy(RoutingStrategyType.EPIDEMIC_FLOODING) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEpidemic) Color(0xFF333333) else Color.Transparent,
                        contentColor = if (isEpidemic) Color.White else TextMuted
                    ),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text("Baseline (Epidemic)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                val isEds = currentStrategy == RoutingStrategyType.INTELLIGENT_EDS
                Button(
                    onClick = { onSelectStrategy(RoutingStrategyType.INTELLIGENT_EDS) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEds) GpsSkyBlue else Color.Transparent,
                        contentColor = if (isEds) Color.Black else TextMuted
                    ),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text("IER (EDS)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Telemetry Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Total Transmissions
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface)
                        .padding(10.dp)
                ) {
                    Text("Transmissions", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${metrics.totalTransmissions}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Duplicates Suppressed
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface)
                        .padding(10.dp)
                ) {
                    Text("Duplicates Filtered", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${metrics.duplicatePacketsSuppressed}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentEmerald
                    )
                }

                // Packets Saved by EDS
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface)
                        .padding(10.dp)
                ) {
                    Text("Packets Saved", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${metrics.packetsSavedEstimate}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GpsSkyBlue
                    )
                }
            }

            if (metrics.powerReductionPercent > 0f) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentEmerald.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ElectricBolt,
                        contentDescription = null,
                        tint = AccentEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Estimated Over-the-Air Tx Energy Reduction: ${String.format("%.1f", metrics.powerReductionPercent)}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentEmerald
                    )
                }
            }
        }
    }
}
