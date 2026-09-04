package com.minipay.mobile.merchant

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.ServiceSettings
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.minipay.mobile.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeFalse
import org.junit.Test

class AmapConfigurationTest {
    @Test
    fun configuredAndroidKeyCanReverseGeocode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val applicationInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        val manifestKey = applicationInfo.metaData.getString("com.amap.api.v2.apikey").orEmpty()
        assumeFalse("AMap Android key is not configured for this build", BuildConfig.AMAP_API_KEY.isBlank())
        assertEquals(BuildConfig.AMAP_API_KEY, manifestKey)

        ServiceSettings.updatePrivacyShow(context, true, true)
        ServiceSettings.updatePrivacyAgree(context, true)
        val address = GeocodeSearch(context).getFromLocation(
            RegeocodeQuery(LatLonPoint(39.9087, 116.3975), 200f, GeocodeSearch.AMAP)
        )

        assertFalse("AMap reverse geocoding must return an address", address.formatAddress.isNullOrBlank())
    }
}
