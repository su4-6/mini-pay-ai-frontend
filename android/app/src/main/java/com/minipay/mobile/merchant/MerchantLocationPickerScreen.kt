package com.minipay.mobile.merchant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.core.PoiItemV2
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.poisearch.PoiResultV2
import com.amap.api.services.poisearch.PoiSearchV2
import com.minipay.mobile.BuildConfig
import java.util.Locale
import kotlinx.coroutines.delay

private const val MERCHANT_MAP_DEBOUNCE_MILLIS = 400L
private const val MERCHANT_SEARCH_DEBOUNCE_MILLIS = 300L
private const val MERCHANT_MAP_LOAD_TIMEOUT_MILLIS = 8_000L
private const val MERCHANT_NEARBY_RADIUS_METERS = 1_000f
private const val MERCHANT_DEFAULT_LATITUDE = 34.6197
private const val MERCHANT_DEFAULT_LONGITUDE = 112.4540

internal data class MerchantLocationSelection(
    val latitude: Double,
    val longitude: Double,
    val address: String?
)

internal data class MerchantPlaceOption(
    val id: String,
    val name: String,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val distanceMeters: Int? = null
)

internal fun merchantCoordinateLabel(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "%.6f, %.6f", latitude, longitude)

internal fun merchantPreferredAddress(formatted: String?, fallback: String?): String? =
    sequenceOf(formatted, fallback)
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull()

