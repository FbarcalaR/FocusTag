package io.github.fbarcalar.focustag.nfc

import android.app.Activity
import android.os.Bundle

/** Stub declared in the manifest by T1; T3 makes it validate scans and call the engine (D-13). */
class NfcTriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
