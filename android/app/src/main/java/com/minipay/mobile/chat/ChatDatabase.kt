package com.minipay.mobile.chat

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ContactEntity::class,
        HiddenConversationEntity::class,
        ConversationRemarkEntity::class,
        SearchHistoryEntity::class,
        PendingTransferReceiptEntity::class
    ],
    version = 13,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
