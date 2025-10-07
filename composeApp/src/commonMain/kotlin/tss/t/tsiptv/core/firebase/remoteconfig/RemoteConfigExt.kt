package tss.t.tsiptv.core.firebase.remoteconfig

import kotlinx.serialization.json.Json
import tss.t.tsiptv.core.firebase.IRemoteConfig
import tss.t.tsiptv.core.model.ShopeeAffiliateAds
import tss.t.tsiptv.core.model.ShopeeAffiliateAdsResponse

private const val SHOPEE_AFFILIATE_ADS = "shopee_affiliate_ads"

suspend inline fun <reified T> IRemoteConfig.decodeJson(key: String): T? {
    return runCatching {
        getString(key)?.let {
            Json.decodeFromString<T>(key)
        }
    }.getOrNull()
}

suspend fun IRemoteConfig.getShopeeAffiliateAds(): List<ShopeeAffiliateAds>? {
    return decodeJson<ShopeeAffiliateAdsResponse>(SHOPEE_AFFILIATE_ADS)
        ?.data
}
