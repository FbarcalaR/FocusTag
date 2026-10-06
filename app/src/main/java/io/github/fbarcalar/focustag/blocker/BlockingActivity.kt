package io.github.fbarcalar.focustag.blocker

import android.app.Activity
import android.os.Bundle

/** Stub declared in the manifest by T1; T5 builds the blocking screen. */
class BlockingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
