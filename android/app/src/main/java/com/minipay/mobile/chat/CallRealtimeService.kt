package com.minipay.mobile.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.minipay.mobile.R
import com.minipay.mobile.auth.AuthRepository
import com.minipay.mobile.finance.CollectionReceiptAnnouncementFilter
import com.minipay.mobile.finance.FinanceRepository
import com.minipay.mobile.ui.chat.VoiceCallActivity
import com.minipay.mobile.voice.SpeechChannel
import com.minipay.mobile.voice.SpeechBlocker
import com.minipay.mobile.voice.SpeechOutput
import com.minipay.mobile.voice.VoiceSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CallRealtimeService : Service() {
    @Inject lateinit var realtime: CallRealtimeClient
    @Inject lateinit var calls: VoiceCallManager
    @Inject lateinit var chats: ChatRepository
    @Inject lateinit var finance: FinanceRepository
    @Inject lateinit var auth: AuthRepository
    @Inject lateinit var voiceSettings: VoiceSettings
    @Inject lateinit var speechOutput: SpeechOutput
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val receiptFilter = CollectionReceiptAnnouncementFilter()
    private val deferredReceipts = ArrayDeque<Pair<String, String>>()

    override fun onCreate() {
        super.onCreate(); createChannels(); realtime.connect()
        scope.launch { realtime.events.collect { if (it.type == "CHAT_MESSAGE_CREATED") it.conversationId?.let { id -> chats.syncMessages(id) } } }
        scope.launch {
            calls.state.collect { state ->
                val active = callActive(state.status)
                speechOutput.setBlocked(SpeechBlocker.VOICE_CALL, active)
                if (!active) {
                    drainDeferredReceipts()
                }
            }
        }
        scope.launch {
            combine(auth.currentUserId, voiceSettings.receiptSpeechEnabled) { userId, enabled ->
                userId != null && enabled
            }.collectLatest { enabled ->
                if (enabled) {
                    // Warm the Chinese TTS engine before the first payment arrives. On a cold
                    // emulator the language model can otherwise add more than ten seconds.
                    speechOutput.prepare()
                    collectReceiptAnnouncements()
                }
                else {
                    synchronized(deferredReceipts) { deferredReceipts.clear() }
                    speechOutput.stop(SpeechChannel.RECEIPT)
                }
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent
        when (command?.action ?: ACTION_ONLINE) {
            ACTION_ACCEPT -> calls.accept()
            ACTION_REJECT -> calls.reject()
            ACTION_HANG_UP -> calls.hangUp()
            ACTION_INCOMING -> startAsForeground(incomingNotification(command?.getStringExtra(EXTRA_CALL_ID).orEmpty(), command?.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty()), false)
            ACTION_ACTIVE -> startAsForeground(activeNotification(), true)
            else -> startAsForeground(onlineNotification(), false)
        }
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        scope.cancel()
        realtime.disconnect()
        speechOutput.stop(SpeechChannel.RECEIPT)
        speechOutput.setBlocked(SpeechBlocker.VOICE_CALL, false)
        super.onDestroy()
    }

    private suspend fun collectReceiptAnnouncements() {
        var reconnectAttempt = 0
        while (currentCoroutineContext().isActive) {
            try {
                finance.collectionReceiptEvents().collect { event ->
                    receiptFilter.announcement(event)?.let { announcement ->
                        reconnectAttempt = 0
                        announceOrDefer(event.eventId, announcement)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The access token is refreshed by FinanceRepository on the next connection.
                // Wallet bills remain authoritative while this best-effort stream reconnects.
            }
            reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(4)
            delay(2_000L shl (reconnectAttempt - 1))
        }
    }

    private fun announceOrDefer(eventId: String, announcement: String) {
        if (!callActive(calls.state.value.status)) {
            speechOutput.speakReceipt(eventId, announcement)
            return
        }
        synchronized(deferredReceipts) {
            if (deferredReceipts.size >= MAX_DEFERRED_RECEIPTS) deferredReceipts.removeFirst()
            deferredReceipts.addLast(eventId to announcement)
        }
    }

    private fun drainDeferredReceipts() {
        if (!voiceSettings.receiptSpeechEnabled.value) return
        val receipts = synchronized(deferredReceipts) {
            buildList { while (deferredReceipts.isNotEmpty()) add(deferredReceipts.removeFirst()) }
        }
        receipts.forEach { (eventId, announcement) -> speechOutput.speakReceipt(eventId, announcement) }
    }

    private fun callActive(status: String): Boolean = status !in INACTIVE_CALL_STATUSES

    private fun startAsForeground(notification: android.app.Notification, microphone: Boolean) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, if (microphone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        else startForeground(NOTIFICATION_ID, notification)
    }
    private fun onlineNotification() = NotificationCompat.Builder(this, CHANNEL_ONLINE).setSmallIcon(R.drawable.minipay_logo).setContentTitle("MiniPay 实时提醒在线").setContentText("可在后台接收消息、语音来电和收款提醒").setOngoing(true).setSilent(true).build()
    private fun incomingNotification(callId: String, conversationId: String): android.app.Notification {
        val activity = PendingIntent.getActivity(this, 100, VoiceCallActivity.intent(this, conversationId, "语音来电", true).putExtra(EXTRA_CALL_ID, callId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val accept = PendingIntent.getService(this, 101, Intent(this, CallRealtimeService::class.java).setAction(ACTION_ACCEPT), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val reject = PendingIntent.getService(this, 102, Intent(this, CallRealtimeService::class.java).setAction(ACTION_REJECT), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val person = Person.Builder().setName("MiniPay 联系人").setImportant(true).build()
        return NotificationCompat.Builder(this, CHANNEL_CALLS).setSmallIcon(R.drawable.minipay_logo).setContentTitle("语音来电").setContentText("点击接听").setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_CALL).setOngoing(true).setContentIntent(activity).setFullScreenIntent(activity, true).setStyle(NotificationCompat.CallStyle.forIncomingCall(person, reject, accept)).build()
    }
    private fun activeNotification(): android.app.Notification {
        val activity = PendingIntent.getActivity(this, 103, VoiceCallActivity.intent(this, calls.state.value.conversationId.orEmpty(), calls.state.value.peerName, calls.state.value.incoming), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val hangup = PendingIntent.getService(this, 104, Intent(this, CallRealtimeService::class.java).setAction(ACTION_HANG_UP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_CALLS).setSmallIcon(R.drawable.minipay_logo).setContentTitle("语音通话中").setContentText(calls.state.value.peerName).setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_HIGH).setOngoing(true).setContentIntent(activity).addAction(0, "挂断", hangup).build()
    }
    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ONLINE, "实时提醒在线状态", NotificationManager.IMPORTANCE_LOW).apply { description = "后台消息、语音来电和个人收款提醒" })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_CALLS, "语音通话", NotificationManager.IMPORTANCE_HIGH).apply { description = "语音来电和通话状态" })
    }

    companion object {
        private const val NOTIFICATION_ID = 4102
        private const val CHANNEL_ONLINE = "minipay_realtime"
        private const val CHANNEL_CALLS = "minipay_calls"
        private const val MAX_DEFERRED_RECEIPTS = 20
        private val INACTIVE_CALL_STATUSES = setOf("IDLE", "ENDED", "FAILED", "REJECTED", "CANCELLED", "MISSED")
        const val EXTRA_CALL_ID = "callId"; const val EXTRA_CONVERSATION_ID = "conversationId"
        private const val ACTION_ONLINE = "minipay.call.ONLINE"; private const val ACTION_INCOMING = "minipay.call.INCOMING"; private const val ACTION_ACTIVE = "minipay.call.ACTIVE"
        private const val ACTION_ACCEPT = "minipay.call.ACCEPT"; private const val ACTION_REJECT = "minipay.call.REJECT"; private const val ACTION_HANG_UP = "minipay.call.HANG_UP"
        fun showOnline(context: Context) = start(context, ACTION_ONLINE)
        fun showActive(context: Context) = start(context, ACTION_ACTIVE)
        fun showIncoming(context: Context, callId: String, conversationId: String) = start(context, ACTION_INCOMING, callId, conversationId)
        private fun start(context: Context, action: String, callId: String? = null, conversationId: String? = null) {
            val intent = Intent(context, CallRealtimeService::class.java).setAction(action).putExtra(EXTRA_CALL_ID, callId).putExtra(EXTRA_CONVERSATION_ID, conversationId)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
