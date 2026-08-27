package com.ourindia.app.ui.partystructure

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ourindia.app.ui.theme.CivicColors

@Composable
fun PartyAnalyticsDashboard(
    partyMetadata: PartyMetadata,
    allNodes: List<PartyTreeNode>,
    stateGeoDataList: List<StateGeoData>,
    selectedState: String? = null,
    onClearLocationFilter: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Filter nodes for regional analytics if a state is selected
    val filteredNodes = if (selectedState != null) {
        allNodes.filter { it.state.equals(selectedState, ignoreCase = true) }
    } else {
        allNodes
    }

    val totalNodes = filteredNodes.size.coerceAtLeast(1)

    // Level breakdown — dynamically grouped by tier ranges, not hardcoded enum values
    val nationalCount = filteredNodes.count { it.level.tier <= 8 }          // National tier (1-8)
    val stateCount    = filteredNodes.count { it.level.tier in 9..15 }      // State tier (9-15)
    val districtCount = filteredNodes.count { it.level.tier in 16..22 }     // District/Taluka tier (16-22)
    val wardCount     = filteredNodes.count { it.level.tier >= 23 }         // Ward/Booth tier (23+)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CivicColors.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 0. Active Scope Indicator
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, CivicColors.Navy, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedState != null) Color(0xFFFFF8E7) else Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedState != null) "Regional Representation Insights" else "Nationwide Insights",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = CivicColors.Navy
                        )
                        Text(
                            text = if (selectedState != null) "Filtered by region: $selectedState" else "Showing statistics across all states",
                            fontSize = 11.5.sp,
                            color = CivicColors.TextSecondary
                        )
                    }
                    if (selectedState != null && onClearLocationFilter != null) {
                        TextButton(onClick = onClearLocationFilter) {
                            Text(
                                text = "Show Nationwide",
                                color = CivicColors.Saffron,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
        // 1. Key Summary Stats Grid
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, CivicColors.Navy, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = CivicColors.Navy,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${partyMetadata.name} Overview",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = CivicColors.Navy
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatMetricBox(
                            value = "$totalNodes",
                            label = "Organizational Units",
                            color = partyMetadata.color,
                            modifier = Modifier.weight(1f)
                        )
                        StatMetricBox(
                            value = "${partyMetadata.nationalSeats}",
                            label = "Lok Sabha Seats",
                            color = CivicColors.Saffron,
                            modifier = Modifier.weight(1f)
                        )
                        StatMetricBox(
                            value = "${stateGeoDataList.size}",
                            label = "Active States",
                            color = CivicColors.Teal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Hierarchy Level Distribution (Custom Donut Chart & Breakdown)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, CivicColors.Navy, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = null,
                            tint = CivicColors.Navy,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Level-wise Hierarchy Distribution",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = CivicColors.Navy
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Custom Canvas Donut Chart
                        Box(
                            modifier = Modifier.size(110.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            DonutChart(
                                nationalCount = nationalCount,
                                stateCount = stateCount,
                                districtCount = districtCount,
                                wardCount = wardCount,
                                total = totalNodes
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$totalNodes",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = CivicColors.Navy
                                )
                                Text(
                                    text = "Nodes",
                                    fontSize = 9.sp,
                                    color = CivicColors.TextSecondary
                                )
                            }
                        }

                        // Legend Breakdown List
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LevelLegendRow("National", nationalCount, totalNodes, CivicColors.Saffron)
                            LevelLegendRow("State", stateCount, totalNodes, CivicColors.Navy)
                            LevelLegendRow("District", districtCount, totalNodes, CivicColors.Teal)
                            LevelLegendRow("Ward/Local", wardCount, totalNodes, CivicColors.CivicRed)
                        }
                    }
                }
            }
        }

        // 3. State-wise Representation Comparison Bar Chart
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, CivicColors.Navy, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = CivicColors.Navy,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "State-wise Representation Intensity",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = CivicColors.Navy
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    stateGeoDataList.take(6).forEach { state ->
                        StateBarRow(
                            stateName = state.stateName,
                            count = state.leaderCount,
                            maxCount = stateGeoDataList.maxOfOrNull { it.leaderCount }?.coerceAtLeast(1) ?: 1,
                            partyColor = partyMetadata.color
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // 4. Data Transparency / Methodology Note
        item {
            Surface(
                color = CivicColors.Navy.copy(alpha = 0.06f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CivicColors.Navy.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CivicColors.Navy,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Data represents verified public organizational records and party leadership appointments compiled from party constitutions, public bulletins, and election registries.",
                        fontSize = 11.sp,
                        color = CivicColors.OnSurface,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StatMetricBox(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = CivicColors.TextSecondary,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DonutChart(
    nationalCount: Int,
    stateCount: Int,
    districtCount: Int,
    wardCount: Int,
    total: Int
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 14.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        var startAngle = -90f
        val counts = listOf(
            nationalCount to CivicColors.Saffron,
            stateCount to CivicColors.Navy,
            districtCount to CivicColors.Teal,
            wardCount to CivicColors.CivicRed
        )

        counts.forEach { (count, color) ->
            val sweep = (count.toFloat() / total) * 360f
            if (sweep > 0f) {
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweep - 2f, // subtle gap
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                startAngle += sweep
            }
        }
    }
}

@Composable
private fun LevelLegendRow(
    label: String,
    count: Int,
    total: Int,
    color: Color
) {
    val percentage = ((count.toFloat() / total) * 100).toInt()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = CivicColors.OnSurface
            )
        }

        Text(
            text = "$count ($percentage%)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = CivicColors.Navy
        )
    }
}

@Composable
private fun StateBarRow(
    stateName: String,
    count: Int,
    maxCount: Int,
    partyColor: Color
) {
    val fraction = (count.toFloat() / maxCount).coerceIn(0.05f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stateName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = CivicColors.Navy
            )
            Text(
                text = "$count Leaders",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CivicColors.TextSecondary
            )
        }

        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = partyColor,
            trackColor = CivicColors.Navy.copy(alpha = 0.08f)
        )
    }
}
