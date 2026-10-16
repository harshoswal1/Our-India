@file:OptIn(ExperimentalMaterial3Api::class)

package com.ourindia.app.ui.civic

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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ourindia.app.data.civic.Complaint
import com.ourindia.app.data.geo.LocationStore
import com.ourindia.app.data.geo.ResolvedLocation
import com.ourindia.app.ui.partystructure.EmptyState
import com.ourindia.app.ui.partystructure.SegmentedToggle
import com.ourindia.app.ui.theme.CivicColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FeedSort(val label: String, val apiValue: String?) {
    LATEST("Latest", null),
    NEARBY("Near me", "nearby"),
    SUPPORTED("Most supported", "most_supported"),
    OVERDUE("Overdue", "most_overdue")
}

data class IssuesUiState(
    val loading: Boolean = true,
    val slow: Boolean = false,
    val error: String? = null,
    val complaints: List<Complaint> = emptyList(),
    val sort: FeedSort = FeedSort.LATEST,
    val category: IssueCategory? = null,
    val query: String = "",
    val rankings: List<Authority> = emptyList(),
    val rankingsError: String? = null,
    val city: Authority? = null,
    val authorities: List<Authority> = emptyList(),
    val here: ResolvedLocation? = null,
    val message: String? = null
) {
    val visible: List<Complaint>
        get() = if (query.isBlank()) complaints else complaints.filter {
            it.title.contains(query, true) || it.locality.contains(query, true) || it.id.contains(query, true) ||
                it.authorityName.orEmpty().contains(query, true) || it.administrativeArea.contains(query, true)
        }
}

@HiltViewModel
class IssuesViewModel @Inject constructor(
    private val repo: CivicRepository,
    private val locationStore: LocationStore
) : ViewModel() {
    private val _state = MutableStateFlow(IssuesUiState(city = repo.city.value))
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            repo.authorities().onSuccess { list ->
                _state.update { it.copy(authorities = list) }
                // Auto-filter by user's saved municipal corp from registration (only if no city chosen yet)
                if (_state.value.city == null) {
                    val savedCity = repo.municipalCity.value
                    if (!savedCity.isNullOrBlank()) {
                        val matched = list.firstOrNull { auth ->
                            auth.name.contains(savedCity.take(8), ignoreCase = true) ||
                                savedCity.contains(auth.name.take(8), ignoreCase = true)
                        }
                        if (matched != null) {
                            _state.update { it.copy(city = matched) }
                            refresh()
                        }
                    }
                }
            }
        }
        viewModelScope.launch { locationStore.location.collect { loc -> _state.update { it.copy(here = loc) } } }
    }

    fun setCity(a: Authority?) {
        repo.setCity(a)
        _state.update { it.copy(city = a) }
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, slow = false) }
            val slowFlag = launch { delay(4000); _state.update { it.copy(slow = true) } }
            val s = _state.value
            val here = locationStore.location.value
            val result = repo.feed(s.sort.apiValue, s.category?.code, here?.latitude, here?.longitude, s.city?.key)
            slowFlag.cancel()
            result.onSuccess { list -> _state.update { it.copy(loading = false, slow = false, complaints = list) } }
                // No fabricated fallback: the old client showed made-up Kolhapur complaints when offline.
                .onFailure { e -> _state.update { it.copy(loading = false, slow = false, error = e.message) } }
        }
        viewModelScope.launch {
            repo.rankings()
                .onSuccess { r -> _state.update { it.copy(rankings = r.rankings, rankingsError = null) } }
                .onFailure { e -> _state.update { it.copy(rankingsError = e.message) } }
        }
    }

    fun setSort(sort: FeedSort) {
        if (sort == FeedSort.NEARBY && locationStore.location.value == null) {
            _state.update { it.copy(message = "Use your location from Home first to sort by distance") }
            return
        }
        _state.update { it.copy(sort = sort) }
        refresh()
    }

    fun setCategory(c: IssueCategory?) {
        _state.update { it.copy(category = c) }
        refresh()
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun messageShown() = _state.update { it.copy(message = null) }

    /** Optimistic toggle; rolls back if the server refuses (e.g. not signed in). */
    fun toggleSupport(c: Complaint, onNeedsSignIn: () -> Unit) {
        if (repo.user.value == null) { onNeedsSignIn(); return }
        fun flip(list: List<Complaint>) = list.map {
            if (it.id != c.id) it else it.copy(
                userHasSupported = if (it.supported) 0 else 1,
                supportCount = it.supportCount + if (it.supported) -1 else 1
            )
        }
        _state.update { it.copy(complaints = flip(it.complaints)) }
        viewModelScope.launch {
            repo.toggleSupport(c.id)
                .onSuccess { r -> _state.update { s -> s.copy(complaints = s.complaints.map { if (it.id == c.id) it.copy(supportCount = r.supportCount, userHasSupported = if (r.supported) 1 else 0) else it }) } }
                .onFailure { e -> _state.update { it.copy(complaints = flip(it.complaints), message = e.message) } }
        }
    }
}

