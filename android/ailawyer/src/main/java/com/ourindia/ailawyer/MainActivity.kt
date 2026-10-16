package com.ourindia.ailawyer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ourindia.ailawyer.ui.AiLawyerApp
import com.ourindia.ailawyer.ui.theme.AiLawyerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiLawyerTheme {
                AiLawyerApp()
            }
        }
    }
}
