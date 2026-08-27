package com.ourindia.app.ui.partystructure

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.drawText
import coil3.compose.AsyncImage
import com.ourindia.app.ui.theme.CivicColors
import kotlin.math.roundToInt

@Composable
fun PartyHierarchyCanvas(
    rootNodes: List<PartyTreeNode>,
    allNodes: List<PartyTreeNode>,
    partyMetadata: PartyMetadata,
    focusedNodeId: String?,
    onNodeClicked: (PartyTreeNode) -> Unit,
    onToggleExpand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(0.5f) }
    var panOffset by remember { mutableStateOf(Offset(200f, 80f)) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val cardWidthPx = 220f * density
    val cardHeightPx = 110f * density

    var hasCenteredInitiallyKey by remember { mutableStateOf("") }

    // Initial centering of topmost node on viewport ready / party switch
    LaunchedEffect(canvasSize, rootNodes, partyMetadata.name) {
        if (canvasSize != IntSize.Zero && rootNodes.isNotEmpty() && hasCenteredInitiallyKey != partyMetadata.name) {
            val rootNode = rootNodes.first()
            scale = 0.5f
            val nodePixelX = rootNode.canvasX * density
            val nodePixelY = rootNode.canvasY * density
            panOffset = Offset(
                x = (canvasSize.width / 2f) - (nodePixelX + (cardWidthPx / 2f)) * scale,
                y = (canvasSize.height / 2f) - (nodePixelY + (cardHeightPx / 2f)) * scale
            )
            hasCenteredInitiallyKey = partyMetadata.name
        }
    }

    // Auto pan ONLY if focusedNodeId changes — intentionally does NOT change scale.
    // User's current zoom is preserved when navigating to a focused node.
    LaunchedEffect(focusedNodeId, canvasSize) {
        if (focusedNodeId != null && canvasSize != IntSize.Zero) {
            val target = allNodes.firstOrNull { it.id == focusedNodeId }
            if (target != null) {
                // Do NOT set scale here — preserve user's current zoom
                val nodePixelX = target.canvasX * density
                val nodePixelY = target.canvasY * density
                panOffset = Offset(
                    x = (canvasSize.width / 2f) - (nodePixelX + (cardWidthPx / 2f)) * scale,
                    y = (canvasSize.height / 2f) - (nodePixelY + (cardHeightPx / 2f)) * scale
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(CivicColors.Background)
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // 0.15f allows zooming far out for large party hierarchies (15+ levels)
                    scale = (scale * zoom).coerceIn(0.15f, 2.5f)
                    panOffset += pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        // Double tap to reset or zoom in
                        if (scale > 1.2f) {
                            scale = 0.9f
                            panOffset = Offset(200f, 80f)
                        } else {
                            scale = 1.3f
                            panOffset = Offset(
                                (canvasSize.width / 2f) - tapOffset.x,
                                (canvasSize.height / 2f) - tapOffset.y
                            )
                        }
                    }
                )
            }
    ) {
        val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()

        // Grid pattern background for canvas feel
        CanvasGridBackground(scale = scale, panOffset = panOffset)

        // Hierarchy Connection Lines (Drawn behind nodes)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = panOffset.x
                    translationY = panOffset.y
                }
        ) {
            drawTreeConnectionLines(
                nodes = allNodes,
                partyColor = partyMetadata.color,
                textMeasurer = textMeasurer
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = panOffset.x
                    translationY = panOffset.y
                }
        ) {
            allNodes.forEach { node ->
                val isFocused = node.id == focusedNodeId
                val nodeWidth = 220.dp
                val nodeHeight = 110.dp

                Box(
                    modifier = Modifier
                        .offset(x = node.canvasX.dp, y = node.canvasY.dp)
                        .size(width = nodeWidth, height = nodeHeight)
                ) {
                    CanvasNodeCard(
                        node = node,
                        scale = scale,
                        isFocused = isFocused,
                        onNodeClick = { onNodeClicked(node) },
                        onToggleExpand = { onToggleExpand(node.id) }
                    )
                }
            }
        }

        // Canvas Floating Control Overlay (Zoom In, Zoom Out, Reset, Info Badge)
        CanvasControlsOverlay(
            scale = scale,
            onZoomIn = { scale = (scale * 1.2f).coerceAtMost(2.5f) },
            onZoomOut = { scale = (scale / 1.2f).coerceAtLeast(0.15f) },
            onResetView = {
                if (rootNodes.isNotEmpty()) {
                    val rootNode = rootNodes.first()
                    val nodePixelX = rootNode.canvasX * density
                    val nodePixelY = rootNode.canvasY * density
                    panOffset = Offset(
                        x = (canvasSize.width / 2f) - (nodePixelX + (cardWidthPx / 2f)) * scale,
                        y = (canvasSize.height / 2f) - (nodePixelY + (cardHeightPx / 2f)) * scale
                    )
                } else {
                    panOffset = Offset(180f * density, 80f * density)
                }
            },
            totalNodes = allNodes.size,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )

        // Subtle Canvas Tip Bar at Top
        Surface(
            color = CivicColors.Navy.copy(alpha = 0.85f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomOutMap,
                    contentDescription = null,
                    tint = CivicColors.Saffron,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Pinch to Zoom • Drag to Pan Canvas • Tap Leader for Profile",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** Subtle engineering grid background */
@Composable
private fun CanvasGridBackground(scale: Float, panOffset: Offset) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val gridSize = 40.dp.toPx() * scale
        if (gridSize < 10f) return@Canvas

        val startX = (panOffset.x % gridSize)
        val startY = (panOffset.y % gridSize)
        val gridColor = Color(0xFF0A1628).copy(alpha = 0.05f)

        var x = startX
        while (x < size.width) {
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
            x += gridSize
        }

        var y = startY
        while (y < size.height) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += gridSize
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTreeConnectionLines(
    nodes: List<PartyTreeNode>,
    partyColor: Color,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val nodeWidthPx = 220.dp.toPx()
    val nodeHeightPx = 110.dp.toPx()

    val nodeMap = nodes.associateBy { it.id }

    nodes.forEach { child ->
        if (child.parentId != null) {
            val parent = nodeMap[child.parentId]
            if (parent != null) {
                val startX = parent.canvasX.dp.toPx() + (nodeWidthPx / 2f)
                val startY = parent.canvasY.dp.toPx() + nodeHeightPx
                val endX = child.canvasX.dp.toPx() + (nodeWidthPx / 2f)
                val endY = child.canvasY.dp.toPx()

                val path = Path().apply {
                    moveTo(startX, startY)
                    val midY = (startY + endY) / 2f
                    cubicTo(
                        startX, midY,
                        endX, midY,
                        endX, endY
                    )
                }

                // Draw connecting bezier line (Opaque dashed connection matching component borders)
                drawPath(
                    path = path,
                    color = CivicColors.Navy,
                    style = Stroke(
                        width = 1.8f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                )

                // Calculate connection midpoint coordinates
                val midX = (startX + endX) / 2f
                val midY = (startY + endY) / 2f

                // Label showing connection context — uses jurisdictionLabel which covers all tiers
                val labelText = child.jurisdictionLabel.ifEmpty {
                    when {
                        child.level.tier <= 8 -> null // National-tier nodes don't need geographic labels
                        !child.district.isNullOrEmpty() -> child.district
                        !child.state.isNullOrEmpty() -> child.state
                        else -> null
                    }
                }.takeIf { it?.isNotEmpty() == true }

                if (!labelText.isNullOrEmpty()) {
                    val textStyle = androidx.compose.ui.text.TextStyle(
                        color = CivicColors.Navy.copy(alpha = 0.9f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    val textLayoutResult = textMeasurer.measure(
                        text = labelText,
                        style = textStyle
                    )
                    
                    // Draw centered annotation label slightly offset horizontally so it doesn't collide
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(
                            x = midX + 8f,
                            y = midY - (textLayoutResult.size.height / 2f)
                        )
                    )
                }

                // Draw connector dot at connection points
                drawCircle(
                    color = partyColor,
                    radius = 3.5f,
                    center = Offset(startX, startY)
                )
                drawCircle(
                    color = CivicColors.Navy.copy(alpha = 0.5f),
                    radius = 3.5f,
                    center = Offset(endX, endY)
                )
            }
        }
    }
}

/** Level-of-Detail Node Card (adapts appearance based on canvas zoom level) */
@Composable
private fun CanvasNodeCard(
    node: PartyTreeNode,
    scale: Float,
    isFocused: Boolean,
    onNodeClick: () -> Unit,
    onToggleExpand: () -> Unit
) {
    val isNotFetched = node.holderName.contains("Not yet fetched", ignoreCase = true) ||
                       node.holderName.contains("Leader data not yet available", ignoreCase = true)

    val borderColor = if (isFocused) CivicColors.Saffron else if (isNotFetched) CivicColors.Navy.copy(alpha = 0.25f) else CivicColors.Navy
    val borderWidth = if (isFocused) 3.dp else 1.5.dp

    Card(
        modifier = Modifier
            .fillMaxSize()
            .shadow(if (isFocused) 10.dp else 3.dp, RoundedCornerShape(10.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .clickable { onNodeClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFFFFF8E7) else if (isNotFetched) Color(0xFFF5F5F5) else Color.White
        )
    ) {
        // High & Medium Detail Mode (Normal to zoomed in)
        if (scale >= 0.55f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row: Level Badge & State/District
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = if (isNotFetched) CivicColors.Navy.copy(alpha = 0.35f) else node.level.badgeColor,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = node.level.name,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    val loc = node.state ?: "National HQ"
                    Text(
                        text = loc,
                        fontSize = 10.sp,
                        color = CivicColors.TextSecondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Middle: Photo Image (Level 1 & 2) or Emoji (Level 3+) & Leader Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isFirstTwoLevels = node.level.tier <= 2 // Show photos for top-tier national leaders only
                    val photoUrl = node.photoUrl

                    if (isFirstTwoLevels && !photoUrl.isNullOrEmpty() && !isNotFetched) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = node.holderName,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(1.dp, node.level.badgeColor, CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (isNotFetched) CivicColors.Navy.copy(alpha = 0.08f) else node.level.badgeColor.copy(alpha = 0.15f),
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    if (isNotFetched) CivicColors.Navy.copy(alpha = 0.2f) else node.level.badgeColor,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = if (isNotFetched) "❓" else node.photoEmoji, fontSize = 18.sp)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = node.holderName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isNotFetched) CivicColors.TextSecondary else CivicColors.Navy,
                            fontStyle = if (isNotFetched) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = node.roleTitle,
                            fontSize = 10.5.sp,
                            color = CivicColors.OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Bottom row: Jurisdiction details & Tap action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (node.district != null) {
                        Text(
                            text = "📍 ${node.district}",
                            fontSize = 9.sp,
                            color = if (isNotFetched) CivicColors.TextSecondary else CivicColors.Teal,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = "🇮🇳 All-India Mandate",
                            fontSize = 9.sp,
                            color = CivicColors.TextSecondary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isNotFetched) "Pending" else "View",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isNotFetched) CivicColors.TextSecondary else CivicColors.Saffron
                        )
                        if (!isNotFetched) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = CivicColors.Saffron,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Low Zoom LoD: Compact Overview Badge
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = if (isNotFetched) CivicColors.Navy.copy(alpha = 0.3f) else node.level.badgeColor,
                    shape = CircleShape,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isNotFetched) "?" else node.holderName.take(2).uppercase(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = node.holderName,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isNotFetched) CivicColors.TextSecondary else CivicColors.Navy,
                    fontStyle = if (isNotFetched) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/** Floating Zoom Controls & Reset Button */
@Composable
private fun CanvasControlsOverlay(
    scale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetView: () -> Unit,
    totalNodes: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, CivicColors.Navy.copy(alpha = 0.2f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = onZoomOut,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = CivicColors.Navy,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = "${(scale * 100).roundToInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CivicColors.Navy,
                modifier = Modifier.width(42.dp),
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = onZoomIn,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = CivicColors.Navy,
                    modifier = Modifier.size(18.dp)
                )
            }

            Divider(
                modifier = Modifier
                    .height(20.dp)
                    .width(1.dp),
                color = CivicColors.Navy.copy(alpha = 0.2f)
            )

            IconButton(
                onClick = onResetView,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "Reset Canvas",
                    tint = CivicColors.Saffron,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
