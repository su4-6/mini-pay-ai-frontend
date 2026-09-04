package com.minipay.mobile.ui.chat

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.minipay.mobile.ui.theme.MilingBackground
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingIconPrimary
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingRadii
import com.minipay.mobile.ui.theme.MilingSpacing
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingSurfaceSubtle
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.theme.MilingTextSecondary

@Composable
fun FriendSettingsScreen(
    friendName: String,
    onBack: () -> Unit,
    onEditRemark: () -> Unit,
    onDeleteFriend: () -> Unit
) {
    val context = LocalContext.current
    val developing = { Toast.makeText(context, "还在开发", Toast.LENGTH_SHORT).show() }
    Column(Modifier.fillMaxSize().background(MilingBackground).statusBarsPadding()) {
        SettingsTopBar(title = "资料设置", onBack = onBack)
        Spacer(Modifier.height(12.dp))
        SettingsRow("设置备注和标签", friendName, onEditRemark)
        SettingsRow("朋友权限", onClick = developing)
        Spacer(Modifier.height(10.dp))
        SettingsRow("把他推荐给朋友", onClick = developing)
        SettingsRow("添加到桌面", onClick = developing)
        Spacer(Modifier.height(10.dp))
        SettingsRow("设为星标朋友", trailing = "关闭", onClick = developing)
        Spacer(Modifier.height(10.dp))
        SettingsRow("加入黑名单", trailing = "关闭", onClick = developing)
        SettingsRow("投诉", onClick = developing)
        Spacer(Modifier.height(10.dp))
        Surface(color = MilingSurface, modifier = Modifier.fillMaxWidth().clickable(onClick = onDeleteFriend)) {
            Box(Modifier.padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                Text("删除", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color(0xFFE85050))
            }
        }
    }
}

@Composable
fun RemarkSettingsScreen(
    friendName: String,
    onBack: () -> Unit,
    onSave: (String) -> Unit
) {
    val context = LocalContext.current
    var remark by rememberSaveable { mutableStateOf(friendName) }
    Column(Modifier.fillMaxSize().background(MilingBackground).statusBarsPadding().padding(horizontal = MilingSpacing.Xl)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("取消", modifier = Modifier.clickable(onClick = onBack).padding(12.dp), color = MilingTextPrimary)
            Spacer(Modifier.weight(1f))
            Button(onClick = { onSave(remark); onBack() }) { Text("完成") }
        }
        Spacer(Modifier.height(62.dp))
        Text("设置备注和标签", style = MaterialTheme.typography.headlineMedium, color = MilingTextPrimary, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(64.dp))
        Text("备注", color = MilingTextSecondary)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = remark, onValueChange = { remark = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(28.dp))
        Text("标签", color = MilingTextSecondary)
        SettingsRow("添加标签", onClick = { Toast.makeText(context, "还在开发", Toast.LENGTH_SHORT).show() })
        Spacer(Modifier.height(20.dp))
        Text("电话", color = MilingTextSecondary)
        SettingsRow("添加电话", onClick = { Toast.makeText(context, "还在开发", Toast.LENGTH_SHORT).show() })
        Spacer(Modifier.height(20.dp))
        Text("描述", color = MilingTextSecondary)
        SettingsRow("添加文字", onClick = { Toast.makeText(context, "还在开发", Toast.LENGTH_SHORT).show() })
    }
}

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = MilingIconPrimary) }
        Text(title, style = MaterialTheme.typography.titleLarge, color = MilingTextPrimary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun SettingsRow(label: String, trailing: String? = null, onClick: () -> Unit) {
    Surface(color = MilingSurface, border = androidx.compose.foundation.BorderStroke(1.dp, MilingBorder), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = MilingSpacing.Xl, vertical = 19.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MilingTextPrimary, modifier = Modifier.weight(1f))
            trailing?.let { Text(it, color = MilingTextSecondary) }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MilingTextSecondary)
        }
    }
}
