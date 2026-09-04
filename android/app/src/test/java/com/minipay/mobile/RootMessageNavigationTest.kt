package com.minipay.mobile

import com.minipay.mobile.ui.home.RootTab
import org.junit.Assert.assertEquals
import org.junit.Test

class RootMessageNavigationTest {
    @Test
    fun `bottom messages tab always opens conversations instead of recent payment messages`() {
        assertEquals(
            RootMessageContent.CONVERSATIONS,
            rootMessageContentAfterTabSelection(RootTab.MESSAGES, RootMessageContent.PAYMENTS)
        )
    }

    @Test
    fun `other bottom tabs preserve the selected message subpage`() {
        assertEquals(
            RootMessageContent.PAYMENTS,
            rootMessageContentAfterTabSelection(RootTab.RECOMMENDATION, RootMessageContent.PAYMENTS)
        )
        assertEquals(
            RootMessageContent.PAYMENTS,
            rootMessageContentAfterTabSelection(RootTab.PROFILE, RootMessageContent.PAYMENTS)
        )
    }
}
