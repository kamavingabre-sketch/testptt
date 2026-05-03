package com.diy.walkietalkie

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.diy.walkietalkie.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var wsManager: WebSocketManager
    private lateinit var recorder: AudioRecorder
    private lateinit var player: AudioPlayer
    private lateinit var audioManager: AudioManager

    private var isConnected = false

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        wsManager = WebSocketManager()
        player = AudioPlayer()
        recorder = AudioRecorder { data -> wsManager.sendAudio(data) }

        setupWebSocketCallbacks()
        setupUI()
        checkPermissions()
    }

    private fun setupWebSocketCallbacks() {
        wsManager.onConnected = {
            runOnUiThread {
                isConnected = true
                updateConnectionUI(true)
                addLog("✅ Connected to server")
            }
        }

        wsManager.onDisconnected = { reason ->
            runOnUiThread {
                isConnected = false
                updateConnectionUI(false)
                addLog("❌ $reason")
            }
        }

        wsManager.onInfoMessage = { message ->
            runOnUiThread { addLog("ℹ️ $message") }
        }

        wsManager.onAudioReceived = { data ->
            player.play(data)
        }
    }

    private fun setupUI() {
        // Tombol Connect/Disconnect
        binding.btnConnect.setOnClickListener {
            if (isConnected) {
                disconnect()
            } else {
                connect()
            }
        }

        // Tombol Settings
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Tombol PTT — tahan untuk bicara
        binding.btnPtt.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isConnected) startTalking()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isConnected) stopTalking()
                }
            }
            true
        }

        // Toggle speakerphone
        binding.switchSpeaker.setOnCheckedChangeListener { _, isChecked ->
            player.setSpeakerphone(audioManager, isChecked)
        }

        // Default speakerphone ON
        binding.switchSpeaker.isChecked = true
        player.setSpeakerphone(audioManager, true)

        updateConnectionUI(false)
    }

    private fun connect() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val serverUrl = prefs.getString("server_url", "") ?: ""
        val roomId = prefs.getString("room_id", "room-001") ?: "room-001"
        val name = prefs.getString("user_name", "User") ?: "User"

        if (serverUrl.isBlank()) {
            addLog("⚠️ Please set server URL in Settings first")
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        addLog("🔌 Connecting to $serverUrl ...")
        binding.btnConnect.isEnabled = false

        wsManager.connect(serverUrl, roomId, name)

        // Re-enable setelah 5 detik jika gagal
        binding.btnConnect.postDelayed({
            if (!isConnected) {
                binding.btnConnect.isEnabled = true
            }
        }, 5000)
    }

    private fun disconnect() {
        recorder.stop()
        wsManager.disconnect()
        isConnected = false
        updateConnectionUI(false)
        addLog("🔌 Disconnected")
    }

    private fun startTalking() {
        wsManager.sendPttStart()
        recorder.start()
        binding.btnPtt.alpha = 0.6f
        binding.tvStatus.text = "🎙️ TALKING..."
        binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_light))
    }

    private fun stopTalking() {
        recorder.stop()
        wsManager.sendPttStop()
        binding.btnPtt.alpha = 1.0f
        binding.tvStatus.text = "Hold PTT to Talk"
        binding.tvStatus.setTextColor(getColor(android.R.color.white))
    }

    private fun updateConnectionUI(connected: Boolean) {
        binding.btnConnect.isEnabled = true
        binding.btnConnect.text = if (connected) "Disconnect" else "Connect"
        binding.btnPtt.isEnabled = connected
        binding.btnPtt.alpha = if (connected) 1.0f else 0.4f
        binding.tvStatus.text = if (connected) "Hold PTT to Talk" else "Not Connected"

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.tvRoomInfo.text = if (connected) {
            "Room: ${prefs.getString("room_id", "room-001")} | ${prefs.getString("user_name", "User")}"
        } else {
            "Tap Connect to join"
        }
    }

    private fun addLog(message: String) {
        val current = binding.tvLog.text.toString()
        val lines = current.split("\n").takeLast(10) // Simpan 10 baris terakhir
        binding.tvLog.text = (lines + message).joinToString("\n")
        binding.scrollLog.post { binding.scrollLog.fullScroll(View.FOCUS_DOWN) }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            addLog("⚠️ Microphone permission required!")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        player.release()
    }
}
