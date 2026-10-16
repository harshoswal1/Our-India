package com.ourindia.ailawyer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ourindia.ailawyer.ui.screens.ChatScreen
import com.ourindia.ailawyer.ui.screens.DraftingScreen
import com.ourindia.ailawyer.ui.screens.IpcToBnsScreen
import com.ourindia.ailawyer.ui.screens.RightsAndSosScreen
import com.ourindia.ailawyer.ui.theme.LegalColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiLawyerApp(
    viewModel: AiLawyerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Surface(
                color = LegalColors.Surface,
                shadowElevation = 2.dp
            ) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = LegalColors.SaffronContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("⚖️", fontSize = 20.sp)
                                }
                            }
                            Column {
                                Text(
                                    text = "AI Lawyer India",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = LegalColors.Navy
                                )
                                Text(
                                    text = "न्याय साथी · Powered by Indian Jurisprudence & BNS 2023",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LegalColors.TextSecondary,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = LegalColors.Surface,
                        titleContentColor = LegalColors.Navy
                    )
                )
            }
        },
        bottomBar = {
            Surface(
                color = LegalColors.Surface,
                shadowElevation = 8.dp
            ) {
                NavigationBar(
                    containerColor = LegalColors.Surface,
                    contentColor = LegalColors.Navy,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = state.activeTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Consult AI") },
                        label = { Text("Consult AI", fontSize = 11.sp, fontWeight = if (state.activeTab == 0) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = LegalColors.SaffronDeep,
                            selectedTextColor = LegalColors.SaffronDeep,
                            indicatorColor = LegalColors.SaffronContainer,
                            unselectedIconColor = LegalColors.TextSecondary,
                            unselectedTextColor = LegalColors.TextSecondary
                        )
                    )

                    NavigationBarItem(
                        selected = state.activeTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        icon = { Icon(Icons.Default.Description, contentDescription = "Drafter") },
                        label = { Text("Drafter", fontSize = 11.sp, fontWeight = if (state.activeTab == 1) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = LegalColors.SaffronDeep,
                            selectedTextColor = LegalColors.SaffronDeep,
                            indicatorColor = LegalColors.SaffronContainer,
                            unselectedIconColor = LegalColors.TextSecondary,
                            unselectedTextColor = LegalColors.TextSecondary
                        )
                    )

                    NavigationBarItem(
                        selected = state.activeTab == 2,
                        onClick = { viewModel.selectTab(2) },
                        icon = { Icon(Icons.Default.SwapHoriz, contentDescription = "IPC->BNS") },
                        label = { Text("IPC ⇄ BNS", fontSize = 11.sp, fontWeight = if (state.activeTab == 2) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = LegalColors.SaffronDeep,
                            selectedTextColor = LegalColors.SaffronDeep,
                            indicatorColor = LegalColors.SaffronContainer,
                            unselectedIconColor = LegalColors.TextSecondary,
                            unselectedTextColor = LegalColors.TextSecondary
                        )
                    )

                    NavigationBarItem(
                        selected = state.activeTab == 3,
                        onClick = { viewModel.selectTab(3) },
                        icon = { Icon(Icons.Default.Security, contentDescription = "Rights & SOS") },
                        label = { Text("Rights & SOS", fontSize = 11.sp, fontWeight = if (state.activeTab == 3) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = LegalColors.SaffronDeep,
                            selectedTextColor = LegalColors.SaffronDeep,
                            indicatorColor = LegalColors.SaffronContainer,
                            unselectedIconColor = LegalColors.TextSecondary,
                            unselectedTextColor = LegalColors.TextSecondary
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(LegalColors.Background)
        ) {
            when (state.activeTab) {
                0 -> ChatScreen(
                    state = state,
                    onInputChange = viewModel::updateChatInput,
                    onSendMessage = viewModel::sendChatMessage
                )
                1 -> DraftingScreen(
                    state = state,
                    onDocTypeSelected = viewModel::setDocumentType,
                    onFieldUpdated = { sName, sAddr, rName, rAddr, facts, amount ->
                        viewModel.updateDrafterField(sName, sAddr, rName, rAddr, facts, amount)
                    },
                    onGenerate = viewModel::generateLegalDocument
                )
                2 -> IpcToBnsScreen(
                    state = state,
                    onSearchQueryChange = viewModel::searchIpcBns
                )
                3 -> RightsAndSosScreen(
                    state = state
                )
            }
        }
    }
}
