//package tss.t.tsiptv.core.firebase.remoteconfig
//
//import dev.gitlive.firebase.Firebase
//import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
//import dev.gitlive.firebase.remoteconfig.remoteConfig
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.launch
//import kotlinx.serialization.json.Json
//import kotlinx.serialization.json.JsonArray
//import kotlinx.serialization.json.JsonElement
//import kotlinx.serialization.json.jsonArray
//import kotlinx.serialization.decodeFromString
//import tss.t.tsiptv.core.model.ShopeeAffiliateAds
//import tss.t.tsiptv.core.model.ShopeeAffiliateAdsResponse
//import tss.t.tsiptv.core.firebase.IRemoteConfig
//import tss.t.tsiptv.core.storage.KeyValueStorage
//import kotlin.time.Duration.Companion.seconds
//
//class FirebaseRemoteConfigImpl(
//    private val impl: FirebaseRemoteConfig = Firebase.remoteConfig,
//    private val keyValueStorage: KeyValueStorage,
//    private val coroutineScope: CoroutineScope,
//) : IRemoteConfig {
//
//    init {
//        coroutineScope.launch {
//            impl.settings {
//                this.minimumFetchInterval = 3600.seconds
//                this.fetchTimeout = 200.seconds
//            }
//            val activate = impl.fetchAndActivate()
//            if (activate) {
//                val version = impl.getValue("remote_config_version").asLong()
//                keyValueStorage.putLong("remote_config_version", version)
//            }
//        }
//    }
//
//    override suspend fun getString(key: String): String? {
//        return impl.getValue(key).asString()
//    }
//
//    override suspend fun getInt(key: String): Int? {
//        return impl.getValue(key).asLong().toInt()
//    }
//
//    override suspend fun getLong(key: String): Long? {
//        return impl.getValue(key).asLong()
//    }
//
//    override suspend fun getBoolean(key: String): Boolean? {
//        return impl.getValue(key).asBoolean()
//    }
//
//    override suspend fun getVersion(): Long? {
//        return keyValueStorage.getLong("remote_config_version")
//    }
//
//    override suspend fun getJson(key: String): JsonElement? {
//        return runCatching {
//            impl.getValue(key).asString().let {
//                Json.parseToJsonElement(it)
//            }
//        }.getOrNull()
//    }
//
//    override suspend fun getJsonArray(key: String): JsonArray? {
//        return runCatching {
//            impl.getValue(key).asString().let {
//                Json.parseToJsonElement(it).jsonArray
//            }
//        }.getOrNull()
//    }
//}
