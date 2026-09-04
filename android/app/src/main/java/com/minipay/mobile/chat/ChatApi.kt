package com.minipay.mobile.chat

import com.minipay.mobile.BuildConfig
import com.minipay.mobile.auth.AuthRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- API DTOs (match backend ChatController responses) ---

@Serializable
data class ConversationResponse(
    val id: String,
    val name: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val unreadCount: Int,
    val isTransfer: Boolean,
    val avatarColorIndex: Int,
    val avatarUrl: String? = null,
    val avatarUrlExpiresAt: String? = null
)

@Serializable
data class MessageResponse(
    val id: Long,
    val conversationId: String,
    val senderType: String,
    val senderId: String? = null,
    val senderNickname: String? = null,
    val senderOriginalNickname: String? = null,
    val senderAvatarUrl: String? = null,
    val senderAvatarUrlExpiresAt: String? = null,
    val content: String,
    val messageType: String,
    val transferAmount: String? = null,
    val transferStatus: String? = null,
    val transferDirection: String? = null,
    val transferId: String? = null,
    val transferTargetUserId: String? = null,
    val voiceMediaId: String? = null,
    val voiceDurationMs: Int? = null,
    val mediaId: String? = null,
    val mediaKind: String? = null,
    val mediaContentType: String? = null,
    val mediaWidth: Int? = null,
    val mediaHeight: Int? = null,
    val mediaDurationMs: Int? = null,
    val callId: String? = null,
    val callStatus: String? = null,
    val callDurationSeconds: Int? = null,
    val timestamp: Long
)

@Serializable
data class MessageListResponse(
    val messages: List<MessageResponse>,
    val total: Int
)

@Serializable
data class CreateConversationRequest(
    val contactId: String,
    val name: String
)

@Serializable
data class GroupMemberInput(val userId: String, val nickname: String)
@Serializable data class CreateGroupRequest(val name: String, val members: List<GroupMemberInput>)

@Serializable data class GroupMemberResponse(
    val userId: String,
    val nickname: String? = null,
    val originalNickname: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val avatarUrlExpiresAt: String? = null
)
@Serializable data class GroupDetailResponse(val id: String, val name: String, val ownerId: String, val avatarUrl: String? = null, val avatarUrlExpiresAt: String? = null, val members: List<GroupMemberResponse>)
@Serializable data class GroupMembersRequest(val members: List<GroupMemberInput>)
@Serializable data class TextRequest(val value: String)

@Serializable
data class SendMessageRequest(
    val content: String,
    val messageType: String,
    val transferAmount: String? = null,
    val transferStatus: String? = null,
    val transferDirection: String? = null,
    val transferId: String? = null,
    val transferTargetUserId: String? = null,
    val voiceMediaId: String? = null,
    val voiceDurationMs: Int? = null,
    val mediaId: String? = null,
    val contactId: String? = null
)

@Serializable data class VoiceUploadRequest(val conversationId: String, val contentType: String, val sizeBytes: Long, val sha256: String)
@Serializable data class VoiceUploadResponse(val mediaId: String, val uploadUrl: String, val requiredHeaders: Map<String, String>, val expiresAt: String)
@Serializable data class CompleteVoiceRequest(val durationMs: Int)
@Serializable data class CompleteVoiceResponse(val mediaId: String, val durationMs: Int)
@Serializable data class VoicePlaybackResponse(val playbackUrl: String, val expiresAt: String)
@Serializable data class ChatMediaUploadRequest(val conversationId: String, val mediaKind: String, val contentType: String, val sizeBytes: Long, val sha256: String)
@Serializable data class ChatMediaUploadResponse(val mediaId: String, val uploadUrl: String, val requiredHeaders: Map<String, String>, val expiresAt: String)
@Serializable data class CompleteChatMediaRequest(val width: Int, val height: Int, val durationMs: Int? = null)
@Serializable data class CompleteChatMediaResponse(val mediaId: String, val mediaKind: String, val contentType: String, val width: Int, val height: Int, val durationMs: Int? = null)
@Serializable data class ChatMediaPlaybackResponse(val playbackUrl: String, val expiresAt: String)
@Serializable data class CreateCallRequest(val conversationId: String)
@Serializable data class VoiceCallResponse(val id: String, val conversationId: String, val callerId: String, val calleeId: String, val status: String, val createdAt: String, val answeredAt: String? = null, val endedAt: String? = null)
@Serializable data class IceServersResponse(val urls: List<String>, val username: String? = null, val credential: String? = null, val expiresAtEpochSeconds: Long)
@Serializable data class GroupAvatarUploadRequest(val contentType: String, val sizeBytes: Long, val sha256: String)
@Serializable data class GroupAvatarUploadResponse(val uploadId: String, val uploadUrl: String, val requiredHeaders: Map<String, String>, val expiresAt: String)
@Serializable data class GroupAvatarResponse(val avatarUrl: String, val avatarUrlExpiresAt: String)

