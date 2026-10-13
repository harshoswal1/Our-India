package com.ourindia.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ourindia.app.ui.navigation.MainAppScreen
import com.ourindia.app.ui.theme.OurIndiaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The UI is light-only, so bars are forced light. SystemBarStyle.auto would flip
        // to light icons under a system dark theme and they'd vanish on the cream ground.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.argb(0xE6, 0xFF, 0xFF, 0xFF), Color.argb(0x80, 0x1B, 0x1B, 0x1B))
        )
        super.onCreate(savedInstanceState)
        setContent {
            OurIndiaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScreen()
                }
            }
        }
    }
}
