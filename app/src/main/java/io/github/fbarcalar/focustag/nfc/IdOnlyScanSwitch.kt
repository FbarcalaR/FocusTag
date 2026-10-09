package io.github.fbarcalar.focustag.nfc

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.fbarcalar.focustag.focus.AppStartHook
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Cards paired by ID only carry no FocusTag URI, so the manifest catches them by chip type through
 * the `IdOnlyScanTrigger` alias (D-62). That alias stays disabled unless such a card is paired, so
 * people who only use stickers never have card taps (a bank card, say) routed to the app.
 */
class IdOnlyScanSwitch @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PairingRepository,
) : AppStartHook {
    override suspend fun onAppStart() {
        repository.pairings
            .map { pairings -> pairings.values.any { it.isIdOnly } }
            .distinctUntilChanged()
            .collect(::setEnabled)
    }

    private fun setEnabled(enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(component(context), state, PackageManager.DONT_KILL_APP)
    }

    companion object {
        /** The manifest `<activity-alias>` that routes `TECH_DISCOVERED` to [NfcTriggerActivity]. */
        fun component(context: Context) = ComponentName(context, ALIAS)

        private const val ALIAS = "io.github.fbarcalar.focustag.nfc.IdOnlyScanTrigger"
    }
}
