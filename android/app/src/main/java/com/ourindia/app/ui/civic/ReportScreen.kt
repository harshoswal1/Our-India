@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ourindia.app.ui.civic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ourindia.app.data.civic.CivicConfig
import com.ourindia.app.data.civic.Complaint
import com.ourindia.app.data.geo.LocalGeographicResolver
import com.ourindia.app.ui.common.rememberLocateMe
import com.ourindia.app.ui.theme.CivicColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReportScreen(
    resolver: LocalGeographicResolver,
    onClose: () -> Unit,
    onOpenIssue: (String) -> Unit,
    onNeedsSignIn: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }

    val takePhoto = rememberPhotoCapture(viewModel::onPhoto)
    val locate = rememberLocateMe(
        resolver = resolver,
        onStart = { viewModel.setLocating(true) },
        onResolved = viewModel::onLocation,
        onFailed = viewModel::onLocationFailed
    )
    // A stale shared fix is fine for browsing but not for evidence: refresh it once on open.
    LaunchedEffect(Unit) { locate() }

    (state.stage as? ReportStage.Sent)?.let { sent ->
        SentView(sent.complaint, onDone = { viewModel.reset(); onClose() }, onOpen = { viewModel.reset(); onOpenIssue(sent.complaint.id) })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = CivicColors.Navy) } },
                title = { Text("Report an issue", style = MaterialTheme.typography.titleLarge, color = CivicColors.Navy) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CivicColors.Background)
            )
        },
        bottomBar = {
            Surface(color = CivicColors.Surface, shadowElevation = 8.dp) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                    val busy = state.stage is ReportStage.Checking || state.stage is ReportStage.Sending
                    if (state.missing.isNotEmpty()) {
                        Text("Add ${state.missing.joinToString(", ")} to send", style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Button(
                        onClick = { if (viewModel.signedIn) viewModel.submit() else onNeedsSignIn() },
                        enabled = !busy && state.missing.isEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = CivicColors.Civic),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(if (state.stage is ReportStage.Checking) "Checking for the same report nearby…" else "Sending…")
                        } else {
                            Text(
                                when {
                                    !viewModel.signedIn -> "Sign in to send"
                                    state.target != null -> "Send to ${state.target!!.code.ifBlank { state.target!!.name }}"
                                    else -> "Send report"
                                }
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = CivicColors.Background
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Step(1, "Photo", "Take it now, on the spot. Gallery photos aren't accepted as evidence.") {
                PhotoSlot(state.photo, takenAt = state.photoTakenAt, encoding = state.encodingPhoto, onTake = takePhoto)
            }
            Step(2, "Place", null) {
                PlaceCard(state, onLocate = locate, onToggleTest = viewModel::setSendAsTest, onChoose = viewModel::chooseAuthority)
            }
            Step(3, "What's wrong", null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IssueCategory.entries.forEach { c ->
                        CivicChip(c.label, selected = state.category == c, icon = c.icon) { viewModel.setCategory(c) }
                    }
                }
                state.category?.subtypes?.takeIf { it.isNotEmpty() }?.let { subs ->
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        subs.forEach { sub ->
                            CivicChip(sub, selected = state.subtype == sub) { viewModel.setSubtype(if (state.subtype == sub) "" else sub) }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                CivicField(state.title, viewModel::setTitle, "Title", "e.g. Deep pothole outside the bus stop")
                Spacer(Modifier.height(12.dp))
                CivicField(state.description, viewModel::setDescription, "What's happening", "How big is it, since when, who's affected", minLines = 3)
                Spacer(Modifier.height(12.dp))
                CivicField(state.landmark, viewModel::setLandmark, "Nearby landmark (optional)", "e.g. Opposite the post office")
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    (state.stage as? ReportStage.Duplicate)?.let { d ->
        AlertDialog(
            onDismissRequest = viewModel::backToEditing,
            icon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = CivicColors.Civic) },
            title = { Text("Is this the same problem?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Someone reported this ${d.candidate.distanceMeters.toInt()} m away. One problem should have one report — supporting it raises its priority without a duplicate email.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        Modifier.fillMaxWidth().background(CivicColors.Background, RoundedCornerShape(14.dp)).padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IssueImage(d.candidate.photoUrl, IssueCategory.of(d.candidate.category), Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(d.candidate.title, style = MaterialTheme.typography.titleSmall, color = CivicColors.Navy, maxLines = 2)
                            Text("${d.candidate.supportCount} supporters · ${d.candidate.locality}", style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.supportDuplicate(d.candidate, onOpenIssue) }) { Text("Same — support it", color = CivicColors.CivicDeep) }
            },
            dismissButton = { TextButton(onClick = viewModel::sendAnyway) { Text("Mine is different") } }
        )
    }
}

@Composable
private fun Step(n: Int, title: String, hint: String?, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(26.dp).background(CivicColors.Navy, CircleShape), contentAlignment = Alignment.Center) {
                Text("$n", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, color = CivicColors.Navy)
        }
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary, modifier = Modifier.padding(start = 36.dp, top = 2.dp))
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun PhotoSlot(photo: String?, takenAt: String?, encoding: Boolean, onTake: () -> Unit) {
    if (photo == null) {
        Surface(
            onClick = onTake,
            enabled = !encoding,
            shape = RoundedCornerShape(20.dp),
            color = CivicColors.Surface,
            border = BorderStroke(1.5.dp, CivicColors.Outline),
            modifier = Modifier.fillMaxWidth().height(180.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (encoding) CircularProgressIndicator(color = CivicColors.Civic)
                else {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = CivicColors.CivicDeep, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Open camera", style = MaterialTheme.typography.titleSmall, color = CivicColors.CivicDeep)
                }
            }
        }
    } else {
        Box {
            IssueImage(photo, IssueCategory.OTHER, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(20.dp)))
            // The capture time travels with the report (camera_capture_timestamp); show what's sent.
            takenAt?.let { capturedLabel(it) }?.let { label ->
                Row(
                    Modifier.align(Alignment.TopStart).padding(12.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            FilledTonalButton(
                onClick = onTake,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.Black.copy(alpha = 0.6f), contentColor = Color.White),
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
            ) {
                Icon(Icons.Outlined.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Retake")
            }
        }
    }
}

@Composable
private fun PlaceCard(state: ReportUiState, onLocate: () -> Unit, onToggleTest: (Boolean) -> Unit, onChoose: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = CivicColors.Surface, border = BorderStroke(1.dp, CivicColors.Outline)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.MyLocation, contentDescription = null, tint = CivicColors.CivicDeep)
                Column(Modifier.weight(1f)) {
                    val loc = state.location
                    Text(
                        when {
                            state.locating -> "Finding you…"
                            loc != null -> listOfNotNull(state.place, loc.district).distinct().joinToString(", ").ifBlank { loc.label }
                            else -> "Location needed"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = CivicColors.Navy
                    )
                    if (loc?.latitude != null && loc.longitude != null) {
                        Text(
                            "%.5f, %.5f".format(loc.latitude, loc.longitude) + (loc.accuracyMeters?.let { "  ·  ±${it.toInt()} m" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = CivicColors.TextSecondary
                        )
                    }
                }
                TextButton(onClick = onLocate, enabled = !state.locating) { Text(if (state.location == null) "Locate" else "Refresh", color = CivicColors.CivicDeep) }
            }

            when (val c = state.coverage) {
                is Coverage.Covered -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { CoverageLine(Icons.Outlined.Verified, "Goes to ${c.authority.name}", CivicColors.CivicDeep) }
                    if (c.others.isNotEmpty()) {
                        var open by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { open = true }) { Text("Change", color = CivicColors.CivicDeep) }
                            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                c.others.forEach { (a, km) ->
                                    DropdownMenuItem(
                                        text = { Text("${a.name}  ·  ${km.toInt()} km") },
                                        onClick = { open = false; onChoose(a.key) }
                                    )
                                }
                            }
                        }
                    }
                }
                is Coverage.Outside -> {
                    HorizontalDivider(color = CivicColors.Outline)
                    Text(
                        "NagrikVoice isn't live here yet." + (c.nearest?.let { n ->
                            " It currently routes to ${n.name}" + (c.distanceKm?.let { " (${it.toInt()} km away)" } ?: "") + "."
                        } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CivicColors.OnSurface
                    )
                    if (c.nearest != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Send as a test report", style = MaterialTheme.typography.titleSmall, color = CivicColors.Navy)
                                Text("Marked [Test] so officials can tell it apart", style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary)
                            }
                            Switch(
                                checked = state.sendAsTest,
                                onCheckedChange = onToggleTest,
                                colors = SwitchDefaults.colors(checkedTrackColor = CivicColors.Civic)
                            )
                        }
                    }
                }
                Coverage.Unknown -> if (state.location != null) CoverageLine(Icons.Outlined.Schedule, "Checking which corporation covers this spot…", CivicColors.TextSecondary)
            }
        }
    }
}

