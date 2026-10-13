package com.ourindia.app.ui.partystructure

import android.view.Gravity
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ourindia.app.data.geo.LocalGeographicResolver
import com.ourindia.app.data.geo.ResolvedLocation
import com.ourindia.app.ui.theme.CivicColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.upcase
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Map tab: a state/district choropleth of the party's organisation.
 *
 * The map draws the bundled boundary files on its own minimal style instead of
 * OpenFreeMap's street tiles. The tile style needed a network round-trip before any
 * layer could exist, so offline the map stayed blank and long-press did nothing; and a
 * street map can't show where a party is organised anyway. Only label glyphs are
 * fetched — offline the labels drop out and everything else still works.
 */
@Composable
fun PartyMapExplorer(
    partyMetadata: PartyMetadata,
    resolver: LocalGeographicResolver,
    stateGeoDataList: List<StateGeoData>,
    allNodes: List<PartyTreeNode>,
    selectedState: String?,
    selectedDistrict: String?,
    resolvedLocation: ResolvedLocation?,
    onStateSelected: (String?) -> Unit,
    onDistrictSelected: (String?) -> Unit,
    onPositionClicked: (PartyTreeNode) -> Unit,
    onMapLongPressed: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()

        // Panel rests at one of three heights: peek (header only), half, or nearly full.
        val stops = with(density) { listOf(112.dp.toPx(), (maxHeight * 0.45f).toPx(), (maxHeight * 0.9f).toPx()) }
        var panelPx by remember { mutableFloatStateOf(stops[1]) }
        LaunchedEffect(stops[2]) { panelPx = panelPx.coerceIn(stops[0], stops[2]) }

        fun settle(velocity: Float) {
            val projected = panelPx - velocity * 0.15f
            val target = stops.minBy { kotlin.math.abs(it - projected) }
            scope.launch { animate(panelPx, target, animationSpec = tween(260)) { v, _ -> panelPx = v } }
        }

        IndiaMap(
            resolver = resolver,
            partyColor = partyMetadata.color,
            stateGeoDataList = stateGeoDataList,
            selectedState = selectedState,
            selectedDistrict = selectedDistrict,
            pin = resolvedLocation,
            // Fit selections into the map area left above a half-height panel.
            bottomPaddingPx = { panelPx.coerceAtMost(stops[1]).toInt() },
            onStateTapped = { if (!it.equals(selectedState, ignoreCase = true)) onStateSelected(it) },
            onDistrictTapped = onDistrictSelected,
            onLongPress = onMapLongPressed
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GeoBreadcrumb(
                selectedState = selectedState,
                selectedDistrict = selectedDistrict,
                onIndia = { onStateSelected(null) },
                onState = { onDistrictSelected(null) }
            )
            PresenceLegend(partyMetadata.color)
        }

        MapPanel(
            height = with(density) { panelPx.toDp() },
            party = partyMetadata,
            stateGeoDataList = stateGeoDataList,
            allNodes = allNodes,
            selectedState = selectedState,
            selectedDistrict = selectedDistrict,
            onDrag = { delta -> panelPx = (panelPx - delta).coerceIn(stops[0], stops[2]) },
            onDragStopped = ::settle,
            onHandleTap = {
                val target = if (panelPx > stops[1] + 1f) stops[1] else stops[2]
                scope.launch { animate(panelPx, target, animationSpec = tween(260)) { v, _ -> panelPx = v } }
            },
            onStateSelected = onStateSelected,
            onDistrictSelected = onDistrictSelected,
            onPositionClicked = onPositionClicked,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ── MapLibre ──────────────────────────────────────────────────────────────────

private const val STATES_SRC = "states"
private const val DISTRICTS_SRC = "districts"
private const val PIN_SRC = "pin"
private const val STATE_FILL = "state-fill"
private const val STATE_LINE = "state-line"
private const val STATE_SELECTED_LINE = "state-selected-line"
private const val STATE_LABEL = "state-label"
private const val DISTRICT_FILL = "district-fill"
private const val DISTRICT_LINE = "district-line"
private const val DISTRICT_SELECTED_LINE = "district-selected-line"
private const val DISTRICT_LABEL = "district-label"
private const val PIN_LAYER = "pin-layer"

private val INDIA_BOUNDS = LatLngBounds.from(37.6, 97.6, 6.4, 68.0)
private const val EMPTY_FC = """{"type":"FeatureCollection","features":[]}"""
private val LABEL_FONT = arrayOf("Noto Sans Regular")
private val LAND = Color(0xFFFBFAF7)
private val STYLE_JSON = """
    {"version":8,"name":"our-india",
     "glyphs":"https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
     "sources":{},
     "layers":[{"id":"water","type":"background","paint":{"background-color":"#DDE7EE"}}]}
""".trimIndent()

@Composable
private fun IndiaMap(
    resolver: LocalGeographicResolver,
    partyColor: Color,
    stateGeoDataList: List<StateGeoData>,
    selectedState: String?,
    selectedDistrict: String?,
    pin: ResolvedLocation?,
    bottomPaddingPx: () -> Int,
    onStateTapped: (String) -> Unit,
    onDistrictTapped: (String) -> Unit,
    onLongPress: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    // TextureView mode: the default SurfaceView ignores Compose clipping and alpha, so
    // rounded corners, the sliding panel and screen transitions showed the map through.
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply { onCreate(null) }
    }

    // The old wrapper never forwarded pause/resume, so the GL surface kept running in the
    // background and could come back black. Mirror the host lifecycle instead.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    val latestState by rememberUpdatedState(selectedState)
    val latestOnState by rememberUpdatedState(onStateTapped)
    val latestOnDistrict by rememberUpdatedState(onDistrictTapped)
    val latestOnLongPress by rememberUpdatedState(onLongPress)
    val latestPadding by rememberUpdatedState(bottomPaddingPx)

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    LaunchedEffect(mapView) {
        val statesJson = withContext(Dispatchers.IO) { resolver.readGeoJson(LocalGeographicResolver.STATES_ASSET) }
        val districtsJson = withContext(Dispatchers.IO) { resolver.readGeoJson(LocalGeographicResolver.DISTRICTS_ASSET) }
        mapView.getMapAsync { m ->
            m.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                logoGravity = Gravity.TOP or Gravity.END
                attributionGravity = Gravity.TOP or Gravity.END
                val gap = (12 * density).toInt()
                setLogoMargins(0, gap, (48 * density).toInt(), 0)
                setAttributionMargins(0, gap, gap, 0)
            }
            m.setMinZoomPreference(2.5)
            m.setMaxZoomPreference(11.0)
            m.setLatLngBoundsForCameraTarget(INDIA_BOUNDS)
            m.setStyle(Style.Builder().fromJson(STYLE_JSON)) { s ->
                s.addSource(GeoJsonSource(STATES_SRC, statesJson ?: EMPTY_FC))
                s.addSource(GeoJsonSource(DISTRICTS_SRC, districtsJson ?: EMPTY_FC))
                s.addSource(GeoJsonSource(PIN_SRC, EMPTY_FC))
                addBoundaryLayers(s)
                val pad = (24 * density).toInt()
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(INDIA_BOUNDS, pad, (96 * density).toInt(), pad, latestPadding() + pad))

                m.addOnMapClickListener { point ->
                    val screen = m.projection.toScreenLocation(point)
                    if (latestState != null) {
                        m.queryRenderedFeatures(screen, DISTRICT_FILL).firstOrNull()
                            ?.let { f -> runCatching { f.getStringProperty("district") }.getOrNull() }
                            ?.let { latestOnDistrict(it); return@addOnMapClickListener true }
                    }
                    m.queryRenderedFeatures(screen, STATE_FILL).firstOrNull()
                        ?.let { f -> runCatching { f.getStringProperty("state") }.getOrNull() }
                        ?.let(latestOnState)
                    true
                }
                m.addOnMapLongClickListener { point ->
                    s.getSourceAs<GeoJsonSource>(PIN_SRC)?.setGeoJson(Point.fromLngLat(point.longitude, point.latitude))
                    latestOnLongPress(point.latitude, point.longitude)
                    true
                }
                map = m
                style = s
            }
        }
    }

    // Choropleth: tint each state by how much of the party's structure sits there.
    LaunchedEffect(style, stateGeoDataList, partyColor) {
        val s = style ?: return@LaunchedEffect
        val stops = stateGeoDataList.map {
            Expression.stop(it.stateName.uppercase(), Expression.color(lerp(LAND, partyColor, 0.25f + 0.55f * it.representationScore).toArgb()))
        }
        val fill = if (stops.isEmpty()) Expression.color(LAND.toArgb())
        else Expression.match(upcase(get("state")), Expression.color(LAND.toArgb()), *stops.toTypedArray())
        s.getLayerAs<FillLayer>(STATE_FILL)?.setProperties(fillColor(fill))
    }

    // Selection: outline the chosen state, reveal its districts, highlight the district.
    LaunchedEffect(style, selectedState, selectedDistrict, stateGeoDataList, partyColor) {
        val s = style ?: return@LaunchedEffect
        val st = selectedState?.uppercase()
        val inState: Expression = if (st == null) literal(false) else Expression.eq(upcase(get("state")), literal(st))
        val isDistrict: Expression = if (st == null || selectedDistrict == null) literal(false)
        else Expression.all(inState, Expression.eq(upcase(get("district")), literal(selectedDistrict.uppercase())))

        s.getLayerAs<LineLayer>(STATE_SELECTED_LINE)?.setFilter(inState)
        s.getLayerAs<SymbolLayer>(STATE_LABEL)?.setFilter(if (st == null) literal(true) else Expression.not(inState))
        s.getLayerAs<FillLayer>(STATE_FILL)?.setProperties(
            fillOpacity(if (st == null) literal(1f) else Expression.switchCase(inState, literal(1f), literal(0.55f)))
        )
        s.getLayerAs<FillLayer>(DISTRICT_FILL)?.setFilter(inState)
        s.getLayerAs<LineLayer>(DISTRICT_LINE)?.setFilter(inState)
        s.getLayerAs<SymbolLayer>(DISTRICT_LABEL)?.setFilter(inState)
        s.getLayerAs<LineLayer>(DISTRICT_SELECTED_LINE)?.setFilter(isDistrict)

        val districts = stateGeoDataList.firstOrNull { it.stateName.equals(selectedState, ignoreCase = true) }?.districts.orEmpty()
        val maxCount = districts.maxOfOrNull { it.leaderCount }?.coerceAtLeast(1) ?: 1
        val stops = districts.map {
            Expression.stop(it.districtName.uppercase(), Expression.color(lerp(LAND, partyColor, 0.3f + 0.5f * it.leaderCount / maxCount).toArgb()))
        }
        val base = Expression.color(LAND.toArgb())
        val districtFill = if (stops.isEmpty()) base else Expression.match(upcase(get("district")), base, *stops.toTypedArray())
        s.getLayerAs<FillLayer>(DISTRICT_FILL)?.setProperties(fillColor(districtFill))
    }

    LaunchedEffect(style, pin) {
        val s = style ?: return@LaunchedEffect
        val lat = pin?.latitude
        val lng = pin?.longitude
        val source = s.getSourceAs<GeoJsonSource>(PIN_SRC) ?: return@LaunchedEffect
        if (lat != null && lng != null) source.setGeoJson(Point.fromLngLat(lng, lat))
        else source.setGeoJson(FeatureCollection.fromJson(EMPTY_FC))
    }

    // Fly to the real polygon bounds. The old code aimed at a hard-coded state centre
    // even for districts, so picking Pune zoomed to level 9 somewhere near Jalna.
    LaunchedEffect(map, selectedState, selectedDistrict) {
        val m = map ?: return@LaunchedEffect
        val bounds = when {
            selectedState == null -> INDIA_BOUNDS
            else -> selectedDistrict?.let { resolver.boundsOf(selectedState, it) } ?: resolver.boundsOf(selectedState)
        } ?: return@LaunchedEffect
        val pad = (24 * density).toInt()
        m.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, pad, (96 * density).toInt(), pad, latestPadding() + pad),
            700
        )
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

