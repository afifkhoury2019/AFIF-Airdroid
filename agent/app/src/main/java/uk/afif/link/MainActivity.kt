package uk.afif.link

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var broker: EditText
    private lateinit var device: EditText
    private lateinit var token: EditText
    private lateinit var port: EditText
    private lateinit var status: TextView
    private lateinit var log: TextView

    private val ticker = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        broker = findViewById(R.id.edBroker)
        device = findViewById(R.id.edDevice)
        token = findViewById(R.id.edToken)
        port = findViewById(R.id.edPort)
        status = findViewById(R.id.tvStatus)
        log = findViewById(R.id.tvLog)

        broker.setText(Prefs.broker(this))
        device.setText(Prefs.deviceId(this))
        token.setText(Prefs.token(this))
        port.setText(Prefs.httpPort(this).toString())

        findViewById<Button>(R.id.btnStart).setOnClickListener { saveAndStart() }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            LinkService.stop(this)
            append("stop requested")
        }
        findViewById<Button>(R.id.btnNotif).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<Button>(R.id.btnAccess).setOnClickListener {
            append("Log file root: " + CommandRouter.rootDir(this).absolutePath)
        }

        askPermissions()
        startTicker()
    }

    override fun onDestroy() {
        ticker.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun saveAndStart() {
        Prefs.setBroker(this, broker.text.toString())
        Prefs.setDeviceId(this, device.text.toString())
        Prefs.setToken(this, token.text.toString())
        port.text.toString().toIntOrNull()?.let { Prefs.setHttpPort(this, it) }

        append("starting with broker " + Prefs.broker(this))
        LinkService.start(this)
    }

    private fun askPermissions() {
        val want = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) want.add(Manifest.permission.POST_NOTIFICATIONS)
        want.add(Manifest.permission.SEND_SMS)
        want.add(Manifest.permission.READ_SMS)
        want.add(Manifest.permission.RECEIVE_SMS)

        val missing = want.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 7)
        }
    }

    private fun startTicker() {
        ticker.postDelayed(object : Runnable {
            override fun run() {
                status.text = LinkService.lastStatus
                ticker.postDelayed(this, 1000)
            }
        }, 500)
    }

    private fun append(line: String) {
        log.append(line + "\n")
    }
}
