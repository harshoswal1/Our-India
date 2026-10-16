@file:OptIn(ExperimentalMaterial3Api::class)

package com.ourindia.app.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ourindia.app.data.civic.CivicRepository
import com.ourindia.app.data.civic.Complaint
import com.ourindia.app.data.geo.LocalGeographicResolver
import com.ourindia.app.data.geo.LocationStore
import com.ourindia.app.data.geo.ResolvedLocation
import com.ourindia.app.ui.civic.ClaimFixSheet
import com.ourindia.app.ui.civic.IssueCategory
import com.ourindia.app.ui.civic.IssueImage
import com.ourindia.app.ui.civic.StatusPill
import com.ourindia.app.ui.civic.WakingServerHint
import com.ourindia.app.ui.civic.relativeTime
import com.ourindia.app.ui.civic.statusLook
import com.ourindia.app.ui.common.rememberLocateMe
import com.ourindia.app.ui.partystructure.EmptyState
import com.ourindia.app.ui.partystructure.TricolorRule
import com.ourindia.app.ui.theme.CivicColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.sin

/** Matches CivicApi.feed's default limit; a shorter page means the end of the feed. */
private const val PAGE = 30

data class HomeUiState(
    val here: ResolvedLocation? = null,
    val posts: List<Complaint> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val slow: Boolean = false,
    val error: String? = null,
    val claimingId: String? = null,
    val busy: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val civic: CivicRepository,
    private val locationStore: LocationStore
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()
    private var pageJob: Job? = null

    val signedIn get() = civic.user.value != null

    init {
        viewModelScope.launch { locationStore.location.collect { loc -> _state.update { it.copy(here = loc) } } }
        refresh()
    }

    // Newest first. The server's "nearby" only re-sorts one page by distance, so it can't
    // page; distance is shown on each post instead.
    fun refresh() {
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, slow = false, endReached = false) }
            val slow = launch { delay(4000); _state.update { it.copy(slow = true) } }
            val result = civic.feed(null, null, null, null)
            slow.cancel()
            result
                .onSuccess { list -> _state.update { it.copy(loading = false, slow = false, posts = list, endReached = list.size < PAGE) } }
                .onFailure { e -> _state.update { it.copy(loading = false, slow = false, error = e.message) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached || s.posts.isEmpty()) return
        _state.update { it.copy(loadingMore = true) }
        pageJob = viewModelScope.launch {
            civic.feed(null, null, null, null, offset = s.posts.size)
                .onSuccess { more ->
                    _state.update { st ->
                        val known = st.posts.mapTo(HashSet()) { it.id }
                        st.copy(loadingMore = false, posts = st.posts + more.filter { it.id !in known }, endReached = more.size < PAGE)
                    }
                }
                .onFailure { e -> _state.update { it.copy(loadingMore = false, message = e.message) } }
        }
    }

    /** Optimistic; a double-tap only ever adds support, like a like. */
    fun support(c: Complaint, addOnly: Boolean) {
        if (addOnly && c.supported) return
        fun flip(list: List<Complaint>) = list.map {
            if (it.id != c.id) it else it.copy(userHasSupported = if (it.supported) 0 else 1, supportCount = it.supportCount + if (it.supported) -1 else 1)
        }
        _state.update { it.copy(posts = flip(it.posts)) }
        viewModelScope.launch {
            civic.toggleSupport(c.id)
                .onSuccess { r -> _state.update { s -> s.copy(posts = s.posts.map { if (it.id == c.id) it.copy(supportCount = r.supportCount, userHasSupported = if (r.supported) 1 else 0) else it }) } }
                .onFailure { e -> _state.update { it.copy(posts = flip(it.posts), message = e.message) } }
        }
    }

    fun startClaim(id: String) = _state.update { it.copy(claimingId = id) }
    fun cancelClaim() = _state.update { it.copy(claimingId = null) }

    fun claimFix(evidence: String) {
        val id = _state.value.claimingId ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            civic.claimResolution(id, evidence)
                .onSuccess { _state.update { it.copy(busy = false, claimingId = null, message = "Thanks — your fix report is waiting for verification") }; refresh() }
                .onFailure { e -> _state.update { it.copy(busy = false, message = e.message) } }
        }
    }

    fun flag(id: String, reason: String) = viewModelScope.launch {
        civic.reportAbuse(id, reason)
            .onSuccess { _state.update { it.copy(message = "Reported. A moderator will review this post.") } }
            .onFailure { e -> _state.update { it.copy(message = e.message) } }
    }

    fun onLocation(loc: ResolvedLocation) { locationStore.location.value = loc }
    fun say(msg: String) = _state.update { it.copy(message = msg) }
    fun messageShown() = _state.update { it.copy(message = null) }
}

