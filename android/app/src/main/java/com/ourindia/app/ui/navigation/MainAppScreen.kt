package com.ourindia.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ourindia.app.ui.partystructure.PartyStructureScreen

@Composable
fun MainAppScreen() {
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        PartyStructureScreen()
    }
}
