package com.ourindia.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ourindia.app.data.civic.Authority
import com.ourindia.app.data.civic.CivicConfig
import com.ourindia.app.data.civic.CivicRepository
import com.ourindia.app.data.civic.CivicUser
import com.ourindia.app.data.civic.Complaint
import com.ourindia.app.data.geo.LocalGeographicResolver
import com.ourindia.app.data.geo.LocationStore
import com.ourindia.app.data.geo.ResolvedLocation
import com.ourindia.app.data.local.entity.PartyNodeEntity
import com.ourindia.app.ui.civic.IssueCategory
import com.ourindia.app.ui.civic.IssueImage
import com.ourindia.app.ui.civic.NagrikVoiceMark
import com.ourindia.app.ui.civic.relativeTime
import com.ourindia.app.ui.civic.statusLook
import com.ourindia.app.ui.common.rememberLocateMe
import com.ourindia.app.ui.partystructure.PartyCatalog
import com.ourindia.app.ui.partystructure.PartyMetadata
import com.ourindia.app.ui.partystructure.PartySeedData
import com.ourindia.app.ui.partystructure.PartySymbol
import com.ourindia.app.ui.partystructure.TricolorRule
import com.ourindia.app.ui.theme.CivicColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocalLeader(val party: PartyMetadata, val node: PartyNodeEntity)

data class HomeUiState(
    val user: CivicUser? = null,
    val location: ResolvedLocation? = null,
    val locating: Boolean = false,
    val stateLeaders: List<LocalLeader> = emptyList(),
    val districtLeaders: List<LocalLeader> = emptyList(),
    val issues: List<Complaint> = emptyList(),
    val issuesLoading: Boolean = true,
    val issuesError: String? = null,
    val coveringAuthority: Authority? = null,
    val liveAuthorities: List<Authority> = emptyList(),
    val message: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val civic: CivicRepository,
    private val locationStore: LocationStore
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { civic.user.collect { u -> _state.update { it.copy(user = u) } } }
        viewModelScope.launch {
            civic.authorities().onSuccess { list -> _state.update { it.copy(liveAuthorities = list) }; updateCoverage() }
        }
        viewModelScope.launch {
            locationStore.location.collect { loc ->
                _state.update { it.copy(location = loc, locating = false) }
                computeLeaders(loc)
                updateCoverage()
                loadIssues(loc)
            }
        }
    }

    fun setLocating(on: Boolean) = _state.update { it.copy(locating = on) }
    fun onLocation(loc: ResolvedLocation) { locationStore.location.value = loc }
    fun onLocationFailed(msg: String) = _state.update { it.copy(locating = false, message = msg) }
    fun messageShown() = _state.update { it.copy(message = null) }

    /** The head of each party's unit for this state, and for this district where one exists. */
    private fun computeLeaders(loc: ResolvedLocation?) {
        val st = loc?.state
        if (st == null) {
            _state.update { it.copy(stateLeaders = emptyList(), districtLeaders = emptyList()) }
            return
        }
        fun named(n: PartyNodeEntity) = !n.holderName.contains("Not yet fetched", true)
        val all = PartySeedData.parties.flatMap { p -> PartySeedData.nodesFor(p).map { p to it } }
        val stateHeads = all.filter { (_, n) -> n.level == "STATE" && n.state.equals(st, true) && named(n) }
            .map { (p, n) -> LocalLeader(PartyCatalog.getParty(p), n) }
            .sortedByDescending { it.party.nationalSeats }
        val districtHeads = loc.district?.let { d ->
            all.filter { (_, n) -> n.level == "DISTRICT" && n.state.equals(st, true) && n.district.equals(d, true) && named(n) }
                .map { (p, n) -> LocalLeader(PartyCatalog.getParty(p), n) }
        }.orEmpty()
        _state.update { it.copy(stateLeaders = stateHeads, districtLeaders = districtHeads) }
    }

    private fun updateCoverage() {
        val loc = _state.value.location
        val lat = loc?.latitude
        val lng = loc?.longitude
        val covering = if (lat == null || lng == null) null else _state.value.liveAuthorities.firstOrNull { a ->
            a.centerLat != null && a.centerLng != null &&
                CivicRepository.distanceKm(lat, lng, a.centerLat, a.centerLng) <= CivicConfig.COVERAGE_RADIUS_KM
        }
        _state.update { it.copy(coveringAuthority = covering) }
    }

    fun refreshIssues() = viewModelScope.launch { loadIssues(_state.value.location) }

    private suspend fun loadIssues(loc: ResolvedLocation?) {
        _state.update { it.copy(issuesLoading = true, issuesError = null) }
        civic.feed(if (loc?.latitude != null) "nearby" else null, null, loc?.latitude, loc?.longitude)
            .onSuccess { list -> _state.update { it.copy(issuesLoading = false, issues = list.take(3)) } }
            .onFailure { e -> _state.update { it.copy(issuesLoading = false, issuesError = e.message) } }
    }
}

