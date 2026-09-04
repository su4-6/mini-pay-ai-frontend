package com.minipay.mobile.auth

interface SessionStorage {
    fun userId(): String? = null
    fun saveUserId(value: String) = Unit
    fun mobile(): String? = null
    fun saveMobile(value: String) = Unit
    fun refreshToken(): String?
    fun saveRefreshToken(value: String)
    fun payPasswordSet(): Boolean
    fun savePayPasswordSet(value: Boolean)
    fun onboardingRequired(): Boolean
    fun saveOnboardingState(payPasswordSet: Boolean, onboardingRequired: Boolean)
    fun clearSession()
    fun deviceId(): String
    fun savePkceVerifier(value: String)
    fun pkceVerifier(): String?
    fun clearPkceVerifier()
}