internal fun merchantCombinedAddress(district: String?, address: String?): String? {
    val normalizedDistrict = district?.trim().orEmpty()
    val normalizedAddress = address?.trim().orEmpty()
    return when {
        normalizedAddress.isEmpty() -> normalizedDistrict.takeIf(String::isNotEmpty)
        normalizedDistrict.isEmpty() || normalizedAddress.startsWith(normalizedDistrict) -> normalizedAddress
        else -> normalizedDistrict + normalizedAddress
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MerchantLocationPickerScreen(
    initialLatitude: Double?,
    initialLongitude: Double?,
    initialAddress: String?,
    onBack: () -> Unit,
    onConfirm: (MerchantLocationSelection) -> Unit
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val hasInitialLocation = isValidMerchantCoordinate(initialLatitude, initialLongitude)
    val initialLat = if (hasInitialLocation) requireNotNull(initialLatitude) else MERCHANT_DEFAULT_LATITUDE
    val initialLng = if (hasInitialLocation) requireNotNull(initialLongitude) else MERCHANT_DEFAULT_LONGITUDE

    var selectedLatitude by rememberSaveable { mutableStateOf(initialLat) }
    var selectedLongitude by rememberSaveable { mutableStateOf(initialLng) }
    var selectedAddress by rememberSaveable { mutableStateOf(initialAddress?.trim()?.takeIf(String::isNotEmpty)) }
    var fallbackAddressForNextMove by remember { mutableStateOf(initialAddress) }
    var nearbyPlaces by remember { mutableStateOf<List<MerchantPlaceOption>>(emptyList()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchSuggestions by remember { mutableStateOf<List<MerchantPlaceOption>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var addressLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var mapServiceError by remember { mutableStateOf<String?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var currentCityCode by remember { mutableStateOf<String?>(null) }
    var cameraRevision by remember { mutableIntStateOf(0) }
    var addressRequestId by remember { mutableIntStateOf(0) }
    var searchRequestId by remember { mutableIntStateOf(0) }
    var placeLookupRequestId by remember { mutableIntStateOf(0) }
    var locationRequestId by remember { mutableIntStateOf(0) }
    var mapAttempt by rememberSaveable { mutableIntStateOf(0) }
    var mapLoaded by remember(mapAttempt) { mutableStateOf(false) }
    var mapLoadError by remember(mapAttempt) { mutableStateOf<String?>(null) }
    var initialCameraSettled by remember(mapAttempt) { mutableStateOf(false) }
    var attemptedInitialLocation by rememberSaveable { mutableStateOf(false) }

    val initializationError = remember {
        initializeMerchantAmap(applicationContext)
    }
    val mapViewResult = remember(initializationError, mapAttempt) {
        if (initializationError != null) {
            Result.failure(IllegalStateException(initializationError))
        } else {
            runCatching { MapView(context).apply { onCreate(null) } }
        }
    }
    val mapView = mapViewResult.getOrNull()
    val mapCreationError = initializationError
        ?: mapViewResult.exceptionOrNull()?.let { "地图初始化失败，请稍后重试" }
    val locationClient = remember(initializationError) {
        if (initializationError != null) null
        else runCatching { AMapLocationClient(applicationContext) }.getOrNull()
    }

    fun moveMapTo(latitude: Double, longitude: Double, fallbackAddress: String?) {
        if (!isValidMerchantCoordinate(latitude, longitude)) return
        selectedLatitude = latitude
        selectedLongitude = longitude
        selectedAddress = fallbackAddress?.trim()?.takeIf(String::isNotEmpty)
        fallbackAddressForNextMove = fallbackAddress
        mapView?.map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 17f)
        )
        if (mapView == null) cameraRevision += 1
    }

    fun selectResolvedPlace(place: MerchantPlaceOption) {
        val latitude = place.latitude ?: return
        val longitude = place.longitude ?: return
        locationRequestId += 1
        searchRequestId += 1
        focusManager.clearFocus()
        searchSuggestions = emptyList()
        searchError = null
        moveMapTo(latitude, longitude, place.address)
    }

    fun lookUpPlace(place: MerchantPlaceOption) {
        if (place.latitude != null && place.longitude != null) {
            selectResolvedPlace(place)
            return
        }
        if (place.id.isBlank()) {
            searchError = "该地点没有可用坐标，请选择其他结果"
            return
        }
        val requestId = placeLookupRequestId + 1
        placeLookupRequestId = requestId
        searchLoading = true
        searchError = null
        val search = runCatching {
            PoiSearchV2(applicationContext, PoiSearchV2.Query("", "", currentCityCode.orEmpty()))
        }.getOrElse {
            searchLoading = false
            searchError = "地点详情服务暂不可用，请稍后重试"
            return
        }
        search.setOnPoiSearchListener(object : PoiSearchV2.OnPoiSearchListener {
            override fun onPoiSearched(result: PoiResultV2?, code: Int) = Unit

            override fun onPoiItemSearched(item: PoiItemV2?, code: Int) {
                if (requestId != placeLookupRequestId) return
                searchLoading = false
                val resolved = item?.toMerchantPlaceOption()
                if (code == AMapException.CODE_AMAP_SUCCESS && resolved?.latitude != null) {
                    selectResolvedPlace(resolved)
                } else {
                    searchError = merchantMapErrorMessage(code)
                }
            }
        })
        runCatching { search.searchPOIIdAsyn(place.id) }
            .onFailure {
                if (requestId == placeLookupRequestId) {
                    searchLoading = false
                    searchError = "地点详情加载失败，请稍后重试"
                }
            }
    }

    fun startOneShotLocation() {
        val client = locationClient
        if (client == null) {
            locationError = "定位服务初始化失败，仍可搜索或拖动地图选点"
            return
        }
        val requestId = locationRequestId + 1
        locationRequestId = requestId
        locationError = null
        runCatching {
            client.stopLocation()
            client.setLocationListener { location ->
                client.stopLocation()
                if (requestId != locationRequestId) return@setLocationListener
                if (location != null && location.errorCode == 0 &&
                    isValidMerchantCoordinate(location.latitude, location.longitude)
                ) {
                    locationError = null
                    moveMapTo(location.latitude, location.longitude, location.address)
                } else {
                    val code = location?.errorCode
                    locationError = if (code == null) {
                        "暂时无法获取当前位置，仍可搜索或拖动地图选点"
                    } else {
                        "暂时无法获取当前位置（错误码 $code），仍可手动选点"
                    }
                }
            }
            client.setLocationOption(
                AMapLocationClientOption().apply {
                    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                    isOnceLocation = true
                    isOnceLocationLatest = true
                    isNeedAddress = true
                    httpTimeOut = 10_000L
                }
            )
            client.startLocation()
        }.onFailure {
            locationError = "无法启动定位，仍可搜索或拖动地图选点"
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) startOneShotLocation()
        else locationError = "未授予定位权限，仍可搜索或拖动地图选点"
    }

    fun locateOrRequestPermission() {
        if (hasMerchantLocationPermission(context)) {
            startOneShotLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(mapView, mapLoaded, initialCameraSettled, hasInitialLocation) {
        if (mapView != null && mapLoaded && initialCameraSettled &&
            !hasInitialLocation && !attemptedInitialLocation
        ) {
            attemptedInitialLocation = true
            locateOrRequestPermission()
        }
    }

    LaunchedEffect(mapView, mapLoaded, mapAttempt) {
        if (mapView != null && !mapLoaded) {
            delay(MERCHANT_MAP_LOAD_TIMEOUT_MILLIS)
            if (!mapLoaded) mapLoadError = "地图加载超时，请检查网络或高德地图 Key 配置"
        }
    }

    LaunchedEffect(cameraRevision) {
        if (cameraRevision <= 0 || initializationError != null) return@LaunchedEffect
        delay(MERCHANT_MAP_DEBOUNCE_MILLIS)
        val latitude = selectedLatitude
        val longitude = selectedLongitude
        val fallbackAddress = selectedAddress
        val requestId = addressRequestId
        addressLoading = true
        mapServiceError = null
        val geocoder = runCatching { GeocodeSearch(applicationContext) }.getOrElse {
            addressLoading = false
            mapServiceError = "地址解析服务初始化失败，仍可确认当前坐标"
            return@LaunchedEffect
        }
        geocoder.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
            override fun onRegeocodeSearched(result: RegeocodeResult?, code: Int) {
                if (requestId != addressRequestId) return
                addressLoading = false
                if (code == AMapException.CODE_AMAP_SUCCESS) {
                    val resolved = result?.regeocodeAddress
                    selectedAddress = merchantPreferredAddress(resolved?.formatAddress, fallbackAddress)
                    currentCityCode = resolved?.cityCode?.takeIf(String::isNotBlank)
                        ?: resolved?.adCode?.takeIf(String::isNotBlank)
                        ?: currentCityCode
                    nearbyPlaces = resolved?.pois.orEmpty()
                        .mapNotNull(PoiItem::toMerchantPlaceOption)
                        .distinctBy { Triple(it.id, it.latitude, it.longitude) }
                        .take(20)
                    mapServiceError = null
                } else {
                    nearbyPlaces = emptyList()
                    mapServiceError = merchantMapErrorMessage(code) + "，仍可确认当前坐标"
                }
            }

            override fun onGeocodeSearched(result: GeocodeResult?, code: Int) = Unit
        })
        runCatching {
            geocoder.getFromLocationAsyn(
                RegeocodeQuery(
                    LatLonPoint(latitude, longitude),
                    MERCHANT_NEARBY_RADIUS_METERS,
                    GeocodeSearch.AMAP
                )
            )
        }.onFailure {
            if (requestId == addressRequestId) {
                addressLoading = false
                mapServiceError = "地址解析失败，仍可确认当前坐标"
            }
        }
    }

    LaunchedEffect(searchQuery, currentCityCode) {
        val keyword = searchQuery.trim()
        val requestId = searchRequestId + 1
        searchRequestId = requestId
        if (keyword.isEmpty()) {
            searchSuggestions = emptyList()
            searchLoading = false
            searchError = null
            return@LaunchedEffect
        }
        delay(MERCHANT_SEARCH_DEBOUNCE_MILLIS)
        searchLoading = true
        searchError = null
        val query = InputtipsQuery(keyword, currentCityCode.orEmpty()).apply {
            setLocation(LatLonPoint(selectedLatitude, selectedLongitude))
        }
        val inputtips = runCatching { Inputtips(applicationContext, query) }.getOrElse {
            searchLoading = false
            searchError = "地点搜索服务初始化失败"
            return@LaunchedEffect
        }
        inputtips.setInputtipsListener { tips, code ->
            if (requestId != searchRequestId) return@setInputtipsListener
            searchLoading = false
            if (code == AMapException.CODE_AMAP_SUCCESS) {
                searchSuggestions = tips.orEmpty().map { tip ->
                    val point = tip.point
                    MerchantPlaceOption(
                        id = tip.poiID.orEmpty(),
                        name = tip.name?.takeIf(String::isNotBlank) ?: keyword,
                        address = merchantCombinedAddress(tip.district, tip.address),
                        latitude = point?.latitude,
                        longitude = point?.longitude
                    )
                }.filter { it.name.isNotBlank() }.distinctBy { it.id to it.name }.take(12)
                searchError = if (searchSuggestions.isEmpty()) "未找到匹配地点" else null
            } else {
                searchSuggestions = emptyList()
                searchError = merchantMapErrorMessage(code)
            }
        }
        runCatching { inputtips.requestInputtipsAsyn() }
            .onFailure {
                if (requestId == searchRequestId) {
                    searchLoading = false
                    searchError = "地点搜索失败，请稍后重试"
                }
            }
    }

    DisposableEffect(mapView) {
        val map = mapView?.map
        map?.uiSettings?.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = false
        }
        map?.setOnMapLoadedListener {
            mapLoaded = true
            mapLoadError = null
        }
        map?.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
            override fun onCameraChange(position: CameraPosition?) = Unit

            override fun onCameraChangeFinish(position: CameraPosition?) {
                val target = position?.target ?: return
                val moved = target.latitude != selectedLatitude || target.longitude != selectedLongitude
                val fallback = fallbackAddressForNextMove?.trim()?.takeIf(String::isNotEmpty)
                selectedLatitude = target.latitude
                selectedLongitude = target.longitude
                if (fallback != null || moved) selectedAddress = fallback
                fallbackAddressForNextMove = null
                placeLookupRequestId += 1
                addressRequestId += 1
                if (initialCameraSettled) locationRequestId += 1
                else initialCameraSettled = true
                cameraRevision += 1
            }
        })
        map?.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(selectedLatitude, selectedLongitude),
                if (hasInitialLocation || selectedLatitude != MERCHANT_DEFAULT_LATITUDE ||
                    selectedLongitude != MERCHANT_DEFAULT_LONGITUDE
                ) 17f else 12f
            )
        )
        onDispose {
            map?.setOnMapLoadedListener(null)
            map?.setOnCameraChangeListener(null)
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        var destroyed = false
        fun destroyMap() {
            if (!destroyed) {
                destroyed = true
                mapView?.onDestroy()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_DESTROY -> destroyMap()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView?.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onPause()
            destroyMap()
        }
    }

    DisposableEffect(locationClient) {
        onDispose {
            locationClient?.stopLocation()
            locationClient?.onDestroy()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择经营位置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            onConfirm(
                                MerchantLocationSelection(
                                    latitude = selectedLatitude,
                                    longitude = selectedLongitude,
                                    address = selectedAddress
                                )
                            )
                        },
                        enabled = mapView != null && isValidMerchantCoordinate(selectedLatitude, selectedLongitude),
                        modifier = Modifier.testTag("merchant_location_confirm")
                    ) {
                        Text("确定")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it.take(80) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("搜索地点") },
                placeholder = { Text("输入店铺、商场或道路名称") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "清空搜索")
                        }
                    }
                }
            )
            if (searchLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (searchSuggestions.isNotEmpty()) {
                SearchSuggestionList(
                    places = searchSuggestions,
                    onSelect = ::lookUpPlace
                )
            } else {
                searchError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            Box(Modifier.fillMaxWidth().weight(1f).testTag("merchant_location_picker_map")) {
                if (mapView != null) {
                    AndroidView(
                        factory = { mapView },
                        modifier = Modifier.fillMaxSize()
                    )
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = "地图中心选点",
                        tint = Color(0xFFE86600),
                        modifier = Modifier.align(Alignment.Center).offset(y = (-20).dp).size(44.dp)
                    )
                    FloatingActionButton(
                        onClick = ::locateOrRequestPermission,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        containerColor = Color.White
                    ) {
                        Icon(Icons.Outlined.MyLocation, contentDescription = "定位到当前位置")
                    }
                    if (!mapLoaded) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.94f),
                            shadowElevation = 4.dp
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                mapLoadError?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error)
                                    TextButton(onClick = { mapAttempt += 1 }) { Text("重新加载") }
                                } ?: run {
                                    CircularProgressIndicator(Modifier.size(28.dp))
                                    Text("地图加载中…", modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                    }
                } else {
                    Surface(Modifier.fillMaxSize(), color = Color(0xFFF1F2F4)) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                mapCreationError ?: "地图暂不可用",
                                color = MaterialTheme.colorScheme.error
                            )
                            if (initializationError == null) {
                                TextButton(onClick = { mapAttempt += 1 }) { Text("重新加载") }
                            }
                        }
                    }
                }
            }

            MerchantSelectedLocationPanel(
                latitude = selectedLatitude,
                longitude = selectedLongitude,
                address = selectedAddress,
                addressLoading = addressLoading,
                error = mapServiceError ?: locationError,
                nearbyPlaces = nearbyPlaces,
                onSelect = { place ->
                    if (place.latitude != null && place.longitude != null) {
                        selectResolvedPlace(place)
                    }
                }
            )
        }
    }
}

