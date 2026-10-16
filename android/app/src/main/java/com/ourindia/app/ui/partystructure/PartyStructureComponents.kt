@file:OptIn(ExperimentalMaterial3Api::class)

package com.ourindia.app.ui.partystructure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ourindia.app.ui.theme.CivicColors

// ═══════════════════════════════════════════════════════════════════
// Party selector
// ═══════════════════════════════════════════════════════════════════

@Composable
fun PartySelectionGrid(
    parties: List<PartyMetadata>,
    partiesWithData: Set<String>,
    onSelectParty: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    var classification by rememberSaveable { mutableStateOf(PartyClassification.NATIONAL) }
    var stateFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val regionalStates = remember(parties) {
        parties.filter { it.classification == PartyClassification.STATE_REGIONAL }
            .mapNotNull { it.primaryState }.distinct().sorted()
    }
    val visible = remember(classification, stateFilter, parties) {
        parties.filter {
            it.classification == classification &&
                (classification == PartyClassification.NATIONAL || stateFilter == null ||
                    it.primaryState.equals(stateFilter, ignoreCase = true))
        }
    }
    val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = fullSpan) {
            SelectorHero(totalParties = parties.size, mappedParties = parties.count { it.shortName in partiesWithData })
        }

        item(span = fullSpan) {
            SegmentedToggle(
                options = listOf(
                    "National (${parties.count { it.classification == PartyClassification.NATIONAL }})",
                    "State & regional (${parties.count { it.classification == PartyClassification.STATE_REGIONAL }})"
                ),
                selectedIndex = if (classification == PartyClassification.NATIONAL) 0 else 1,
                onSelect = {
                    classification = if (it == 0) PartyClassification.NATIONAL else PartyClassification.STATE_REGIONAL
                    stateFilter = null
                }
            )
        }

        if (classification == PartyClassification.STATE_REGIONAL) {
            item(span = fullSpan) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (listOf<String?>(null) + regionalStates).forEach { state ->
                        FilterChip(
                            selected = stateFilter == state,
                            onClick = { stateFilter = state },
                            label = { Text(state ?: "All states") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = CivicColors.Surface,
                                selectedContainerColor = CivicColors.Navy,
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = stateFilter == state,
                                borderColor = CivicColors.Outline,
                                selectedBorderColor = CivicColors.Navy
                            )
                        )
                    }
                }
            }
        }

        items(visible, key = { it.shortName }) { party ->
            PartyGridCard(
                party = party,
                isMapped = party.shortName in partiesWithData,
                onSelect = { onSelectParty(party.shortName) }
            )
        }
    }
}

@Composable
private fun SelectorHero(totalParties: Int, mappedParties: Int) {
    Column(
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TricolorRule()
        Text(
            text = "How India's parties are organised",
            style = MaterialTheme.typography.headlineMedium,
            color = CivicColors.Navy
        )
        Text(
            text = "Pick a party to walk its chain of command from national leadership down to the booth, see where it is organised, and who holds each post.",
            style = MaterialTheme.typography.bodyMedium,
            color = CivicColors.TextSecondary
        )
        Text(
            text = "$totalParties parties · $mappedParties with mapped structure",
            style = MaterialTheme.typography.labelMedium,
            color = CivicColors.SaffronDeep
        )
    }
}

/** Saffron / white / green rule — the one flag reference, used as a section mark. */
@Composable
fun TricolorRule(modifier: Modifier = Modifier) {
    Row(modifier.height(4.dp).width(48.dp).clip(RoundedCornerShape(2.dp))) {
        Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFFFF9933)))
        Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFFE9E4DA)))
        Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF138808)))
    }
}

