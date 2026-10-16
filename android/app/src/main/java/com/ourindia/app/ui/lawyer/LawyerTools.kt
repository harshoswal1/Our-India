@file:OptIn(ExperimentalLayoutApi::class)

package com.ourindia.app.ui.lawyer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ourindia.app.data.lawyer.CitizenRight
import com.ourindia.app.data.lawyer.DocumentType
import com.ourindia.app.data.lawyer.EmergencyContact
import com.ourindia.app.data.lawyer.IpcBnsItem
import com.ourindia.app.data.lawyer.LawyerClient
import com.ourindia.app.data.lawyer.LegalKnowledge
import com.ourindia.app.ui.partystructure.EmptyState
import com.ourindia.app.ui.theme.CivicColors

// ── Draft ────────────────────────────────────────────────────────────────────

@Composable
internal fun DraftTab(s: LawyerUiState, vm: LawyerViewModel) {
    val d = s.draft
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Draft a document", style = MaterialTheme.typography.headlineSmall, color = CivicColors.Navy)
            Text("Fill in the facts and get a first draft to review, print or send.", style = MaterialTheme.typography.bodyMedium, color = CivicColors.TextSecondary)
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DocumentType.entries.forEach { t -> LawChip(t.title, selected = d.type == t) { vm.updateDraft { it.copy(type = t) } } }
            }
            Text(d.type.description, style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LawField(d.senderName, { v -> vm.updateDraft { it.copy(senderName = v) } }, "Your name")
                LawField(d.senderAddress, { v -> vm.updateDraft { it.copy(senderAddress = v) } }, "Your address")
                LawField(d.recipientName, { v -> vm.updateDraft { it.copy(recipientName = v) } }, "Addressed to", "Person, company, SHO or PIO")
                LawField(d.recipientAddress, { v -> vm.updateDraft { it.copy(recipientAddress = v) } }, "Their address")
                LawField(d.amount, { v -> vm.updateDraft { it.copy(amount = v) } }, "Amount in ₹ (optional)", keyboard = KeyboardType.Number)
                LawField(d.facts, { v -> vm.updateDraft { it.copy(facts = v) } }, "What happened", "Dates, amounts, what was promised and what went wrong", minLines = 4)
            }
        }
        item {
            if (d.missing.isNotEmpty()) {
                Text("Add ${d.missing.joinToString(", ")} to draft", style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
            }
            s.draftError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CivicColors.Danger, modifier = Modifier.padding(bottom = 8.dp)) }
            Button(
                onClick = vm::generateDraft,
                enabled = LawyerClient.configured && d.missing.isEmpty() && !s.drafting,
                colors = ButtonDefaults.buttonColors(containerColor = LawAccent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                if (s.drafting) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Drafting…")
                } else Text(if (LawyerClient.configured) "Draft it" else "Drafting needs AI, not set up in this build")
            }
        }
        s.draftResult?.let { text ->
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = CivicColors.Surface, border = BorderStroke(1.dp, LawAccent)) {
                    Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Your draft", style = MaterialTheme.typography.titleMedium, color = CivicColors.Navy, modifier = Modifier.weight(1f))
                            IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy draft", tint = LawAccent) }
                            IconButton(onClick = {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share draft"))
                            }) { Icon(Icons.Outlined.Share, contentDescription = "Share draft", tint = LawAccent) }
                        }
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = CivicColors.OnSurface, modifier = Modifier.padding(end = 12.dp))
                        Text(
                            "Check every fact, name and section with an advocate before you send or file this.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CivicColors.SaffronDeep,
                            modifier = Modifier.padding(top = 12.dp, end = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LawField(value: String, onChange: (String) -> Unit, label: String, placeholder: String? = null, minLines: Int = 1, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        minLines = minLines,
        singleLine = minLines == 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(14.dp),
        colors = lawFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

// ── IPC → BNS ────────────────────────────────────────────────────────────────

@Composable
internal fun SectionsTab(s: LawyerUiState, vm: LawyerViewModel) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Old IPC to new BNS", style = MaterialTheme.typography.headlineSmall, color = CivicColors.Navy)
            Text("The Bharatiya Nyaya Sanhita replaced the Indian Penal Code on 1 July 2024.", style = MaterialTheme.typography.bodyMedium, color = CivicColors.TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedTextField(
                value = s.sectionQuery,
                onValueChange = vm::searchSections,
                placeholder = { Text("e.g. 420 or theft") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = CivicColors.TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = lawFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (s.sections.isEmpty()) item {
            EmptyState(Icons.Outlined.SearchOff, "Not in the quick list", "Ask the AI Lawyer about \"${s.sectionQuery}\" instead.",
                action = { OutlinedButton(onClick = { vm.ask("What is the BNS equivalent of IPC ${s.sectionQuery.trim()}, and what does it cover?") }) { Text("Ask", color = LawAccent) } })
        }
        items(s.sections, key = { it.ipcSection }) { item ->
            SectionCard(item) {
                vm.ask("Explain BNS section ${item.bnsSection} (old IPC ${item.ipcSection}), ${item.offence}: what it covers, the punishment, and what someone accused or affected should do.")
            }
        }
    }
}

@Composable
private fun SectionCard(item: IpcBnsItem, onAsk: () -> Unit) {
    val nonBailable = item.bailable.contains("non", ignoreCase = true)
    Surface(shape = RoundedCornerShape(20.dp), color = CivicColors.Surface, border = BorderStroke(1.dp, CivicColors.Outline)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag("IPC ${item.ipcSection}", CivicColors.SurfaceVariant, CivicColors.Navy)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "becomes", tint = CivicColors.TextMuted, modifier = Modifier.size(16.dp))
                Tag("BNS ${item.bnsSection}", LawContainer, LawAccent)
            }
            Text(item.offence, style = MaterialTheme.typography.titleMedium, color = CivicColors.Navy)
            Text(item.keyElements, style = MaterialTheme.typography.bodyMedium, color = CivicColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(item.bailable, if (nonBailable) CivicColors.DangerContainer else CivicColors.CivicContainer, if (nonBailable) CivicColors.Danger else CivicColors.CivicDeep)
                Tag(item.triableBy, CivicColors.SurfaceVariant, CivicColors.TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Gavel, contentDescription = null, tint = CivicColors.TextSecondary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Text(item.punishment, style = MaterialTheme.typography.bodyMedium, color = CivicColors.OnSurface)
            }
            if (LawyerClient.configured) TextButton(onClick = onAsk, contentPadding = PaddingValues(0.dp)) { Text("Ask about this", color = LawAccent) }
        }
    }
}

@Composable
private fun Tag(text: String, bg: Color, fg: Color) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = fg, modifier = Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
}

// ── Rights & helplines ───────────────────────────────────────────────────────

@Composable
internal fun RightsTab() {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header("Helplines", "Free, 24x7 unless noted. Tap to open the dialler.") }
        items(LegalKnowledge.emergencyContacts, key = { it.number }) { c ->
            Helpline(c) { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.number}"))) } }
        }
        item { Header("Know your rights", "What the law guarantees in situations people most often ask about.", top = 12.dp) }
        items(LegalKnowledge.citizenRights, key = { it.title }) { r -> RightCard(r) }
    }
}

