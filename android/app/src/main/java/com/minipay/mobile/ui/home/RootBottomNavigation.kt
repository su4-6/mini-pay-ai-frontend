package com.minipay.mobile.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minipay.mobile.R
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingHomeTokens
import com.minipay.mobile.ui.theme.MilingIconSecondary
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingTextSecondary

enum class RootTab { RECOMMENDATION, MESSAGES, PROFILE }

@Composable
fun RootBottomNavigation(
    selected: RootTab,
    onSelect: (RootTab) -> Unit,
    onOpenMiling: () -> Unit,
    showMessageReminder: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("home_bottom_navigation")
    ) {
        Surface(
            Modifier.fillMaxWidth().height(MilingHomeTokens.BottomBarHeight).padding(start = 64.dp),
            shape = RoundedCornerShape(34.dp),
            color = MilingSurface,
            border = BorderStroke(1.dp, MilingBorder),
            shadowElevation = 8.dp
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RootTabItem("首页", RootTab.RECOMMENDATION, selected, "home_tab_recommend", false, onSelect)
                RootTabItem("消息", RootTab.MESSAGES, selected, "home_tab_messages", showMessageReminder, onSelect)
                RootTabItem("我的", RootTab.PROFILE, selected, "home_tab_profile", false, onSelect)
            }
        }
        Surface(
            Modifier.align(Alignment.CenterStart).size(64.dp).clickable(onClick = onOpenMiling)
                .semantics { contentDescription = "打开米灵智能助手" }
                .testTag("home_tab_miling"),
            shape = CircleShape,
            color = Color(0xFFEAF2FF),
            border = BorderStroke(1.dp, Color.White),
            shadowElevation = 8.dp
        ) { Box(contentAlignment = Alignment.Center) { MilingMascot(54.dp) } }
    }
}

@Composable
private fun RootTabItem(
    label: String,
    tab: RootTab,
    selected: RootTab,
    tag: String,
    showReminder: Boolean,
    onSelect: (RootTab) -> Unit
) {
    val active = tab == selected
    Column(
        Modifier.size(width = 74.dp, height = 60.dp)
            .selectable(selected = active, role = Role.Tab) { onSelect(tab) }
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (tab) {
            RootTab.RECOMMENDATION -> Icon(
                painter = painterResource(R.drawable.minipay_login_logo),
                contentDescription = label,
                tint = if (active) Color.Unspecified else MilingIconSecondary,
                modifier = Modifier.size(26.dp)
            )
            RootTab.MESSAGES -> Box {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = if (showReminder) "$label，有未读提醒" else label,
                    tint = if (active) MilingPrimary else MilingIconSecondary,
                    modifier = Modifier.size(26.dp)
                )
                if (showReminder) Surface(
                    modifier = Modifier.align(Alignment.TopEnd).size(9.dp).testTag("home_message_reminder"),
                    shape = CircleShape,
                    color = Color(0xFFFF3B30),
                    border = BorderStroke(1.dp, MilingSurface)
                ) {}
            }
            RootTab.PROFILE -> Icon(
                imageVector = Icons.Outlined.PersonOutline,
                contentDescription = label,
                tint = if (active) MilingPrimary else MilingIconSecondary,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            label,
            color = if (active) MilingPrimary else MilingTextSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}
