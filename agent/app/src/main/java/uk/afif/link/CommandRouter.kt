package uk.afif.link

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import org.json.JSONObject
import java.io.File

/**
 * Executes one command envelope and returns the response body.
 * Envelope: { "id": "...", "cmd": "info", "args": {...}, "token": "..." }
 */
object CommandRouter {

    fun handle(ctx: Context, envelope: JSONObject): JSONObject {
        val id = envelope.optString("id", "")
        val cmd = envelope.optString("cmd", "").trim().lowercase()
        val args = envelope.optJSONObject("args") ?: JSONObject()

        val expected = Prefs.token(ctx)
        if (expected.isNotBlank() && envelope.optString("token", "") != expected) {
            return reply(id, cmd, false, "bad token", null)
        }

        return try {
            when (cmd) {
                "ping" -> reply(id, cmd, true, "pong", null)
                "info" -> reply(id, cmd, true, null, DeviceState.snapshot(ctx))
                "sms.list" -> if (!SmsIo.AVAILABLE) {
                    reply(id, cmd, false, "SMS is not part of this build; use the -sms flavour", null)
                } else {
                    reply(id, cmd, true, null, JSONObject().put("messages", SmsIo.list(ctx, args.optInt("limit", 20))))
                }
                "sms.send" -> if (!SmsIo.AVAILABLE) {
                    reply(id, cmd, false, "SMS is not part of this build; use the -sms flavour", null)
                } else {
                    val to = args.optString("to", "")
                    val text = args.optString("text", "")
                    if (to.isBlank() || text.isBlank()) {
                        reply(id, cmd, false, "to/text required", null)
                    } else {
                        val sent = SmsIo.send(ctx, to, text)
                        reply(id, cmd, sent, if (sent) null else "send failed", null)
                    }
                }
                "notify.dump" -> reply(id, cmd, true, null, JSONObject().put("notifications", NotifyListener.dump(args.optInt("limit", 20))))
                "file.list" -> reply(id, cmd, true, null, JSONObject().put("entries", listFiles(ctx, args.optString("path", ""))))
                "toast" -> {
                    Toast.makeText(ctx, args.optString("text", "AFIF Link"), Toast.LENGTH_SHORT).show()
                    reply(id, cmd, true, "shown", null)
                }
                "vibrate" -> {
                    val v = ctx.getSystemService(Vibrator::class.java)
                    val ms = args.optLong("ms", 250L)
                    if (Build.VERSION.SDK_INT >= 26) {
                        v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION") v?.vibrate(ms)
                    }
                    reply(id, cmd, true, "ok", null)
                }
                else -> reply(id, cmd, false, "unknown command", null)
            }
        } catch (e: Exception) {
            reply(id, cmd, false, e.message ?: e.javaClass.simpleName, null)
        }
    }

    private fun listFiles(ctx: Context, rel: String): org.json.JSONArray {
        val arr = org.json.JSONArray()
        val root = rootDir(ctx)
        val target = File(root, rel)
        val canonRoot = root.canonicalFile
        val canonTarget = target.canonicalFile
        if (!canonTarget.path.startsWith(canonRoot.path)) return arr

        val kids = canonTarget.listFiles() ?: return arr
        kids.sortedByDescending { it.lastModified() }.forEach { f ->
            arr.put(
                JSONObject()
                    .put("name", f.name)
                    .put("dir", f.isDirectory)
                    .put("size", if (f.isDirectory) 0 else f.length())
                    .put("sizeHuman", if (f.isDirectory) "-" else DeviceState.human(f.length()))
                    .put("modified", f.lastModified())
            )
        }
        return arr
    }

    fun rootDir(ctx: Context): File =
        ctx.getExternalFilesDir(null) ?: File(ctx.filesDir, "shared").apply { mkdirs() }

    private fun reply(id: String, cmd: String, ok: Boolean, message: String?, data: JSONObject?): JSONObject {
        val o = JSONObject()
            .put("id", id)
            .put("cmd", cmd)
            .put("ok", ok)
            .put("ts", System.currentTimeMillis())
        if (message != null) o.put("message", message)
        if (data != null) o.put("data", data)
        return o
    }
}
