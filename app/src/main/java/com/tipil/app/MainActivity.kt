package com.tipil.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.tipil.app.navigation.TipilNavGraph
import com.tipil.app.ui.auth.AuthViewModel
import com.tipil.app.ui.theme.TipilTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TipilTheme {
                TipilNavGraph(authViewModel = authViewModel)
            }
        }
    }
}
