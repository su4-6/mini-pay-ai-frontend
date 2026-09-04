package com.minipay.mobile.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.minipay.mobile.ai.AiPaymentPrompt
import com.minipay.mobile.ai.CreateDeliveryAddressRequest
import com.minipay.mobile.ui.finance.PaymentConfirmationBackdrop
import com.minipay.mobile.ui.finance.PaymentPasswordSheet

@Composable
internal fun AiPaymentConfirmationDialog(
    prompt: AiPaymentPrompt,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember(prompt.messageId) { mutableStateOf("") }
    val dismiss = {
        password = ""
        onDismiss()
    }
    Box(Modifier.fillMaxSize().testTag("ai-payment-flow")) {
        PaymentConfirmationBackdrop(
            title = if (prompt.type == AiPaymentPrompt.Type.TRANSFER) "转账" else "付款",
            counterparty = prompt.counterparty,
            counterpartyDetail = "密码仅用于本次原生安全验证，不会进入 AI 上下文",
            amountCent = prompt.amountCent,
            method = "账户余额"
        )
        PaymentPasswordSheet(
            purpose = "付款",
            counterparty = prompt.counterparty,
            amountCent = prompt.amountCent,
            password = password,
            busy = submitting,
            error = null,
            onClose = dismiss,
            onDigit = { digit ->
                if (submitting || password.length >= 6) return@PaymentPasswordSheet
                password += digit
                if (password.length == 6) {
                    val value = password
                    password = ""
                    onConfirm(value)
                }
            },
            onDelete = { if (!submitting) password = password.dropLast(1) }
        )
    }
}

@Composable
internal fun AiAddressDialog(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (CreateDeliveryAddressRequest) -> Unit
) {
    var label by remember { mutableStateOf("家") }
    var recipient by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    val clear = {
        label = "家"
        recipient = ""
        mobile = ""
        address = ""
    }
    val dismiss = {
        clear()
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            securePolicy = SecureFlagPolicy.SecureOn
        ),
        title = { Text("添加配送地址") },
        text = {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(label, { label = it.take(32) }, label = { Text("地址标签") }, singleLine = true)
                OutlinedTextField(recipient, { recipient = it.take(64) }, label = { Text("收货人") }, singleLine = true)
                OutlinedTextField(
                    mobile,
                    { value -> if (value.length <= 11 && value.all(Char::isDigit)) mobile = value },
                    label = { Text("手机号") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.testTag("ai_address_mobile")
                )
                OutlinedTextField(
                    address,
                    { address = it.take(512) },
                    label = { Text("详细地址") },
                    minLines = 2,
                    modifier = Modifier.testTag("ai_address_detail")
                )
                Text("敏感地址字段将由 Commerce 加密保存，AI 只会看到地址 ID 和脱敏摘要。")
            }
        },
        confirmButton = {
            Button(
                enabled = label.isNotBlank() && recipient.isNotBlank() &&
                    mobile.matches(Regex("1[3-9]\\d{9}")) && address.isNotBlank() && !submitting,
                onClick = {
                    val request = CreateDeliveryAddressRequest(
                        label.trim(), recipient.trim(), mobile, address.trim(), "CN-SH-PD"
                    )
                    clear()
                    onSave(request)
                }
            ) { Text(if (submitting) "保存中…" else "保存并结算") }
        },
        dismissButton = { OutlinedButton(onClick = dismiss, enabled = !submitting) { Text("取消") } }
    )
}

private fun formatCent(value: Long): String {
    val whole = value / 100
    val fraction = kotlin.math.abs(value % 100)
    return "$whole.${fraction.toString().padStart(2, '0')}"
}
