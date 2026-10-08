package io.github.fbarcalar.focustag

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.fbarcalar.focustag.ui.nav.FocusTagNavHost
import io.github.fbarcalar.focustag.ui.theme.FocusTagTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusTagTheme { FocusTagNavHost() }
        }
    }
}
