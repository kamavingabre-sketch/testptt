package com.diy.walkietalkie

import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketManager {

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    var onAudioReceived: ((ByteArray) -> Unit)? = null
    var onInfoMessage: ((String) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: ((String) -> Unit)? = null

    fun connect(serverUrl: String, roomId: String, name: String) {
        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                // Kirim join message
                val joinMsg = JSONObject().apply {
                    put("type", "join")
                    put("room", roomId)
                    put("name", name)
                }
                ws.send(joinMsg.toString())
                onConnected?.invoke()
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                onAudioReceived?.invoke(bytes.toByteArray())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    if (msg.getString("type") == "info") {
                        onInfoMessage?.invoke(msg.getString("message"))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                onDisconnected?.invoke("Connection failed: ${t.message}")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                onDisconnected?.invoke("Disconnected: $reason")
            }
        })
    }

    fun sendAudio(data: ByteArray) {
        webSocket?.send(ByteString.of(*data))
    }

    fun sendPttStart() {
        webSocket?.send(JSONObject().apply { put("type", "ptt_start") }.toString())
    }

    fun sendPttStop() {
        webSocket?.send(JSONObject().apply { put("type", "ptt_stop") }.toString())
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    fun isConnected(): Boolean = webSocket != null
}
