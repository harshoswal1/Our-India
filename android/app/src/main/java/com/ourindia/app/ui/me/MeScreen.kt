@file:OptIn(ExperimentalMaterial3Api::class)

package com.ourindia.app.ui.me

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Science
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
import com.ourindia.app.BuildConfig
import com.ourindia.app.data.civic.CivicConfig
import com.ourindia.app.data.civic.CivicRepository
import com.ourindia.app.data.civic.CivicUser
import com.ourindia.app.data.civic.Complaint
import com.ourindia.app.data.leaders.LeaderUpdateDao
import com.ourindia.app.ui.civic.IssueCategory
import com.ourindia.app.ui.civic.IssueImage
import com.ourindia.app.ui.civic.NagrikVoiceMark
import com.ourindia.app.ui.civic.relativeTime
import com.ourindia.app.ui.civic.statusLook
import com.ourindia.app.ui.partystructure.initialsOf
import com.ourindia.app.ui.theme.CivicColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MeUiState(
    val user: CivicUser? = null,
    val loading: Boolean = false,
    val submitted: List<Complaint> = emptyList(),
    val supported: List<Complaint> = emptyList(),
    val error: String? = null,
    val leadersCheckedAt: String? = null
)

@HiltViewModel
class MeViewModel @Inject constructor(private val repo: CivicRepository, leaderUpdates: LeaderUpdateDao) : ViewModel() {
    private val _state = MutableStateFlow(MeUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { leaderUpdates.observeLastChecked().collect { d -> _state.update { it.copy(leadersCheckedAt = d) } } }
        viewModelScope.launch {
            repo.user.collect { u ->
                _state.update { it.copy(user = u, submitted = emptyList(), supported = emptyList()) }
                if (u != null) load(u.id)
            }
        }
    }

    fun refresh() { _state.value.user?.let { viewModelScope.launch { load(it.id) } } }

    private suspend fun load(userId: String) {
        _state.update { it.copy(loading = true, error = null) }
        repo.profile(userId)
            .onSuccess { p -> _state.update { it.copy(loading = false, submitted = p.submitted, supported = p.supported) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
    }

    fun signOut() = repo.signOut()
}

@Composable
fun MeScreen(onSignIn: () -> Unit, onOpenIssue: (String) -> Unit, onShowTutorial: () -> Unit, viewModel: MeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("You", style = MaterialTheme.typography.headlineSmall, color = CivicColors.Navy) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CivicColors.Background)
            )
        },
        containerColor = CivicColors.Background
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val user = state.user
            if (user == null) {
                item {
                    Surface(shape = RoundedCornerShape(24.dp), color = CivicColors.Surface, border = BorderStroke(1.dp, CivicColors.Outline)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            NagrikVoiceMark()
                            Text("Sign in to report problems, support reports and confirm fixes.", style = MaterialTheme.typography.bodyLarge, color = CivicColors.OnSurface)
                            Button(onClick = onSignIn, colors = ButtonDefaults.buttonColors(containerColor = CivicColors.Civic), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                                Text("Sign in or create account")
                            }
                        }
                    }
                }
            } else {
                item { ProfileHeader(user, reported = state.submitted.size, supported = state.supported.size) }
                if (state.loading) item { LinearProgressIndicator(color = CivicColors.Civic, trackColor = CivicColors.SurfaceVariant, modifier = Modifier.fillMaxWidth()) }
                state.error?.let { e -> item { Text(e, style = MaterialTheme.typography.bodySmall, color = CivicColors.Danger) } }
                item { ListHeader("Your reports") }
                if (state.submitted.isEmpty() && !state.loading) item { Muted("You haven't reported anything yet.") }
                items(state.submitted, key = { "s-${it.id}" }) { c -> MyIssueRow(c) { onOpenIssue(c.id) } }
                item { ListHeader("Reports you support") }
                if (state.supported.isEmpty() && !state.loading) item { Muted("Support a report to follow it here.") }
                items(state.supported, key = { "v-${it.id}" }) { c -> MyIssueRow(c) { onOpenIssue(c.id) } }
                item {
                    OutlinedButton(onClick = { confirmSignOut = true }, border = BorderStroke(1.dp, CivicColors.Outline), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = CivicColors.Navy, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign out", color = CivicColors.Navy)
                    }
                }
            }

            item {
                Surface(onClick = onShowTutorial, shape = RoundedCornerShape(16.dp), color = CivicColors.Surface, border = BorderStroke(1.dp, CivicColors.Outline)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = CivicColors.Navy)
                        Text("How Our India works", style = MaterialTheme.typography.titleSmall, color = CivicColors.Navy, modifier = Modifier.weight(1f))
                    }
                }
            }
            item { ListHeader("About the data") }
            item {
                AboutRow(Icons.Outlined.Policy, "Party leadership",
                    "Each named post links to the public report it was checked against. Posts we couldn't verify show as \"Not yet recorded\" rather than a guess. " +
                        "National and state heads are re-checked on Wikipedia monthly; a change shows straight away, marked as not yet verified." +
                        (state.leadersCheckedAt?.let { " Last checked $it." } ?: ""))
            }
            if (CivicConfig.SIMULATED_DELIVERY) {
                item {
                    AboutRow(Icons.Outlined.Science, "NagrikVoice deliveries are simulated",
                        "The server has no mail provider yet, so \"delivered\" reports are marked as demo deliveries. Rankings include them.")
                }
            }
            item {
                AboutRow(Icons.Outlined.Balance, "AI Lawyer answers come from an AI model",
                    "Questions go through the Our India server to Groq and are answered by an open model. It can be wrong or out of date: check anything important with an advocate or free legal aid (NALSA 15100).")
            }
            item { Muted("Our India ${BuildConfig.VERSION_NAME}") }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("You can still browse parties and reports. You'll need to sign in again to report or support.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; viewModel.signOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProfileHeader(user: CivicUser, reported: Int, supported: Int) {
    Surface(shape = RoundedCornerShape(24.dp), color = CivicColors.Navy) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).background(CivicColors.Saffron, CircleShape), contentAlignment = Alignment.Center) {
                    Text(initialsOf(user.name), style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
                Column {
                    Text(user.name, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text(user.email, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat("$reported", "reported")
                Stat("$supported", "supported")
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun ListHeader(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = CivicColors.TextMuted, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = CivicColors.TextSecondary)
}

@Composable
private fun MyIssueRow(c: Complaint, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = CivicColors.Surface, border = BorderStroke(1.dp, CivicColors.Outline)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IssueImage(c.photoUrl, IssueCategory.of(c.category), Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f)) {
                Text(c.title, style = MaterialTheme.typography.titleSmall, color = CivicColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${statusLook(c).label} · ${c.id} · ${relativeTime(c.createdAt)}", style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AboutRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, contentDescription = null, tint = CivicColors.TextSecondary, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = CivicColors.Navy)
            Text(body, style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary)
        }
    }
}