@Composable
fun IssuesScreen(
    onOpenIssue: (String) -> Unit,
    onReport: () -> Unit,
    onNeedsSignIn: () -> Unit,
    viewModel: IssuesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { NagrikVoiceMark(subtitle = "Civic issues, on the record") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CivicColors.Background)
            )
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = onReport,
                    containerColor = CivicColors.Civic,
                    contentColor = Color.White,
                    text = { Text("Report an issue") },
                    icon = { Icon(Icons.Outlined.Inbox, contentDescription = null) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = CivicColors.Background
    ) { padding ->
        Column(Modifier.padding(padding)) {
            SegmentedToggle(
                options = listOf("Reports", "City ranking"),
                selectedIndex = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            when (tab) {
                0 -> FeedTab(state, viewModel, onOpenIssue, onReport, onNeedsSignIn)
                else -> RankingTab(state, onRetry = viewModel::refresh)
            }
        }
    }
}

@Composable
private fun FeedTab(
    state: IssuesUiState,
    viewModel: IssuesViewModel,
    onOpenIssue: (String) -> Unit,
    onReport: () -> Unit,
    onNeedsSignIn: () -> Unit
) {
    var pickingCity by rememberSaveable { mutableStateOf(false) }
    if (pickingCity) {
        CityPickerSheet(
            authorities = state.authorities,
            here = state.here,
            selected = state.city,
            onPick = { pickingCity = false; viewModel.setCity(it) },
            onDismiss = { pickingCity = false }
        )
    }
    PullToRefreshBox(isRefreshing = state.loading && state.complaints.isNotEmpty(), onRefresh = viewModel::refresh) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    onClick = { pickingCity = true },
                    enabled = state.authorities.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    color = CivicColors.CivicContainer
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.LocationCity, contentDescription = null, tint = CivicColors.CivicDeep)
                        Column(Modifier.weight(1f)) {
                            Text("SHOWING", style = MaterialTheme.typography.labelSmall, color = CivicColors.CivicDeep.copy(alpha = 0.7f))
                            Text(state.city?.name ?: "All cities", style = MaterialTheme.typography.titleSmall, color = CivicColors.CivicDeep, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("Change", style = MaterialTheme.typography.labelLarge, color = CivicColors.CivicDeep)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text("Search title, area, city or reference ID") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = CivicColors.TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CivicColors.Surface,
                        unfocusedContainerColor = CivicColors.Surface,
                        focusedBorderColor = CivicColors.Civic,
                        unfocusedBorderColor = CivicColors.Outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FeedSort.entries) { sort ->
                        CivicChip(sort.label, selected = state.sort == sort) { viewModel.setSort(sort) }
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { CivicChip("All types", selected = state.category == null) { viewModel.setCategory(null) } }
                    items(IssueCategory.entries) { c ->
                        CivicChip(c.label, selected = state.category == c, icon = c.icon) {
                            viewModel.setCategory(if (state.category == c) null else c)
                        }
                    }
                }
            }

            when {
                state.loading && state.complaints.isEmpty() -> item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = CivicColors.Civic)
                        if (state.slow) WakingServerHint()
                    }
                }
                state.error != null && state.complaints.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Outlined.CloudOff,
                        title = "Couldn't load reports",
                        message = state.error,
                        action = { OutlinedButton(onClick = viewModel::refresh) { Text("Try again", color = CivicColors.CivicDeep) } }
                    )
                }
                state.visible.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Outlined.Inbox,
                        title = if (state.query.isNotBlank()) "No reports match" else "No reports yet",
                        message = if (state.query.isNotBlank()) "Try a different word or clear the filters."
                        else "Nothing reported ${state.city?.let { "in ${it.shortName} " } ?: ""}for this filter yet. Seen a pothole or a broken light? Put it on the record.",
                        action = { Button(onClick = onReport, colors = ButtonDefaults.buttonColors(containerColor = CivicColors.Civic)) { Text("Report an issue") } }
                    )
                }
                else -> items(state.visible, key = { it.id }) { c ->
                    IssueCard(
                        complaint = c,
                        onOpen = { onOpenIssue(c.id) },
                        onSupport = { viewModel.toggleSupport(c, onNeedsSignIn) }
                    )
                }
            }
        }
    }
}