// --- Service interface ---

interface ChatApiService {
    suspend fun listConversations(): List<ConversationResponse>
    suspend fun createConversation(request: CreateConversationRequest): ConversationResponse
    suspend fun deleteConversation(conversationId: String)
    suspend fun createGroup(request: CreateGroupRequest): ConversationResponse
    suspend fun getGroupDetail(groupId: String): GroupDetailResponse
    suspend fun addGroupMembers(groupId: String, members: List<GroupMemberInput>)
    suspend fun removeGroupMember(groupId: String, memberId: String)
    suspend fun renameGroup(groupId: String, name: String)
    suspend fun updateMyGroupNickname(groupId: String, nickname: String)
    suspend fun createGroupAvatarUpload(groupId: String, request: GroupAvatarUploadRequest): GroupAvatarUploadResponse
    suspend fun uploadGroupAvatarBytes(uploadUrl: String, requiredHeaders: Map<String, String>, bytes: ByteArray)
    suspend fun completeGroupAvatarUpload(groupId: String, uploadId: String): GroupAvatarResponse
    suspend fun disbandGroup(groupId: String)
    suspend fun leaveGroup(groupId: String)
    suspend fun listMessages(conversationId: String, limit: Int = 50, offset: Int = 0): MessageListResponse
    suspend fun sendMessage(conversationId: String, request: SendMessageRequest): MessageResponse
    suspend fun createVoiceUpload(request: VoiceUploadRequest): VoiceUploadResponse
    suspend fun uploadVoiceBytes(uploadUrl: String, requiredHeaders: Map<String, String>, bytes: ByteArray)
    suspend fun completeVoiceUpload(mediaId: String, durationMs: Int): CompleteVoiceResponse
    suspend fun voicePlayback(mediaId: String): VoicePlaybackResponse
    suspend fun createChatMediaUpload(request: ChatMediaUploadRequest): ChatMediaUploadResponse
    suspend fun uploadChatMediaFile(uploadUrl: String, requiredHeaders: Map<String, String>, contentType: String, file: File)
    suspend fun completeChatMediaUpload(mediaId: String, request: CompleteChatMediaRequest): CompleteChatMediaResponse
    suspend fun chatMediaPlayback(mediaId: String): ChatMediaPlaybackResponse
    suspend fun createCall(conversationId: String): VoiceCallResponse
    suspend fun acceptCall(callId: String): VoiceCallResponse
    suspend fun rejectCall(callId: String): VoiceCallResponse
    suspend fun cancelCall(callId: String): VoiceCallResponse
    suspend fun endCall(callId: String): VoiceCallResponse
    suspend fun iceServers(): IceServersResponse
}

// --- Implementation ---

