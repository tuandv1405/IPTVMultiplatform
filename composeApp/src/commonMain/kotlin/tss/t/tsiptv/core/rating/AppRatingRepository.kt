package tss.t.tsiptv.core.rating

import tss.t.tsiptv.core.storage.KeyValueStorage

/** Persists [AppRatingState] in the app's key-value settings. */
class AppRatingRepository(private val storage: KeyValueStorage) {
    companion object {
        private const val KEY_FIRST_LAUNCH_AT = "rating:first_launch_at"
        private const val KEY_PLAY_COUNT = "rating:play_count"
        private const val KEY_LAST_PROMPT_AT = "rating:last_prompt_at"
        private const val KEY_PROMPT_COUNT = "rating:prompt_count"
        private const val KEY_LAST_MANUAL_RATE_AT = "rating:last_manual_rate_at"
    }

    suspend fun getState(): AppRatingState = AppRatingState(
        firstLaunchAt = storage.getLong(KEY_FIRST_LAUNCH_AT, 0L),
        playCount = storage.getInt(KEY_PLAY_COUNT, 0),
        lastPromptAt = storage.getLong(KEY_LAST_PROMPT_AT, 0L),
        promptCount = storage.getInt(KEY_PROMPT_COUNT, 0),
        lastManualRateAt = storage.getLong(KEY_LAST_MANUAL_RATE_AT, 0L),
    )

    /** Only the first call counts; existing installs start their clock on update. */
    suspend fun recordFirstLaunchIfNeeded(now: Long) {
        if (storage.getLong(KEY_FIRST_LAUNCH_AT, 0L) == 0L) {
            storage.putLong(KEY_FIRST_LAUNCH_AT, now)
        }
    }

    suspend fun recordPlay() {
        storage.putInt(KEY_PLAY_COUNT, storage.getInt(KEY_PLAY_COUNT, 0) + 1)
    }

    suspend fun recordPrompt(now: Long) {
        storage.putLong(KEY_LAST_PROMPT_AT, now)
        storage.putInt(KEY_PROMPT_COUNT, storage.getInt(KEY_PROMPT_COUNT, 0) + 1)
    }

    suspend fun recordManualRate(now: Long) {
        storage.putLong(KEY_LAST_MANUAL_RATE_AT, now)
    }
}
