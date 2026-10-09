package io.github.fbarcalar.focustag.ui.status

import com.google.common.truth.Truth.assertThat
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.focus.FocusMode
import io.github.fbarcalar.focustag.focus.TagRole
import io.github.fbarcalar.focustag.nfc.FocusTagUri
import io.github.fbarcalar.focustag.nfc.InMemoryPairingRepository
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.nfc.ScannedTag
import io.github.fbarcalar.focustag.nfc.TagPairing
import io.github.fbarcalar.focustag.nfc.TagScanProcessor
import io.github.fbarcalar.focustag.nfc.TapSource
import io.github.fbarcalar.focustag.nfc.tagId
import io.github.fbarcalar.focustag.testing.FakeClock
import io.github.fbarcalar.focustag.testing.FakeFocusEngine
import io.github.fbarcalar.focustag.testing.FakeNfcGateway
import io.github.fbarcalar.focustag.testing.FakeTagHandle
import io.github.fbarcalar.focustag.testing.FakeTapLog
import io.github.fbarcalar.focustag.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InAppScanViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val sticker = TagPairing.written(TagRole.ACTIVATE, "6f1d2c3b-4a59-4e8f-9a01-0b2c3d4e5f60", "04A1B2C3D4E5F6")
    private val repository = InMemoryPairingRepository(mapOf(TagRole.ACTIVATE to sticker))
    private val gateway = FakeNfcGateway()
    private val engine = FakeFocusEngine()
    private val tapLog = FakeTapLog()

    private fun test(block: suspend TestScope.(InAppScanViewModel) -> Unit) = runTest(mainRule.dispatcher) {
        val processor = TagScanProcessor(repository, engine, tapLog, FakeClock())
        block(InAppScanViewModel(gateway, processor, backgroundScope))
    }

    @Test
    fun `a paired tag read in the app toggles focus, toasts the result and is logged as in-app`() = test { viewModel ->
        viewModel.onTagDiscovered(FakeTagHandle(ScannedTag(sticker.uidHex, listOf(FocusTagUri.build(sticker.tagId)))))

        assertThat(viewModel.feedback.first()).isEqualTo(R.string.nfc_scan_focus_on)
        assertThat(engine.state.value.mode).isEqualTo(FocusMode.FOCUS)
        assertThat(tapLog.lastTap.value?.source).isEqualTo(TapSource.IN_APP)
    }

    @Test
    fun `an unknown tag says it is not recognised and leaves the mode alone`() = test { viewModel ->
        viewModel.onTagDiscovered(FakeTagHandle(ScannedTag("5A000000", emptyList())))

        assertThat(viewModel.feedback.first()).isEqualTo(R.string.nfc_scan_not_recognised)
        assertThat(engine.state.value.mode).isEqualTo(FocusMode.FREE)
    }

    @Test
    fun `reader mode is only allowed while nfc is on`() = test { viewModel ->
        backgroundScope.launch { viewModel.readerModeAllowed.collect {} }
        val whenOn = viewModel.readerModeAllowed.value

        gateway.availability.value = NfcAvailability.DISABLED

        assertThat(whenOn).isTrue()
        assertThat(viewModel.readerModeAllowed.value).isFalse()
    }
}