@Composable
fun HomeScreen(
    resolver: LocalGeographicResolver,
    locationAllowed: Boolean,
    onOpenIssue: (String) -> Unit,
    onReport: () -> Unit,
    onNeedsSignIn: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }

    val locate = rememberLocateMe(resolver = resolver, onStart = {}, onResolved = viewModel::onLocation, onFailed = {})
    // Permission is asked once at first launch (MainAppScreen); here we only use it.
    LaunchedEffect(locationAllowed) { if (locationAllowed && state.here == null) locate() }

    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> if (last >= list.layoutInfo.totalItemsCount - 4) viewModel.loadMore() }
    }
    var flagging by remember { mutableStateOf<Complaint?>(null) }

    Scaffold(
        topBar = {
            Column(Modifier.background(CivicColors.Background).statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)) {
                TricolorRule()
                Spacer(Modifier.height(8.dp))
                Text("Our India", style = MaterialTheme.typography.headlineMedium, color = CivicColors.Navy)
                Text(
                    state.here?.let { "Civic reports · you're in ${it.district ?: it.state}" } ?: "Civic reports from across the country",
                    style = MaterialTheme.typography.bodySmall,
                    color = CivicColors.TextSecondary
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = CivicColors.Background
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.posts.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding)
        ) {
            LazyColumn(state = list, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading && state.posts.isEmpty() -> item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(color = CivicColors.Civic)
                            if (state.slow) WakingServerHint()
                        }
                    }
                    state.error != null && state.posts.isEmpty() -> item {
                        EmptyState(
                            icon = Icons.Outlined.CloudOff,
                            title = "Couldn't load the feed",
                            message = state.error.orEmpty(),
                            action = { OutlinedButton(onClick = viewModel::refresh) { Text("Try again", color = CivicColors.CivicDeep) } },
                            modifier = Modifier.padding(top = 48.dp)
                        )
                    }
                    state.posts.isEmpty() -> item {
                        EmptyState(
                            icon = Icons.Outlined.PhotoCamera,
                            title = "No reports yet",
                            message = "Seen a pothole, a broken light or garbage left for days? Photograph it and put it on the record.",
                            action = { Button(onClick = onReport, colors = ButtonDefaults.buttonColors(containerColor = CivicColors.Civic)) { Text("Report an issue") } },
                            modifier = Modifier.padding(top = 48.dp)
                        )
                    }
                    else -> {
                        items(state.posts, key = { it.id }) { c ->
                            FeedPost(
                                c = c,
                                here = state.here,
                                onOpen = { onOpenIssue(c.id) },
                                onSupport = { addOnly -> if (viewModel.signedIn) viewModel.support(c, addOnly) else onNeedsSignIn() },
                                onShare = {
                                    val text = "${c.title} — reported on NagrikVoice (${c.id}) to ${c.authorityName ?: c.administrativeArea}, ${c.locality}\n" +
                                        "https://maps.google.com/?q=${c.latitude},${c.longitude}"
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share report"))
                                },
                                onMap = {
                                    val uri = Uri.parse("geo:${c.latitude},${c.longitude}?q=${c.latitude},${c.longitude}(${Uri.encode(c.title)})")
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure { viewModel.say("No maps app found") }
                                },
                                onClaim = { if (viewModel.signedIn) viewModel.startClaim(c.id) else onNeedsSignIn() },
                                onFlag = { flagging = c }
                            )
                        }
                        item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                when {
                                    state.loadingMore -> CircularProgressIndicator(color = CivicColors.Civic, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                                    state.endReached -> Text("You're all caught up", style = MaterialTheme.typography.bodySmall, color = CivicColors.TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.claimingId != null) {
        ClaimFixSheet(busy = state.busy, onDismiss = viewModel::cancelClaim, onSubmit = viewModel::claimFix)
    }
    flagging?.let { c -> FlagDialog(onDismiss = { flagging = null }, onFlag = { reason -> flagging = null; viewModel.flag(c.id, reason) }) }
}

@Composable
private fun FeedPost(
    c: Complaint,
    here: ResolvedLocation?,
    onOpen: () -> Unit,
    onSupport: (addOnly: Boolean) -> Unit,
    onShare: () -> Unit,
    onMap: () -> Unit,
    onClaim: () -> Unit,
    onFlag: () -> Unit
) {
    val category = IssueCategory.of(c.category)
    var burst by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val open = c.resolutionStatus == "UNRESOLVED" || c.resolutionStatus == "REOPENED"

    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp).background(CivicColors.Surface)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(CivicColors.CivicContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(category.icon, contentDescription = null, tint = CivicColors.CivicDeep, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    (c.authorityName ?: c.administrativeArea).substringBefore(" (").ifBlank { category.label },
                    style = MaterialTheme.typography.titleSmall,
                    color = CivicColors.Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(c.locality.ifBlank { null }, distanceLabel(here, c), relativeTime(c.createdAt).ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = CivicColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = CivicColors.Navy) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Open full record") }, leadingIcon = { Icon(Icons.Outlined.Description, null) }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text("Show on map") }, leadingIcon = { Icon(Icons.Outlined.Place, null) }, onClick = { menu = false; onMap() })
                    DropdownMenuItem(text = { Text("Report this post") }, leadingIcon = { Icon(Icons.Outlined.Flag, null) }, onClick = { menu = false; onFlag() })
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .pointerInput(c.id) {
                    detectTapGestures(
                        onDoubleTap = { burst++; onSupport(true) },
                        onTap = { onOpen() }
                    )
                }
        ) {
            IssueImage(c.photoUrl, category, Modifier.matchParentSize(), contentDescription = c.title)
            StatusPill(statusLook(c), Modifier.align(Alignment.TopStart).padding(12.dp))
            SupportBurst(burst, Modifier.align(Alignment.Center))
        }

        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSupport(false) }) {
                Icon(
                    if (c.supported) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    contentDescription = if (c.supported) "Remove support" else "Support",
                    tint = if (c.supported) CivicColors.Civic else CivicColors.Navy
                )
            }
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "Share", tint = CivicColors.Navy) }
            IconButton(onClick = onMap) { Icon(Icons.Outlined.Place, contentDescription = "Show on map", tint = CivicColors.Navy) }
            Spacer(Modifier.weight(1f))
            if (open) {
                TextButton(onClick = onClaim) {
                    Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = CivicColors.CivicDeep, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Fixed?", color = CivicColors.CivicDeep)
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (c.supportCount == 1) "1 supporter" else "${c.supportCount} supporters",
                style = MaterialTheme.typography.labelLarge,
                color = CivicColors.Navy
            )
            Text(c.title, style = MaterialTheme.typography.titleMedium, color = CivicColors.Navy)
            if (c.description.isNotBlank() && c.description != c.title) {
                Text(
                    c.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CivicColors.OnSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            }
            Text(
                "${category.label}${c.subtype?.let { " · $it" } ?: ""} · ${c.id}",
                style = MaterialTheme.typography.bodySmall,
                color = CivicColors.TextMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )
        }
    }
}

