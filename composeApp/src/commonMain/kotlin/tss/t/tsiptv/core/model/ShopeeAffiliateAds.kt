package tss.t.tsiptv.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShopeeAffiliateAdsResponse(
    @SerialName("total") val total: Int? = null,
    @SerialName("data") val data: List<ShopeeAffiliateAds>? = null,
)

@Serializable
data class ShopeeAffiliateAds(
    @SerialName("_id") val id: String? = null,
    @SerialName("productId") val productId: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("price") val price: Long? = null,
    @SerialName("productLink") val productLink: String? = null,
    @SerialName("qrCodeLink") val qrCodeLink: String? = null,
    @SerialName("ctaAction") val ctaAction: String? = null,
    @SerialName("ctaUrl") val ctaUrl: String? = null,
    @SerialName("sale") val sale: Int? = null,
    @SerialName("salePrice") val salePrice: Long? = null,
    @SerialName("imageUrl") val imageUrl: String? = null,
    @SerialName("videoUrl") val videoUrl: String? = null,
    @SerialName("adsType") val adsType: String? = null,
    @SerialName("productImages") val productImages: List<String>? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null,
    @SerialName("bannerImage") val bannerImage: String? = null,
    @SerialName("categoryId") val categoryId: String? = null,
    @SerialName("categoryName") val categoryName: String? = null,
    @SerialName("subcategoryId") val subcategoryId: String? = null,
    @SerialName("subcategoryName") val subcategoryName: String? = null,
)
