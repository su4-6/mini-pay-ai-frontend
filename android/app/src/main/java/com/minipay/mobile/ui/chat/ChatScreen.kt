package com.minipay.mobile.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minipay.mobile.chat.ChatDetailViewModel
import com.minipay.mobile.chat.ChatMessage
import com.minipay.mobile.chat.ChatTransferTarget
import com.minipay.mobile.chat.ChatCaptureTarget
import com.minipay.mobile.chat.ChatMediaPreparationException
import com.minipay.mobile.chat.MessageType
import com.minipay.mobile.chat.SenderType
import com.minipay.mobile.chat.TransferDirection
import com.minipay.mobile.chat.createChatCaptureTarget
import com.minipay.mobile.chat.prepareChatImage
import com.minipay.mobile.ui.components.UserAvatar
import com.minipay.mobile.ui.theme.MilingBackground
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingIconPrimary
import com.minipay.mobile.ui.theme.MilingIconSecondary
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingPrimarySoft
import com.minipay.mobile.ui.theme.MilingRadii
import com.minipay.mobile.ui.theme.MilingSpacing
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingSurfaceSubtle
import com.minipay.mobile.ui.theme.MilingTextMuted
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.theme.MilingTextSecondary
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun ChatRoute(
    conversationId: String,
    conversationName: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenTransferRecords: (ChatTransferTarget) -> Unit = {},
    onTransfer: (ChatTransferTarget) -> Unit = {},
    onGroupTransfer: () -> Unit = {},
    viewModel: ChatDetailViewModel = hiltViewModel()
) {
    val currentConversationName by viewModel.conversationName.collectAsStateWithLifecycle()
    val displayConversationName = currentConversationName.ifBlank { conversationName }
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val sendError by viewModel.sendError.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val playingVoiceId by viewModel.playingVoiceId.collectAsStateWithLifecycle()
    val mediaPlaybackUrls by viewModel.mediaPlaybackUrls.collectAsStateWithLifecycle()
    val mediaLoadingIds by viewModel.mediaLoadingIds.collectAsStateWithLifecycle()
    val mediaFailedIds by viewModel.mediaFailedIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showImageSource by remember { mutableStateOf(false) }
    var captureTarget by remember { mutableStateOf<ChatCaptureTarget?>(null) }
    var cameraRequested by remember { mutableStateOf(false) }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var micGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        if (!granted) Toast.makeText(context, "需要麦克风权限才能发送语音", Toast.LENGTH_SHORT).show()
    }

    val prepareAndSend: (Uri, java.io.File?) -> Unit = { uri, sourceFile ->
        scope.launch {
            try {
                val prepared = prepareChatImage(context, uri)
                viewModel.sendMedia(prepared)
            } catch (error: Throwable) {
                val message = (error as? ChatMediaPreparationException)?.message ?: "无法处理这个文件"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } finally {
                sourceFile?.delete()
            }
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { prepareAndSend(it, null) }
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val target = captureTarget
        captureTarget = null
        if (saved && target != null) prepareAndSend(target.uri, target.file) else target?.file?.delete()
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        if (!granted) {
            cameraRequested = false
            Toast.makeText(context, "需要相机权限才能直接拍摄", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(cameraGranted, cameraRequested) {
        if (!cameraGranted || !cameraRequested) return@LaunchedEffect
        cameraRequested = false
        runCatching {
            createChatCaptureTarget(context).also { target ->
                captureTarget = target
                takePicture.launch(target.uri)
            }
        }.onFailure {
            captureTarget?.file?.delete()
            captureTarget = null
            Toast.makeText(context, "无法打开系统相机", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(messages.mapNotNull { it.mediaId }) {
        messages.filter { it.messageType == MessageType.Image || it.messageType == MessageType.Video }
            .forEach(viewModel::ensureMediaPlayback)
    }

    LaunchedEffect(sendError) {
        sendError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    ChatScreen(
        conversationId = conversationId,
        conversationName = displayConversationName,
        messages = messages,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onOpenTransferRecords = { viewModel.withTransferTarget(onOpenTransferRecords) },
        onTransfer = {
            if (conversationId.startsWith("group_")) onGroupTransfer()
            else viewModel.withTransferTarget(onTransfer)
        },
        onSend = { text -> viewModel.sendMessage(text) },
        recordingActive = recording.active,
        recordingCancel = recording.cancel,
        playingVoiceId = playingVoiceId,
        onVoiceModeSelected = { if (!micGranted) micPermission.launch(Manifest.permission.RECORD_AUDIO) },
        onStartRecording = { if (micGranted) viewModel.startRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO) },
        onUpdateRecordingCancel = viewModel::updateRecordingCancel,
        onFinishRecording = viewModel::finishRecording,
        onPlayVoice = viewModel::playVoice,
        onStartCall = { context.startActivity(VoiceCallActivity.intent(context, conversationId, displayConversationName, false)) },
        mediaPlaybackUrls = mediaPlaybackUrls,
        mediaLoadingIds = mediaLoadingIds,
        mediaFailedIds = mediaFailedIds,
        onPickImage = { showImageSource = true },
        onRefreshMedia = { viewModel.ensureMediaPlayback(it, forceRefresh = true) },
        onMediaPlaybackError = viewModel::markMediaPlaybackFailed,
        isSending = sending
    )

    if (showImageSource) {
        AlertDialog(
            onDismissRequest = { showImageSource = false },
            title = { Text("发送照片") },
            text = {
                Column {
                    TextButton(onClick = {
                        showImageSource = false
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Outlined.PhotoLibrary, null)
                        Spacer(Modifier.width(12.dp))
                        Text("从相册选择")
                    }
                    TextButton(onClick = {
                        showImageSource = false
                        cameraRequested = true
                        if (!cameraGranted) cameraPermission.launch(Manifest.permission.CAMERA)
                    }) {
                        Icon(Icons.Outlined.CameraAlt, null)
                        Spacer(Modifier.width(12.dp))
                        Text("使用相机拍摄")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showImageSource = false }) { Text("取消") } }
        )
    }
}

@Composable
fun ChatScreen(
    conversationName: String,
    messages: List<ChatMessage>,
    conversationId: String = "",
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenTransferRecords: () -> Unit = {},
    onTransfer: () -> Unit = {},
    onSend: (String) -> Unit,
    recordingActive: Boolean = false,
    recordingCancel: Boolean = false,
    playingVoiceId: Long? = null,
    onVoiceModeSelected: () -> Unit = {},
    onStartRecording: () -> Unit = {},
    onUpdateRecordingCancel: (Boolean) -> Unit = {},
    onFinishRecording: (Boolean) -> Unit = {},
    onPlayVoice: (ChatMessage) -> Unit = {},
    onStartCall: () -> Unit = {},
    mediaPlaybackUrls: Map<String, String> = emptyMap(),
    mediaLoadingIds: Set<String> = emptySet(),
    mediaFailedIds: Set<String> = emptySet(),
    onPickImage: () -> Unit = {},
    onRefreshMedia: (ChatMessage) -> Unit = {},
    onMediaPlaybackError: (ChatMessage) -> Unit = {},
    isSending: Boolean = false
) {
    var input by rememberSaveable { mutableStateOf("") }
    var panelExpanded by rememberSaveable { mutableStateOf(false) }
    var voiceMode by rememberSaveable { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }
    LaunchedEffect(recordingActive) {
        recordingSeconds = 0
        while (recordingActive && recordingSeconds < 60) {
            delay(1000)
            recordingSeconds++
            if (recordingSeconds >= 60) onFinishRecording(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MilingBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            ChatTopBar(
                conversationName = conversationName,
                onBack = onBack,
                onOpenSettings = onOpenSettings,
                showWallet = conversationId.isNotBlank() && !conversationId.startsWith("group_"),
                onOpenTransferRecords = onOpenTransferRecords
            )

            MessageList(
                messages = messages,
                listState = listState,
                playingVoiceId = playingVoiceId,
                onPlayVoice = onPlayVoice,
                mediaPlaybackUrls = mediaPlaybackUrls,
                mediaLoadingIds = mediaLoadingIds,
                mediaFailedIds = mediaFailedIds,
                onRefreshMedia = onRefreshMedia,
                onMediaPlaybackError = onMediaPlaybackError,
                modifier = Modifier.weight(1f)
            )

            ChatComposer(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    val trimmed = input.trim()
                    if (trimmed.isNotEmpty() && !isSending) {
                        onSend(trimmed)
                        input = ""
                    }
                },
                onTogglePanel = { panelExpanded = !panelExpanded },
                voiceMode = voiceMode,
                onToggleVoiceMode = {
                    voiceMode = !voiceMode
                    panelExpanded = false
                    if (voiceMode) onVoiceModeSelected()
                },
                onStartRecording = onStartRecording,
                onUpdateRecordingCancel = onUpdateRecordingCancel,
                onFinishRecording = onFinishRecording,
                isSending = isSending
            )

            if (panelExpanded) {
                ChatActionPanel(
                    onPickImage = { panelExpanded = false; onPickImage() },
                    showTransfer = conversationId.isNotBlank(),
                    onTransfer = { panelExpanded = false; onTransfer() },
                    showVoiceCall = conversationId.isNotBlank() && !conversationId.startsWith("group_"),
                    onVoiceCall = { panelExpanded = false; onStartCall() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MilingSurface)
                )
            }
        }
        if (recordingActive) {
            Surface(modifier = Modifier.align(Alignment.Center), shape = RoundedCornerShape(MilingRadii.Large), color = Color(0xDD111318)) {
                Column(Modifier.padding(horizontal = 32.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.GraphicEq, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    Text(if (recordingCancel) "松开取消" else if (recordingSeconds >= 50) "${60 - recordingSeconds} 秒后发送" else "松开发送，上滑取消", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ChatTopBar(
    conversationName: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    showWallet: Boolean,
    onOpenTransferRecords: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = MilingSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = MilingIconPrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        Text(
            text = conversationName,
            style = MaterialTheme.typography.titleLarge,
            color = MilingTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MilingSpacing.Sm)
                .semantics { heading() }
        )

        if (showWallet) {
            IconButton(onClick = onOpenTransferRecords, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = "与该好友的转账记录",
                    tint = MilingIconPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "更多",
                tint = MilingIconPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    playingVoiceId: Long?,
    onPlayVoice: (ChatMessage) -> Unit,
    mediaPlaybackUrls: Map<String, String>,
    mediaLoadingIds: Set<String>,
    mediaFailedIds: Set<String>,
    onRefreshMedia: (ChatMessage) -> Unit,
    onMediaPlaybackError: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MilingSpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(MilingSpacing.Md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = MilingSpacing.Md)
    ) {
        items(buildChatTimeline(messages), key = { it.message.id }) { entry ->
            Column(verticalArrangement = Arrangement.spacedBy(MilingSpacing.Md)) {
                entry.timeHeader?.let { DateHeader(date = it) }
                MessageItem(
                    message = entry.message,
                    playing = playingVoiceId == entry.message.id,
                    onPlayVoice = onPlayVoice,
                    mediaUrl = entry.message.mediaId?.let(mediaPlaybackUrls::get),
                    mediaLoading = entry.message.mediaId?.let(mediaLoadingIds::contains) == true,
                    mediaFailed = entry.message.mediaId?.let(mediaFailedIds::contains) == true,
                    onRefreshMedia = onRefreshMedia,
                    onMediaPlaybackError = onMediaPlaybackError
                )
            }
        }
    }
}

@Composable
private fun DateHeader(date: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date,
            style = MaterialTheme.typography.bodySmall,
            color = MilingTextMuted
        )
    }
}

@Composable
private fun MessageItem(
    message: ChatMessage,
    playing: Boolean,
    onPlayVoice: (ChatMessage) -> Unit,
    mediaUrl: String? = null,
    mediaLoading: Boolean = false,
    mediaFailed: Boolean = false,
    onRefreshMedia: (ChatMessage) -> Unit = {},
    onMediaPlaybackError: (ChatMessage) -> Unit = {}
) {
    val isMe = message.senderType == SenderType.Me
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (!isMe) {
                UserAvatar(
                    name = message.senderName.orEmpty().ifBlank { "群成员" },
                    avatarUrl = message.senderAvatarUrl,
                    colorIndex = message.senderId?.hashCode() ?: 0,
                    size = 36.dp
                )
                Spacer(Modifier.width(MilingSpacing.Sm))
            }
            Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                if (!isMe && !message.senderName.isNullOrBlank()) {
                    Text(message.senderName, style = MaterialTheme.typography.labelSmall, color = MilingTextMuted)
                }
                when (message.messageType) {
                    MessageType.Transfer -> TransferCard(message = message)
                    MessageType.Voice -> VoiceBubble(message, playing) { onPlayVoice(message) }
                    MessageType.Call -> CallBubble(message)
                    MessageType.Image -> ChatImageBubble(
                        message = message,
                        url = mediaUrl,
                        loading = mediaLoading,
                        failed = mediaFailed,
                        onRetry = { onRefreshMedia(message) },
                        onPlaybackError = { onMediaPlaybackError(message) }
                    )
                    MessageType.Video -> ChatVideoBubble(message, mediaUrl) { onRefreshMedia(message) }
                    else -> TextBubble(message = message)
                }
            }
        }
    }
}

@Composable
private fun VoiceBubble(message: ChatMessage, playing: Boolean, onClick: () -> Unit) {
    val isMe = message.senderType == SenderType.Me
    Row(
        modifier = Modifier
            .widthIn(min = 96.dp, max = 220.dp)
            .clip(RoundedCornerShape(MilingRadii.Medium))
            .background(if (isMe) MilingPrimarySoft else MilingSurfaceSubtle)
            .clickable(onClick = onClick)
            .padding(horizontal = MilingSpacing.Lg, vertical = MilingSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MilingSpacing.Sm)
    ) {
        Icon(if (playing) Icons.Outlined.GraphicEq else Icons.Outlined.PlayArrow, "播放语音", tint = MilingPrimary)
        Text("${kotlin.math.ceil((message.voiceDurationMs ?: 0) / 1000.0).toInt()}\"", color = MilingTextPrimary)
    }
}

@Composable
private fun CallBubble(message: ChatMessage) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(MilingRadii.Medium))
            .background(MilingSurfaceSubtle)
            .padding(horizontal = MilingSpacing.Lg, vertical = MilingSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MilingSpacing.Sm)
    ) {
        Icon(Icons.Outlined.Call, null, tint = MilingPrimary)
        Text(message.content, color = MilingTextPrimary)
    }
}

@Composable
private fun TextBubble(message: ChatMessage) {
    val isMe = message.senderType == SenderType.Me
    val background = if (isMe) MilingPrimarySoft else MilingSurfaceSubtle
    val shape = if (isMe) {
        RoundedCornerShape(
            topStart = MilingRadii.Medium,
            topEnd = MilingRadii.Small,
            bottomStart = MilingRadii.Medium,
            bottomEnd = MilingRadii.Medium
        )
    } else {
        RoundedCornerShape(
            topStart = MilingRadii.Small,
            topEnd = MilingRadii.Medium,
            bottomStart = MilingRadii.Medium,
            bottomEnd = MilingRadii.Medium
        )
    }

    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(shape)
            .background(background)
            .padding(horizontal = MilingSpacing.Lg, vertical = MilingSpacing.Md)
    ) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyLarge,
            color = MilingTextPrimary
        )
    }
}

@Composable
private fun TransferCard(message: ChatMessage) {
    val shape = RoundedCornerShape(14.dp)
    val orange = Color(0xFFFFA31A)
    val footerOrange = Color(0xFFFFB84D)

    Column(
        modifier = Modifier
            .width(286.dp)
            .clip(shape)
            .background(orange)
            .semantics { contentDescription = "转账卡片 ${formatTransferAmount(message.transferAmount)} ${message.content}" }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = formatTransferAmount(message.transferAmount),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(footerOrange)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "转账",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.94f)
            )
            Icon(
                imageVector = Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.94f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onTogglePanel: () -> Unit,
    voiceMode: Boolean,
    onToggleVoiceMode: () -> Unit,
    onStartRecording: () -> Unit,
    onUpdateRecordingCancel: (Boolean) -> Unit,
    onFinishRecording: (Boolean) -> Unit,
    isSending: Boolean = false
) {
    val shape = RoundedCornerShape(36.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MilingSpacing.Xl, vertical = MilingSpacing.Sm)
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(MilingSurface)
            .border(1.dp, MilingBorder, shape)
            .padding(horizontal = MilingSpacing.Sm, vertical = MilingSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onToggleVoiceMode,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (voiceMode) Icons.Outlined.Keyboard else Icons.Outlined.VolumeUp,
                contentDescription = if (voiceMode) "切换键盘" else "语音输入",
                tint = MilingIconPrimary,
                modifier = Modifier.size(26.dp)
            )
        }

        if (voiceMode) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MilingSurfaceSubtle)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            onStartRecording()
                            var cancel = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                cancel = down.position.y - change.position.y > 80.dp.toPx()
                                onUpdateRecordingCancel(cancel)
                                if (!change.pressed) {
                                    onFinishRecording(cancel)
                                    break
                                }
                                change.consume()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("按住 说话", color = MilingTextPrimary, style = MaterialTheme.typography.titleMedium)
            }
        } else BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MilingSpacing.Xs)
                .semantics { contentDescription = "消息输入框" },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MilingTextPrimary),
            cursorBrush = SolidColor(MilingPrimary),
            maxLines = 4,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = "发送消息…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MilingTextMuted
                        )
                    }
                    inner()
                }
            }
        )

        if (value.isBlank()) {
            IconButton(
                onClick = { /* 表情 */ },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.EmojiEmotions,
                    contentDescription = "表情",
                    tint = MilingIconSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }
            IconButton(
                onClick = onTogglePanel,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "打开更多功能",
                    tint = MilingIconSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            IconButton(
                onClick = onSend,
                modifier = Modifier.size(48.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MilingPrimary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "发送",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatActionPanel(
    onPickImage: () -> Unit,
    showTransfer: Boolean,
    onTransfer: () -> Unit,
    showVoiceCall: Boolean,
    onVoiceCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(MilingSpacing.Xl),
        horizontalArrangement = Arrangement.spacedBy(MilingSpacing.Xl)
    ) {
        ChatActionItem(
            label = "照片",
            icon = Icons.Outlined.Image,
            onClick = onPickImage
        )
        if (showTransfer) ChatActionItem(
            label = "转账",
            icon = Icons.Outlined.AccountBalanceWallet,
            onClick = onTransfer
        )
        if (showVoiceCall) {
            ChatActionItem(
                label = "语音通话",
                icon = Icons.Outlined.Call,
                onClick = onVoiceCall
            )
        }
    }
}

@Composable
private fun ChatActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MilingSpacing.Sm)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(MilingRadii.Medium))
                .background(MilingSurfaceSubtle)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MilingPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MilingTextSecondary
        )
    }
}

internal data class ChatTimelineEntry(val message: ChatMessage, val timeHeader: String?)

internal fun buildChatTimeline(
    messages: List<ChatMessage>,
    zone: ZoneId = ZoneId.systemDefault()
): List<ChatTimelineEntry> = messages.mapIndexed { index, message ->
    val currentDate = Instant.ofEpochMilli(message.timestamp).atZone(zone).toLocalDate()
    val previous = messages.getOrNull(index - 1)
    val shouldShowTime = previous == null ||
        Instant.ofEpochMilli(previous.timestamp).atZone(zone).toLocalDate() != currentDate ||
        message.timestamp - previous.timestamp >= 30 * 60 * 1000L
    ChatTimelineEntry(message, if (shouldShowTime) formatDateHeader(message.timestamp, zone) else null)
}

internal fun formatTransferAmount(amount: String?): String = runCatching {
    NumberFormat.getCurrencyInstance(Locale.CHINA).format(BigDecimal(amount.orEmpty()))
}.getOrElse { "¥${amount.orEmpty()}" }

private fun formatDateHeader(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val instant = Instant.ofEpochMilli(timestamp)
    val dateTime = LocalDateTime.ofInstant(instant, zone)
    val formatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    return formatter.format(dateTime)
}
