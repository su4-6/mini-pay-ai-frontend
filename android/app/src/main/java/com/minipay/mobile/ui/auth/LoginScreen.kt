package com.minipay.mobile.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.minipay.mobile.R
import com.minipay.mobile.auth.AuthUiState
import com.minipay.mobile.ui.theme.MilingBorder
import com.minipay.mobile.ui.theme.MilingError
import com.minipay.mobile.ui.theme.MilingGradientEnd
import com.minipay.mobile.ui.theme.MilingGradientMiddle
import com.minipay.mobile.ui.theme.MilingGradientStart
import com.minipay.mobile.ui.theme.MilingPrimary
import com.minipay.mobile.ui.theme.MilingRadii
import com.minipay.mobile.ui.theme.MilingSurface
import com.minipay.mobile.ui.theme.MilingTextMuted
import com.minipay.mobile.ui.theme.MilingTextSecondary
import com.minipay.mobile.ui.theme.LocalPhoneLayout

private const val LOGIN_TRANSITION_MILLIS = 220

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun LoginScreen(
    state: AuthUiState.PhoneEntry,
    onMobileChange: (String) -> Unit,
    onClearMobile: () -> Unit,
    onToggleAgreement: () -> Unit,
    onSendCode: () -> Unit,
    onOpenUserAgreement: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LoginScreenContent(
        state = state,
        keyboardVisible = WindowInsets.isImeVisible,
        onDismissKeyboard = {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        },
        onMobileChange = onMobileChange,
        onClearMobile = onClearMobile,
        onToggleAgreement = onToggleAgreement,
        onSendCode = onSendCode,
        onOpenUserAgreement = onOpenUserAgreement,
        onOpenPrivacyPolicy = onOpenPrivacyPolicy
    )
}

