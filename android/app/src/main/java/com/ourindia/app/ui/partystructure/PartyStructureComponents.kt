package com.ourindia.app.ui.partystructure

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ourindia.app.ui.theme.CivicColors


/**
 * 1. Step 1 Screen: Political Parties Selector Grid
 */
@Composable
fun PartySelectionGrid(
    parties: List<PartyMetadata>,
    onSelectParty: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CivicColors.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Welcome Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, CivicColors.Navy, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "🇮🇳", fontSize = 24.sp)
                    Text(
                        text = "Our India Political System",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = CivicColors.Navy
                    )
                }
                Text(
                    text = "Select a registered political party below to explore its national-to-ward hierarchy, geographic spread, and representation analytics.",
                    fontSize = 12.5.sp,
                    color = CivicColors.TextSecondary,
                    lineHeight = 17.sp
                )
            }
        }

        // Classification Switcher (National vs State/Regional)
        var selectedClassification by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(PartyClassification.NATIONAL)
        }
        var selectedStateFilter by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf<String?>(null)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(8.dp))
                .border(1.dp, CivicColors.Navy.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val isNational = selectedClassification == PartyClassification.NATIONAL
            Button(
                onClick = {
                    selectedClassification = PartyClassification.NATIONAL
                    selectedStateFilter = null
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isNational) CivicColors.Navy else Color.Transparent,
                    contentColor = if (isNational) Color.White else CivicColors.Navy
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Text(
                    text = "🇮🇳 National Parties (${PartyCatalog.nationalParties.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val isRegional = selectedClassification == PartyClassification.STATE_REGIONAL
            Button(
                onClick = {
                    selectedClassification = PartyClassification.STATE_REGIONAL
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRegional) CivicColors.Navy else Color.Transparent,
                    contentColor = if (isRegional) Color.White else CivicColors.Navy
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Text(
                    text = "🏛️ State & Regional (${PartyCatalog.regionalParties.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // State filter chips (when State/Regional selected)
        if (selectedClassification == PartyClassification.STATE_REGIONAL) {
            val regionalStates = androidx.compose.runtime.remember {
                listOf("All") + PartyCatalog.regionalParties.mapNotNull { it.primaryState }.distinct().sorted()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                regionalStates.forEach { stateName ->
                    val isSelected = (stateName == "All" && selectedStateFilter == null) || selectedStateFilter == stateName
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedStateFilter = if (stateName == "All") null else stateName
                        },
                        label = {
                            Text(
                                text = stateName,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CivicColors.Saffron,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = CivicColors.Navy
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) CivicColors.Saffron else CivicColors.Navy.copy(alpha = 0.2f),
                            borderWidth = 1.dp
                        )
                    )
                }
            }
        }

        val filteredParties = remember(selectedClassification, selectedStateFilter, parties) {
            when (selectedClassification) {
                PartyClassification.NATIONAL -> parties.filter { it.classification == PartyClassification.NATIONAL }
                PartyClassification.STATE_REGIONAL -> parties.filter {
                    it.classification == PartyClassification.STATE_REGIONAL &&
                        (selectedStateFilter == null || it.primaryState.equals(selectedStateFilter, ignoreCase = true))
                }
            }
        }

        val headerText = if (selectedClassification == PartyClassification.NATIONAL) {
            "Recognized National Parties"
        } else {
            "State & Regional Parties" + (if (selectedStateFilter != null) " • $selectedStateFilter" else " (All States)")
        }

        Text(
            text = headerText,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = CivicColors.Navy
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(
                items = filteredParties,
                key = { party -> party.shortName }
            ) { party ->
                PartyGridCard(party = party, onSelect = { onSelectParty(party.shortName) })
            }
        }
    }
}

@Composable
private fun PartyGridCard(
    party: PartyMetadata,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, CivicColors.Navy, RoundedCornerShape(10.dp))
            .clickable { onSelect() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(party.color.copy(alpha = 0.15f), CircleShape)
                        .border(1.5.dp, party.color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = party.symbolEmoji, fontSize = 22.sp)
                }

                Surface(
                    color = CivicColors.Navy,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = party.shortName,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = party.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = CivicColors.Navy,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Founded: ${party.foundedYear} • HQ: ${party.headquarters}",
                fontSize = 10.sp,
                color = CivicColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            HorizontalDivider(color = CivicColors.Navy.copy(alpha = 0.1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${party.nationalSeats} MPs",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CivicColors.Saffron
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Explore",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CivicColors.Navy
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = CivicColors.Navy,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * 2. Workspace Sub-Module Switching Tabs (Hierarchy, Map, Analytics)
 */
@Composable
fun WorkspaceSubModuleTabBar(
    activeSubModule: WorkspaceSubModule,
    onSubModuleSelected: (WorkspaceSubModule) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, CivicColors.Navy),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WorkspaceSubModule.entries.forEach { subModule ->
                val isActive = subModule == activeSubModule
                Surface(
                    onClick = { onSubModuleSelected(subModule) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isActive) CivicColors.Navy else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = subModule.iconEmoji, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = subModule.title,
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) Color.White else CivicColors.Navy
                        )
                    }
                }
            }
        }
    }
}

/**
 * 3. Glassy Floating Politician Popup (Sections 7 & 8 of Prompt)
 */
