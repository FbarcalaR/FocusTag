package io.github.fbarcalar.focustag.ui.status

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.fbarcalar.focustag.R
import io.github.fbarcalar.focustag.di.ApplicationScope
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.nfc.NfcGateway
import io.github.fbarcalar.focustag.nfc.NfcTagHandle
import io.github.fbarcalar.focustag.nfc.TagScanProcessor
import io.github.fbarcalar.focustag.nfc.TapSource
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Reads tags itself while Status is open (D-65), so a tap works even when Android's background
 * dispatch never reaches FocusTag. Scans go through the same [TagScanProcessor] as background taps,
 * so only a valid living-room tag can end FOCUS (D-45 holds).
 */
@HiltViewModel
class InAppScanViewModel @Inject constructor(
    private val nfcGateway: NfcGateway,
    private val processor: TagScanProcessor,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val feedbackChannel = Channel<Int>(Channel.BUFFERED)

    /** A string resource to toast for each tap. */
    val feedback: Flow<Int> = feedbackChannel.receiveAsFlow()

    val readerModeAllowed: StateFlow<Boolean> = nfcGateway.availability
        .map { it == NfcAvailability.ENABLED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    fun enableReaderMode(activity: Activity) = nfcGateway.enableReaderMode(activity, ::onTagDiscovered)

    fun disableReaderMode(activity: Activity) = nfcGateway.disableReaderMode(activity)

    /** Reader-mode callback (binder thread). The app scope lets a toggle finish even if Status closes. */
    internal fun onTagDiscovered(tag: NfcTagHandle) {
        appScope.launch {
            val feedback = processor.process(tag.scanned, TapSource.IN_APP)
            feedbackChannel.trySend(feedback?.message ?: R.string.nfc_scan_not_recognised)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
