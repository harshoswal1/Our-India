package com.ourindia.ailawyer.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ourindia.ailawyer.data.DocumentType
import com.ourindia.ailawyer.ui.AiLawyerUiState
import com.ourindia.ailawyer.ui.theme.LegalColors

@Composable
fun DraftingScreen(
    state: AiLawyerUiState,
    onDocTypeSelected: (DocumentType) -> Unit,
    onFieldUpdated: (senderName: String?, senderAddress: String?, recipientName: String?, recipientAddress: String?, incidentDetails: String?, claimAmount: String?) -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(LegalColors.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "📜 Legal Document Drafting Studio",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = LegalColors.Navy
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Generate authoritative, court-ready legal notices, RTI applications, and police complaints formatted according to Indian legal procedure.",
                style = MaterialTheme.typography.bodySmall,
                color = LegalColors.TextSecondary,
                lineHeight = 18.sp
            )
        }

        // Document Type Selector Chips
        item {
            Text(
                text = "Select Document Category:",
                fontWeight = FontWeight.Bold,
                color = LegalColors.Navy,
                fontSize = 13.5.sp
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DocumentType.values()) { docType ->
                    val isSelected = state.selectedDocType == docType
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) LegalColors.SaffronContainer else LegalColors.Surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) LegalColors.SaffronDeep else LegalColors.Outline
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onDocTypeSelected(docType) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(docType.icon, fontSize = 16.sp)
                            Text(
                                text = docType.title,
                                color = if (isSelected) LegalColors.SaffronDeep else LegalColors.Navy,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Selected Document Description
        item {
            Surface(
                color = LegalColors.SurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, LegalColors.Outline)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = LegalColors.Surface,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(state.selectedDocType.icon, fontSize = 22.sp)
                        }
                    }
                    Column {
                        Text(state.selectedDocType.title, fontWeight = FontWeight.Bold, color = LegalColors.Navy, fontSize = 14.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(state.selectedDocType.description, color = LegalColors.TextSecondary, fontSize = 11.5.sp, lineHeight = 16.sp)
                    }
                }
            }
        }

        // Form Fields Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = LegalColors.Surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, LegalColors.Outline),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Parties & Dispute Particulars", fontWeight = FontWeight.Bold, color = LegalColors.Navy, fontSize = 14.5.sp)

                    // Sender
                    OutlinedTextField(
                        value = state.senderName,
                        onValueChange = { onFieldUpdated(it, null, null, null, null, null) },
                        label = { Text("Your Name (Sender / Complainant)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = outlinedColors()
                    )

                    OutlinedTextField(
                        value = state.senderAddress,
                        onValueChange = { onFieldUpdated(null, it, null, null, null, null) },
                        label = { Text("Your Address & City") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = outlinedColors()
                    )

                    // Recipient
                    OutlinedTextField(
                        value = state.recipientName,
                        onValueChange = { onFieldUpdated(null, null, it, null, null, null) },
                        label = { Text("Opposite Party / Station House Officer / Company Name") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = outlinedColors()
                    )

                    OutlinedTextField(
                        value = state.recipientAddress,
                        onValueChange = { onFieldUpdated(null, null, null, it, null, null) },
                        label = { Text("Opposite Party Address") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = outlinedColors()
                    )

                    // Claim amount
                    OutlinedTextField(
                        value = state.claimAmount,
                        onValueChange = { onFieldUpdated(null, null, null, null, null, it) },
                        label = { Text("Dispute / Claim Amount in ₹ (optional)") },
                        placeholder = { Text("e.g. 1,50,000") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = outlinedColors()
                    )

                    // Incident Facts
                    OutlinedTextField(
                        value = state.incidentDetails,
                        onValueChange = { onFieldUpdated(null, null, null, null, it, null) },
                        label = { Text("Chronological Facts, Dates & Nature of Breach") },
                        placeholder = { Text("Describe the events, dates of transaction, non-payment or offence committed...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        colors = outlinedColors()
                    )

                    if (state.draftError != null) {
                        Text(state.draftError, color = LegalColors.Crimson, fontSize = 12.sp)
                    }

                    // Generate Button
                    Button(
                        onClick = onGenerate,
                        enabled = !state.isDrafting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LegalColors.Saffron,
                            contentColor = Color.White
                        )
                    ) {
                        if (state.isDrafting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Drafting Legal Document...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Draft Legal Document with AI", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        // Generated Draft Output
        if (state.generatedDraft != null) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = LegalColors.Surface),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, LegalColors.SaffronDeep),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚖️ Generated Legal Draft",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = LegalColors.Navy
                            )

                            Row {
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(state.generatedDraft))
                                    Toast.makeText(context, "Copied draft to clipboard", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = LegalColors.SaffronDeep)
                                }
                                IconButton(onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, state.generatedDraft)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Share Legal Draft")
                                    context.startActivity(shareIntent)
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = LegalColors.SaffronDeep)
                                }
                            }
                        }

                        HorizontalDivider(color = LegalColors.Outline, modifier = Modifier.padding(vertical = 8.dp))

                        Text(
                            text = state.generatedDraft,
                            color = LegalColors.OnSurface,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LegalColors.SaffronDeep,
    unfocusedBorderColor = LegalColors.Outline,
    focusedTextColor = LegalColors.Navy,
    unfocusedTextColor = LegalColors.Navy,
    focusedLabelColor = LegalColors.SaffronDeep,
    unfocusedLabelColor = LegalColors.TextSecondary,
    focusedContainerColor = LegalColors.Background,
    unfocusedContainerColor = LegalColors.Background
)
