package com.minipay.mobile.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.minipay.mobile.ui.theme.MilingBackground
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingSpacing
import com.minipay.mobile.ui.theme.MilingTextMuted
import com.minipay.mobile.ui.theme.MilingTextPrimary

@Composable
fun ServiceSearchScreen(
    onBack: () -> Unit,
    onServiceClick: (AppService) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = searchServices(query)
    Column(
        Modifier.fillMaxSize().background(MilingBackground).statusBarsPadding().navigationBarsPadding().testTag("service_search_screen")
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = MilingSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f).testTag("service_search_input"),
                singleLine = true,
                placeholder = { Text("搜索服务") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, "清空")
                    }
                },
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {})
            )
        }

        if (results.isEmpty()) {
            Box(Modifier.fillMaxSize().testTag("service_search_empty"), contentAlignment = Alignment.Center) {
                Text("没有找到相关服务", color = MilingTextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(MilingSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(MilingSpacing.Sm)
            ) {
                items(results, key = { it.id }) { service ->
                    ServiceResult(service, onClick = { onServiceClick(service) })
                }
            }
        }
    }
}

@Composable
private fun ServiceResult(service: AppService, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("service_result_${service.id}"),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp
    ) {
        Row(
            Modifier.padding(horizontal = MilingSpacing.Lg, vertical = MilingSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(appServiceIcon(service), null, tint = MilingPrimary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.size(MilingSpacing.Md))
            Column(Modifier.weight(1f)) {
                Text(service.name, style = MaterialTheme.typography.titleMedium, color = MilingTextPrimary)
                if (!service.available) Text("建设中", style = MaterialTheme.typography.bodySmall, color = MilingTextMuted)
            }
            Text(if (service.available) "打开" else "暂不可用", color = if (service.available) MilingPrimary else MilingTextMuted)
        }
    }
}
