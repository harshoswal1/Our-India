package com.ourindia.app.ui.partystructure

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import com.ourindia.app.data.geo.LocalGeographicResolver
import com.ourindia.app.data.geo.ResolvedLocation
import com.ourindia.app.ui.theme.CivicColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyStructureScreen(
    viewModel: PartyStructureViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeParty = PartyCatalog.getParty(uiState.selectedParty)

    BackHandler(enabled = uiState.currentScreen != FeatureScreen.PARTY_SELECTOR) {
        when (uiState.currentScreen) {
            FeatureScreen.POLITICIAN_PROFILE -> {
                viewModel.navigateBackFromProfile()
            }
            FeatureScreen.WORKSPACE -> {
                if (uiState.selectedNodeDetail != null) {
                    viewModel.dismissNodeDetail()
                } else if (uiState.activeSubModule == WorkspaceSubModule.MAP && uiState.selectedDistrict != null) {
                    viewModel.onMapDistrictSelected(null)
                } else if (uiState.activeSubModule == WorkspaceSubModule.MAP && uiState.selectedState != null) {
                    viewModel.onMapStateSelected(null)
                } else {
                    viewModel.navigateToPartySelector()
                }
            }
            else -> {}
        }
    }

    AnimatedContent(
        targetState = uiState.currentScreen,
        transitionSpec = {
            fadeIn() + slideInHorizontally() togetherWith fadeOut() + slideOutHorizontally()
        },
        label = "FeatureScreenTransition"
    ) { screen ->
        when (screen) {
            // ── Screen 1: Political Parties Selector ─────────────────────
            FeatureScreen.PARTY_SELECTOR -> {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Our India • Political Parties",
                                    fontWeight = FontWeight.Bold,
                                    color = CivicColors.Navy,
                                    fontSize = 18.sp
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = CivicColors.Background
                            )
                        )
                    },
                    containerColor = CivicColors.Background
                ) { innerPadding ->
                    PartySelectionGrid(
                        parties = PartyCatalog.parties,
                        onSelectParty = viewModel::onSelectPartyFromGrid,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }

            // ── Screen 2: Political Workspace (3 Switchable Sub-Modules) ─
            FeatureScreen.WORKSPACE -> {
                if (activeParty == null) return@AnimatedContent
                val screenContext = LocalContext.current
                val coroutineScope = rememberCoroutineScope()
                val resolver = remember { LocalGeographicResolver(screenContext) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${activeParty.symbolEmoji} ${activeParty.name}",
                                            fontWeight = FontWeight.Bold,
                                            color = CivicColors.Navy,
                                            fontSize = 16.sp
                                        )
                                    }
                                    Text(
                                        text = "Political Workspace • ${uiState.activeSubModule.title}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CivicColors.TextSecondary
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = viewModel::navigateToPartySelector) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Switch Party",
                                        tint = CivicColors.Navy
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { viewModel.loadData() }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh Data",
                                        tint = CivicColors.Navy
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = CivicColors.Background
                            )
                        )
                    },
                    containerColor = CivicColors.Background,
                    modifier = modifier
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp)
                        ) {
                            Spacer(modifier = Modifier.height(4.dp))

                            // 1. Sub-Module Switcher Tab Bar
                            WorkspaceSubModuleTabBar(
                                activeSubModule = uiState.activeSubModule,
                                onSubModuleSelected = viewModel::switchSubModule
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 2. Universal Search Bar (only shown in Hierarchy)
                            if (uiState.activeSubModule == WorkspaceSubModule.HIERARCHY) {
                                PartySearchBar(
                                    query = uiState.searchQuery,
                                    onQueryChange = viewModel::onSearchQueryChanged
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // 3. Location filter indicator (Active across all 3 sub-modules: Hierarchy, Map, Analytics)
                            val locationState = uiState.resolvedLocation?.state ?: uiState.selectedState
                            if (locationState != null) {
                                Surface(
                                    color = CivicColors.Saffron.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        val districtPart = uiState.resolvedLocation?.district ?: uiState.selectedDistrict
                                        Text(
                                            text = "📍 $locationState" + (districtPart?.let { " • $it" } ?: ""),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = CivicColors.Navy
                                        )
                                        TextButton(
                                            onClick = { viewModel.onLocationResolved(null) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text("Clear Location", fontSize = 11.sp, color = CivicColors.Saffron, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // 4. Sub-Module Content Viewport
                            if (uiState.isLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = CivicColors.Saffron)
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clipToBounds()
                                ) {
                                    when (uiState.activeSubModule) {
                                        // Sub-Module 1: Zoomable/Pannable Infinite Canvas Hierarchy
                                        WorkspaceSubModule.HIERARCHY -> {
                                            PartyHierarchyCanvas(
                                                rootNodes = uiState.rootNodes,
                                                allNodes = uiState.allFlattenedNodes,
                                                partyMetadata = activeParty,
                                                focusedNodeId = uiState.focusedNodeId,
                                                onNodeClicked = viewModel::onNodeClicked,
                                                onToggleExpand = viewModel::toggleNodeExpand
                                            )
                                        }

                                        // Sub-Module 2: Interactive Map Explorer
                                        WorkspaceSubModule.MAP -> {
                                            PartyMapExplorer(
                                                partyMetadata = activeParty,
                                                stateGeoDataList = uiState.stateGeoDataList,
                                                selectedState = uiState.selectedState,
                                                selectedDistrict = uiState.selectedDistrict,
                                                resolvedLocation = uiState.resolvedLocation,
                                                onStateSelected = viewModel::onMapStateSelected,
                                                onDistrictSelected = viewModel::onMapDistrictSelected,
                                                allNodes = uiState.allFlattenedNodes,
                                                onPoliticianClicked = viewModel::onNodeClicked,
                                                onLocateInHierarchy = viewModel::locateInHierarchy,
                                                onMapLongPressed = { lat, lng ->
                                                    coroutineScope.launch {
                                                        val loc = resolver.resolveLocation(lat, lng)
                                                        if (loc != null) {
                                                            viewModel.onLocationResolved(loc)
                                                        }
                                                    }
                                                }
                                            )
                                        }

                                        // Sub-Module 3: Analytics & Insights Dashboard
                                        WorkspaceSubModule.ANALYTICS -> {
                                            PartyAnalyticsDashboard(
                                                partyMetadata = activeParty,
                                                allNodes = uiState.allFlattenedNodes,
                                                stateGeoDataList = uiState.stateGeoDataList,
                                                selectedState = uiState.selectedState,
                                                onClearLocationFilter = { viewModel.onLocationResolved(null) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── Shared Floating My Location Button ────────────────────
                        val context = LocalContext.current

                        var hasLocationPermission by remember {
                            mutableStateOf(
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            )
                        }
                        var showPermissionDeniedMessage by remember { mutableStateOf(false) }
                        var showGpsDisabledDialog by remember { mutableStateOf(false) }

                        val permissionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestMultiplePermissions()
                        ) { permissions ->
                            val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                          permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
                            hasLocationPermission = granted
                            if (granted) {
                                val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
                                val isEnabled = lm?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                                               lm?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
                                if (!isEnabled) {
                                    showGpsDisabledDialog = true
                                } else {
                                    viewModel.setLocationLoading(true)
                                    triggerSharedLocationCentering(context, coroutineScope, resolver, viewModel::onLocationResolved)
                                }
                            } else {
                                showPermissionDeniedMessage = true
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(
                                    bottom = if (uiState.activeSubModule == WorkspaceSubModule.HIERARCHY) 96.dp else 24.dp,
                                    end = 24.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isLocationLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(66.dp),
                                    color = CivicColors.Saffron,
                                    strokeWidth = 3.dp
                                )
                            }

                            FloatingActionButton(
                                onClick = {
                                    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
                                    val isEnabled = lm?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                                                   lm?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true

                                    if (!hasLocationPermission) {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    } else if (!isEnabled) {
                                        showGpsDisabledDialog = true
                                    } else {
                                        viewModel.setLocationLoading(true)
                                        triggerSharedLocationCentering(context, coroutineScope, resolver, viewModel::onLocationResolved)
                                    }
                                },
                                containerColor = Color.White,
                                contentColor = CivicColors.Navy,
                                shape = CircleShape,
                                modifier = Modifier
                                    .border(1.dp, CivicColors.Navy.copy(alpha = 0.2f), CircleShape)
                                    .size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = "My Location",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        if (showPermissionDeniedMessage) {
                            AlertDialog(
                                onDismissRequest = { showPermissionDeniedMessage = false },
                                title = { Text("Location Permission Required") },
                                text = { Text("To inspect political presence in your region, please enable Location permissions in system settings.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showPermissionDeniedMessage = false
                                        try {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = android.net.Uri.fromParts("package", context.packageName, null)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {}
                                    }) {
                                        Text("Open Settings")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showPermissionDeniedMessage = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        if (showGpsDisabledDialog) {
                            AlertDialog(
                                onDismissRequest = { showGpsDisabledDialog = false },
                                title = { Text("Location Services Disabled") },
                                text = { Text("GPS/Location Services are currently disabled on your device. Please enable Location in system settings to locate your local political hierarchy.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showGpsDisabledDialog = false
                                        try {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {}
                                    }) {
                                        Text("Enable Location")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showGpsDisabledDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        // Floating Glassy Politician Popup
                        PoliticianGlassyPopup(
                            node = uiState.selectedNodeDetail,
                            onDismiss = viewModel::dismissNodeDetail,
                            onViewProfile = viewModel::navigateToProfile,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }

            // ── Screen 3: Dedicated Politician Profile & Timeline History
            FeatureScreen.POLITICIAN_PROFILE -> {
                val profile = uiState.selectedProfile
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

private fun triggerSharedLocationCentering(
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    resolver: com.ourindia.app.data.geo.GeographicResolver,
    onLocationResolved: (ResolvedLocation?) -> Unit
) {
    try {
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        val provider = if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
            android.location.LocationManager.GPS_PROVIDER
        } else {
            android.location.LocationManager.NETWORK_PROVIDER
        }

        @Suppress("MissingPermission")
        val lastKnown = locationManager.getLastKnownLocation(provider)
        if (lastKnown != null) {
            coroutineScope.launch {
                val resolved = resolver.resolveLocation(lastKnown.latitude, lastKnown.longitude)
                onLocationResolved(resolved)
            }
        } else {
            @Suppress("MissingPermission")
            locationManager.requestSingleUpdate(
                provider,
                object : android.location.LocationListener {
                    override fun onLocationChanged(location: android.location.Location) {
                        coroutineScope.launch {
                            val resolved = resolver.resolveLocation(location.latitude, location.longitude)
                            onLocationResolved(resolved)
                        }
                    }
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                },
                null
            )
        }
    } catch (e: SecurityException) {
        onLocationResolved(null)
    } catch (e: Exception) {
        onLocationResolved(null)
    }
}
