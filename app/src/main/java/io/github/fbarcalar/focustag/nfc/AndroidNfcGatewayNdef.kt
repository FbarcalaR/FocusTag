package io.github.fbarcalar.focustag.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.TagTechnology
import java.io.Closeable
import java.io.IOException

/** Our tag payload: the URI record first (it drives dispatch), then the AAR (D-10). */
internal fun focusMessage(uri: String, packageName: String): NdefMessage =
    NdefMessage(NdefRecord.createUri(uri), NdefRecord.createApplicationRecord(packageName))

/** Every URI record in [messages]; the AAR is skipped explicitly because `toUri()` maps it to a URI too. */
internal fun uris(messages: List<NdefMessage>): List<String> =
    messages.flatMap { it.records.asList() }
        .filter { it.tnf != NdefRecord.TNF_EXTERNAL_TYPE }
        .mapNotNull { it.toUri()?.toString() }

internal fun Tag.toScannedTag(messages: List<NdefMessage>) = ScannedTag(id.toUidHex(), uris(messages))

internal fun ByteArray.toUidHex(): String = joinToString(separator = "") { "%02X".format(it) }

/**
 * The writable NDEF view of this tag, or null when it cannot hold our message.
 *
 * Android also reports MIFARE Classic and DESFire cards (transport, access and loyalty cards) as
 * `NdefFormatable`, but those are key-protected and refuse a format. So only blank NFC Forum
 * Type 2 stickers (NTAG/Ultralight) are formatted; anything else without `Ndef` is unsupported.
 */
internal fun Tag.ndefTarget(message: NdefMessage): CloseableNdefTarget? =
    Ndef.get(this)?.let { NdefTechTarget(it, message) }
        ?: formatableSticker()?.let { FormatableTarget(it, message) }

private fun Tag.formatableSticker(): NdefFormatable? =
    if (MifareUltralight.get(this) != null) NdefFormatable.get(this) else null

internal interface CloseableNdefTarget : NdefTarget, Closeable

private class NdefTechTarget(private val ndef: Ndef, private val message: NdefMessage) : CloseableNdefTarget {
    override val isWritable: Boolean get() = ndef.isWritable
    override val maxSize: Int get() = ndef.maxSize

    override fun write() = ioBoundary {
        ndef.connect()
        ndef.writeNdefMessage(message)
    }

    /** A fresh read from the tag, not the message cached at discovery. */
    override fun readBackUris(): List<String> = ioBoundary { uris(listOfNotNull(ndef.ndefMessage)) }

    override fun close() = ndef.closeQuietly()
}

/** After `format` the same `Tag` still lacks the `Ndef` tech, so it cannot be verified (PLAN T3 N3). */
private class FormatableTarget(
    private val formatable: NdefFormatable,
    private val message: NdefMessage,
) : CloseableNdefTarget {
    override val isWritable: Boolean = true
    override val maxSize: Int? = null

    override fun write() = ioBoundary {
        formatable.connect()
        formatable.format(message)
    }

    override fun readBackUris(): List<String>? = null

    override fun close() = formatable.closeQuietly()
}

/** Framework exceptions for a stale, busy or garbled tag become the I/O failure the rules expect. */
private inline fun <T> ioBoundary(block: () -> T): T = try {
    block()
} catch (e: TagLostException) {
    throw TagLeftFieldException(e)
} catch (e: FormatException) {
    throw IOException(e)
} catch (e: SecurityException) {
    throw IOException(e)
} catch (e: IllegalStateException) {
    throw IOException(e)
}

private fun TagTechnology.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {
        // The tag already left the field; there is nothing left to release.
    }
}
