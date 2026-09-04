package com.minipay.mobile.ui.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minipay.mobile.chat.VoiceCallManager
import com.minipay.mobile.chat.VoiceCallUiState
import com.minipay.mobile.ui.theme.MilingTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay

@AndroidEntryPoint
class VoiceCallActivity : ComponentActivity() {
    @Inject lateinit var calls: VoiceCallManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "语音通话" }
        val incoming = intent.getBooleanExtra(EXTRA_INCOMING, false)
        setContent {
            MilingTheme {
                val state by calls.state.collectAsStateWithLifecycle()
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) { if (incoming) calls.accept() else calls.startOutgoing(conversationId, name) }
                }
                LaunchedEffect(Unit) {
                    if (!incoming && state.status == "IDLE") {
                        if (ContextCompat.checkSelfPermission(this@VoiceCallActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) calls.startOutgoing(conversationId, name)
                        else permission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                VoiceCallScreen(state, onAccept = {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) calls.accept() else permission.launch(Manifest.permission.RECORD_AUDIO)
                }, onReject = calls::reject, onHangUp = calls::hangUp, onMute = calls::toggleMute, onSpeaker = calls::toggleSpeaker, onClose = { calls.reset(); finish() })
            }
        }
    }
    companion object {
        private const val EXTRA_CONVERSATION_ID = "conversationId"; private const val EXTRA_NAME = "name"; private const val EXTRA_INCOMING = "incoming"
        fun intent(context: Context, conversationId: String, name: String, incoming: Boolean) = Intent(context, VoiceCallActivity::class.java).putExtra(EXTRA_CONVERSATION_ID, conversationId).putExtra(EXTRA_NAME, name).putExtra(EXTRA_INCOMING, incoming).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

@Composable
private fun VoiceCallScreen(state: VoiceCallUiState, onAccept: () -> Unit, onReject: () -> Unit, onHangUp: () -> Unit, onMute: () -> Unit, onSpeaker: () -> Unit, onClose: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.connectedAt) { while (state.connectedAt != null && state.status in listOf("ACCEPTED", "CONNECTED")) { delay(1000); now = System.currentTimeMillis() } }
    LaunchedEffect(state.status) { if (state.status in listOf("REJECTED", "CANCELLED", "MISSED", "ENDED", "FAILED")) { delay(1500); onClose() } }
    val duration = state.connectedAt?.let { ((now - it) / 1000).coerceAtLeast(0) }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFF18202C)).safeDrawingPadding().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val compact = maxHeight < 500.dp
        val avatarSize = if (compact) 72.dp else 104.dp
        val buttonSize = if (compact) 56.dp else 64.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = Color(0xFF344155), modifier = Modifier.size(avatarSize)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Call, null, tint = Color.White, modifier = Modifier.size(if (compact) 36.dp else 48.dp)) } }
            Spacer(Modifier.height(24.dp)); Text(state.peerName, color = Color.White)
            Text(duration?.let { "%02d:%02d".format(it / 60, it % 60) } ?: when (state.status) { "RINGING" -> if (state.incoming) "邀请你语音通话" else "正在等待对方接听…"; "CREATING" -> "正在呼叫…"; "FAILED" -> state.error ?: "连接失败"; else -> "正在连接…" }, color = Color(0xFFCBD5E1))
            Spacer(Modifier.height(if (compact) 28.dp else 72.dp))
            if (state.incoming && state.status == "RINGING") Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 32.dp else 56.dp)) { CallButton(Color(0xFFE5484D), Icons.Outlined.CallEnd, buttonSize, onReject); CallButton(Color(0xFF22C55E), Icons.Outlined.Call, buttonSize, onAccept) }
            else Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 32.dp)) { CallButton(Color(0xFF344155), if (state.muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, buttonSize, onMute); CallButton(Color(0xFFE5484D), Icons.Outlined.CallEnd, buttonSize, onHangUp); CallButton(if (state.speaker) Color(0xFF1677FF) else Color(0xFF344155), Icons.Outlined.VolumeUp, buttonSize, onSpeaker) }
        }
    }
}

@Composable private fun CallButton(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) { Surface(shape = CircleShape, color = color, modifier = Modifier.size(size)) { IconButton(onClick = onClick) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp)) } } }
