package io.github.fbarcalar.focustag.ui.status

import io.github.fbarcalar.focustag.focus.Effect
import io.github.fbarcalar.focustag.focus.EffectsStatus
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.FocusState
import io.github.fbarcalar.focustag.focus.FocusStats
import io.github.fbarcalar.focustag.system.PermissionItem
import io.github.fbarcalar.focustag.system.missingRequired
import kotlin.time.Duration

internal sealed interface StatusUiState {
    /** Before the store's first read, so FREE TIME never flashes while FOCUS loads. */
    data object Loading : StatusUiState

    data class Ready(
        val mode: FocusMode,
        val currentSession: Duration,
        val todayTotal: Duration,
        val missingPermissions: List<PermissionItem>,
        val failedEffects: Set<Effect>,
    ) : StatusUiState
}

internal fun statusUiState(
    state: FocusState,
    stats: FocusStats,
    effects: EffectsStatus,
    items: List<PermissionItem>,
): StatusUiState.Ready = StatusUiState.Ready(
    mode = state.mode,
    currentSession = stats.currentSession,
    todayTotal = stats.todayTotal,
    missingPermissions = items.missingRequired,
    // In FREE nothing should be on; a failed disable() without DND access is covered by the banner.
    failedEffects = if (state.mode == FocusMode.FOCUS) effects.failed else emptySet(),
)
