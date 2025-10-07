package tss.t.tsiptv.core.repository

import tss.t.tsiptv.core.model.ShopeeAffiliateAds

interface IAdsRepository {
    suspend fun getAdsList(): List<ShopeeAffiliateAds>
    suspend fun getAdsVideoList(): List<ShopeeAffiliateAds>
    suspend fun getOpenAdsList(): List<ShopeeAffiliateAds>
}
