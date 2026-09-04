package com.minipay.mobile.ai

import com.minipay.mobile.BuildConfig
import com.minipay.mobile.auth.AuthRepository
import com.minipay.mobile.auth.SessionStorage
import com.minipay.mobile.auth.ProblemDetails
import com.minipay.mobile.home.LocationSnapshot
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class DeliveryAddressDto(
    val id: String,
    val label: String,
    val maskedSummary: String,
    val zoneCode: String,
    val defaultAddress: Boolean,
    val version: Long,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateDeliveryAddressRequest(
    val label: String,
    val recipient: String,
    val mobile: String,
    val address: String,
    val zoneCode: String,
    val defaultAddress: Boolean = true
)

@Serializable data class CreateFoodOrderRequest(val quoteId: String)

@Serializable
data class FoodBindingDto(
    val bindingId: String? = null,
    val provider: String,
    val subject: String,
    val active: Boolean,
    val createdAt: String? = null,
    val username: String? = null
)

@Serializable
data class FoodEntryStatusDto(
    val state: String,
    val bindingActive: Boolean,
    val profileUpgradeRequired: Boolean,
    val grantedScopes: Set<String> = emptySet(),
    val locationAllowed: Boolean
)

@Serializable data class FoodHandoffDto(
    val code: String,
    val deviceProof: String,
    val origin: String,
    val expiresAt: String
)

@Serializable data class FoodLocationContextRequest(
    val longitude: Double,
    val latitude: Double,
    val accuracyMeters: Double? = null,
    val capturedAt: String,
    val source: String = "AMAP_GCJ02"
)

@Serializable data class FoodLocationContextDto(
    val locationContextId: String,
    val expiresAt: String
)

@Serializable
data class FoodOrderItemDto(
    val productId: Long,
    val name: String,
    val sku: String? = null,
    val image: String? = null,
    val quantity: Int,
    val unitPriceCent: Long,
    val lineAmountCent: Long
)

@Serializable
data class FoodOrderSummaryDto(
    val orderRefId: String,
    val externalOrderNo: String,
    val amountCent: Long,
    val currency: String,
    val paymentOrderId: String? = null,
    val paymentStatus: String,
    val fulfillmentStatus: String,
    val refundStatus: String,
    val expiresAt: String,
    val createdAt: String,
    val storeName: String? = null,
    val fulfillmentType: String? = null,
    val recipient: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val items: List<FoodOrderItemDto> = emptyList(),
    val totalQuantity: Int = 0,
    val subtotalCent: Long = 0,
    val deliveryFeeCent: Long = 0,
    val discountCent: Long = 0
)

@Serializable
data class FoodOrderDetailDto(
    val orderRefId: String,
    val externalOrderNo: String,
    val amountCent: Long,
    val currency: String,
    val paymentOrderId: String? = null,
    val paymentStatus: String,
    val fulfillmentStatus: String,
    val refundStatus: String,
    val expiresAt: String,
    val createdAt: String,
    val storeName: String? = null,
    val fulfillmentType: String? = null,
    val recipient: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val items: List<FoodOrderItemDto> = emptyList(),
    val totalQuantity: Int = 0,
    val subtotalCent: Long = 0,
    val deliveryFeeCent: Long = 0,
    val discountCent: Long = 0
)

@Serializable
data class FoodOrderDto(
    val id: String,
    val orderNo: String,
    val merchantId: String,
    val merchantName: String,
    val addressId: String,
    val addressSummary: String,
    val quoteId: String,
    val paymentOrderId: String? = null,
    val itemAmountCent: Long,
    val deliveryFeeCent: Long,
    val discountCent: Long,
    val payableAmountCent: Long,
    val status: String,
    val paymentStatus: String,
    val refundStatus: String,
    val expiresAt: String,
    val version: Long,
    val createdAt: String,
    val updatedAt: String
)

@Singleton
class CommerceApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val auth: AuthRepository,
    private val sessionStorage: SessionStorage
) {
    private val baseUrl = BuildConfig.COMMERCE_BASE_URL.trimEnd('/')

    suspend fun addresses(): List<DeliveryAddressDto> = get("/api/v1/commerce/addresses")

    suspend fun createAddress(request: CreateDeliveryAddressRequest): DeliveryAddressDto =
        post("/api/v1/commerce/addresses", request)

    suspend fun createOrder(quoteId: String, idempotencyKey: String): FoodOrderDto =
        post("/api/v1/commerce/orders", CreateFoodOrderRequest(quoteId), idempotencyKey)

    suspend fun cancelOrder(orderId: String): FoodOrderDto =
        post("/api/v1/commerce/orders/$orderId/cancel", EmptyRequest)

    suspend fun foodBinding(): FoodBindingDto = get("/api/v1/commerce/food-binding")

    suspend fun foodEntryStatus(): FoodEntryStatusDto =
        get("/api/v1/commerce/food-entry-status")

    suspend fun bindFood(): FoodBindingDto =
        post("/api/v1/commerce/food-bindings", EmptyRequest)

    suspend fun unbindFood() {
        executeUnit(Request.Builder().url(url("/api/v1/commerce/food-binding")).delete().build())
    }

    suspend fun issueFoodHandoff(): FoodHandoffDto = post(
        "/api/v1/commerce/food-handoffs",
        EmptyRequest,
        headers = mapOf("X-Device-Id" to sessionStorage.deviceId())
    )

    suspend fun createFoodLocationContext(location: LocationSnapshot): FoodLocationContextDto {
        return post(
            "/api/v1/commerce/food-location-contexts",
            FoodLocationContextRequest(
                longitude = location.longitude,
                latitude = location.latitude,
                accuracyMeters = location.accuracyMeters,
                capturedAt = java.time.Instant.ofEpochMilli(location.capturedAtEpochMillis).toString()
            )
        )
    }

    suspend fun foodOrders(): List<FoodOrderSummaryDto> =
        get("/api/v1/commerce/food-orders?page=0&size=100")

    suspend fun foodOrder(orderRefId: String): FoodOrderDetailDto =
        get("/api/v1/commerce/food-orders/$orderRefId")

    suspend fun createFoodOrder(quoteId: String, idempotencyKey: String): FoodOrderDetailDto =
        post(
            "/api/v1/commerce/food-checkouts/$quoteId/orders",
            FoodOrderRemarkRequest(null),
            idempotencyKey
        )

    suspend fun cancelFoodOrder(
        orderRefId: String,
        idempotencyKey: String,
        reason: String = "用户取消订单"
    ): FoodOrderDetailDto = post(
        "/api/v1/commerce/food-orders/$orderRefId/cancellation-requests",
        FoodOrderCancellationRequest(reason),
        idempotencyKey
    )

    suspend fun prepareExternalFoodPayment(
        externalOrderNo: String,
        idempotencyKey: String
    ): FoodOrderDetailDto = post(
        "/api/v1/commerce/food-orders/${java.net.URLEncoder.encode(externalOrderNo, Charsets.UTF_8)}/payment-orders",
        EmptyRequest,
        idempotencyKey
    )

    private suspend inline fun <reified T> get(path: String): T = execute(
        Request.Builder().url(url(path)).get().build()
    )

    private suspend inline fun <reified T, reified B> post(
        path: String,
        body: B,
        idempotencyKey: String? = null,
        headers: Map<String, String> = emptyMap()
    ): T {
        val builder = Request.Builder().url(url(path)).post(
            json.encodeToString(body).toRequestBody(JSON_MEDIA)
        )
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey)
        headers.forEach(builder::header)
        return execute(builder.build())
    }

    private suspend inline fun <reified T> execute(request: Request): T = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken()
            ?: throw AiAgentApiException("NOT_AUTHENTICATED", "请重新登录")
        val authenticated = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .header("X-Request-Id", UUID.randomUUID().toString())
            .build()
        val response = try {
            client.newCall(authenticated).execute()
        } catch (cause: IOException) {
            throw AiAgentApiException("NETWORK_UNAVAILABLE", "外卖服务暂时不可用", cause = cause)
        }
        response.use {
            if (!it.isSuccessful) {
                val raw = it.body?.string().orEmpty()
                val problem = runCatching { json.decodeFromString<ProblemDetails>(raw) }.getOrNull()
                throw AiAgentApiException(
                    problem?.code ?: "COMMERCE_REQUEST_FAILED",
                    "外卖操作失败，请刷新后重试",
                    it.code,
                    problem?.requestId
                )
            }
            json.decodeFromString<T>(it.body?.string().orEmpty())
        }
    }

    private suspend fun executeUnit(request: Request) = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken()
            ?: throw AiAgentApiException("NOT_AUTHENTICATED", "请重新登录")
        val authenticated = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .header("X-Request-Id", UUID.randomUUID().toString())
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .build()
        val response = try { client.newCall(authenticated).execute() }
        catch (cause: IOException) {
            throw AiAgentApiException("NETWORK_UNAVAILABLE", "外卖服务暂时不可用", cause = cause)
        }
        response.use {
            if (!it.isSuccessful) throw AiAgentApiException(
                "COMMERCE_REQUEST_FAILED", "解除授权同步失败", it.code)
        }
    }

    private fun url(path: String): String {
        if (baseUrl.isBlank()) throw AiAgentApiException("COMMERCE_NOT_CONFIGURED", "外卖服务尚未配置")
        return baseUrl + path
    }

    @Serializable private data object EmptyRequest
    @Serializable private data class FoodOrderRemarkRequest(val remark: String?)
    @Serializable private data class FoodOrderCancellationRequest(val reason: String)

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
