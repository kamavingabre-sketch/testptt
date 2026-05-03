package com.diy.walkietalkie

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

class AudioPlayer {

    companion object {
        const val SAMPLE_RATE = 16000
    }

    private val bufferSize = AudioTrack.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(3200)

    // Lazy init agar tidak crash saat service belum fully started
    private var track: AudioTrack? = null

    private fun getOrCreateTrack(): AudioTrack {
        return track ?: AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also {
                it.play()
                track = it
            }
    }

    fun play(data: ByteArray) {
        try {
            val t = getOrCreateTrack()
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                t.write(data, 0, data.size)
            }
        } catch (e: Exception) {
            // Jika AudioTrack gagal, reset dan coba lagi berikutnya
            track?.release()
            track = null
        }
    }

    fun setSpeakerphone(audioManager: AudioManager, enabled: Boolean) {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            track?.stop()
            track?.release()
            track = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
