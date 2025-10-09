package tss.t.tsiptv.core.repository.ads

import kotlinx.serialization.json.Json
import tss.t.tsiptv.core.firebase.IRemoteConfig
import tss.t.tsiptv.core.model.ShopeeAffiliateAds
import tss.t.tsiptv.core.model.ShopeeAffiliateAdsResponse
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.repository.IAdsRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class IAdsRepositoryImpl(
    private val remoteConfig: IRemoteConfig,
    private val networkClient: NetworkClient,
    private val json: Json = Json {
        this.ignoreUnknownKeys = true
    },
) : IAdsRepository {
    private var _cacheAdsList: List<ShopeeAffiliateAds>? = null
    private var _lastFetchAdsList: Long = 0L

    @OptIn(ExperimentalTime::class)
    override suspend fun getAdsList(): List<ShopeeAffiliateAds> {
        if (!_cacheAdsList.isNullOrEmpty() &&
            Clock.System.now().epochSeconds - _lastFetchAdsList < 5 * 60
        ) {
            return _cacheAdsList!!
        }
        val configUrl = remoteConfig.getString("ads_url") ?: return emptyList()
        val adsListRes = networkClient.get(configUrl)
        val adsList = runCatching {
            json.decodeFromString<ShopeeAffiliateAdsResponse>(adsListRes)
        }.onFailure {
            it.printStackTrace()
        }.getOrNull() ?: return emptyList()
        _lastFetchAdsList = Clock.System.now().epochSeconds
        _cacheAdsList = adsList.data
        return adsList.data ?: emptyList()
    }

    override suspend fun getAdsVideoList(): List<ShopeeAffiliateAds> {
        val configUrl = remoteConfig.getString("ads_video") ?: return emptyList()
        val adsListRes = networkClient.get(configUrl)
        val adsList = json.decodeFromString<ShopeeAffiliateAdsResponse>(adsListRes)
        return adsList.data ?: emptyList()
    }

    override suspend fun getOpenAdsList(): List<ShopeeAffiliateAds> {
        val configUrl = remoteConfig.getString("ads_open_app") ?: return emptyList()
        val adsListRes = networkClient.get(configUrl)
        val adsList = json.decodeFromString<ShopeeAffiliateAdsResponse>(adsListRes)
        return adsList.data ?: emptyList()
    }
}
