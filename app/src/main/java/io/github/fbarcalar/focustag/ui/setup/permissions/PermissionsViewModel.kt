package io.github.fbarcalar.focustag.ui.setup.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.FocusStateReader
import io.github.fbarcalar.focustag.system.GrayscaleFallbackSettings
import io.github.fbarcalar.focustag.system.PermissionChecker
import io.github.fbarcalar.focustag.system.PermissionId
import io.github.fbarcalar.focustag.system.PermissionItem
import io.github.fbarcalar.focustag.system.PermissionStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Permission checklist and the grayscale fallback switch. */
@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val permissionChecker: PermissionChecker,
    private val fallbackSettings: GrayscaleFallbackSettings,
    focusStateReader: FocusStateReader,
) : ViewModel() {
    private val locked = focusStateReader.state.map { it.mode == FocusMode.FOCUS }

    val uiState: StateFlow<PermissionsUiState> =
        combine(permissionChecker.items, fallbackSettings.enabled, locked) { items, fallbackOn, locked ->
            PermissionsUiState(items, fallbackOn, fallbackToggleEnabled(items.secureSettingsGranted(), fallbackOn, locked))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PermissionsUiState())

    /** Re-checks every item (ON_RESUME, after a runtime request). */
    fun refresh() = permissionChecker.refresh()

    /** Applies only when the rule above allows it, whatever the UI showed. */
    fun setFallbackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val allowed = fallbackToggleEnabled(
                secureGranted = permissionChecker.items.value.secureSettingsGranted(),
                fallbackOn = fallbackSettings.enabled.first(),
                locked = locked.first(),
            )
            if (allowed) fallbackSettings.setEnabled(enabled)
        }
    }

    private fun List<PermissionItem>.secureSettingsGranted() =
        any { it.id == PermissionId.WRITE_SECURE_SETTINGS && it.status == PermissionStatus.GRANTED }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
