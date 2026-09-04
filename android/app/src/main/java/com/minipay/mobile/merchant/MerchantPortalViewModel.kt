package com.minipay.mobile.merchant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minipay.mobile.auth.IdentityApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal const val CONSUMER_MERCHANT_TYPE = "INDIVIDUAL"

internal fun consumerMerchantSubmission(
    version: Long?,
    shopName: String,
    address: String?,
    latitude: Double,
    longitude: Double,
    imageKeys: List<String>,
    contactName: String,
    contactMobile: String
) = MerchantSubmission(
    version = version,
    merchantType = CONSUMER_MERCHANT_TYPE,
    shopName = shopName.trim(),
    address = address,
    latitude = latitude,
    longitude = longitude,
    shopImages = imageKeys.joinToString(","),
    contactName = contactName,
    contactMobile = contactMobile
)

data class MerchantPortalState(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val userId: String? = null,
    val application: MerchantApplication? = null,
    val draft: MerchantOnboardingDraft = MerchantOnboardingDraft(),
    val legalNameMasked: String? = null,
    val mobile: String? = null,
    val initialization: MerchantInitialization? = null,
    val initializationLoading: Boolean = false,
    val initializationError: String? = null,
    val uploading: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val paymentOrder: MerchantPaymentOrder? = null,
    val paying: Boolean = false,
    val resolution: MerchantResolution? = null,
    val imageUrls: Map<String, String> = emptyMap()
)

