package com.minipay.mobile.merchant

import com.minipay.mobile.auth.IdentityApiException
import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantPortalErrorMessageTest {
    @Test
    fun objectStorageUnavailableExplainsHowToRecover() {
        assertEquals(
            "图片存储服务未启用，请重启支付服务后重试",
            merchantPortalErrorMessage(
                IdentityApiException("OBJECT_STORAGE_UNAVAILABLE", status = 503)
            )
        )
    }

    @Test
    fun directOssUploadFailureIsDifferentFromPaymentServiceFailure() {
        assertEquals(
            "上传到图片服务器失败，请检查网络后重试",
            merchantPortalErrorMessage(
                IdentityApiException("SHOP_IMAGE_UPLOAD_FAILED", status = 403)
            )
        )
        assertEquals(
            "无法连接支付服务，请检查真机网络和端口映射",
            merchantPortalErrorMessage(IdentityApiException("NETWORK_UNAVAILABLE"))
        )
    }
}
