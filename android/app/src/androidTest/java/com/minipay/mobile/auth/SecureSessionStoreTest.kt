package com.minipay.mobile.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureSessionStoreTest {
    @Test
    fun persistsEncryptedRefreshPkceDeviceAndPaymentPasswordState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SecureSessionStore(context)
        store.clearSession()

        store.saveRefreshToken("refresh-token-secret")
        store.savePkceVerifier("pkce-verifier-secret")
        store.saveOnboardingState(payPasswordSet = false, onboardingRequired = true)
        val deviceId = store.deviceId()

        val restored = SecureSessionStore(context)
        assertEquals("refresh-token-secret", restored.refreshToken())
        assertEquals("pkce-verifier-secret", restored.pkceVerifier())
        assertEquals(deviceId, restored.deviceId())
        assertEquals(false, restored.payPasswordSet())
        assertEquals(true, restored.onboardingRequired())

        val raw = context.getSharedPreferences(
            "minipay_secure_session",
            Context.MODE_PRIVATE
        ).all.toString()
        assertFalse(raw.contains("refresh-token-secret"))
        assertFalse(raw.contains("pkce-verifier-secret"))

        restored.clearSession()
        assertNull(restored.refreshToken())
        assertNull(restored.pkceVerifier())
        assertNotNull(restored.deviceId())
    }
}
