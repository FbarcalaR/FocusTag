package io.github.fbarcalar.focustag.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.fbarcalar.focustag.focus.FocusStateReader
import io.github.fbarcalar.focustag.system.PermissionChecker
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Read-only Status state. It has no mode-changing dependency, so the screen cannot exit FOCUS (D-45). */
@HiltViewModel
internal class StatusViewModel @Inject constructor(
    reader: FocusStateReader,
    private val permissions: PermissionChecker,
) : ViewModel() {
    val uiState: StateFlow<StatusUiState> =
        combine(reader.state, reader.stats, reader.effectsStatus, permissions.items, ::statusUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StatusUiState.Loading)

    /** A permission may have changed in system Settings while we were away. */
    fun onResume() = permissions.refresh()

    private companion object {
        // Survives a configuration change without restarting the upstream flows.
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