@Composable
fun CivicChip(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp)) } },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = CivicColors.Surface,
            selectedContainerColor = CivicColors.Civic,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
            iconColor = CivicColors.TextSecondary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = CivicColors.Outline,
            selectedBorderColor = CivicColors.Civic
        )
    )
}

@Composable
private fun RankingTab(state: IssuesUiState, onRetry: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Corporations ranked by unresolved complaints — counted only after delivery is confirmed, and cleared only when a fix is verified.",
                style = MaterialTheme.typography.bodyMedium,
                color = CivicColors.TextSecondary
            )
        }
        if (CivicConfig.SIMULATED_DELIVERY) item { DemoDeliveryNote() }
        if (state.rankings.isEmpty()) {
            item {
                if (state.rankingsError != null) {
                    EmptyState(Icons.Outlined.CloudOff, "Couldn't load the ranking", state.rankingsError,
                        action = { OutlinedButton(onClick = onRetry) { Text("Try again", color = CivicColors.CivicDeep) } })
                } else {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CivicColors.Civic) }
                }
            }
        }
        // With 50 cities and few reports, rank only cities with activity; list the rest compactly
        // instead of 50 identical "0 unresolved" cards.
        val (active, idle) = state.rankings.partition { it.unresolvedCount + it.resolvedVerifiedCount + it.awaitingEmailCount + it.failedDeliveryCount > 0 }
        items(active.sortedByDescending { it.unresolvedCount }.withIndex().toList(), key = { it.value.key }) { (i, a) ->
            AuthorityRow(rank = i + 1, a = a)
        }
        if (idle.isNotEmpty()) item(key = "idle") {
            Surface(shape = RoundedCornerShape(20.dp), color = CivicColors.Surface, border = BorderStroke(1.dp, CivicColors.Outline)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (active.isEmpty()) "No confirmed reports anywhere yet" else "No confirmed reports yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = CivicColors.Navy
                    )
                    idle.groupBy { it.state.ifBlank { "Other" } }.toSortedMap().forEach { (st, list) ->
                        Column {
                            Text(st.uppercase(), style = MaterialTheme.typography.labelSmall, color = CivicColors.TextMuted)
                            Text(list.joinToString(" · ") { it.shortName }, style = MaterialTheme.typography.bodyMedium, color = CivicColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

/** "Pune Municipal Corporation (PMC)" → "Pune"; for dense lists where the full title is noise. */
val Authority.shortName: String
    get() {
        val base = name.substringBefore(" (").trim()
        val cut = listOf(" City Municipal Corporation", " Municipal Corporation", " Municipal Council", " Mahanagara Palike", " Corporation")
            .firstOrNull { base.endsWith(it) }
        return when {
            cut == null -> base
            name.contains("Council") -> base.removeSuffix(cut) + " (council)"
            else -> base.removeSuffix(cut)
        }
    }

@Composable
private fun CityPickerSheet(
    authorities: List<Authority>,
    here: ResolvedLocation?,
    selected: Authority?,
    onPick: (Authority?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val near = remember(authorities, here) {
        val lat = here?.latitude
        val lng = here?.longitude
        if (lat == null || lng == null) emptyList()
        else CivicRepository.byDistance(authorities, lat, lng).filter { it.second <= CivicConfig.COVERAGE_RADIUS_KM }
    }
    val groups = remember(authorities, query) {
        authorities
            .filter { query.isBlank() || it.name.contains(query, true) || it.district.contains(query, true) || it.state.contains(query, true) }
            .groupBy { it.state.ifBlank { "Other" } }
            .toSortedMap()
            .mapValues { (_, list) -> list.sortedBy { it.name } }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CivicColors.Surface) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Choose a city", style = MaterialTheme.typography.titleLarge, color = CivicColors.Navy)
            Text("${authorities.size} corporations and councils are on NagrikVoice.", style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("City, district or state") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = CivicColors.TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CivicColors.Civic, unfocusedBorderColor = CivicColors.Outline),
                modifier = Modifier.fillMaxWidth()
            )
        }
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
            if (query.isBlank()) {
                item(key = "all") { CityRow("All cities", null, selected == null) { onPick(null) } }
                if (near.isNotEmpty()) {
                    item(key = "h-near") { CityGroupHeader("Near you") }
                    items(near, key = { "n-" + it.first.key }) { (a, km) -> CityRow(a.name, "${km.toInt()} km away", selected?.key == a.key) { onPick(a) } }
                }
            }
            groups.forEach { (st, list) ->
                item(key = "h-$st") { CityGroupHeader(st) }
                items(list, key = { "c-" + it.key }) { a ->
                    CityRow(a.name, a.district.takeIf { it.isNotBlank() && !a.name.contains(it, true) }, selected?.key == a.key) { onPick(a) }
                }
            }
            if (groups.isEmpty()) item(key = "none") {
                Text("No city on NagrikVoice matches \"$query\".", style = MaterialTheme.typography.bodyMedium, color = CivicColors.TextSecondary, modifier = Modifier.padding(20.dp))
            }
        }
    }
}

@Composable
private fun CityGroupHeader(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = CivicColors.TextMuted, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun CityRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (selected) CivicColors.CivicDeep else CivicColors.Navy)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary) }
        }
        if (selected) Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = CivicColors.Civic)
    }
}