@HiltViewModel
class MerchantPortalViewModel @Inject constructor(
    private val repository: MerchantPortalRepository,
    private val draftStore: MerchantDraftStore
) : ViewModel() {
    private val mutableState = MutableStateFlow(MerchantPortalState())
    val state = mutableState.asStateFlow()
    private var polling: Job? = null
    private var loadingJob: Job? = null
    private var initializationJob: Job? = null
    private var initializingMerchantId: String? = null

    fun load() {
        val expectedUserId = repository.currentUserId()
            ?: return fail(IdentityApiException("TOKEN_INVALID"))
        val beforeLoad = mutableState.value
        val isInitialLoad = !beforeLoad.loaded || beforeLoad.userId != expectedUserId
        val userChanged = beforeLoad.userId != null && beforeLoad.userId != expectedUserId
        if (userChanged) {
            initializationJob?.cancel()
            initializationJob = null
            initializingMerchantId = null
        }
        mutableState.value = beforeLoad.copy(
            loading = isInitialLoad,
            initialization = beforeLoad.initialization.takeUnless { userChanged },
            initializationLoading = beforeLoad.initializationLoading && !userChanged,
            initializationError = beforeLoad.initializationError.takeUnless { userChanged },
            error = null
        )
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            runCatching {
                val profile = repository.profile()
                val application = repository.currentApplication()
                Triple(profile.legalNameMasked, repository.currentMobile(), application)
            }.onSuccess { (name, mobile, application) ->
                if (repository.currentUserId() != expectedUserId) return@onSuccess
                val current = mutableState.value
                val draft = resolveDraft(expectedUserId, current, application)
                val applicationMerchantId = application?.resultantMerchantId
                val keepInitialization = application?.applyStatus == "APPROVED" &&
                    applicationMerchantId != null &&
                    current.initialization?.merchant?.merchantId == applicationMerchantId
                mutableState.value = mutableState.value.copy(
                    loading = false,
                    loaded = true,
                    userId = expectedUserId,
                    legalNameMasked = name,
                    mobile = mobile,
                    application = application,
                    draft = draft,
                    initialization = current.initialization.takeIf { keepInitialization },
                    initializationLoading = current.initializationLoading &&
                        initializingMerchantId == applicationMerchantId,
                    initializationError = current.initializationError.takeIf {
                        application?.applyStatus == "APPROVED" &&
                            initializingMerchantId == null
                    }
                )
                refreshImageUrls(expectedUserId, applicationImageKeys(application) + draft.imageKeys)
                if (application?.applyStatus == "APPROVED") initialize(application)
                configurePolling(application?.applyStatus == "PENDING")
            }.onFailure { error ->
                if (error is CancellationException) throw error
                fail(error)
            }
        }
    }

    fun updateShopName(value: String) = updateDraft { copy(shopName = value.take(64)) }

    fun updateLocation(latitude: Double, longitude: Double) =
        updateDraft { withLocation(latitude, longitude) }

    fun updateSelectedLocation(latitude: Double, longitude: Double, address: String?) =
        updateDraft { withSelectedLocation(latitude, longitude, address) }

    fun removeImage(key: String) {
        updateDraft { withoutImage(key) }
        mutableState.value = mutableState.value.copy(imageUrls = mutableState.value.imageUrls - key)
    }

    fun submit() {
        val current = mutableState.value
        val contactName = current.legalNameMasked ?: return fail(IllegalStateException("请先完成实名认证"))
        val mobile = current.mobile ?: return fail(IllegalStateException("登录手机号缺失，请重新登录"))
        val userId = current.userId ?: return fail(IdentityApiException("TOKEN_INVALID"))
        val draft = current.draft
        val latitude = draft.latitude ?: return fail(IllegalStateException("请选择经营位置"))
        val longitude = draft.longitude ?: return fail(IllegalStateException("请选择经营位置"))
        if (draft.shopName.trim().length < 2) return fail(IllegalStateException("经营名称至少需要 2 个字"))
        if (draft.imageKeys.isEmpty()) return fail(IllegalStateException("请至少上传 1 张店铺照片"))
        viewModelScope.launch {
            mutableState.value = current.copy(submitting = true, error = null)
            val body = consumerMerchantSubmission(
                version = current.application?.version,
                shopName = draft.shopName,
                address = draft.address,
                latitude = latitude,
                longitude = longitude,
                imageKeys = draft.imageKeys,
                contactName = contactName,
                contactMobile = mobile
            )
            runCatching {
                current.application?.let { repository.resubmit(it.id, body) } ?: repository.submit(body)
            }.onSuccess {
                draftStore.clear(userId)
                mutableState.value = mutableState.value.copy(
                    application = it,
                    draft = MerchantOnboardingDraft(),
                    submitting = false
                )
                configurePolling(true)
            }.onFailure(::fail)
        }
    }

    fun upload(bytes: ByteArray, contentType: String, done: (String?) -> Unit) {
        val expectedUserId = mutableState.value.userId ?: repository.currentUserId()
        if (expectedUserId == null) {
            fail(IdentityApiException("TOKEN_INVALID"))
            done(null)
            return
        }
        if (mutableState.value.draft.imageKeys.size >= MerchantOnboardingDraft.MAX_SHOP_IMAGES) {
            fail(IllegalStateException("最多只能上传 5 张店铺照片"))
            done(null)
            return
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(uploading = true, error = null)
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val key = runCatching { repository.upload(bytes, contentType, digest) }
                .getOrElse {
                    fail(it)
                    done(null)
                    return@launch
                }
            if (repository.currentUserId() != expectedUserId || mutableState.value.userId != expectedUserId) {
                mutableState.value = mutableState.value.copy(uploading = false)
                done(null)
                return@launch
            }
            val draft = mutableState.value.draft.withImage(key)
            draftStore.save(expectedUserId, draft)
            mutableState.value = mutableState.value.copy(draft = draft)
            val preview = runCatching { repository.imageUrls(listOf(key)) }
            mutableState.value = mutableState.value.copy(
                uploading = false,
                imageUrls = mutableState.value.imageUrls + preview.getOrDefault(emptyMap()),
                error = preview.exceptionOrNull()?.let {
                    "照片已上传，但预览加载失败，请检查网络后重试"
                }
            )
            done(key)
        }
    }

    fun resolveAndPay(deepLink: String, amountCent: Long, password: String, done: () -> Unit = {}) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(paying = true, error = null, paymentOrder = null)
            runCatching { repository.pay(repository.resolve(deepLink), amountCent, password) }
                .onSuccess { mutableState.value = mutableState.value.copy(paying = false, paymentOrder = it); done() }
                .onFailure(::fail)
        }
    }

    fun resolve(deepLink: String, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(paying = true, error = null)
            runCatching { repository.resolve(deepLink) }
                .onSuccess {
                    mutableState.value = mutableState.value.copy(paying = false, resolution = it)
                    done(true)
                }
                .onFailure { fail(it); done(false) }
        }
    }

    fun clearPayment() { mutableState.value = mutableState.value.copy(paymentOrder = null, error = null) }

    fun retryInitialization() {
        val application = mutableState.value.application
        if (application?.applyStatus == "APPROVED") initialize(application, force = true)
    }

    private fun initialize(application: MerchantApplication, force: Boolean = false) {
        val merchantId = application.resultantMerchantId
        if (merchantId.isNullOrBlank()) {
            mutableState.value = mutableState.value.copy(
                initializationLoading = false,
                initializationError = merchantPortalErrorMessage(
                    IdentityApiException("MERCHANT_INITIALIZATION_UNAVAILABLE")
                )
            )
            return
        }
        val currentInitialization = mutableState.value.initialization
        if (!force && currentInitialization?.merchant?.merchantId == merchantId &&
            merchantQrContent(currentInitialization) != null) return
        if (initializingMerchantId == merchantId && initializationJob?.isActive == true) return
        val expectedUserId = mutableState.value.userId ?: repository.currentUserId() ?: return
        initializingMerchantId = merchantId
        initializationJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                initializationLoading = true,
                initializationError = null,
                error = null
            )
            try {
                val initialized = repository.initialize(merchantId)
                if (merchantQrContent(initialized) == null) {
                    throw IdentityApiException("MERCHANT_COLLECTION_CODE_EMPTY")
                }
                val current = mutableState.value
                if (repository.currentUserId() == expectedUserId &&
                    current.userId == expectedUserId &&
                    current.application?.resultantMerchantId == merchantId) {
                    mutableState.value = current.copy(
                        initialization = initialized,
                        initializationLoading = false,
                        initializationError = null
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val current = mutableState.value
                if (repository.currentUserId() == expectedUserId && current.userId == expectedUserId) {
                    mutableState.value = current.copy(
                        initializationLoading = false,
                        initializationError = merchantPortalErrorMessage(error)
                    )
                }
            } finally {
                if (initializingMerchantId == merchantId) initializingMerchantId = null
            }
        }
    }

    private fun configurePolling(enabled: Boolean) {
        polling?.cancel()
        if (!enabled) return
        polling = viewModelScope.launch {
            while (isActive) { delay(10_000); refreshApplication() }
        }
    }

    private suspend fun refreshApplication() {
        runCatching { repository.currentApplication() }.onSuccess { application ->
            val userId = mutableState.value.userId ?: return@onSuccess
            if (repository.currentUserId() != userId) return@onSuccess
            val current = mutableState.value
            val draft = resolveDraft(userId, current, application)
            mutableState.value = current.copy(
                application = application,
                draft = draft,
                loading = false,
                loaded = true
            )
            refreshImageUrls(userId, applicationImageKeys(application) + draft.imageKeys)
            if (application?.applyStatus == "APPROVED") {
                configurePolling(false)
                initialize(application)
            } else if (application?.applyStatus != "PENDING") {
                configurePolling(false)
            }
        }
    }

    private fun refreshImageUrls(userId: String, imageKeys: List<String>) {
        val keys = imageKeys.map(String::trim).filter(String::isNotBlank).distinct()
        if (keys.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.imageUrls(keys) }.onSuccess {
                if (mutableState.value.userId == userId && repository.currentUserId() == userId) {
                    mutableState.value = mutableState.value.copy(imageUrls = mutableState.value.imageUrls + it)
                }
            }
        }
    }

    private fun resolveDraft(
        userId: String,
        current: MerchantPortalState,
        application: MerchantApplication?
    ): MerchantOnboardingDraft {
        if (!isEditable(application)) {
            draftStore.clear(userId)
            return MerchantOnboardingDraft()
        }
        val wasEditing = current.loaded &&
            current.userId == userId &&
            isEditable(current.application)
        return if (wasEditing) {
            current.draft
        } else {
            draftStore.load(userId) ?: MerchantOnboardingDraft.from(application)
        }
    }

    private fun updateDraft(transform: MerchantOnboardingDraft.() -> MerchantOnboardingDraft) {
        val current = mutableState.value
        val userId = current.userId ?: repository.currentUserId() ?: return
        if (repository.currentUserId() != userId) return
        val draft = current.draft.transform()
        draftStore.save(userId, draft)
        mutableState.value = current.copy(draft = draft, error = null)
    }

    private fun isEditable(application: MerchantApplication?): Boolean =
        application == null || application.applyStatus in setOf("DRAFT", "REJECTED", "SUPPLEMENT")

    private fun applicationImageKeys(application: MerchantApplication?): List<String> =
        application?.shopImages
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()

    private fun fail(error: Throwable) {
        mutableState.value = mutableState.value.copy(
            loading = false,
            uploading = false,
            submitting = false,
            paying = false,
            error = merchantPortalErrorMessage(error)
        )
    }

    override fun onCleared() {
        loadingJob?.cancel()
        initializationJob?.cancel()
        polling?.cancel()
    }
}

