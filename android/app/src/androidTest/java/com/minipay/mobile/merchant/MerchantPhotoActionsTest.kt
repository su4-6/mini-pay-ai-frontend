package com.minipay.mobile.merchant

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.minipay.mobile.ui.theme.MilingTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MerchantPhotoActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun deniedCameraPermissionShowsAnErrorWithoutLaunchingCamera() {
        val registry = RecordingActivityResultRegistry(permissionResult = false)
        render(registry, cameraPermissionGranted = false)

        composeRule.onNodeWithText("拍摄").performClick()

        composeRule.onNodeWithText("需要相机权限才能拍摄店铺照片，仍可从相册选择").assertIsDisplayed()
        assertEquals(Manifest.permission.CAMERA, registry.requestedPermission)
        assertEquals(null, registry.capturedUri)
    }

    @Test
    fun grantedPermissionLaunchesCameraAndCancelReturnsToTheForm() {
        val registry = RecordingActivityResultRegistry(permissionResult = true, captureResult = false)
        render(registry, cameraPermissionGranted = false)

        composeRule.onNodeWithText("拍摄").performClick()

        composeRule.onNodeWithText("需要相机权限才能拍摄店铺照片，仍可从相册选择").assertDoesNotExist()
        assertEquals(Manifest.permission.CAMERA, registry.requestedPermission)
        assertNotNull(registry.capturedUri)
        assertTrue(merchantCameraDirectory().listFiles().isNullOrEmpty())
    }

    @Test
    fun successfulCaptureDeliversTheUriBeforeDeletingTheTemporaryFile() {
        val registry = RecordingActivityResultRegistry(captureResult = true)
        var delivered = false
        merchantCameraDirectory().deleteRecursively()
        render(registry, cameraPermissionGranted = true) { uris ->
            context.contentResolver.openFileDescriptor(uris.single(), "r").use { descriptor ->
                delivered = descriptor != null
            }
        }

        composeRule.onNodeWithText("拍摄").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { delivered }
        assertTrue(delivered)
        assertTrue(merchantCameraDirectory().listFiles().isNullOrEmpty())
    }

    @Test
    fun missingCameraApplicationShowsAnErrorAndCleansTheTemporaryFile() {
        val registry = RecordingActivityResultRegistry(cameraFailure = ActivityNotFoundException())
        merchantCameraDirectory().deleteRecursively()
        render(registry, cameraPermissionGranted = true)

        composeRule.onNodeWithText("拍摄").performClick()

        composeRule.onNodeWithText("设备上没有可用的相机应用，请从相册选择").assertIsDisplayed()
        assertTrue(merchantCameraDirectory().listFiles().isNullOrEmpty())
    }

    private fun render(
        registry: RecordingActivityResultRegistry,
        cameraPermissionGranted: Boolean,
        onUris: (List<Uri>) -> Unit = {}
    ) {
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                MilingTheme {
                    MerchantPhotoActions(
                        imageCount = 0,
                        onUris = onUris,
                        cameraPermissionGranted = { cameraPermissionGranted }
                    )
                }
            }
        }
    }

    private fun merchantCameraDirectory() = File(context.cacheDir, MERCHANT_CAMERA_DIRECTORY)
}

private class RecordingActivityResultRegistry(
    private val permissionResult: Boolean = false,
    private val captureResult: Boolean = false,
    private val cameraFailure: Throwable? = null
) : ActivityResultRegistry() {
    var requestedPermission: String? = null
    var capturedUri: Uri? = null

    override fun <I : Any?, O : Any?> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?
    ) {
        when (contract) {
            is ActivityResultContracts.RequestPermission -> {
                requestedPermission = input as String
                Handler(Looper.getMainLooper()).post { dispatchResult(requestCode, permissionResult) }
            }
            is ActivityResultContracts.TakePicture -> {
                cameraFailure?.let { throw it }
                capturedUri = input as Uri
                Handler(Looper.getMainLooper()).post { dispatchResult(requestCode, captureResult) }
            }
            else -> error("Unexpected activity result contract: ${contract::class.java.name}")
        }
    }
}
