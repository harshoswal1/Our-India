package com.ourindia.app.ui.partystructure

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ourindia.app.ui.theme.CivicColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Orbit geometry, in dp from the centre. The selected unit's head sits in the middle and
 * its sub-units ring around it, so the structure spreads in every direction instead of
 * marching off the bottom of the screen like the chart.
 */
object OrbitMetrics {
    const val NODE = 56f
    const val LABEL = 40f          // two lines of unit title + holder, under the disc
    const val NODE_WIDTH = 92f
    const val SLOT = 86f           // arc length each node needs along its ring
    const val R0 = 140f            // first ring clears the centre card
    const val GAP = 104f

    fun radius(ring: Int) = R0 + GAP * ring
    fun capacity(ring: Int) = floor(2 * PI * radius(ring) / SLOT).toInt()

    /** Diameter that holds [rings] rings with their labels. */
    fun extent(rings: Int) = if (rings == 0) 2 * R0 else 2 * (radius(rings - 1) + NODE / 2 + LABEL + 12f)
}

data class OrbitSlot(val ring: Int, val x: Float, val y: Float)

/**
 * Places [n] nodes on as few rings as hold them, spread in proportion to each ring's
 * room so no ring is crammed while the next holds three. Odd rings are offset by half a
 * step so labels don't stack radially.
 */
fun orbitSlots(n: Int): List<OrbitSlot> {
    if (n == 0) return emptyList()
    val caps = mutableListOf<Int>()
    while (caps.sum() < n) caps += OrbitMetrics.capacity(caps.size)
    val total = caps.sum().toDouble()
    val exact = caps.map { it * n / total }
    val counts = exact.map { floor(it).toInt() }.toMutableList()
    // Largest remainder, never past a ring's capacity.
    exact.indices.sortedByDescending { exact[it] - counts[it] }.forEach { i ->
        if (counts.sum() < n && counts[i] < caps[i]) counts[i]++
    }
    return counts.flatMapIndexed { ring, c ->
        val r = OrbitMetrics.radius(ring)
        val step = 2 * PI / c
        (0 until c).map { i ->
            val a = -PI / 2 + step * i + if (ring % 2 == 1) step / 2 else 0.0
            OrbitSlot(ring, (r * cos(a)).toFloat(), (r * sin(a)).toFloat())
        }
    }
}

@Composable
fun PartyOrbitView(
    tree: OrgTree,
    unitId: String,
    partyColor: Color,
    focusedNodeId: String?,
    onFocusUnit: (String) -> Unit,
    onNodeClicked: (PartyTreeNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val unit = tree.units[unitId] ?: return
    Column(modifier) {
        Breadcrumb(tree, unit, onFocusUnit)
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            AnimatedContent(
                targetState = unit,
                contentKey = { it.id },
                transitionSpec = {
                    // Going in zooms through the ring; going up pulls back out.
                    val deeper = targetState.depth >= initialState.depth
                    (fadeIn(tween(260)) + scaleIn(tween(320), initialScale = if (deeper) 0.7f else 1.3f)) togetherWith
                        (fadeOut(tween(180)) + scaleOut(tween(260), targetScale = if (deeper) 1.3f else 0.7f))
                },
                label = "orbit"
            ) { u -> Orbit(tree, u, partyColor, focusedNodeId, onFocusUnit, onNodeClicked) }
        }
        Team(unit, onNodeClicked)
    }
}

