package com.minipay.mobile.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AvatarPreparer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun prepare(uri: Uri): PreparedAvatar = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(
                    (maxOf(info.size.width, info.size.height) / MAX_DECODE_SIDE).coerceAtLeast(1)
                )
            }
        } else {
            resolver.openInputStream(uri).use { input ->
                BitmapFactory.decodeStream(input) ?: error("无法读取所选图片")
            }
        }
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(
            bitmap,
            (bitmap.width - side) / 2,
            (bitmap.height - side) / 2,
            side,
            side
        )
        val scaled = if (side > MAX_AVATAR_SIDE) {
            Bitmap.createScaledBitmap(square, MAX_AVATAR_SIDE, MAX_AVATAR_SIDE, true)
        } else {
            square
        }
        val bytes = ByteArrayOutputStream().use { output ->
            check(scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output))
            output.toByteArray()
        }
        require(bytes.size <= MAX_AVATAR_BYTES) { "头像文件不能超过 5MB" }
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        PreparedAvatar(bytes, sha)
    }

    private companion object {
        const val MAX_DECODE_SIDE = 2048
        const val MAX_AVATAR_SIDE = 1024
        const val MAX_AVATAR_BYTES = 5_242_880
        const val JPEG_QUALITY = 90
    }
}
