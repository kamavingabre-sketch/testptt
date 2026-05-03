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

    private val track = AudioTrack.Builder()
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

    init {
        track.play()
    }

    fun play(data: ByteArray) {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            track.write(data, 0, data.size)
        }
    }

    fun setSpeakerphone(audioManager: AudioManager, enabled: Boolean) {
        audioManager.isSpeakerphoneOn = enabled
        audioManager.mode = if (enabled) AudioManager.MODE_IN_COMMUNICATION
                            else AudioManager.MODE_NORMAL
    }

    fun release() {
        track.stop()
        track.release()
    }
}
