package tss.t.tsiptv.ui.screens.ads

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import tss.t.tsiptv.core.firebase.IRemoteConfig
import tss.t.tsiptv.core.model.ShopeeAffiliateAds
import tss.t.tsiptv.core.repository.IAdsRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AdsViewModel(
    private val remoteConfig: IRemoteConfig,
    private val adsRepository: IAdsRepository,
) : ViewModel() {

    private val _ads = MutableStateFlow<List<ShopeeAffiliateAds>>(emptyList())
    val ads: StateFlow<List<ShopeeAffiliateAds>> = _ads.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _displayAd by lazy {
        MutableStateFlow<ShopeeAffiliateAds?>(null)
    }
    val displayAd: StateFlow<ShopeeAffiliateAds?>
        get() = _displayAd
    private var _lastTimeDisplay = 0L
    private val _adsMaps = mutableStateMapOf<String, ShopeeAffiliateAds>()
    private val _adsJobs = mutableStateMapOf<String, Job?>()
    private val _adsTime = mutableStateMapOf<String, Long>()

    fun loadAds() {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val adsList = adsRepository.getAdsList()
                _ads.value = adsList
                val adList = _ads.value
                val displayAds = adList.randomOrNull()
                if (displayAds != null) {
                    _displayAd.update {
                        displayAds
                    }
                }
                refreshAds()
            } catch (e: Exception) {
                e.printStackTrace()
                _ads.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    fun refreshAds() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isLoading.value) {
                try {
                    withTimeout(30_000) {
                        while (_isLoading.value) {
                            delay(15_00)
                        }
                    }
                } catch (_: Exception) {
                    return@launch
                }
            }

            while (_ads.value.isNotEmpty() && !_isLoading.value) {
                val gap = Clock.System.now().epochSeconds - _lastTimeDisplay
                if (gap <= 60 * 2) {
                    delay(120 * 1_000)
                }
                val adList = _ads.value
                val displayAds = adList.randomOrNull()
                if (displayAds != null) {
                    _displayAd.update {
                        displayAds
                    }
                    _lastTimeDisplay = Clock.System.now().epochSeconds
                } else {
                    break
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    fun refreshAdsForKey(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _adsJobs[key]?.cancel()
            if (_isLoading.value) {
                runCatching {
                    withTimeout(60_000) {
                        while (_isLoading.value) {
                            delay(15_00)
                        }
                    }
                }
            }
            while (_ads.value.isNotEmpty() &&
                !_isLoading.value &&
                true == _adsJobs[key]?.isActive
            ) {
                val gap = Clock.System.now().epochSeconds - (_adsTime[key] ?: 0)
                if (gap <= 60 * 2) {
                    delay(120 * 1_000)
                }
                val adList = _ads.value
                val displayAds = adList.randomOrNull()
                if (displayAds != null) {
                    _adsMaps.put(key, displayAds)
                    _adsTime[key] = Clock.System.now().epochSeconds
                } else {
                    break
                }
            }
        }.also {
            _adsJobs[key] = it
        }.invokeOnCompletion {
            _adsJobs[key] = null
        }
    }

    fun removeBannerAdsForKey(key: String) {
        viewModelScope.launch {
            _adsJobs[key]?.cancelAndJoin()
        }
    }

    @Composable
    fun RefreshAdsForKeyWithLifeCycle(
        key: String,
        content: @Composable (ads: ShopeeAffiliateAds) -> Unit,
    ) {
        val ads = remember(_adsMaps[key]) {
            _adsMaps[key]
        }

        DisposableEffect(Unit) {
            refreshAdsForKey(key)

            onDispose {
                removeBannerAdsForKey(key)
            }
        }

        ads?.let {
            content(it)
        } ?: Spacer(Modifier.height(1.dp))

    }
}