@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CivicColors.SurfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Surface(
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(9.dp),
                    color = if (selected) CivicColors.Surface else Color.Transparent,
                    shadowElevation = if (selected) 1.dp else 0.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) CivicColors.Navy else CivicColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PartyGridCard(
    party: PartyMetadata,
    isMapped: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        color = CivicColors.Surface,
        border = BorderStroke(1.dp, CivicColors.Outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(party.color)
            )
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    PartySymbol(party = party, size = 44.dp)
                    Text(
                        text = party.shortName,
                        style = MaterialTheme.typography.labelMedium,
                        color = CivicColors.Navy,
                        modifier = Modifier
                            .background(CivicColors.SurfaceVariant, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = party.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = CivicColors.Navy,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Est. ${party.foundedYear} · ${party.headquarters.substringBefore(',')}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CivicColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider(color = CivicColors.Outline)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${party.nationalSeats} Lok Sabha MPs",
                        style = MaterialTheme.typography.labelMedium,
                        color = CivicColors.Navy
                    )
                    if (isMapped) {
                        Icon(
                            imageVector = Icons.Outlined.AccountTree,
                            contentDescription = "Structure mapped",
                            tint = CivicColors.TealDeep,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = "Structure not mapped yet",
                            tint = CivicColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Shared building blocks
// ═══════════════════════════════════════════════════════════════════

/** Party's electoral symbol on a tinted tile. */
@Composable
fun PartySymbol(party: PartyMetadata, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(party.color.copy(alpha = 0.14f), RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = party.symbolEmoji, fontSize = (size.value * 0.5f).sp)
    }
}

fun initialsOf(name: String): String {
    val words = name.split(' ', '.').filter { it.isNotBlank() && it.first().isLetter() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words.first().first()}${words.last().first()}".uppercase()
    }
}

/** Initials on a tinted disc; a photo, when there is one, is layered over it so a failed load falls back cleanly. */
@Composable
fun PersonAvatar(
    node: PartyTreeNode,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = node.level.badgeColor
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (node.isNotFetched) CivicColors.SurfaceVariant else tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        if (node.isNotFetched) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = CivicColors.TextMuted,
                modifier = Modifier.size(size * 0.55f)
            )
        } else {
            Text(
                text = initialsOf(node.holderName),
                color = tint,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp
            )
            if (node.photoUrl != null) {
                AsyncImage(
                    model = node.photoUrl,
                    contentDescription = node.holderName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
    }
}

@Composable
fun LevelBadge(level: HierarchyLevel, modifier: Modifier = Modifier) {
    Text(
        text = level.groupLabel.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = level.badgeColor,
        maxLines = 1,
        modifier = modifier
            .background(level.badgeColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

/** One position as a list row: avatar, holder, role, jurisdiction. */
@Composable
fun PositionRow(
    node: PartyTreeNode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showJurisdiction: Boolean = true,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PersonAvatar(node = node, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = node.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = if (node.isNotFetched) CivicColors.TextMuted else CivicColors.Navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = node.roleTitle,
                style = MaterialTheme.typography.bodySmall,
                color = CivicColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showJurisdiction) {
                Text(
                    text = node.jurisdiction,
                    style = MaterialTheme.typography.labelSmall,
                    color = CivicColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) trailing() else LevelBadge(node.level)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(CivicColors.SurfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = CivicColors.TextSecondary, modifier = Modifier.size(28.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = CivicColors.Navy, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = CivicColors.TextSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

/** White card with a titled header; the base for Insights and Profile sections. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CivicColors.Surface,
        border = BorderStroke(1.dp, CivicColors.Outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = CivicColors.Navy, modifier = Modifier.size(20.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, color = CivicColors.Navy, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Hierarchy search
// ═══════════════════════════════════════════════════════════════════

@Composable
fun HierarchySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<PartyTreeNode>,
    onResultChosen: (PartyTreeNode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search a leader, post or place", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CivicColors.TextSecondary) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", tint = CivicColors.TextSecondary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CivicColors.Surface,
                unfocusedContainerColor = CivicColors.Surface,
                focusedBorderColor = CivicColors.Navy,
                unfocusedBorderColor = CivicColors.Outline
            )
        )
        if (query.trim().length >= 2) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CivicColors.Surface,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, CivicColors.Outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                if (results.isEmpty()) {
                    Text(
                        text = "No positions match \"${query.trim()}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CivicColors.TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 340.dp), contentPadding = PaddingValues(4.dp)) {
                        items(results, key = { it.id }) { node ->
                            PositionRow(node = node, onClick = { onResultChosen(node) })
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Position detail sheet
// ═══════════════════════════════════════════════════════════════════

@Composable
fun PositionDetailSheet(
    node: PartyTreeNode,
    onDismiss: () -> Unit,
    onViewProfile: (PartyTreeNode) -> Unit,
    onLocateInHierarchy: ((String) -> Unit)?
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CivicColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PersonAvatar(node = node, size = 60.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LevelBadge(node.level)
                        Text(
                            text = node.partyName,
                            style = MaterialTheme.typography.labelSmall,
                            color = CivicColors.Navy,
                            modifier = Modifier
                                .background(CivicColors.SurfaceVariant, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = node.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (node.isNotFetched) CivicColors.TextSecondary else CivicColors.Navy
                    )
                    Text(node.roleTitle, style = MaterialTheme.typography.bodyMedium, color = CivicColors.SaffronDeep)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine(Icons.Outlined.Place, node.jurisdiction)
                val source = node.source
                when {
                    node.isNotFetched -> DetailLine(
                        Icons.Outlined.Schedule,
                        "This post exists in the party's organisation; its current holder isn't confirmed yet."
                    )
                    source != null -> {
                        if (node.verificationStatus is VerificationStatus.PendingVerification) DetailLine(
                            Icons.Outlined.Schedule,
                            "Wikipedia changed this post on ${source.date}. Shown as reported there; not yet checked against a news source.",
                            tint = CivicColors.SaffronDeep
                        )
                        SourceLine(source)
                    }
                    else -> DetailLine(
                        Icons.Outlined.Schedule,
                        "Named in synced records but not yet checked against a published source.",
                        tint = CivicColors.SaffronDeep
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onLocateInHierarchy != null) {
                    OutlinedButton(
                        onClick = { onLocateInHierarchy(node.id) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CivicColors.Outline)
                    ) {
                        Icon(Icons.Outlined.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("In structure")
                    }
                }
                Button(
                    onClick = { onViewProfile(node) },
                    enabled = !node.isNotFetched,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CivicColors.Navy)
                ) {
                    Text(if (node.isNotFetched) "No profile yet" else "Full profile")
                    if (!node.isNotFetched) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(icon: ImageVector, text: String, tint: Color = CivicColors.TextSecondary) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = CivicColors.OnSurface)
    }
}

/** Citation row: who reported it and when; tapping opens the article. */
@Composable
fun SourceLine(source: VerifiedSource, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CivicColors.Background)
            .clickable(enabled = source.referenceUrl != null) {
                source.referenceUrl?.let { runCatching { uriHandler.openUri(it) } }
            }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Verified, contentDescription = null, tint = CivicColors.TealDeep, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(source.title, style = MaterialTheme.typography.titleSmall, color = CivicColors.Navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${source.authority} · ${source.date}",
                style = MaterialTheme.typography.bodySmall,
                color = CivicColors.TextSecondary
            )
        }
        if (source.referenceUrl != null) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open source", tint = CivicColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}
