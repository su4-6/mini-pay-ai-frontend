package com.minipay.mobile.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.minipay.mobile.auth.AuthUiState
import com.minipay.mobile.auth.CodeDeliveryStatus
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingError
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingRadii
import com.minipay.mobile.ui.theme.MilingTextSecondary
import com.minipay.mobile.ui.theme.LocalPhoneLayout

@Composable
fun VerificationCodeScreen(
    state: AuthUiState.CodeEntry,
    onCodeChange: (String) -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit
) {
    val layout = LocalPhoneLayout.current
    BackHandler {
        if (!state.submitting) onBack()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .padding(start = 20.dp, top = 18.dp)
                .size(48.dp)
                .clickable(enabled = !state.submitting, onClick = onBack)
                .testTag("verification-back"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回修改手机号",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(30.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (layout.shortHeight) 76.dp else 124.dp,
                    start = layout.horizontalPadding,
                    end = layout.horizontalPadding
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "输入验证码",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (state.deliveryStatus == CodeDeliveryStatus.SENDING) {
                    "正在向 +86 ${state.maskedMobile} 发送验证码"
                } else {
                    "验证码已发送至 +86 ${state.maskedMobile}"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MilingTextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(if (layout.shortHeight) 20.dp else 43.dp))
            OtpField(
                value = state.code,
                enabled = !state.submitting && state.deliveryStatus == CodeDeliveryStatus.SENT,
                isError = state.errorMessage != null,
                onValueChange = onCodeChange
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                state.errorMessage?.let {
                    Text(
                        text = it,
                        color = MilingError,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
            ResendControl(state = state, onResend = onResend)
        }
    }
}

@Composable
private fun OtpField(
    value: String,
    enabled: Boolean,
    isError: Boolean,
    onValueChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        focusRequester.requestFocus()
        keyboard?.show()
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = MaterialTheme.typography.headlineMedium.copy(color = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics { contentDescription = "六位短信验证码" }
            .testTag("otp-field"),
        decorationBox = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = if (maxWidth < 330.dp) 6.dp else 11.dp
                val cellWidth = ((maxWidth - gap * 5) / 6).coerceAtMost(49.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(6) { index ->
                        val active = enabled && index == value.length.coerceAtMost(5)
                        Box(
                            modifier = Modifier
                                .width(cellWidth)
                                .height(62.dp)
                                .clip(RoundedCornerShape(MilingRadii.Small))
                                .border(
                                    width = if (active || isError) 1.5.dp else 1.dp,
                                    color = when {
                                        isError -> MilingError
                                        active -> MilingPrimary
                                        else -> MilingBorder
                                    },
                                    shape = RoundedCornerShape(MilingRadii.Small)
                                )
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            value.getOrNull(index)?.let { digit ->
                                Text(
                                    text = digit.toString(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (active && value.getOrNull(index) == null) {
                                Box(
                                    Modifier.width(1.5.dp).height(35.dp).background(MilingPrimary)
                                )
                            }
                        }
                        if (index < 5) Spacer(Modifier.width(gap))
                    }
                }
            }
        }
    )
}

@Composable
private fun ResendControl(
    state: AuthUiState.CodeEntry,
    onResend: () -> Unit
) {
    val canResend = !state.submitting && (
        state.deliveryStatus == CodeDeliveryStatus.FAILED
            || (state.deliveryStatus == CodeDeliveryStatus.SENT && state.secondsUntilResend <= 0)
        )
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .height(48.dp)
            .clickable(
                enabled = canResend,
                interactionSource = interactionSource,
                indication = null,
                onClick = onResend
            )
            .testTag("resend-code"),
        contentAlignment = Alignment.Center
    ) {
        if (state.submitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MilingPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = when {
                    state.deliveryStatus == CodeDeliveryStatus.SENDING -> "验证码发送中，请稍候"
                    canResend -> "重新发送"
                    else -> "${state.secondsUntilResend} 秒后重新发送"
                },
                color = if (canResend) MilingPrimary else MilingTextSecondary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