@Composable
private fun Header(title: String, sub: String, top: androidx.compose.ui.unit.Dp = 0.dp) {
    Column(Modifier.padding(top = top)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = CivicColors.Navy)
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = CivicColors.TextSecondary)
    }
}

@Composable
private fun Helpline(c: EmergencyContact, onCall: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = CivicColors.Surface, border = BorderStroke(1.dp, CivicColors.Outline)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(c.title, style = MaterialTheme.typography.titleSmall, color = CivicColors.Navy)
                Text(c.description, style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary)
                Text(c.authority, style = MaterialTheme.typography.labelSmall, color = CivicColors.TextMuted, modifier = Modifier.padding(top = 2.dp))
            }
            FilledTonalButton(
                onClick = onCall,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = LawContainer, contentColor = LawAccent),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(c.number, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun RightCard(r: CitizenRight) {
    Surface(shape = RoundedCornerShape(20.dp), color = CivicColors.Surface, border = BorderStroke(1.dp, CivicColors.Outline)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(r.category.uppercase(), style = MaterialTheme.typography.labelSmall, color = LawAccent)
            Text(r.title, style = MaterialTheme.typography.titleMedium, color = CivicColors.Navy)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Gavel, contentDescription = null, tint = CivicColors.TextSecondary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Text(r.legalProvision, style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary)
            }
            Text(r.summary, style = MaterialTheme.typography.bodyMedium, color = CivicColors.OnSurface)
            Row(
                Modifier.fillMaxWidth().background(LawContainer.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = LawAccent, modifier = Modifier.size(18.dp))
                Text(r.practicalTip, style = MaterialTheme.typography.bodySmall, color = CivicColors.Navy)
            }
        }
    }
}
