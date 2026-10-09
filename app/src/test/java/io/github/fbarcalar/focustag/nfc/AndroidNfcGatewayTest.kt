package io.github.fbarcalar.focustag.nfc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNfcAdapter
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(AndroidJUnit4::class)
class AndroidNfcGatewayTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val gateway = AndroidNfcGateway(context, Dispatchers.Unconfined)
    private val uri = "focustag://toggle/6f1d2c3b-4a59-4e8f-9a01-0b2c3d4e5f60"
    private val uid = byteArrayOf(0x04, 0xA1.toByte(), 0xB2.toByte(), 0x0C, 0xD4.toByte(), 0xE5.toByte(), 0x06)

    @Before
    fun installEnabledAdapter() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_NFC, true)
        ShadowNfcAdapter.setNfcHardwareExists(true)
        shadowAdapter().setEnabled(true)
    }

    @After
    fun resetAdapter() = ShadowNfcAdapter.reset()

    @Test
    fun `read tag returns the upper case uid and only the uri record`() {
        val intent = ndefIntent(mockTag(), focusMessage(uri, context.packageName))

        assertThat(gateway.readTag(intent)).isEqualTo(ScannedTag("04A1B20CD4E506", listOf(uri)))
    }

    @Test
    fun `read tag without a tag extra returns null`() {
        assertThat(gateway.readTag(Intent(NfcAdapter.ACTION_NDEF_DISCOVERED))).isNull()
    }

    @Test
    fun `read tag without ndef messages has no uris`() {
        val intent = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED).putExtra(NfcAdapter.EXTRA_TAG, mockTag())

        assertThat(gateway.readTag(intent)?.ndefUris).isEmpty()
    }

    @Test
    fun `the focus message holds the uri first and our application record`() {
        val records = focusMessage(uri, context.packageName).records

        assertThat(records[0].toUri().toString()).isEqualTo(uri)
        assertThat(records[1].tnf).isEqualTo(NdefRecord.TNF_EXTERNAL_TYPE)
        assertThat(String(records[1].payload)).isEqualTo(context.packageName)
    }

    @Test
    fun `availability follows the adapter state`() = runTest {
        gateway.availability.test {
            assertThat(awaitItem()).isEqualTo(NfcAvailability.ENABLED)

            setAdapterEnabled(false)
            assertThat(awaitItem()).isEqualTo(NfcAvailability.DISABLED)

            setAdapterEnabled(true)
            assertThat(awaitItem()).isEqualTo(NfcAvailability.ENABLED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `availability is unavailable without nfc hardware`() = runTest {
        ShadowNfcAdapter.setNfcHardwareExists(false)

        gateway.availability.test {
            assertThat(awaitItem()).isEqualTo(NfcAvailability.UNAVAILABLE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reader mode delivers discovered tags until disabled`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val delivered = mutableListOf<NfcTagHandle>()

        gateway.enableReaderMode(activity) { delivered += it }
        shadowAdapter().dispatchTagDiscovered(mockTag())
        gateway.disableReaderMode(activity)
        shadowAdapter().dispatchTagDiscovered(mockTag())

        assertThat(delivered.single().scanned).isEqualTo(ScannedTag("04A1B20CD4E506", emptyList()))
        assertThat(shadowAdapter().isInReaderMode).isFalse()
    }

    @Test
    fun `enabling reader mode puts the adapter in reader mode`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        gateway.enableReaderMode(activity) {}

        assertThat(shadowAdapter().isInReaderMode).isTrue()
    }

    @Test
    fun `writing a tag without ndef support fails as not ndef`() = runTest {
        val handle = AndroidNfcTagHandle(mockTag(), ScannedTag("04A1B20CD4E506", emptyList()))

        assertThat(gateway.writeFocusTag(handle, uri)).isEqualTo(WriteResult.Failed(WriteFailure.NOT_NDEF))
    }

    @Test
    fun `a protected desfire transport card is unsupported, not formatted`() = runTest {
        val card = mockTag(TECH_NFC_A, TECH_ISO_DEP, TECH_NDEF_FORMATABLE)
        val handle = AndroidNfcTagHandle(card, ScannedTag("04A1B20CD4E506", emptyList()))

        assertThat(gateway.writeFocusTag(handle, uri)).isEqualTo(WriteResult.Failed(WriteFailure.NOT_NDEF))
    }

    @Test
    fun `a mifare classic card is unsupported, not formatted`() = runTest {
        val card = mockTag(TECH_NFC_A, TECH_MIFARE_CLASSIC, TECH_NDEF_FORMATABLE)
        val handle = AndroidNfcTagHandle(card, ScannedTag("04A1B20CD4E506", emptyList()))

        assertThat(gateway.writeFocusTag(handle, uri)).isEqualTo(WriteResult.Failed(WriteFailure.NOT_NDEF))
    }

    private fun shadowAdapter(): ShadowNfcAdapter = shadowOf(NfcAdapter.getDefaultAdapter(context))

    private fun setAdapterEnabled(enabled: Boolean) {
        shadowAdapter().setEnabled(enabled)
        context.sendBroadcast(Intent(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun ndefIntent(tag: Tag, message: NdefMessage) = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
        .putExtra(NfcAdapter.EXTRA_TAG, tag)
        .putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf(message))

    /** `Tag.createMockTag` is hidden but present in android-all; NFC-A only by default, so no NDEF tech. */
    private fun mockTag(vararg techs: Int = intArrayOf(TECH_NFC_A)): Tag = ReflectionHelpers.callStaticMethod(
        Tag::class.java,
        "createMockTag",
        ClassParameter.from(ByteArray::class.java, uid),
        ClassParameter.from(IntArray::class.java, techs),
        ClassParameter.from(Array<Bundle>::class.java, Array(techs.size) { Bundle() }),
        ClassParameter.from(Long::class.javaPrimitiveType, 0L),
    )

    private companion object {
        /** Hidden `TagTechnology` constants. */
        const val TECH_NFC_A = 1
        const val TECH_ISO_DEP = 3
        const val TECH_NDEF_FORMATABLE = 7
        const val TECH_MIFARE_CLASSIC = 8
    }
}
