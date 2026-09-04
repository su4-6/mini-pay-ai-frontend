package com.minipay.mobile.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationDisplayNameTest {
    @Test
    fun `remark wins over current nickname and cached name`() {
        assertEquals(
            "同事小林",
            resolveConversationDisplayName(false, "同事小林", "林夏新昵称", "林夏旧昵称", "林夏")
        )
    }

    @Test
    fun `current nickname wins over cached and server names`() {
        assertEquals(
            "路博宇",
            resolveConversationDisplayName(false, "", "路博宇", "米灵用户", "米灵用户")
        )
    }

    @Test
    fun `cached name survives a temporary friend service failure`() {
        assertEquals(
            "路博宇",
            resolveConversationDisplayName(false, null, null, "路博宇", "米灵用户")
        )
    }

    @Test
    fun `server name is the final direct conversation fallback`() {
        assertEquals(
            "好友",
            resolveConversationDisplayName(false, null, null, null, "好友")
        )
    }

    @Test
    fun `group always keeps its server managed name`() {
        assertEquals(
            "项目群",
            resolveConversationDisplayName(true, "备注", "昵称", "旧群名", "项目群")
        )
    }
}
