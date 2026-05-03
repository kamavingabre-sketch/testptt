package com.diy.walkietalkie

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager

class WalkieTalkieService : Service() {

    companion object {
        const val CHANNEL_ID = "walkie_talkie_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "ACTION_STOP"
    }

    private val binder = LocalBinder()

    lateinit var wsManager: WebSocketManager
        private set
    lateinit var player: AudioPlayer
        private set
    lateinit var recorder: AudioRecorder
        private set

    var onInfoMessage: ((String) -> Unit)? = null
    var onConnectionChanged: ((Boolean) -> Unit)? = null

    private var isConnected = false

    inner class LocalBinder : Binder() {
        fun getService(): WalkieTalkieService = this@WalkieTalkieService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        wsManager = WebSocketManager()
        player = AudioPlayer()
        recorder = AudioRecorder { data -> wsManager.sendAudio(data) }

        wsManager.onAudioReceived = { data -> player.play(data) }

        wsManager.onConnected = {
            isConnected = true
            updateNotification("Connected — Hold PTT to talk")
            onConnectionChanged?.invoke(true)
        }

        wsManager.onDisconnected = { reason ->
            isConnected = false
            updateNotification("Disconnected")
            onConnectionChanged?.invoke(false)
            onInfoMessage?.invoke("❌ $reason")
        }

        wsManager.onInfoMessage = { msg ->
            onInfoMessage?.invoke("ℹ️ $msg")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            disconnect()
            stopSelf()
            return START_NOT_STICKY
        }

        // Mulai sebagai foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification("Initializing..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Initializing..."))
        }

        // Auto connect saat service start
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val url = prefs.getString("server_url", "") ?: ""
        val room = prefs.getString("room_id", "room-001") ?: "room-001"
        val name = prefs.getString("user_name", "User") ?: "User"

        if (url.isNotBlank()) {
            wsManager.connect(url, room, name)
        }

        return START_STICKY // Restart otomatis jika sistem kill service
    }

    override fun onBind(intent: Intent): IBinder = binder

    fun startTalking() {
        wsManager.sendPttStart()
        recorder.start()
        updateNotification("🎙️ Talking...")
    }

    fun stopTalking() {
        recorder.stop()
        wsManager.sendPttStop()
        updateNotification("Connected — Hold PTT to talk")
    }

    fun disconnect() {
        recorder.stop()
        wsManager.disconnect()
        isConnected = false
    }

    fun isConnected() = isConnected

    fun setSpeakerphone(enabled: Boolean) {
        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        player.setSpeakerphone(audioManager, enabled)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Walkie-Talkie",
                NotificationManager.IMPORTANCE_LOW // LOW = tidak ada suara notifikasi
            ).apply {
                description = "Walkie-Talkie active connection"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        // Intent buka app saat notifikasi diklik
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        // Intent tombol Stop di notifikasi
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, WalkieTalkieService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📡 Walkie-Talkie")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .setOngoing(true) // Tidak bisa di-dismiss user
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .build()
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        player.release()
    }
}
