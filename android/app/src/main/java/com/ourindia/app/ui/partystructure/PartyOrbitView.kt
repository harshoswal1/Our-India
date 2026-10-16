package com.ourindia.app.ui.partystructure

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ourindia.app.ui.theme.CivicColors
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Orbit geometry, in dp from the centre at zoom 1. The selected unit's head sits in the
 * middle and its sub-units ring around it, so the structure spreads in every direction
 * instead of marching off the bottom of the screen like the chart.
 */
object OrbitMetrics {
    const val SLOT = 86f           // arc length each node gets along its ring at zoom 1
    const val R0 = 140f            // first ring
    const val GAP = 104f

    fun radius(ring: Int) = R0 + GAP * ring
    fun capacity(ring: Int) = floor(2 * PI * radius(ring) / SLOT).toInt()
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

// Vehicle-registration codes people already know; initials made MA both Maharashtra and Manipur.
private val STATE_CODES = mapOf(
    "Andhra Pradesh" to "AP", "Arunachal Pradesh" to "AR", "Assam" to "AS", "Bihar" to "BR", "Chhattisgarh" to "CG",
    "Goa" to "GA", "Gujarat" to "GJ", "Haryana" to "HR", "Himachal Pradesh" to "HP", "Jharkhand" to "JH",
    "Karnataka" to "KA", "Kerala" to "KL", "Madhya Pradesh" to "MP", "Maharashtra" to "MH", "Manipur" to "MN",
    "Meghalaya" to "ML", "Mizoram" to "MZ", "Nagaland" to "NL", "Odisha" to "OD", "Punjab" to "PB",
    "Rajasthan" to "RJ", "Sikkim" to "SK", "Tamil Nadu" to "TN", "Telangana" to "TG", "Tripura" to "TR",
    "Uttar Pradesh" to "UP", "Uttarakhand" to "UK", "West Bengal" to "WB", "Andaman & Nicobar" to "AN",
    "Chandigarh" to "CH", "Dadra & Nagar Haveli and Daman & Diu" to "DD", "Delhi" to "DL", "Jammu & Kashmir" to "JK",
    "Ladakh" to "LA", "Lakshadweep" to "LD", "Puducherry" to "PY"
)

/** "Maharashtra" → "MH", "Pune City" → "PC": what fits in a small disc at overview zoom. */
fun unitCode(title: String): String {
    STATE_CODES[title]?.let { return it }
    val words = title.split(' ', '-', '&', '/').filter { it.isNotBlank() && it.first().isLetter() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2)
        else -> "${words[0][0]}${words[1][0]}"
    }.uppercase()
}

/** Codes for siblings, lengthened where two would read the same ("Pune East"/"Pune West" → "PUE"/"PUW"). */
fun unitCodes(titles: List<String>): List<String> {
    val base = titles.map(::unitCode)
    val clash = base.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    val longer = titles.mapIndexed { i, t ->
        if (base[i] !in clash) base[i]
        else t.split(' ', '-', '&', '/').filter { it.isNotBlank() && it.first().isLetter() }
            .let { w -> if (w.size > 1) w[0].take(2) + w.last().first() else w.firstOrNull()?.take(3) ?: "?" }.uppercase()
    }
    // Still the same (e.g. two identically named units): number them.
    val seen = HashMap<String, Int>()
    return longer.map { c -> val n = seen.merge(c, 1, Int::plus)!!; if (longer.count { it == c } > 1) "$c$n" else c }
}

/**
 * Map-style detail: nodes keep their size while zoom spreads them apart, and text appears
 * once there is room for it. Scaling the whole drawing shrank 35 labels into a smear.
 */
private enum class Detail(val disc: Int, val width: Int) { DOT(32, 32), LABEL(40, 84), FULL(46, 108) }

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
        Header(tree, unit, partyColor, onFocusUnit, onNodeClicked)
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
            ) { u -> Orbit(tree, u, partyColor, focusedNodeId, onFocusUnit) }
        }
        Team(unit, onNodeClicked)
    }
}

