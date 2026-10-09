package io.github.fbarcalar.focustag.ui.status

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fbarcalar.focustag.ui.common.ReaderModeEffect

/** Status screen entry point (frozen signature): wires [StatusViewModel] to the stateless [StatusScreen]. */
@Composable
fun StatusDestination(onOpenSetup: () -> Unit) {
    val viewModel = hiltViewModel<StatusViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    InAppScanning()
    StatusScreen(state = state, onOpenSetup = onOpenSetup)
}

/** Reader mode while Status is resumed, with a toast per tap (D-65). */
@Composable
private fun InAppScanning() {
    val viewModel = hiltViewModel<InAppScanViewModel>()
    val allowed by viewModel.readerModeAllowed.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    ReaderModeEffect(allowed, viewModel::enableReaderMode, viewModel::disableReaderMode)
    LaunchedEffect(viewModel) {
        viewModel.feedback.collect { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
}
