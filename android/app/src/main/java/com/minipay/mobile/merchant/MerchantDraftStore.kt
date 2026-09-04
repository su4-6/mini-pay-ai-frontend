package com.minipay.mobile.merchant

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MerchantOnboardingDraft(
    val shopName: String = "",
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val imageKeys: List<String> = emptyList()
) {
    fun withLocation(latitude: Double, longitude: Double): MerchantOnboardingDraft = copy(
        latitude = latitude,
        longitude = longitude,
        address = null
    )

    fun withSelectedLocation(
        latitude: Double,
        longitude: Double,
        address: String?
    ): MerchantOnboardingDraft = copy(
        latitude = latitude,
        longitude = longitude,
        address = address
    )

    fun withResolvedAddress(
        latitude: Double,
        longitude: Double,
        address: String?
    ): MerchantOnboardingDraft = if (this.latitude == latitude && this.longitude == longitude) {
        copy(address = address)
    } else {
        this
    }

    fun withImage(key: String): MerchantOnboardingDraft = copy(
        imageKeys = (imageKeys + key).distinct().take(MAX_SHOP_IMAGES)
    )

    fun withoutImage(key: String): MerchantOnboardingDraft = copy(
        imageKeys = imageKeys.filterNot { it == key }
    )

    companion object {
        const val MAX_SHOP_IMAGES = 5

        fun from(application: MerchantApplication?): MerchantOnboardingDraft =
            MerchantOnboardingDraft(
                shopName = application?.shopName.orEmpty(),
                address = application?.address,
                latitude = application?.latitude,
                longitude = application?.longitude,
                imageKeys = application?.shopImages
                    ?.split(',')
                    ?.map(String::trim)
                    ?.filter(String::isNotBlank)
                    ?.distinct()
                    ?.take(MAX_SHOP_IMAGES)
                    .orEmpty()
            )
    }
}

@Singleton
class MerchantDraftStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(userId: String): MerchantOnboardingDraft? {
        val key = key(userId)
        val encoded = preferences.getString(key, null) ?: return null
        return runCatching { json.decodeFromString<MerchantOnboardingDraft>(encoded) }
            .getOrElse {
                preferences.edit().remove(key).apply()
                null
            }
    }

    fun save(userId: String, draft: MerchantOnboardingDraft) {
        preferences.edit().putString(key(userId), json.encodeToString(draft)).apply()
    }

    fun clear(userId: String) {
        preferences.edit().remove(key(userId)).apply()
    }

    private fun key(userId: String): String = "merchant_onboarding_draft_$userId"

    private companion object {
        const val PREFERENCES_NAME = "merchant_onboarding_drafts"
    }
}
