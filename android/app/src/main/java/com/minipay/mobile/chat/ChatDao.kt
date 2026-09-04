package com.minipay.mobile.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM conversations WHERE ownerUserId = :ownerUserId ORDER BY lastMessageTime DESC")
    fun observeConversations(ownerUserId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE ownerUserId = :ownerUserId AND id = :conversationId LIMIT 1")
    fun observeConversation(ownerUserId: String, conversationId: String): Flow<ConversationEntity?>

    @Query("SELECT query FROM friend_search_history WHERE ownerUserId = :ownerUserId ORDER BY searchedAt DESC, query ASC LIMIT 20")
    fun observeSearchHistory(ownerUserId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(entity: SearchHistoryEntity)

    @Query("DELETE FROM friend_search_history WHERE ownerUserId = :ownerUserId AND query NOT IN (SELECT query FROM friend_search_history WHERE ownerUserId = :ownerUserId ORDER BY searchedAt DESC, query ASC LIMIT 20)")
    suspend fun trimSearchHistory(ownerUserId: String)

    @Query("DELETE FROM friend_search_history WHERE ownerUserId = :ownerUserId")
    suspend fun clearSearchHistory(ownerUserId: String)

    @Transaction
    suspend fun recordSearchHistory(ownerUserId: String, query: String, searchedAt: Long) {
        insertSearchHistory(SearchHistoryEntity(ownerUserId, query, searchedAt))
        trimSearchHistory(ownerUserId)
    }

    @Query("SELECT * FROM messages WHERE ownerUserId = :ownerUserId AND conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeMessages(ownerUserId: String, conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM conversations WHERE ownerUserId = :ownerUserId AND id = :conversationId LIMIT 1")
    suspend fun getConversation(ownerUserId: String, conversationId: String): ConversationEntity?

    @Query("SELECT COUNT(*) FROM conversations WHERE ownerUserId = :ownerUserId")
    suspend fun conversationCount(ownerUserId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(entity: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(entities: List<ConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(entity: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingTransferReceipt(entity: PendingTransferReceiptEntity)

    @Query("SELECT * FROM pending_transfer_receipts WHERE ownerUserId = :ownerUserId ORDER BY createdAt ASC")
    suspend fun getPendingTransferReceipts(ownerUserId: String): List<PendingTransferReceiptEntity>

    @Query("DELETE FROM pending_transfer_receipts WHERE ownerUserId = :ownerUserId AND transferId = :transferId")
    suspend fun deletePendingTransferReceipt(ownerUserId: String, transferId: String)

    @Query("SELECT * FROM contacts WHERE ownerUserId = :ownerUserId ORDER BY firstLetter ASC, name ASC")
    fun observeContacts(ownerUserId: String): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE ownerUserId = :ownerUserId AND id IN (:ids) ORDER BY name ASC")
    suspend fun getContactsByIds(ownerUserId: String, ids: List<String>): List<ContactEntity>

    @Query("SELECT COUNT(*) FROM contacts WHERE ownerUserId = :ownerUserId")
    suspend fun contactCount(ownerUserId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(entity: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(entities: List<ContactEntity>)

    @Query("DELETE FROM contacts WHERE ownerUserId = :ownerUserId")
    suspend fun deleteContactsForOwner(ownerUserId: String)

    @Transaction
    suspend fun replaceContactsForOwner(ownerUserId: String, contacts: List<ContactEntity>) {
        deleteContactsForOwner(ownerUserId)
        insertContacts(contacts)
    }

    @Transaction
    suspend fun replaceSynchronizedChatState(
        ownerUserId: String,
        authorizedIds: List<String>,
        contacts: List<ContactEntity>?,
        conversations: List<ConversationEntity>
    ) {
        if (authorizedIds.isEmpty()) {
            deleteMessagesForOwner(ownerUserId)
            deleteConversationsForOwner(ownerUserId)
        } else {
            deleteMessagesNotIn(ownerUserId, authorizedIds)
            deleteConversationsNotIn(ownerUserId, authorizedIds)
        }
        if (contacts != null) {
            deleteContactsForOwner(ownerUserId)
            insertContacts(contacts)
        }
        insertConversations(conversations)
    }

    @Query("UPDATE contacts SET name = :name, firstLetter = :firstLetter WHERE ownerUserId = :ownerUserId AND id = :contactId")
    suspend fun updateContactName(ownerUserId: String, contactId: String, name: String, firstLetter: String)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE ownerUserId = :ownerUserId AND id = :conversationId")
    suspend fun clearUnread(ownerUserId: String, conversationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideConversation(entity: HiddenConversationEntity)

    @Query("SELECT COUNT(*) > 0 FROM hidden_conversations WHERE ownerUserId = :ownerUserId AND conversationId = :conversationId")
    suspend fun isConversationHidden(ownerUserId: String, conversationId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRemark(entity: ConversationRemarkEntity)

    @Query("SELECT remark FROM conversation_remarks WHERE ownerUserId = :ownerUserId AND conversationId = :conversationId LIMIT 1")
    suspend fun getRemark(ownerUserId: String, conversationId: String): String?

    @Query("UPDATE conversations SET name = :name WHERE ownerUserId = :ownerUserId AND id = :conversationId")
    suspend fun updateConversationName(ownerUserId: String, conversationId: String, name: String)

    @Query("UPDATE conversations SET avatarUrl = :avatarUrl, avatarUrlExpiresAt = :expiresAt WHERE ownerUserId = :ownerUserId AND id = :conversationId")
    suspend fun updateConversationAvatar(
        ownerUserId: String,
        conversationId: String,
        avatarUrl: String?,
        expiresAt: String?
    )

    @Query("DELETE FROM messages WHERE ownerUserId = :ownerUserId AND conversationId = :conversationId")
    suspend fun deleteMessages(ownerUserId: String, conversationId: String)

    @Transaction
    suspend fun replaceMessages(
        ownerUserId: String,
        conversationId: String,
        messages: List<MessageEntity>
    ) {
        deleteMessages(ownerUserId, conversationId)
        messages.forEach { insertMessage(it) }
    }

    @Query("DELETE FROM conversations WHERE ownerUserId = :ownerUserId AND id = :conversationId")
    suspend fun deleteConversationRow(ownerUserId: String, conversationId: String)

    @Query("DELETE FROM conversations WHERE ownerUserId = :ownerUserId")
    suspend fun deleteConversationsForOwner(ownerUserId: String)

    @Query("DELETE FROM conversations WHERE ownerUserId = :ownerUserId AND id NOT IN (:authorizedIds)")
    suspend fun deleteConversationsNotIn(ownerUserId: String, authorizedIds: List<String>)

    @Query("DELETE FROM messages WHERE ownerUserId = :ownerUserId")
    suspend fun deleteMessagesForOwner(ownerUserId: String)

    @Query("DELETE FROM messages WHERE ownerUserId = :ownerUserId AND conversationId NOT IN (:authorizedIds)")
    suspend fun deleteMessagesNotIn(ownerUserId: String, authorizedIds: List<String>)

    @Transaction
    suspend fun updateConversationLastMessage(
        ownerUserId: String,
        conversationId: String,
        lastMessage: String,
        lastMessageTime: Long,
        isTransfer: Boolean,
        unreadIncrement: Int
    ) {
        val current = getConversation(ownerUserId, conversationId)
        if (current != null) {
            insertConversation(
                current.copy(
                    lastMessage = lastMessage,
                    lastMessageTime = lastMessageTime,
                    isTransfer = isTransfer,
                    unreadCount = current.unreadCount + unreadIncrement
                )
            )
        }
    }
}
