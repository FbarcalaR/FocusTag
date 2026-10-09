package io.github.fbarcalar.focustag.nfc

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.focus.TagRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class IdOnlyScanSwitchTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = InMemoryPairingRepository()
    private val sticker = TagPairing.written(TagRole.ACTIVATE, "6f1d2c3b-4a59-4e8f-9a01-0b2c3d4e5f60", "04A1B2C3D4E5F6")

    private fun aliasState() = context.packageManager.getComponentEnabledSetting(IdOnlyScanSwitch.component(context))

    private val switch = IdOnlyScanSwitch(context, repository)

    private fun runSwitch(block: suspend () -> Unit) = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { switch.onAppStart() }
        block()
    }

    @Test
    fun `card scans stay off while only stickers are paired`() = runSwitch {
        repository.save(sticker)

        assertThat(aliasState()).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    @Test
    fun `card scans turn on once a card is paired by id and off again when it is reset`() = runSwitch {
        repository.save(TagPairing.idOnly(TagRole.DEACTIVATE, "5A3B9C21"))
        val withCard = aliasState()
        repository.reset(TagRole.DEACTIVATE)

        assertThat(withCard).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        assertThat(aliasState()).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    @Test
    fun `the status tells never switched apart from switched off and on`() {
        val before = switch.current()

        runSwitch {
            repository.save(sticker)
            assertThat(switch.current()).isEqualTo(CardScanning.OFF)
            repository.save(TagPairing.idOnly(TagRole.DEACTIVATE, "5A3B9C21"))
            assertThat(switch.current()).isEqualTo(CardScanning.ON)
        }

        assertThat(before).isEqualTo(CardScanning.NEVER_SWITCHED)
    }
}
