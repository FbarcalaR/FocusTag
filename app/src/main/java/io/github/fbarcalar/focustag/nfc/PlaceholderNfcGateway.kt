package io.github.fbarcalar.focustag.nfc

import android.app.Activity
import android.content.Intent
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Stand-in reporting no NFC hardware until T3 lands `AndroidNfcGateway`. Deleted by T3. */
class PlaceholderNfcGateway @Inject constructor() : NfcGateway {
    override val availability: Flow<NfcAvailability> = flowOf(NfcAvailability.UNAVAILABLE)

    override fun readTag(intent: Intent): ScannedTag? = null

    override fun enableReaderMode(activity: Activity, onTag: (NfcTagHandle) -> Unit) = Unit

    override fun disableReaderMode(activity: Activity) = Unit

    override suspend fun writeFocusTag(tag: NfcTagHandle, uri: String): WriteResult =
        WriteResult.Failed(WriteFailure.IO_ERROR)
}
