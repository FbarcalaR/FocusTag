package io.github.fbarcalar.focustag.ui.status

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.ui.theme.FocusTagTheme

/** Status screen entry point (frozen signature). PLACEHOLDER(T6): body replaced by T6. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusDestination(onOpenSetup: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSetup) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_open_setup))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.title_status))
        }
    }
}

@Preview
@Composable
private fun StatusDestinationPreview() {
    FocusTagTheme { StatusDestination(onOpenSetup = {}) }
}