@Singleton
class ChatApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val authRepository: AuthRepository
) : ChatApiService {
    private val baseUrl = BuildConfig.AGENT_BASE_URL.trimEnd('/')
    private val mediaUploadClient = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.MINUTES)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun listConversations(): List<ConversationResponse> =
        get("/api/v1/agent/conversations")

    override suspend fun createConversation(
        request: CreateConversationRequest
    ): ConversationResponse = post("/api/v1/agent/conversations", request)

    override suspend fun deleteConversation(conversationId: String) =
        delete("/api/v1/agent/conversations/$conversationId")

    override suspend fun createGroup(request: CreateGroupRequest): ConversationResponse =
        post("/api/v1/agent/conversations/groups", request)

    override suspend fun getGroupDetail(groupId: String): GroupDetailResponse = get("/api/v1/agent/conversations/groups/$groupId")
    override suspend fun addGroupMembers(groupId: String, members: List<GroupMemberInput>) = postUnit("/api/v1/agent/conversations/groups/$groupId/members", GroupMembersRequest(members))
    override suspend fun removeGroupMember(groupId: String, memberId: String) = delete("/api/v1/agent/conversations/groups/$groupId/members/$memberId")
    override suspend fun renameGroup(groupId: String, name: String) = put("/api/v1/agent/conversations/groups/$groupId/name", TextRequest(name))
    override suspend fun updateMyGroupNickname(groupId: String, nickname: String) = put("/api/v1/agent/conversations/groups/$groupId/my-nickname", TextRequest(nickname))
    override suspend fun createGroupAvatarUpload(groupId: String, request: GroupAvatarUploadRequest): GroupAvatarUploadResponse =
        post("/api/v1/agent/conversations/groups/$groupId/avatar-uploads", request)
    override suspend fun uploadGroupAvatarBytes(uploadUrl: String, requiredHeaders: Map<String, String>, bytes: ByteArray) =
        uploadBytes(uploadUrl, requiredHeaders, bytes, "image/jpeg", "GROUP_AVATAR_UPLOAD_FAILED")
    override suspend fun completeGroupAvatarUpload(groupId: String, uploadId: String): GroupAvatarResponse =
        postEmpty("/api/v1/agent/conversations/groups/$groupId/avatar-uploads/$uploadId/complete")
    override suspend fun disbandGroup(groupId: String) = delete("/api/v1/agent/conversations/groups/$groupId")
    override suspend fun leaveGroup(groupId: String) = delete("/api/v1/agent/conversations/groups/$groupId/membership")

    override suspend fun listMessages(
        conversationId: String,
        limit: Int,
        offset: Int
    ): MessageListResponse = get(
        "/api/v1/agent/conversations/$conversationId/messages?limit=$limit&offset=$offset"
    )

    override suspend fun sendMessage(
        conversationId: String,
        request: SendMessageRequest
    ): MessageResponse = post(
        "/api/v1/agent/conversations/$conversationId/messages",
        request
    )

    override suspend fun createVoiceUpload(request: VoiceUploadRequest): VoiceUploadResponse =
        post("/api/v1/agent/voice-media/uploads", request)

    override suspend fun uploadVoiceBytes(uploadUrl: String, requiredHeaders: Map<String, String>, bytes: ByteArray) =
        uploadBytes(uploadUrl, requiredHeaders, bytes, "audio/mp4", "VOICE_UPLOAD_FAILED")

    private suspend fun uploadBytes(uploadUrl: String, requiredHeaders: Map<String, String>, bytes: ByteArray, contentType: String, failureCode: String) =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(resolveUrl(uploadUrl))
            requiredHeaders.forEach { (name, value) -> builder.header(name, value) }
            client.newCall(builder.put(bytes.toRequestBody(contentType.toMediaType())).build()).execute().use {
                if (!it.isSuccessful) throw ChatApiException(failureCode, null, it.code)
            }
        }

    override suspend fun completeVoiceUpload(mediaId: String, durationMs: Int): CompleteVoiceResponse =
        post("/api/v1/agent/voice-media/$mediaId/complete", CompleteVoiceRequest(durationMs))

    override suspend fun voicePlayback(mediaId: String): VoicePlaybackResponse =
        get<VoicePlaybackResponse>("/api/v1/agent/voice-media/$mediaId/playback")
            .let { it.copy(playbackUrl = resolveUrl(it.playbackUrl)) }

    override suspend fun createChatMediaUpload(request: ChatMediaUploadRequest): ChatMediaUploadResponse =
        post("/api/v1/agent/chat-media/uploads", request)

    override suspend fun uploadChatMediaFile(
        uploadUrl: String,
        requiredHeaders: Map<String, String>,
        contentType: String,
        file: File
    ) = uploadSignedMediaFile(
        client = mediaUploadClient,
        uploadUrl = resolveUrl(uploadUrl),
        requiredHeaders = requiredHeaders,
        contentType = contentType,
        file = file
    )

    override suspend fun completeChatMediaUpload(mediaId: String, request: CompleteChatMediaRequest): CompleteChatMediaResponse =
        post("/api/v1/agent/chat-media/$mediaId/complete", request)

    override suspend fun chatMediaPlayback(mediaId: String): ChatMediaPlaybackResponse =
        get<ChatMediaPlaybackResponse>("/api/v1/agent/chat-media/$mediaId/playback")
            .let { it.copy(playbackUrl = resolveUrl(it.playbackUrl)) }

    override suspend fun createCall(conversationId: String): VoiceCallResponse =
        post("/api/v1/agent/calls", CreateCallRequest(conversationId))
    override suspend fun acceptCall(callId: String): VoiceCallResponse = postEmpty("/api/v1/agent/calls/$callId/accept")
    override suspend fun rejectCall(callId: String): VoiceCallResponse = postEmpty("/api/v1/agent/calls/$callId/reject")
    override suspend fun cancelCall(callId: String): VoiceCallResponse = postEmpty("/api/v1/agent/calls/$callId/cancel")
    override suspend fun endCall(callId: String): VoiceCallResponse = postEmpty("/api/v1/agent/calls/$callId/end")
    override suspend fun iceServers(): IceServersResponse = get("/api/v1/agent/calls/ice-servers")

    private suspend inline fun <reified T> get(path: String): T =
        execute(Request.Builder().url(url(path)).get().build())

    private suspend inline fun <reified T, reified B> post(path: String, body: B): T {
        val requestBody = json.encodeToString(body)
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        return execute(Request.Builder().url(url(path))
            .post(requestBody).build())
    }
    private suspend inline fun <reified T> postEmpty(path: String): T =
        execute(Request.Builder().url(url(path)).post(ByteArray(0).toRequestBody(null)).build())
    private suspend inline fun <reified B> postUnit(path: String, body: B) { executeUnit(Request.Builder().url(url(path)).post(json.encodeToString(body).toRequestBody("application/json; charset=utf-8".toMediaType())).build()) }
    private suspend inline fun <reified B> put(path: String, body: B) { executeUnit(Request.Builder().url(url(path)).put(json.encodeToString(body).toRequestBody("application/json; charset=utf-8".toMediaType())).build()) }
    private suspend fun delete(path: String) { executeUnit(Request.Builder().url(url(path)).delete().build()) }

    private suspend inline fun <reified T> execute(request: Request): T =
        withContext(Dispatchers.IO) {
            val token = authRepository.validAccessToken()
                ?: throw ChatApiException("NOT_AUTHENTICATED", null, null)
            val authenticated = request.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("X-Request-Id", UUID.randomUUID().toString())
                .build()
            val response = try {
                client.newCall(authenticated).execute()
            } catch (e: IOException) {
                throw ChatApiException("NETWORK_UNAVAILABLE", null, null, e)
            }
            response.use {
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    throw ChatApiException(
                        "REQUEST_FAILED",
                        null,
                        it.code,
                        null,
                        body
                    )
                }
                runCatching { json.decodeFromString<T>(body) }
                    .getOrElse { cause ->
                        throw ChatApiException("INVALID_RESPONSE", null, it.code, cause)
                    }
            }
        }

    private suspend fun executeUnit(request: Request) = withContext(Dispatchers.IO) {
        val token = authRepository.validAccessToken() ?: throw ChatApiException("NOT_AUTHENTICATED", null, null)
        client.newCall(request.newBuilder().header("Authorization", "Bearer $token").header("X-Request-Id", UUID.randomUUID().toString()).build()).execute().use {
            if (!it.isSuccessful) throw ChatApiException("REQUEST_FAILED", null, it.code, null, it.body?.string().orEmpty())
        }
    }

    private fun url(path: String): String {
        if (baseUrl.isBlank()) {
            throw ChatApiException("AGENT_NOT_CONFIGURED")
        }
        return "$baseUrl$path"
    }

    private fun resolveUrl(value: String): String =
        if (value.startsWith("http://") || value.startsWith("https://")) value
        else "$baseUrl/${value.trimStart('/')}"
}

