package com.ourindia.ailawyer.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ourindia.ailawyer.data.CitizenRight
import com.ourindia.ailawyer.data.EmergencyContact
import com.ourindia.ailawyer.data.LegalKnowledge
import com.ourindia.ailawyer.ui.AiLawyerUiState
import com.ourindia.ailawyer.ui.theme.LegalColors

@Composable
fun RightsAndSosScreen(
    state: AiLawyerUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(LegalColors.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Emergency Legal SOS
        item {
            Text(
                text = "🚨 Emergency Legal SOS & Helplines",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = LegalColors.Navy
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Instant 24x7 government hotlines for legal aid, cyber crime freeze, women distress, and emergency support.",
                style = MaterialTheme.typography.bodySmall,
                color = LegalColors.TextSecondary
            )
        }

        items(LegalKnowledge.emergencyContacts) { contact ->
            EmergencyCard(contact = contact) {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${contact.number}")
                }
                context.startActivity(dialIntent)
            }
        }

        // Section: Citizen Rights
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "🛡️ Know Your Rights (नागरिक अधिकार)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = LegalColors.Navy
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Empowering legal protections guaranteed under the Constitution of India and newly enacted BNSS 2023.",
                style = MaterialTheme.typography.bodySmall,
                color = LegalColors.TextSecondary
            )
        }

        items(LegalKnowledge.citizenRights) { right ->
            RightCard(right = right)
        }
    }
}

@Composable
private fun EmergencyCard(contact: EmergencyContact, onCall: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LegalColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, LegalColors.Outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.title, fontWeight = FontWeight.Bold, color = LegalColors.Navy, fontSize = 14.5.sp)
                Spacer(Modifier.height(3.dp))
                Text(contact.description, color = LegalColors.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(contact.authority, color = LegalColors.SaffronDeep, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.width(10.dp))

            // Dial button
            Button(
                onClick = onCall,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LegalColors.Saffron),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(contact.number, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun RightCard(right: CitizenRight) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = LegalColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, LegalColors.Outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(right.title, fontWeight = FontWeight.Bold, color = LegalColors.Navy, fontSize = 14.5.sp)
                Surface(
                    color = LegalColors.CivicContainer,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, LegalColors.Civic.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = right.category,
                        color = LegalColors.CivicDeep,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "⚖️ Provision: ${right.legalProvision}",
                color = LegalColors.SaffronDeep,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = right.summary,
                color = LegalColors.OnSurface,
                fontSize = 12.5.sp,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(10.dp))

            Surface(
                color = LegalColors.CivicContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, LegalColors.Civic.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("💡", fontSize = 14.sp)
                    Text(
                        text = right.practicalTip,
                        color = LegalColors.CivicDeep,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
