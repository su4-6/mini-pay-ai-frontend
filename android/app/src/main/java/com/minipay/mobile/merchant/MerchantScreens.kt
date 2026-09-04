package com.minipay.mobile.merchant

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import coil.compose.AsyncImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Instant

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MerchantReceiveScreen(
    state: MerchantPortalState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onUpload: (ByteArray, String, (String?) -> Unit) -> Unit,
    onShopNameChange: (String) -> Unit,
    onSelectLocation: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetryInitialization: () -> Unit,
    onRealName: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) { onLoad() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) onLoad() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("经营收钱") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFFF9D1C), titleContentColor = Color.White)
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.application?.applyStatus == "APPROVED" -> ApprovedMerchant(
                state = state,
                modifier = Modifier.padding(padding),
                onRetryInitialization = onRetryInitialization
            )
            state.application?.applyStatus == "PENDING" -> ApplicationStatus(state.application, Modifier.padding(padding))
            else -> MerchantApplicationForm(
                state = state,
                modifier = Modifier.padding(padding),
                onUpload = onUpload,
                onShopNameChange = onShopNameChange,
                onSelectLocation = onSelectLocation,
                onRemoveImage = onRemoveImage,
                onSubmit = onSubmit,
                onRealName = onRealName
            )
        }
    }
}

@Composable
private fun ApplicationStatus(application: MerchantApplication, modifier: Modifier) {
    Column(modifier.fillMaxSize().background(Color(0xFFF6F7F9)).padding(20.dp)) {
        Text("入驻审核", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(18.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                StatusRow("经营名称", application.shopName)
                StatusRow("商户类型", "个体工商户")
                StatusRow("提交时间", application.applyTime.replace('T', ' ').take(19))
                StatusRow("审核状态", "审核中")
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("页面每 10 秒自动刷新，审核完成后将直接显示经营收款码。", color = Color.Gray)
            }
        }
    }
}

@Composable private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) { Text(label, color = Color.Gray, modifier = Modifier.width(92.dp)); Text(value) }
}

