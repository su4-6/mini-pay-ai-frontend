package com.minipay.mobile.finance

import com.minipay.mobile.BuildConfig
import com.minipay.mobile.auth.AuthRepository
import com.minipay.mobile.auth.IdentityApiException
import com.minipay.mobile.auth.ProblemDetails
import com.minipay.mobile.auth.SessionStorage
import com.minipay.mobile.profile.UserProfile
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Singleton
class FinanceRepository @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val auth: AuthRepository,
    private val session: SessionStorage
) {
    private val collectionReceiptEventSourceFactory = EventSources.createFactory(
        collectionReceiptSseClient(client)
    )

    suspend fun capabilities(): ConsumerCapabilities = get(identity("/api/v1/users/me/capabilities"))

    /** Issues a fresh JWT after Identity has changed security claims. */
    suspend fun refreshClaimsAfterRealName() {
        auth.refreshClaims()
    }
    suspend fun wallet(): WalletSummary = get(wallet("/api/v1/wallets/me"))
    suspend fun bills(
        direction: String? = null,
        source: String? = null,
        businessType: String? = null,
        from: String? = null,
        to: String? = null,
        page: Int = 1,
        size: Int = 20
    ): BillPage {
        val query = buildList {
            add("page=$page")
            add("size=$size")
            direction?.let { add("direction=${encode(it)}") }
            source?.let { add("source=${encode(it)}") }
            businessType?.let { add("businessType=${encode(it)}") }
            from?.let { add("from=${encode(it)}") }
            to?.let { add("to=${encode(it)}") }
        }.joinToString("&")
        return get(wallet("/api/v1/wallets/me/bills?$query"))
    }
    suspend fun bill(billId: String): WalletBillDetail =
        get(wallet("/api/v1/wallets/me/bills/$billId"))
    suspend fun collectionRecords(
        type: CollectionRecordType,
        period: CollectionRecordPeriod,
        page: Int = 1,
        size: Int = 20
    ): CollectionRecordPage = get(wallet(
        "/api/v1/wallets/me/collection-records?type=${type.name}&period=${period.name}&page=$page&size=$size"
    ))
    suspend fun recentTransferCounterparties(page: Int = 1, size: Int = 50): RecentTransferCounterpartyPage =
        get(wallet("/api/v1/wallets/me/recent-transfer-counterparties?page=$page&size=$size"))
    suspend fun transferRecords(
        counterpartyUserId: String,
        direction: String? = null,
        month: String? = null,
        status: String? = null,
        page: Int = 1,
        size: Int = 20
    ): TransferRecordPage {
        val query = buildList {
            add("counterpartyUserId=${encode(counterpartyUserId)}")
            add("page=$page")
            add("size=$size")
            direction?.let { add("direction=${encode(it)}") }
            month?.let { add("month=${encode(it)}") }
            status?.let { add("status=${encode(it)}") }
        }.joinToString("&")
        return get(wallet("/api/v1/wallets/me/transfer-records?$query"))
    }
    suspend fun updateBillManagement(
        billId: String,
        request: UpdateBillManagementRequest
    ): WalletBillDetail = putForResponse(
        wallet("/api/v1/wallets/me/bills/$billId/management"), request
    )
    suspend fun billTags(page: Int = 1, size: Int = 50): BillTagPage =
        get(wallet("/api/v1/wallets/me/bill-tags?page=$page&size=$size"))
    suspend fun createBillTag(name: String): BillTag = post(
        wallet("/api/v1/wallets/me/bill-tags"), CreateBillTagRequest(name),
        UUID.randomUUID().toString()
    )
    suspend fun rechargeOrders(page: Int = 1, size: Int = 20): FundingOrderPage =
        get(payment("/api/v1/recharge-orders?page=$page&size=$size"))
    suspend fun rechargeOrder(rechargeId: String): RechargeOrder =
        get(payment("/api/v1/recharge-orders/$rechargeId"))
    suspend fun withdrawalOrders(page: Int = 1, size: Int = 20): FundingOrderPage =
        get(payment("/api/v1/withdrawal-orders?page=$page&size=$size"))
    suspend fun withdrawalOrder(withdrawalId: String): WithdrawalOrder =
        get(payment("/api/v1/withdrawal-orders/$withdrawalId"))
    suspend fun cards(): List<BankCard> = get(payment("/api/v1/bank-cards"))
    suspend fun card(cardId: String): BankCard = get(payment("/api/v1/bank-cards/$cardId"))
    suspend fun paymentLimits(cardId: String): BankPaymentLimits =
        get(payment("/api/v1/bank-cards/$cardId/payment-limits"))
    suspend fun bankTransactions(
        cardId: String,
        from: String,
        to: String,
        page: Int,
        size: Int = 20
    ): BankTransactionPage {
        val query = "from=${encode(from)}&to=${encode(to)}&page=$page&size=$size"
        return get(payment("/api/v1/bank-cards/$cardId/transactions?$query"))
    }
    suspend fun collectionCode(): CollectionCode = get(payment("/api/v1/personal-collection-codes/current"))
    suspend fun profile(): UserProfile = get(identity("/api/v1/users/me"))

    suspend fun resolveTransferRecipient(mobile: String): TransferRecipientResponse = post(
        identity("/api/v1/transfer-recipients/resolve"), ResolveTransferRecipientRequest(mobile)
    )

    suspend fun setPaymentPassword(password: String) {
        put(identity("/api/v1/users/me/payment-password"), SetPaymentPasswordRequest(password))
    }

    suspend fun refreshClaimsAfterPaymentPassword() {
        auth.refreshClaims()
    }

    suspend fun bindCard(holderName: String, cardNumber: String, code: String): BankCard = post(
        payment("/api/v1/bank-cards"), BindBankCardRequest(holderName, cardNumber, code)
    )

    suspend fun disableCard(cardId: String) {
        delete(payment("/api/v1/bank-cards/$cardId"))
    }

    suspend fun bankBalance(cardId: String, password: String): BankBalance {
        val authorization: PaymentAuthorization = post(
            identity("/api/v1/payment-authorizations"),
            IssueAuthorizationRequest(
                "BANK_CARD_BALANCE_QUERY", cardId, 0L, session.deviceId(), password
            ),
            true
        )
        return post(
            payment("/api/v1/bank-cards/$cardId/balance-queries"),
            BalanceQueryRequest(authorization.paymentAuthToken)
        )
    }

    suspend fun recharge(cardId: String, amountCent: Long, password: String): RechargeOrder {
        val intent: RechargeOrder = post(
            payment("/api/v1/recharge-intents"), FundingRequest(cardId, amountCent), true
        )
        if (intent.status != "PENDING_CONFIRMATION") return intent
        val authorization: PaymentAuthorization = post(
            identity("/api/v1/payment-authorizations"),
            IssueAuthorizationRequest("RECHARGE_ORDER", intent.rechargeId, amountCent, session.deviceId(), password),
            true
        )
        return post(
            payment("/api/v1/recharge-intents/${intent.rechargeId}/confirm"),
            ConfirmRequest(authorization.paymentAuthToken)
        )
    }

    suspend fun withdraw(cardId: String, amountCent: Long, password: String): WithdrawalOrder {
        val order: WithdrawalOrder = post(
            payment("/api/v1/withdrawal-orders"), FundingRequest(cardId, amountCent), true
        )
        val authorization: PaymentAuthorization = post(
            identity("/api/v1/payment-authorizations"),
            IssueAuthorizationRequest("WITHDRAWAL_ORDER", order.withdrawalId, amountCent, session.deviceId(), password),
            true
        )
        return post(
            payment("/api/v1/withdrawal-orders/${order.withdrawalId}/confirm"),
            ConfirmRequest(authorization.paymentAuthToken)
        )
    }

    suspend fun submitRealName(name: String, idNumber: String, faceJpeg: ByteArray): RealNameResult =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("legalName", name)
                .addFormDataPart("idNumber", idNumber)
                .addFormDataPart("faceImage", "live.jpg", faceJpeg.toRequestBody("image/jpeg".toMediaType()))
                .build()
            execute<RealNameResult>(Request.Builder().url(identity("/api/v1/real-name-verifications"))
                .header("Authorization", bearer()).header("Idempotency-Key", UUID.randomUUID().toString())
                .post(body).build())
        }

    suspend fun resolveCode(deepLink: String): ScanResolution = post(
        payment("/api/v1/scan-resolutions"), ScanRequest(deepLink)
    )

    suspend fun payMerchantCollection(
        resolution: ScanResolution,
        amountCent: Long,
        password: String
    ): MerchantPaymentOrder {
        val resolutionId = requireNotNull(resolution.resolutionId) { "Merchant resolution is missing resolutionId" }
        val order: MerchantPaymentOrder = post(
            payment("/api/v1/payment-orders"),
            CreatePaymentOrderRequest(
                amountCent = amountCent,
                subject = "扫码付款-${resolution.merchantName.orEmpty().ifBlank { "商户" }}",
                paymentMethod = "WALLET_BALANCE",
                resolutionId = resolutionId
            ),
            UUID.randomUUID().toString()
        )
        val authorization: PaymentAuthorization = post(
            identity("/api/v1/payment-authorizations"),
            IssueAuthorizationRequest(
                "PAYMENT_ORDER", order.paymentOrderId, amountCent, session.deviceId(), password
            ),
            UUID.randomUUID().toString()
        )
        return post(
            payment("/api/v1/payment-orders/${order.paymentOrderId}/confirm"),
            ConfirmRequest(authorization.paymentAuthToken),
            UUID.randomUUID().toString()
        )
    }

    fun collectionReceiptEvents(): Flow<CollectionReceiptEvent> = callbackFlow {
        val token = runCatching { bearer() }.getOrElse {
            close(it)
            return@callbackFlow
        }
        val request = Request.Builder()
            .url(wallet("/api/v1/wallets/me/collection-receipts/stream"))
            .header("Authorization", token)
            .build()
        val source = collectionReceiptEventSourceFactory.newEventSource(request,
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    runCatching { json.decodeFromString<CollectionReceiptEvent>(data) }
                        .onSuccess { trySend(it) }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                    if (t != null) close(t) else close()
                }
            })
        awaitClose { source.cancel() }
    }

    suspend fun transfer(
        receiverUserId: String, amountCent: Long, remark: String?, password: String, source: String = "FORM"
    ): TransferOrder {
        val intent = createTransfer(receiverUserId, amountCent, remark, source, UUID.randomUUID().toString())
        val authorization: PaymentAuthorization = post(
            identity("/api/v1/payment-authorizations"),
            IssueAuthorizationRequest("TRANSFER_INTENT", intent.intentId, amountCent, session.deviceId(), password),
            true
        )
        return confirmTransfer(intent.intentId, authorization.paymentAuthToken, UUID.randomUUID().toString())
    }

    suspend fun createTransfer(
        receiverUserId: String,
        amountCent: Long,
        remark: String?,
        source: String,
        idempotencyKey: String
    ): TransferIntent = post(
        payment("/api/v1/transfers"),
        CreateTransferRequest(receiverUserId, amountCent, remark, source),
        idempotencyKey
    )

    suspend fun authorizeTransfer(
        intent: TransferIntent,
        password: String
    ): PaymentAuthorization = post(
        identity("/api/v1/payment-authorizations"),
        IssueAuthorizationRequest(
            "TRANSFER_INTENT", intent.intentId, intent.amountCent, session.deviceId(), password
        ),
        UUID.randomUUID().toString()
    )

    suspend fun confirmTransfer(
        intentId: String,
        paymentAuthToken: String,
        idempotencyKey: String
    ): TransferOrder = post(
        payment("/api/v1/transfers/$intentId/confirm"),
        ConfirmRequest(paymentAuthToken),
        idempotencyKey
    )

    suspend fun transferOrder(transferId: String): TransferOrder =
        get(payment("/api/v1/transfer-orders/$transferId"))

    suspend fun foodPaymentOrder(foodOrderId: String): PaymentOrder =
        get(payment("/api/v1/food-orders/$foodOrderId/payment-order"))

    suspend fun paymentOrder(paymentOrderId: String): PaymentOrder =
        get(payment("/api/v1/payment-orders/$paymentOrderId"))

    suspend fun authorizePaymentOrder(
        order: PaymentOrder,
        password: String
    ): PaymentAuthorization = post(
        identity("/api/v1/payment-authorizations"),
        IssueAuthorizationRequest(
            "PAYMENT_ORDER", order.paymentOrderId, order.amountCent, session.deviceId(), password
        ),
        UUID.randomUUID().toString()
    )

    suspend fun confirmPaymentOrder(
        order: PaymentOrder,
        paymentAuthToken: String
    ): PaymentOrder = post(
        payment("/api/v1/payment-orders/${order.paymentOrderId}/confirm"),
        ConfirmRequest(paymentAuthToken)
    )

    private suspend inline fun <reified T> get(url: String): T = withContext(Dispatchers.IO) {
        // Reads alone are safe to retry.  Funding and profile mutations deliberately use the
        // one-shot execute path below so reconnecting never replays a user action.
        var authorization = bearer()
        var last: Throwable? = null
        repeat(2) { attempt ->
            try {
                val request = Request.Builder().url(url)
                    .header("Authorization", authorization).get().build()
                return@withContext execute<T>(request)
            } catch (error: IdentityApiException) {
                last = error
                if (error.status == 401 && attempt == 0) {
                    authorization = auth.forceRefreshAccessToken()
                        ?.let { "Bearer $it" }
                        ?: throw IdentityApiException("TOKEN_INVALID", status = 401)
                    return@repeat
                }
                val retryable = error.code == "NETWORK_UNAVAILABLE" ||
                    (error.status != null && error.status >= 500)
                if (!retryable || attempt == 1) throw error
                delay(180L * (attempt + 1))
            }
        }
        throw checkNotNull(last)
    }

    private suspend inline fun <reified B, reified T> post(
        url: String, body: B, idempotent: Boolean = false
    ): T = post(url, body, if (idempotent) UUID.randomUUID().toString() else null)

    private suspend inline fun <reified B, reified T> post(
        url: String, body: B, idempotencyKey: String?
    ): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Authorization", bearer())
            .header("X-Request-Id", UUID.randomUUID().toString())
        idempotencyKey?.let { request.header("Idempotency-Key", it) }
        execute(request.post(json.encodeToString(body).toRequestBody(JSON)).build())
    }

    private suspend inline fun <reified B> put(url: String, body: B) = withContext(Dispatchers.IO) {
        execute<Unit>(Request.Builder().url(url).header("Authorization", bearer())
            .put(json.encodeToString(body).toRequestBody(JSON)).build())
    }

    private suspend inline fun <reified B, reified T> putForResponse(url: String, body: B): T =
        withContext(Dispatchers.IO) {
            execute(Request.Builder().url(url).header("Authorization", bearer())
                .header("X-Request-Id", UUID.randomUUID().toString())
                .put(json.encodeToString(body).toRequestBody(JSON)).build())
        }

    private suspend fun delete(url: String) = withContext(Dispatchers.IO) {
        execute<Unit>(Request.Builder().url(url).header("Authorization", bearer())
            .header("X-Request-Id", UUID.randomUUID().toString()).delete().build())
    }

    private suspend fun bearer(): String = auth.validAccessToken()
        ?.let { "Bearer $it" } ?: throw IdentityApiException("TOKEN_INVALID")

    private inline fun <reified T> execute(request: Request): T {
        val response = try { client.newCall(request).execute() }
        catch (e: IOException) { throw IdentityApiException("NETWORK_UNAVAILABLE", cause = e) }
        response.use {
            val raw = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val problem = runCatching { json.decodeFromString<ProblemDetails>(raw) }.getOrNull()
                throw IdentityApiException(problem?.code ?: "REQUEST_FAILED", problem?.requestId, it.code)
            }
            if (T::class == Unit::class) return Unit as T
            return json.decodeFromString(raw)
        }
    }

    private fun identity(path: String) = configured(BuildConfig.IDENTITY_BASE_URL, path)
    private fun payment(path: String) = configured(BuildConfig.PAYMENT_BASE_URL, path)
    private fun wallet(path: String) = configured(BuildConfig.WALLET_BASE_URL, path)
    private fun configured(base: String, path: String): String {
        if (base.isBlank()) throw IdentityApiException("SERVICE_NOT_CONFIGURED")
        return base.trimEnd('/') + path
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private companion object { val JSON = "application/json; charset=utf-8".toMediaType() }
}

/**
 * SSE responses are intentionally idle between events. Reusing the regular API client's
 * finite read timeout disconnects the stream every 15 seconds and creates gaps where an
 * ephemeral collection receipt can be missed.
 */
internal fun collectionReceiptSseClient(client: OkHttpClient): OkHttpClient =
    client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