@Composable
fun HomeScreen(
    resolver: LocalGeographicResolver,
    onOpenParty: (party: String, nodeId: String?) -> Unit,
    onBrowseParties: () -> Unit,
    onOpenIssue: (String) -> Unit,
    onBrowseIssues: () -> Unit,
    onReport: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }
    val locate = rememberLocateMe(
        resolver = resolver,
        onStart = { viewModel.setLocating(true) },
        onResolved = viewModel::onLocation,
        onFailed = viewModel::onLocationFailed
    )

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = CivicColors.Background) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item { Header(state.user, Modifier.padding(horizontal = 20.dp)) }
            item { LocationCard(state, onLocate = locate, modifier = Modifier.padding(horizontal = 16.dp)) }

            if (state.location?.state != null) {
                item {
                    Section(
                        title = "Party heads in ${state.location?.state}",
                        action = "All parties",
                        onAction = onBrowseParties
                    ) {
                        if (state.stateLeaders.isEmpty()) {
                            Text(
                                "No verified state-unit heads recorded here yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CivicColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        } else {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.stateLeaders, key = { it.node.id }) { l -> LeaderTile(l) { onOpenParty(l.party.shortName, l.node.id) } }
                            }
                        }
                    }
                }
                if (state.districtLeaders.isNotEmpty()) {
                    item {
                        Section(title = "In ${state.location?.district}") {
                            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.districtLeaders.forEach { l -> LeaderRow(l) { onOpenParty(l.party.shortName, l.node.id) } }
                            }
                        }
                    }
                }
            } else {
                item {
                    Section(title = "Explore the parties", action = "Open", onAction = onBrowseParties) {
                        Text(
                            "Who leads each party, from the national office down to your district — every name linked to its source.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CivicColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            }

            item { CivicSection(state, onOpenIssue, onBrowseIssues, onReport, onRetry = { viewModel.refreshIssues() }) }
        }
    }
}

@Composable
private fun Header(user: CivicUser?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TricolorRule()
        Text("Our India", style = MaterialTheme.typography.displaySmall, color = CivicColors.Navy)
        Text(
            user?.name?.substringBefore(' ')?.let { "Namaste, $it. Here's what's around you." }
                ?: "Who runs your area, and what needs fixing in it.",
            style = MaterialTheme.typography.bodyLarge,
            color = CivicColors.TextSecondary
        )
    }
}

