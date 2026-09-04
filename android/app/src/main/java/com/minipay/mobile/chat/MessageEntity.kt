package com.minipay.mobile.chat

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "messages",
    primaryKeys = ["ownerUserId", "id"],
    indices = [Index(value = ["ownerUserId", "conversationId", "timestamp"])]
)
data class MessageEntity(
    val ownerUserId: String,
    val id: Long,
    val conversationId: String,
    val senderType: String,
    val senderId: String? = null,
    val senderName: String? = null,
    val senderAvatarUrl: String? = null,
    val senderAvatarUrlExpiresAt: String? = null,
    val content: String,
    val messageType: String,
    val transferAmount: String?,
    val transferStatus: String?,
    val transferDirection: String?,
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
