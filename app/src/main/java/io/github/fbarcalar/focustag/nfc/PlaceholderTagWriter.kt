package io.github.fbarcalar.focustag.nfc

import io.github.fbarcalar.focustag.focus.TagRole
import javax.inject.Inject

/** Stand-in that never pairs until T3 lands the real writer. Deleted by T3. */
class PlaceholderTagWriter @Inject constructor() : TagWriter {
    override suspend fun pair(tag: NfcTagHandle, role: TagRole): PairingResult =
        PairingResult.WriteFailed(WriteFailure.IO_ERROR)
}
