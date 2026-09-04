package com.minipay.mobile.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureSessionStore @Inject constructor(
    @ApplicationContext context: Context
) : SessionStorage {
    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val encryptionKey: SecretKey by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loadOrCreateKey()
    }

    override fun refreshToken(): String? = readEncrypted(KEY_REFRESH_TOKEN)

    override fun userId(): String? = readEncrypted(KEY_USER_ID)

    override fun saveUserId(value: String) { writeEncrypted(KEY_USER_ID, value) }

    override fun mobile(): String? = readEncrypted(KEY_MOBILE)

    override fun saveMobile(value: String) { writeEncrypted(KEY_MOBILE, value) }

    override fun saveRefreshToken(value: String) {
        writeEncrypted(KEY_REFRESH_TOKEN, value)
    }

    override fun payPasswordSet(): Boolean =
        readEncrypted(KEY_PAY_PASSWORD_SET)?.toBooleanStrictOrNull() ?: false

    override fun savePayPasswordSet(value: Boolean) {
        writeEncrypted(KEY_PAY_PASSWORD_SET, value.toString())
    }

    override fun onboardingRequired(): Boolean =
        readEncrypted(KEY_ONBOARDING_REQUIRED)?.toBooleanStrictOrNull() ?: false

    override fun saveOnboardingState(payPasswordSet: Boolean, onboardingRequired: Boolean) {
        preferences.edit()
            .putString(KEY_PAY_PASSWORD_SET, encrypt(payPasswordSet.toString()))
            .putString(KEY_ONBOARDING_REQUIRED, encrypt(onboardingRequired.toString()))
            .apply()
    }

    override fun clearSession() {
        preferences.edit()
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_MOBILE)
            .remove(KEY_PKCE_VERIFIER)
            .remove(KEY_PAY_PASSWORD_SET)
            .remove(KEY_ONBOARDING_REQUIRED)
            .apply()
    }

    override fun deviceId(): String {
        return readEncrypted(KEY_DEVICE_ID)
            ?: UUID.randomUUID().toString().also {
                writeEncrypted(KEY_DEVICE_ID, it, synchronous = true)
            }
    }

    override fun savePkceVerifier(value: String) {
        writeEncrypted(KEY_PKCE_VERIFIER, value)
    }

    override fun pkceVerifier(): String? = readEncrypted(KEY_PKCE_VERIFIER)

    override fun clearPkceVerifier() {
        preferences.edit().remove(KEY_PKCE_VERIFIER).apply()
    }

    private fun writeEncrypted(
        key: String,
        value: String,
        synchronous: Boolean = false
    ) {
        val editor = preferences.edit().putString(
            key,
            encrypt(value)
        )
        if (synchronous) editor.commit() else editor.apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey)
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(cipher.iv.size + encrypted.size)
        cipher.iv.copyInto(payload)
        encrypted.copyInto(payload, destinationOffset = cipher.iv.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun readEncrypted(key: String): String? {
        val payload = preferences.getString(key, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            require(bytes.size > GCM_IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                encryptionKey,
                GCMParameterSpec(GCM_TAG_BITS, bytes.copyOfRange(0, GCM_IV_BYTES))
            )
            val plaintext = cipher.doFinal(bytes.copyOfRange(GCM_IV_BYTES, bytes.size))
            plaintext.toString(Charsets.UTF_8)
        }.getOrElse {
            preferences.edit().remove(key).apply()
            null
        }
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        ).run {
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

    private companion object {
        const val PREFERENCES_NAME = "minipay_secure_session"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "minipay_session_aes_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_MOBILE = "mobile"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_PKCE_VERIFIER = "pkce_verifier"
        const val KEY_PAY_PASSWORD_SET = "pay_password_set"
        const val KEY_ONBOARDING_REQUIRED = "onboarding_required"
    }
}
