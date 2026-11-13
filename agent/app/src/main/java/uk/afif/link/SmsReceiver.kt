package uk.afif.link

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import org.json.JSONObject

/** Republishes inbound SMS to Node-RED and keeps a copy in the notification ring. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val from = messages.firstOrNull()?.originatingAddress ?: ""
        val body = messages.joinToString("") { it.messageBody ?: "" }

        val payload = JSONObject()
            .put("from", from)
            .put("body", body)
            .put("ts", System.currentTimeMillis())

        // The service owns the MQTT client; expose the event through the ring and
        // let the next telemetry cycle carry it. A dedicated outbox is overkill here.
        NotifyListener.dump(1) // touch to keep the class loaded on some OEM ROMs
        EventOutbox.push(payload)
    }
}

/** Tiny in-memory outbox drained by LinkService on each publish cycle. */
object EventOutbox {
    private val items = ArrayDeque<JSONObject>()

    @Synchronized
    fun push(o: JSONObject) {
        items.addFirst(o)
        while (items.size > 50) items.removeLast()
    }

    @Synchronized
    fun drain(): List<JSONObject> {
        val out = items.toList()
        items.clear()
        return out
    }
}
