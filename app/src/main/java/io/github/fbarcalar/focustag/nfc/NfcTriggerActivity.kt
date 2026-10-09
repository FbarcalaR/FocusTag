package io.github.fbarcalar.focustag.nfc

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.fbarcalar.focustag.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Invisible target of the manifest `NDEF_DISCOVERED` filter (D-13) and, through the
 * `IdOnlyScanTrigger` alias, of `TECH_DISCOVERED` for ID-only cards (D-62): validates the scan, toggles
 * focus, toasts, finishes. The toggle runs on the app scope so a destroyed activity cannot cancel
 * it; the activity stays resumed until it is done so the process keeps foreground priority.
 */
@AndroidEntryPoint
class NfcTriggerActivity : ComponentActivity() {
    @Inject
    lateinit var gateway: NfcGateway

    @Inject
    lateinit var processor: TagScanProcessor

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A re-created instance must not process the same intent twice; the first one already did.
        if (savedInstanceState != null) return finish()
        val scan = gateway.readTag(intent) ?: return finish()
        val feedback = CompletableDeferred<ScanFeedback?>()
        appScope.launch { process(scan, feedback) }
        lifecycleScope.launch {
            try {
                feedback.await()?.let { Toast.makeText(applicationContext, it.message, Toast.LENGTH_SHORT).show() }
            } finally {
                finish()
            }
        }
    }

    /** Failures still reach the app scope's handler; the activity is released either way. */
    private suspend fun process(scan: ScannedTag, feedback: CompletableDeferred<ScanFeedback?>) {
        try {
            feedback.complete(processor.process(scan))
        } finally {
            feedback.complete(null)
        }
    }
}
