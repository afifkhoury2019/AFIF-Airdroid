package uk.afif.link

import android.content.Context
import java.util.UUID

/** Small typed wrapper over SharedPreferences. No secrets are ever logged. */
object Prefs {

    private const val FILE = "afif_link"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun broker(c: Context): String = sp(c).getString("broker", "tcp://192.168.1.10:1883") ?: ""
    fun setBroker(c: Context, v: String) = sp(c).edit().putString("broker", v.trim()).apply()

    fun user(c: Context): String = sp(c).getString("user", "") ?: ""
    fun setUser(c: Context, v: String) = sp(c).edit().putString("user", v.trim()).apply()

    fun pass(c: Context): String = sp(c).getString("pass", "") ?: ""
    fun setPass(c: Context, v: String) = sp(c).edit().putString("pass", v).apply()

    fun token(c: Context): String = sp(c).getString("token", "") ?: ""
    fun setToken(c: Context, v: String) = sp(c).edit().putString("token", v).apply()

    fun deviceId(c: Context): String {
        val current = sp(c).getString("device", null)
        if (!current.isNullOrBlank()) return current
        val fresh = "phone-" + UUID.randomUUID().toString().take(6)
        sp(c).edit().putString("device", fresh).apply()
        return fresh
    }

    fun setDeviceId(c: Context, v: String) {
        val clean = v.trim().ifBlank { "phone-" + UUID.randomUUID().toString().take(6) }
        sp(c).edit().putString("device", clean).apply()
    }

    fun autoStart(c: Context): Boolean = sp(c).getBoolean("autostart", true)
    fun setAutoStart(c: Context, v: Boolean) = sp(c).edit().putBoolean("autostart", v).apply()

    fun httpPort(c: Context): Int = sp(c).getInt("port", 8080)
    fun setHttpPort(c: Context, v: Int) = sp(c).edit().putInt("port", v).apply()

    fun topicRoot(c: Context): String = "afif/link/" + deviceId(c)

    fun cmdTopic(c: Context): String = topicRoot(c) + "/cmd"
    fun stateTopic(c: Context): String = topicRoot(c) + "/state"
    fun resTopic(c: Context): String = topicRoot(c) + "/res"
    fun eventTopic(c: Context): String = topicRoot(c) + "/event"
}
