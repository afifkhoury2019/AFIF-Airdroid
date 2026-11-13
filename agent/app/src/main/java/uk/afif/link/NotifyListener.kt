package uk.afif.link

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps a small in-memory ring of recent notifications so Node-RED can pull them.
 * The user must grant notification access in system settings.
 */
class NotifyListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        val extras = n.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return

        val rec = JSONObject()
            .put("pkg", n.packageName)
            .put("title", title)
            .put("text", text)
            .put("when", n.postTime)
        push(rec)
    }

    companion object {
        private const val MAX = 60
        private val ring = ArrayDeque<JSONObject>()

        @Synchronized
        private fun push(o: JSONObject) {
            ring.addFirst(o)
            while (ring.size > MAX) ring.removeLast()
        }

        @Synchronized
        fun dump(limit: Int): JSONArray {
            val arr = JSONArray()
            ring.take(limit.coerceIn(1, MAX)).forEach { arr.put(it) }
            return arr
        }
    }
}
