package com.minipay.mobile.chat

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

data class VoiceCallUiState(
    val callId: String? = null,
    val conversationId: String? = null,
    val peerName: String = "语音通话",
    val incoming: Boolean = false,
    val status: String = "IDLE",
    val connectedAt: Long? = null,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val error: String? = null
)

@Singleton
class VoiceCallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ChatApiService,
    private val realtime: CallRealtimeClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(VoiceCallUiState())
    val state = mutableState.asStateFlow()
    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var pendingOffer: SessionDescription? = null
    private val outgoingOfferStarted = AtomicBoolean(false)

    init {
        realtime.connect()
        scope.launch { realtime.events.collect(::handleEvent) }
    }

    fun startOutgoing(conversationId: String, peerName: String) {
        if (mutableState.value.status != "IDLE") return
        mutableState.value = VoiceCallUiState(conversationId = conversationId, peerName = peerName, status = "CREATING")
        scope.launch {
            runCatching { api.createCall(conversationId) }
                .onSuccess { mutableState.value = mutableState.value.copy(callId = it.id, status = it.status) }
                .onFailure { mutableState.value = mutableState.value.copy(status = "FAILED", error = friendly(it)) }
        }
    }

    fun accept() {
        val callId = mutableState.value.callId ?: return
        scope.launch {
            runCatching { setupPeer(); api.acceptCall(callId) }
                .onSuccess { mutableState.value = mutableState.value.copy(status = "ACCEPTED", connectedAt = System.currentTimeMillis()); pendingOffer?.let(::answerOffer) }
                .onFailure { mutableState.value = mutableState.value.copy(status = "FAILED", error = friendly(it)) }
        }
    }
    fun reject() = finishCommand { api.rejectCall(it) }
    fun hangUp() = finishCommand { id -> if (mutableState.value.status == "RINGING") api.cancelCall(id) else api.endCall(id) }
    fun toggleMute() { val muted = !mutableState.value.muted; audioTrack?.setEnabled(!muted); mutableState.value = mutableState.value.copy(muted = muted) }
    fun toggleSpeaker() { setSpeaker(!mutableState.value.speaker) }
    fun reset() { releasePeer(); mutableState.value = VoiceCallUiState() }

    private fun finishCommand(command: suspend (String) -> VoiceCallResponse) {
        val id = mutableState.value.callId ?: return
        scope.launch { runCatching { command(id) }; releasePeer(); mutableState.value = mutableState.value.copy(status = "ENDED") }
    }

    private suspend fun handleEvent(event: RealtimeEvent) {
        when (event.type) {
            "CALL_INVITE" -> {
                if (mutableState.value.status == "IDLE") {
                    mutableState.value = VoiceCallUiState(event.callId, event.conversationId, "语音来电", true, "RINGING")
                    CallRealtimeService.showIncoming(context, event.callId.orEmpty(), event.conversationId.orEmpty())
                }
            }
            "CALL_STATUS" -> {
                if (event.callId != mutableState.value.callId) return
                val status = event.payload["status"]?.jsonPrimitive?.content ?: return
                mutableState.value = mutableState.value.copy(status = status, connectedAt = if (status == "ACCEPTED") mutableState.value.connectedAt ?: System.currentTimeMillis() else mutableState.value.connectedAt)
                if (status == "ACCEPTED" && !mutableState.value.incoming && outgoingOfferStarted.compareAndSet(false, true)) { setupPeer(); createOffer() }
                if (status in listOf("REJECTED", "CANCELLED", "MISSED", "ENDED")) releasePeer()
            }
            "WEBRTC_OFFER" -> if (event.callId == mutableState.value.callId) {
                val offer = SessionDescription(SessionDescription.Type.OFFER, event.payload["sdp"]?.jsonPrimitive?.content.orEmpty())
                pendingOffer = offer
                if (mutableState.value.status == "ACCEPTED") answerOffer(offer)
            }
            "WEBRTC_ANSWER" -> if (event.callId == mutableState.value.callId) peer?.setRemoteDescription(observer(), SessionDescription(SessionDescription.Type.ANSWER, event.payload["sdp"]?.jsonPrimitive?.content.orEmpty()))
            "ICE_CANDIDATE" -> if (event.callId == mutableState.value.callId) peer?.addIceCandidate(IceCandidate(event.payload["sdpMid"]?.jsonPrimitive?.content, event.payload["sdpMLineIndex"]?.jsonPrimitive?.int ?: 0, event.payload["candidate"]?.jsonPrimitive?.content.orEmpty()))
        }
    }

    private suspend fun setupPeer() {
        if (peer != null) return
        if (factory == null) {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
            factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        }
        val config = api.iceServers()
        val servers = config.urls.map { url -> PeerConnection.IceServer.builder(url).apply { if (url.startsWith("turn:")) { setUsername(config.username.orEmpty()); setPassword(config.credential.orEmpty()) } }.createIceServer() }
        audioSource = factory!!.createAudioSource(MediaConstraints())
        audioTrack = factory!!.createAudioTrack("minipay-audio", audioSource).also { it.setEnabled(true) }
        peer = factory!!.createPeerConnection(PeerConnection.RTCConfiguration(servers), peerObserver())?.also { it.addTrack(audioTrack) }
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeaker(false)
        CallRealtimeService.showActive(context)
    }

    private fun createOffer() { peer?.createOffer(object : SimpleSdpObserver() { override fun onCreateSuccess(value: SessionDescription) { peer?.setLocalDescription(observer(), value); signalSdp("WEBRTC_OFFER", value) } }, MediaConstraints()) }
    private fun answerOffer(offer: SessionDescription) { pendingOffer = null; peer?.setRemoteDescription(object : SimpleSdpObserver() { override fun onSetSuccess() { peer?.createAnswer(object : SimpleSdpObserver() { override fun onCreateSuccess(value: SessionDescription) { peer?.setLocalDescription(observer(), value); signalSdp("WEBRTC_ANSWER", value) } }, MediaConstraints()) } }, offer) }
    private fun signalSdp(type: String, description: SessionDescription) { mutableState.value.callId?.let { realtime.sendSignal(type, it, buildJsonObject { put("sdp", description.description) }) } }
    private fun peerObserver() = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) { mutableState.value.callId?.let { realtime.sendSignal("ICE_CANDIDATE", it, buildJsonObject { put("sdpMid", candidate.sdpMid ?: ""); put("sdpMLineIndex", candidate.sdpMLineIndex); put("candidate", candidate.sdp) }) } }
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) { if (state == PeerConnection.IceConnectionState.CONNECTED || state == PeerConnection.IceConnectionState.COMPLETED) mutableState.value = mutableState.value.copy(status = "CONNECTED", connectedAt = mutableState.value.connectedAt ?: System.currentTimeMillis()); if (state == PeerConnection.IceConnectionState.DISCONNECTED) scope.launch { delay(15_000); if (peer?.iceConnectionState() == PeerConnection.IceConnectionState.DISCONNECTED) hangUp() } }
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {} ; override fun onIceConnectionReceivingChange(p0: Boolean) {} ; override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {} ; override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {} ; override fun onAddStream(p0: MediaStream?) {} ; override fun onRemoveStream(p0: MediaStream?) {} ; override fun onDataChannel(p0: DataChannel?) {} ; override fun onRenegotiationNeeded() {} ; override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
    }
    private fun observer() = SimpleSdpObserver()
    private fun setSpeaker(enabled: Boolean) { val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager; @Suppress("DEPRECATION") manager.isSpeakerphoneOn = enabled; mutableState.value = mutableState.value.copy(speaker = enabled) }
    private fun releasePeer() { peer?.close(); peer?.dispose(); peer = null; audioTrack?.dispose(); audioTrack = null; audioSource?.dispose(); audioSource = null; outgoingOfferStarted.set(false); val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager; manager.mode = AudioManager.MODE_NORMAL; @Suppress("DEPRECATION") manager.isSpeakerphoneOn = false; CallRealtimeService.showOnline(context) }
    private fun friendly(error: Throwable) = when ((error as? ChatApiException)?.status) { 409 -> "对方不在线或正在通话"; else -> "语音通话连接失败" }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(value: SessionDescription) {} ; override fun onSetSuccess() {} ; override fun onCreateFailure(error: String?) {} ; override fun onSetFailure(error: String?) {}
}
