package io.github.fbarcalar.focustag.e2e

import android.app.Application
import android.content.Intent
import android.net.Uri
import io.github.fbarcalar.focustag.nfc.NfcTriggerActivity
import io.github.fbarcalar.focustag.nfc.ScannedTag
import io.github.fbarcalar.focustag.testing.FakeNfcGateway
import org.robolectric.Robolectric

/** Delivers [scan] the way Android does for a background tag: an NDEF intent to the trigger activity. */
internal fun deliverScan(app: Application, gateway: FakeNfcGateway, scan: ScannedTag) {
    gateway.enqueueRead(scan)
    val intent = Intent(HarnessTags.ACTION_NDEF_DISCOVERED, Uri.parse(scan.ndefUris.first()))
        .setClass(app, NfcTriggerActivity::class.java)
    Robolectric.buildActivity(NfcTriggerActivity::class.java, intent).setup()
    idleMainLooper()
}
