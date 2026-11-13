package uk.afif.link

import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import org.json.JSONArray
import org.json.JSONObject

/** SMS read/send helpers. Requires READ_SMS / SEND_SMS. */
object SmsIo {

    fun list(c: Context, limit: Int): JSONArray {
        val out = JSONArray()
        val cols = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.READ)
        val cur = try {
            c.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, cols, null, null,
                Telephony.Sms.DATE + " DESC"
            )
        } catch (e: Exception) {
            return out
        } ?: return out

        cur.use {
            var n = 0
            while (it.moveToNext() && n < limit) {
                out.put(
                    JSONObject()
                        .put("from", it.getString(0) ?: "")
                        .put("body", it.getString(1) ?: "")
                        .put("date", it.getLong(2))
                        .put("read", it.getInt(3) == 1)
                )
                n++
            }
        }
        return out
    }

    fun send(c: Context, to: String, text: String): Boolean {
        return try {
            val mgr = if (Build.VERSION.SDK_INT >= 31) {
                c.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } ?: return false

            val parts = mgr.divideMessage(text)
            if (parts.size > 1) {
                mgr.sendMultipartTextMessage(to, null, parts, null, null)
            } else {
                mgr.sendTextMessage(to, null, text, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
