package io.github.fbarcalar.focustag.blocker

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

/** Intent extra carrying the blocked package name to the blocking screen. */
const val EXTRA_BLOCKED_PACKAGE = "io.github.fbarcalar.focustag.extra.BLOCKED_PACKAGE"

/** Result of removing an app from the block list. */
sealed interface RemoveResult {
    /** The app is no longer blocked. */
    data object Removed : RemoveResult

    /** Refused because a focus session is running (D-45). */
    data object NotAllowedDuringFocus : RemoveResult
}

/** The user's list of apps blocked during FOCUS. */
interface BlockListRepository {
    /** Package names currently blocked. */
    val blockedPackages: Flow<Set<String>>

    /** Blocks [packageName]; allowed in any mode. */
    suspend fun add(packageName: String)

    /** Unblocks [packageName]; refused while FOCUS (D-45). */
    suspend fun remove(packageName: String): RemoveResult
}

/** A launchable app the user can block. */
data class InstalledApp(val packageName: String, val label: String)

/** Source of launchable apps (D-24). */
interface InstalledAppsSource {
    /** Launchable apps except ours, sorted by label; runs off the main thread. */
    suspend fun launchableApps(): List<InstalledApp>

    /** The app's icon; null when the package has no loadable icon. */
    suspend fun icon(packageName: String): Bitmap?
}
