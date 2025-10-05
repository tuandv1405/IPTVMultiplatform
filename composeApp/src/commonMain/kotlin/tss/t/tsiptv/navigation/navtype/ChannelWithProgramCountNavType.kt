package tss.t.tsiptv.navigation.navtype

import androidx.navigation.NavType
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import kotlinx.serialization.json.Json
import tss.t.tsiptv.core.database.entity.ChannelWithProgramCount
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


object ChannelWithProgramCountNavType : NavType<ChannelWithProgramCount>(isNullableAllowed = false) {
    override fun put(
        bundle: SavedState,
        key: String,
        value: ChannelWithProgramCount,
    ) {
        bundle.write {
            putString(key, Json.encodeToString(value))
        }
    }

    override fun get(
        bundle: SavedState,
        key: String,
    ): ChannelWithProgramCount? {
        return bundle.read {
            getString(key)
        }.let {
            Json.decodeFromString(it)
        }
    }
    @OptIn(ExperimentalEncodingApi::class)
    override fun parseValue(value: String): ChannelWithProgramCount {
        // value is URL-safe Base64; decode to JSON then to object
        val json = Base64.UrlSafe.decode(value).decodeToString()
        return Json.decodeFromString(json)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun serializeAsValue(value: ChannelWithProgramCount): String {
        val json = Json.encodeToString(value)
        return Base64.UrlSafe.encode(json.encodeToByteArray())
    }
}
