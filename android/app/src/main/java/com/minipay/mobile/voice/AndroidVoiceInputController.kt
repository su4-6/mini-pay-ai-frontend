package com.minipay.mobile.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.minipay.mobile.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AndroidVoiceInputController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speechOutput: SpeechOutput
) : VoiceInputController, RecognitionListener {
    private val mutableState = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    private val mutableFinalTranscripts = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private var recognizer: SpeechRecognizer? = null
    private var acceptingCallbacks = false
    private var speechOutputBlocked = false

    override val state = mutableState.asStateFlow()
    override val finalTranscripts = mutableFinalTranscripts.asSharedFlow()

    override fun start() {
        debug("start requested")
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            mutableState.value = VoiceInputState.Error("当前设备没有可用的系统语音识别服务")
            return
        }
        cancelRecognizer(resetState = false)
        setSpeechOutputBlocked(true)
        runCatching {
            SpeechRecognizer.createSpeechRecognizer(context).also {
                recognizer = it
                acceptingCallbacks = true
                it.setRecognitionListener(this)
                mutableState.value = VoiceInputState.Listening()
                it.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    // Press-and-hold owns the end of recording. Keep vendor engines from
                    // treating a short pause as an early release and cancelling the gesture.
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1_000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 60_000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 60_000L)
                })
            }
        }.onFailure {
            cancelRecognizer(resetState = false)
            mutableState.value = VoiceInputState.Error("无法启动语音识别，请检查麦克风权限和系统语音设置")
        }
    }

    override fun stop() {
        debug("stop requested state=${mutableState.value::class.simpleName}")
        if (mutableState.value is VoiceInputState.Listening) {
            mutableState.value = VoiceInputState.Processing
            recognizer?.stopListening()
        }
    }

    override fun cancel() {
        debug("cancel requested")
        cancelRecognizer(resetState = true)
    }

    private fun cancelRecognizer(resetState: Boolean) {
        recognizer?.runCatching { cancel() }
        recognizer?.destroy()
        recognizer = null
        acceptingCallbacks = false
        setSpeechOutputBlocked(false)
        if (resetState) mutableState.value = VoiceInputState.Idle
    }

    private fun setSpeechOutputBlocked(blocked: Boolean) {
        if (speechOutputBlocked == blocked) return
        speechOutputBlocked = blocked
        speechOutput.setBlocked(SpeechBlocker.VOICE_INPUT, blocked)
    }

    override fun onResults(results: Bundle?) {
        debug("results received")
        if (!acceptingCallbacks) return
        val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()?.trim().orEmpty()
        cancelRecognizer(resetState = false)
        if (transcript.isEmpty()) {
            mutableState.value = VoiceInputState.Error("没有识别到语音，请重试")
        } else {
            mutableState.value = VoiceInputState.Idle
            mutableFinalTranscripts.tryEmit(transcript)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        if (!acceptingCallbacks) return
        val transcript = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()?.trim().orEmpty()
        if (mutableState.value is VoiceInputState.Listening) {
            mutableState.value = VoiceInputState.Listening(transcript)
        }
    }

    override fun onError(error: Int) {
        debug("recognizer error=$error")
        if (!acceptingCallbacks) return
        cancelRecognizer(resetState = false)
        mutableState.value = VoiceInputState.Error(when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "需要麦克风权限才能使用语音输入"
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别网络不可用，请稍后重试"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别服务正忙，请稍后重试"
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听清，请重试"
            SpeechRecognizer.ERROR_AUDIO -> "麦克风录音失败，请重试"
            else -> "语音识别失败，请稍后重试"
        })
    }

    override fun onReadyForSpeech(params: Bundle?) = debug("ready for speech")
    override fun onBeginningOfSpeech() = debug("speech began")
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    // Some vendor recognizers report end-of-speech during a short pause even while the
    // user is still holding the button. Only stop() (the real finger release) owns the
    // transition to Processing, otherwise Compose would visually release the button early.
    override fun onEndOfSpeech() = debug("vendor end of speech")
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, message)
    }

    private companion object {
        const val LOG_TAG = "MiniPayVoiceInput"
    }
}
