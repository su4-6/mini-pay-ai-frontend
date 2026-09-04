package com.minipay.mobile.ui.contacts

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.minipay.mobile.R
import com.minipay.mobile.ui.components.AvatarImage
import com.minipay.mobile.ui.theme.MilingBackground
import com.minipay.mobile.ui.theme.MilingIconPrimary
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingPrimarySoft
import com.minipay.mobile.ui.theme.MilingRadii
import com.minipay.mobile.ui.theme.MilingSpacing
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.theme.MilingTextSecondary
import com.minipay.mobile.ui.scan.friendCardQrValue

@Composable
fun MyQrCodeScreen(
    accountId: String = "",
    userName: String = "MiniPay 用户",
    avatarUrl: String? = null,
    phone: String = "",
    onBack: () -> Unit,
    onOpenProfile: () -> Unit = {}
) {
    val qrSize = 240.dp
    val qrBitmap = remember(accountId) {
        accountId.trim().takeIf { it.isNotEmpty() }
            ?.let { generateQrBitmap(friendCardQrValue(it), 512) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MilingBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        MyQrCodeTopBar(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MilingSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ProfileHeader(
                name = userName,
                avatarUrl = avatarUrl,
                phone = phone,
                onClick = onOpenProfile
            )

            Spacer(Modifier.height(MilingSpacing.Xxl))

            Surface(
                shape = RoundedCornerShape(MilingRadii.Large),
                color = MilingSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(MilingSpacing.Xxl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(qrSize),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap == null) {
                            Text("正在生成名片二维码", color = MilingTextSecondary)
                        } else {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "MiniPay 名片二维码",
                                modifier = Modifier.fillMaxSize()
                            )
                            Image(
                                painter = painterResource(R.drawable.minipay_logo),
                                contentDescription = "MiniPay",
                                modifier = Modifier.size(44.dp).clip(CircleShape)
                            )
                        }
                    }

                    Spacer(Modifier.height(MilingSpacing.Lg))

                    Text(
                        text = "MiniPay 名片二维码",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MilingTextSecondary
                    )
                }
            }

            Spacer(Modifier.height(MilingSpacing.Xxl))

            MiniPayLogo()
        }
    }
}

@Composable
private fun MyQrCodeTopBar(onBack: () -> Unit) {
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
                text = "我的名片",
                style = MaterialTheme.typography.titleLarge,
                color = MilingTextPrimary,
                modifier = Modifier.semantics { heading() }
            )
        }

        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun ProfileHeader(name: String, avatarUrl: String?, phone: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MilingSpacing.Md)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MilingPrimarySoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = "默认头像",
                tint = MilingPrimary,
                modifier = Modifier.size(36.dp)
            )
            if (!avatarUrl.isNullOrBlank()) {
                AvatarImage(
                    avatarUrl = avatarUrl,
                    contentDescription = "$name 的头像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = MilingTextPrimary
            )
            Text(
                text = "我的 MiniPay 账号：$phone",
                style = MaterialTheme.typography.bodySmall,
                color = MilingTextSecondary
            )
        }
    }
}

@Composable
private fun MiniPayLogo() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MilingSpacing.Sm)
    ) {
        Image(
            painter = painterResource(R.drawable.minipay_logo),
            contentDescription = "MiniPay",
            modifier = Modifier.size(48.dp).clip(CircleShape)
        )
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap {
    val writer = MultiFormatWriter()
    val bitMatrix = writer.encode(
        content, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, EncodeHintType.MARGIN to 2)
    )
    val width = bitMatrix.width
    val height = bitMatrix.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(
                x,
                y,
                if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            )
        }
    }
    return bitmap
}