internal fun merchantQrContent(initialization: MerchantInitialization?): String? {
    initialization ?: return null
    if (initialization.collectionCode?.status?.uppercase() !in setOf(null, "ENABLED")) return null
    return initialization.qrContent?.trim()?.takeIf(String::isNotEmpty)
        ?: initialization.collectionCode?.qrContent?.trim()?.takeIf(String::isNotEmpty)
}

internal fun merchantPortalErrorMessage(error: Throwable): String =
    when ((error as? IdentityApiException)?.code) {
        "INSUFFICIENT_BALANCE" -> "余额不足"
        "PAYMENT_PASSWORD_INVALID", "PAY_PASSWORD_INVALID" -> "支付密码错误"
        "COLLECTION_CODE_EXPIRED", "SCAN_RESOLUTION_EXPIRED", "SCAN_RESOLUTION_UNAVAILABLE",
        "SCAN_RESOLUTION_NOT_FOUND" -> "二维码已失效"
        "MERCHANT_NOT_ACTIVE", "MERCHANT_COLLECTION_UNAVAILABLE" -> "商户当前不可收款"
        "SELF_PAYMENT_NOT_ALLOWED", "SELF_MERCHANT_PAYMENT" -> "不能向自己的商户付款"
        "MERCHANT_ONBOARDING_ALREADY_EXISTS" -> "该账号已有入驻申请"
        "MERCHANT_INITIALIZATION_UNAVAILABLE" -> "收款码生成服务暂不可用，请检查服务配置后重试"
        "MERCHANT_COLLECTION_CODE_EMPTY" -> "服务未返回有效收款码，请重新生成"
        "OBJECT_STORAGE_UNAVAILABLE" -> "图片存储服务未启用，请重启支付服务后重试"
        "IMAGE_UPLOAD_INVALID" -> "图片格式、大小或校验信息不符合要求"
        "SHOP_IMAGE_UPLOAD_FAILED" -> "上传到图片服务器失败，请检查网络后重试"
        "NETWORK_UNAVAILABLE" -> "无法连接支付服务，请检查真机网络和端口映射"
        else -> error.message ?: (error as? IdentityApiException)?.code ?: "请求失败，请重试"
    }
