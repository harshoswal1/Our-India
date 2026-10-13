package com.ourindia.app.ui.partystructure

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ourindia.app.ui.theme.CivicColors
import kotlin.math.roundToInt

private data class TierSlice(val label: String, val count: Int, val color: Color)

@Composable
fun PartyAnalyticsDashboard(
    partyMetadata: PartyMetadata,
    allNodes: List<PartyTreeNode>,
    stateGeoDataList: List<StateGeoData>,
    selectedState: String? = null,
    onClearLocationFilter: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val nodes = remember(allNodes, selectedState) {
        if (selectedState == null) allNodes else allNodes.filter { it.state.equals(selectedState, ignoreCase = true) }
    }
    val named = nodes.count { !it.isNotFetched }
    val slices = remember(nodes) {
        listOf(
            TierSlice("National", nodes.count { it.level.tier <= 8 }, HierarchyLevel.NATIONAL.badgeColor),
            TierSlice("State", nodes.count { it.level.tier in 9..15 }, HierarchyLevel.STATE.badgeColor),
            TierSlice("District", nodes.count { it.level.tier in 16..19 }, HierarchyLevel.DISTRICT.badgeColor),
            TierSlice("Mandal & constituency", nodes.count { it.level.tier in 20..22 }, HierarchyLevel.TALUKA_MANDAL.badgeColor),
            TierSlice("Ward & booth", nodes.count { it.level.tier >= 23 }, HierarchyLevel.WARD.badgeColor)
        )
    }
    var showAllStates by remember(partyMetadata.shortName) { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = selectedState ?: "All India",
                        style = MaterialTheme.typography.headlineSmall,
                        color = CivicColors.Navy
                    )
                    Text(
                        text = if (selectedState != null) "${partyMetadata.shortName} organisation in this state"
                        else "${partyMetadata.name} across the country",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CivicColors.TextSecondary
                    )
                }
                if (selectedState != null && onClearLocationFilter != null) {
                    TextButton(onClick = onClearLocationFilter) { Text("All India", color = CivicColors.SaffronDeep) }
                }
            }
        }

        if (nodes.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Insights,
                    title = "Nothing to measure yet",
                    message = if (selectedState != null) "No ${partyMetadata.shortName} posts are recorded in $selectedState."
                    else "${partyMetadata.name}'s structure hasn't been ingested yet."
                )
            }
            return@LazyColumn
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Posts mapped", "${nodes.size}", Modifier.weight(1f))
                    StatTile("Named holders", "$named", Modifier.weight(1f), caption = "of ${nodes.size}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (selectedState == null) {
                        StatTile("States organised", "${stateGeoDataList.size}", Modifier.weight(1f))
                    } else {
                        StatTile(
                            "Districts organised",
                            "${stateGeoDataList.firstOrNull { it.stateName.equals(selectedState, true) }?.districts?.size ?: 0}",
                            Modifier.weight(1f)
                        )
                    }
                    // Seats are a national figure; labelled so a state filter doesn't make it read as local.
                    StatTile("Lok Sabha MPs", "${partyMetadata.nationalSeats}", Modifier.weight(1f), caption = "nationwide")
                }
            }
        }

        item {
            SectionCard(title = "Record completeness", icon = Icons.Outlined.TaskAlt) {
                val fraction = named.toFloat() / nodes.size
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${(fraction * 100).roundToInt()}%", style = MaterialTheme.typography.headlineMedium, color = CivicColors.TealDeep)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "of posts have a named holder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CivicColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape),
                    color = CivicColors.Teal,
                    trackColor = CivicColors.SurfaceVariant,
                    drawStopIndicator = {}
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Posts without a holder exist in the party constitution but their occupant hasn't been ingested yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CivicColors.TextMuted
                )
            }
        }

        item {
            SectionCard(title = "Posts by tier", icon = Icons.Outlined.DonutLarge) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                        DonutChart(slices = slices, total = nodes.size)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${nodes.size}", style = MaterialTheme.typography.titleLarge, color = CivicColors.Navy)
                            Text("posts", style = MaterialTheme.typography.labelSmall, color = CivicColors.TextMuted)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        slices.forEach { LegendRow(it, nodes.size) }
                    }
                }
            }
        }

        if (selectedState == null && stateGeoDataList.isNotEmpty()) {
            item {
                SectionCard(
                    title = "Where it's organised",
                    icon = Icons.Outlined.Place,
                    trailing = {
                        if (stateGeoDataList.size > 6) {
                            TextButton(onClick = { showAllStates = !showAllStates }) {
                                Text(if (showAllStates) "Top 6" else "All ${stateGeoDataList.size}", color = CivicColors.SaffronDeep)
                            }
                        }
                    }
                ) {
                    val max = stateGeoDataList.maxOf { it.leaderCount }.coerceAtLeast(1)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        (if (showAllStates) stateGeoDataList else stateGeoDataList.take(6)).forEach { st ->
                            StateBar(st.stateName, st.leaderCount, max, partyMetadata.color)
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CivicColors.SurfaceVariant, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = CivicColors.TextSecondary, modifier = Modifier.size(18.dp))
                Text(
                    "Counts reflect positions recorded on this device from party constitutions, public bulletins and election registries. Seat totals come from the party catalogue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CivicColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, caption: String? = null) {
    Column(
        modifier = modifier
            .background(CivicColors.Surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = CivicColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = CivicColors.Navy)
            if (caption != null) {
                Spacer(Modifier.width(6.dp))
                Text(caption, style = MaterialTheme.typography.bodySmall, color = CivicColors.TextMuted, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun DonutChart(slices: List<TierSlice>, total: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 16.dp.toPx()
        val radius = (size.minDimension - stroke) / 2f
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)
        drawArc(CivicColors.SurfaceVariant, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        // Butt caps with a fixed gap: the old round caps overran each slice by half the
        // stroke, so small slices rendered as overlapping dots.
        val present = slices.filter { it.count > 0 }
        val gap = if (present.size > 1) 2.5f else 0f
        var start = -90f
        present.forEach { slice ->
            val sweep = slice.count.toFloat() / total * 360f
            drawArc(slice.color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
            start += sweep
        }
    }
}

@Composable
private fun LegendRow(slice: TierSlice, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).background(slice.color, CircleShape))
        Text(slice.label, style = MaterialTheme.typography.bodySmall, color = CivicColors.OnSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${slice.count} · ${(slice.count * 100f / total).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = CivicColors.Navy
        )
    }
}

@Composable
private fun StateBar(name: String, count: Int, max: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(name, style = MaterialTheme.typography.bodyMedium, color = CivicColors.Navy, modifier = Modifier.weight(1f))
            Text("$count posts", style = MaterialTheme.typography.labelMedium, color = CivicColors.TextSecondary)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(CivicColors.SurfaceVariant, CircleShape)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(count.toFloat() / max)
                    .fillMaxHeight()
                    .background(color, CircleShape)
            )
        }
    }
}
