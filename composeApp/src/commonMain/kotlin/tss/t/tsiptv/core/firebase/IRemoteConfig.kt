package tss.t.tsiptv.core.firebase

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

interface IRemoteConfig {
    suspend fun getString(key: String): String?
    suspend fun getInt(key: String): Int?
    suspend fun getLong(key: String): Long?
    suspend fun getBoolean(key: String): Boolean?
    suspend fun getVersion(): Long?
    suspend fun getJson(key: String): JsonElement?
    suspend fun getJsonArray(key: String): JsonArray?
}
