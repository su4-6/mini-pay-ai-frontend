package com.minipay.mobile.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val MAX_CHAT_IMAGE_BYTES = 10L * 1024 * 1024

data class PreparedChatMedia(
    val file: File,
    val messageType: MessageType,
    val contentType: String,
    val width: Int,
    val height: Int,
    val durationMs: Int? = null
)

data class ChatCaptureTarget(val file: File, val uri: Uri)

class ChatMediaPreparationException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

fun createChatCaptureTarget(context: Context): ChatCaptureTarget {
    val directory = chatMediaCacheDirectory(context)
    val file = File(directory, "capture-${UUID.randomUUID()}.jpg")
    return ChatCaptureTarget(
        file,
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    )
}

suspend fun prepareChatImage(context: Context, uri: Uri): PreparedChatMedia = withContext(Dispatchers.IO) {
    val source = copySelectedMediaToCache(context, uri, MessageType.Image)
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw ChatMediaPreparationException("图片文件已损坏或格式不受支持")
        }

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 4096 || bounds.outHeight / sampleSize > 4096) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: throw ChatMediaPreparationException("图片文件已损坏或格式不受支持")
        val orientation = runCatching {
            ExifInterface(source).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val oriented = orientBitmap(bitmap, orientation)
        if (oriented !== bitmap) bitmap.recycle()
        val longest = maxOf(oriented.width, oriented.height)
        val scaled = if (longest > 2048) {
            val scale = 2048f / longest
            Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * scale).toInt(),
                (oriented.height * scale).toInt(),
                true
            )
        } else {
            oriented
        }
        if (scaled !== oriented) oriented.recycle()

        try {
            val output = ByteArrayOutputStream()
            var quality = 90
            do {
                output.reset()
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                    throw ChatMediaPreparationException("图片处理失败，请重新选择")
                }
                quality -= 5
            } while (output.size().toLong() > MAX_CHAT_IMAGE_BYTES && quality >= 55)
            if (output.size().toLong() > MAX_CHAT_IMAGE_BYTES) {
                throw ChatMediaPreparationException("图片处理后仍超过10MB，请选择较小的图片")
            }
            val file = newPreparedFile(context, "jpg")
            try {
                FileOutputStream(file).use { output.writeTo(it) }
            } catch (error: IOException) {
                file.delete()
                throw ChatMediaPreparationException("无法保存处理后的图片，请重试", error)
            }
            PreparedChatMedia(file, MessageType.Image, "image/jpeg", scaled.width, scaled.height)
        } finally {
            scaled.recycle()
        }
    } finally {
        source.delete()
    }
}

internal fun copySelectedMediaToCache(context: Context, uri: Uri, messageType: MessageType): File {
    require(messageType == MessageType.Image)
    val file = File(chatMediaCacheDirectory(context), "selected-${UUID.randomUUID()}.media")
    try {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw ChatMediaPreparationException("无法读取所选图片，请重新选择")
        var total = 0L
        input.use { source ->
            FileOutputStream(file).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total += count
                    if (total > MAX_CHAT_IMAGE_BYTES) {
                        throw ChatMediaPreparationException("图片不能超过10MB")
                    }
                    output.write(buffer, 0, count)
                }
            }
        }
        if (total == 0L) throw ChatMediaPreparationException("所选图片文件为空")
        return file
    } catch (error: Throwable) {
        file.delete()
        if (error is ChatMediaPreparationException) throw error
        throw ChatMediaPreparationException("无法读取所选图片，请重新选择", error)
    }
}

private fun orientBitmap(source: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return source
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun chatMediaCacheDirectory(context: Context): File =
    File(context.cacheDir, "chat-media").apply { mkdirs() }

private fun newPreparedFile(context: Context, extension: String): File =
    File(chatMediaCacheDirectory(context), "prepared-${UUID.randomUUID()}.$extension")
