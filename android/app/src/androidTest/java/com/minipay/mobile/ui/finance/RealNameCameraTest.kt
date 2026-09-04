package com.minipay.mobile.ui.finance

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.rule.GrantPermissionRule
import com.minipay.mobile.ui.theme.MilingTheme
import org.junit.Rule
import org.junit.Test

class RealNameCameraTest {
    @get:Rule(order = 0)
    val cameraPermission = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun frontCameraPreviewIsFullScreenAndKeepsCaptureControlVisible() {
        composeRule.setContent {
            MilingTheme {
                FullScreenFaceCamera(onDismiss = {}, onCaptured = {})
            }
        }

        composeRule.onNodeWithText("请正对镜头，保持面部清晰").assertIsDisplayed()
        composeRule.onNodeWithText("拍摄").assertIsDisplayed()
    }
}
