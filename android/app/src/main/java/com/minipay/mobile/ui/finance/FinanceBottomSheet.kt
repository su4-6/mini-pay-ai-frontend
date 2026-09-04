package com.minipay.mobile.ui.finance

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An in-tree finance overlay measured against the activity's real safe drawing area.
 * Keeping the sheet in the activity window avoids platform Dialog bounds that include hidden
 * system-bar space on Android 16/MIUI.
 */
@Composable
internal fun FinanceBottomSheetOverlay(
    preferredHeight: Dp,
    sheetTestTag: String,
    scrimColor: Color,
    content: @Composable ColumnScope.(compactHeight: Boolean) -> Unit
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawing = WindowInsets.safeDrawing
    val topInset = with(density) { safeDrawing.getTop(this).toDp() }
    val bottomInset = with(density) { safeDrawing.getBottom(this).toDp() }
    val leftInset = with(density) { safeDrawing.getLeft(this, layoutDirection).toDp() }
    val rightInset = with(density) { safeDrawing.getRight(this, layoutDirection).toDp() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(scrimColor)
            .testTag("finance-sheet-overlay-root")
    ) {
        val availableHeight = (maxHeight - topInset - bottomInset).coerceAtLeast(0.dp)
        val compactHeight = availableHeight < 720.dp
        val boundedHeight = minOf(preferredHeight, availableHeight)
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    start = leftInset,
                    top = topInset,
                    end = rightInset,
                    bottom = bottomInset
                )
                .testTag("finance-sheet-safe-area")
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(boundedHeight)
                    .testTag(sheetTestTag),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color.White
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxSize(),
                    content = { content(compactHeight) }
                )
            }
        }
    }
}

/** Protects password pixels without creating another window with independent inset handling. */
@Composable
internal fun SecureFinanceWindow() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val wasSecure = window?.attributes?.flags
            ?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (!wasSecure) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
