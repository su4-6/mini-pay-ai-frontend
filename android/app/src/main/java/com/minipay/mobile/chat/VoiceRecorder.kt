package com.minipay.mobile.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceRecorder @Inject constructor(@ApplicationContext private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt: Long = 0L

    @Synchronized
    fun start(): File {
        cancel()
        val file = File.createTempFile("voice_", ".m4a", context.cacheDir)
        val value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        value.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        value.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        value.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        value.setAudioChannels(1)
        value.setAudioSamplingRate(24_000)
        value.setAudioEncodingBitRate(32_000)
        value.setMaxDuration(60_000)
        value.setOutputFile(file.absolutePath)
        value.prepare()
        value.start()
        recorder = value
        output = file
        startedAt = SystemClock.elapsedRealtime()
        return file
    }

    @Synchronized
    fun stop(): Recording {
        val value = recorder ?: throw IllegalStateException("Recorder is not running")
        val file = output ?: throw IllegalStateException("Recording file is missing")
        val duration = (SystemClock.elapsedRealtime() - startedAt).coerceAtMost(60_000L).toInt()
        try { value.stop() } finally { value.release(); recorder = null; output = null; startedAt = 0L }
        return Recording(file, duration)
    }

    @Synchronized
    fun cancel() {
        val value = recorder
        val file = output
        if (value != null) {
            runCatching { value.stop() }
            value.release()
        }
        recorder = null; output = null; startedAt = 0L
        file?.delete()
    }

    data class Recording(val file: File, val durationMs: Int)
}
