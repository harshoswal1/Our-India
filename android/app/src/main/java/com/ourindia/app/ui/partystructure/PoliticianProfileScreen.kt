package com.ourindia.app.ui.partystructure

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ourindia.app.ui.theme.CivicColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoliticianProfileScreen(
    profile: PoliticianProfile,
    onBack: () -> Unit,
    onLocateInHierarchy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Politician Profile",
                        fontWeight = FontWeight.Bold,
                        color = CivicColors.Navy,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = CivicColors.Navy
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onLocateInHierarchy(profile.id) }) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = "Locate in Tree",
                            tint = CivicColors.Saffron
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CivicColors.Background
                )
            )
        },
        containerColor = CivicColors.Background,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Politician Header Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, CivicColors.Navy, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(profile.level.badgeColor.copy(alpha = 0.15f), CircleShape)
                                    .border(2.dp, profile.level.badgeColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = profile.photoEmoji, fontSize = 32.sp)
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = profile.level.badgeColor,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = profile.level.name,
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
                                            text = profile.party,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = CivicColors.Navy
                                )

                                Text(
                                    text = profile.roleTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CivicColors.Saffron,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Divider(color = CivicColors.Navy.copy(alpha = 0.1f))

                        // Quick stats grid (Jurisdiction, Education, Status)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ProfileFactItem("Jurisdiction", profile.state ?: "National HQ")
                            ProfileFactItem("District/Ward", profile.district ?: "Nationwide")
                            ProfileFactItem("Education", profile.education)
                        }
                    }
                }
            }

            // 2. Action: Jump into Hierarchy Canvas
            item {
                Button(
                    onClick = { onLocateInHierarchy(profile.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, CivicColors.Navy, RoundedCornerShape(10.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = CivicColors.Navy),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = CivicColors.Saffron,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "View in Organizational Hierarchy Tree",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // 3. Biography / Background Card
            item {
                ProfileSectionCard(title = "Background & Profile", icon = Icons.Default.Person) {
                    Text(
                        text = profile.bio,
                        fontSize = 13.sp,
                        color = CivicColors.OnSurface,
                        lineHeight = 19.sp
                    )
                }
            }

            // 4. Political Journey Timeline
            item {
                ProfileSectionCard(title = "Political Journey & Milestones", icon = Icons.Default.Timeline) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        profile.timeline.forEachIndexed { index, milestone ->
                            TimelineMilestoneItem(
                                milestone = milestone,
                                isLast = index == profile.timeline.lastIndex
                            )
                        }
                    }
                }
            }

            // 5. Key Achievements & Initiatives
            if (profile.keyAchievements.isNotEmpty()) {
                item {
                    ProfileSectionCard(title = "Key Roles & Achievements", icon = Icons.Default.Star) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            profile.keyAchievements.forEach { achievement ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = CivicColors.Teal,
                                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                    )
                                    Text(
                                        text = achievement,
                                        fontSize = 13.sp,
                                        color = CivicColors.OnSurface,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Verified References & Sources
            item {
                ProfileSectionCard(title = "Verified Data Sources", icon = Icons.Default.VerifiedUser) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        profile.verifiedSources.forEach { source ->
                            Surface(
                                color = CivicColors.Navy.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = source.title,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = CivicColors.Navy
                                        )
                                        Text(
                                            text = "Authority: ${source.authority} • Updated: ${source.date}",
                                            fontSize = 10.sp,
                                            color = CivicColors.TextSecondary
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Verified",
                                        tint = CivicColors.Teal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileFactItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 10.sp, color = CivicColors.TextSecondary)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = CivicColors.Navy
        )
    }
}

@Composable
private fun ProfileSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CivicColors.Navy.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CivicColors.Navy,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CivicColors.Navy
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun TimelineMilestoneItem(
    milestone: TimelineMilestone,
    isLast: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Timeline node indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(44.dp)
        ) {
            Surface(
                color = CivicColors.Navy,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = milestone.year,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(36.dp)
                        .background(CivicColors.Navy.copy(alpha = 0.3f))
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = milestone.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = CivicColors.Navy
            )
            Text(
                text = milestone.description,
                fontSize = 11.sp,
                color = CivicColors.OnSurface,
                lineHeight = 15.sp
            )
        }
    }
}
