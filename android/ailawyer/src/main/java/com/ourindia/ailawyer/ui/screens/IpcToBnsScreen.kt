package com.ourindia.ailawyer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ourindia.ailawyer.data.IpcBnsItem
import com.ourindia.ailawyer.ui.AiLawyerUiState
import com.ourindia.ailawyer.ui.theme.LegalColors

@Composable
fun IpcToBnsScreen(
    state: AiLawyerUiState,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LegalColors.Background)
            .padding(16.dp)
    ) {
        Text(
            text = "🔄 IPC ⇄ BNS Converter",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = LegalColors.Navy
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Cross-reference legacy Indian Penal Code (IPC 1860) sections with Bharatiya Nyaya Sanhita (BNS 2023).",
            style = MaterialTheme.typography.bodySmall,
            color = LegalColors.TextSecondary
        )

        Spacer(Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
            value = state.ipcSearchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search by IPC/BNS section or offence (e.g. 420, theft, murder)", fontSize = 13.sp, color = LegalColors.TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LegalColors.SaffronDeep) },
            trailingIcon = {
                if (state.ipcSearchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = LegalColors.TextMuted)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LegalColors.SaffronDeep,
                unfocusedBorderColor = LegalColors.Outline,
                focusedTextColor = LegalColors.Navy,
                unfocusedTextColor = LegalColors.Navy,
                focusedContainerColor = LegalColors.Surface,
                unfocusedContainerColor = LegalColors.Surface
            ),
            singleLine = true
        )

        Spacer(Modifier.height(14.dp))

        // Results Count
        Text(
            text = "Showing ${state.ipcBnsResults.size} legal provisions",
            style = MaterialTheme.typography.labelMedium,
            color = LegalColors.TextSecondary,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(8.dp))

        // Results List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(state.ipcBnsResults) { item ->
                IpcBnsCard(item = item)
            }
        }
    }
}

@Composable
private fun IpcBnsCard(item: IpcBnsItem) {
    val isNonBailable = item.bailable.contains("non", ignoreCase = true)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LegalColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, LegalColors.Outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Section Mapping
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // IPC badge
                Surface(
                    color = LegalColors.SurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LegalColors.Outline)
                ) {
                    Text(
                        text = "IPC Sec ${item.ipcSection}",
                        color = LegalColors.Navy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = LegalColors.SaffronDeep,
                    modifier = Modifier.size(18.dp)
                )

                // BNS badge
                Surface(
                    color = LegalColors.SaffronContainer,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LegalColors.SaffronDeep.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "BNS Sec ${item.bnsSection}",
                        color = LegalColors.SaffronDeep,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Offence Name
            Text(
                text = item.offence,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = LegalColors.Navy
            )

            Spacer(Modifier.height(4.dp))

            // Key elements
            Text(
                text = item.keyElements,
                style = MaterialTheme.typography.bodySmall,
                color = LegalColors.TextSecondary,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = LegalColors.Outline)
            Spacer(Modifier.height(10.dp))

            // Badges: Punishment, Bailable, Jurisdiction
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Bailable badge
                Surface(
                    color = if (isNonBailable) LegalColors.CrimsonContainer else LegalColors.CivicContainer,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isNonBailable) LegalColors.Crimson.copy(alpha = 0.3f) else LegalColors.Civic.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = item.bailable,
                        color = if (isNonBailable) LegalColors.Crimson else LegalColors.CivicDeep,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Court badge
                Surface(
                    color = LegalColors.SurfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LegalColors.Outline)
                ) {
                    Text(
                        text = "🏛️ ${item.triableBy}",
                        color = LegalColors.Navy,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Punishment text
            Text(
                text = "⚖️ Punishment: ${item.punishment}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = LegalColors.SaffronDeep
            )
        }
    }
}
