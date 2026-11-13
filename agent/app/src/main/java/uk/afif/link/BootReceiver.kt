package uk.afif.link

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the link after a reboot when the user enabled auto-start. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Prefs.autoStart(context)) {
            LinkService.start(context)
        }
    }
}
