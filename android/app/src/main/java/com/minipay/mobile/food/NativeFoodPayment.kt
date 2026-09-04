package com.minipay.mobile.food

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.minipay.mobile.ai.CommerceApi
import com.minipay.mobile.ai.AiAgentApiException
import com.minipay.mobile.auth.IdentityApiException
import com.minipay.mobile.finance.FinanceRepository
import com.minipay.mobile.finance.PaymentOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NativeFoodPaymentState(
    val loading: Boolean = true,
    val storeName: String = "",
    val externalOrderNo: String = "",
    val paymentOrder: PaymentOrder? = null,
    val submitting: Boolean = false,
    val completedStatus: String? = null,
    val error: String? = null
)

@HiltViewModel
class NativeFoodPaymentViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val commerce: CommerceApi,
    private val finance: FinanceRepository
) : ViewModel() {
    private val externalOrderNo = savedStateHandle.get<String>("externalOrderNo").orEmpty()
    private val suppliedOrderRefId = savedStateHandle.get<String>("orderRefId").orEmpty()
    private val mutableState = MutableStateFlow(
        NativeFoodPaymentState(externalOrderNo = externalOrderNo)
    )
    val state: StateFlow<NativeFoodPaymentState> = mutableState.asStateFlow()

    init { load() }

    fun load() {
        if (externalOrderNo.isBlank()) {
            mutableState.update { it.copy(loading = false, error = "外卖订单号无效") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                Log.i(TAG, "Loading payment externalOrderNo=$externalOrderNo orderRefId=$suppliedOrderRefId")
                val order = if (suppliedOrderRefId.isBlank()) {
                    commerce.prepareExternalFoodPayment(
                        externalOrderNo,
                        "android-h5-payment:$externalOrderNo"
                    )
                } else null
                val orderRefId = suppliedOrderRefId.ifBlank { order?.orderRefId.orEmpty() }
                if (orderRefId.isBlank()) error("PAYMENT_ORDER_REFERENCE_MISSING")

                var payment: PaymentOrder? = null
                var lastError: Throwable? = null
                repeat(8) { attempt ->
                    if (payment == null) {
                        try {
                            payment = finance.foodPaymentOrder(orderRefId)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            lastError = error
                            if (attempt < 7) delay(350L)
                        }
                    }
                }
                val authoritativePayment = payment ?: throw PaymentOrderNotReadyException(lastError)
                mutableState.update {
                    it.copy(
                        loading = false,
                        storeName = order?.storeName.orEmpty(),
                        paymentOrder = authoritativePayment,
                        completedStatus = authoritativePayment.status.takeIf { status -> status == "SUCCEEDED" }
                    )
                }
            } catch (error: CancellationException) {
                Log.i(TAG, "Payment loading cancelled for order=$externalOrderNo")
                throw error
            } catch (error: Throwable) {
                Log.e(TAG, "Unable to load authoritative payment for order=$externalOrderNo", error)
                mutableState.update { state ->
                    state.copy(loading = false, error = paymentLoadMessage(error))
                }
            }
        }
    }

    fun confirm(password: String) {
        val order = mutableState.value.paymentOrder ?: return
        if (!password.matches(Regex("\\d{6}"))) {
            mutableState.update { it.copy(error = "请输入 6 位支付密码") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(submitting = true, error = null) }
            runCatching {
                val authorization = finance.authorizePaymentOrder(order, password)
                finance.confirmPaymentOrder(order, authorization.paymentAuthToken)
            }.onSuccess { confirmed ->
                mutableState.update {
                    it.copy(
                        submitting = false,
                        paymentOrder = confirmed,
                        completedStatus = confirmed.status
                    )
                }
            }.onFailure {
                mutableState.update { state ->
                    state.copy(submitting = false, error = "付款未完成，请检查密码或余额后重试")
                }
            }
        }
    }

    private companion object {
        const val TAG = "MiniPayFoodPayment"

        fun paymentLoadMessage(error: Throwable): String {
            val cause = if (error is PaymentOrderNotReadyException) error.cause else error
            val code = (cause as? IdentityApiException)?.code
                ?: (cause as? AiAgentApiException)?.code
            val status = (cause as? IdentityApiException)?.status
                ?: (cause as? AiAgentApiException)?.status
            return when {
                status == 401 || code in setOf("TOKEN_INVALID", "NOT_AUTHENTICATED") ->
                    "登录状态已失效，请返回 MiniPay 重新登录"
                status == 404 || code in setOf("ORDER_NOT_FOUND", "PAYMENT_ORDER_NOT_FOUND") ->
                    "支付单尚未就绪，请点击重新读取订单"
                status == 409 || code in setOf("ORDER_CLOSED", "ORDER_EXPIRED") ->
                    "订单已关闭或过期，请返回后重新下单"
                code == "NETWORK_UNAVAILABLE" || cause is java.io.IOException ->
                    "网络异常，请检查连接后重新读取订单"
                error is PaymentOrderNotReadyException ->
                    "支付单尚未就绪，请点击重新读取订单"
                else -> "无法读取权威订单金额，请稍后重试"
            }
        }
    }
}

private class PaymentOrderNotReadyException(cause: Throwable?) :
    RuntimeException("Payment order is not ready", cause)

@Composable
fun NativeFoodPaymentScreen(
    externalOrderNo: String,
    onBack: () -> Unit,
    onFinished: (paymentOrderNo: String, status: String) -> Unit,
    viewModel: NativeFoodPaymentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("MiniPay 外卖付款", style = MaterialTheme.typography.headlineSmall)
        Text("沙箱钱包余额支付 · 金额以服务端为准")
        if (state.loading) CircularProgressIndicator()
        if (state.storeName.isNotBlank()) Text("门店：${state.storeName}")
        Text("订单：${externalOrderNo}")
        state.paymentOrder?.let { order ->
            Text("应付：¥${"%.2f".format(order.amountCent / 100.0)}")
            Text("状态：${state.completedStatus ?: order.status}")
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.error != null && state.paymentOrder == null && !state.loading) {
            Button(onClick = viewModel::load, modifier = Modifier.fillMaxWidth()) {
                Text("重新读取订单")
            }
        }
        if (state.completedStatus == null && state.paymentOrder != null) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.filter(Char::isDigit).take(6) },
                label = { Text("支付密码") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = !state.submitting,
                onClick = { viewModel.confirm(password) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (state.submitting) "付款中…" else "确认支付") }
        }
        OutlinedButton(
            onClick = {
                val payment = state.paymentOrder
                val status = state.completedStatus
                if (payment != null && status != null) {
                    onFinished(payment.paymentOrderNo, status)
                } else onBack()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.completedStatus == null) "返回订单" else "完成")
        }
    }
}
