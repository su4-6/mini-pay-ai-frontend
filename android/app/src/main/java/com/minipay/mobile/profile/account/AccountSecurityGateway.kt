package com.minipay.mobile.profile.account

interface AccountSecurityGateway {
    suspend fun loadOverview(): AccountSecurityOverview
    suspend fun requestPhoneChange(mobile: String, idempotencyKey: String): VerificationChallenge
    suspend fun confirmPhoneChange(challengeId: String, code: String, idempotencyKey: String)
    suspend fun requestEmailVerification(email: String, idempotencyKey: String): VerificationChallenge
    suspend fun confirmEmail(challengeId: String, code: String, idempotencyKey: String)
    suspend fun deleteEmail(idempotencyKey: String)
    suspend fun requestPaymentPasswordChallenge(idempotencyKey: String): VerificationChallenge
    suspend fun verifyPaymentPasswordChallenge(
        challengeId: String,
        code: String,
        idempotencyKey: String
    ): PaymentPasswordVerification
    suspend fun changePaymentPassword(
        verificationToken: String,
        newPassword: String,
        idempotencyKey: String
    )
    fun currentMobile(): String?
}