private fun addBoundaryLayers(s: Style) {
    val navy = CivicColors.Navy.toArgb()
    val white = Color.White.toArgb()
    s.addLayer(FillLayer(STATE_FILL, STATES_SRC).withProperties(fillColor(LAND.toArgb())))
    s.addLayer(LineLayer(STATE_LINE, STATES_SRC).withProperties(lineColor(Color(0xFFB9C3CC).toArgb()), lineWidth(0.8f)))
    s.addLayer(
        FillLayer(DISTRICT_FILL, DISTRICTS_SRC).withFilter(literal(false)).withProperties(fillColor(LAND.toArgb()))
    )
    s.addLayer(
        LineLayer(DISTRICT_LINE, DISTRICTS_SRC).withFilter(literal(false))
            .withProperties(lineColor(Color(0xFFC9D1D8).toArgb()), lineWidth(0.6f))
    )
    s.addLayer(
        LineLayer(STATE_SELECTED_LINE, STATES_SRC).withFilter(literal(false))
            .withProperties(lineColor(navy), lineWidth(2f))
    )
    s.addLayer(
        LineLayer(DISTRICT_SELECTED_LINE, DISTRICTS_SRC).withFilter(literal(false))
            .withProperties(lineColor(CivicColors.Saffron.toArgb()), lineWidth(2.5f))
    )
    s.addLayer(
        SymbolLayer(STATE_LABEL, STATES_SRC).withProperties(
            textField(get("state")), textFont(LABEL_FONT), textSize(11f), textMaxWidth(6f),
            textColor(CivicColors.Navy.copy(alpha = 0.75f).toArgb()), textHaloColor(white), textHaloWidth(1.4f)
        )
    )
    s.addLayer(
        SymbolLayer(DISTRICT_LABEL, DISTRICTS_SRC).withFilter(literal(false)).withProperties(
            textField(get("district")), textFont(LABEL_FONT), textSize(10f), textMaxWidth(6f),
            textColor(CivicColors.TextSecondary.toArgb()), textHaloColor(white), textHaloWidth(1.2f)
        )
    )
    s.addLayer(
        CircleLayer(PIN_LAYER, PIN_SRC).withProperties(
            circleRadius(7f), circleColor(CivicColors.Saffron.toArgb()),
            circleStrokeWidth(3f), circleStrokeColor(white)
        )
    )
}

