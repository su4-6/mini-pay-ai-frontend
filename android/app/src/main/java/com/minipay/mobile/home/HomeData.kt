package com.minipay.mobile.home

import android.content.Context
import android.util.Log
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.services.core.AMapException
import com.amap.api.services.weather.LocalWeatherForecastResult
import com.amap.api.services.weather.LocalWeatherLiveResult
import com.amap.api.services.weather.LocalWeatherLive
import com.amap.api.services.weather.WeatherSearch
import com.amap.api.services.weather.WeatherSearchQuery
import com.minipay.mobile.BuildConfig
import com.minipay.mobile.finance.FinanceRepository
import com.minipay.mobile.finance.WalletBill
import com.minipay.mobile.ui.home.AppService
import androidx.lifecycle.viewModelScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.launch

data class LocationWeather(
    val city: String,
    val adCode: String,
    val weather: String,
    val temperature: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Double? = null,
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
) {
    val displayCity: String get() = city.removeSuffix("市").ifBlank { city }
    val displayWeather: String get() = listOf(weather, temperature.takeIf { it.isNotBlank() }?.plus("°C"))
        .filterNotNull().joinToString("")
}

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double? = null,
    val capturedAtEpochMillis: Long = System.currentTimeMillis()
)

fun LocationSnapshot.isFresh(
    nowEpochMillis: Long = System.currentTimeMillis(),
    maxAgeMillis: Long = 5 * 60_000L
): Boolean = nowEpochMillis - capturedAtEpochMillis in 0..maxAgeMillis

class LocationAcquisitionException(
    val stableCode: String,
    cause: Throwable? = null
) : IllegalStateException(stableCode, cause)

interface LocationWeatherProvider {
    fun cached(): LocationWeather?
    fun cachedLocation(): LocationSnapshot?
    suspend fun locate(): Result<LocationSnapshot>
    suspend fun locateAndReadWeather(): Result<LocationWeather>
}

@Singleton
class AMapLocationWeatherProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationWeatherProvider {
    private val preferences = context.getSharedPreferences("home_location_weather", Context.MODE_PRIVATE)
    @Volatile private var latest: LocationWeather? = null
    @Volatile private var latestLocation: LocationSnapshot? = null

    override fun cached(): LocationWeather? {
        latest?.let { return it }
        val city = preferences.getString("city", null) ?: return null
        return LocationWeather(
            city = city,
            adCode = preferences.getString("adCode", "").orEmpty(),
            weather = preferences.getString("weather", "").orEmpty(),
            temperature = preferences.getString("temperature", "").orEmpty(),
            updatedAtEpochMillis = preferences.getLong("updatedAt", 0L)
        )
    }

    override fun cachedLocation(): LocationSnapshot? = latestLocation

    override suspend fun locate(): Result<LocationSnapshot> = runCatching {
        check(BuildConfig.AMAP_API_KEY.isNotBlank()) { "高德地图 Key 未配置" }
        AMapLocationClient.updatePrivacyShow(context, true, true)
        AMapLocationClient.updatePrivacyAgree(context, true)
        suspendCancellableCoroutine { continuation: CancellableContinuation<LocationSnapshot?> ->
            val client = AMapLocationClient(context)
            val option = AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isOnceLocation = true
                isOnceLocationLatest = true
                isNeedAddress = false
                httpTimeOut = 10_000L
            }
            client.setLocationOption(option)
            client.setLocationListener { result ->
                client.stopLocation()
                client.onDestroy()
                if (!continuation.isActive) return@setLocationListener
                if (result != null && result.errorCode == 0 &&
                    result.latitude in -90.0..90.0 && result.longitude in -180.0..180.0) {
                    continuation.resume(LocationSnapshot(
                        latitude = result.latitude,
                        longitude = result.longitude,
                        accuracyMeters = result.accuracy.toDouble()
                    ))
                } else {
                    Log.w(TAG, "AMap location failed; code=${result?.errorCode ?: -1}")
                    continuation.resume(null)
                }
            }
            continuation.invokeOnCancellation {
                client.stopLocation()
                client.onDestroy()
            }
            client.startLocation()
        }?.also { latestLocation = it }
            ?: throw LocationAcquisitionException("LOCATION_FAILED")
    }.recoverCatching { cause ->
        if (cause is LocationAcquisitionException) throw cause
        Log.w(TAG, "AMap location unavailable; type=${cause.javaClass.simpleName}")
        throw LocationAcquisitionException("LOCATION_FAILED", cause)
    }

    override suspend fun locateAndReadWeather(): Result<LocationWeather> = runCatching {
        check(BuildConfig.AMAP_API_KEY.isNotBlank()) { "高德地图 Key 未配置" }
        AMapLocationClient.updatePrivacyShow(context, true, true)
        AMapLocationClient.updatePrivacyAgree(context, true)
        val location: LocatedPoint = suspendCancellableCoroutine { continuation: CancellableContinuation<LocatedPoint?> ->
            val client = AMapLocationClient(context)
            val option = AMapLocationClientOption().apply {
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                isOnceLocation = true
                isOnceLocationLatest = true
                isNeedAddress = true
                httpTimeOut = 10_000L
            }
            client.setLocationOption(option)
            client.setLocationListener { result ->
                client.stopLocation()
                client.onDestroy()
                if (!continuation.isActive) return@setLocationListener
                if (result != null && result.errorCode == 0 && !result.city.isNullOrBlank()) {
                    continuation.resume(LocatedPoint(
                        result.city.orEmpty(), result.adCode.orEmpty(), result.latitude,
                        result.longitude, result.accuracy.toDouble()
                    ))
                } else {
                    continuation.resume(null)
                }
            }
            continuation.invokeOnCancellation {
                client.stopLocation()
                client.onDestroy()
            }
            client.startLocation()
        } ?: error("无法获取当前位置")

        latestLocation = LocationSnapshot(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracyMeters
        )

        val live: LocalWeatherLive = suspendCancellableCoroutine { continuation: CancellableContinuation<LocalWeatherLive?> ->
            val search = WeatherSearch(context)
            search.setOnWeatherSearchListener(object : WeatherSearch.OnWeatherSearchListener {
                override fun onWeatherLiveSearched(result: LocalWeatherLiveResult?, code: Int) {
                    if (!continuation.isActive) return
                    if (code == AMapException.CODE_AMAP_SUCCESS && result?.liveResult != null) {
                        continuation.resume(result.liveResult)
                    } else continuation.resume(null)
                }

                override fun onWeatherForecastSearched(result: LocalWeatherForecastResult?, code: Int) = Unit
            })
            search.query = WeatherSearchQuery(
                location.adCode.ifBlank { location.city },
                WeatherSearchQuery.WEATHER_TYPE_LIVE
            )
            search.searchWeatherAsyn()
        } ?: error("无法获取实时天气")

        LocationWeather(
            city = live.city.ifBlank { location.city },
            adCode = live.adCode.ifBlank { location.adCode },
            weather = live.weather.orEmpty(),
            temperature = live.temperature.orEmpty(),
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracyMeters
        ).also {
            latest = it
            save(it)
        }
    }

    private fun save(value: LocationWeather) {
        preferences.edit()
            .putString("city", value.city)
            .putString("adCode", value.adCode)
            .putString("weather", value.weather)
            .putString("temperature", value.temperature)
            .putLong("updatedAt", value.updatedAtEpochMillis)
            .apply()
    }

    private data class LocatedPoint(
        val city: String,
        val adCode: String,
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Double
    )

    private companion object {
        const val TAG = "MiniPayLocation"
    }
}

