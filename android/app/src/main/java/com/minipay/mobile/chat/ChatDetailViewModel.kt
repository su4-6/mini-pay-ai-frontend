package com.minipay.mobile.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val voiceRecorder: VoiceRecorder,
    private val voicePlayback: VoicePlaybackManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conversationId: String =
        savedStateHandle.get<String>("conversationId") ?: ""

    private val fallbackConversationName: String =
        savedStateHandle.get<String>("name") ?: ""

    val conversationName: StateFlow<String> =
        repository.observeConversationName(conversationId, fallbackConversationName)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = fallbackConversationName
            )

    val messages: StateFlow<List<ChatMessage>> = repository.observeMessages(conversationId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()
    private var refreshJob: Job? = null

    private val _recording = MutableStateFlow(VoiceRecordingState())
    val recording: StateFlow<VoiceRecordingState> = _recording.asStateFlow()
    private val _playingVoiceId = MutableStateFlow<Long?>(null)
    val playingVoiceId: StateFlow<Long?> = _playingVoiceId.asStateFlow()
    private val _mediaPlaybackUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val mediaPlaybackUrls: StateFlow<Map<String, String>> = _mediaPlaybackUrls.asStateFlow()
    private val _mediaLoadingIds = MutableStateFlow<Set<String>>(emptySet())
    val mediaLoadingIds: StateFlow<Set<String>> = _mediaLoadingIds.asStateFlow()
    private val _mediaFailedIds = MutableStateFlow<Set<String>>(emptySet())
    val mediaFailedIds: StateFlow<Set<String>> = _mediaFailedIds.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true || _sending.value) return
        refreshJob = viewModelScope.launch {
            repository.syncMessages(conversationId)
            repository.clearUnread(conversationId)
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            _sending.value = true
            _sendError.value = null
            val result = repository.sendMessage(
                conversationId = conversationId,
                content = content,
                messageType = "Text"
            )
            if (result == null) {
                _sendError.value = "发送失败，请重试"
            }
            _sending.value = false
        }
    }

    fun clearError() {
        _sendError.value = null
    }

    fun startRecording() {
        if (_recording.value.active) return
        runCatching { voiceRecorder.start() }
            .onSuccess { _recording.value = VoiceRecordingState(active = true) }
            .onFailure { _sendError.value = "无法开始录音" }
    }

    fun updateRecordingCancel(cancel: Boolean) {
        if (_recording.value.active) _recording.value = _recording.value.copy(cancel = cancel)
    }

    fun finishRecording(cancel: Boolean = _recording.value.cancel) {
        if (!_recording.value.active) return
        _recording.value = VoiceRecordingState()
        if (cancel) { voiceRecorder.cancel(); return }
        val recording = runCatching { voiceRecorder.stop() }.getOrElse {
            voiceRecorder.cancel(); _sendError.value = "录音失败"; return
        }
        if (recording.durationMs < 1000) { recording.file.delete(); _sendError.value = "说话时间太短"; return }
        viewModelScope.launch {
            _sending.value = true
            val sent = repository.sendVoiceMessage(conversationId, recording.file, recording.durationMs)
            recording.file.delete()
            sent.exceptionOrNull()?.let { _sendError.value = voiceSendErrorMessage(it) }
            _sending.value = false
        }
    }

    fun playVoice(message: ChatMessage) {
        val mediaId = message.voiceMediaId ?: return
        if (_playingVoiceId.value == message.id) { voicePlayback.stop(); _playingVoiceId.value = null; return }
        viewModelScope.launch {
            playVoiceAttempt(message, allowRetry = true)
        }
    }

    private suspend fun playVoiceAttempt(message: ChatMessage, allowRetry: Boolean) {
        val mediaId = message.voiceMediaId ?: return
        val url = repository.voicePlaybackUrl(mediaId)
        if (url == null) {
            _playingVoiceId.value = null
            _sendError.value = "语音加载失败，请重试"
            return
        }
        _playingVoiceId.value = message.id
        voicePlayback.play(
            url = url,
            onFinished = { _playingVoiceId.value = null },
            onError = {
                _playingVoiceId.value = null
                if (allowRetry) {
                    viewModelScope.launch { playVoiceAttempt(message, allowRetry = false) }
                } else {
                    _sendError.value = "语音播放失败，请重试"
                }
            }
        )
    }

    fun sendMedia(media: PreparedChatMedia) {
        if (_sending.value) {
            media.file.delete()
            return
        }
        viewModelScope.launch {
            _sending.value = true
            _sendError.value = null
            try {
                val result = repository.sendMediaMessage(conversationId, media)
                result.onSuccess { message ->
                    message.mediaId?.let { mediaId ->
                        _mediaPlaybackUrls.value = _mediaPlaybackUrls.value +
                            (mediaId to media.file.toURI().toString())
                        _mediaFailedIds.value = _mediaFailedIds.value - mediaId
                    }
                }.onFailure {
                    media.file.delete()
                    _sendError.value = chatMediaSendErrorMessage(it)
                }
            } finally {
                _sending.value = false
            }
        }
    }

    fun ensureMediaPlayback(message: ChatMessage, forceRefresh: Boolean = false) {
        val mediaId = message.mediaId ?: return
        if (!forceRefresh && _mediaPlaybackUrls.value.containsKey(mediaId)) return
        viewModelScope.launch {
            _mediaLoadingIds.value = _mediaLoadingIds.value + mediaId
            _mediaFailedIds.value = _mediaFailedIds.value - mediaId
            val url = repository.chatMediaPlaybackUrl(mediaId, forceRefresh)
            if (url != null) {
                _mediaPlaybackUrls.value = _mediaPlaybackUrls.value + (mediaId to url)
            } else {
                _mediaFailedIds.value = _mediaFailedIds.value + mediaId
            }
            _mediaLoadingIds.value = _mediaLoadingIds.value - mediaId
        }
    }

    fun markMediaPlaybackFailed(message: ChatMessage) {
        val mediaId = message.mediaId ?: return
        _mediaPlaybackUrls.value = _mediaPlaybackUrls.value - mediaId
        _mediaLoadingIds.value = _mediaLoadingIds.value - mediaId
        _mediaFailedIds.value = _mediaFailedIds.value + mediaId
    }

    override fun onCleared() {
        voiceRecorder.cancel(); voicePlayback.stop(); super.onCleared()
    }

    fun saveRemark(remark: String) {
        viewModelScope.launch { repository.saveRemark(conversationId, remark) }
    }

    fun deleteFriend(onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(repository.deleteFriendAndConversation(conversationId)) }
    }

    fun withTransferTarget(onReady: (ChatTransferTarget) -> Unit) {
        if (conversationId.startsWith("group_")) return
        viewModelScope.launch {
            repository.findTransferTarget(conversationId, conversationName.value)
                ?.let(onReady)
                ?: run { _sendError.value = "无法获取好友转账账号，请稍后重试" }
        }
    }
}

data class VoiceRecordingState(val active: Boolean = false, val cancel: Boolean = false)
