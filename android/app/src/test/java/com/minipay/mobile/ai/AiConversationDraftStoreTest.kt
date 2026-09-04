package com.minipay.mobile.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiConversationDraftStoreTest {

    @Test
    fun draftsAreIsolatedByConversationAndNewConversationKey() {
        val store = AiConversationDraftStore()

        store.write("conversation-a", "给小李转 20 元")
        store.write("conversation-b", "查询本月账单")
        store.write("NEW_CONVERSATION_DRAFT", "我不能吃坚果")

        assertEquals("给小李转 20 元", store.read("conversation-a"))
        assertEquals("查询本月账单", store.read("conversation-b"))
        assertEquals("我不能吃坚果", store.read("NEW_CONVERSATION_DRAFT"))
    }

    @Test
    fun clearingOneDraftDoesNotAffectOtherConversations() {
        val store = AiConversationDraftStore()
        store.write("conversation-a", "草稿 A")
        store.write("conversation-b", "草稿 B")

        store.write("conversation-a", "")

        assertEquals("", store.read("conversation-a"))
        assertEquals("草稿 B", store.read("conversation-b"))
    }
}
