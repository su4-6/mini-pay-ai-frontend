package com.minipay.mobile.chat

data class Conversation(
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String?,
    val lastMessageTime: Long,
    val unreadCount: Int,
    val isTransfer: Boolean,
    val avatarColorIndex: Int,
    val avatarUrl: String? = null,
    val avatarUrlExpiresAt: String? = null
)

data class Contact(
    val id: String,
    val name: String,
    val firstLetter: String,
    val avatarColorIndex: Int,
    val avatarUrl: String? = null,
    val avatarUrlExpiresAt: String? = null
)

data class ChatMessage(
    val id: Long,
    val conversationId: String,
    val senderType: SenderType,
    val senderId: String? = null,
    val senderName: String? = null,
    val senderAvatarUrl: String? = null,
    val senderAvatarUrlExpiresAt: String? = null,
    val content: String,
    val messageType: MessageType,
    val transferAmount: String?,
    val transferStatus: String?,
    val transferDirection: TransferDirection?,
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

data class ChatTransferTarget(
    val userId: String,
    val name: String,
    val accountMasked: String? = null,
    val avatarUrl: String? = null,
    val avatarUrlExpiresAt: String? = null,
    val conversationId: String? = null,
    val nickname: String = name,
    val miniPayNo: String? = null
)

enum class SenderType { Me, Other }

enum class MessageType { Text, Transfer, Voice, Call, Image, Video }

enum class TransferDirection { Incoming, Outgoing }
