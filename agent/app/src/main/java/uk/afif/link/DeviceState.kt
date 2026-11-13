package uk.afif.link

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface

/** Collects the telemetry payload published on the state topic. */
object DeviceState {

    fun snapshot(c: Context): JSONObject {
        val o = JSONObject()
        o.put("device", Prefs.deviceId(c))
        o.put("model", Build.MODEL)
        o.put("manufacturer", Build.MANUFACTURER)
        o.put("android", Build.VERSION.RELEASE)
        o.put("sdk", Build.VERSION.SDK_INT)
        o.put("ts", System.currentTimeMillis())

        battery(c)?.let { b ->
            o.put("battery", b.first)
            o.put("charging", b.second)
        }

        o.put("network", networkType(c))
        o.put("ip", localIp())
        o.put("uptime", SystemClock.elapsedRealtime() / 1000)

        totalFree()?.let { f ->
            o.put("storageFree", human(f))
            o.put("storageFreeBytes", f)
        }
        return o
    }

    private fun battery(c: Context): Pair<Int, Boolean>? {
        val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return Pair((level * 100 / scale), charging)
    }

    private fun networkType(c: Context): String {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return "unknown"
        val n = cm.activeNetwork ?: return "offline"
        val caps = cm.getNetworkCapabilities(n) ?: return "offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
    }

    fun localIp(): String? {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun totalFree(): Long? {
        return try {
            val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            null
        }
    }

    fun human(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
        return String.format("%.1f %s", v, units[i])
    }
}
