package com.minipay.mobile.chat

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatDatabaseIsolationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ChatDatabase::class.java
    )

    private lateinit var database: ChatDatabase
    private lateinit var dao: ChatDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ChatDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.chatDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun duplicateRemoteIdsAndMutationsRemainOwnerScoped() = runBlocking {
        dao.insertConversation(conversation(OWNER_A, unreadCount = 4, name = "账号 A"))
        dao.insertConversation(conversation(OWNER_B, unreadCount = 7, name = "账号 B"))
        dao.insertMessage(message(OWNER_A, "A 的消息"))
        dao.insertMessage(message(OWNER_B, "B 的消息"))

        assertEquals("A 的消息", dao.observeMessages(OWNER_A, CONVERSATION_ID).first().single().content)
        assertEquals("B 的消息", dao.observeMessages(OWNER_B, CONVERSATION_ID).first().single().content)

        dao.clearUnread(OWNER_A, CONVERSATION_ID)
        dao.updateConversationName(OWNER_A, CONVERSATION_ID, "A 的备注")
        dao.updateConversationLastMessage(OWNER_A, CONVERSATION_ID, "A 新预览", 20L, false, 1)

        assertEquals(1, dao.getConversation(OWNER_A, CONVERSATION_ID)?.unreadCount)
        assertEquals("A 的备注", dao.getConversation(OWNER_A, CONVERSATION_ID)?.name)
        assertEquals("A 新预览", dao.getConversation(OWNER_A, CONVERSATION_ID)?.lastMessage)
        assertEquals(7, dao.getConversation(OWNER_B, CONVERSATION_ID)?.unreadCount)
        assertEquals("账号 B", dao.getConversation(OWNER_B, CONVERSATION_ID)?.name)

        dao.deleteMessages(OWNER_A, CONVERSATION_ID)
        dao.deleteConversationRow(OWNER_A, CONVERSATION_ID)

        assertTrue(dao.observeMessages(OWNER_A, CONVERSATION_ID).first().isEmpty())
        assertEquals(1, dao.observeMessages(OWNER_B, CONVERSATION_ID).first().size)
        assertEquals(null, dao.getConversation(OWNER_A, CONVERSATION_ID))
        assertEquals("账号 B", dao.getConversation(OWNER_B, CONVERSATION_ID)?.name)
    }

    @Test
    fun migrationSixToSevenPreservesOwnedRowsAndDropsUnownedCache() {
        migrationHelper.createDatabase(MIGRATION_DB, 6).apply {
            insertVersionSixRows(this)
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            7,
            true,
            MIGRATION_6_7
        )

        migrated.query("SELECT ownerUserId, id FROM messages ORDER BY ownerUserId").use { cursor ->
            assertEquals(2, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertFalse(cursor.getString(0).isBlank())
        }
        migrated.query("SELECT COUNT(*) FROM conversations WHERE ownerUserId = ''").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("PRAGMA index_list(`messages`)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == MESSAGE_INDEX) found = true
            }
            assertTrue(found)
        }
        migrated.close()
    }

    private fun insertVersionSixRows(database: SupportSQLiteDatabase) {
        database.execSQL(
            "INSERT INTO conversations (ownerUserId,id,name,lastMessage,lastMessageTime,unreadCount,isTransfer,avatarColorIndex) " +
                "VALUES ('$OWNER_A','$CONVERSATION_ID','A','preview',1,0,0,0)," +
                "('','$CONVERSATION_ID','unknown','preview',1,0,0,0)"
        )
        database.execSQL(
            "INSERT INTO messages (id,ownerUserId,conversationId,senderType,senderId,senderName,content,messageType,transferAmount,transferStatus,transferDirection,timestamp) " +
                "VALUES (1,'$OWNER_A','$CONVERSATION_ID','OTHER',NULL,NULL,'A1','TEXT',NULL,NULL,NULL,1)," +
                "(2,'$OWNER_B','$CONVERSATION_ID','OTHER',NULL,NULL,'B1','TEXT',NULL,NULL,NULL,2)," +
                "(3,'','$CONVERSATION_ID','OTHER',NULL,NULL,'unknown','TEXT',NULL,NULL,NULL,3)"
        )
        database.execSQL("INSERT INTO contacts (ownerUserId,id,name,firstLetter,avatarColorIndex) VALUES ('','old','old','#',0)")
    }

    private fun conversation(owner: String, unreadCount: Int, name: String) = ConversationEntity(
        ownerUserId = owner,
        id = CONVERSATION_ID,
        name = name,
        lastMessage = "preview",
        lastMessageTime = 1L,
        unreadCount = unreadCount,
        isTransfer = false,
        avatarColorIndex = 0
    )

    private fun message(owner: String, content: String) = MessageEntity(
        ownerUserId = owner,
        id = MESSAGE_ID,
        conversationId = CONVERSATION_ID,
        senderType = "OTHER",
        content = content,
        messageType = "TEXT",
        transferAmount = null,
        transferStatus = null,
        transferDirection = null,
        timestamp = 1L
    )

    private companion object {
        const val OWNER_A = "018f0f5d-52c7-7b8d-9f22-6f858e711001"
        const val OWNER_B = "018f0f5d-52c7-7b8d-9f22-6f858e711002"
        const val CONVERSATION_ID = "conversation-1"
        const val MESSAGE_ID = 42L
        const val MIGRATION_DB = "chat-migration-6-7"
        const val MESSAGE_INDEX = "index_messages_ownerUserId_conversationId_timestamp"
    }
}
