package io.github.fbarcalar.focustag.nfc

import java.io.IOException

/** Seam over the Android NDEF techs so the write/verify rules stay JVM-testable. */
internal interface NdefTarget {
    val isWritable: Boolean

    /** Capacity in bytes, or null when unknown (a tag that still has to be formatted). */
    val maxSize: Int?

    /** Writes the prepared message. */
    @Throws(IOException::class)
    fun write()

    /** URIs read back from the tag, or null when this tag cannot be re-read in the same session. */
    @Throws(IOException::class)
    fun readBackUris(): List<String>?
}

/** Write-then-verify policy (D-10): every expected failure becomes a typed [WriteResult]. */
internal object TagWriteRules {
    fun write(target: NdefTarget, messageSize: Int, expectedUri: String): WriteResult = when {
        !target.isWritable -> failed(WriteFailure.READ_ONLY)
        messageSize > (target.maxSize ?: messageSize) -> failed(WriteFailure.TOO_SMALL)
        else -> writeAndVerify(target, expectedUri)
    }

    private fun writeAndVerify(target: NdefTarget, expectedUri: String): WriteResult = try {
        target.write()
        verify(target.readBackUris(), expectedUri)
    } catch (_: IOException) {
        failed(WriteFailure.IO_ERROR)
    }

    private fun verify(readBack: List<String>?, expectedUri: String): WriteResult = when (readBack?.firstOrNull()) {
        expectedUri -> WriteResult.Written
        else -> failed(WriteFailure.VERIFY_FAILED)
    }

    private fun failed(reason: WriteFailure) = WriteResult.Failed(reason)
}
