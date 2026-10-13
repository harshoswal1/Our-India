package com.ourindia.app.ui.partystructure

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ourindia.app.ui.theme.CivicColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val MIN_SCALE = 0.2f
private const val MAX_SCALE = 1.6f
// Below this zoom card text is under ~7sp on screen, so cards switch to a big-type overview.
private const val COMPACT_BELOW = 0.55f

/**
 * Pannable, zoomable org chart of party units.
 *
 * Transform: screen = offset + contentPx * scale, pivot at the content's top-left.
 * The previous canvas scaled about the layer centre while computing pans as if the
 * pivot were top-left, so pinches drifted and "centre on node" missed its target.
 */
@Composable
fun PartyHierarchyCanvas(
    chart: HierarchyChart,
    partyColor: Color,
    focusedNodeId: String?,
    focusRequest: Int,
    onNodeClicked: (PartyTreeNode) -> Unit,
    onToggleUnit: (String) -> Unit,
    onSetAllExpanded: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Space reserved at the top for overlays (the search field). */
    contentTopInset: Dp = 0.dp
) {
    val density = LocalDensity.current.density
    val topInsetPx = with(LocalDensity.current) { contentTopInset.toPx() }
    val scope = rememberCoroutineScope()

    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val motion = remember { arrayOfNulls<Job>(1) }
    val compact by remember { derivedStateOf { scale < COMPACT_BELOW } }
    var showHint by remember { mutableStateOf(true) }

    val rootId = chart.units.firstOrNull { it.unit.parentId == null }?.unit?.id
    val focusedUnitId = focusedNodeId?.let { id -> chart.units.firstOrNull { p -> p.unit.members.any { it.id == id } }?.unit?.id }
    val allExpanded = chart.units.none { it.collapsed }

    // Keep at least a strip of the chart on screen so it can't be flung into the void.
    fun bounded(o: Offset, s: Float): Offset {
        val keep = 96f * density
        val w = chart.width * density * s
        val h = chart.height * density * s
        return Offset(
            o.x.coerceIn(min(keep - w, viewport.width - keep), max(keep - w, viewport.width - keep)),
            o.y.coerceIn(min(keep - h, viewport.height - keep), max(keep - h, viewport.height - keep))
        )
    }

    fun animateTo(targetScale: Float, targetOffset: Offset) {
        motion[0]?.cancel()
        motion[0] = scope.launch {
            val s0 = scale
            val o0 = offset
            animate(0f, 1f, animationSpec = tween(450, easing = FastOutSlowInEasing)) { t, _ ->
                scale = s0 + (targetScale - s0) * t
                offset = o0 + (targetOffset - o0) * t
            }
        }
    }

    fun zoomAround(pivot: Offset, targetScale: Float) {
        val s = targetScale.coerceIn(MIN_SCALE, MAX_SCALE)
        animateTo(s, bounded(pivot - (pivot - offset) * (s / scale), s))
    }

    fun fitAll() {
        if (viewport == IntSize.Zero || chart.units.isEmpty()) return
        val s = min(viewport.width / (chart.width * density), viewport.height / (chart.height * density))
            .coerceIn(MIN_SCALE, 1f)
        val w = chart.width * density * s
        val h = chart.height * density * s
        animateTo(s, Offset((viewport.width - w) / 2f, max(0f, (viewport.height - h) / 2f)))
    }

    /** Puts a unit (or one of its rows) at the upper-middle of the viewport. */
    fun focusOn(unitId: String, nodeId: String?, animated: Boolean) {
        val placed = chart.byId[unitId] ?: return
        val s = scale.coerceIn(0.8f, 1.1f)
        val row = placed.unit.members.indexOfFirst { it.id == nodeId }.coerceAtLeast(0)
        val rowCenter = placed.y + ChartMetrics.HEADER_HEIGHT +
            if (row == 0) ChartMetrics.HEAD_ROW_HEIGHT / 2f
            else ChartMetrics.HEAD_ROW_HEIGHT + (row - 0.5f) * ChartMetrics.MEMBER_ROW_HEIGHT
        val target = bounded(
            Offset(
                viewport.width / 2f - placed.centerX * density * s,
                viewport.height * 0.4f - rowCenter * density * s
            ),
            s
        )
        if (animated) animateTo(s, target) else { scale = s; offset = target }
    }

    fun showRoot(id: String) {
        val placed = chart.byId[id] ?: return
        val s = (viewport.width / ((ChartMetrics.UNIT_WIDTH + 48f) * density)).coerceIn(0.6f, 1f)
        scale = s
        offset = Offset(viewport.width / 2f - placed.centerX * density * s, topInsetPx - placed.y * density * s)
    }

    // Initial placement per party, and every explicit locate request (search, map, GPS).
    // Keyed on readiness, not size: the keyboard opening resizes the viewport and must
    // not yank the chart back to the last focused node mid-search.
    var positionedFor by remember { mutableStateOf<String?>(null) }
    val ready = viewport != IntSize.Zero
    LaunchedEffect(ready, rootId, focusRequest) {
        if (!ready || rootId == null) return@LaunchedEffect
        val firstPlacement = positionedFor != rootId
        positionedFor = rootId
        when {
            focusedUnitId != null && (focusRequest > 0) -> focusOn(focusedUnitId, focusedNodeId, animated = !firstPlacement)
            firstPlacement -> showRoot(rootId)
        }
    }

    // Folding a unit re-centres every ancestor over its new set of children, which shifted
    // the whole chart under the finger. Pin the unit the user touched to its old screen spot.
    val anchor = remember { arrayOfNulls<String>(1) }
    val previousChart = remember { arrayOfNulls<HierarchyChart>(1) }
    SideEffect {
        val prev = previousChart[0]
        if (prev !== chart && prev != null) {
            val id = anchor[0] ?: rootId
            val before = id?.let { prev.byId[it] }
            val after = id?.let { chart.byId[it] }
            if (before != null && after != null) {
                offset += Offset((before.x - after.x) * density * scale, (before.y - after.y) * density * scale)
            }
        }
        previousChart[0] = chart
        anchor[0] = null
    }

    LaunchedEffect(Unit) {
        delay(5000)
        showHint = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(CivicColors.Background)
            .onSizeChanged { viewport = it }
            .pointerInput(chart) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    motion[0]?.cancel()
                    showHint = false
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var pastSlop = false
                    var multiTouch = false
                    var zoomSinceDown = 1f
                    var panSinceDown = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) break
                        if (event.changes.size > 1) multiTouch = true
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        if (!pastSlop) {
                            zoomSinceDown *= zoomChange
                            panSinceDown += panChange
                            val zoomMotion = abs(1 - zoomSinceDown) * event.calculateCentroidSize(useCurrent = false)
                            pastSlop = zoomMotion > viewConfiguration.touchSlop ||
                                panSinceDown.getDistance() > viewConfiguration.touchSlop
                        }
                        if (pastSlop) {
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val newScale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
                            offset = bounded(centroid - (centroid - offset) * (newScale / scale) + panChange, newScale)
                            scale = newScale
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                        event.changes.firstOrNull()?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                    } while (event.changes.any { it.pressed })

                    if (pastSlop && !multiTouch) {
                        val v = tracker.calculateVelocity()
                        val speed = hypot(v.x, v.y)
                        if (speed > 400f) {
                            val dir = Offset(v.x / speed, v.y / speed)
                            motion[0] = scope.launch {
                                val start = offset
                                animateDecay(0f, speed, FloatExponentialDecaySpec()) { dist, _ ->
                                    offset = bounded(start + dir * dist, scale)
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { p -> zoomAround(p, if (scale >= 1.2f) scale / 2f else scale * 2f) }
                )
            }
    ) {
        DotGrid(scale = { scale }, offset = { offset })

        if (chart.units.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(chart.width.dp, chart.height.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            ) {
                Connectors(
                    chart = chart,
                    highlightUnitId = focusedUnitId,
                    highlightColor = partyColor,
                    scale = { scale }
                )
                chart.units.forEach { placed ->
                    key(placed.unit.id) {
                        UnitCard(
                            placed = placed,
                            compact = compact,
                            isFocusedUnit = placed.unit.id == focusedUnitId,
                            focusedNodeId = focusedNodeId,
                            partyColor = partyColor,
                            onNodeClicked = onNodeClicked,
                            onToggle = {
                                anchor[0] = placed.unit.id
                                onToggleUnit(placed.unit.id)
                            },
                            modifier = Modifier
                                .offset(placed.x.dp, placed.y.dp)
                                .size(ChartMetrics.UNIT_WIDTH.dp, placed.height.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Surface(shape = CircleShape, color = CivicColors.Navy.copy(alpha = 0.9f)) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.TouchApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("Pinch to zoom · drag to explore", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
        }

        ChartControls(
            allExpanded = allExpanded,
            onZoomIn = { zoomAround(Offset(viewport.width / 2f, viewport.height / 2f), scale * 1.4f) },
            onZoomOut = { zoomAround(Offset(viewport.width / 2f, viewport.height / 2f), scale / 1.4f) },
            onFit = ::fitAll,
            onToggleAll = {
                anchor[0] = rootId
                onSetAllExpanded(!allExpanded)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

/** Dot grid in screen space; tracks pan/zoom so the canvas reads as one surface. */
@Composable
private fun DotGrid(scale: () -> Float, offset: () -> Offset) {
    Canvas(Modifier.fillMaxSize()) {
        // Double the spacing as you zoom out so the dot count stays in the hundreds.
        var step = 28.dp.toPx() * scale()
        while (step < 20.dp.toPx()) step *= 2f
        val o = offset()
        val color = CivicColors.Navy.copy(alpha = 0.07f)
        val r = 1.2.dp.toPx()
        var x = o.x.mod(step)
        while (x < size.width) {
            var y = o.y.mod(step)
            while (y < size.height) {
                drawCircle(color, r, Offset(x, y))
                y += step
            }
            x += step
        }
    }
}

/** Orthogonal elbow connectors: parent stem, a bus across the children, a drop to each. */
@Composable
private fun Connectors(
    chart: HierarchyChart,
    highlightUnitId: String?,
    highlightColor: Color,
    scale: () -> Float
) {
    val highlightPath = remember(chart, highlightUnitId) {
        generateSequence(highlightUnitId) { chart.byId[it]?.unit?.parentId }.toSet()
    }
    Canvas(Modifier.fillMaxSize()) {
        val d = density
        // Divide by zoom so lines stay ~1.5dp on screen instead of thinning to hairlines.
        val base = 1.5.dp.toPx() / scale().coerceAtMost(1f)
        val lineColor = CivicColors.Navy.copy(alpha = 0.22f)
        chart.units.forEach { parent ->
            if (parent.collapsed) return@forEach
            val kids = parent.unit.childIds.mapNotNull { chart.byId[it] }
            if (kids.isEmpty()) return@forEach
            val px = parent.centerX * d
            val py = parent.bottom * d
            val busY = py + ChartMetrics.V_GAP * d / 2f
            val onPath = parent.unit.id in highlightPath && highlightUnitId != parent.unit.id
            drawLine(if (onPath) highlightColor else lineColor, Offset(px, py), Offset(px, busY), if (onPath) base * 1.6f else base, StrokeCap.Round)
            drawLine(
                lineColor,
                Offset(min(px, kids.first().centerX * d), busY),
                Offset(max(px, kids.last().centerX * d), busY),
                base,
                StrokeCap.Round
            )
            kids.forEach { kid ->
                val kx = kid.centerX * d
                val kidOnPath = kid.unit.id in highlightPath
                if (kidOnPath) {
                    drawLine(highlightColor, Offset(px, busY), Offset(kx, busY), base * 1.6f, StrokeCap.Round)
                }
                drawLine(
                    if (kidOnPath) highlightColor else lineColor,
                    Offset(kx, busY),
                    Offset(kx, kid.y * d),
                    if (kidOnPath) base * 1.6f else base,
                    StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun UnitCard(
    placed: PlacedUnit,
    compact: Boolean,
    isFocusedUnit: Boolean,
    focusedNodeId: String?,
    partyColor: Color,
    onNodeClicked: (PartyTreeNode) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unit = placed.unit
    val levelColor = unit.level.badgeColor
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CivicColors.Surface,
        border = if (isFocusedUnit) BorderStroke(2.dp, partyColor) else BorderStroke(1.dp, CivicColors.Outline),
        shadowElevation = if (isFocusedUnit) 6.dp else 1.dp,
        modifier = modifier
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChartMetrics.HEADER_HEIGHT.dp)
                    .background(levelColor.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(8.dp).background(levelColor, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = unit.level.groupLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = levelColor
                )
                if (unit.title != unit.level.groupLabel) {
                    Text(
                        text = "  ·  ${unit.title}",
                        style = MaterialTheme.typography.labelMedium,
                        color = CivicColors.Navy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    text = "${unit.members.size} ${if (unit.members.size == 1) "post" else "posts"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CivicColors.TextMuted
                )
            }

            if (compact) {
                // Overview: one big label in the space the rows would take.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = unit.title,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = CivicColors.Navy,
                        textAlign = TextAlign.Center,
                        lineHeight = 34.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                HeadRow(
                    node = unit.head,
                    highlighted = unit.head.id == focusedNodeId,
                    onClick = { onNodeClicked(unit.head) }
                )
                unit.members.drop(1).forEach { member ->
                    MemberRow(
                        node = member,
                        highlighted = member.id == focusedNodeId,
                        onClick = { onNodeClicked(member) }
                    )
                }
                if (unit.childIds.isEmpty()) Spacer(Modifier.height(ChartMetrics.BOTTOM_PADDING.dp))
            }

            if (unit.childIds.isNotEmpty()) {
                HorizontalDivider(color = CivicColors.Outline)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ChartMetrics.FOOTER_HEIGHT.dp - 1.dp)
                        .clickable(onClick = onToggle)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val n = unit.childIds.size
                    Text(
                        text = if (placed.collapsed) "Show $n ${if (n == 1) "unit" else "units"} below" else "Hide units below",
                        style = MaterialTheme.typography.labelMedium,
                        color = CivicColors.SaffronDeep
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (placed.collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = null,
                        tint = CivicColors.SaffronDeep,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeadRow(node: PartyTreeNode, highlighted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChartMetrics.HEAD_ROW_HEIGHT.dp)
            .background(if (highlighted) CivicColors.Saffron.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PersonAvatar(node = node, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(
                text = node.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontSize = 15.sp,
                color = if (node.isNotFetched) CivicColors.TextMuted else CivicColors.Navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = node.roleTitle,
                style = MaterialTheme.typography.bodySmall,
                color = CivicColors.SaffronDeep,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MemberRow(node: PartyTreeNode, highlighted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChartMetrics.MEMBER_ROW_HEIGHT.dp)
            .background(if (highlighted) CivicColors.Saffron.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PersonAvatar(node = node, size = 28.dp)
        Column(Modifier.weight(1f)) {
            Text(
                text = node.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (node.isNotFetched) FontWeight.Normal else FontWeight.SemiBold,
                color = if (node.isNotFetched) CivicColors.TextMuted else CivicColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = node.roleTitle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = CivicColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChartControls(
    allExpanded: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: () -> Unit,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CivicColors.Surface,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, CivicColors.Outline),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ControlButton(Icons.Default.Add, "Zoom in", onZoomIn)
            ControlButton(Icons.Default.Remove, "Zoom out", onZoomOut)
            HorizontalDivider(Modifier.width(28.dp), color = CivicColors.Outline)
            ControlButton(Icons.Default.FitScreen, "Fit whole chart", onFit)
            ControlButton(
                if (allExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                if (allExpanded) "Fold to state level" else "Unfold every unit",
                onToggleAll
            )
        }
    }
}

@Composable
private fun ControlButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, tint = CivicColors.Navy, modifier = Modifier.size(22.dp))
    }
}
