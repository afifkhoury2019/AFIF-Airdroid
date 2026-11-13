package uk.afif.link

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannels()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CH_SERVICE, getString(R.string.channel_service), NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_EVENTS, getString(R.string.channel_events), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    companion object {
        const val CH_SERVICE = "afif.link.service"
        const val CH_EVENTS = "afif.link.events"

        lateinit var instance: App
            private set
    }
}