internal suspend fun uploadSignedMediaFile(
    client: OkHttpClient,
    uploadUrl: String,
    requiredHeaders: Map<String, String>,
    contentType: String,
    file: File
) = withContext(Dispatchers.IO) {
    if (!file.isFile || file.length() <= 0L) {
        throw ChatApiException("CHAT_MEDIA_FILE_UNREADABLE")
    }
    val request = Request.Builder()
        .url(uploadUrl)
        .apply { requiredHeaders.forEach { (name, value) -> header(name, value) } }
        .put(file.asRequestBody(contentType.toMediaType()))
        .build()
    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val code = when (response.code) {
                    403 -> "CHAT_MEDIA_UPLOAD_EXPIRED"
                    408, 504 -> "CHAT_MEDIA_UPLOAD_TIMEOUT"
                    else -> "CHAT_MEDIA_UPLOAD_FAILED"
                }
                throw ChatApiException(code, status = response.code)
            }
        }
    } catch (error: ChatApiException) {
        throw error
    } catch (error: SocketTimeoutException) {
        throw ChatApiException("CHAT_MEDIA_UPLOAD_TIMEOUT", cause = error)
    } catch (error: IOException) {
        throw ChatApiException("CHAT_MEDIA_UPLOAD_NETWORK_FAILED", cause = error)
    }
}

class ChatApiException(
    val code: String,
    val requestId: String? = null,
    val status: Int? = null,
    cause: Throwable? = null,
    val responseBody: String? = null
) : RuntimeException(code, cause)
