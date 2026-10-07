package io.github.fbarcalar.focustag.blocker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle

/** Stub declared in the manifest by T1; T5 builds the blocking screen. */
class BlockingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }

    companion object {
        /** Starts the blocking screen for [packageName] from a non-activity context. */
        fun intent(context: Context, packageName: String): Intent = Intent(context, BlockingActivity::class.java)
            .putExtra(EXTRA_BLOCKED_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