// ── Overlays ─────────────────────────────────────────────────────────────────

@Composable
private fun GeoBreadcrumb(
    selectedState: String?,
    selectedDistrict: String?,
    onIndia: () -> Unit,
    onState: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = CivicColors.Surface,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, CivicColors.Outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Crumb("India", active = selectedState == null, onClick = onIndia)
            if (selectedState != null) {
                CrumbSeparator()
                Crumb(selectedState, active = selectedDistrict == null, onClick = onState)
            }
            if (selectedDistrict != null) {
                CrumbSeparator()
                Crumb(selectedDistrict, active = true, onClick = {})
            }
        }
    }
}

@Composable
private fun Crumb(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) CivicColors.Navy else CivicColors.TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = 140.dp)
            .clip(CircleShape)
            .clickable(enabled = !active, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun CrumbSeparator() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = CivicColors.TextMuted,
        modifier = Modifier.size(16.dp)
    )
}

@Composable
private fun PresenceLegend(partyColor: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = CivicColors.Surface.copy(alpha = 0.92f)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Fewer posts", style = MaterialTheme.typography.labelSmall, color = CivicColors.TextSecondary)
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(lerp(LAND, partyColor, 0.25f + 0.55f * it), RoundedCornerShape(3.dp))
                )
            }
            Text("More", style = MaterialTheme.typography.labelSmall, color = CivicColors.TextSecondary)
        }
    }
}

