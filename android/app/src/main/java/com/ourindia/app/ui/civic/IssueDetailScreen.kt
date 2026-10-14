@file:OptIn(ExperimentalMaterial3Api::class)

package com.ourindia.app.ui.civic

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ourindia.app.data.civic.CivicRepository
import com.ourindia.app.data.civic.Complaint
import com.ourindia.app.data.civic.StatusChange
import com.ourindia.app.ui.partystructure.EmptyState
import com.ourindia.app.ui.theme.CivicColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IssueDetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val complaint: Complaint? = null,
    val history: List<StatusChange> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class IssueDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: CivicRepository
) : ViewModel() {
    val id: String = checkNotNull(savedState["id"])
    private val _state = MutableStateFlow(IssueDetailState())
    val state = _state.asStateFlow()
    val signedIn get() = repo.user.value != null

    init { load() }

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        repo.detail(id)
            .onSuccess { d -> _state.update { it.copy(loading = false, complaint = d.complaint, history = d.history) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
    }

    fun toggleSupport() {
        val c = _state.value.complaint ?: return
        _state.update { it.copy(complaint = c.copy(userHasSupported = if (c.supported) 0 else 1, supportCount = c.supportCount + if (c.supported) -1 else 1)) }
        viewModelScope.launch {
            repo.toggleSupport(c.id)
                .onSuccess { r -> _state.update { s -> s.copy(complaint = s.complaint?.copy(supportCount = r.supportCount, userHasSupported = if (r.supported) 1 else 0)) } }
                .onFailure { e -> _state.update { it.copy(complaint = c, message = e.message) } }
        }
    }

    fun claimFix(evidence: String, onDone: () -> Unit) = act({ repo.claimResolution(id, evidence) }, "Thanks — your fix report is waiting for verification", onDone)

    fun retryDelivery() = act({ repo.retryDelivery(id) }, "Delivery retried with the same reference ID") {}

    private fun act(call: suspend () -> Result<*>, success: String, onDone: () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        call()
            .onSuccess { _state.update { it.copy(busy = false, message = success) }; onDone(); load() }
            .onFailure { e -> _state.update { it.copy(busy = false, message = e.message) } }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

@Composable
fun IssueDetailScreen(
    onBack: () -> Unit,
    onNeedsSignIn: () -> Unit,
    viewModel: IssueDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showClaim by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.complaint?.id ?: viewModel.id, style = MaterialTheme.typography.titleMedium, color = CivicColors.Navy) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CivicColors.Navy) } },
                actions = {
                    state.complaint?.let { c ->
                        IconButton(onClick = {
                            // The server has no public web page per report, so link the spot, not a dead URL.
                            val text = "${c.title} — reported on NagrikVoice (${c.id}) to ${c.authorityName ?: c.administrativeArea}, ${c.locality}\n" +
                                "https://maps.google.com/?q=${c.latitude},${c.longitude}"
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share issue"))
                        }) { Icon(Icons.Outlined.Share, contentDescription = "Share", tint = CivicColors.Navy) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CivicColors.Background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = CivicColors.Background
    ) { padding ->
        val c = state.complaint
        when {
            c == null && state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CivicColors.Civic) }
            c == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Outlined.CloudOff, "Couldn't open this report", state.error ?: "Unknown error",
                    action = { OutlinedButton(onClick = { viewModel.load() }) { Text("Try again", color = CivicColors.CivicDeep) } })
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val category = IssueCategory.of(c.category)
                IssueImage(
                    c.photoUrl, category, contentDescription = c.title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(24.dp))
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(statusLook(c))
                        Text("${category.label}${c.subtype?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.labelMedium, color = CivicColors.TextSecondary)
                    }
                    Text(c.title, style = MaterialTheme.typography.headlineSmall, color = CivicColors.Navy)
                    Text(
                        listOfNotNull(c.landmark, c.locality.ifBlank { null }).joinToString(" · ") + "  ·  reported ${relativeTime(c.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CivicColors.TextSecondary
                    )
                }
                if (c.description.isNotBlank()) {
                    Text(c.description, style = MaterialTheme.typography.bodyLarge, color = CivicColors.OnSurface)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SupportButton(c.supportCount, c.supported, onClick = { if (viewModel.signedIn) viewModel.toggleSupport() else onNeedsSignIn() }, height = 44.dp)
                    if (c.resolutionStatus == "UNRESOLVED" || c.resolutionStatus == "REOPENED") {
                        OutlinedButton(
                            onClick = { if (viewModel.signedIn) showClaim = true else onNeedsSignIn() },
                            border = BorderStroke(1.dp, CivicColors.Outline),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = CivicColors.CivicDeep, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("It's been fixed", color = CivicColors.CivicDeep)
                        }
                    }
                }

                DetailBlock("Sent to") {
                    Text(c.authorityName ?: c.administrativeArea, style = MaterialTheme.typography.titleSmall, color = CivicColors.Navy)
                    c.departmentName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CivicColors.TextSecondary) }
                    Spacer(Modifier.height(8.dp))
                    DeliveryLine(c, busy = state.busy, onRetry = viewModel::retryDelivery)
                    TextButton(
                        onClick = {
                            val uri = Uri.parse("geo:${c.latitude},${c.longitude}?q=${c.latitude},${c.longitude}(${Uri.encode(c.title)})")
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Outlined.Place, contentDescription = null, tint = CivicColors.CivicDeep, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open location in maps", color = CivicColors.CivicDeep)
                    }
                }

                DetailBlock("Record") {
                    if (state.history.isEmpty()) {
                        Text("Submitted ${relativeTime(c.createdAt)}. No status changes yet.", style = MaterialTheme.typography.bodyMedium, color = CivicColors.TextSecondary)
                    } else {
                        state.history.forEachIndexed { i, h -> HistoryRow(h, last = i == state.history.lastIndex) }
                    }
                }
            }
        }
    }

    if (showClaim) {
        ClaimFixSheet(
            busy = state.busy,
            onDismiss = { showClaim = false },
            onSubmit = { viewModel.claimFix(it) { showClaim = false } }
        )
    }
}

