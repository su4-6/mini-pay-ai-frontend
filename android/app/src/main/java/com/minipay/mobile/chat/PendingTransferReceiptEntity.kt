package com.minipay.mobile.chat

import androidx.room.Entity

@Entity(tableName = "pending_transfer_receipts", primaryKeys = ["ownerUserId", "transferId"])
data class PendingTransferReceiptEntity(
    val ownerUserId: String,
    val transferId: String,
    val conversationId: String,
    val targetUserId: String,
    val targetName: String,
    val conversationType: String,
    val createdAt: Long
)

enum class TransferReceiptConversationType { DIRECT, GROUP }
