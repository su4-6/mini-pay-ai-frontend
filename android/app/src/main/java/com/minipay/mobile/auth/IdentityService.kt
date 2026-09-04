package com.minipay.mobile.auth

interface IdentityService {
    suspend fun sendCode(request: SendCodeRequest): CodeChallengeResponse
    suspend fun verifyCode(request: VerifyCodeRequest): AuthorizationCodeResponse
    suspend fun exchangeCode(code: String, verifier: String): OAuthTokenResponse
    suspend fun refresh(refreshToken: String): OAuthTokenResponse
    suspend fun revoke(refreshToken: String)
}
