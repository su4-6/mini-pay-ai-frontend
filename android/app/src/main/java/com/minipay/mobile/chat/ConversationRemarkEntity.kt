package com.minipay.mobile.chat

import androidx.room.Entity

@Entity(tableName = "conversation_remarks", primaryKeys = ["ownerUserId", "conversationId"])
data class ConversationRemarkEntity(
    val ownerUserId: String,
    val conversationId: String,
    val remark: String
)
