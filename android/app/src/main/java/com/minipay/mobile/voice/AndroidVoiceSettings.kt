package com.minipay.mobile.voice

import android.content.Context
import com.minipay.mobile.auth.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class AndroidVoiceSettings @Inject constructor(
    @ApplicationContext context: Context,
    private val auth: AuthRepository
) : VoiceSettings {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableAiSpeechEnabled = MutableStateFlow(read(AI_PREFIX, auth.currentUserId.value))
    private val mutableReceiptSpeechEnabled = MutableStateFlow(read(RECEIPT_PREFIX, auth.currentUserId.value))

    override val aiSpeechEnabled = mutableAiSpeechEnabled.asStateFlow()
    override val receiptSpeechEnabled = mutableReceiptSpeechEnabled.asStateFlow()

    init {
        scope.launch {
            auth.currentUserId.collect { userId ->
                mutableAiSpeechEnabled.value = read(AI_PREFIX, userId)
                mutableReceiptSpeechEnabled.value = read(RECEIPT_PREFIX, userId)
            }
        }
    }

    override fun setAiSpeechEnabled(enabled: Boolean) {
        write(AI_PREFIX, enabled)
        mutableAiSpeechEnabled.value = enabled
    }

    override fun setReceiptSpeechEnabled(enabled: Boolean) {
        write(RECEIPT_PREFIX, enabled)
        mutableReceiptSpeechEnabled.value = enabled
    }

    private fun read(prefix: String, userId: String?): Boolean =
        userId?.let { preferences.getBoolean(key(prefix, it), DEFAULT_ENABLED) } ?: DEFAULT_ENABLED

    private fun write(prefix: String, enabled: Boolean) {
        val userId = auth.currentUserId.value ?: return
        preferences.edit().putBoolean(key(prefix, userId), enabled).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "minipay_voice_preferences"
        const val AI_PREFIX = "ai_speech"
        const val RECEIPT_PREFIX = "receipt_speech"
        const val DEFAULT_ENABLED = true
        fun key(prefix: String, userId: String) = "${prefix}_$userId"
    }
}
