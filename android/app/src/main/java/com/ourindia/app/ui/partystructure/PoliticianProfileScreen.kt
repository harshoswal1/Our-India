package com.ourindia.app.ui.partystructure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.ourindia.app.ui.theme.CivicColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoliticianProfileScreen(
    profile: PoliticianProfile,
    onBack: () -> Unit,
    onLocateInHierarchy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val party = PartyCatalog.getParty(profile.party)
    val asNode = PartyTreeNode(
        id = profile.id,
        partyName = profile.party,
        level = profile.level,
        parentId = null,
        roleTitle = profile.roleTitle,
        holderName = profile.name,
        state = profile.state,
        district = profile.district,
        photoUrl = profile.photoUrl,
        verificationStatus = profile.verificationStatus
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", style = MaterialTheme.typography.titleMedium, color = CivicColors.Navy) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CivicColors.Navy)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CivicColors.Background)
            )
        },
        containerColor = CivicColors.Background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = CivicColors.Surface) {
                    Column {
                        // Avatar straddles the party-colour band.
                        Box {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(party.color.copy(alpha = 0.85f), party.color.copy(alpha = 0.35f))
                                        )
                                    )
                            )
                            PersonAvatar(
                                node = asNode,
                                size = 84.dp,
                                modifier = Modifier
                                    .padding(start = 20.dp, top = 30.dp)
                                    .background(CivicColors.Surface, CircleShape)
                                    .padding(4.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    LevelBadge(profile.level)
                                    Text(
                                        text = "${party.symbolEmoji} ${party.shortName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CivicColors.Navy,
                                        modifier = Modifier
                                            .background(CivicColors.SurfaceVariant, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                Text(profile.name, style = MaterialTheme.typography.headlineSmall, color = CivicColors.Navy)
                                Text(profile.roleTitle, style = MaterialTheme.typography.titleSmall, color = CivicColors.SaffronDeep)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FactCell("Jurisdiction", profile.state ?: "All India", Modifier.weight(1f))
                                    FactCell("District", profile.district ?: "—", Modifier.weight(1f))
                                }
                                FactCell("Education", profile.education)
                            }
                            Button(
                                onClick = { onLocateInHierarchy(profile.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CivicColors.Navy)
                            ) {
                                Icon(Icons.Outlined.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Show in party structure")
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Background", icon = Icons.Outlined.Person) {
                    Text(profile.bio, style = MaterialTheme.typography.bodyMedium, color = CivicColors.OnSurface)
                }
            }

            if (profile.timeline.isNotEmpty()) {
                item {
                    SectionCard(title = "Political journey", icon = Icons.Outlined.Timeline) {
                        profile.timeline.forEachIndexed { index, milestone ->
                            TimelineItem(milestone, isLast = index == profile.timeline.lastIndex)
                        }
                    }
                }
            }

            if (profile.keyAchievements.isNotEmpty()) {
                item {
                    SectionCard(title = "Key roles & achievements", icon = Icons.Outlined.EmojiEvents) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            profile.keyAchievements.forEach { achievement ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        Modifier
                                            .padding(top = 7.dp)
                                            .size(6.dp)
                                            .background(CivicColors.Saffron, CircleShape)
                                    )
                                    Text(achievement, style = MaterialTheme.typography.bodyMedium, color = CivicColors.OnSurface)
                                }
                            }
                        }
                    }
                }
            }

            if (profile.verifiedSources.isNotEmpty()) {
                item {
                    SectionCard(title = "Source for this post", icon = Icons.Outlined.Description) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            profile.verifiedSources.forEach { SourceLine(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FactCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = CivicColors.TextMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = CivicColors.Navy)
    }
}

/** Year chip on a rail; the rail is drawn to the row's full height so it never gaps or overshoots. */
@Composable
private fun TimelineItem(milestone: TimelineMilestone, isLast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .drawBehind {
                    if (!isLast) {
                        drawLine(
                            color = CivicColors.Outline,
                            start = Offset(size.width / 2f, 24.dp.toPx()),
                            end = Offset(size.width / 2f, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = milestone.year,
                style = MaterialTheme.typography.labelMedium,
                color = CivicColors.Navy,
                modifier = Modifier
                    .background(CivicColors.SurfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 18.dp)) {
            Text(milestone.title, style = MaterialTheme.typography.titleSmall, color = CivicColors.Navy)
            Text(milestone.description, style = MaterialTheme.typography.bodySmall, color = CivicColors.TextSecondary)
        }
    }
}
