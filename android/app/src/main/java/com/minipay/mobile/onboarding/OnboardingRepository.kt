package com.minipay.mobile.onboarding

import com.minipay.mobile.BuildConfig
import com.minipay.mobile.auth.AuthRepository
import com.minipay.mobile.auth.IdentityApiException
import com.minipay.mobile.auth.ProblemDetails
import com.minipay.mobile.profile.PreparedAvatar
import com.minipay.mobile.profile.AvatarPreparer
import com.minipay.mobile.profile.ProfileRepository
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class OnboardingRepository @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
    private val avatarPreparer: AvatarPreparer
) : OnboardingGateway {
    private val baseUrl = BuildConfig.IDENTITY_BASE_URL.trimEnd('/')

    override suspend fun prepareAvatar(uri: android.net.Uri): PreparedAvatar = avatarPreparer.prepare(uri)

    override suspend fun uploadAvatar(avatar: PreparedAvatar): String = profiles.uploadAvatar(avatar)

    override suspend fun complete(
        nickname: String,
        avatarUploadId: String?,
        idempotencyKey: String
    ): OnboardingResponse = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken() ?: throw IdentityApiException("TOKEN_INVALID")
        val body = json.encodeToString(
            CompleteOnboardingRequest(nickname, avatarUploadId)
        ).toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url("/api/v1/users/me/onboarding"))
            .header("Authorization", "Bearer $token")
            .header("X-Request-Id", UUID.randomUUID().toString())
            .header("Idempotency-Key", idempotencyKey)
            .put(body)
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (exception: IOException) {
            throw IdentityApiException("NETWORK_UNAVAILABLE", null, null, exception)
        }
        response.use {
            val responseBody = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val problem = runCatching { json.decodeFromString<ProblemDetails>(responseBody) }.getOrNull()
                if (problem?.code == "ONBOARDING_ALREADY_COMPLETED") {
                    auth.refreshAfterOnboarding()
                    profiles.refresh()
                    return@withContext OnboardingResponse(
                        userId = "",
                        nickname = nickname,
                        payPasswordSet = false,
                        onboardingCompleted = true
                    )
                }
                throw IdentityApiException(problem?.code ?: "REQUEST_FAILED", problem?.requestId, it.code)
            }
            runCatching { json.decodeFromString<OnboardingResponse>(responseBody) }
                .getOrElse { cause ->
                    throw IdentityApiException("INVALID_RESPONSE", null, it.code, cause)
                }
        }.also {
            auth.refreshAfterOnboarding()
            profiles.refresh()
        }
    }

    override fun message(error: Throwable): String = when ((error as? IdentityApiException)?.code) {
        "TOKEN_INVALID" -> "登录状态已失效，请退出后重新登录"
        "NETWORK_UNAVAILABLE" -> "网络连接失败，请检查网络后重试"
        "NICKNAME_INVALID" -> "昵称仅支持 2～20 个中文、字母、数字或下划线"
        "PROFILE_CONTENT_REJECTED" -> "昵称或头像未通过内容安全审核，请修改后重试"
        "AVATAR_UPLOAD_FAILED", "AVATAR_UPLOAD_EXPIRED", "AVATAR_UPLOAD_NOT_FOUND" ->
            "头像上传已失效，请重新选择头像"
        "AVATAR_UPLOAD_FORBIDDEN", "AVATAR_OBJECT_MISMATCH", "AVATAR_UPLOAD_INVALID" ->
            "头像文件校验失败，请重新选择"
        "CONTENT_REVIEW_UNAVAILABLE", "OBJECT_STORAGE_UNAVAILABLE", "AVATAR_OBJECT_UNAVAILABLE" ->
            "头像服务暂不可用，你可以移除头像后继续"
        "IDEMPOTENCY_KEY_REUSED" -> "初始化资料已变化，请退出后重新登录再试"
        "ONBOARDING_ALREADY_COMPLETED", "PAY_PASSWORD_ALREADY_SET" ->
            "账号初始化已完成"
        "PAY_PASSWORD_INVALID" -> "支付密码必须为 6 位数字"
        "INVALID_RESPONSE" -> "服务响应异常，请稍后重试"
        else -> "初始化失败，请稍后重试"
    }

    private fun url(path: String): String {
        if (baseUrl.isBlank()) throw IdentityApiException("IDENTITY_NOT_CONFIGURED")
        return "$baseUrl$path"
    }
}