@Composable
private fun DeliveryLine(c: Complaint, busy: Boolean, onRetry: () -> Unit) {
    val (icon, text, tint) = when (c.emailStatus) {
        "DELIVERED" -> Triple(Icons.Outlined.MarkEmailRead, "Delivered${relativeTime(c.deliveredTimestamp ?: "").let { if (it.isBlank()) "" else " $it" }}", CivicColors.CivicDeep)
        "FAILED" -> Triple(Icons.Outlined.ErrorOutline, "Email delivery failed", CivicColors.Danger)
        else -> Triple(Icons.Outlined.Schedule, "Waiting for delivery confirmation", CivicColors.Amber)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint, modifier = Modifier.weight(1f))
        if (c.emailStatus == "FAILED") {
            TextButton(onClick = onRetry, enabled = !busy) { Text("Retry", color = CivicColors.CivicDeep) }
        }
    }
    if (c.emailStatus == "DELIVERED") {
        Spacer(Modifier.height(8.dp))
        DemoDeliveryNote()
    }
}

@Composable
private fun DetailBlock(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = CivicColors.Surface, border = BorderStroke(1.dp, CivicColors.Outline)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = CivicColors.TextMuted)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

private fun humanStatus(code: String) = when (code) {
    "PENDING_EMAIL" -> "Awaiting delivery"
    "UNRESOLVED" -> "Delivered, unresolved"
    "RESOLUTION_CLAIMED" -> "Fix reported"
    "RESOLUTION_UNDER_REVIEW" -> "Fix under review"
    "RESOLVED_VERIFIED" -> "Fix verified"
    "REOPENED" -> "Reopened"
    "INVALIDATED" -> "Invalidated"
    else -> code.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun HistoryRow(h: StatusChange, last: Boolean) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier.width(20.dp).fillMaxHeight().drawBehind {
                if (!last) drawLine(CivicColors.Outline, Offset(size.width / 2, 14.dp.toPx()), Offset(size.width / 2, size.height), 2.dp.toPx())
            },
            contentAlignment = Alignment.TopCenter
        ) {
            Box(Modifier.padding(top = 5.dp).size(10.dp).background(CivicColors.Civic, CircleShape))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.padding(bottom = if (last) 0.dp else 14.dp)) {
            Text(humanStatus(h.newStatus), style = MaterialTheme.typography.titleSmall, color = CivicColors.Navy)
            if (h.reason.isNotBlank()) Text(h.reason, style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary)
            Text(relativeTime(h.timestamp), style = MaterialTheme.typography.labelSmall, color = CivicColors.TextMuted)
        }
    }
}

@Composable
private fun ClaimFixSheet(busy: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CivicColors.Surface) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Report that it's fixed", style = MaterialTheme.typography.headlineSmall, color = CivicColors.Navy)
            Text(
                "The issue stays open until someone independent verifies the fix. Say what changed and when.",
                style = MaterialTheme.typography.bodyMedium,
                color = CivicColors.TextSecondary
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("e.g. The pothole was filled and resurfaced on Monday") },
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onSubmit(text.trim()) },
                enabled = text.isNotBlank() && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = CivicColors.Civic),
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
            ) { Text(if (busy) "Sending…" else "Send for verification") }
        }
    }
}
