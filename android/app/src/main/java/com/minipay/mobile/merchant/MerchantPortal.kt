package com.minipay.mobile.merchant

import com.minipay.mobile.BuildConfig
import com.minipay.mobile.auth.AuthRepository
import com.minipay.mobile.auth.IdentityApiException
import com.minipay.mobile.auth.ProblemDetails
import com.minipay.mobile.auth.SessionStorage
import com.minipay.mobile.finance.ConfirmRequest
import com.minipay.mobile.finance.IssueAuthorizationRequest
import com.minipay.mobile.finance.PaymentAuthorization
import com.minipay.mobile.profile.UserProfile
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable data class MerchantApplicationPage(val items: List<MerchantApplication> = emptyList())
@Serializable data class MerchantApplication(
    val id: Long,
    val merchantType: String,
    val shopName: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val shopImages: String? = null,
    val contactName: String,
    val contactMobile: String,
    val applyStatus: String,
    val rejectReason: String? = null,
    val resultantMerchantId: String? = null,
    val applyTime: String,
    val version: Long
)
@Serializable data class MerchantSubmission(
    val version: Long? = null,
    val merchantType: String,
    val shopName: String,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val shopImages: String,
    val contactName: String,
    val contactMobile: String
)
@Serializable data class ImageUploadRequest(val fileName: String, val contentType: String, val sizeBytes: Long, val sha256: String)
@Serializable data class ImageUploadGrant(val uploadUrl: String, val objectKey: String, val requiredHeaders: Map<String, String> = emptyMap())
@Serializable data class ImageReadRequest(val objectKeys: List<String>)
@Serializable data class ImageReadResponse(val urls: Map<String, String> = emptyMap())
@Serializable data class MerchantInitialization(val merchant: MerchantSummary, val collectionCode: MerchantCollectionCode? = null, val qrContent: String? = null)
@Serializable data class MerchantSummary(val merchantId: String, val name: String)
@Serializable data class MerchantCollectionCode(val qrContent: String, val status: String)
@Serializable data class MerchantResolution(
    val type: String,
    val resolutionId: String,
    val merchantId: String,
    val merchantName: String,
    val allowedChannels: List<String>,
    val expiresAt: String
)
@Serializable data class MerchantPaymentRequest(
    val amountCent: Long,
    val subject: String,
    val paymentMethod: String = "WALLET_BALANCE",
    val resolutionId: String
)
@Serializable data class MerchantPaymentOrder(
    val paymentOrderId: String,
    val amountCent: Long,
    val status: String,
    val failureCode: String? = null
)

@Singleton
class MerchantPortalRepository @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val auth: AuthRepository,
    private val session: SessionStorage
) {
    fun currentUserId(): String? = auth.currentUserId.value
    fun currentMobile(): String? = auth.currentMobile()
    suspend fun profile(): UserProfile = get(identity("/api/v1/users/me"))
    suspend fun currentApplication(): MerchantApplication? =
        get<MerchantApplicationPage>(payment("/api/v1/merchant/onboardings?page=0&size=1")).items.firstOrNull()

    suspend fun submit(body: MerchantSubmission): MerchantApplication =
        post(payment("/api/v1/merchant/onboardings"), body, UUID.randomUUID().toString())

    suspend fun resubmit(id: Long, body: MerchantSubmission): MerchantApplication =
        put(payment("/api/v1/merchant/onboardings/$id"), body, UUID.randomUUID().toString())

    suspend fun upload(bytes: ByteArray, contentType: String, sha256: String): String {
        val extension = when (contentType) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val grant: ImageUploadGrant = post(
            payment("/api/v1/merchant/image-uploads"),
            ImageUploadRequest("shop-${UUID.randomUUID()}.$extension", contentType, bytes.size.toLong(), sha256),
            null
        )
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(grant.uploadUrl)
            grant.requiredHeaders.forEach(builder::header)
            val response = client.newCall(builder.put(bytes.toRequestBody(contentType.toMediaType())).build()).execute()
            response.use { if (!it.isSuccessful) throw IdentityApiException("SHOP_IMAGE_UPLOAD_FAILED", status = it.code) }
        }
        return grant.objectKey
    }

    suspend fun imageUrls(keys: List<String>): Map<String, String> = if (keys.isEmpty()) emptyMap() else
        post<ImageReadRequest, ImageReadResponse>(
            payment("/api/v1/merchant/image-read-urls"), ImageReadRequest(keys), null
        ).urls

    suspend fun initialize(merchantId: String): MerchantInitialization = post(
        payment("/api/v1/merchant/merchants/$merchantId/initialization"), UnitBody,
        UUID.randomUUID().toString()
    )

    suspend fun resolve(deepLink: String): MerchantResolution =
        post(payment("/api/v1/scan-resolutions"), ResolveRequest(deepLink), null)

    suspend fun pay(resolution: MerchantResolution, amountCent: Long, password: String): MerchantPaymentOrder {
        val order: MerchantPaymentOrder = post(
            payment("/api/v1/payment-orders"),
            MerchantPaymentRequest(amountCent, "向${resolution.merchantName}付款", resolutionId = resolution.resolutionId),
            UUID.randomUUID().toString()
        )
        val authorization: PaymentAuthorization = post(
            identity("/api/v1/payment-authorizations"),
            IssueAuthorizationRequest("PAYMENT_ORDER", order.paymentOrderId, amountCent, session.deviceId(), password),
            UUID.randomUUID().toString()
        )
        return post(
            payment("/api/v1/payment-orders/${order.paymentOrderId}/confirm"),
            ConfirmRequest(authorization.paymentAuthToken), null
        )
    }

    private suspend inline fun <reified T> get(url: String): T = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(url).header("Authorization", bearer()).get().build())
    }
    private suspend inline fun <reified B, reified T> post(url: String, body: B, key: String?): T =
        request(url, body, key, false)
    private suspend inline fun <reified B, reified T> put(url: String, body: B, key: String?): T =
        request(url, body, key, true)
    private suspend inline fun <reified B, reified T> request(url: String, body: B, key: String?, put: Boolean): T =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url).header("Authorization", bearer())
                .header("X-Request-Id", UUID.randomUUID().toString())
            key?.let { builder.header("Idempotency-Key", it) }
            val encoded = if (body === UnitBody) "{}" else json.encodeToString(body)
            val requestBody = encoded.toRequestBody(JSON)
            execute(if (put) builder.put(requestBody).build() else builder.post(requestBody).build())
        }
    private suspend fun bearer() = auth.validAccessToken()?.let { "Bearer $it" }
        ?: throw IdentityApiException("TOKEN_INVALID")
    private inline fun <reified T> execute(request: Request): T {
        val response = try { client.newCall(request).execute() }
        catch (error: IOException) { throw IdentityApiException("NETWORK_UNAVAILABLE", cause = error) }
        response.use {
            val raw = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val problem = runCatching { json.decodeFromString<ProblemDetails>(raw) }.getOrNull()
                throw IdentityApiException(problem?.code ?: "REQUEST_FAILED", problem?.requestId, it.code)
            }
            return json.decodeFromString(raw)
        }
    }
    private fun identity(path: String) = BuildConfig.IDENTITY_BASE_URL.trimEnd('/') + path
    private fun payment(path: String) = BuildConfig.PAYMENT_BASE_URL.trimEnd('/') + path
    @Serializable private data class ResolveRequest(val deepLink: String)
    @Serializable private data object UnitBody
    private companion object { val JSON = "application/json; charset=utf-8".toMediaType() }
}
