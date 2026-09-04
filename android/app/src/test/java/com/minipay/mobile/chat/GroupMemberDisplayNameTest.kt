package com.minipay.mobile.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupMemberDisplayNameTest {
    @Test
    fun `group nickname takes priority over current account nickname`() {
        val member = GroupMemberResponse("owner", nickname = "群内昵称", originalNickname = "最新昵称")
        assertEquals("群内昵称", resolveGroupMemberDisplayName(member, "好友昵称"))
    }

    @Test
    fun `latest account nickname is used when group nickname is absent`() {
        val member = GroupMemberResponse("owner", originalNickname = "最新昵称")
        assertEquals("最新昵称", resolveGroupMemberDisplayName(member, null))
    }

    @Test
    fun `stored friend name and generic fallback remain available`() {
        assertEquals("好友昵称", resolveGroupMemberDisplayName(GroupMemberResponse("member"), "好友昵称"))
        assertEquals("群成员", resolveGroupMemberDisplayName(GroupMemberResponse("member"), null))
    }
}