/** Trail back up, and the head of the unit in the middle; tap for their card. */
@Composable
private fun Header(tree: OrgTree, unit: OrgUnit, partyColor: Color, onFocusUnit: (String) -> Unit, onNodeClicked: (PartyTreeNode) -> Unit) {
    val head = unit.head
    val trail = tree.ancestors(unit.id).mapNotNull(tree.units::get)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        if (trail.isNotEmpty()) {
            LazyRow(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(32.dp)) {
                items(trail, key = { it.id }) { u ->
                    Text(
                        u.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = CivicColors.SaffronDeep,
                        modifier = Modifier.clip(CircleShape).clickable { onFocusUnit(u.id) }.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CivicColors.TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onNodeClicked(head) }.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.border(2.dp, partyColor, CircleShape).padding(2.dp)) { PersonAvatar(head, 40.dp, tint = partyColor) }
            Column(Modifier.weight(1f)) {
                Text(unit.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = partyColor, maxLines = 1)
                Text(head.displayName, style = MaterialTheme.typography.titleMedium, color = if (head.isNotFetched) CivicColors.TextSecondary else CivicColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(head.roleTitle.substringBefore(" ("), style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (unit.childIds.isNotEmpty()) {
                Text("${unit.childIds.size} units", style = MaterialTheme.typography.labelMedium, color = CivicColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun Orbit(tree: OrgTree, unit: OrgUnit, partyColor: Color, focusedNodeId: String?, onFocusUnit: (String) -> Unit) {
    val kids = remember(unit) { unit.childIds.mapNotNull(tree.units::get) }
    val slots = remember(kids.size) { orbitSlots(kids.size) }
    val codes = remember(kids) { unitCodes(kids.map { it.title }) }
    val outer = slots.maxOfOrNull { it.ring }?.let(OrbitMetrics::radius) ?: (OrbitMetrics.R0 * 0.6f)
    // The child on the way to a searched/located post gets the only spoke.
    val trail = remember(focusedNodeId, tree) {
        tree.unitOf(focusedNodeId ?: "")?.let { tree.ancestors(it.id).toSet() + it.id }.orEmpty()
    }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val dp = with(androidx.compose.ui.platform.LocalDensity.current) { 1.dp.toPx() }
        // Start on the whole orbit with room around it: a clean overview, and empty canvas to pinch on.
        val fit = ((minOf(maxWidth, maxHeight).value / 2 - 24f) / (outer + 22f)).coerceIn(0.2f, 1.2f)
        val minScale = fit * 0.8f
        val maxScale = 3f
        var scale by remember { mutableFloatStateOf(fit) }
        var pan by remember { mutableStateOf(Offset.Zero) }   // px: orbit centre relative to box centre
        val detail by remember {
            derivedStateOf {
                val spacing = OrbitMetrics.SLOT * scale
                when {
                    spacing >= 112f -> Detail.FULL
                    spacing >= 88f -> Detail.LABEL
                    else -> Detail.DOT
                }
            }
        }

        // Zoom by [factor] keeping the point at [at] (px from the box centre) under the finger.
        fun zoomBy(at: Offset, factor: Float, drag: Offset = Offset.Zero) {
            val next = (scale * factor).coerceIn(minScale, maxScale)
            val f = next / scale
            val limit = (outer * next + 80f) * dp
            val p = at - (at - pan) * f + drag
            pan = Offset(p.x.coerceIn(-limit, limit), p.y.coerceIn(-limit, limit))
            scale = next
        }
        fun animateZoom(at: Offset, factor: Float) = scope.launch {
            var last = 1f
            animate(1f, factor, animationSpec = tween(220)) { v, _ -> zoomBy(at, v / last); last = v }
        }
        fun animateFit() = scope.launch {
            val s0 = scale
            val p0 = pan
            animate(0f, 1f, animationSpec = tween(260)) { t, _ -> scale = s0 + (fit - s0) * t; pan = p0 * (1 - t) }
        }

        Box(
            Modifier
                .fillMaxSize()
                // Initial pass: the canvas sees two fingers (or a drag past slop) before the
                // nodes do, so a pinch that starts on a circle zooms instead of being eaten by it.
                .pointerInput(fit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var moving = false
                        var travel = Offset.Zero
                        var event: PointerEvent
                        do {
                            event = awaitPointerEvent(PointerEventPass.Initial)
                            val zoom = event.calculateZoom()
                            val drag = event.calculatePan()
                            if (!moving) {
                                travel += drag
                                moving = event.changes.count { it.pressed } > 1 || travel.getDistance() > viewConfiguration.touchSlop
                            }
                            if (moving) {
                                val c = event.calculateCentroid(useCurrent = true)
                                if (c.isSpecified) zoomBy(c - Offset(size.width / 2f, size.height / 2f), zoom, drag)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(fit) {
                    detectTapGestures(onDoubleTap = { p -> animateZoom(p - Offset(size.width / 2f, size.height / 2f), 2f) })
                }
                .drawBehind {
                    val c = Offset(boxW / 2 + pan.x, boxH / 2 + pan.y)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(5 * dp, 6 * dp))
                    slots.map { it.ring }.distinct().forEach { r ->
                        drawCircle(CivicColors.Outline, radius = OrbitMetrics.radius(r) * scale * dp, center = c, style = Stroke(1 * dp, pathEffect = dash))
                    }
                    slots.forEachIndexed { i, s ->
                        if (kids[i].id in trail) drawLine(partyColor, c, c + Offset(s.x, s.y) * (scale * dp), strokeWidth = 2.5f * dp)
                    }
                }
        ) {
            // The head, as a disc in the middle; their name is in the header above.
            val centre = when (detail) { Detail.DOT -> 52; Detail.LABEL -> 60; Detail.FULL -> 72 }
            Box(
                Modifier
                    .offset {
                        val half = (centre + 6) * dp / 2
                        IntOffset((boxW / 2 + pan.x - half).roundToInt(), (boxH / 2 + pan.y - half).roundToInt())
                    }
                    .border(3.dp, partyColor, CircleShape)
                    .padding(3.dp)
            ) { PersonAvatar(unit.head, centre.dp, tint = partyColor) }

            kids.forEachIndexed { i, k ->
                val s = slots[i]
                OrbitNode(
                    unit = k,
                    partyColor = partyColor,
                    detail = detail,
                    code = codes[i],
                    onPath = k.id in trail,
                    onClick = { onFocusUnit(k.id) },
                    // The offset lambda runs at layout, so a pinch moves nodes without recomposing them;
                    // the disc's centre sits on the slot.
                    modifier = Modifier.offset {
                        val x = boxW / 2 + pan.x + s.x * scale * dp - detail.width * dp / 2
                        val y = boxH / 2 + pan.y + s.y * scale * dp - detail.disc * dp / 2
                        IntOffset(x.roundToInt(), y.roundToInt())
                    }
                )
            }
        }

        Column(Modifier.align(Alignment.BottomEnd).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ZoomButton(Icons.Outlined.Add, "Zoom in") { animateZoom(Offset.Zero, 1.6f) }
            ZoomButton(Icons.Outlined.Remove, "Zoom out") { animateZoom(Offset.Zero, 1 / 1.6f) }
            ZoomButton(Icons.Outlined.CenterFocusWeak, "Show all") { animateFit() }
        }
        if (detail == Detail.DOT && kids.isNotEmpty()) {
            Text(
                "Pinch or double-tap to read names",
                style = MaterialTheme.typography.labelMedium,
                color = CivicColors.TextSecondary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .background(CivicColors.Surface.copy(alpha = 0.92f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ZoomButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = CivicColors.Surface, border = BorderStroke(1.dp, CivicColors.Outline), shadowElevation = 2.dp) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = CivicColors.Navy, modifier = Modifier.size(20.dp))
        }
    }
}

/** Disc with the unit's code; title and holder under it when zoom leaves room. */
@Composable
private fun OrbitNode(unit: OrgUnit, partyColor: Color, detail: Detail, code: String, onPath: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val head = unit.head
    val open = head.isNotFetched
    Column(
        modifier
            .width(detail.width.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = unit.title },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Box(
                Modifier
                    .size(detail.disc.dp)
                    .background(if (open) CivicColors.SurfaceVariant else partyColor.copy(alpha = if (onPath) 0.28f else 0.12f), CircleShape)
                    .border(if (onPath) 2.5.dp else 1.5.dp, if (open) CivicColors.Outline else partyColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    code,
                    color = if (open) CivicColors.TextMuted else partyColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = (detail.disc * 0.34f).sp
                )
            }
            if (detail != Detail.DOT && unit.childIds.isNotEmpty()) {
                Surface(shape = CircleShape, color = partyColor, modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp)) {
                    Text("${unit.childIds.size}", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 5.dp))
                }
            }
        }
        if (detail != Detail.DOT) {
            Text(unit.title, style = MaterialTheme.typography.labelMedium, color = CivicColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
        }
        if (detail == Detail.FULL) {
            Text(head.displayName, style = MaterialTheme.typography.labelSmall, color = CivicColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
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
