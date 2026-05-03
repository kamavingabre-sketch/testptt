package com.diy.walkietalkie

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class WalkieTalkieService : Service() {

    companion object {
        const val CHANNEL_ID = "walkie_talkie_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "ACTION_STOP"
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    lateinit var wsManager: WebSocketManager private set
    lateinit var player: AudioPlayer private set
    lateinit var recorder: AudioRecorder private set

    var onInfoMessage: ((String) -> Unit)? = null
    var onConnectionChanged: ((Boolean) -> Unit)? = null

    private var isConnected = false

    inner class LocalBinder : Binder() {
        fun getService(): WalkieTalkieService = this@WalkieTalkieService
    }

    override fun onCreate() {
        super.onCreate()

        // Buat notification channel dan panggil startForeground SESEGERA mungkin
        // di onCreate (bukan onStartCommand) agar tidak timeout di Android 8+
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Starting..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Starting..."))
        }

        // Inisialisasi komponen audio setelah startForeground
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

        wsManager.onConfigUpdated = { roomId ->
            ConfigManager.saveRoomId(this, roomId)
            onInfoMessage?.invoke("🔄 Config updated, room: $roomId")
            reconnect()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            disconnect()
            stopSelf()
            return START_NOT_STICKY
        }

        // startForeground sudah dipanggil di onCreate, tinggal connect
        connectWithFetchedConfig()
        return START_STICKY
    }

    private fun connectWithFetchedConfig() {
        val serverUrl = ConfigManager.getServerUrl(this)
        if (serverUrl.isBlank()) {
            updateNotification("No server URL — open app to configure")
            onInfoMessage?.invoke("⚠️ Server URL belum diset")
            return
        }

        serviceScope.launch {
            updateNotification("Fetching config...")

            val result = ConfigManager.fetchConfig(serverUrl)
            result.fold(
                onSuccess = { json ->
                    val roomId = json.optString("room_id", ConfigManager.getRoomId(this@WalkieTalkieService))
                    ConfigManager.saveRoomId(this@WalkieTalkieService, roomId)
                    onInfoMessage?.invoke("✅ Config fetched, room: $roomId")
                    val name = ConfigManager.getUserName(this@WalkieTalkieService)
                    wsManager.connect(serverUrl, roomId, name)
                },
                onFailure = { e ->
                    onInfoMessage?.invoke("⚠️ Using local config: ${e.message}")
                    val roomId = ConfigManager.getRoomId(this@WalkieTalkieService)
                    val name = ConfigManager.getUserName(this@WalkieTalkieService)
                    wsManager.connect(serverUrl, roomId, name)
                }
            )
        }
    }

    private fun reconnect() {
        serviceScope.launch {
            wsManager.disconnect()
            delay(500)
            connectWithFetchedConfig()
        }
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
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            player.setSpeakerphone(am, enabled)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Walkie-Talkie", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Walkie-Talkie active connection"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, WalkieTalkieService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📡 Walkie-Talkie")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(status))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        disconnect()
        player.release()
    }
}
