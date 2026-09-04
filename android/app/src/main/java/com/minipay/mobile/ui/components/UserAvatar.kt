package com.minipay.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val avatarColors = listOf(
    Color(0xFF4A90D9), Color(0xFF50C878), Color(0xFFFF6B6B), Color(0xFF9B59B6),
    Color(0xFFF39C12), Color(0xFF1ABC9C), Color(0xFFE74C3C), Color(0xFF3498DB)
)

@Composable
fun UserAvatar(
    name: String,
    avatarUrl: String?,
    colorIndex: Int,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = CircleShape
) {
    val normalizedIndex = Math.floorMod(colorIndex, avatarColors.size)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(avatarColors[normalizedIndex])
            .semantics { contentDescription = "$name 头像" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().firstOrNull()?.toString() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        if (!avatarUrl.isNullOrBlank()) {
            AvatarImage(
                avatarUrl = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun AvatarImage(
    avatarUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val cacheKey = avatarCacheKey(avatarUrl)
    val request = ImageRequest.Builder(context)
        .data(avatarUrl)
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .crossfade(false)
        .build()
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
    )
}

@Composable
fun AvatarPreloadEffect(avatarUrls: List<String?>, limit: Int = 13) {
    val context = LocalContext.current
    val urls = avatarUrls.asSequence()
        .filterNotNull()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy(::avatarCacheKey)
        .take(limit)
        .toList()
    DisposableEffect(context, urls) {
        val requests = urls.map { url ->
            val cacheKey = avatarCacheKey(url)
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .crossfade(false)
                    .build()
            )
        }
        onDispose { requests.forEach { it.dispose() } }
    }
}

internal fun avatarCacheKey(url: String): String {
    val normalized = runCatching {
        val uri = URI(url)
        if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
            URI(uri.scheme.lowercase(), uri.authority?.lowercase(), uri.path, null, null).toString()
        } else {
            url
        }
    }.getOrDefault(url)
    return "avatar:$normalized"
}

@OptIn(ExperimentalCoilApi::class)
fun clearPrivateImageCache(context: android.content.Context) {
    context.imageLoader.memoryCache?.clear()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        context.imageLoader.diskCache?.clear()
    }
}
