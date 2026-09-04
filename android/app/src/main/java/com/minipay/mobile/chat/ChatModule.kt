package com.minipay.mobile.chat

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.minipay.mobile.auth.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object ChatModule {

    @Provides
    @Singleton
    fun provideChatDatabase(@ApplicationContext context: Context): ChatDatabase {
        return Room.databaseBuilder(
            context,
            ChatDatabase::class.java,
            "chat.db"
        ).addMigrations(
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13
        ).build()
    }

    @Provides
    @Singleton
    fun provideChatDao(database: ChatDatabase): ChatDao = database.chatDao()

    @Provides
    @Singleton
    fun provideChatApiService(
        client: OkHttpClient,
        json: Json,
        authRepository: AuthRepository
    ): ChatApiService = ChatApi(client, json, authRepository)

    @Provides
    @Singleton
    fun provideFriendApiService(
        client: OkHttpClient,
        json: Json,
        authRepository: AuthRepository
    ): FriendApiService = FriendApi(client, json, authRepository)
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Existing rows have no authenticated owner. Delete this non-authoritative cache
        // instead of assigning it to whichever user signs in after the upgrade.
        database.execSQL("DROP TABLE IF EXISTS messages")
        database.execSQL("DROP TABLE IF EXISTS conversations")
        database.execSQL("DROP TABLE IF EXISTS contacts")
        database.execSQL("CREATE TABLE IF NOT EXISTS `conversations` (`ownerUserId` TEXT NOT NULL, `id` TEXT NOT NULL, `name` TEXT NOT NULL, `lastMessage` TEXT NOT NULL, `lastMessageTime` INTEGER NOT NULL, `unreadCount` INTEGER NOT NULL, `isTransfer` INTEGER NOT NULL, `avatarColorIndex` INTEGER NOT NULL, PRIMARY KEY(`ownerUserId`, `id`))")
        database.execSQL("CREATE TABLE IF NOT EXISTS `contacts` (`ownerUserId` TEXT NOT NULL, `id` TEXT NOT NULL, `name` TEXT NOT NULL, `firstLetter` TEXT NOT NULL, `avatarColorIndex` INTEGER NOT NULL, PRIMARY KEY(`ownerUserId`, `id`))")
        database.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ownerUserId` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `senderType` TEXT NOT NULL, `content` TEXT NOT NULL, `messageType` TEXT NOT NULL, `transferAmount` TEXT, `transferStatus` TEXT, `transferDirection` TEXT, `timestamp` INTEGER NOT NULL)")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `hidden_conversations` (`ownerUserId` TEXT NOT NULL, `conversationId` TEXT NOT NULL, PRIMARY KEY(`ownerUserId`, `conversationId`))")
        database.execSQL("CREATE TABLE IF NOT EXISTS `conversation_remarks` (`ownerUserId` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `remark` TEXT NOT NULL, PRIMARY KEY(`ownerUserId`, `conversationId`))")
    }
}

// Version 5 preserves the schema already shipped to real devices. The feature
// tables were introduced in v4, so no SQL change is required for this bridge.
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) = Unit
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE messages ADD COLUMN senderId TEXT")
        database.execSQL("ALTER TABLE messages ADD COLUMN senderName TEXT")
    }
}

internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `messages_new` (
              `ownerUserId` TEXT NOT NULL,
              `id` INTEGER NOT NULL,
              `conversationId` TEXT NOT NULL,
              `senderType` TEXT NOT NULL,
              `senderId` TEXT,
              `senderName` TEXT,
              `content` TEXT NOT NULL,
              `messageType` TEXT NOT NULL,
              `transferAmount` TEXT,
              `transferStatus` TEXT,
              `transferDirection` TEXT,
              `timestamp` INTEGER NOT NULL,
              PRIMARY KEY(`ownerUserId`, `id`)
            )
        """.trimIndent())
        database.execSQL("""
            INSERT INTO `messages_new` (
              `ownerUserId`, `id`, `conversationId`, `senderType`, `senderId`, `senderName`,
              `content`, `messageType`, `transferAmount`, `transferStatus`, `transferDirection`, `timestamp`
            )
            SELECT `ownerUserId`, `id`, `conversationId`, `senderType`, `senderId`, `senderName`,
                   `content`, `messageType`, `transferAmount`, `transferStatus`, `transferDirection`, `timestamp`
            FROM `messages`
            WHERE `ownerUserId` <> ''
        """.trimIndent())
        database.execSQL("DROP TABLE `messages`")
        database.execSQL("ALTER TABLE `messages_new` RENAME TO `messages`")
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS `index_messages_ownerUserId_conversationId_timestamp`
            ON `messages` (`ownerUserId`, `conversationId`, `timestamp`)
        """.trimIndent())
        database.execSQL("DELETE FROM `conversations` WHERE `ownerUserId` = ''")
        database.execSQL("DELETE FROM `contacts` WHERE `ownerUserId` = ''")
        database.execSQL("DELETE FROM `hidden_conversations` WHERE `ownerUserId` = ''")
        database.execSQL("DELETE FROM `conversation_remarks` WHERE `ownerUserId` = ''")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `friend_search_history` (`ownerUserId` TEXT NOT NULL, `query` TEXT NOT NULL, `searchedAt` INTEGER NOT NULL, PRIMARY KEY(`ownerUserId`, `query`))")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_friend_search_history_ownerUserId_searchedAt` ON `friend_search_history` (`ownerUserId`, `searchedAt`)")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE messages ADD COLUMN voiceMediaId TEXT")
        database.execSQL("ALTER TABLE messages ADD COLUMN voiceDurationMs INTEGER")
        database.execSQL("ALTER TABLE messages ADD COLUMN callId TEXT")
        database.execSQL("ALTER TABLE messages ADD COLUMN callStatus TEXT")
        database.execSQL("ALTER TABLE messages ADD COLUMN callDurationSeconds INTEGER")
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE conversations ADD COLUMN avatarUrl TEXT")
        database.execSQL("ALTER TABLE conversations ADD COLUMN avatarUrlExpiresAt TEXT")
        database.execSQL("ALTER TABLE messages ADD COLUMN transferId TEXT")
        database.execSQL("ALTER TABLE messages ADD COLUMN transferTargetUserId TEXT")
        database.execSQL("CREATE TABLE IF NOT EXISTS `pending_group_transfer_receipts` (`ownerUserId` TEXT NOT NULL, `transferId` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `targetUserId` TEXT NOT NULL, `targetName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`ownerUserId`, `transferId`))")
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE contacts ADD COLUMN avatarUrl TEXT")
        database.execSQL("ALTER TABLE contacts ADD COLUMN avatarUrlExpiresAt TEXT")
        database.execSQL("ALTER TABLE messages ADD COLUMN senderAvatarUrl TEXT")
        database.execSQL("ALTER TABLE messages ADD COLUMN senderAvatarUrlExpiresAt TEXT")
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `pending_transfer_receipts` (`ownerUserId` TEXT NOT NULL, `transferId` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `targetUserId` TEXT NOT NULL, `targetName` TEXT NOT NULL, `conversationType` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`ownerUserId`, `transferId`))")
        database.execSQL("INSERT OR REPLACE INTO `pending_transfer_receipts` (`ownerUserId`, `transferId`, `conversationId`, `targetUserId`, `targetName`, `conversationType`, `createdAt`) SELECT `ownerUserId`, `transferId`, `conversationId`, `targetUserId`, `targetName`, 'GROUP', `createdAt` FROM `pending_group_transfer_receipts`")
        database.execSQL("DROP TABLE IF EXISTS `pending_group_transfer_receipts`")
    }
}

private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE messages ADD COLUMN mediaId TEXT")
        database.execSQL("ALTER TABLE messages ADD COLUMN mediaKind TEXT")
        database.execSQL("ALTER TABLE messages ADD COLUMN mediaContentType TEXT")
        database.execSQL("ALTER TABLE messages ADD COLUMN mediaWidth INTEGER")
        database.execSQL("ALTER TABLE messages ADD COLUMN mediaHeight INTEGER")
        database.execSQL("ALTER TABLE messages ADD COLUMN mediaDurationMs INTEGER")
    }
}
