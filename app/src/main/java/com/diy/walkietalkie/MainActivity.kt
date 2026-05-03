package com.diy.walkietalkie

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.diy.walkietalkie.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var service: WalkieTalkieService? = null
    private var isBound = false

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as WalkieTalkieService.LocalBinder).getService()
            isBound = true

            service?.onConnectionChanged = { connected ->
                runOnUiThread { updateConnectionUI(connected) }
            }
            service?.onInfoMessage = { msg ->
                runOnUiThread { addLog(msg) }
            }

            updateConnectionUI(service?.isConnected() ?: false)
            addLog("✅ Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        setupUI()
        startAndBindService()
    }

    private fun startAndBindService() {
        val intent = Intent(this, WalkieTalkieService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setupUI() {
        binding.btnConnect.setOnClickListener {
            if (service?.isConnected() == true) {
                stopService(Intent(this, WalkieTalkieService::class.java))
                updateConnectionUI(false)
                addLog("🔌 Disconnected")
            } else {
                startAndBindService()
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnPtt.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (service?.isConnected() == true) {
                        service?.startTalking()
                        binding.btnPtt.alpha = 0.6f
                        binding.tvStatus.text = "🎙️ TALKING..."
                        binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_light))
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    service?.stopTalking()
                    binding.btnPtt.alpha = 1.0f
                    binding.tvStatus.text = "Hold PTT to Talk"
                    binding.tvStatus.setTextColor(getColor(android.R.color.white))
                }
            }
            true
        }

        binding.switchSpeaker.isChecked = true
        service?.setSpeakerphone(true)
        binding.switchSpeaker.setOnCheckedChangeListener { _, isChecked ->
            service?.setSpeakerphone(isChecked)
        }

        updateConnectionUI(false)
    }

    private fun updateConnectionUI(connected: Boolean) {
        binding.btnConnect.text = if (connected) "Disconnect" else "Connect"
        binding.btnPtt.isEnabled = connected
        binding.btnPtt.alpha = if (connected) 1.0f else 0.4f
        binding.tvStatus.text = if (connected) "Hold PTT to Talk" else "Not Connected"
        binding.tvStatus.setTextColor(getColor(android.R.color.white))
    }

    private fun addLog(message: String) {
        val lines = binding.tvLog.text.split("\n").takeLast(10)
        binding.tvLog.text = (lines + message).joinToString("\n")
        binding.scrollLog.post { binding.scrollLog.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onResume() {
        super.onResume()
        if (!isBound) {
            bindService(
                Intent(this, WalkieTalkieService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }
}