// ── Info panel ───────────────────────────────────────────────────────────────

@Composable
private fun MapPanel(
    height: Dp,
    party: PartyMetadata,
    stateGeoDataList: List<StateGeoData>,
    allNodes: List<PartyTreeNode>,
    selectedState: String?,
    selectedDistrict: String?,
    onDrag: (Float) -> Unit,
    onDragStopped: (Float) -> Unit,
    onHandleTap: () -> Unit,
    onStateSelected: (String?) -> Unit,
    onDistrictSelected: (String?) -> Unit,
    onPositionClicked: (PartyTreeNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val stateData = stateGeoDataList.firstOrNull { it.stateName.equals(selectedState, ignoreCase = true) }
    val positions = remember(allNodes, selectedState, selectedDistrict) {
        allNodes.filter { n ->
            when {
                selectedState == null -> n.level.tier <= 8
                !n.state.equals(selectedState, ignoreCase = true) -> false
                selectedDistrict == null -> true
                else -> n.district.equals(selectedDistrict, ignoreCase = true)
            }
        }.sortedWith(compareBy({ it.level.tier }, { it.isNotFetched }))
    }

    val title = selectedDistrict ?: selectedState ?: "All India"
    val subtitle = when {
        allNodes.isEmpty() -> "No structure data for ${party.shortName} yet"
        selectedState == null -> "${party.shortName} organised in ${stateGeoDataList.size} states · ${allNodes.size} posts"
        else -> "${positions.size} ${party.shortName} posts · ${positions.count { !it.isNotFetched }} with a named holder"
    }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = CivicColors.Surface,
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = rememberDraggableState(onDrag),
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> onDragStopped(velocity) }
                    )
                    .clickable(onClick = onHandleTap)
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp)
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .background(CivicColors.Outline, CircleShape)
                )
                Spacer(Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, color = CivicColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider(color = CivicColors.Outline)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp)
            ) {
                if (allNodes.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.Map,
                            title = "Not mapped yet",
                            message = "${party.name}'s organisation hasn't been ingested, so there is nothing to place on the map."
                        )
                    }
                    return@LazyColumn
                }

                if (selectedState == null) {
                    item { PanelSectionLabel("State units") }
                    if (stateGeoDataList.isEmpty()) {
                        item { PanelNote("No state units recorded yet.") }
                    }
                    items(stateGeoDataList, key = { "state-${it.stateName}" }) { st ->
                        StateRow(state = st, partyColor = party.color, onClick = { onStateSelected(st.stateName) })
                    }
                    item { PanelSectionLabel("National leadership") }
                } else {
                    if (stateData != null && stateData.districts.isNotEmpty()) {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(stateData.districts, key = { it.districtName }) { d ->
                                    val selected = d.districtName.equals(selectedDistrict, ignoreCase = true)
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onDistrictSelected(if (selected) null else d.districtName) },
                                        label = { Text("${d.districtName} · ${d.leaderCount}") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = CivicColors.Surface,
                                            selectedContainerColor = CivicColors.Navy,
                                            selectedLabelColor = Color.White
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = selected,
                                            borderColor = CivicColors.Outline,
                                            selectedBorderColor = CivicColors.Navy
                                        )
                                    )
                                }
                            }
                        }
                    }
                    item { PanelSectionLabel(if (selectedDistrict != null) "Posts in $selectedDistrict" else "Posts in $selectedState") }
                }

                if (positions.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.PersonSearch,
                            title = "No posts recorded here",
                            message = "${party.shortName} has no positions on record for ${selectedDistrict ?: selectedState ?: "this area"} yet."
                        )
                    }
                } else {
                    items(positions, key = { it.id }) { node ->
                        PositionRow(node = node, onClick = { onPositionClicked(node) }, showJurisdiction = selectedState == null)
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = CivicColors.TextMuted,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun PanelNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = CivicColors.TextSecondary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun StateRow(state: StateGeoData, partyColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(lerp(LAND, partyColor, 0.25f + 0.55f * state.representationScore), RoundedCornerShape(10.dp))
        )
        Column(Modifier.weight(1f)) {
            Text(state.stateName, style = MaterialTheme.typography.titleSmall, color = CivicColors.Navy)
            Text(
                text = "${state.leaderCount} posts · led by ${state.topLeaderName.takeUnless { it.contains("Not yet", true) } ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = CivicColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CivicColors.TextMuted)
    }
}
