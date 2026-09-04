package com.minipay.mobile.chat

import com.minipay.mobile.avatar.avatarContentIdentity
import java.time.Instant
import com.minipay.mobile.auth.AuthRepository
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val chatApi: ChatApiService,
    private val friendApi: FriendApiService,
    private val authRepository: AuthRepository
) {
    private val mediaPlaybackCache = ConcurrentHashMap<String, CachedMediaPlayback>()

    fun observeConversations(): Flow<List<Conversation>> =
        authRepository.currentUserId.flatMapLatest { ownerId ->
            if (ownerId == null) flowOf(emptyList()) else chatDao.observeConversations(ownerId)
        }.map { list ->
            list.distinctBy { it.id }.map { entity ->
                Conversation(
                    id = entity.id,
                    name = entity.name,
                    lastMessage = entity.lastMessage,
                    time = formatMessageTime(entity.lastMessageTime),
                    lastMessageTime = entity.lastMessageTime,
                    unreadCount = entity.unreadCount,
                    isTransfer = entity.isTransfer,
                    avatarColorIndex = entity.avatarColorIndex,
                    avatarUrl = entity.avatarUrl,
                    avatarUrlExpiresAt = entity.avatarUrlExpiresAt
                )
            }
        }

    fun observeConversationName(conversationId: String, fallback: String): Flow<String> =
        authRepository.currentUserId.flatMapLatest { ownerId ->
            if (ownerId == null) flowOf(fallback)
            else chatDao.observeConversation(ownerId, conversationId).map { entity ->
                entity?.name?.takeIf { it.isNotBlank() } ?: fallback
            }
        }

    fun observeSearchHistory(): Flow<List<String>> =
        authRepository.currentUserId.flatMapLatest { ownerId ->
            if (ownerId == null) flowOf(emptyList()) else chatDao.observeSearchHistory(ownerId)
        }

    suspend fun recordSearchHistory(query: String) {
        val ownerId = authRepository.currentUserId.value ?: return
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        chatDao.recordSearchHistory(ownerId, normalized, System.currentTimeMillis())
    }

    suspend fun clearSearchHistory() {
        val ownerId = authRepository.currentUserId.value ?: return
        chatDao.clearSearchHistory(ownerId)
    }

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        authRepository.currentUserId.flatMapLatest { ownerId ->
            if (ownerId == null) flowOf(emptyList()) else chatDao.observeMessages(ownerId, conversationId)
        }.map { list ->
            list.map { entity ->
                ChatMessage(
                    id = entity.id,
                    conversationId = entity.conversationId,
                    senderType = SenderType.valueOf(entity.senderType),
                    senderId = entity.senderId,
                    senderName = entity.senderName,
                    senderAvatarUrl = entity.senderAvatarUrl,
                    senderAvatarUrlExpiresAt = entity.senderAvatarUrlExpiresAt,
                    content = entity.content,
                    messageType = MessageType.valueOf(entity.messageType),
                    transferAmount = entity.transferAmount,
                    transferStatus = entity.transferStatus,
                    transferDirection = entity.transferDirection?.let { TransferDirection.valueOf(it) },
                    transferId = entity.transferId,
                    transferTargetUserId = entity.transferTargetUserId,
                    voiceMediaId = entity.voiceMediaId,
                    voiceDurationMs = entity.voiceDurationMs,
                    mediaId = entity.mediaId,
                    mediaKind = entity.mediaKind,
                    mediaContentType = entity.mediaContentType,
                    mediaWidth = entity.mediaWidth,
                    mediaHeight = entity.mediaHeight,
                    mediaDurationMs = entity.mediaDurationMs,
                    callId = entity.callId,
                    callStatus = entity.callStatus,
                    callDurationSeconds = entity.callDurationSeconds,
                    timestamp = entity.timestamp
                )
            }
        }

    fun observeContacts(): Flow<List<Contact>> =
        authRepository.currentUserId.flatMapLatest { ownerId ->
            if (ownerId == null) flowOf(emptyList()) else chatDao.observeContacts(ownerId)
        }.map { list ->
            list.distinctBy { it.id }.map { entity ->
                Contact(
                    id = entity.id,
                    name = entity.name,
                    firstLetter = entity.firstLetter,
                    avatarColorIndex = entity.avatarColorIndex,
                    avatarUrl = entity.avatarUrl,
                    avatarUrlExpiresAt = entity.avatarUrlExpiresAt
                )
            }
        }

    // --- Remote sync ---

    suspend fun syncConversations(): Boolean {
        return try {
            val ownerId = authRepository.currentUserId.value ?: return false
            val friends = runCatching { friendApi.listFriends() }.getOrNull()
            val contacts = friends?.let { contactsForOwner(ownerId, it) }
            val friendNames = friends.orEmpty()
                .mapNotNull { friend -> friend.conversationId?.let { it to friend.nickname } }
                .toMap()
            val friendsByConversation = friends.orEmpty().mapNotNull { friend ->
                friend.conversationId?.let { it to friend }
            }.toMap()
            val remote = chatApi.listConversations()
            val authorizedIds = remote.map { it.id }
            val conversations = mutableListOf<ConversationEntity>()
            remote.forEach { conv ->
                val name = resolveConversationDisplayName(
                    isGroup = conv.id.startsWith("group_"),
                    remark = chatDao.getRemark(ownerId, conv.id),
                    friendNickname = friendNames[conv.id],
                    cachedName = chatDao.getConversation(ownerId, conv.id)?.name,
                    serverName = conv.name
                )
                val cached = chatDao.getConversation(ownerId, conv.id)
                val friend = friendsByConversation[conv.id]
                val remoteAvatar = selectUsableAvatar(
                    AvatarReference(friend?.avatarUrl, friend?.avatarUrlExpiresAt),
                    AvatarReference(conv.avatarUrl, conv.avatarUrlExpiresAt)
                )
                val avatar = selectStableAvatar(
                    remote = remoteAvatar,
                    cached = AvatarReference(cached?.avatarUrl, cached?.avatarUrlExpiresAt)
                )
                conversations +=
                    ConversationEntity(
                        ownerUserId = ownerId,
                        id = conv.id,
                        name = name,
                        lastMessage = conv.lastMessage,
                        lastMessageTime = conv.lastMessageTime,
                        unreadCount = conv.unreadCount,
                        isTransfer = conv.isTransfer,
                        avatarColorIndex = conv.avatarColorIndex,
                        avatarUrl = avatar?.url,
                        avatarUrlExpiresAt = avatar?.expiresAt
                    )
            }
            chatDao.replaceSynchronizedChatState(ownerId, authorizedIds, contacts, conversations)
            retryPendingTransferReceipts(ownerId)
            true
        } catch (_: ChatApiException) {
            false
        }
    }

    suspend fun syncMessages(conversationId: String): Boolean {
        return try {
            val ownerId = authRepository.currentUserId.value ?: return false
            retryPendingTransferReceipts(ownerId)
            val response = chatApi.listMessages(conversationId)
            val displayNames = mutableMapOf<String, String>()
            if (conversationId.startsWith("group_")) {
                getGroupDetail(conversationId)?.members.orEmpty().forEach { member ->
                    displayNames[member.userId] = member.displayName ?: displayGroupMemberName(member)
                }
            }
            val synchronizedMessages = response.messages.map { msg ->
                    MessageEntity(
                        id = msg.id,
                        ownerUserId = ownerId,
                        conversationId = msg.conversationId,
                        senderType = msg.senderType,
                        senderId = msg.senderId,
                        senderName = displayNames[msg.senderId] ?: msg.senderNickname ?: msg.senderOriginalNickname,
                        senderAvatarUrl = msg.senderAvatarUrl,
                        senderAvatarUrlExpiresAt = msg.senderAvatarUrlExpiresAt,
                        content = msg.content,
                        messageType = msg.messageType,
                        transferAmount = msg.transferAmount,
                        transferStatus = msg.transferStatus,
                        transferDirection = msg.transferDirection,
                        transferId = msg.transferId,
                        transferTargetUserId = msg.transferTargetUserId,
                        voiceMediaId = msg.voiceMediaId,
                        voiceDurationMs = msg.voiceDurationMs,
                        mediaId = msg.mediaId,
                        mediaKind = msg.mediaKind,
                        mediaContentType = msg.mediaContentType,
                        mediaWidth = msg.mediaWidth,
                        mediaHeight = msg.mediaHeight,
                        mediaDurationMs = msg.mediaDurationMs,
                        callId = msg.callId,
                        callStatus = msg.callStatus,
                        callDurationSeconds = msg.callDurationSeconds,
                        timestamp = msg.timestamp
                    )
            }
            chatDao.replaceMessages(ownerId, conversationId, synchronizedMessages)
            true
        } catch (_: ChatApiException) {
            false
        }
    }

    suspend fun sendMessage(
        conversationId: String,
        content: String,
        messageType: String = "Text",
        transferAmount: String? = null,
        transferStatus: String? = null,
        transferDirection: String? = null,
        transferId: String? = null,
        transferTargetUserId: String? = null,
        voiceMediaId: String? = null,
        voiceDurationMs: Int? = null,
        mediaId: String? = null,
        contactId: String? = null
    ): ChatMessage? {
        return try {
            val ownerId = authRepository.currentUserId.value ?: return null
            val response = chatApi.sendMessage(
                conversationId,
                SendMessageRequest(
                    content = content,
                    messageType = messageType,
                    transferAmount = transferAmount,
                    transferStatus = transferStatus,
                    transferDirection = transferDirection,
                    transferId = transferId,
                    transferTargetUserId = transferTargetUserId,
                    voiceMediaId = voiceMediaId,
                    voiceDurationMs = voiceDurationMs,
                    mediaId = mediaId,
                    contactId = contactId
                )
            )
            // Insert the returned message into local Room
            val entity = MessageEntity(
                id = response.id,
                ownerUserId = ownerId,
                conversationId = response.conversationId,
                senderType = response.senderType,
                senderId = response.senderId,
                senderName = response.senderNickname ?: response.senderOriginalNickname,
                senderAvatarUrl = response.senderAvatarUrl,
                senderAvatarUrlExpiresAt = response.senderAvatarUrlExpiresAt,
                content = response.content,
                messageType = response.messageType,
                transferAmount = response.transferAmount,
                transferStatus = response.transferStatus,
                transferDirection = response.transferDirection,
                transferId = response.transferId,
                transferTargetUserId = response.transferTargetUserId,
                voiceMediaId = response.voiceMediaId,
                voiceDurationMs = response.voiceDurationMs,
                mediaId = response.mediaId,
                mediaKind = response.mediaKind,
                mediaContentType = response.mediaContentType,
                mediaWidth = response.mediaWidth,
                mediaHeight = response.mediaHeight,
                mediaDurationMs = response.mediaDurationMs,
                callId = response.callId,
                callStatus = response.callStatus,
                callDurationSeconds = response.callDurationSeconds,
                timestamp = response.timestamp
            )
            chatDao.insertMessage(entity)
            // Also update conversation preview
            chatDao.updateConversationLastMessage(
                ownerUserId = ownerId,
                conversationId = conversationId,
                lastMessage = response.content,
                lastMessageTime = response.timestamp,
                isTransfer = response.messageType == "Transfer",
                unreadIncrement = 0
            )
            ChatMessage(
                id = response.id,
                conversationId = response.conversationId,
                senderType = SenderType.valueOf(response.senderType),
                senderId = response.senderId,
                senderName = response.senderNickname ?: response.senderOriginalNickname,
                senderAvatarUrl = response.senderAvatarUrl,
                senderAvatarUrlExpiresAt = response.senderAvatarUrlExpiresAt,
                content = response.content,
                messageType = MessageType.valueOf(response.messageType),
                transferAmount = response.transferAmount,
                transferStatus = response.transferStatus,
                transferDirection = response.transferDirection?.let { TransferDirection.valueOf(it) },
                transferId = response.transferId,
                transferTargetUserId = response.transferTargetUserId,
                voiceMediaId = response.voiceMediaId,
                voiceDurationMs = response.voiceDurationMs,
                mediaId = response.mediaId,
                mediaKind = response.mediaKind,
                mediaContentType = response.mediaContentType,
                mediaWidth = response.mediaWidth,
                mediaHeight = response.mediaHeight,
                mediaDurationMs = response.mediaDurationMs,
                callId = response.callId,
                callStatus = response.callStatus,
                callDurationSeconds = response.callDurationSeconds,
                timestamp = response.timestamp
            )
        } catch (e: ChatApiException) {
            null
        }
    }

    suspend fun sendVoiceMessage(conversationId: String, file: File, durationMs: Int): Result<ChatMessage> =
        runCatching {
            val bytes = voiceStep(VoiceSendStage.PREPARE_RECORDING) { file.readBytes() }
            val sha256 = voiceStep(VoiceSendStage.PREPARE_RECORDING) {
                MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
            }
            val upload = voiceStep(VoiceSendStage.REQUEST_UPLOAD) {
                chatApi.createVoiceUpload(
                    VoiceUploadRequest(conversationId, "audio/mp4", bytes.size.toLong(), sha256)
                )
            }
            voiceStep(VoiceSendStage.UPLOAD_BYTES) {
                chatApi.uploadVoiceBytes(upload.uploadUrl, upload.requiredHeaders, bytes)
            }
            voiceStep(VoiceSendStage.COMPLETE_UPLOAD) {
                chatApi.completeVoiceUpload(upload.mediaId, durationMs)
            }
            voiceStep(VoiceSendStage.SEND_MESSAGE) {
                sendMessage(
                    conversationId = conversationId,
                    content = "[语音] ${kotlin.math.ceil(durationMs / 1000.0).toInt()}\"",
                    messageType = "Voice",
                    voiceMediaId = upload.mediaId,
                    voiceDurationMs = durationMs
                ) ?: throw ChatApiException("VOICE_MESSAGE_SEND_FAILED")
            }
        }

    private suspend fun <T> voiceStep(stage: VoiceSendStage, block: suspend () -> T): T =
        try {
            block()
        } catch (error: Throwable) {
            throw VoiceSendException(stage, error)
        }

    suspend fun voicePlaybackUrl(mediaId: String): String? =
        try { chatApi.voicePlayback(mediaId).playbackUrl } catch (_: ChatApiException) { null }

    suspend fun sendMediaMessage(conversationId: String, media: PreparedChatMedia): Result<ChatMessage> =
        runCatching {
            val (sizeBytes, sha256) = chatMediaStep(ChatMediaSendStage.PREPARE_FILE) {
                val size = media.file.length()
                if (!media.file.isFile || size <= 0L) {
                    throw ChatApiException("CHAT_MEDIA_FILE_UNREADABLE")
                }
                if (media.messageType != MessageType.Image) {
                    throw ChatApiException("CHAT_MEDIA_TYPE_UNSUPPORTED")
                }
                if (size > MAX_CHAT_IMAGE_BYTES) throw ChatApiException("CHAT_MEDIA_FILE_TOO_LARGE")
                size to sha256(media.file)
            }
            val upload = chatMediaStep(ChatMediaSendStage.REQUEST_UPLOAD) {
                chatApi.createChatMediaUpload(
                    ChatMediaUploadRequest(
                        conversationId = conversationId,
                        mediaKind = media.messageType.name,
                        contentType = media.contentType,
                        sizeBytes = sizeBytes,
                        sha256 = sha256
                    )
                )
            }
            chatMediaStep(ChatMediaSendStage.UPLOAD_FILE) {
                chatApi.uploadChatMediaFile(
                    upload.uploadUrl,
                    upload.requiredHeaders,
                    media.contentType,
                    media.file
                )
            }
            chatMediaStep(ChatMediaSendStage.COMPLETE_UPLOAD) {
                chatApi.completeChatMediaUpload(
                    upload.mediaId,
                    CompleteChatMediaRequest(media.width, media.height, media.durationMs)
                )
            }
            chatMediaStep(ChatMediaSendStage.SEND_MESSAGE) {
                sendMessage(
                    conversationId = conversationId,
                    content = if (media.messageType == MessageType.Image) "[图片]" else "[视频]",
                    messageType = media.messageType.name,
                    mediaId = upload.mediaId
                ) ?: throw ChatApiException("CHAT_MEDIA_MESSAGE_SEND_FAILED")
            }
        }

    private suspend fun <T> chatMediaStep(stage: ChatMediaSendStage, block: suspend () -> T): T =
        try {
            block()
        } catch (error: Throwable) {
            throw ChatMediaSendException(stage, error)
        }

    suspend fun chatMediaPlaybackUrl(mediaId: String, forceRefresh: Boolean = false): String? {
        val cached = mediaPlaybackCache[mediaId]
        if (!forceRefresh && cached != null && cached.expiresAt.isAfter(Instant.now().plusSeconds(10))) {
            return cached.url
        }
        return try {
            val response = chatApi.chatMediaPlayback(mediaId)
            val expiresAt = runCatching { Instant.parse(response.expiresAt) }
                .getOrElse { Instant.now().plusSeconds(60) }
            mediaPlaybackCache[mediaId] = CachedMediaPlayback(response.playbackUrl, expiresAt)
            response.playbackUrl
        } catch (_: ChatApiException) {
            null
        }
    }

    // --- Remote contacts sync ---

    suspend fun syncContactsFromServer(): Boolean {
        return try {
            val ownerId = authRepository.currentUserId.value ?: return false
            val friends = friendApi.listFriends()
            replaceContacts(ownerId, friends)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun receivedFriendRequests(): List<ReceivedRequest> = friendApi.getReceivedRequests()

    suspend fun acceptFriendRequest(requestId: String): Boolean = runCatching {
        friendApi.acceptRequest(requestId)
        syncContactsFromServer()
        true
    }.getOrDefault(false)

    suspend fun rejectFriendRequest(requestId: String): Boolean = runCatching {
        friendApi.rejectRequest(requestId)
        true
    }.getOrDefault(false)

    suspend fun ensureConversation(contactId: String, name: String): Conversation? {
        return try {
            val ownerId = authRepository.currentUserId.value ?: return null
            val conv = chatApi.createConversation(
                CreateConversationRequest(contactId = contactId, name = name)
            )
            val displayName = chatDao.getRemark(ownerId, conv.id)
                ?.takeIf { it.isNotBlank() } ?: name
            chatDao.insertConversation(
                ConversationEntity(
                    ownerUserId = ownerId,
                    id = conv.id,
                    name = displayName,
                    lastMessage = conv.lastMessage,
                    lastMessageTime = conv.lastMessageTime,
                    unreadCount = conv.unreadCount,
                    isTransfer = conv.isTransfer,
                    avatarColorIndex = conv.avatarColorIndex,
                    avatarUrl = conv.avatarUrl,
                    avatarUrlExpiresAt = conv.avatarUrlExpiresAt
                )
            )
            Conversation(
                id = conv.id,
                name = displayName,
                lastMessage = conv.lastMessage,
                time = formatMessageTime(conv.lastMessageTime),
                lastMessageTime = conv.lastMessageTime,
                unreadCount = conv.unreadCount,
                isTransfer = conv.isTransfer,
                avatarColorIndex = conv.avatarColorIndex,
                avatarUrl = conv.avatarUrl,
                avatarUrlExpiresAt = conv.avatarUrlExpiresAt
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clearUnread(conversationId: String) {
        val ownerId = authRepository.currentUserId.value ?: return
        chatDao.clearUnread(ownerId, conversationId)
    }

    suspend fun deleteConversation(conversationId: String): Boolean = runCatching {
        chatApi.deleteConversation(conversationId)
        deleteConversationLocally(conversationId)
        true
    }.getOrDefault(false)

    private suspend fun deleteConversationLocally(conversationId: String) {
        val ownerId = authRepository.currentUserId.value ?: return
        chatDao.deleteMessages(ownerId, conversationId)
        chatDao.deleteConversationRow(ownerId, conversationId)
    }

    suspend fun saveRemark(conversationId: String, remark: String) {
        val ownerId = authRepository.currentUserId.value ?: return
        val saved = remark.trim()
        chatDao.saveRemark(ConversationRemarkEntity(ownerId, conversationId, saved))
        val friend = runCatching { friendApi.listFriends() }.getOrNull()
            ?.firstOrNull { it.conversationId == conversationId }
        val displayName = saved.takeIf { it.isNotBlank() } ?: friend?.nickname
        if (displayName != null) {
            chatDao.updateConversationName(ownerId, conversationId, displayName)
        }
        if (friend != null && displayName != null) {
            chatDao.updateContactName(
                ownerId, friend.userId, displayName,
                displayName.firstOrNull()?.uppercase() ?: "#"
            )
        }
    }

    private suspend fun replaceContacts(ownerId: String, friends: List<FriendResponse>) {
        // The server is authoritative for relationship membership; remarks stay private to this account.
        chatDao.replaceContactsForOwner(ownerId, contactsForOwner(ownerId, friends))
    }

    private suspend fun contactsForOwner(ownerId: String, friends: List<FriendResponse>): List<ContactEntity> {
        val cachedById = if (friends.isEmpty()) emptyMap() else
            chatDao.getContactsByIds(ownerId, friends.map(FriendResponse::userId)).associateBy(ContactEntity::id)
        return friends.map { friend ->
            val displayName = friend.conversationId?.let { chatDao.getRemark(ownerId, it) }
                ?.takeIf { it.isNotBlank() } ?: friend.nickname
            val cached = cachedById[friend.userId]
            val avatar = selectStableAvatar(
                remote = AvatarReference(friend.avatarUrl, friend.avatarUrlExpiresAt),
                cached = AvatarReference(cached?.avatarUrl, cached?.avatarUrlExpiresAt)
            )
            ContactEntity(
                ownerUserId = ownerId,
                id = friend.userId,
                name = displayName,
                firstLetter = displayName.firstOrNull()?.uppercase() ?: "#",
                avatarColorIndex = kotlin.math.abs(friend.userId.hashCode() % 8),
                avatarUrl = avatar?.url,
                avatarUrlExpiresAt = avatar?.expiresAt
            )
        }
    }

    suspend fun deleteFriendAndConversation(conversationId: String): Boolean {
        return try {
            val friend = friendApi.listFriends()
                .firstOrNull { it.conversationId == conversationId }
                ?: return false
            friendApi.deleteFriend(friend.userId)
            deleteConversation(conversationId)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun findTransferTarget(conversationId: String, fallbackName: String): ChatTransferTarget? =
        runCatching {
            friendApi.listFriends().firstOrNull { it.conversationId == conversationId }?.let {
                val ownerId = authRepository.currentUserId.value
                val displayName = ownerId?.let { owner -> chatDao.getRemark(owner, conversationId) }
                    ?.takeIf(String::isNotBlank) ?: it.nickname.ifBlank { fallbackName }
                ChatTransferTarget(
                    userId = it.userId,
                    name = displayName,
                    accountMasked = it.phoneMasked,
                    avatarUrl = it.avatarUrl,
                    avatarUrlExpiresAt = it.avatarUrlExpiresAt,
                    conversationId = it.conversationId,
                    nickname = it.nickname,
                    miniPayNo = it.minipayNo
                )
            }
        }.getOrNull()

    suspend fun listTransferTargets(): List<ChatTransferTarget> {
        val ownerId = authRepository.currentUserId.value ?: return emptyList()
        return friendApi.listFriends().map { friend ->
            val displayName = friend.conversationId
                ?.let { chatDao.getRemark(ownerId, it) }
                ?.takeIf(String::isNotBlank)
                ?: friend.nickname
            ChatTransferTarget(
                userId = friend.userId,
                name = displayName,
                accountMasked = friend.phoneMasked,
                avatarUrl = friend.avatarUrl,
                avatarUrlExpiresAt = friend.avatarUrlExpiresAt,
                conversationId = friend.conversationId,
                nickname = friend.nickname,
                miniPayNo = friend.minipayNo
            )
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun getGroupDetail(groupId: String): GroupDetailResponse? = runCatching {
        val detail = chatApi.getGroupDetail(groupId)
        detail.copy(members = detail.members.map { it.copy(displayName = displayGroupMemberName(it)) })
    }.getOrNull()

    suspend fun displayGroupMemberName(member: GroupMemberResponse): String {
        val friend = runCatching { friendApi.listFriends() }.getOrDefault(emptyList())
            .firstOrNull { it.userId == member.userId }
        return resolveGroupMemberDisplayName(member, friend?.nickname)
    }

    suspend fun addGroupMembers(groupId: String, members: List<GroupMemberInput>): Boolean = runCatching { chatApi.addGroupMembers(groupId, members); true }.getOrDefault(false)
    suspend fun removeGroupMember(groupId: String, memberId: String): Boolean = runCatching { chatApi.removeGroupMember(groupId, memberId); true }.getOrDefault(false)
    suspend fun renameGroup(groupId: String, name: String): Boolean = runCatching {
        val ownerId = authRepository.currentUserId.value ?: return@runCatching false
        val normalized = name.trim()
        chatApi.renameGroup(groupId, normalized)
        chatDao.updateConversationName(ownerId, groupId, normalized)
        true
    }.getOrDefault(false)
    suspend fun updateMyGroupNickname(groupId: String, nickname: String): Boolean = runCatching { chatApi.updateMyGroupNickname(groupId, nickname); true }.getOrDefault(false)
    suspend fun updateGroupAvatar(groupId: String, jpegBytes: ByteArray): Result<GroupAvatarResponse> = runCatching {
        val ownerId = authRepository.currentUserId.value
            ?: throw ChatApiException("AUTH_REQUIRED")
        val digest = MessageDigest.getInstance("SHA-256").digest(jpegBytes).joinToString("") { "%02x".format(it) }
        val upload = chatApi.createGroupAvatarUpload(groupId, GroupAvatarUploadRequest("image/jpeg", jpegBytes.size.toLong(), digest))
        chatApi.uploadGroupAvatarBytes(upload.uploadUrl, upload.requiredHeaders, jpegBytes)
        chatApi.completeGroupAvatarUpload(groupId, upload.uploadId).also { avatar ->
            chatDao.updateConversationAvatar(
                ownerUserId = ownerId,
                conversationId = groupId,
                avatarUrl = avatar.avatarUrl,
                expiresAt = avatar.avatarUrlExpiresAt
            )
        }
    }

    suspend fun queueTransferReceipt(
        conversationId: String,
        transferId: String,
        targetUserId: String,
        targetName: String,
        conversationType: TransferReceiptConversationType
    ): Boolean {
        val ownerId = authRepository.currentUserId.value ?: return false
        val receipt = PendingTransferReceiptEntity(
            ownerUserId = ownerId,
            transferId = transferId,
            conversationId = conversationId,
            targetUserId = targetUserId,
            targetName = targetName,
            conversationType = conversationType.name,
            createdAt = System.currentTimeMillis()
        )
        chatDao.insertPendingTransferReceipt(receipt)
        return sendPendingTransferReceipt(ownerId, receipt)
    }

    private suspend fun retryPendingTransferReceipts(ownerId: String) {
        chatDao.getPendingTransferReceipts(ownerId).forEach { sendPendingTransferReceipt(ownerId, it) }
    }

    private suspend fun sendPendingTransferReceipt(ownerId: String, receipt: PendingTransferReceiptEntity): Boolean {
        val conversationId = if (receipt.conversationType == TransferReceiptConversationType.DIRECT.name) {
            ensureConversation(receipt.targetUserId, receipt.targetName)?.id ?: return false
        } else {
            receipt.conversationId
        }
        val sent = sendMessage(
            conversationId = conversationId,
            content = "转账给${receipt.targetName}",
            messageType = "Transfer",
            transferId = receipt.transferId,
            transferTargetUserId = receipt.targetUserId
        ) != null
        if (sent) chatDao.deletePendingTransferReceipt(ownerId, receipt.transferId)
        return sent
    }
    suspend fun disbandGroup(groupId: String): Boolean = runCatching { chatApi.disbandGroup(groupId); deleteConversationLocally(groupId); true }.getOrDefault(false)
    suspend fun leaveGroup(groupId: String): Boolean = runCatching { chatApi.leaveGroup(groupId); deleteConversationLocally(groupId); true }.getOrDefault(false)

    suspend fun createGroup(memberIds: List<String>): Conversation? {
        if (memberIds.isEmpty()) return null
        val ownerId = authRepository.currentUserId.value ?: return null
        val contacts = chatDao.getContactsByIds(ownerId, memberIds)
        if (contacts.isEmpty()) return null
        val groupName = contacts.joinToString(
            separator = "、",
            limit = 3,
            truncated = "等${contacts.size}人"
        ) { it.name }.let { if (it.length > 20) "群聊(${contacts.size})" else it }
        val remote = try {
            chatApi.createGroup(CreateGroupRequest(groupName, contacts.map { GroupMemberInput(it.id, it.name) }))
        } catch (_: ChatApiException) {
            return null
        }
        val conversationEntity = ConversationEntity(
            ownerUserId = ownerId,
            id = remote.id,
            name = remote.name,
            lastMessage = remote.lastMessage,
            lastMessageTime = remote.lastMessageTime,
            unreadCount = remote.unreadCount,
            isTransfer = remote.isTransfer,
            avatarColorIndex = remote.avatarColorIndex
        )
        chatDao.insertConversation(conversationEntity)
        return Conversation(
            id = conversationEntity.id,
            name = conversationEntity.name,
            lastMessage = conversationEntity.lastMessage,
            time = formatMessageTime(conversationEntity.lastMessageTime),
            lastMessageTime = conversationEntity.lastMessageTime,
            unreadCount = conversationEntity.unreadCount,
            isTransfer = conversationEntity.isTransfer,
            avatarColorIndex = conversationEntity.avatarColorIndex
        )
    }

    private fun formatMessageTime(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val zone = ZoneId.systemDefault()
        val dateTime = LocalDateTime.ofInstant(instant, zone)
        val now = LocalDateTime.now(zone)

        return when {
            isSameDay(dateTime, now) -> DateTimeFormatter.ofPattern("HH:mm").format(dateTime)
            isSameDay(dateTime, now.minusDays(1)) -> "昨天"
            isSameWeek(dateTime, now) -> {
                val dayOfWeek = dateTime.dayOfWeek.value
                listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[dayOfWeek - 1]
            }
            else -> DateTimeFormatter.ofPattern("M月d日").format(dateTime)
        }
    }

    private fun isSameDay(a: LocalDateTime, b: LocalDateTime): Boolean =
        a.toLocalDate() == b.toLocalDate()

    private fun isSameWeek(a: LocalDateTime, b: LocalDateTime): Boolean {
        val startOfWeekA = a.toLocalDate().minusDays((a.dayOfWeek.value - 1).toLong())
        val startOfWeekB = b.toLocalDate().minusDays((b.dayOfWeek.value - 1).toLong())
        return startOfWeekA == startOfWeekB
    }
}

internal data class AvatarReference(val url: String?, val expiresAt: String?)

internal fun selectUsableAvatar(
    vararg candidates: AvatarReference,
    now: Instant = Instant.now()
): AvatarReference? = candidates.firstOrNull { candidate ->
    if (candidate.url.isNullOrBlank()) return@firstOrNull false
    val expiresAt = candidate.expiresAt?.takeIf(String::isNotBlank) ?: return@firstOrNull true
    runCatching { Instant.parse(expiresAt) }
        .getOrNull()
        ?.isAfter(now.plusSeconds(30)) == true
}

internal fun selectStableAvatar(
    remote: AvatarReference?,
    cached: AvatarReference?,
    now: Instant = Instant.now()
): AvatarReference? {
    val usableRemote = remote?.let { selectUsableAvatar(it, now = now) }
    val usableCached = cached?.let { selectUsableAvatar(it, now = now) }
    if (usableRemote == null) return usableCached
    if (usableCached == null) return usableRemote

    return if (avatarContentIdentity(usableRemote.url.orEmpty()) ==
        avatarContentIdentity(usableCached.url.orEmpty())) {
        usableCached
    } else {
        usableRemote
    }
}

internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun resolveConversationDisplayName(
    isGroup: Boolean,
    remark: String?,
    friendNickname: String?,
    cachedName: String?,
    serverName: String
): String {
    if (isGroup) return serverName
    return remark?.takeIf { it.isNotBlank() }
        ?: friendNickname?.takeIf { it.isNotBlank() }
        ?: cachedName?.takeIf { it.isNotBlank() }
        ?: serverName
}

internal fun resolveGroupMemberDisplayName(
    member: GroupMemberResponse,
    friendNickname: String?
): String = member.nickname?.takeIf { it.isNotBlank() }
    ?: member.originalNickname?.takeIf { it.isNotBlank() }
    ?: friendNickname?.takeIf { it.isNotBlank() }
    ?: "群成员"

private data class CachedMediaPlayback(val url: String, val expiresAt: Instant)