@Composable
private fun Breadcrumb(tree: OrgTree, unit: OrgUnit, onFocusUnit: (String) -> Unit) {
    val trail = tree.ancestors(unit.id).mapNotNull(tree.units::get) + unit
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(40.dp)
    ) {
        items(trail, key = { it.id }) { u ->
            val current = u.id == unit.id
            Text(
                u.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                color = if (current) CivicColors.Navy else CivicColors.SaffronDeep,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = !current) { onFocusUnit(u.id) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
            if (!current) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CivicColors.TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Orbit(
    tree: OrgTree,
    unit: OrgUnit,
    partyColor: Color,
    focusedNodeId: String?,
    onFocusUnit: (String) -> Unit,
    onNodeClicked: (PartyTreeNode) -> Unit
) {
    val kids = remember(unit) { unit.childIds.mapNotNull(tree.units::get) }
    val slots = remember(kids.size) { orbitSlots(kids.size) }
    val rings = (slots.maxOfOrNull { it.ring } ?: -1) + 1
    // The child on the way to a searched/located post gets a bold spoke.
    val trail = remember(focusedNodeId, tree) {
        tree.unitOf(focusedNodeId ?: "")?.let { tree.ancestors(it.id).toSet() + it.id }.orEmpty()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val avail = minOf(maxWidth, maxHeight).value
        // First view fits the inner two rings at a readable size; outer rings are a pinch away.
        val fit = (avail / OrbitMetrics.extent(minOf(rings, 2))).coerceAtMost(1f)
        var scale by remember { mutableFloatStateOf(fit) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(fit * 0.6f, 2.5f)
                        offset += pan
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = fit; offset = Offset.Zero }) },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .requiredSize(OrbitMetrics.extent(rings).dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val c = center
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
                    repeat(rings) { r ->
                        drawCircle(CivicColors.Outline, radius = OrbitMetrics.radius(r).dp.toPx(), center = c, style = Stroke(1.dp.toPx(), pathEffect = dash))
                    }
                    slots.forEachIndexed { i, s ->
                        val on = kids[i].id in trail
                        drawLine(
                            color = if (on) partyColor else partyColor.copy(alpha = 0.22f),
                            start = c,
                            end = c + Offset(s.x.dp.toPx(), s.y.dp.toPx()),
                            strokeWidth = (if (on) 3f else 1.5f).dp.toPx()
                        )
                    }
                }
                CenterHead(unit, partyColor, Modifier.align(Alignment.Center)) { onNodeClicked(unit.head) }
                kids.forEachIndexed { i, k ->
                    OrbitNode(
                        unit = k,
                        partyColor = partyColor,
                        onPath = k.id in trail,
                        modifier = Modifier.align(Alignment.Center).offset(slots[i].x.dp, slots[i].y.dp),
                        onClick = { onFocusUnit(k.id) }
                    )
                }
            }
        }
        if (rings > 2) {
            Text(
                "${kids.size} units · pinch out to see all",
                style = MaterialTheme.typography.labelMedium,
                color = CivicColors.TextSecondary,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
                    .background(CivicColors.Surface.copy(alpha = 0.9f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun CenterHead(unit: OrgUnit, partyColor: Color, modifier: Modifier, onClick: () -> Unit) {
    val head = unit.head
    Column(
        modifier
            .width(176.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.border(3.dp, partyColor, CircleShape).padding(4.dp)) { PersonAvatar(head, 68.dp, tint = partyColor) }
        Text(unit.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = partyColor, maxLines = 1)
        Text(
            head.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = if (head.isNotFetched) CivicColors.TextSecondary else CivicColors.Navy,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(head.roleTitle.substringBefore(" ("), style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Disc centred in its box, label beneath; the box is symmetric so the disc sits exactly on the slot. */
@Composable
private fun OrbitNode(unit: OrgUnit, partyColor: Color, onPath: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val head = unit.head
    Column(
        modifier
            .size(OrbitMetrics.NODE_WIDTH.dp, (OrbitMetrics.NODE + 2 * OrbitMetrics.LABEL).dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(OrbitMetrics.LABEL.dp))
        Box {
            PersonAvatar(
                head,
                OrbitMetrics.NODE.dp,
                tint = partyColor,
                modifier = Modifier.border(
                    BorderStroke(if (onPath) 3.dp else 1.5.dp, if (head.isNotFetched) CivicColors.Outline else partyColor),
                    CircleShape
                )
            )
            if (unit.childIds.isNotEmpty()) {
                Surface(shape = CircleShape, color = partyColor, modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp)) {
                    Text(
                        "${unit.childIds.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Text(unit.title, style = MaterialTheme.typography.labelMedium, color = CivicColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        Text(head.displayName, style = MaterialTheme.typography.labelSmall, color = CivicColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** The centre unit's other office-bearers; the orbit shows only who leads each sub-unit. */
@Composable
private fun Team(unit: OrgUnit, onNodeClicked: (PartyTreeNode) -> Unit) {
    val team = unit.members.drop(1)
    if (team.isEmpty()) return
    Column(Modifier.fillMaxWidth().background(CivicColors.Surface).padding(vertical = 10.dp)) {
        Text(
            "${unit.title.uppercase()} TEAM · ${team.size}",
            style = MaterialTheme.typography.labelSmall,
            color = CivicColors.TextMuted,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(team, key = { it.id }) { n ->
                Surface(onClick = { onNodeClicked(n) }, shape = RoundedCornerShape(16.dp), color = CivicColors.Background, border = BorderStroke(1.dp, CivicColors.Outline)) {
                    Row(Modifier.padding(8.dp).widthIn(max = 220.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PersonAvatar(n, 34.dp)
                        Column {
                            Text(n.displayName, style = MaterialTheme.typography.titleSmall, color = CivicColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(n.roleTitle, style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
