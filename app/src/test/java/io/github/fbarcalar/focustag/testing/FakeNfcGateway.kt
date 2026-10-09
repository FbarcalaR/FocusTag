package io.github.fbarcalar.focustag.testing

import android.app.Activity
import android.content.Intent
import io.github.fbarcalar.focustag.nfc.NfcAvailability
import io.github.fbarcalar.focustag.nfc.NfcGateway
import io.github.fbarcalar.focustag.nfc.NfcTagHandle
import io.github.fbarcalar.focustag.nfc.ScannedTag
import io.github.fbarcalar.focustag.nfc.WriteResult
import kotlinx.coroutines.flow.MutableStateFlow

/** A tag "in the field" for reader-mode tests. */
data class FakeTagHandle(override val scanned: ScannedTag) : NfcTagHandle

/** A call to [NfcGateway.writeFocusTag]. */
data class RecordedWrite(val tag: NfcTagHandle, val uri: String)

/** Scriptable [NfcGateway]; NFC is ENABLED by default and writes succeed unless queued otherwise. */
class FakeNfcGateway : NfcGateway {
    override val availability = MutableStateFlow(NfcAvailability.ENABLED)

    /** What [tagIntentsAllowed] reports; tests flip it to simulate Android 16's per-app NFC tag setting. */
    @Volatile
    var tagIntents: Boolean = true

    private val reads = ArrayDeque<ScannedTag?>()
    private val writeResults = ArrayDeque<WriteResult>()
    private val recordedWrites = mutableListOf<RecordedWrite>()
    private var readerCallback: ((NfcTagHandle) -> Unit)? = null

    val writes: List<RecordedWrite> get() = recordedWrites.toList()
    val readerModeEnabled: Boolean get() = readerCallback != null

    /** The next [readTag] call returns [tag]. */
    fun enqueueRead(tag: ScannedTag?) = reads.addLast(tag)

    /** The next [writeFocusTag] call returns [result]. */
    fun enqueueWriteResult(result: WriteResult) = writeResults.addLast(result)

    /** Delivers [tag] to the active reader-mode callback. */
    fun present(tag: FakeTagHandle) {
        val callback = checkNotNull(readerCallback) { "Reader mode is not enabled" }
        callback(tag)
    }

    override fun readTag(intent: Intent): ScannedTag? = reads.removeFirstOrNull()

    override fun enableReaderMode(activity: Activity, onTag: (NfcTagHandle) -> Unit) {
        readerCallback = onTag
    }

    override fun disableReaderMode(activity: Activity) {
        readerCallback = null
    }

    override fun tagIntentsAllowed(): Boolean = tagIntents

    override suspend fun writeFocusTag(tag: NfcTagHandle, uri: String): WriteResult {
        recordedWrites += RecordedWrite(tag, uri)
        return writeResults.removeFirstOrNull() ?: WriteResult.Written
    }
}
