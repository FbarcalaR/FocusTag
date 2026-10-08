package io.github.fbarcalar.focustag.ui.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Status screen entry point (frozen signature): wires [StatusViewModel] to the stateless [StatusScreen]. */
@Composable
fun StatusDestination(onOpenSetup: () -> Unit) {
    val viewModel = hiltViewModel<StatusViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    StatusScreen(state = state, onOpenSetup = onOpenSetup)
}
