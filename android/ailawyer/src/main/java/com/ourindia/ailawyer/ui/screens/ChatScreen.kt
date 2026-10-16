package com.ourindia.ailawyer.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ourindia.ailawyer.data.ChatMessage
import com.ourindia.ailawyer.ui.AiLawyerUiState
import com.ourindia.ailawyer.ui.theme.LegalColors

@Composable
fun ChatScreen(
    state: AiLawyerUiState,
    onInputChange: (String) -> Unit,
    onSendMessage: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val promptSuggestions = listOf(
        "Cheque bounce procedure under Sec 138",
        "Can police search my phone without warrant?",
        "How to file Zero FIR in another city?",
        "Tenant rights against sudden eviction",
        "What is Sec 420 (Cheating) in new BNS?",
        "Online complaint for defective product"
    )

    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.chatMessages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LegalColors.Background)
    ) {
        // Disclaimer Banner
        Surface(
            color = LegalColors.SurfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, LegalColors.Outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Gavel, contentDescription = null, tint = LegalColors.SaffronDeep, modifier = Modifier.size(16.dp))
                Text(
                    text = "Legal advice based on Indian Statutes (BNS 2023, BNSS, BSA). For official court filing, verify with an advocate.",
                    style = MaterialTheme.typography.labelSmall,
                    color = LegalColors.TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }
        }

        // Chat Message List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(state.chatMessages) { message ->
                MessageBubble(
                    message = message,
                    onCopy = {
                        clipboard.setText(AnnotatedString(cleanLegalText(message.content)))
                        Toast.makeText(context, "Copied legal response", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (state.isChatLoading) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = LegalColors.Surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, LegalColors.Outline),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = LegalColors.SaffronDeep,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Analyzing Indian Statutes & formulating legal counsel...",
                                style = MaterialTheme.typography.bodySmall,
                                color = LegalColors.TextSecondary,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }
            }

            if (state.chatError != null) {
                item {
                    Surface(
                        color = LegalColors.CrimsonContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LegalColors.Crimson.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = state.chatError,
                            color = LegalColors.Crimson,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Suggestions chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(promptSuggestions) { prompt ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = LegalColors.Surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, LegalColors.Outline),
                    modifier = Modifier.clip(RoundedCornerShape(18.dp))
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { onSendMessage(prompt) },
                        label = { Text(prompt, fontSize = 11.5.sp, color = LegalColors.Navy, fontWeight = FontWeight.Medium) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = LegalColors.Surface
                        ),
                        border = null
                    )
                }
            }
        }

        // Input Field
        Surface(
            color = LegalColors.Surface,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, LegalColors.Outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = state.chatInput,
                    onValueChange = onInputChange,
                    placeholder = {
                        Text(
                            "Ask legal query (e.g. cheque bounce, police FIR, bail...)",
                            fontSize = 13.sp,
                            color = LegalColors.TextMuted
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LegalColors.SaffronDeep,
                        unfocusedBorderColor = LegalColors.Outline,
                        focusedTextColor = LegalColors.Navy,
                        unfocusedTextColor = LegalColors.Navy,
                        focusedContainerColor = LegalColors.Background,
                        unfocusedContainerColor = LegalColors.Background
                    ),
                    maxLines = 4
                )

                IconButton(
                    onClick = { onSendMessage(null) },
                    enabled = state.chatInput.isNotBlank() && !state.isChatLoading,
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (state.chatInput.isNotBlank()) LegalColors.SaffronDeep else LegalColors.SurfaceVariant,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (state.chatInput.isNotBlank()) Color.White else LegalColors.TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, onCopy: () -> Unit) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                shape = CircleShape,
                color = LegalColors.SaffronContainer,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("⚖️", fontSize = 17.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) LegalColors.Navy else LegalColors.Surface
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isUser) LegalColors.Navy else LegalColors.Outline
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 2.dp else 1.5.dp),
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI Lawyer India (न्याय साथी)",
                            fontWeight = FontWeight.Bold,
                            color = LegalColors.SaffronDeep,
                            fontSize = 12.sp
                        )
                        IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = LegalColors.TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = LegalColors.Outline.copy(alpha = 0.6f))
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    text = if (isUser) AnnotatedString(message.content) else formatLegalResponse(message.content),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) Color.White else LegalColors.OnSurface,
                    lineHeight = 22.sp,
                    fontSize = 13.5.sp
                )
            }
        }
    }
}

/**
 * Strips raw markdown symbols like hashtags, asterisks, and hyphens
 * to produce clean, citizen-friendly plain text for copy-paste or sharing.
 */
private fun cleanLegalText(raw: String): String {
    return raw.lines().joinToString("\n") { line ->
        var trimmed = line.trimStart()
        // Strip markdown heading hashes (#, ##, ###)
        trimmed = trimmed.replace(Regex("^#{1,6}\\s*"), "")
        // Replace bullet hyphens or asterisks (- or *) with clean 🔹 emoji
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            "🔹 " + trimmed.substring(2).trim()
        } else {
            line
        }
    }.replace("**", "").replace("*", "")
}

/**
 * Parses text into an AnnotatedString that styles **bold text** as bold
 * and eliminates distracting AI markdown symbols (*, -, #) for a clean UI presentation.
 */
private fun formatLegalResponse(raw: String): AnnotatedString {
    val preprocessed = raw.lines().joinToString("\n") { line ->
        var trimmed = line.trimStart()
        // Strip markdown heading hashes (#, ##, ###)
        trimmed = trimmed.replace(Regex("^#{1,6}\\s*"), "")
        // Replace raw bullets (- or * or +) with clean blue bullet emoji
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            "🔹 " + trimmed.substring(2).trim()
        } else {
            line
        }
    }

    return buildAnnotatedString {
        val pattern = Regex("\\*\\*(.*?)\\*\\*")
        var lastIndex = 0
        val matches = pattern.findAll(preprocessed)

        for (match in matches) {
            val range = match.range
            // Append preceding text and strip stray single asterisks
            val before = preprocessed.substring(lastIndex, range.first).replace("*", "")
            append(before)

            // Append bold span without asterisks
            val boldContent = match.groupValues[1].replace("*", "")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = LegalColors.Navy)) {
                append(boldContent)
            }
            lastIndex = range.last + 1
        }

        if (lastIndex < preprocessed.length) {
            val remaining = preprocessed.substring(lastIndex).replace("*", "")
            append(remaining)
        }
    }
}
