package com.minipay.mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.minipay.mobile.chat.ChatMessage
import com.minipay.mobile.ui.theme.MilingSurfaceSubtle

@Composable
internal fun ChatImageBubble(
    message: ChatMessage,
    url: String?,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onPlaybackError: () -> Unit
) {
    var fullScreen by remember(message.id) { mutableStateOf(false) }
    val ratio = safeMediaRatio(message)
    Box(
        modifier = Modifier
            .width(220.dp)
            .heightIn(min = 120.dp, max = 280.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MilingSurfaceSubtle)
            .clickable(enabled = url != null) { fullScreen = true },
        contentAlignment = Alignment.Center
    ) {
        if (failed) {
            TextButton(onClick = onRetry) { Text("加载失败，点击重试") }
        } else if (url == null || loading) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        } else {
            val context = LocalContext.current
            val request = remember(url, message.mediaId) {
                ImageRequest.Builder(context)
                    .data(url)
                    .diskCacheKey("chat-media:${message.mediaId}")
                    .memoryCacheKey("chat-media:${message.mediaId}")
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = "聊天图片",
                contentScale = ContentScale.Crop,
                onError = { onPlaybackError() },
                modifier = Modifier.width(220.dp).heightIn(min = (220 / ratio).coerceIn(120f, 280f).dp, max = 280.dp)
            )
        }
    }
    if (fullScreen && url != null) FullScreenImage(message, url, onPlaybackError) { fullScreen = false }
}

@Composable
internal fun ChatVideoBubble(message: ChatMessage, url: String?, onPlaybackError: () -> Unit) {
    var fullScreen by remember(message.id) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(220.dp)
            .heightIn(min = 140.dp, max = 280.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF20242B))
            .clickable(enabled = url != null) { fullScreen = true },
        contentAlignment = Alignment.Center
    ) {
        if (url == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Outlined.PlayArrow, "播放视频", tint = Color.White, modifier = Modifier.size(56.dp))
            Text(
                formatDuration(message.mediaDurationMs ?: 0),
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
    if (fullScreen && url != null) FullScreenVideo(url, onPlaybackError) { fullScreen = false }
}

@Composable
private fun FullScreenImage(message: ChatMessage, url: String, onPlaybackError: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val request = remember(url, message.mediaId) {
        ImageRequest.Builder(context)
            .data(url)
            .diskCacheKey("chat-media:${message.mediaId}")
            .memoryCacheKey("chat-media:${message.mediaId}")
            .build()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            AsyncImage(request, "全屏图片", Modifier.fillMaxSize(), contentScale = ContentScale.Fit, onError = { onPlaybackError() })
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Outlined.Close, "关闭", tint = Color.White)
            }
        }
    }
}

@Composable
private fun FullScreenVideo(url: String, onPlaybackError: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = onPlaybackError()
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player } },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Outlined.Close, "关闭", tint = Color.White)
            }
        }
    }
}

private fun safeMediaRatio(message: ChatMessage): Float {
    val width = message.mediaWidth ?: 1
    val height = message.mediaHeight ?: 1
    return (width.toFloat() / height).coerceIn(0.6f, 1.8f)
}

private fun formatDuration(durationMs: Int): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
