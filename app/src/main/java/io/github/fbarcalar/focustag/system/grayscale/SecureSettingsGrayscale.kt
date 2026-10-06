package io.github.fbarcalar.focustag.system.grayscale

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Result of switching the fallback grayscale. */
sealed interface FallbackOutcome {
    /** The settings are in the requested state. */
    data object Applied : FallbackOutcome

    /** WRITE_SECURE_SETTINGS is missing (D-33). */
    data object NotGranted : FallbackOutcome
}

/** Fallback grayscale through colour correction; restores the exact previous values (D-33). */
class SecureSettingsGrayscale @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: DaltonizerSettings,
    private val snapshots: DaltonizerSnapshotStore,
) {
    fun isGranted(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    suspend fun enable(): FallbackOutcome {
        if (!isGranted()) return FallbackOutcome.NotGranted
        val current = settings.read()
        snapshots.saveIfAbsent(current)
        val applied = current == DaltonizerValues.GRAYSCALE || settings.write(DaltonizerValues.GRAYSCALE)
        return if (applied) FallbackOutcome.Applied else FallbackOutcome.NotGranted
    }

    /** No-op without a snapshot; keeps the snapshot when it can't be restored yet. */
    suspend fun disable(): FallbackOutcome {
        val snapshot = snapshots.saved() ?: return FallbackOutcome.Applied
        if (!isGranted() || !settings.write(snapshot)) return FallbackOutcome.NotGranted
        snapshots.clear()
        return FallbackOutcome.Applied
    }
}
