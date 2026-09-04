package com.minipay.mobile.ui.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minipay.mobile.ai.MemoryItemDto
import com.minipay.mobile.ai.MemorySettingDto
import com.minipay.mobile.ai.MemorySettingsViewModel
import com.minipay.mobile.ai.UpdateMemorySettingsRequest
import com.minipay.mobile.network.AutoRefreshEffect
import com.minipay.mobile.ui.theme.MilingError
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingSurfaceSubtle
import com.minipay.mobile.ui.theme.MilingTextSecondary

@Composable
fun MemorySettingsRoute(
    onBack: () -> Unit,
    viewModel: MemorySettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AutoRefreshEffect(enabled = !state.saving, onRefresh = viewModel::refresh)
    MemorySettingsScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onUpdateSettings = viewModel::updateSettings,
        onAdd = viewModel::addCustom,
        onUpdate = viewModel::updateItem,
        onDelete = viewModel::deleteItem
    )
}

@Composable
private fun MemorySettingsScreen(
    state: com.minipay.mobile.ai.MemorySettingsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onUpdateSettings: (UpdateMemorySettingsRequest) -> Unit,
    onAdd: (String, () -> Unit) -> Unit,
    onUpdate: (MemoryItemDto, String) -> Unit,
    onDelete: (MemoryItemDto) -> Unit
) {
    var newValue by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(MilingSurfaceSubtle).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
            }
            Text("记忆", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("memory_settings_screen"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            state.error?.let { error ->
                item("error") {
                    Surface(color = MilingError.copy(alpha = .1f), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(error, color = MilingError, modifier = Modifier.weight(1f))
                            TextButton(onClick = onRetry) { Text("重试") }
                        }
                    }
                }
            }
            state.settings?.let { settings ->
                item("settings") {
                    Surface(color = MilingSurface, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("长期记忆", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("仅保存你主动添加或明确同意的内容。手机号、详细地址、账单和凭据不会保存。", color = MilingTextSecondary)
                            MemorySwitch("启用长期记忆", settings.enabled, state.saving) {
                                onUpdateSettings(settings.request(enabled = it))
                            }
                            HorizontalDivider()
                            MemorySwitch("餐饮偏好", settings.foodPreferenceEnabled, state.saving || !settings.enabled) {
                                onUpdateSettings(settings.request(foodPreferenceEnabled = it))
                            }
                            MemorySwitch("忌口与过敏原", settings.allergenAvoidanceEnabled, state.saving || !settings.enabled) {
                                onUpdateSettings(settings.request(allergenAvoidanceEnabled = it))
                            }
                            MemorySwitch("用餐预算习惯", settings.mealBudgetEnabled, state.saving || !settings.enabled) {
                                onUpdateSettings(settings.request(mealBudgetEnabled = it))
                            }
                            MemorySwitch("常用联系人别名", settings.contactAliasEnabled, state.saving || !settings.enabled) {
                                onUpdateSettings(settings.request(contactAliasEnabled = it))
                            }
                            MemorySwitch("常用地址别名", settings.addressAliasEnabled, state.saving || !settings.enabled) {
                                onUpdateSettings(settings.request(addressAliasEnabled = it))
                            }
                        }
                    }
                }
                item("add") {
                    Surface(color = MilingSurface, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("添加自由记忆", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = newValue,
                                onValueChange = { if (it.codePointCount() <= 256) newValue = it },
                                enabled = settings.enabled && !state.saving,
                                modifier = Modifier.fillMaxWidth().testTag("memory_input"),
                                label = { Text("例如：我喜欢少辣的川菜") },
                                supportingText = { Text("${newValue.codePointCount()}/256") },
                                minLines = 2,
                                maxLines = 4
                            )
                            Button(
                                onClick = { onAdd(newValue) { newValue = "" } },
                                enabled = settings.enabled && newValue.isNotBlank() && !state.saving,
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("memory_add_button")
                            ) { if (state.saving) CircularProgressIndicator(Modifier.size(20.dp)) else Text("添加记忆") }
                            state.operationError?.let {
                                Text(it, color = MilingError, style = MaterialTheme.typography.bodySmall)
                            }
                            state.successMessage?.let {
                                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item("saved-title") { Text("已保存内容", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            if (state.items.isEmpty()) item("empty") { Text("暂无已保存的长期记忆", color = MilingTextSecondary) }
            items(state.items, key = { it.id }) { item ->
                MemoryEditor(item, !state.saving, onUpdate, onDelete)
            }
        }
    }
}

@Composable
private fun MemorySwitch(label: String, checked: Boolean, disabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked, onChange, enabled = !disabled, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun MemoryEditor(item: MemoryItemDto, enabled: Boolean, onUpdate: (MemoryItemDto, String) -> Unit, onDelete: (MemoryItemDto) -> Unit) {
    var value by remember(item.id, item.version) { mutableStateOf(item.displayValue) }
    Surface(color = MilingSurface, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (item.type == "CUSTOM") "自由记忆" else item.type, color = MilingTextSecondary)
            OutlinedTextField(value, { if (it.codePointCount() <= 256) value = it }, Modifier.fillMaxWidth(), enabled = enabled)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { onDelete(item) }, enabled = enabled, modifier = Modifier.semantics { contentDescription = "删除记忆" }) {
                    Icon(Icons.Outlined.DeleteOutline, null)
                }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = { onUpdate(item, value) }, enabled = enabled && value.isNotBlank() && value != item.displayValue) {
                    Text("保存修改")
                }
            }
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
) = UpdateMemorySettingsRequest(enabled, foodPreferenceEnabled, allergenAvoidanceEnabled, mealBudgetEnabled, contactAliasEnabled, addressAliasEnabled, version)

private fun String.codePointCount(): Int = codePointCount(0, length)
