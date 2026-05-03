package com.diy.walkietalkie

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor

class AudioRecorder(private val onData: (ByteArray) -> Unit) {

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        .coerceAtLeast(3200) // Minimal 100ms buffer

    private var recorder: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var isRecording = false
    private var recordThread: Thread? = null

    fun start() {
        if (isRecording) return

        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Optimal untuk PTT
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        // Enable noise suppressor jika tersedia
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(recorder!!.audioSessionId)
            noiseSuppressor?.enabled = true
        }

        recorder?.startRecording()
        isRecording = true

        recordThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    onData(buffer.copyOf(read))
                }
            }
        }.apply { start() }
    }

    fun stop() {
        isRecording = false
        recordThread?.join(500)
        recordThread = null

        noiseSuppressor?.release()
        noiseSuppressor = null

        recorder?.stop()
        recorder?.release()
        recorder = null
    }
}
