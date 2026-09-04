package com.minipay.mobile.chat

import androidx.room.Entity

@Entity(tableName = "conversations", primaryKeys = ["ownerUserId", "id"])
data class ConversationEntity(
    val ownerUserId: String,
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
