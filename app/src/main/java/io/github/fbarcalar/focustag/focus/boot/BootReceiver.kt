package io.github.fbarcalar.focustag.focus.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

/** Stub declared in the manifest by T1; T2 makes it reconcile on boot (D-43). */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
