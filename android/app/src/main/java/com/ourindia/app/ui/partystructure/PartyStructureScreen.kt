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
import com.ourindia.app.ui.common.rememberLocateMe

/**
 * Leaders tab. [openParty]/[openNodeId] deep-link from Home ("your area's leaders").
 * [onImmersiveChange] tells the app shell to hide its bottom bar while a party workspace
 * or profile is open — the workspace has its own tab bar and two stacked bars read as noise.
 */
@Composable
fun PartyStructureScreen(
    resolver: LocalGeographicResolver,
    modifier: Modifier = Modifier,
    openParty: String? = null,
    openNodeId: String? = null,
    onImmersiveChange: (Boolean) -> Unit = {},
    viewModel: PartyStructureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(openParty, openNodeId) {
        if (openParty != null) viewModel.openParty(openParty, openNodeId)
    }
    LaunchedEffect(uiState.currentScreen) {
        onImmersiveChange(uiState.currentScreen != FeatureScreen.PARTY_SELECTOR)
    }

    BackHandler(enabled = uiState.currentScreen != FeatureScreen.PARTY_SELECTOR) {
        when (uiState.currentScreen) {
            FeatureScreen.POLITICIAN_PROFILE -> viewModel.navigateBackFromProfile()
            FeatureScreen.WORKSPACE -> when {
                uiState.selectedNodeDetail != null -> viewModel.dismissNodeDetail()
                uiState.searchQuery.isNotEmpty() -> viewModel.onSearchQueryChanged("")
                uiState.activeSubModule == WorkspaceSubModule.MAP && uiState.selectedDistrict != null -> viewModel.onMapDistrictSelected(null)
                uiState.activeSubModule == WorkspaceSubModule.MAP && uiState.selectedState != null -> viewModel.onMapStateSelected(null)
                uiState.activeSubModule != WorkspaceSubModule.HIERARCHY -> viewModel.switchSubModule(WorkspaceSubModule.HIERARCHY)
                // In the orbit, back steps out one ring before it leaves the party.
                else -> if (!viewModel.orbitUp()) viewModel.navigateToPartySelector()
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
                title = { Text("Leaders", style = MaterialTheme.typography.headlineSmall, color = CivicColors.Navy) },
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
            val orbitUnit = state.orbitUnitId
            if (state.structureView == StructureView.ORBIT && orbitUnit != null) {
                PartyOrbitView(
                    tree = state.orgTree,
                    unitId = orbitUnit,
                    partyColor = party.color,
                    focusedNodeId = state.focusedNodeId,
                    onFocusUnit = viewModel::focusOrbit,
                    onNodeClicked = viewModel::onNodeClicked,
                    modifier = Modifier.fillMaxSize().padding(top = 124.dp)
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
                    contentTopInset = 128.dp
                )
            }
            // Search first so its result list draws over the toggle.
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Spacer(Modifier.height(64.dp))
                SegmentedToggle(
                    options = StructureView.entries.map { it.label },
                    selectedIndex = state.structureView.ordinal,
                    onSelect = { viewModel.setStructureView(StructureView.entries[it]) }
                )
            }
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
