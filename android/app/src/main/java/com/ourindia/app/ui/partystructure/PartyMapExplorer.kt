package com.ourindia.app.ui.partystructure

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ourindia.app.data.geo.ResolvedLocation
import com.ourindia.app.ui.theme.CivicColors
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * Political Map Explorer — Sub-Module 2
 *
 * Renders a real MapLibre interactive India map as the primary exploration surface.
 * Geographic data drives political data selection.
 * Map → political context → politician profile → Find in Hierarchy flow is fully wired.
 *
 * Map layout:
 * - Top 55%: MapLibre interactive India map
 * - Bottom 45%: Political info for selected area (leaders, breadcrumbs)
 */
@Composable
fun PartyMapExplorer(
    partyMetadata: PartyMetadata,
    stateGeoDataList: List<StateGeoData>,
    selectedState: String?,
    selectedDistrict: String?,
    resolvedLocation: ResolvedLocation? = null,
    onStateSelected: (String?) -> Unit,
    onDistrictSelected: (String?) -> Unit,
    allNodes: List<PartyTreeNode>,
    onPoliticianClicked: (PartyTreeNode) -> Unit,
    onLocateInHierarchy: (String) -> Unit,
    onMapLongPressed: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val activeStateData = stateGeoDataList.firstOrNull {
        it.stateName.equals(selectedState, ignoreCase = true)
    }

    // Filter politicians based on map geographic selection
    val filteredLeaders = remember(selectedState, selectedDistrict, allNodes) {
        allNodes.filter { node ->
            if (selectedState == null) {
                // When nothing selected, show national-level leaders only
                node.level.tier <= 8
            } else {
                val matchesState = node.state.equals(selectedState, ignoreCase = true)
                if (selectedDistrict != null) {
                    matchesState && node.district.equals(selectedDistrict, ignoreCase = true)
                } else {
                    matchesState
                }
            }
        }.sortedBy { it.level.tier }
    }

    // Map camera target — center on resolved location or selected state, fallback to India center
    val mapCameraTarget = remember(resolvedLocation, selectedState) {
        when {
            resolvedLocation?.state != null -> getStateCenterLatLng(resolvedLocation.state)
            selectedState != null -> getStateCenterLatLng(selectedState)
            else -> LatLng(20.5937, 78.9629) // India center
        }
    }

    val mapZoom = remember(resolvedLocation, selectedState, selectedDistrict) {
        when {
            selectedDistrict != null -> 9.0
            selectedState != null || resolvedLocation?.state != null -> 6.5
            else -> 4.5
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Geographic Breadcrumb Navigation Bar ─────────────────────────
        GeoBreadcrumbBar(
            selectedState = selectedState,
            selectedDistrict = selectedDistrict,
            resolvedLocation = resolvedLocation,
            partyColor = partyMetadata.color,
            onClearState = { onStateSelected(null); onDistrictSelected(null) },
            onClearDistrict = { onDistrictSelected(null) }
        )

        // ── Real MapLibre Map (top 50% of screen) ────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
        ) {
            MapLibreMapView(
                cameraTarget = mapCameraTarget,
                zoom = mapZoom,
                partyColor = partyMetadata.color,
                stateGeoDataList = stateGeoDataList,
                selectedState = selectedState,
                resolvedLocation = resolvedLocation,
                onStateSelected = onStateSelected,
                onMapLongPressed = onMapLongPressed
            )

            // Map overlay: Party context badge
            Surface(
                color = CivicColors.Navy.copy(alpha = 0.88f),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = partyMetadata.symbolEmoji, fontSize = 14.sp)
                    Text(
                        text = "${partyMetadata.shortName} Political Geography",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // State selector chips overlaid on map (quick-select popular states)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(8.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(stateGeoDataList.take(12)) { stateData ->
                        val isSelected = stateData.stateName.equals(selectedState, ignoreCase = true)
                        Surface(
                            onClick = {
                                if (isSelected) onStateSelected(null) else onStateSelected(stateData.stateName)
                            },
                            color = if (isSelected) partyMetadata.color else Color.White.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = stateData.stateCode,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else CivicColors.Navy,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── Political Info Panel for selected area (bottom 50%) ──────────
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .background(CivicColors.Background),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Context header
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, CivicColors.Navy, RoundedCornerShape(10.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    selectedDistrict != null -> "📍 $selectedDistrict, $selectedState"
                                    selectedState != null -> "📍 $selectedState"
                                    resolvedLocation?.state != null -> "📍 ${resolvedLocation.state} (Your Location)"
                                    else -> "🇮🇳 National Level — ${partyMetadata.shortName}"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = CivicColors.Navy
                            )
                            Text(
                                text = "${filteredLeaders.size} organizational positions • ${
                                    filteredLeaders.count { !it.isNotFetched }
                                } verified",
                                fontSize = 11.sp,
                                color = CivicColors.TextSecondary
                            )
                        }

                        if (activeStateData != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${activeStateData.leaderCount}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = partyMetadata.color
                                )
                                Text(
                                    text = "Positions",
                                    fontSize = 10.sp,
                                    color = CivicColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // District drill-down chips (shown when state selected)
            if (selectedState != null && activeStateData != null && activeStateData.districts.isNotEmpty()) {
                item {
                    Text(
                        text = "Drill down to district:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CivicColors.Navy,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(activeStateData.districts) { district ->
                            val isSelected = district.districtName.equals(selectedDistrict, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) onDistrictSelected(null)
                                    else onDistrictSelected(district.districtName)
                                },
                                label = {
                                    Text(
                                        district.districtName,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = partyMetadata.color,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // Leader cards for the selected area
            if (filteredLeaders.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔍", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No positions recorded for this area yet",
                                fontSize = 13.sp,
                                color = CivicColors.TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredLeaders, key = { it.id }) { leader ->
                    MapLeaderCard(
                        node = leader,
                        partyColor = partyMetadata.color,
                        onCardClick = { onPoliticianClicked(leader) },
                        onLocateInHierarchy = { onLocateInHierarchy(leader.id) }
                    )
                }
            }
        }
    }
}

// ── MapLibre AndroidView wrapper ──────────────────────────────────────────────

@Composable
private fun MapLibreMapView(
    cameraTarget: LatLng,
    zoom: Double,
    partyColor: Color,
    stateGeoDataList: List<StateGeoData>,
    selectedState: String?,
    resolvedLocation: ResolvedLocation?,
    onStateSelected: (String?) -> Unit,
    onMapLongPressed: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // MapLibre v11 style URL — OpenFreeMap liberty (free, no API key, attribution required)
    val styleUrl = "https://tiles.openfreemap.org/styles/liberty"

    // We hold the MapView reference in a remembered ref so we can call lifecycle methods
    var mapViewRef: MapView? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose {
            mapViewRef?.onStop()
            mapViewRef?.onDestroy()
            mapViewRef = null
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapLibre.getInstance(ctx)
            MapView(ctx).also { mv ->
                mapViewRef = mv
                mv.onCreate(null)
                mv.getMapAsync { map ->
                    map.setStyle(styleUrl) {
                        // Position camera over India or specified target
                        val position = CameraPosition.Builder()
                            .target(cameraTarget)
                            .zoom(zoom)
                            .build()
                        map.cameraPosition = position

                        // Add a location dot if we have a resolved GPS state
                        if (resolvedLocation?.state != null) {
                            val loc = getStateCenterLatLng(resolvedLocation.state)
                            safeAddLocationMarker(map, loc)
                        }
                    }

                    map.addOnMapLongClickListener { point ->
                        safeAddLocationMarker(map, point) // Instant visual feedback
                        onMapLongPressed(point.latitude, point.longitude)
                        true
                    }
                }
                mv.onStart()
                mv.onResume()
            }
        },
        update = { mv ->
            mv.getMapAsync { map ->
                val position = CameraPosition.Builder()
                    .target(cameraTarget)
                    .zoom(zoom)
                    .build()
                map.animateCamera(
                    org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(position),
                    800
                )
            }
        }
    )
}

private fun safeAddLocationMarker(map: MapLibreMap, latLng: LatLng) {
    try {
        val style = map.style ?: return
        // Remove existing marker if present (avoid duplicate sources)
        style.removeLayer("location-layer")
        style.removeSource("location-source")

        val geoJsonSource = org.maplibre.android.style.sources.GeoJsonSource(
            "location-source",
            "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[${latLng.longitude},${latLng.latitude}]}}"
        )
        style.addSource(geoJsonSource)
        style.addLayer(
            org.maplibre.android.style.layers.CircleLayer("location-layer", "location-source").apply {
                setProperties(
                    org.maplibre.android.style.layers.PropertyFactory.circleRadius(9f),
                    org.maplibre.android.style.layers.PropertyFactory.circleColor("#E85D04"),
                    org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth(2.5f),
                    org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor("#FFFFFF"),
                    org.maplibre.android.style.layers.PropertyFactory.circleOpacity(0.92f)
                )
            }
        )
    } catch (e: Exception) {
        // Style not yet ready or layer already added — ignore safely
    }
}

// ── Geographic center lookup for states ──────────────────────────────────────

private fun getStateCenterLatLng(state: String): LatLng = when (state.lowercase().trim()) {
    "jammu & kashmir", "j&k" -> LatLng(34.0, 76.5)
    "ladakh" -> LatLng(34.2, 77.6)
    "himachal pradesh" -> LatLng(31.9, 77.1)
    "punjab" -> LatLng(31.1, 75.3)
    "chandigarh" -> LatLng(30.7, 76.8)
    "haryana" -> LatLng(29.0, 76.1)
    "delhi" -> LatLng(28.65, 77.2)
    "uttarakhand" -> LatLng(30.0, 79.0)
    "uttar pradesh" -> LatLng(26.8, 80.9)
    "rajasthan" -> LatLng(27.0, 74.2)
    "madhya pradesh" -> LatLng(23.5, 77.8)
    "gujarat" -> LatLng(22.2, 71.5)
    "maharashtra" -> LatLng(19.7, 75.7)
    "chhattisgarh" -> LatLng(21.3, 81.9)
    "jharkhand" -> LatLng(23.6, 85.3)
    "odisha" -> LatLng(20.9, 85.1)
    "west bengal" -> LatLng(23.5, 87.9)
    "bihar" -> LatLng(25.7, 85.4)
    "sikkim" -> LatLng(27.5, 88.5)
    "assam" -> LatLng(26.2, 92.9)
    "arunachal pradesh" -> LatLng(28.2, 94.7)
    "nagaland" -> LatLng(26.2, 94.5)
    "manipur" -> LatLng(24.7, 93.9)
    "mizoram" -> LatLng(23.2, 92.8)
    "tripura" -> LatLng(23.9, 91.7)
    "meghalaya" -> LatLng(25.5, 91.4)
    "andhra pradesh" -> LatLng(15.9, 80.0)
    "telangana" -> LatLng(17.4, 79.1)
    "karnataka" -> LatLng(15.3, 75.7)
    "kerala" -> LatLng(10.8, 76.3)
    "tamil nadu" -> LatLng(11.1, 78.7)
    "puducherry" -> LatLng(11.9, 79.8)
    "goa" -> LatLng(15.3, 74.0)
    "lakshadweep" -> LatLng(10.6, 72.6)
    "andaman & nicobar islands" -> LatLng(11.7, 92.7)
    else -> LatLng(20.5937, 78.9629) // Default: India center
}

// ── UI Components ─────────────────────────────────────────────────────────────

@Composable
private fun GeoBreadcrumbBar(
    selectedState: String?,
    selectedDistrict: String?,
    resolvedLocation: ResolvedLocation?,
    partyColor: Color,
    onClearState: () -> Unit,
    onClearDistrict: () -> Unit
) {
    Surface(
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = CivicColors.Navy.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = null,
                tint = CivicColors.Navy,
                modifier = Modifier.size(16.dp)
            )

            // India (always)
            Text(
                text = "India",
                fontSize = 12.sp,
                fontWeight = if (selectedState == null) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedState == null) CivicColors.Navy else CivicColors.TextSecondary,
                modifier = Modifier.clickable { onClearState() }
            )

            if (selectedState != null) {
                Text("›", color = CivicColors.TextSecondary, fontSize = 12.sp)
                Surface(
                    color = partyColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selectedState,
                            fontSize = 12.sp,
                            fontWeight = if (selectedDistrict == null) FontWeight.Bold else FontWeight.Normal,
                            color = CivicColors.Navy
                        )
                        if (selectedDistrict == null) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear state",
                                tint = CivicColors.Navy.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { onClearState() }
                            )
                        }
                    }
                }
            }

            if (selectedDistrict != null) {
                Text("›", color = CivicColors.TextSecondary, fontSize = 12.sp)
                Surface(
                    color = partyColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selectedDistrict,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CivicColors.Navy
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear district",
                            tint = CivicColors.Navy.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(12.dp)
                                .clickable { onClearDistrict() }
                        )
                    }
                }
            }

            // Show resolved location indicator
            if (resolvedLocation?.state != null && selectedState == null) {
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = CivicColors.Saffron.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "📍 ${resolvedLocation.state}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CivicColors.Saffron,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MapLeaderCard(
    node: PartyTreeNode,
    partyColor: Color,
    onCardClick: () -> Unit,
    onLocateInHierarchy: () -> Unit
) {
    val isNotFetched = node.isNotFetched

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isNotFetched) 1.dp else 1.5.dp,
                color = if (isNotFetched) CivicColors.Navy.copy(alpha = 0.2f) else CivicColors.Navy,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { if (!isNotFetched) onCardClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNotFetched) Color(0xFFF7F7F7) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isNotFetched) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isNotFetched) CivicColors.Navy.copy(alpha = 0.07f)
                        else node.level.badgeColor.copy(alpha = 0.15f),
                        CircleShape
                    )
                    .border(
                        1.dp,
                        if (isNotFetched) CivicColors.Navy.copy(alpha = 0.15f) else node.level.badgeColor,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = if (isNotFetched) "❓" else node.photoEmoji, fontSize = 22.sp)
            }

            // Info column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = if (isNotFetched) CivicColors.Navy.copy(alpha = 0.3f) else node.level.badgeColor,
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = node.level.groupLabel,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = node.holderName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isNotFetched) CivicColors.TextSecondary else CivicColors.Navy,
                    fontStyle = if (isNotFetched) androidx.compose.ui.text.font.FontStyle.Italic
                                else androidx.compose.ui.text.font.FontStyle.Normal,
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

            // Find in hierarchy button
            if (!isNotFetched) {
                IconButton(
                    onClick = onLocateInHierarchy,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = "Find in Hierarchy",
                        tint = CivicColors.Saffron,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Surface(
                    color = CivicColors.Saffron.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Pending",
                        fontSize = 10.sp,
                        color = CivicColors.Saffron,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