private fun capturedLabel(iso: String): String? = runCatching {
    "Taken " + DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(iso))
}.getOrNull()

@Composable
private fun CoverageLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

@Composable
fun CivicField(value: String, onChange: (String) -> Unit, label: String, placeholder: String, minLines: Int = 1) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        minLines = minLines,
        singleLine = minLines == 1,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CivicColors.Surface,
            unfocusedContainerColor = CivicColors.Surface,
            focusedBorderColor = CivicColors.Civic,
            unfocusedBorderColor = CivicColors.Outline,
            focusedLabelColor = CivicColors.CivicDeep
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SentView(complaint: Complaint, onDone: () -> Unit, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(CivicColors.Background)
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(72.dp).background(CivicColors.CivicContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = CivicColors.Civic, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("It's on the record", style = MaterialTheme.typography.headlineMedium, color = CivicColors.Navy, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Reference ${complaint.id}. Anyone can now see and support it; it stays open until a fix is verified.",
            style = MaterialTheme.typography.bodyLarge,
            color = CivicColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        // A receipt: what was sent, where, to whom.
        Row(
            Modifier.fillMaxWidth().background(CivicColors.Surface, RoundedCornerShape(20.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IssueImage(complaint.photoUrl, IssueCategory.of(complaint.category), Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f)) {
                Text(complaint.title, style = MaterialTheme.typography.titleSmall, color = CivicColors.Navy, maxLines = 2)
                Text(complaint.locality, style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary, maxLines = 1)
                Text("To ${complaint.authorityName ?: complaint.administrativeArea}", style = MaterialTheme.typography.bodySmall, color = CivicColors.CivicDeep, maxLines = 2)
            }
        }
        Spacer(Modifier.height(20.dp))
        if (CivicConfig.SIMULATED_DELIVERY) DemoDeliveryNote()
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = CivicColors.Civic), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("View report")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDone, border = BorderStroke(1.dp, CivicColors.Outline), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("Done", color = CivicColors.Navy)
        }
    }
}
