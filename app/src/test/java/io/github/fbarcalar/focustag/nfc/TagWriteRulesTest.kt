package io.github.fbarcalar.focustag.nfc

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Test

class TagWriteRulesTest {
    private val uri = "focustag://toggle/6f1d2c3b-4a59-4e8f-9a01-0b2c3d4e5f60"

    private class FakeNdefTarget(
        override val isWritable: Boolean = true,
        override val maxSize: Int? = 100,
        private val failWrite: Boolean = false,
        private val failRead: Boolean = false,
        private val readBack: List<String>? = null,
    ) : NdefTarget {
        var writeCount = 0

        override fun write() {
            writeCount++
            if (failWrite) throw IOException("tag lost")
        }

        override fun readBackUris(): List<String>? = if (failRead) throw IOException("tag lost") else readBack
    }

    @Test
    fun `a locked tag is read only and never written`() {
        val target = FakeNdefTarget(isWritable = false)

        assertThat(TagWriteRules.write(target, 40, uri)).isEqualTo(WriteResult.Failed(WriteFailure.READ_ONLY))
        assertThat(target.writeCount).isEqualTo(0)
    }

    @Test
    fun `a message larger than the tag is too small and never written`() {
        val target = FakeNdefTarget(maxSize = 39)

        assertThat(TagWriteRules.write(target, 40, uri)).isEqualTo(WriteResult.Failed(WriteFailure.TOO_SMALL))
        assertThat(target.writeCount).isEqualTo(0)
    }

    @Test
    fun `a message exactly the tag size fits`() {
        val target = FakeNdefTarget(maxSize = 40, readBack = listOf(uri))

        assertThat(TagWriteRules.write(target, 40, uri)).isEqualTo(WriteResult.Written)
    }

    @Test
    fun `an io failure while writing is an io error`() {
        val target = FakeNdefTarget(failWrite = true)

        assertThat(TagWriteRules.write(target, 40, uri)).isEqualTo(WriteResult.Failed(WriteFailure.IO_ERROR))
    }

    @Test
    fun `an io failure while reading back is an io error`() {
        val target = FakeNdefTarget(failRead = true)

        assertThat(TagWriteRules.write(target, 40, uri)).isEqualTo(WriteResult.Failed(WriteFailure.IO_ERROR))
    }

    @Test
    fun `a different uri read back fails verification`() {
        val target = FakeNdefTarget(readBack = listOf("focustag://toggle/other"))

        assertThat(TagWriteRules.write(target, 40, uri)).isEqualTo(WriteResult.Failed(WriteFailure.VERIFY_FAILED))
    }

    @Test
    fun `a tag that cannot be read back fails verification`() {
        val target = FakeNdefTarget(maxSize = null, readBack = null)

        assertThat(TagWriteRules.write(target, 40, uri)).isEqualTo(WriteResult.Failed(WriteFailure.VERIFY_FAILED))
        assertThat(target.writeCount).isEqualTo(1)
    }

    @Test
    fun `a matching read back is written`() {
        val target = FakeNdefTarget(readBack = listOf(uri))

        assertThat(TagWriteRules.write(target, 40, uri)).isEqualTo(WriteResult.Written)
        assertThat(target.writeCount).isEqualTo(1)
    }
}
