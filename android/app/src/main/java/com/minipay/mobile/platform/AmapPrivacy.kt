package com.minipay.mobile.platform

import android.content.Context
import android.util.Log
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.ServiceSettings

/**
 * 高德 SDK 的隐私合规声明。
 *
 * 高德的三套 SDK 各自要求在**首次使用前**调用自己的
 * `updatePrivacyShow(...)` + `updatePrivacyAgree(...)`，否则相关能力直接失败：
 * 定位回调 errorCode = 12，搜索（天气、POI、逆地理）抛「缺少隐私合规接口调用」。
 *
 * 现状问题：原来只有定位（[com.minipay.mobile.home.AMapLocationWeatherProvider]）
 * 与商户选点（[com.minipay.mobile.merchant.initializeMerchantAmap]）各自声明，
 * **首页的天气搜索没有声明** → 首页整条「定位 + 天气」链路一起失败，
 * 界面上只显示「定位失败」。
 *
 * 现在统一在 Application 启动时声明一次，之后各处只做兜底调用（幂等）。
 */
object AmapPrivacy {

    private const val TAG = "MiniPayAmap"

    @Volatile
    private var declared = false

    /** 幂等：进程内只真正执行一次，重复调用几乎零成本。 */
    fun agree(context: Context) {
        if (declared) return
        val app = context.applicationContext
        runCatching {
            AMapLocationClient.updatePrivacyShow(app, true, true)
            AMapLocationClient.updatePrivacyAgree(app, true)
            MapsInitializer.updatePrivacyShow(app, true, true)
            MapsInitializer.updatePrivacyAgree(app, true)
            ServiceSettings.updatePrivacyShow(app, true, true)
            ServiceSettings.updatePrivacyAgree(app, true)
        }.onSuccess {
            declared = true
        }.onFailure {
            // 不抛出：地图相关界面各自还有降级提示，不应因为一次声明失败让应用启动崩溃。
            Log.w(TAG, "AMap privacy declaration failed; type=${it.javaClass.simpleName}")
        }
    }

    /** 供测试与诊断使用：当前进程是否已完成声明。 */
    internal fun isDeclared(): Boolean = declared
}
