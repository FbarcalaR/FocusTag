package io.github.fbarcalar.focustag.nfc

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.fbarcalar.focustag.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/** A tag delivered by reader mode; keeps the framework [Tag] for writing. */
internal class AndroidNfcTagHandle(val tag: Tag, override val scanned: ScannedTag) : NfcTagHandle

/** The production [NfcGateway] over [NfcAdapter] (D-14, D-15). */
@Singleton
class AndroidNfcGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : NfcGateway {
    private val adapter: NfcAdapter? get() = NfcAdapter.getDefaultAdapter(context)

    override val availability: Flow<NfcAvailability> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(currentAvailability())
            }
        }
        val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(currentAvailability())
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    override fun readTag(intent: Intent): ScannedTag? {
        val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return null
        val messages = IntentCompat.getParcelableArrayExtra(intent, NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        return tag.toScannedTag(messages.orEmpty().filterIsInstance<NdefMessage>())
    }

    override fun enableReaderMode(activity: Activity, onTag: (NfcTagHandle) -> Unit) {
        adapter?.enableReaderMode(activity, { tag -> onTag(tag.toHandle()) }, READER_FLAGS, null)
    }

    override fun disableReaderMode(activity: Activity) {
        adapter?.disableReaderMode(activity)
    }

    override suspend fun writeFocusTag(tag: NfcTagHandle, uri: String): WriteResult {
        require(tag is AndroidNfcTagHandle) { "Only tags from reader mode can be written: $tag" }
        val message = focusMessage(uri, context.packageName)
        val target = tag.tag.ndefTarget(message) ?: return WriteResult.Failed(WriteFailure.NOT_NDEF)
        return withContext(io) { target.use { TagWriteRules.write(it, message.byteArrayLength, uri) } }
    }

    private fun currentAvailability(): NfcAvailability {
        val current = adapter
        return when {
            current == null -> NfcAvailability.UNAVAILABLE
            current.isEnabled -> NfcAvailability.ENABLED
            else -> NfcAvailability.DISABLED
        }
    }

    /** No `FLAG_READER_SKIP_NDEF_CHECK`: the platform's NDEF check fills `cachedNdefMessage`. */
    private fun Tag.toHandle() = AndroidNfcTagHandle(this, toScannedTag(listOfNotNull(Ndef.get(this)?.cachedNdefMessage)))

    private companion object {
        const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
    }
}
