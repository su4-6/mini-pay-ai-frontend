package com.minipay.mobile.chat

import androidx.room.Entity

@Entity(tableName = "hidden_conversations", primaryKeys = ["ownerUserId", "conversationId"])
data class HiddenConversationEntity(
    val ownerUserId: String,
    val conversationId: String
)
