package tss.t.tsiptv.core.database

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import tss.t.tsiptv.core.parser.IPTVProgram

class Converter {

    @TypeConverter
    fun fromCredits(credits: IPTVProgram.Credits?): String? {
        return runCatching {
            Json.encodeToString(credits)
        }.getOrNull()
    }

    @TypeConverter
    fun toCredits(credits: String?): IPTVProgram.Credits? {
        return runCatching {
            Json.decodeFromString<IPTVProgram.Credits?>(credits!!)
        }.getOrNull()
    }

}