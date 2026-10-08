package io.github.fbarcalar.focustag.focus

import java.time.Instant
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The role a paired NFC tag plays. */
enum class TagRole {
    /** Tag A, at the desk: starts a focus session. */
    ACTIVATE,

    /** Tag B, in the living room: ends a focus session. */
    DEACTIVATE,
}

/** The two modes the app can be in. */
enum class FocusMode {
    /** Free time: no effects, nothing blocked. */
    FREE,

    /** Focus session: DND + grayscale active, blocked apps intercepted. */
    FOCUS,
}

/** Persisted focus state; the single source of truth for every side effect (D-41). */
sealed interface FocusState {
    /** The mode this state represents. */
    val mode: FocusMode

    /** Free time. */
    data object Free : FocusState {
        override val mode = FocusMode.FREE
    }

    /** A focus session that started at [since] (wall-clock, D-42). */
    data class Focus(val since: Instant) : FocusState {
        override val mode = FocusMode.FOCUS
    }
}

/**
 * Timers shown on the Status screen.
 *
 * @property currentSession length of the live session; [Duration.ZERO] while FREE.
 * @property todayTotal focus time accumulated today (device zone), including the live session (D-42).
 */
data class FocusStats(val currentSession: Duration, val todayTotal: Duration)

/** What a tag scan did, used for user feedback. */
enum class ScanOutcome {
    /** FREE → FOCUS. */
    ACTIVATED,

    /** FOCUS → FREE. */
    DEACTIVATED,

    /** The scan did not change the mode (D-40). */
    NO_CHANGE,
}

/** Mode-changing entry point. Only the nfc layer (and the test harness) calls it. */
interface FocusController {
    /**
     * Applies a validated scan of a tag with [role]. Calls are serialised; the new state is
     * persisted before effects are reconciled. Never throws for permission problems.
     */
    suspend fun onTagScanned(role: TagRole): ScanOutcome
}

/** Read-only view of the focus state for the blocker and the UI. */
interface FocusStateReader {
    /** The persisted state; cold, backed by the store, distinct until changed. */
    val state: Flow<FocusState>

    /** Session and daily timers; ticks every second while FOCUS. */
    val stats: Flow<FocusStats>

    /** Result of the last effects reconcile (D-35). */
    val effectsStatus: StateFlow<EffectsStatus>
}

/** A system side effect driven by the focus mode. */
enum class Effect {
    /** The AutomaticZenRule providing DND + grayscale (D-30). */
    ZEN_RULE,

    /** The optional secure-settings grayscale fallback (D-33). */
    GRAYSCALE_FALLBACK,
}

/**
 * Outcome of applying the effects.
 *
 * @property failed effects that could not be applied, e.g. because a permission was revoked.
 */
data class EffectsStatus(val failed: Set<Effect> = emptySet()) {
    /** True when at least one effect failed. */
    val isDegraded: Boolean get() = failed.isNotEmpty()
}

/** System side effects of FOCUS, implemented by the system layer. Both calls are idempotent. */
interface FocusEffects {
    /** Turns the effects on; re-asserts a rule that was switched off externally (D-34). */
    suspend fun enable(): EffectsStatus

    /** Turns the effects off and restores anything the fallback changed. */
    suspend fun disable(): EffectsStatus
}

/**
 * Work to run once per process start; contributed with `@IntoSet` (D-43). Expected failures
 * (e.g. a revoked permission) must be handled inside the hook as typed results; anything thrown
 * is treated as a bug, logged and swallowed by the runner.
 */
fun interface AppStartHook {
    /** Runs on the application scope from `FocusTagApp.onCreate`. */
    suspend fun onAppStart()
}
