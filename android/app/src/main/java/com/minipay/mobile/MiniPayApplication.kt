package com.minipay.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.minipay.mobile.platform.AmapPrivacy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient

@HiltAndroidApp
class MiniPayApplication : Application(), ImageLoaderFactory {
    @Inject
    lateinit var httpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        // 高德三套 SDK（定位 / 地图 / 搜索）都要求在首次使用前声明隐私合规，
        // 否则定位回调 errorCode=12、天气搜索抛「缺少隐私合规接口调用」，
        // 首页表现为「定位失败」。
        AmapPrivacy.agree(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(httpClient)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(100L * 1024L * 1024L)
                .build()
        }
        .crossfade(false)
        .build()
}
