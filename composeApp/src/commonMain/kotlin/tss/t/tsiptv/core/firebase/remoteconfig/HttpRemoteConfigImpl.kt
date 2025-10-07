package tss.t.tsiptv.core.firebase.remoteconfig

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.boolean
import tss.t.tsiptv.core.firebase.IRemoteConfig
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.storage.KeyValueStorage
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

class HttpRemoteConfigImpl(
    private val networkClient: NetworkClient,
    private val keyValueStorage: KeyValueStorage,
    coroutineScope: CoroutineScope,
    private val configUrl: String = "https://dvt1405.github.io/iMediaReleasePages/ads_config.json",
) : IRemoteConfig {

    private var cachedConfig: JsonObject? = null

    init {
        coroutineScope.launch {
            loadCachedConfig()
            fetchRemoteConfig()
        }
    }

    private suspend fun loadCachedConfig() {
        val cachedJson = keyValueStorage.getString(CONFIG_CACHE_KEY)
        try {
            cachedConfig = Json.parseToJsonElement(cachedJson).jsonObject
        } catch (e: Exception) {
            // Invalid cached config, ignore
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun fetchRemoteConfig() {
        try {
            val lastFetch = keyValueStorage.getLong(CONFIG_LAST_FETCH_KEY)
            val currentTime = Clock.System.now().toEpochMilliseconds()

            // Check if we need to fetch (cache expired)
            if ((currentTime - lastFetch) < CACHE_DURATION_HOURS.hours.inWholeMilliseconds) {
                return // Use cached version
            }

            val response = networkClient.get(configUrl)
            val configJson = Json.parseToJsonElement(response).jsonObject
            cachedConfig = configJson
            keyValueStorage.putString(CONFIG_CACHE_KEY, response)
            keyValueStorage.putLong(CONFIG_LAST_FETCH_KEY, currentTime)

            // Update version if available
            configJson["version"]?.jsonPrimitive?.long?.let { version ->
                keyValueStorage.putLong(CONFIG_VERSION_KEY, version)
            }

        } catch (e: Exception) {
            // Failed to fetch, use cached config if available
            // Log error in production app
        }
    }

    override suspend fun getString(key: String): String? {
        ensureConfigLoaded()
        return cachedConfig?.get(key)?.jsonPrimitive?.content
    }

    override suspend fun getInt(key: String): Int? {
        ensureConfigLoaded()
        return cachedConfig?.get(key)?.jsonPrimitive?.content?.toIntOrNull()
    }

    override suspend fun getLong(key: String): Long? {
        ensureConfigLoaded()
        return cachedConfig?.get(key)?.jsonPrimitive?.long
    }

    override suspend fun getBoolean(key: String): Boolean? {
        ensureConfigLoaded()
        return cachedConfig?.get(key)?.jsonPrimitive?.boolean
    }

    override suspend fun getVersion(): Long? {
        return keyValueStorage.getLong(CONFIG_VERSION_KEY)
    }

    override suspend fun getJson(key: String): JsonElement? {
        ensureConfigLoaded()
        return runCatching {
            cachedConfig?.get(key)?.let { element ->
                if (element is JsonObject || element is JsonArray) {
                    element
                } else {
                    // If it's a string, try to parse it as JSON
                    element.jsonPrimitive.content.let {
                        Json.parseToJsonElement(it)
                    }
                }
            }
        }.getOrNull()
    }

    override suspend fun getJsonArray(key: String): JsonArray? {
        ensureConfigLoaded()
        return runCatching {
            cachedConfig?.get(key)?.let { element ->
                element as? JsonArray
                    ?: // If it's a string, try to parse it as JSON array
                    element.jsonPrimitive.content.let {
                        Json.parseToJsonElement(it).jsonArray
                    }
            }
        }.getOrNull()
    }

    private suspend fun ensureConfigLoaded() {
        if (cachedConfig == null) {
            loadCachedConfig()
        }
    }

    companion object {
        private const val CONFIG_CACHE_KEY = "remote_config_cache"
        private const val CONFIG_VERSION_KEY = "remote_config_version"
        private const val CONFIG_LAST_FETCH_KEY = "remote_config_last_fetch"
        private const val CACHE_DURATION_HOURS = 1L
    }
}