@Composable
private fun LocationCard(state: HomeUiState, onLocate: () -> Unit, modifier: Modifier) {
    val loc = state.location
    Surface(
        onClick = onLocate,
        enabled = !state.locating,
        shape = RoundedCornerShape(20.dp),
        color = if (loc == null) CivicColors.Navy else CivicColors.Surface,
        border = if (loc == null) null else BorderStroke(1.dp, CivicColors.Outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(44.dp).background(if (loc == null) Color.White.copy(alpha = 0.12f) else CivicColors.SurfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (state.locating) CircularProgressIndicator(strokeWidth = 2.dp, color = if (loc == null) Color.White else CivicColors.Navy, modifier = Modifier.size(20.dp))
                else Icon(Icons.Outlined.NearMe, contentDescription = null, tint = if (loc == null) Color.White else CivicColors.Navy)
            }
            Column(Modifier.weight(1f)) {
                if (loc == null) {
                    Text("Use my location", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("See your local party heads and issues near you", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                } else {
                    Text("NEAR YOU", style = MaterialTheme.typography.labelSmall, color = CivicColors.TextMuted)
                    Text(loc.label, style = MaterialTheme.typography.titleMedium, color = CivicColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (loc != null) Text("Update", style = MaterialTheme.typography.labelLarge, color = CivicColors.SaffronDeep)
        }
    }
}

@Composable
private fun Section(title: String, action: String? = null, onAction: () -> Unit = {}, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = CivicColors.Navy, modifier = Modifier.weight(1f))
            if (action != null) {
                Row(Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(action, style = MaterialTheme.typography.labelLarge, color = CivicColors.SaffronDeep)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CivicColors.SaffronDeep, modifier = Modifier.size(18.dp))
                }
            }
        }
        content()
    }
}

@Composable
private fun LeaderTile(l: LocalLeader, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = CivicColors.Surface, border = BorderStroke(1.dp, CivicColors.Outline), modifier = Modifier.width(196.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PartySymbol(l.party, 36.dp)
                Text(l.party.shortName, style = MaterialTheme.typography.labelLarge, color = CivicColors.TextSecondary)
            }
            Column {
                Text(l.node.holderName, style = MaterialTheme.typography.titleMedium, color = CivicColors.Navy, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                Text(l.node.roleTitle.substringBefore(" ("), style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(l.party.color, CircleShape))
        }
    }
}

@Composable
private fun LeaderRow(l: LocalLeader, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = CivicColors.Surface, border = BorderStroke(1.dp, CivicColors.Outline)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PartySymbol(l.party, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(l.node.holderName, style = MaterialTheme.typography.titleSmall, color = CivicColors.Navy)
                Text("${l.party.shortName} · ${l.node.roleTitle}", style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CivicColors.TextMuted)
        }
    }
}

/** NagrikVoice keeps its green identity inside Home: a distinct panel, not just another list. */
@Composable
private fun CivicSection(state: HomeUiState, onOpenIssue: (String) -> Unit, onBrowseIssues: () -> Unit, onReport: () -> Unit, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = CivicColors.CivicContainer.copy(alpha = 0.55f),
        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                NagrikVoiceMark(Modifier.weight(1f))
                TextButton(onClick = onBrowseIssues) { Text("All reports", color = CivicColors.CivicDeep) }
            }
            Text(
                when {
                    state.coveringAuthority != null -> "Live for ${state.coveringAuthority.name}. Report a problem and it goes on a public record."
                    state.liveAuthorities.isNotEmpty() -> "Live in ${state.liveAuthorities.joinToString { it.name }}. You can browse those reports and send test reports from anywhere."
                    else -> "Report civic problems and track whether they get fixed."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = CivicColors.CivicDeep,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    state.issuesLoading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CivicColors.Civic, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                    state.issuesError != null -> Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.issuesError, style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary, modifier = Modifier.weight(1f))
                        TextButton(onClick = onRetry) { Text("Retry", color = CivicColors.CivicDeep) }
                    }
                    state.issues.isEmpty() -> Text("No reports yet — yours could be the first.", style = MaterialTheme.typography.bodyMedium, color = CivicColors.TextSecondary, modifier = Modifier.padding(horizontal = 8.dp))
                    else -> state.issues.forEach { c -> IssueRow(c) { onOpenIssue(c.id) } }
                }
            }
            Button(
                onClick = onReport,
                colors = ButtonDefaults.buttonColors(containerColor = CivicColors.Civic),
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().heightIn(min = 50.dp)
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Report an issue")
            }
        }
    }
}

@Composable
private fun IssueRow(c: Complaint, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = CivicColors.Surface) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IssueImage(c.photoUrl, IssueCategory.of(c.category), Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f)) {
                Text(c.title, style = MaterialTheme.typography.titleSmall, color = CivicColors.Navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${statusLook(c).label} · ${c.supportCount} support · ${relativeTime(c.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CivicColors.TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