@Composable
private fun SearchSuggestionList(
    places: List<MerchantPlaceOption>,
    onSelect: (MerchantPlaceOption) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 3.dp
    ) {
        LazyColumn(Modifier.heightIn(max = 190.dp)) {
            items(places, key = { "search-${it.id}-${it.name}" }) { place ->
                PlaceRow(place = place, onClick = { onSelect(place) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun MerchantSelectedLocationPanel(
    latitude: Double,
    longitude: Double,
    address: String?,
    addressLoading: Boolean,
    error: String?,
    nearbyPlaces: List<MerchantPlaceOption>,
    onSelect: (MerchantPlaceOption) -> Unit
) {
    Surface(tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color(0xFFE86600))
                Column(Modifier.weight(1f)) {
                    Text(
                        address?.takeIf(String::isNotBlank) ?: "已选择地图中心位置",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        merchantCoordinateLabel(latitude, longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            if (addressLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            if (nearbyPlaces.isEmpty()) {
                Text(
                    "拖动地图或搜索地点，附近位置将在这里显示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            } else {
                Text(
                    "附近地点",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
                )
                LazyColumn(Modifier.heightIn(max = 190.dp)) {
                    items(nearbyPlaces, key = { "nearby-${it.id}-${it.latitude}-${it.longitude}" }) { place ->
                        PlaceRow(place = place, onClick = { onSelect(place) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(place: MerchantPlaceOption, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(place.name) },
        supportingContent = {
            val details = buildList {
                place.address?.takeIf(String::isNotBlank)?.let(::add)
                place.distanceMeters?.takeIf { it >= 0 }?.let { add("距中心点 ${it} 米") }
            }.joinToString(" · ")
            if (details.isNotBlank()) Text(details)
        },
        leadingContent = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun initializeMerchantAmap(context: Context): String? {
    if (BuildConfig.AMAP_API_KEY.isBlank()) return "地图服务未配置，请配置高德地图 Key 后重试"
    return runCatching {
        MapsInitializer.updatePrivacyShow(context, true, true)
        MapsInitializer.updatePrivacyAgree(context, true)
        ServiceSettings.updatePrivacyShow(context, true, true)
        ServiceSettings.updatePrivacyAgree(context, true)
        AMapLocationClient.updatePrivacyShow(context, true, true)
        AMapLocationClient.updatePrivacyAgree(context, true)
    }.exceptionOrNull()?.let { "地图初始化失败，请稍后重试" }
}

private fun isValidMerchantCoordinate(latitude: Double?, longitude: Double?): Boolean =
    latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0

internal fun hasMerchantLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

internal fun merchantMapErrorMessage(code: Int): String = when (code) {
    1001, 1002, 1003, 1008, 1009, 1012, 1013,
    10001, 10002, 10003, 10008, 10009, 10012, 10013 ->
        "高德地图 Key 与应用包名、签名或服务平台不匹配"
    else -> "无法获取所选位置地址（错误码 $code）"
}

private fun PoiItem.toMerchantPlaceOption(): MerchantPlaceOption? {
    val point = latLonPoint ?: return null
    return MerchantPlaceOption(
        id = poiId.orEmpty(),
        name = title?.takeIf(String::isNotBlank) ?: "附近地点",
        address = merchantPreferredAddress(
            snippet,
            listOf(provinceName, cityName, adName)
                .mapNotNull { it?.takeIf(String::isNotBlank) }
                .distinct()
                .joinToString("")
        ),
        latitude = point.latitude,
        longitude = point.longitude,
        distanceMeters = distance.takeIf { it >= 0 }
    )
}

private fun PoiItemV2.toMerchantPlaceOption(): MerchantPlaceOption? {
    val point = latLonPoint ?: return null
    return MerchantPlaceOption(
        id = poiId.orEmpty(),
        name = title?.takeIf(String::isNotBlank) ?: "搜索地点",
        address = merchantPreferredAddress(
            snippet,
            listOf(provinceName, cityName, adName)
                .mapNotNull { it?.takeIf(String::isNotBlank) }
                .distinct()
                .joinToString("")
        ),
        latitude = point.latitude,
        longitude = point.longitude
    )
}
