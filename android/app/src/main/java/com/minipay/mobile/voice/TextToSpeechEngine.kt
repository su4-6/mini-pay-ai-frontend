package com.minipay.mobile.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

interface TextToSpeechEngine {
    interface Listener {
        fun onReady()
        fun onUnavailable(message: String)
        fun onStarted(utteranceId: String)
        fun onFinished(utteranceId: String)
        fun onError(utteranceId: String)
    }

    fun speak(channel: SpeechChannel, text: String, utteranceId: String): Boolean
    fun stop()
}

interface TextToSpeechEngineFactory {
    fun create(listener: TextToSpeechEngine.Listener): TextToSpeechEngine
}

@Singleton
class AndroidTextToSpeechEngineFactory @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeechEngineFactory {
    override fun create(listener: TextToSpeechEngine.Listener): TextToSpeechEngine =
        AndroidTextToSpeechEngine(context, listener)
}

private class AndroidTextToSpeechEngine(
    context: Context,
    private val listener: TextToSpeechEngine.Listener
) : TextToSpeechEngine {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var textToSpeech: TextToSpeech? = null

    init {
        textToSpeech = TextToSpeech(context) { status ->
            mainHandler.post { completeInitialization(status) }
        }
    }

    private fun completeInitialization(status: Int) {
        val engine = textToSpeech
        if (status != TextToSpeech.SUCCESS || engine == null) {
            listener.onUnavailable("当前设备没有可用的系统语音朗读服务")
            return
        }
        val language = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            listener.onUnavailable("当前设备没有可用的中文语音朗读服务")
            return
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.let { mainHandler.post { listener.onStarted(it) } }
            }

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { mainHandler.post { listener.onFinished(it) } }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { mainHandler.post { listener.onError(it) } }
            }
        })
        listener.onReady()
    }

    override fun speak(channel: SpeechChannel, text: String, utteranceId: String): Boolean {
        val engine = textToSpeech ?: return false
        engine.setAudioAttributes(attributes(channel))
        return engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId) == TextToSpeech.SUCCESS
    }

    override fun stop() {
        textToSpeech?.stop()
    }

    private fun attributes(channel: SpeechChannel) = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .setUsage(
            if (channel == SpeechChannel.RECEIPT) {
                AudioAttributes.USAGE_NOTIFICATION_EVENT
            } else {
                AudioAttributes.USAGE_ASSISTANT
            }
        )
        .build()
}