data class HomeUiState(
    val locationWeather: LocationWeather? = null,
    val locating: Boolean = false,
    val locationError: String? = null,
    val recentBills: List<WalletBill> = emptyList(),
    val billsLoading: Boolean = false,
    val billsError: String? = null,
    val commonApps: List<AppService> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val locationWeatherProvider: LocationWeatherProvider,
    private val financeRepository: FinanceRepository,
    private val commonAppsRepository: CommonAppsRepository
) : androidx.lifecycle.ViewModel() {
    private val mutableState = MutableStateFlow(
        HomeUiState(
            locationWeather = locationWeatherProvider.cached(),
            commonApps = commonAppsRepository.apps.value
        )
    )
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    init {
        refreshRecentBills()
        viewModelScope.launch {
            commonAppsRepository.apps.collect { apps ->
                mutableState.value = mutableState.value.copy(commonApps = apps)
            }
        }
    }

    fun addCommonApp(serviceId: String) = commonAppsRepository.add(serviceId)

    fun removeCommonApp(serviceId: String) = commonAppsRepository.remove(serviceId)

    fun moveCommonApp(fromIndex: Int, toIndex: Int) = commonAppsRepository.move(fromIndex, toIndex)

    fun refreshLocation(permissionGranted: Boolean) {
        if (!permissionGranted) {
            mutableState.value = mutableState.value.copy(
                locating = false,
                locationError = if (mutableState.value.locationWeather == null) "定位失败，点击重试" else "定位权限未开启"
            )
            return
        }
        if (mutableState.value.locating) return
        mutableState.value = mutableState.value.copy(locating = true, locationError = null)
        viewModelScope.launch {
            locationWeatherProvider.locateAndReadWeather()
                .onSuccess { mutableState.value = mutableState.value.copy(locationWeather = it, locating = false) }
                .onFailure {
                    mutableState.value = mutableState.value.copy(
                        locating = false,
                        locationError = if (mutableState.value.locationWeather == null) "定位失败，点击重试" else "更新失败，已显示上次结果"
                    )
                }
        }
    }

    fun refreshRecentBills() {
        if (mutableState.value.billsLoading) return
        mutableState.value = mutableState.value.copy(billsLoading = true, billsError = null)
        viewModelScope.launch {
            runCatching { financeRepository.bills(page = 1, size = 10) }
                .onSuccess { page ->
                    val recent = page.items.asSequence()
                        .filter { it.status == "SUCCEEDED" }
                        .sortedByDescending { runCatching { Instant.parse(it.occurredAt) }.getOrNull() }
                        .take(2)
                        .toList()
                    mutableState.value = mutableState.value.copy(
                        recentBills = recent,
                        billsLoading = false,
                        billsError = null
                    )
                }
                .onFailure {
                    mutableState.value = mutableState.value.copy(
                        billsLoading = false,
                        billsError = "最近消息加载失败"
                    )
                }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object HomeDataModule {
    @Provides
    @Singleton
    fun provideLocationWeatherProvider(
        @ApplicationContext context: Context
    ): LocationWeatherProvider = AMapLocationWeatherProvider(context)
}
