package com.minipay.mobile.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.unit.dp
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingSpacing
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingTextPrimary

enum class QuickPlusAction { SCAN, RECEIVE, ADD_FRIEND }

private data class QuickPlusItem(
    val label: String,
    val icon: ImageVector,
    val action: QuickPlusAction,
    val tag: String
)

private val quickPlusItems = listOf(
    QuickPlusItem("扫一扫", Icons.Outlined.CropFree, QuickPlusAction.SCAN, "plus_scan"),
    QuickPlusItem("收款", Icons.Outlined.QrCode2, QuickPlusAction.RECEIVE, "plus_receive"),
    QuickPlusItem("添加朋友", Icons.Outlined.PersonAdd, QuickPlusAction.ADD_FRIEND, "plus_add_friend")
)

@Composable
fun QuickPlusMenuOverlay(
    onDismiss: () -> Unit,
    onAction: (QuickPlusAction) -> Unit,
    topOffset: Int = 56
) {
    Box(
        Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onDismiss
        ).testTag("plus_menu_scrim")
    ) {
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()
                .padding(top = topOffset.dp, end = MilingSpacing.Xl).width(152.dp).testTag("plus_menu")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(14.dp),
            color = MilingSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MilingBorder),
            shadowElevation = 4.dp
        ) {
            Column {
                quickPlusItems.forEachIndexed { index, item ->
                    Row(
                        Modifier.fillMaxWidth().height(52.dp)
                            .clickable(role = Role.Button) { onAction(item.action) }
                            .padding(horizontal = MilingSpacing.Md).testTag(item.tag),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MilingSpacing.Sm)
                    ) {
                        Icon(item.icon, null, tint = MilingPrimary, modifier = Modifier.size(24.dp))
                        Text(item.label, color = MilingTextPrimary)
                    }
                    if (index < quickPlusItems.lastIndex) HorizontalDivider(color = MilingBorder)
                }
            }
        }
    }
}
