package io.github.fbarcalar.focustag.ui.status

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.Effect
import io.github.fbarcalar.focustag.focus.EffectsStatus
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.FocusState
import io.github.fbarcalar.focustag.focus.FocusStats
import io.github.fbarcalar.focustag.system.PermissionId
import io.github.fbarcalar.focustag.system.PermissionStatus
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.junit.Test

class StatusUiStateTest {
    private val focus = FocusState.Focus(Instant.parse("2026-10-06T10:00:00Z"))
    private val zeroStats = FocusStats(Duration.ZERO, Duration.ZERO)

    @Test
    fun `free state maps to free mode with its stats`() {
        val ui = statusUiState(FocusState.Free, FocusStats(Duration.ZERO, 40.minutes), EffectsStatus(), emptyList())

        assertThat(ui).isEqualTo(readyState(mode = FocusMode.FREE, todayTotal = 40.minutes))
    }

    @Test
    fun `focus state carries the session and today timers`() {
        val ui = statusUiState(focus, FocusStats(5.minutes, 45.minutes), EffectsStatus(), emptyList())

        assertThat(ui).isEqualTo(readyState(mode = FocusMode.FOCUS, currentSession = 5.minutes, todayTotal = 45.minutes))
    }

    @Test
    fun `only required missing permissions are listed`() {
        val dnd = missing(PermissionId.NOTIFICATION_POLICY_ACCESS)
        val items = listOf(
            dnd,
            permission(PermissionId.NFC_ENABLED, PermissionStatus.UNSUPPORTED),
            permission(PermissionId.ACCESSIBILITY_SERVICE, PermissionStatus.GRANTED),
            permission(PermissionId.BATTERY_OPTIMIZATION_EXEMPTION, PermissionStatus.MISSING, required = false),
        )

        val ui = statusUiState(focus, zeroStats, EffectsStatus(), items)

        assertThat(ui.missingPermissions).containsExactly(dnd)
    }

    @Test
    fun `failed effects are shown while focused`() {
        val ui = statusUiState(focus, zeroStats, EffectsStatus(setOf(Effect.ZEN_RULE)), emptyList())

        assertThat(ui.failedEffects).containsExactly(Effect.ZEN_RULE)
    }

    @Test
    fun `failed effects are hidden during free time`() {
        val ui = statusUiState(FocusState.Free, zeroStats, EffectsStatus(setOf(Effect.ZEN_RULE)), emptyList())

        assertThat(ui.failedEffects).isEmpty()
    }
}
