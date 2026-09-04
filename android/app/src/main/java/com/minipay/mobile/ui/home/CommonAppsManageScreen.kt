package com.minipay.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minipay.mobile.home.MAX_COMMON_APPS
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingHomeBackground
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingPrimarySoft
import com.minipay.mobile.ui.theme.MilingTextMuted
import com.minipay.mobile.ui.theme.MilingTextPrimary
import com.minipay.mobile.ui.theme.MilingTextSecondary

@Composable
fun CommonAppsManageScreen(
    selectedApps: List<AppService>,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    val availableApps = remember { appServices.filter(AppService::available) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MilingHomeBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("common_apps_manage_screen")
    ) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                "管理常用应用",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MilingTextPrimary,
                modifier = Modifier.semantics { heading() }
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            shape = RoundedCornerShape(18.dp),
            color = androidx.compose.ui.graphics.Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, MilingBorder)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "已选应用",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MilingTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${selectedApps.size}/$MAX_COMMON_APPS · 长按拖动排序",
                        style = MaterialTheme.typography.bodySmall,
                        color = MilingTextSecondary
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (selectedApps.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().height(78.dp).testTag("common_apps_empty"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("从下方添加常用应用", color = MilingTextMuted)
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        selectedApps.forEachIndexed { index, service ->
                            SelectedCommonApp(
                                service = service,
                                index = index,
                                count = selectedApps.size,
                                onRemove = { onRemove(service.id) },
                                onMove = onMove
                            )
                        }
                    }
                }
            }
        }

        Text(
            if (selectedApps.size >= MAX_COMMON_APPS) "全部应用 · 已达到 5 个上限" else "全部应用 · 最多添加 5 个",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MilingTextPrimary,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(availableApps, key = AppService::id) { service ->
                val selected = selectedApps.any { it.id == service.id }
                val addEnabled = selectedApps.size < MAX_COMMON_APPS
                AvailableCommonAppRow(
                    service = service,
                    selected = selected,
                    addEnabled = addEnabled,
                    onAdd = { onAdd(service.id) },
                    onRemove = { onRemove(service.id) }
                )
            }
        }
    }
}

@Composable
private fun SelectedCommonApp(
    service: AppService,
    index: Int,
    count: Int,
    onRemove: () -> Unit,
    onMove: (Int, Int) -> Unit
) {
    var dragDistance by remember(service.id) { mutableFloatStateOf(0f) }
    val actions = buildList {
        if (index > 0) add(CustomAccessibilityAction("左移") { onMove(index, index - 1); true })
        if (index < count - 1) add(CustomAccessibilityAction("右移") { onMove(index, index + 1); true })
    }
    Column(
        modifier = Modifier
            .width(62.dp)
            .pointerInput(service.id, index, count) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragDistance = 0f },
                    onDragEnd = { dragDistance = 0f },
                    onDragCancel = { dragDistance = 0f }
                ) { change, amount ->
                    change.consume()
                    dragDistance += amount.x
                    val threshold = 30.dp.toPx()
                    when {
                        dragDistance <= -threshold && index < count - 1 -> {
                            onMove(index, index + 1)
                            dragDistance = 0f
                        }
                        dragDistance >= threshold && index > 0 -> {
                            onMove(index, index - 1)
                            dragDistance = 0f
                        }
                    }
                }
            }
            .semantics {
                role = Role.Button
                customActions = actions
            }
            .testTag("common_selected_${service.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            ServiceIcon(service, size = 42.dp, iconSize = 27.dp)
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).size(20.dp).testTag("common_remove_${service.id}"),
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color(0xFFEA4335),
                onClick = onRemove
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "移除${service.name}", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(3.dp))
            }
        }
        Text(service.name, style = MaterialTheme.typography.labelMedium, color = MilingTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Outlined.DragIndicator, contentDescription = "长按拖动${service.name}", tint = MilingTextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AvailableCommonAppRow(
    service: AppService,
    selected: Boolean,
    addEnabled: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("common_available_${service.id}"),
        shape = RoundedCornerShape(14.dp),
        color = androidx.compose.ui.graphics.Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, MilingBorder)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ServiceIcon(service, size = 40.dp, iconSize = 26.dp)
            Spacer(Modifier.width(12.dp))
            Text(service.name, style = MaterialTheme.typography.bodyLarge, color = MilingTextPrimary, modifier = Modifier.weight(1f))
            TextButton(
                onClick = if (selected) onRemove else onAdd,
                enabled = selected || addEnabled,
                modifier = Modifier.height(48.dp).testTag("common_toggle_${service.id}")
            ) {
                Icon(
                    if (selected) Icons.Outlined.Close else Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (selected) "移除" else if (addEnabled) "添加" else "已满")
            }
        }
    }
}

@Composable
internal fun ServiceIcon(service: AppService, size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp) {
    Surface(shape = RoundedCornerShape(12.dp), color = MilingPrimarySoft, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(appServiceIcon(service), contentDescription = null, tint = appServiceColor(service), modifier = Modifier.size(iconSize))
        }
    }
}
