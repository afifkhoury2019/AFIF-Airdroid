package uk.afif.link

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

/**
 * Foreground MQTT bridge.
 *
 *  in : afif/link/<device>/cmd     {"id":"..","cmd":"info","args":{..},"token":".."}
 *  out: afif/link/<device>/state   telemetry every 30 s
 *       afif/link/<device>/res     one reply per command
 */
class LinkService : Service() {

    private var client: MqttAsyncClient? = null
    private var scope: CoroutineScope? = null
    private var telemetryJob: Job? = null
    private var http: FileServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        running = true
        connect()
        return START_STICKY
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- lifecycle
    private fun startForegroundCompat() {
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, App.CH_SERVICE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Device link active")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .setContentIntent(tap)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun shutdown() {
        running = false
        telemetryJob?.cancel()
        telemetryJob = null
        try { http?.stop() } catch (_: Exception) {}
        http = null
        try { client?.disconnectForcibly(0, 0) } catch (_: Exception) {}
        try { client?.close() } catch (_: Exception) {}
        client = null
        scope?.cancel()
        scope = null
        lastStatus = "stopped"
    }

    // ---------------------------------------------------------------- mqtt
    private fun connect() {
        val broker = Prefs.broker(this)
        if (broker.isBlank()) {
            lastStatus = "no broker configured"
            return
        }
        val clientId = "afiflink-" + Prefs.deviceId(this) + "-" +
            System.currentTimeMillis().toString().takeLast(4)

        try {
            val c = MqttAsyncClient(broker, clientId, MemoryPersistence())
            c.setCallback(callback)

            val opts = MqttConnectOptions()
            opts.isAutomaticReconnect = true
            opts.isCleanSession = true
            opts.connectionTimeout = 15
            opts.keepAliveInterval = 30
            Prefs.user(this).takeIf { it.isNotBlank() }?.let { opts.userName = it }
            Prefs.pass(this).takeIf { it.isNotBlank() }?.let { opts.password = it.toCharArray() }

            lastStatus = "connecting…"
            c.connect(opts, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    lastStatus = "connected"
                    subscribe()
                    startTelemetry()
                    startHttp()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    lastStatus = "connect failed: " + (exception?.message ?: "unknown")
                }
            })
            client = c
        } catch (e: Exception) {
            lastStatus = "error: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    private val callback = object : MqttCallbackExtended {
        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            lastStatus = if (reconnect) "reconnected" else "connected"
            subscribe()
            startTelemetry()
            startHttp()
        }

        override fun connectionLost(cause: Throwable?) {
            lastStatus = "link lost: " + (cause?.message ?: "unknown")
        }

        override fun messageArrived(topic: String?, message: MqttMessage?) {
            val body = message?.payload?.toString(Charsets.UTF_8) ?: return
            onCommand(body)
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
    }

    private fun subscribe() {
        try {
            client?.subscribe(Prefs.cmdTopic(this), 0)
            lastStatus = "subscribed " + Prefs.cmdTopic(this)
        } catch (e: Exception) {
            lastStatus = "subscribe failed: " + (e.message ?: "unknown")
        }
    }

    private fun onCommand(body: String) {
        val sc = scope ?: return
        sc.launch {
            val envelope = try {
                JSONObject(body)
            } catch (e: Exception) {
                JSONObject().put("cmd", body.trim()).put("id", "")
            }
            val result = CommandRouter.handle(this@LinkService, envelope)
            publish(Prefs.resTopic(this@LinkService), result.toString())
        }
    }

    private fun publish(topic: String, payload: String) {
        try {
            val c = client ?: return
            if (!c.isConnected) return
            val m = MqttMessage(payload.toByteArray(Charsets.UTF_8))
            m.qos = 0
            m.isRetained = false
            c.publish(topic, m)
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- telemetry
    private fun startTelemetry() {
        if (telemetryJob?.isActive == true) return
        telemetryJob = scope?.launch {
            while (isActive) {
                val state = DeviceState.snapshot(this@LinkService)
                publish(Prefs.stateTopic(this@LinkService), state.toString())

                // Drain queued events (inbound SMS, etc.).
                for (ev in EventOutbox.drain()) {
                    val wrapper = JSONObject()
                        .put("kind", "sms")
                        .put("device", Prefs.deviceId(this@LinkService))
                        .put("at", System.currentTimeMillis())
                        .put("data", ev)
                    publish(Prefs.eventTopic(this@LinkService), wrapper.toString())
                }
                delay(30_000L)
            }
        }
    }

    private fun startHttp() {
        if (http != null) return
        val server = FileServer(this, Prefs.httpPort(this))
        server.start()
        http = server
    }

    companion object {
        const val ACTION_START = "uk.afif.link.START"
        const val ACTION_STOP = "uk.afif.link.STOP"
        private const val NOTIF_ID = 4101

        @Volatile
        var running: Boolean = false

        @Volatile
        var lastStatus: String = "stopped"

        fun start(c: Context) {
            val i = Intent(c, LinkService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i) else c.startService(i)
        }

        fun stop(c: Context) {
            val i = Intent(c, LinkService::class.java).setAction(ACTION_STOP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i) else c.startService(i)
        }
    }
}
