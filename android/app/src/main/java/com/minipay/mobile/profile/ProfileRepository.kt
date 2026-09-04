package com.minipay.mobile.profile

import com.minipay.mobile.BuildConfig
import com.minipay.mobile.auth.AuthRepository
import com.minipay.mobile.auth.IdentityApiException
import com.minipay.mobile.auth.ProblemDetails
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class ProfileRepository @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val auth: AuthRepository
) {
    private val baseUrl = BuildConfig.IDENTITY_BASE_URL.trimEnd('/')
    private val mutableState = MutableStateFlow<ProfileLoadState>(ProfileLoadState.Loading)
    val state: StateFlow<ProfileLoadState> = mutableState.asStateFlow()

    suspend fun refresh(expectedUserId: String? = auth.currentUserId.value) {
        if (expectedUserId.isNullOrBlank()) {
            mutableState.value = ProfileLoadState.Loading
            return
        }
        // Foreground and connectivity recovery must keep the last profile visible.  Only the
        // first load needs a blocking screen, otherwise a transient read failure looks like a
        // blank profile page and forces the user to tap retry.
        val previous = mutableState.value
        if (previous !is ProfileLoadState.Ready) mutableState.value = ProfileLoadState.Loading
        runCatching { read<UserProfile>(Request.Builder().url(url("/api/v1/users/me")).get()) }
            .onSuccess { profile ->
                if (auth.currentUserId.value == expectedUserId) {
                    mutableState.value = ProfileLoadState.Ready(profile)
                }
            }
            .onFailure { error ->
                if (auth.currentUserId.value == expectedUserId) {
                    mutableState.value = (previous as? ProfileLoadState.Ready)
                        ?: ProfileLoadState.Failed(message(error))
                }
            }
    }

    fun clear() {
        mutableState.value = ProfileLoadState.Loading
    }

    suspend fun save(nickname: String, avatar: PreparedAvatar?): UserProfile {
        val expectedUserId = auth.currentUserId.value
            ?: throw IdentityApiException("TOKEN_INVALID")
        val current = (mutableState.value as? ProfileLoadState.Ready)?.profile
            ?.takeIf { it.userId == expectedUserId }
            ?: request<UserProfile>(Request.Builder().url(url("/api/v1/users/me")).get())
        val uploadId = avatar?.let { uploadAvatar(it) }
        if (auth.currentUserId.value != expectedUserId) throw IdentityApiException("TOKEN_INVALID")
        val body = json.encodeToString(UpdateProfileRequest(nickname, uploadId, current.version))
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val updated = request<UserProfile>(Request.Builder().url(url("/api/v1/users/me")).patch(body))
        if (auth.currentUserId.value == expectedUserId) mutableState.value = ProfileLoadState.Ready(updated)
        return updated
    }

    suspend fun uploadAvatar(avatar: PreparedAvatar): String {
        val grantBody = json.encodeToString(
            AvatarUploadRequest("image/jpeg", avatar.bytes.size.toLong(), avatar.sha256)
        ).toRequestBody("application/json; charset=utf-8".toMediaType())
        val grant = request<AvatarUploadGrant>(
            Request.Builder().url(url("/api/v1/users/me/avatar-uploads")).post(grantBody)
        )
        withContext(Dispatchers.IO) {
            val upload = Request.Builder().url(grant.uploadUrl)
                .put(avatar.bytes.toRequestBody("image/jpeg".toMediaType()))
            grant.requiredHeaders.forEach { (name, value) -> upload.header(name, value) }
            client.newCall(upload.build()).execute().use {
                if (!it.isSuccessful) throw IdentityApiException("AVATAR_UPLOAD_FAILED", null, it.code)
            }
        }
        return grant.uploadId
    }

    private suspend inline fun <reified T> request(builder: Request.Builder): T = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken() ?: throw IdentityApiException("TOKEN_INVALID")
        val request = builder.header("Authorization", "Bearer $token")
            .header("X-Request-Id", UUID.randomUUID().toString()).build()
        val response = try {
            client.newCall(request).execute()
        } catch (exception: IOException) {
            throw IdentityApiException("NETWORK_UNAVAILABLE", null, null, exception)
        }
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val problem = runCatching { json.decodeFromString<ProblemDetails>(body) }.getOrNull()
                throw IdentityApiException(problem?.code ?: "REQUEST_FAILED", problem?.requestId, it.code)
            }
            runCatching { json.decodeFromString<T>(body) }
                .getOrElse { cause -> throw IdentityApiException("INVALID_RESPONSE", null, it.code, cause) }
        }
    }

    private suspend inline fun <reified T> read(builder: Request.Builder): T {
        repeat(2) { attempt ->
            try {
                return request(builder)
            } catch (error: IdentityApiException) {
                val retryable = error.code == "NETWORK_UNAVAILABLE" ||
                    (error.status != null && error.status >= 500)
                if (!retryable || attempt == 1) throw error
                delay(180L * (attempt + 1))
            }
        }
        error("unreachable")
    }

    private fun url(path: String): String {
        if (baseUrl.isBlank()) throw IdentityApiException("IDENTITY_NOT_CONFIGURED")
        return "$baseUrl$path"
    }

    internal fun message(error: Throwable): String = when ((error as? IdentityApiException)?.code) {
        "TOKEN_INVALID" -> "登录状态已失效，请重新登录"
        "NETWORK_UNAVAILABLE" -> "网络连接失败，请稍后重试"
        "NICKNAME_INVALID" -> "昵称仅支持 2～20 个中文、字母、数字或下划线"
        "PROFILE_CONTENT_REJECTED" -> "资料未通过内容安全审核，请修改后重试"
        "PROFILE_VERSION_CONFLICT" -> "资料已更新，请返回刷新后重试"
        "AVATAR_UPLOAD_FAILED", "AVATAR_UPLOAD_EXPIRED" -> "头像上传失败，请重新选择后重试"
        // OSS 直传的 PUT 走公网域名（不走 adb reverse），手机断网时抛的是 IOException
        // 而非 HTTP 错误码，单独映射以免误报"操作失败"。
        null -> if (error is IOException) "网络连接失败，请检查网络后重试" else "操作失败，请稍后重试"
        else -> "操作失败，请稍后重试"
    }
}
