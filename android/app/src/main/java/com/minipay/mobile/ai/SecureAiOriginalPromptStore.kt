package com.minipay.mobile.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.minipay.mobile.auth.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

interface AiOriginalPromptStore {
    fun write(runId: String, prompt: String)
    fun read(runId: String): String?
}

@Singleton
class SecureAiOriginalPromptStore @Inject constructor(
    @ApplicationContext context: Context,
    private val auth: AuthRepository
) : AiOriginalPromptStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val encryptionKey: SecretKey by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loadOrCreateKey()
    }

    override fun write(runId: String, prompt: String) {
        val userId = auth.currentUserId.value ?: return
        val indexKey = indexKey(userId)
        val runIds = preferences.getString(indexKey, null)
            ?.split(',')
            ?.filter(String::isNotBlank)
            ?.toMutableList()
            ?: mutableListOf()
        runIds.remove(runId)
        runIds += runId
        val editor = preferences.edit().putString(valueKey(userId, runId), encrypt(prompt))
        while (runIds.size > MAX_ENTRIES_PER_USER) {
            editor.remove(valueKey(userId, runIds.removeFirst()))
        }
        editor.putString(indexKey, runIds.joinToString(",")).apply()
    }

    override fun read(runId: String): String? {
        val userId = auth.currentUserId.value ?: return null
        val key = valueKey(userId, runId)
        val payload = preferences.getString(key, null) ?: return null
        return runCatching { decrypt(payload) }.getOrElse {
            preferences.edit().remove(key).apply()
            null
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey)
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.size > GCM_IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            encryptionKey,
            GCMParameterSpec(GCM_TAG_BITS, payload.copyOfRange(0, GCM_IV_BYTES))
        )
        return cipher.doFinal(payload.copyOfRange(GCM_IV_BYTES, payload.size))
            .toString(Charsets.UTF_8)
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private fun indexKey(userId: String) = "index:$userId"
    private fun valueKey(userId: String, runId: String) = "prompt:$userId:$runId"

    private companion object {
        const val PREFERENCES_NAME = "minipay_secure_ai_prompts"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "minipay_ai_prompts_aes_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val MAX_ENTRIES_PER_USER = 100
    }
}
