package com.minipay.mobile.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.LinkedHashSet
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AndroidSpeechOutput @Inject constructor(
    @ApplicationContext context: Context,
    private val engineFactory: TextToSpeechEngineFactory
) : SpeechOutput, TextToSpeechEngine.Listener {
    private data class SpeechRequest(
        val channel: SpeechChannel,
        val sourceId: String,
        val chunks: List<String>,
        val chunkIndex: Int = 0,
        val utteranceId: String = ""
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val pending = mutableListOf<SpeechRequest>()
    private val spokenAiIds = LinkedHashSet<String>()
    private val spokenReceiptIds = LinkedHashSet<String>()
    private val blockers = mutableSetOf<SpeechBlocker>()
    private val mutableState = MutableStateFlow<SpeechOutputState>(SpeechOutputState.Ready)
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private var engine: TextToSpeechEngine? = null
    private var engineReady = false
    private var current: SpeechRequest? = null
    private var focusRequest: AudioFocusRequest? = null

    override val state = mutableState.asStateFlow()
    override val errors = mutableErrors.asSharedFlow()

    override fun prepare() = post { ensureEngine() }

    override fun speakAi(messageId: String, text: String) =
        post { enqueue(SpeechChannel.AI, messageId, text) }

    override fun speakReceipt(eventId: String, text: String) =
        post { enqueue(SpeechChannel.RECEIPT, eventId, text) }

    private fun enqueue(channel: SpeechChannel, sourceId: String, text: String) {
        val remembered = if (channel == SpeechChannel.AI) spokenAiIds else spokenReceiptIds
        if (!remembered.add(sourceId)) return
        while (remembered.size > MAX_REMEMBERED) remembered.remove(remembered.first())
        val chunks = splitSpeechText(text)
        if (chunks.isEmpty()) return
        val request = SpeechRequest(channel, sourceId, chunks)
        if (channel == SpeechChannel.RECEIPT) {
            val firstAi = pending.indexOfFirst { it.channel == SpeechChannel.AI }
            if (firstAi < 0) pending.add(request) else pending.add(firstAi, request)
            if (current?.channel == SpeechChannel.AI) {
                engine?.stop()
                current = null
                pending.removeAll { it.channel == SpeechChannel.AI }
                abandonAudioFocus()
            }
        } else {
            pending.add(request)
        }
        ensureEngine()
        pump()
    }

    override fun stop(channel: SpeechChannel) = post {
        pending.removeAll { it.channel == channel }
        if (current?.channel == channel) {
            engine?.stop()
            current = null
            abandonAudioFocus()
            mutableState.value = SpeechOutputState.Ready
            pump()
        }
    }

    override fun stopAll() = post { stopAllOnMain() }

    private fun stopAllOnMain() {
        pending.clear()
        current = null
        engine?.stop()
        abandonAudioFocus()
        if (mutableState.value !is SpeechOutputState.Unavailable) {
            mutableState.value = if (engineReady) SpeechOutputState.Ready else SpeechOutputState.Initializing
        }
    }

    override fun setBlocked(blocker: SpeechBlocker, blocked: Boolean) = post {
        if (blocked) blockers.add(blocker) else blockers.remove(blocker)
        if (blocked && current != null) {
            current?.takeIf { it.channel == SpeechChannel.RECEIPT }
                ?.let { pending.add(0, it.copy(chunkIndex = 0, utteranceId = "")) }
            engine?.stop()
            current = null
            abandonAudioFocus()
            mutableState.value = SpeechOutputState.Ready
        }
        if (!blocked) pump()
    }

    private fun ensureEngine() {
        if (engine != null || mutableState.value is SpeechOutputState.Unavailable) return
        mutableState.value = SpeechOutputState.Initializing
        engine = runCatching { engineFactory.create(this) }.getOrElse {
            unavailable("当前设备没有可用的系统语音朗读服务")
            null
        }
    }

    private fun pump() {
        if (!engineReady || blockers.isNotEmpty() || current != null || pending.isEmpty()) return
        val next = pending.removeAt(0)
        if (!requestAudioFocus(next.channel)) {
            mutableErrors.tryEmit("当前无法获取音频播放权限")
            mutableState.value = SpeechOutputState.Ready
            pump()
            return
        }
        val utteranceId = "${next.channel.name.lowercase()}-${UUID.randomUUID()}"
        current = next.copy(utteranceId = utteranceId)
        mutableState.value = SpeechOutputState.Speaking(next.channel)
        if (engine?.speak(next.channel, next.chunks[next.chunkIndex], utteranceId) != true) {
            onError(utteranceId)
        }
    }

    override fun onReady() = post {
        engineReady = true
        mutableState.value = SpeechOutputState.Ready
        pump()
    }

    override fun onUnavailable(message: String) = post { unavailable(message) }

    private fun unavailable(message: String) {
        engineReady = false
        pending.clear()
        current = null
        abandonAudioFocus()
        mutableState.value = SpeechOutputState.Unavailable(message)
        mutableErrors.tryEmit(message)
    }

    override fun onStarted(utteranceId: String) = Unit

    override fun onFinished(utteranceId: String) = post {
        val finished = current?.takeIf { it.utteranceId == utteranceId } ?: return@post
        current = null
        if (finished.chunkIndex + 1 < finished.chunks.size) {
            pending.add(0, finished.copy(chunkIndex = finished.chunkIndex + 1, utteranceId = ""))
        }
        abandonAudioFocus()
        mutableState.value = SpeechOutputState.Ready
        pump()
    }

    override fun onError(utteranceId: String) = post {
        if (current?.utteranceId != utteranceId) return@post
        current = null
        abandonAudioFocus()
        mutableState.value = SpeechOutputState.Ready
        mutableErrors.tryEmit("语音朗读失败，请检查系统语音设置")
        pump()
    }

    private fun requestAudioFocus(channel: SpeechChannel): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes(channel))
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) post { stopAllOnMain() }
            }
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
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

    private fun abandonAudioFocus() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private companion object { const val MAX_REMEMBERED = 200 }
}