@Composable
fun PoliticianGlassyPopup(
    node: PartyTreeNode?,
    onDismiss: () -> Unit,
    onViewProfile: (PartyTreeNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val isNotFetched = node?.holderName?.contains("Not yet fetched", ignoreCase = true) == true ||
                       node?.holderName?.contains("Leader data not yet available", ignoreCase = true) == true

    AnimatedVisibility(
        visible = node != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        if (node != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(16.dp, RoundedCornerShape(14.dp))
                    .border(1.5.dp, CivicColors.Navy.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                // Glass-like translucent surface
                colors = CardDefaults.cardColors(
                    containerColor = if (isNotFetched) Color(0xFFF9F9F9).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.94f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header row: Level Badge & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = if (isNotFetched) CivicColors.Navy.copy(alpha = 0.35f) else node.level.badgeColor,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = node.level.name,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                color = CivicColors.Navy,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = node.partyName,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = CivicColors.Navy,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 4 Concise Information Points (Prompt Requirement)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (isNotFetched) CivicColors.Navy.copy(alpha = 0.08f) else node.level.badgeColor.copy(alpha = 0.15f),
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    if (isNotFetched) CivicColors.Navy.copy(alpha = 0.2f) else node.level.badgeColor,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = if (isNotFetched) "❓" else node.photoEmoji, fontSize = 24.sp)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            // Point 1: Politician Name
                            Text(
                                text = node.holderName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isNotFetched) CivicColors.TextSecondary else CivicColors.Navy,
                                fontStyle = if (isNotFetched) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
                            )
                            // Point 2: Current Designation
                            Text(
                                text = node.roleTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = CivicColors.Saffron,
                                fontWeight = FontWeight.SemiBold
                            )
                            // Point 3: Geographic Jurisdiction
                            Text(
                                text = "Jurisdiction: ${node.state ?: "All India"}${if (!node.district.isNullOrEmpty()) " • ${node.district}" else ""}",
                                fontSize = 11.sp,
                                color = CivicColors.TextSecondary
                            )
                            // Point 4: Verification Status
                            Text(
                                text = if (isNotFetched) "⚠ Pending Ingestion & Verification" else "✓ Verified Organizational Record",
                                fontSize = 10.5.sp,
                                color = if (isNotFetched) CivicColors.Saffron else CivicColors.Teal,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    HorizontalDivider(color = CivicColors.Navy.copy(alpha = 0.12f))

                    // Tap Action to Full Profile
                    Button(
                        onClick = { onViewProfile(node) },
                        enabled = !isNotFetched,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.5.dp,
                                color = if (isNotFetched) CivicColors.Navy.copy(alpha = 0.25f) else CivicColors.Navy,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CivicColors.Navy,
                            disabledContainerColor = CivicColors.Navy.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isNotFetched) CivicColors.TextSecondary else CivicColors.Saffron,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isNotFetched) "Profile Details Not Yet Available" else "View Full Profile & Political Journey",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isNotFetched) CivicColors.TextSecondary else Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4. Active Party Header in Workspace
 */
@Composable
fun PartyHeaderCard(
    party: PartyMetadata,
    totalLeadersCount: Int,
    isOffline: Boolean,
    onChangePartyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, CivicColors.Navy, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(party.color.copy(alpha = 0.15f), CircleShape)
                        .border(1.5.dp, party.color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = party.symbolEmoji, fontSize = 20.sp)
                }

                Column {
                    Text(
                        text = party.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CivicColors.Navy
                    )
                    Text(
                        text = "President: ${party.president} • $totalLeadersCount Units",
                        style = MaterialTheme.typography.bodySmall,
                        color = CivicColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            OutlinedButton(
                onClick = onChangePartyClick,
                modifier = Modifier.border(1.dp, CivicColors.Navy, RoundedCornerShape(6.dp)),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Switch",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CivicColors.Navy
                )
            }
        }
    }
}

/**
 * 5. Universal Search Bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search leader (e.g. Narendra Modi, Pune)...",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, CivicColors.Navy, RoundedCornerShape(8.dp)),
        placeholder = { Text(placeholder, fontSize = 13.sp) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = CivicColors.Navy
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = CivicColors.Navy
                    )
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = CivicColors.Navy,
            unfocusedBorderColor = CivicColors.Navy
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

/**
 * 6. Level Filter Chip Group
 */
@Composable
fun LevelFilterChipGroup(
    selectedLevel: HierarchyLevel?,
    onLevelSelected: (HierarchyLevel?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Horizontally scrollable so 16+ level chips don't overflow a single row
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = selectedLevel == null,
            onClick = { onLevelSelected(null) },
            label = { Text("All", fontSize = 11.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = CivicColors.Navy,
                selectedLabelColor = Color.White
            )
        )

        // Show only meaningful grouping levels (not every single sub-level) to keep filter UX clean
        val groupLevels = listOf(
            HierarchyLevel.NATIONAL,
            HierarchyLevel.NATIONAL_GS,
            HierarchyLevel.STATE,
            HierarchyLevel.STATE_GS,
            HierarchyLevel.DISTRICT,
            HierarchyLevel.CONSTITUENCY,
            HierarchyLevel.TALUKA_MANDAL,
            HierarchyLevel.WARD,
            HierarchyLevel.BOOTH
        )

        groupLevels.forEach { level ->
            FilterChip(
                selected = selectedLevel == level,
                onClick = { onLevelSelected(if (selectedLevel == level) null else level) },
                label = { Text(level.groupLabel, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = level.badgeColor,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}
