package io.github.fbarcalar.focustag.system.grayscale

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The two colour-correction secure settings; `null` means unset. */
data class DaltonizerValues(val enabled: String?, val mode: String?) {
    companion object {
        /** Colour correction on, in grayscale mode (D-33). */
        val GRAYSCALE = DaltonizerValues(enabled = "1", mode = "0")
    }
}

/** Thin adapter over the colour-correction keys in `Settings.Secure` (D-33). */
class DaltonizerSettings @Inject constructor(@ApplicationContext context: Context) {
    private val resolver = context.contentResolver

    fun read(): DaltonizerValues =
        DaltonizerValues(Settings.Secure.getString(resolver, KEY_ENABLED), Settings.Secure.getString(resolver, KEY_MODE))

    /** False when WRITE_SECURE_SETTINGS is missing. */
    fun write(values: DaltonizerValues): Boolean = try {
        Settings.Secure.putString(resolver, KEY_MODE, values.mode) &&
            Settings.Secure.putString(resolver, KEY_ENABLED, values.enabled)
    } catch (_: SecurityException) {
        false
    }

    private companion object {
        // Hidden Settings.Secure keys, hence literals.
        const val KEY_ENABLED = "accessibility_display_daltonizer_enabled"
        const val KEY_MODE = "accessibility_display_daltonizer"
    }
}
