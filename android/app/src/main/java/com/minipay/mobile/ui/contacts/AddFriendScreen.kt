package com.minipay.mobile.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minipay.mobile.ui.theme.MilingBackground
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingIconPrimary
import com.minipay.mobile.ui.theme.MilingIconSecondary
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingPrimarySoft
import com.minipay.mobile.ui.theme.MilingRadii
import com.minipay.mobile.ui.theme.MilingSpacing
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingTextMuted
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.theme.MilingTextSecondary
import com.minipay.mobile.R

@Composable
fun AddFriendScreen(
    accountId: String = "",
    onBack: () -> Unit,
    onSearchClick: () -> Unit = {},
    onShowQrCode: () -> Unit = {},
    onScanQr: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MilingBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AddFriendTopBar(onBack = onBack)

        Spacer(Modifier.height(MilingSpacing.Lg))

        SearchBar(
            onClick = onSearchClick,
            modifier = Modifier.padding(horizontal = MilingSpacing.Xl)
        )

        Spacer(Modifier.height(MilingSpacing.Lg))

        AccountRow(
            accountId = accountId,
            onQrClick = onShowQrCode
        )

        Spacer(Modifier.height(MilingSpacing.Lg))

        ActionCard(
            onScanQr = onScanQr,
            modifier = Modifier.padding(horizontal = MilingSpacing.Xl)
        )

        Spacer(Modifier.weight(1f))

        MiniPayLogo(modifier = Modifier.align(Alignment.CenterHorizontally))

        Spacer(Modifier.height(MilingSpacing.Section))
    }
}

@Composable
private fun AddFriendTopBar(
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = MilingSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = MilingIconPrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "添加好友",
                style = MaterialTheme.typography.titleLarge,
                color = MilingTextPrimary,
                modifier = Modifier.semantics { heading() }
            )
        }

        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun SearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(MilingSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .border(1.dp, MilingBorder, CircleShape)
            .padding(horizontal = MilingSpacing.Md)
            .semantics { contentDescription = "手机号" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MilingTextMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(MilingSpacing.Sm))
        Text(
            text = "手机号",
            style = MaterialTheme.typography.bodyLarge,
            color = MilingTextMuted,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AccountRow(
    accountId: String,
    onQrClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "我的 MiniPay 账号：",
            style = MaterialTheme.typography.bodyMedium,
            color = MilingTextSecondary
        )
        Text(
            text = accountId,
            style = MaterialTheme.typography.bodyMedium,
            color = MilingTextPrimary
        )
        Spacer(Modifier.width(MilingSpacing.Sm))
        Icon(
            imageVector = Icons.Outlined.GridView,
            contentDescription = "我的名片二维码",
            tint = MilingPrimary,
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onQrClick)
        )
    }
}

@Composable
private fun ActionCard(
    onScanQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(MilingRadii.Large),
        color = MilingSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MilingBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        ActionRow(
            icon = Icons.Outlined.QrCodeScanner,
            title = "扫一扫",
            subtitle = "扫描二维码添加好友",
            onClick = onScanQr
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MilingSpacing.Lg, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MilingSpacing.Md)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(MilingRadii.Medium))
                .background(MilingPrimarySoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MilingPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MilingTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(MilingSpacing.Xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MilingTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MilingIconSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun MilingMascot(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MilingSpacing.Sm)
    ) {
        Box(
            modifier = Modifier.size(96.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            // 主体
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.BottomCenter)
                    .clip(CircleShape)
                    .background(MilingPrimary)
            ) {
                // 左眼
                Box(
                    modifier = Modifier
                        .size(width = 14.dp, height = 20.dp)
                        .offset(x = 22.dp, y = 26.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                // 右眼
                Box(
                    modifier = Modifier
                        .size(width = 14.dp, height = 20.dp)
                        .offset(x = 44.dp, y = 26.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
            // 小圆球
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MilingPrimarySoft)
            )
        }

        Text(
            text = "米灵",
            style = MaterialTheme.typography.bodyLarge,
            color = MilingPrimary
        )
    }
}

@Composable
private fun MiniPayLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.minipay_logo),
        contentDescription = "MiniPay",
        modifier = modifier.size(64.dp).clip(CircleShape)
    )
}
