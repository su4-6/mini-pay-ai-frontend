package com.minipay.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val MilingBackground = Color(0xFFFFFFFF)
val MilingHomeBackground = Color(0xFFF4F6F9)
val MilingSurface = Color(0xFFFFFFFF)
val MilingSurfaceSubtle = Color(0xFFF7F9FC)
val MilingSurfaceBlue = Color(0xFFF2F6FF)
val MilingTextPrimary = Color(0xFF111318)
val MilingTextSecondary = Color(0xFF667085)
val MilingTextMuted = Color(0xFF8E96A3)
val MilingBorder = Color(0xFFDCE3EE)
val MilingDivider = Color(0xFFEEF1F5)
val MilingIconSecondary = Color(0xFF7C8594)
val MilingIconPrimary = Color(0xFF111318)
val MilingPrimary = Color(0xFF1677FF)
val MilingPrimaryPressed = Color(0xFF0958D9)
val MilingPrimarySoft = Color(0xFFEAF3FF)
val MilingLilac = Color(0xFF8B6DFF)
val MilingLilacSoft = Color(0xFFF1EDFF)
val MilingGradientStart = Color(0xFF2F80FF)
val MilingGradientMiddle = Color(0xFF5D72FF)
val MilingGradientEnd = Color(0xFF9A6BFF)
val MilingError = Color(0xFFDC2626)
val MilingSuccess = Color(0xFF16A34A)
val MilingSuccessSoft = Color(0xFFECFDF3)

object MilingSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Section = 32.dp
}

object MilingRadii {
    val Small = 10.dp
    val Medium = 16.dp
    val Large = 20.dp
    val ExtraLarge = 28.dp
}

object MilingHomeTokens {
    val PageHorizontal = 12.dp
    val SectionGap = 10.dp
    val HeaderHeight = 198.dp
    val HeaderAndServicesHeight = 334.dp
    val ServicePanelHeight = 154.dp
    val SearchHeight = 44.dp
    val CardRadius = 18.dp
    val ServiceIconSize = 40.dp
    val BottomBarHeight = 64.dp
    val BottomContentPadding = 72.dp
}

private val MilingTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 42.sp,
        lineHeight = 50.sp,
        fontWeight = FontWeight.Bold
    ),
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = TextStyle(
        fontSize = 18.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = TextStyle(
        fontSize = 17.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    ),
    labelMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal
    )
)

private val MilingLightColors = lightColorScheme(
    primary = MilingPrimary,
    onPrimary = Color.White,
    primaryContainer = MilingPrimarySoft,
    onPrimaryContainer = MilingTextPrimary,
    background = MilingBackground,
    onBackground = MilingTextPrimary,
    surface = MilingBackground,
    onSurface = MilingTextPrimary,
    surfaceVariant = MilingSurfaceSubtle,
    onSurfaceVariant = MilingTextSecondary,
    outline = MilingBorder,
    error = MilingError
)

@Composable
fun MilingTheme(content: @Composable () -> Unit) {
    val phoneLayout = currentPhoneLayout()
    CompositionLocalProvider(LocalPhoneLayout provides phoneLayout) {
        MaterialTheme(
            colorScheme = MilingLightColors,
            typography = MilingTypography,
            content = content
        )
    }
}
