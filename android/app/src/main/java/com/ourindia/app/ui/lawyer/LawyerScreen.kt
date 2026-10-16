@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ourindia.app.ui.lawyer

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ourindia.app.data.lawyer.ChatMessage
import com.ourindia.app.data.lawyer.LawyerClient
import com.ourindia.app.ui.partystructure.SegmentedToggle
import com.ourindia.app.ui.theme.CivicColors

/** The AI Lawyer keeps its own accent (violet) on the shared paper, like NagrikVoice keeps green. */
internal val LawAccent = CivicColors.Violet
internal val LawContainer = CivicColors.VioletContainer

private val SUGGESTIONS = listOf(
    "My cheque bounced. What can I do?",
    "Can police check my phone without a warrant?",
    "How do I file a Zero FIR in another city?",
    "My landlord wants me out without notice",
    "What is IPC 420 called under BNS?",
    "I got a defective product online"
)

@Composable
fun LawyerScreen(onClose: () -> Unit, viewModel: LawyerViewModel = viewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CivicColors.Navy) } },
                title = { LawyerMark() },
                actions = {
                    if (s.tab == LawyerTab.ASK && s.messages.isNotEmpty()) {
                        IconButton(onClick = viewModel::newConversation) { Icon(Icons.Outlined.EditNote, contentDescription = "New conversation", tint = CivicColors.Navy) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CivicColors.Background)
            )
        },
        containerColor = CivicColors.Background
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SegmentedToggle(
                options = LawyerTab.entries.map { it.label },
                selectedIndex = s.tab.ordinal,
                onSelect = { viewModel.selectTab(LawyerTab.entries[it]) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            when (s.tab) {
                LawyerTab.ASK -> AskTab(s, viewModel)
                LawyerTab.DRAFT -> DraftTab(s, viewModel)
                LawyerTab.SECTIONS -> SectionsTab(s, viewModel)
                LawyerTab.RIGHTS -> RightsTab()
            }
        }
    }
}

@Composable
private fun LawyerMark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LawBadge(34.dp)
        Column {
            Text("AI Lawyer", style = MaterialTheme.typography.titleMedium, color = CivicColors.Navy)
            Text("न्याय साथी · Indian law in plain words", style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary)
        }
    }
}

@Composable
internal fun LawBadge(size: Dp) {
    Box(Modifier.size(size).background(LawContainer, CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Balance, contentDescription = null, tint = LawAccent, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun AskTab(s: LawyerUiState, vm: LawyerViewModel) {
    val list = rememberLazyListState()
    LaunchedEffect(s.messages.size, s.asking, s.askError) {
        val last = list.layoutInfo.totalItemsCount - 1
        if (last > 0) list.animateScrollToItem(last)
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (s.messages.isEmpty()) item { Welcome(onSuggestion = vm::ask) }
            items(s.messages) { m -> if (m.role == "user") UserBubble(m.content) else AnswerCard(m) }
            if (s.asking) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 4.dp)) {
                    CircularProgressIndicator(color = LawAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Text("Reading the law…", style = MaterialTheme.typography.bodyMedium, color = CivicColors.TextSecondary)
                }
            }
            s.askError?.let { e ->
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = CivicColors.DangerContainer) {
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(e, style = MaterialTheme.typography.bodyMedium, color = CivicColors.Danger, modifier = Modifier.weight(1f))
                            if (LawyerClient.configured) TextButton(onClick = vm::retry) { Text("Retry", color = CivicColors.Danger) }
                        }
                    }
                }
            }
        }
        InputBar(s, vm)
    }
}

@Composable
private fun Welcome(onSuggestion: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Ask about a legal problem", style = MaterialTheme.typography.headlineSmall, color = CivicColors.Navy)
        Text(
            "Police, property, money, consumer or family matters. Answers explain the law in plain words, cite the BNS and old IPC sections, and say what you can do next.",
            style = MaterialTheme.typography.bodyLarge,
            color = CivicColors.OnSurface
        )
        if (!LawyerClient.configured) {
            Surface(shape = RoundedCornerShape(16.dp), color = CivicColors.AmberContainer) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = CivicColors.Amber)
                    Text(
                        "AI answers aren't set up in this build. Sections and Rights still work offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CivicColors.OnSurface
                    )
                }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SUGGESTIONS.forEach { q -> LawChip(q, selected = false) { onSuggestion(q) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = CivicColors.TextMuted, modifier = Modifier.size(16.dp).padding(top = 2.dp))
            Text(
                "Questions pass through the Our India server to Groq's AI service. Leave out Aadhaar, bank and password details. This is general information, not a substitute for an advocate.",
                style = MaterialTheme.typography.bodySmall,
                color = CivicColors.TextSecondary
            )
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp), color = CivicColors.Navy, modifier = Modifier.widthIn(max = 300.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun AnswerCard(m: ChatMessage) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val plain = m.content.replace("**", "")
    Surface(shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp), color = CivicColors.Surface, border = BorderStroke(1.dp, CivicColors.Outline)) {
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LawBadge(24.dp)
                Text("AI Lawyer", style = MaterialTheme.typography.labelLarge, color = LawAccent, modifier = Modifier.padding(start = 8.dp).weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(plain)) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy answer", tint = CivicColors.TextMuted, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, plain), "Share answer"))
                }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share answer", tint = CivicColors.TextMuted, modifier = Modifier.size(18.dp))
                }
            }
            Text(withBold(m.content), style = MaterialTheme.typography.bodyLarge, color = CivicColors.OnSurface, modifier = Modifier.padding(end = 10.dp))
        }
    }
}

/** **x** → bold navy span; everything else as written. */
internal fun withBold(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    Regex("\\*\\*(.+?)\\*\\*").findAll(text).forEach { m ->
        append(text.substring(i, m.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = CivicColors.Navy)) { append(m.groupValues[1]) }
        i = m.range.last + 1
    }
    append(text.substring(i).replace("**", ""))
}

@Composable
private fun InputBar(s: LawyerUiState, vm: LawyerViewModel) {
    val can = LawyerClient.configured && s.input.isNotBlank() && !s.asking
    Surface(color = CivicColors.Surface, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = s.input,
                onValueChange = vm::setInput,
                // Always typeable: a disabled field never gets the keyboard, which read as broken.
                placeholder = { Text("Describe your situation…") },
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                colors = lawFieldColors(),
                modifier = Modifier.weight(1f)
            )
            FilledIconButton(
                onClick = { vm.ask() },
                enabled = can,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = LawAccent, contentColor = Color.White),
                modifier = Modifier.size(52.dp)
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
        }
    }
}

@Composable
internal fun LawChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = CivicColors.Surface,
            selectedContainerColor = LawAccent,
            selectedLabelColor = Color.White,
            labelColor = CivicColors.Navy
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = CivicColors.Outline, selectedBorderColor = LawAccent)
    )
}

@Composable
internal fun lawFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CivicColors.Surface,
    unfocusedContainerColor = CivicColors.Surface,
    disabledContainerColor = CivicColors.SurfaceVariant,
    focusedBorderColor = LawAccent,
    unfocusedBorderColor = CivicColors.Outline,
    focusedLabelColor = LawAccent
)
