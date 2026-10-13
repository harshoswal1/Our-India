@file:OptIn(ExperimentalMaterial3Api::class)

package com.ourindia.app.ui.partystructure

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ourindia.app.data.geo.LocalGeographicResolver
import com.ourindia.app.data.geo.ResolvedLocation
import com.ourindia.app.ui.theme.CivicColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PartyStructureScreen(
    viewModel: PartyStructureViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Hoisted out of the screen switch: it caches parsed boundary files, and the old
    // per-entry instance re-parsed them every time the workspace was re-entered.
    val resolver = remember { LocalGeographicResolver(context.applicationContext) }

    BackHandler(enabled = uiState.currentScreen != FeatureScreen.PARTY_SELECTOR) {
        when (uiState.currentScreen) {
            FeatureScreen.POLITICIAN_PROFILE -> viewModel.navigateBackFromProfile()
            FeatureScreen.WORKSPACE -> when {
                uiState.selectedNodeDetail != null -> viewModel.dismissNodeDetail()
                uiState.searchQuery.isNotEmpty() -> viewModel.onSearchQueryChanged("")
                uiState.activeSubModule == WorkspaceSubModule.MAP && uiState.selectedDistrict != null -> viewModel.onMapDistrictSelected(null)
                uiState.activeSubModule == WorkspaceSubModule.MAP && uiState.selectedState != null -> viewModel.onMapStateSelected(null)
                uiState.activeSubModule != WorkspaceSubModule.HIERARCHY -> viewModel.switchSubModule(WorkspaceSubModule.HIERARCHY)
                else -> viewModel.navigateToPartySelector()
            }
            FeatureScreen.PARTY_SELECTOR -> Unit
        }
    }

    AnimatedContent(
        targetState = uiState.currentScreen,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
        label = "FeatureScreenTransition",
        modifier = modifier
    ) { screen ->
        when (screen) {
            FeatureScreen.PARTY_SELECTOR -> PartySelectorScreen(
                partiesWithData = uiState.partiesWithData,
                onSelectParty = viewModel::onSelectPartyFromGrid
            )

            FeatureScreen.WORKSPACE -> WorkspaceScreen(
                state = uiState,
                party = PartyCatalog.getParty(uiState.selectedParty),
                viewModel = viewModel,
                resolver = resolver
            )

            FeatureScreen.POLITICIAN_PROFILE -> {
                // Held for the exit fade; the live state clears the profile on back.
                val profile = remember { uiState.selectedProfile }
                if (profile != null) {
                    PoliticianProfileScreen(
                        profile = profile,
                        onBack = viewModel::navigateBackFromProfile,
                        onLocateInHierarchy = viewModel::locateInHierarchy
                    )
                }
            }
        }
    }
}

@Composable
private fun PartySelectorScreen(
    partiesWithData: Set<String>,
    onSelectParty: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TricolorRule(Modifier.width(18.dp))
                        Text("Our India", style = MaterialTheme.typography.titleLarge, color = CivicColors.Navy)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CivicColors.Background)
            )
        },
        containerColor = CivicColors.Background
    ) { padding ->
        PartySelectionGrid(
            parties = PartyCatalog.parties,
            partiesWithData = partiesWithData,
            onSelectParty = onSelectParty,
            contentPadding = padding
        )
    }
}

