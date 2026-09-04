package com.minipay.mobile.chat

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoicePlaybackManager @Inject constructor(@ApplicationContext private val context: Context) {
    private var player: MediaPlayer? = null
    fun play(url: String, onFinished: () -> Unit, onError: () -> Unit) {
        stop()
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            setDataSource(context, android.net.Uri.parse(url))
            setOnCompletionListener { stop(); onFinished() }
            setOnErrorListener { _, _, _ -> stop(); onError(); true }
            setOnPreparedListener { it.start() }
            prepareAsync()
        }
    }
    fun stop() { player?.runCatching { stop() }; player?.release(); player = null }
}
