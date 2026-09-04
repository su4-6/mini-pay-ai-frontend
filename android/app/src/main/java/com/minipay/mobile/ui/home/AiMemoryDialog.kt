package com.minipay.mobile.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.minipay.mobile.ai.AiHomeUiState
import com.minipay.mobile.ai.MemoryItemDto
import com.minipay.mobile.ai.MemorySettingDto
import com.minipay.mobile.ai.UpdateMemorySettingsRequest
import com.minipay.mobile.ui.theme.MilingTextSecondary

@Composable
internal fun AiMemoryDialog(
    state: AiHomeUiState,
    onDismiss: () -> Unit,
    onUpdateSettings: (UpdateMemorySettingsRequest) -> Unit,
    onUpdateItem: (MemoryItemDto, String) -> Unit,
    onDeleteItem: (MemoryItemDto) -> Unit
) {
    val settings = state.memorySettings
    var deleteTarget by remember { mutableStateOf<MemoryItemDto?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("长期记忆管理") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("memory_manager"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "只有你明确同意的结构化偏好会被保存。完整地址、手机号、余额、账单和凭据不会保存。",
                    color = MilingTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                if (state.memoryLoading && settings == null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (settings != null) {
                    SettingSwitch("启用长期记忆", settings.enabled, state.memoryLoading) {
                        onUpdateSettings(settings.request(enabled = it))
                    }
                    HorizontalDivider()
                    SettingSwitch("餐饮偏好", settings.foodPreferenceEnabled, state.memoryLoading || !settings.enabled) {
                        onUpdateSettings(settings.request(foodPreferenceEnabled = it))
                    }
                    SettingSwitch("忌口与过敏原", settings.allergenAvoidanceEnabled, state.memoryLoading || !settings.enabled) {
                        onUpdateSettings(settings.request(allergenAvoidanceEnabled = it))
                    }
                    SettingSwitch("用餐预算习惯", settings.mealBudgetEnabled, state.memoryLoading || !settings.enabled) {
                        onUpdateSettings(settings.request(mealBudgetEnabled = it))
                    }
                    SettingSwitch("常用联系人别名", settings.contactAliasEnabled, state.memoryLoading || !settings.enabled) {
                        onUpdateSettings(settings.request(contactAliasEnabled = it))
                    }
                    SettingSwitch("常用地址别名", settings.addressAliasEnabled, state.memoryLoading || !settings.enabled) {
                        onUpdateSettings(settings.request(addressAliasEnabled = it))
                    }
                }

                HorizontalDivider()
                Text("已保存内容", style = MaterialTheme.typography.titleMedium)
                if (state.memoryItems.isEmpty()) {
                    Text("暂无已保存的长期记忆", color = MilingTextSecondary)
                }
                state.memoryItems.forEach { item ->
                    MemoryItemEditor(
                        item = item,
                        enabled = !state.memoryLoading,
                        onSave = { onUpdateItem(item, it) },
                        onDelete = { deleteTarget = item }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, enabled = !state.memoryLoading) { Text("完成") }
        }
    )

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这条记忆？") },
            text = { Text("“${item.displayValue}”将从 AI 长期记忆中移除。") },
            confirmButton = {
                Button(onClick = { onDeleteItem(item); deleteTarget = null }) { Text("删除") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, disabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = !disabled)
    }
}

@Composable
private fun MemoryItemEditor(
    item: MemoryItemDto,
    enabled: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit
) {
    var value by rememberSaveable(item.id, item.version) { mutableStateOf(item.displayValue) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(memoryTypeLabel(item.type), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.take(256) },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("记忆内容") }
        )
        Row(modifier = Modifier.align(Alignment.End)) {
            TextButton(onClick = onDelete, enabled = enabled) { Text("删除") }
            TextButton(
                onClick = { onSave(value) },
                enabled = enabled && value.isNotBlank() && value != item.displayValue
            ) { Text("保存修改") }
        }
    }
}

private fun MemorySettingDto.request(
    enabled: Boolean = this.enabled,
    foodPreferenceEnabled: Boolean = this.foodPreferenceEnabled,
    allergenAvoidanceEnabled: Boolean = this.allergenAvoidanceEnabled,
    mealBudgetEnabled: Boolean = this.mealBudgetEnabled,
    contactAliasEnabled: Boolean = this.contactAliasEnabled,
    addressAliasEnabled: Boolean = this.addressAliasEnabled
) = UpdateMemorySettingsRequest(
    enabled,
    foodPreferenceEnabled,
    allergenAvoidanceEnabled,
    mealBudgetEnabled,
    contactAliasEnabled,
    addressAliasEnabled,
    version
)

private fun memoryTypeLabel(type: String): String = when (type) {
    "FOOD_PREFERENCE" -> "餐饮偏好"
    "ALLERGEN_AVOIDANCE" -> "忌口与过敏原"
    "MEAL_BUDGET" -> "用餐预算"
    "CONTACT_ALIAS" -> "联系人别名"
    "ADDRESS_ALIAS" -> "地址别名"
    else -> type
}