@Composable
private fun AuthorityRow(rank: Int, a: Authority) {
    Surface(shape = RoundedCornerShape(20.dp), color = CivicColors.Surface, border = BorderStroke(1.dp, CivicColors.Outline)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$rank", style = MaterialTheme.typography.headlineMedium, color = CivicColors.TextMuted, modifier = Modifier.width(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.titleMedium, color = CivicColors.Navy)
                    Text(listOf(a.district, a.state).filter { it.isNotBlank() }.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${a.unresolvedCount}", style = MaterialTheme.typography.headlineMedium, color = CivicColors.Navy)
                    Text("unresolved", style = MaterialTheme.typography.labelSmall, color = CivicColors.TextMuted)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text("Resolution rate", style = MaterialTheme.typography.labelMedium, color = CivicColors.TextSecondary, modifier = Modifier.weight(1f))
                    Text("${a.resolutionRatePercent.toInt()}%", style = MaterialTheme.typography.labelMedium, color = CivicColors.CivicDeep)
                }
                Box(Modifier.fillMaxWidth().height(8.dp).background(CivicColors.SurfaceVariant, CircleShape)) {
                    Box(Modifier.fillMaxWidth((a.resolutionRatePercent / 100.0).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(CivicColors.Civic, CircleShape))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("Resolved", a.resolvedVerifiedCount, Modifier.weight(1f))
                MiniStat("Overdue", a.overdueCount, Modifier.weight(1f))
                MiniStat("Failed sends", a.failedDeliveryCount, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: Int, modifier: Modifier) {
    Column(modifier.background(CivicColors.Background, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text("$value", style = MaterialTheme.typography.titleMedium, color = CivicColors.Navy)
        Text(label, style = MaterialTheme.typography.labelSmall, color = CivicColors.TextMuted)
    }
}
