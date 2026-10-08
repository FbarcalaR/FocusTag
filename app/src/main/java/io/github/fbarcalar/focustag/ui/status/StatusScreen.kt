package io.github.fbarcalar.focustag.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fbarcalar.focustag.R

/** Stateless Status screen. Its only event opens Setup; nothing on it changes the mode (D-45). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusScreen(state: StatusUiState, onOpenSetup: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
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
        when (state) {
            StatusUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding))
            is StatusUiState.Ready -> StatusContent(state, onOpenSetup, Modifier.padding(padding))
        }
    }
}

private val StatusUiState.Ready.hasWarnings: Boolean
    get() = missingPermissions.isNotEmpty() || failedEffects.isNotEmpty()

@Composable
private fun StatusContent(state: StatusUiState.Ready, onOpenSetup: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        // Scrolls so that large font scales never clip the timers.
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (state.hasWarnings) StatusWarnings(state.missingPermissions, state.failedEffects, onOpenSetup)
        ModeIndicator(state.mode)
        SessionTimers(state.mode, state.currentSession, state.todayTotal)
        ModeHint(state.mode)
    }
}
