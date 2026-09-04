package com.minipay.mobile.chat

import com.minipay.mobile.BuildConfig
import com.minipay.mobile.auth.AuthRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class FriendResponse(
    val userId: String,
    val nickname: String,
    val minipayNo: String,
    val phoneMasked: String?,
    val conversationId: String? = null,
    val avatarUrl: String? = null,
    val avatarUrlExpiresAt: String? = null
)

@Serializable
data class SearchHit(
    val userId: String,
    val nickname: String,
    val minipayNo: String,
    val phoneMasked: String?,
    val friendStatus: String? = null,
    val avatarUrl: String? = null,
    val avatarUrlExpiresAt: String? = null
)

@Serializable
data class PublicCardResponse(
    val userId: String,
    val nickname: String,
    val minipayNo: String,
    val phoneMasked: String? = null,
    val friendStatus: String,
    val avatarUrl: String? = null,
    val avatarUrlExpiresAt: String? = null
)

@Serializable
data class SendFriendRequestBody(
    val toUserId: String
)

@Serializable
data class FriendRequestResponse(
    val id: String,
    val status: String
)

@Serializable
data class ReceivedRequest(
    val id: String,
    val fromUserId: String,
    val nickname: String,
    val minipayNo: String,
    val phoneMasked: String?,
    val status: String,
    val createdAt: Long
)

interface FriendApiService {
    suspend fun listFriends(): List<FriendResponse>
    suspend fun searchUsers(query: String): List<SearchHit>
    suspend fun resolveQrCard(minipayNo: String): PublicCardResponse
    suspend fun sendFriendRequest(toUserId: String): FriendRequestResponse
    suspend fun getReceivedRequests(): List<ReceivedRequest>
    suspend fun acceptRequest(id: String)
    suspend fun rejectRequest(id: String)
    suspend fun deleteFriend(userId: String)
}

@Singleton
class FriendApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val authRepository: AuthRepository
) : FriendApiService {
    private val baseUrl = BuildConfig.IDENTITY_BASE_URL.trimEnd('/')

    override suspend fun listFriends(): List<FriendResponse> =
        get("/api/v1/friends")

    override suspend fun searchUsers(query: String): List<SearchHit> =
        get("/api/v1/users/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}")

    override suspend fun resolveQrCard(minipayNo: String): PublicCardResponse =
        get("/api/v1/users/qr/${java.net.URLEncoder.encode(minipayNo, "UTF-8")}")

    override suspend fun sendFriendRequest(toUserId: String): FriendRequestResponse =
        post("/api/v1/friend-requests", SendFriendRequestBody(toUserId))

    override suspend fun getReceivedRequests(): List<ReceivedRequest> =
        get("/api/v1/friend-requests/received")

    override suspend fun acceptRequest(id: String) {
        put("/api/v1/friend-requests/$id/accept")
    }

    override suspend fun rejectRequest(id: String) {
        put("/api/v1/friend-requests/$id/reject")
    }

    override suspend fun deleteFriend(userId: String) {
        executeUnit(Request.Builder().url(url("/api/v1/friends/$userId")).delete().build())
    }

    private suspend inline fun <reified T> get(path: String): T =
        execute(Request.Builder().url(url(path)).get().build())

    private suspend inline fun <reified T, reified B> post(path: String, body: B): T {
        val requestBody = json.encodeToString(body)
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        return execute(Request.Builder().url(url(path)).post(requestBody).build())
    }

    private suspend fun put(path: String) {
        withContext(Dispatchers.IO) {
            val token = authRepository.validAccessToken()
                ?: throw FriendApiException("NOT_AUTHENTICATED")
            val emptyBody = "".toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url(path)).put(emptyBody)
                .header("Authorization", "Bearer $token")
                .header("X-Request-Id", UUID.randomUUID().toString())
                .build()
            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw FriendApiException("NETWORK_UNAVAILABLE")
            }
            response.use {
                if (!it.isSuccessful) {
                    val body = it.body?.string().orEmpty()
                    throw FriendApiException("HTTP ${it.code}: ${body.take(200)}")
                }
            }
        }
    }

    private suspend inline fun <reified T> execute(request: Request): T =
        withContext(Dispatchers.IO) {
            val token = authRepository.validAccessToken()
                ?: throw FriendApiException("NOT_AUTHENTICATED")
            val authenticated = request.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("X-Request-Id", UUID.randomUUID().toString())
                .build()
            val response = try {
                client.newCall(authenticated).execute()
            } catch (e: IOException) {
                throw FriendApiException("NETWORK_UNAVAILABLE")
            }
            response.use {
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    throw FriendApiException("HTTP ${it.code}: ${body.take(200)}")
                }
                runCatching { json.decodeFromString<T>(body) }
                    .getOrElse { throw FriendApiException("INVALID_RESPONSE") }
            }
        }

    private suspend fun executeUnit(request: Request) = withContext(Dispatchers.IO) {
        val token = authRepository.validAccessToken() ?: throw FriendApiException("NOT_AUTHENTICATED")
        client.newCall(request.newBuilder().header("Authorization", "Bearer $token").header("X-Request-Id", UUID.randomUUID().toString()).build()).execute().use {
            if (!it.isSuccessful) throw FriendApiException("HTTP ${it.code}")
        }
    }

    private fun url(path: String): String {
        if (baseUrl.isBlank()) {
            throw FriendApiException("IDENTITY_NOT_CONFIGURED")
        }
        return "$baseUrl$path"
    }
}

class FriendApiException(code: String) : RuntimeException(code)