@Composable
internal fun LoginScreenContent(
    state: AuthUiState.PhoneEntry,
    keyboardVisible: Boolean,
    onDismissKeyboard: () -> Unit,
    onMobileChange: (String) -> Unit,
    onClearMobile: () -> Unit,
    onToggleAgreement: () -> Unit,
    onSendCode: () -> Unit,
    onOpenUserAgreement: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit
) {
    val layout = LocalPhoneLayout.current
    val compact = layout.shortHeight || layout.landscape
    val formBottomPadding by animateDpAsState(
        targetValue = if (keyboardVisible || compact) 4.dp else 88.dp,
        animationSpec = tween(LOGIN_TRANSITION_MILLIS),
        label = "login form bottom padding"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        AnimatedLoginHeader(
            keyboardVisible = keyboardVisible,
            compact = compact,
            onDismissKeyboard = onDismissKeyboard,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = layout.horizontalPadding)
                .padding(bottom = formBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PhoneNumberField(
                mobile = state.mobile,
                onMobileChange = onMobileChange,
                onClear = onClearMobile
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                state.errorMessage?.let {
                    Text(
                        text = it,
                        color = MilingError,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            PrimaryLoginButton(
                loading = state.submitting,
                onClick = onSendCode
            )
            Spacer(Modifier.height(20.dp))
            AgreementRow(
                accepted = state.agreementAccepted,
                onToggle = onToggleAgreement,
                onOpenUserAgreement = onOpenUserAgreement,
                onOpenPrivacyPolicy = onOpenPrivacyPolicy
            )
        }
    }
}

@Composable
private fun AnimatedLoginHeader(
    keyboardVisible: Boolean,
    compact: Boolean,
    onDismissKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val logoSize by animateDpAsState(
        targetValue = if (keyboardVisible) 48.dp else if (compact) 64.dp else 154.dp,
        animationSpec = tween(LOGIN_TRANSITION_MILLIS),
        label = "login logo size"
    )
    val logoTopOffset by animateDpAsState(
        targetValue = if (keyboardVisible) 4.dp else if (compact) 12.dp else 112.dp,
        animationSpec = tween(LOGIN_TRANSITION_MILLIS),
        label = "login logo top offset"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 96.dp else 380.dp)
    ) {
        AnimatedVisibility(
            visible = keyboardVisible,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 8.dp, y = 4.dp),
            enter = fadeIn(tween(LOGIN_TRANSITION_MILLIS)),
            exit = fadeOut(tween(LOGIN_TRANSITION_MILLIS))
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismissKeyboard)
                    .testTag("dismiss-keyboard-button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "收起键盘",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.minipay_login_logo),
            contentDescription = "Mini Pay 品牌标志",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = logoTopOffset)
                .size(logoSize)
                .testTag("login-logo")
        )
        AnimatedVisibility(
            visible = !keyboardVisible && !compact,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 286.dp),
            enter = fadeIn(tween(LOGIN_TRANSITION_MILLIS)) +
                slideInVertically(tween(LOGIN_TRANSITION_MILLIS)) { -it / 4 },
            exit = fadeOut(tween(LOGIN_TRANSITION_MILLIS)) +
                slideOutVertically(tween(LOGIN_TRANSITION_MILLIS)) { -it / 4 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Mini Pay",
                    style = MaterialTheme.typography.displayLarge.merge(
                        TextStyle(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    MilingGradientStart,
                                    MilingGradientMiddle,
                                    MilingGradientEnd
                                )
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    )
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "你的智能支付与生活助手",
                    color = MilingTextSecondary,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun PhoneNumberField(
    mobile: String,
    onMobileChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(MilingRadii.ExtraLarge))
            .border(1.dp, MilingBorder, RoundedCornerShape(MilingRadii.ExtraLarge))
            .background(MilingSurface)
            .padding(horizontal = 18.dp)
            .testTag("mobile-field"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "+86",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "当前仅支持中国大陆 +86",
            tint = MilingTextMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Canvas(Modifier.size(width = 1.dp, height = 30.dp)) {
            drawLine(
                color = MilingBorder,
                start = Offset.Zero,
                end = Offset(0f, size.height),
                strokeWidth = size.width
            )
        }
        Spacer(Modifier.width(17.dp))
        BasicTextField(
            value = mobile,
            onValueChange = onMobileChange,
            modifier = Modifier
                .weight(1f)
                .testTag("mobile-input")
                .semantics { contentDescription = "手机号" },
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Normal
            ),
            singleLine = true,
            cursorBrush = SolidColor(MilingPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (mobile.isEmpty()) {
                        Text(
                            text = "请输入手机号",
                            color = MilingTextMuted,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Normal
                        )
                    }
                    inner()
                }
            }
        )
        if (mobile.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "清空手机号",
                tint = Color.White,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFC7CDD7))
                    .clickable(onClick = onClear)
                    .padding(7.dp)
            )
        }
    }
}

@Composable
private fun PrimaryLoginButton(
    loading: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(MilingRadii.Medium))
            .background(
                Brush.horizontalGradient(
                    listOf(MilingGradientStart, MilingPrimary)
                )
            )
            .clickable(
                enabled = !loading,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag("send-code-button"),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(23.dp)
            )
        } else {
            Text(
                text = "获取验证码",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AgreementRow(
    accepted: Boolean,
    onToggle: () -> Unit,
    onOpenUserAgreement: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onToggle)
                .testTag("agreement-checkbox"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .then(
                        if (accepted) Modifier.background(MilingPrimary)
                        else Modifier.border(1.2.dp, Color(0xFFB7BFCA), CircleShape)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (accepted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已同意",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
        Text(
            text = "我已阅读并同意",
            color = MilingTextSecondary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
        Text(
            text = "《用户协议》",
            color = MilingPrimary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .clickable(onClick = onOpenUserAgreement)
        )
        Text(
            text = "和",
            color = MilingTextSecondary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.CenterVertically)
        )
        Text(
            text = "《隐私政策》",
            color = MilingPrimary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .clickable(onClick = onOpenPrivacyPolicy)
        )
    }
}
