package com.minipay.mobile.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class PhoneWidthClass { NARROW, STANDARD, WIDE }

@Immutable
data class PhoneLayoutSpec(
    val widthClass: PhoneWidthClass,
    val shortHeight: Boolean,
    val landscape: Boolean,
    val horizontalPadding: Dp,
    val sectionSpacing: Dp,
    val maximumContentWidth: Dp
)

val LocalPhoneLayout = staticCompositionLocalOf {
    PhoneLayoutSpec(
        widthClass = PhoneWidthClass.STANDARD,
        shortHeight = false,
        landscape = false,
        horizontalPadding = 20.dp,
        sectionSpacing = 24.dp,
        maximumContentWidth = 480.dp
    )
}

@Composable
fun currentPhoneLayout(): PhoneLayoutSpec {
    val configuration = LocalConfiguration.current
    return phoneLayoutSpec(configuration.screenWidthDp, configuration.screenHeightDp)
}

internal fun phoneLayoutSpec(width: Int, height: Int): PhoneLayoutSpec {
    val widthClass = when {
        width < 360 -> PhoneWidthClass.NARROW
        width < 420 -> PhoneWidthClass.STANDARD
        else -> PhoneWidthClass.WIDE
    }
    return PhoneLayoutSpec(
        widthClass = widthClass,
        shortHeight = height < 640,
        landscape = width > height,
        horizontalPadding = if (widthClass == PhoneWidthClass.NARROW) 14.dp else 20.dp,
        sectionSpacing = if (height < 640) 16.dp else 24.dp,
        maximumContentWidth = if (width > height) 1_000.dp else 480.dp
    )
}

@Composable
fun AdaptivePhoneFrame(content: @Composable () -> Unit) {
    val spec = LocalPhoneLayout.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier.fillMaxHeight().widthIn(max = spec.maximumContentWidth).fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) { content() }
    }
}