/** The double-tap thumb: swells and fades over the photo. */
@Composable
private fun SupportBurst(trigger: Int, modifier: Modifier) {
    val p = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        p.snapTo(0f)
        p.animateTo(1f, tween(700))
    }
    val v = p.value
    if (v < 1f) {
        Icon(
            Icons.Filled.ThumbUp,
            contentDescription = null,
            tint = Color.White,
            modifier = modifier
                .size(96.dp)
                .graphicsLayer {
                    val s = 0.6f + 0.5f * sin(v * PI / 2).toFloat()
                    scaleX = s
                    scaleY = s
                    alpha = if (v < 0.6f) 1f else (1f - v) / 0.4f
                }
        )
    }
}

private fun distanceLabel(here: ResolvedLocation?, c: Complaint): String? {
    val lat = here?.latitude ?: return null
    val lng = here.longitude ?: return null
    val km = CivicRepository.distanceKm(lat, lng, c.latitude, c.longitude)
    return when {
        km < 1 -> "near you"
        km < 50 -> "${km.toInt()} km away"
        else -> null // another city: the corporation name already says where
    }
}

private val FLAG_REASONS = listOf("Fake or misleading", "Not a civic issue", "Offensive photo", "Shows someone's personal details")

@Composable
private fun FlagDialog(onDismiss: () -> Unit, onFlag: (String) -> Unit) {
    var reason by remember { mutableStateOf(FLAG_REASONS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Flag, contentDescription = null, tint = CivicColors.Danger) },
        title = { Text("Report this post?") },
        text = {
            Column {
                Text("A moderator reviews flagged posts. What's wrong with it?", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                FLAG_REASONS.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clip(CircleShape).selectable(selected = reason == r, onClick = { reason = r }).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == r, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(r, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onFlag(reason) }) { Text("Report", color = CivicColors.Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