@Composable
internal fun ApprovedMerchant(
    state: MerchantPortalState,
    modifier: Modifier,
    onRetryInitialization: () -> Unit
) {
    val code = merchantQrContent(state.initialization)
    Column(
        modifier.fillMaxSize().background(Color(0xFFFF9D1C)).verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = RoundedCornerShape(26.dp), color = Color.White) {
            Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Verified, null, tint = Color(0xFF1E9E61), modifier = Modifier.size(42.dp))
                Text(state.application?.shopName.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                Text("审核通过 · 可正常收款", color = Color(0xFF1E9E61))
                Spacer(Modifier.height(20.dp))
                when {
                    code != null -> MerchantQr(code)
                    state.initializationLoading -> {
                        CircularProgressIndicator(Modifier.testTag("merchant_qr_loading"))
                        Text("正在生成收款码…", color = Color.Gray, modifier = Modifier.padding(top = 10.dp))
                    }
                    else -> {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(38.dp)
                        )
                        Text(
                            state.initializationError ?: "收款码生成失败，请重新生成",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 10.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        OutlinedButton(
                            onClick = onRetryInitialization,
                            modifier = Modifier.padding(top = 10.dp).testTag("merchant_qr_retry")
                        ) { Text("重新生成") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("顾客使用 MiniPay 扫码后可通过余额付款", color = Color.Gray)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun MerchantApplicationForm(
    state: MerchantPortalState,
    modifier: Modifier,
    onUpload: (ByteArray, String, (String?) -> Unit) -> Unit,
    onShopNameChange: (String) -> Unit,
    onSelectLocation: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onSubmit: () -> Unit,
    onRealName: () -> Unit
) {
    val context = LocalContext.current
    val previous = state.application
    val draft = state.draft
    val hasLocation = draft.latitude != null && draft.longitude != null
    val imageKeys = draft.imageKeys
    var mediaError by rememberSaveable { mutableStateOf<String?>(null) }
    fun uploadUris(uris: List<Uri>) {
        mediaError = null
        val prepared = uris.take(5 - imageKeys.size).mapNotNull { uri ->
            runCatching { prepareShopImage(context, uri) }
                .onFailure { mediaError = merchantImageErrorMessage(it) }
                .getOrNull()
        }
        fun uploadNext(index: Int) {
            val image = prepared.getOrNull(index) ?: return
            runCatching {
                onUpload(image.bytes, image.contentType) { key ->
                    if (key == null) mediaError = "部分店铺照片上传失败，请重试失败的照片"
                    uploadNext(index + 1)
                }
            }.onFailure {
                mediaError = "部分店铺照片上传失败，请重试失败的照片"
                uploadNext(index + 1)
            }
        }
        uploadNext(0)
    }
    Column(modifier.fillMaxSize().background(Color(0xFFF6F7F9)).verticalScroll(rememberScrollState()).padding(18.dp)) {
        previous?.takeIf { it.applyStatus in listOf("REJECTED", "SUPPLEMENT") }?.let {
            Surface(color = Color(0xFFFFF0E6), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(if (it.applyStatus == "REJECTED") "审核未通过" else "需要补充资料", color = Color(0xFFD75A00))
                    Text(it.rejectReason ?: "请修改资料后重新提交")
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Text("入驻资料", style = MaterialTheme.typography.headlineSmall)
        Text("每个账号仅可提交一份申请，驳回后在原申请上修改。", color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        ConsumerMerchantTypeField()
        OutlinedTextField(
            value = draft.shopName,
            onValueChange = onShopNameChange,
            label = { Text("经营名称 *") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        ReadOnlyField("联系人", state.legalNameMasked ?: "未实名认证")
        if (state.legalNameMasked.isNullOrBlank()) TextButton(onClick = onRealName) { Text("先完成实名认证") }
        ReadOnlyField("联系电话", state.mobile ?: "登录手机号缺失，请重新登录")
        Spacer(Modifier.height(14.dp))
        Text("经营位置 *", style = MaterialTheme.typography.titleMedium)
        MerchantLocationSummaryCard(
            latitude = draft.latitude,
            longitude = draft.longitude,
            address = draft.address,
            onClick = onSelectLocation
        )
        Spacer(Modifier.height(14.dp))
        Text("店铺照片 *（1–5 张）", style = MaterialTheme.typography.titleMedium)
        imageKeys.forEachIndexed { index, key ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val preview = state.imageUrls[key]
                if (preview == null) Icon(Icons.Outlined.Image, null)
                else AsyncImage(
                    model = preview,
                    contentDescription = "店铺照片 ${index + 1}",
                    modifier = Modifier.size(64.dp),
                    onError = { mediaError = "照片已上传，但预览加载失败，请检查网络后重试" }
                )
                Text("店铺照片 ${index + 1}", Modifier.weight(1f).padding(10.dp));
                IconButton(onClick = { onRemoveImage(key) }) { Icon(Icons.Outlined.Delete, "删除") }
            }
        }
        MerchantPhotoActions(
            imageCount = imageKeys.size,
            enabled = !state.uploading,
            onUris = ::uploadUris
        )
        if (state.uploading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        mediaError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
        Button(
            onClick = onSubmit,
            enabled = !state.submitting && !state.uploading && draft.shopName.trim().length >= 2 && hasLocation && imageKeys.isNotEmpty() && !state.legalNameMasked.isNullOrBlank() && !state.mobile.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp).height(52.dp)
        ) { if (state.submitting) CircularProgressIndicator(Modifier.size(22.dp)) else Text(if (previous == null) "提交审核" else "修改并重新提交") }
    }
}

@Composable
internal fun MerchantLocationSummaryCard(
    latitude: Double?,
    longitude: Double?,
    address: String?,
    onClick: () -> Unit
) {
    val hasLocation = latitude != null && longitude != null
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("merchant_location_summary")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        tonalElevation = 1.dp
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFF1DC)
            ) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFFE57A00),
                    modifier = Modifier.padding(10.dp).size(26.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = address?.takeIf(String::isNotBlank)
                        ?: if (hasLocation) "已选择经营位置" else "请选择经营位置",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = if (hasLocation) {
                        merchantCoordinateLabel(requireNotNull(latitude), requireNotNull(longitude))
                    } else {
                        "打开高德地图搜索或拖动选点"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                text = if (hasLocation) "重新选择" else "选择位置",
                color = Color(0xFFE57A00),
                style = MaterialTheme.typography.labelLarge
            )
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}

@Composable
internal fun MerchantPhotoActions(
    imageCount: Int,
    enabled: Boolean = true,
    onUris: (List<Uri>) -> Unit,
    cameraPermissionGranted: (Context) -> Boolean = { context ->
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
) {
    val context = LocalContext.current
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraError by rememberSaveable { mutableStateOf<String?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { onUris(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val capturedUri = cameraUri?.let(Uri::parse)
        val capturedFile = cameraFilePath?.let(::File)
        if (ok && capturedUri != null) onUris(listOf(capturedUri))
        else if (ok) cameraError = "未能读取拍摄的照片，请重试"
        capturedFile?.delete()
        cameraUri = null
        cameraFilePath = null
    }
    fun launchCamera() {
        cameraError = null
        runCatching {
            createMerchantCameraTarget(context).also { target ->
                cameraUri = target.uri.toString()
                cameraFilePath = target.file.absolutePath
                camera.launch(target.uri)
            }
        }.onFailure { error ->
            cameraFilePath?.let(::File)?.delete()
            cameraUri = null
            cameraFilePath = null
            cameraError = merchantCameraErrorMessage(error)
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
        else cameraError = "需要相机权限才能拍摄店铺照片，仍可从相册选择"
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = enabled && imageCount < 5
        ) { Icon(Icons.Outlined.PhotoLibrary, null); Text("相册") }
        OutlinedButton(
            onClick = {
                if (cameraPermissionGranted(context)) launchCamera()
                else cameraPermission.launch(Manifest.permission.CAMERA)
            },
            enabled = enabled && imageCount < 5
        ) { Icon(Icons.Outlined.PhotoCamera, null); Text("拍摄") }
    }
    cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
}

internal const val MERCHANT_CAMERA_DIRECTORY = "merchant-shop-photos"

internal data class MerchantCameraTarget(val file: File, val uri: Uri)

internal fun createMerchantCameraTarget(context: Context): MerchantCameraTarget {
    val directory = File(context.cacheDir, MERCHANT_CAMERA_DIRECTORY)
    if (!directory.isDirectory && !directory.mkdirs()) {
        throw IOException("Unable to create merchant camera directory")
    }
    val file = File.createTempFile("merchant-shop-", ".jpg", directory)
    return runCatching {
        MerchantCameraTarget(
            file = file,
            uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        )
    }.getOrElse { error ->
        file.delete()
        throw error
    }
}

internal fun merchantCameraErrorMessage(error: Throwable): String = when (error) {
    is ActivityNotFoundException -> "设备上没有可用的相机应用，请从相册选择"
    is SecurityException -> "无法启动相机，请检查相机权限后重试"
    is IOException, is IllegalArgumentException -> "无法准备拍照文件，请稍后重试"
    else -> "无法启动相机，请稍后重试"
}

internal fun merchantImageErrorMessage(error: Throwable): String = when {
    error.message?.contains("5MB", ignoreCase = true) == true -> "图片压缩后仍超过 5MB，请选择较小的图片"
    else -> "照片处理失败，请重新选择或拍摄"
}

@Composable private fun ReadOnlyField(label: String, value: String) {
    OutlinedTextField(value, {}, readOnly = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
}

@Composable
internal fun ConsumerMerchantTypeField() {
    ReadOnlyField("商户类型", "个体工商户")
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MerchantPaymentScreen(
    merchantName: String,
    state: MerchantPortalState,
    onBack: () -> Unit,
    onPay: (Long, String) -> Unit
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val cents = remember(amount) { merchantYuanToCent(amount) }
    Scaffold(topBar = { TopAppBar(title = { Text("向商户付款") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Storefront, null, tint = Color(0xFFFF9D1C), modifier = Modifier.size(52.dp))
            Text(merchantName, style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(amount, { amount = it }, label = { Text("付款金额（元）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
            OutlinedTextField(password, { password = it.filter(Char::isDigit).take(6) }, label = { Text("六位支付密码") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.paymentOrder?.let { Text(if (it.status == "SUCCEEDED") "付款成功" else "付款结果：${it.status}", color = Color(0xFF1E9E61), modifier = Modifier.padding(16.dp)) }
            Button(onClick = { cents?.let { onPay(it, password) } }, enabled = cents != null && password.length == 6 && !state.paying, modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp)) {
                if (state.paying) CircularProgressIndicator(Modifier.size(22.dp)) else Text("确认付款")
            }
        }
    }
}

internal fun merchantYuanToCent(value: String): Long? {
    if (!value.matches(Regex("^(?:0|[1-9]\\d*)(?:\\.\\d{1,2})?$"))) return null
    return runCatching { java.math.BigDecimal(value).movePointRight(2).longValueExact().takeIf { it in 1..1_000_000 } }.getOrNull()
}

private data class PreparedShopImage(val bytes: ByteArray, val contentType: String)
private fun prepareShopImage(context: Context, uri: Uri): PreparedShopImage {
    val orientation = context.contentResolver.openInputStream(uri)!!.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    val original = context.contentResolver.openInputStream(uri)!!.use(BitmapFactory::decodeStream)
        ?: error("无法读取图片")
    val scale = minOf(1f, 2048f / maxOf(original.width, original.height))
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(when (orientation) { ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f })
    }
    val bitmap = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
    var quality = 92
    var bytes: ByteArray
    do { bytes = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it); it.toByteArray() }; quality -= 8 } while (bytes.size > 5 * 1024 * 1024 && quality >= 52)
    require(bytes.size <= 5 * 1024 * 1024) { "图片压缩后仍超过 5MB" }
    if (bitmap !== original) bitmap.recycle()
    original.recycle()
    return PreparedShopImage(bytes, "image/jpeg")
}

@Composable internal fun MerchantQr(value: String) {
    val bitmap = remember(value) {
        runCatching {
            require(value.isNotBlank()) { "Merchant QR content is blank" }
            val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 560, 560)
            Bitmap.createBitmap(560, 560, Bitmap.Config.ARGB_8888).also { image ->
                for (x in 0 until 560) for (y in 0 until 560) image.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }.asImageBitmap()
        }
    }
    bitmap.fold(
        onSuccess = { Image(it, "经营收款二维码", Modifier.size(250.dp).testTag("merchant_qr_image")) },
        onFailure = {
            Text(
                "二维码生成失败，请重新生成",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("merchant_qr_render_error")
            )
        }
    )
}