@Composable
private fun WorkspaceScreen(
    state: PartyStructureUiState,
    party: PartyMetadata,
    viewModel: PartyStructureViewModel,
    resolver: LocalGeographicResolver
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    fun say(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    LaunchedEffect(state.userMessage) {
        val message = state.userMessage ?: return@LaunchedEffect
        // Shown on the screen scope: clearing the message re-keys this effect, which
        // would cancel a snackbar shown from inside it the moment it appeared.
        say(message)
        viewModel.userMessageShown()
    }

    val locateMe = rememberLocateMe(
        resolver = resolver,
        onStart = { viewModel.setLocationLoading(true) },
        onResolved = viewModel::onLocationResolved,
        onFailed = { message ->
            viewModel.setLocationLoading(false)
            say(message)
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = viewModel::navigateToPartySelector) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "All parties", tint = CivicColors.Navy)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PartySymbol(party = party, size = 36.dp)
                        Column {
                            Text(party.shortName, style = MaterialTheme.typography.titleMedium, color = CivicColors.Navy, maxLines = 1)
                            Text(
                                party.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = CivicColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    BusyIconButton(busy = state.isLocationLoading, onClick = locateMe) {
                        Icon(Icons.Outlined.MyLocation, contentDescription = "Use my location", tint = CivicColors.Navy)
                    }
                    BusyIconButton(busy = state.isSyncing, onClick = viewModel::refresh) {
                        Icon(Icons.Outlined.Sync, contentDescription = "Sync latest data", tint = CivicColors.Navy)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CivicColors.Background)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = CivicColors.Surface, tonalElevation = 0.dp) {
                WorkspaceSubModule.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == state.activeSubModule,
                        onClick = { viewModel.switchSubModule(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CivicColors.Navy,
                            selectedTextColor = CivicColors.Navy,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = CivicColors.TextSecondary,
                            unselectedTextColor = CivicColors.TextSecondary
                        )
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = CivicColors.Background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            state.resolvedLocation?.state?.let { here ->
                NearYouBar(
                    label = listOfNotNull(state.resolvedLocation.district, here).joinToString(", "),
                    onClear = { viewModel.onLocationResolved(null) }
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = CivicColors.Saffron, modifier = Modifier.align(Alignment.Center))
                } else when (state.activeSubModule) {
                    WorkspaceSubModule.HIERARCHY -> HierarchyTab(
                        state = state,
                        party = party,
                        viewModel = viewModel
                    )

                    WorkspaceSubModule.MAP -> PartyMapExplorer(
                        partyMetadata = party,
                        resolver = resolver,
                        stateGeoDataList = state.stateGeoDataList,
                        allNodes = state.allNodes,
                        selectedState = state.selectedState,
                        selectedDistrict = state.selectedDistrict,
                        resolvedLocation = state.resolvedLocation,
                        onStateSelected = viewModel::onMapStateSelected,
                        onDistrictSelected = viewModel::onMapDistrictSelected,
                        onPositionClicked = viewModel::onNodeClicked,
                        onMapLongPressed = { lat, lng ->
                            scope.launch {
                                val loc = resolver.resolveLocation(lat, lng)
                                if (loc != null) viewModel.onLocationResolved(loc)
                                else say("That point is outside India's mapped boundaries")
                            }
                        }
                    )

                    WorkspaceSubModule.ANALYTICS -> PartyAnalyticsDashboard(
                        partyMetadata = party,
                        allNodes = state.allNodes,
                        stateGeoDataList = state.stateGeoDataList,
                        selectedState = state.selectedState,
                        onClearLocationFilter = {
                            if (state.resolvedLocation != null) viewModel.onLocationResolved(null)
                            else viewModel.onMapStateSelected(null)
                        }
                    )
                }
            }
        }
    }

    state.selectedNodeDetail?.let { node ->
        PositionDetailSheet(
            node = node,
            onDismiss = viewModel::dismissNodeDetail,
            onViewProfile = viewModel::navigateToProfile,
            onLocateInHierarchy = viewModel::locateInHierarchy
        )
    }
}

@Composable
private fun HierarchyTab(
    state: PartyStructureUiState,
    party: PartyMetadata,
    viewModel: PartyStructureViewModel
) {
    Box(Modifier.fillMaxSize()) {
        if (state.chart.units.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.AccountTree,
                title = "Structure not mapped yet",
                message = "${party.name}'s organisation hasn't been ingested. Sync to check for new data, or explore another party.",
                modifier = Modifier.align(Alignment.Center),
                action = {
                    OutlinedButton(onClick = viewModel::navigateToPartySelector, border = BorderStroke(1.dp, CivicColors.Outline)) {
                        Text("Choose another party", color = CivicColors.Navy)
                    }
                }
            )
        } else {
            PartyHierarchyCanvas(
                chart = state.chart,
                partyColor = party.color,
                focusedNodeId = state.focusedNodeId,
                focusRequest = state.focusRequest,
                onNodeClicked = viewModel::onNodeClicked,
                onToggleUnit = viewModel::toggleUnit,
                onSetAllExpanded = viewModel::setAllExpanded,
                contentTopInset = 80.dp
            )
            HierarchySearchField(
                query = state.searchQuery,
                onQueryChange = viewModel::onSearchQueryChanged,
                results = state.searchResults,
                onResultChosen = viewModel::onSearchResultChosen,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun NearYouBar(label: String, onClear: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.NearMe, contentDescription = null, tint = CivicColors.SaffronDeep, modifier = Modifier.size(16.dp))
            Text(
                text = "Near you · $label",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Clear location filter", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun BusyIconButton(busy: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    IconButton(onClick = onClick, enabled = !busy) {
        if (busy) CircularProgressIndicator(strokeWidth = 2.dp, color = CivicColors.Saffron, modifier = Modifier.size(20.dp))
        else icon()
    }
}

// ── Location ─────────────────────────────────────────────────────────────────

/** Permission → services check → one fix → resolve. Returns the click handler. */
@Composable
private fun rememberLocateMe(
    resolver: LocalGeographicResolver,
    onStart: () -> Unit,
    onResolved: (ResolvedLocation) -> Unit,
    onFailed: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showServicesDialog by remember { mutableStateOf(false) }

    val start = {
        if (!locationServicesOn(context)) {
            showServicesDialog = true
        } else {
            onStart()
            requestFix(context, scope, resolver, onResolved, onFailed)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) start() else showPermissionDialog = true
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Location permission needed") },
            text = { Text("Allow location access to see the party posts that cover where you are.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                    }
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { showPermissionDialog = false }) { Text("Not now") } }
        )
    }
    if (showServicesDialog) {
        AlertDialog(
            onDismissRequest = { showServicesDialog = false },
            title = { Text("Location is off") },
            text = { Text("Turn on location services to find the party structure around you.") },
            confirmButton = {
                TextButton(onClick = {
                    showServicesDialog = false
                    runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                }) { Text("Turn on") }
            },
            dismissButton = { TextButton(onClick = { showServicesDialog = false }) { Text("Not now") } }
        )
    }

    return {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) start()
        else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}

private fun locationServicesOn(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

@SuppressLint("MissingPermission")
private fun requestFix(
    context: Context,
    scope: CoroutineScope,
    resolver: LocalGeographicResolver,
    onResolved: (ResolvedLocation) -> Unit,
    onFailed: (String) -> Unit
) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    fun deliver(location: Location) {
        scope.launch {
            val resolved = resolver.resolveLocation(location.latitude, location.longitude)
            if (resolved != null) onResolved(resolved) else onFailed("Your location is outside India's mapped boundaries")
        }
    }

    try {
        val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        // A cached fix is fine if recent; a stale one could be another city.
        if (last != null && System.currentTimeMillis() - last.time < 10 * 60_000) {
            deliver(last)
            return
        }

        val provider = if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        var done = false
        // All four callbacks are overridden on purpose: they are only default methods from
        // API 30, and on 26–29 the platform calls them and would hit an AbstractMethodError.
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (done) return
                done = true
                deliver(location)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        @Suppress("DEPRECATION")
        lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        // Without a timeout the spinner ran forever indoors where GPS never fixes.
        scope.launch {
            delay(15_000)
            if (done) return@launch
            done = true
            lm.removeUpdates(listener)
            if (last != null) deliver(last) else onFailed("Couldn't get a location fix — try again with Wi-Fi on")
        }
    } catch (e: SecurityException) {
        onFailed("Location permission was revoked")
    } catch (e: Exception) {
        onFailed("Location is unavailable right now")
    }
}
