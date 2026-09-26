package tss.t.tsiptv.core.uimode

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tss.t.tsiptv.core.storage.KeyValueStorage

/**
 * Which layout the app renders.
 *
 * [AUTO] follows the device: TV layout on a television, phone layout elsewhere.
 * [PHONE] and [TV] are explicit choices from Settings and win over detection.
 */
enum class UiMode {
    AUTO,
    PHONE,
    TV;

    fun resolveIsTv(deviceIsTv: Boolean): Boolean = when (this) {
        AUTO -> deviceIsTv
        PHONE -> false
        TV -> true
    }

    companion object {
        fun fromName(name: String?): UiMode = entries.find { it.name == name } ?: AUTO
    }
}

class UiModeRepository(private val storage: KeyValueStorage) {
    companion object {
        private const val KEY_UI_MODE = "ui_mode"
    }

    fun observeUiMode(): Flow<UiMode> =
        storage.observeString(KEY_UI_MODE, UiMode.AUTO.name).map(UiMode::fromName)

    suspend fun getUiMode(): UiMode = UiMode.fromName(storage.getString(KEY_UI_MODE, ""))

    // AUTO is written rather than removed: KeyValueStorage.remove() drops the
    // observed flow without emitting, so observers would never see the change.
    suspend fun setUiMode(mode: UiMode) {
        storage.putString(KEY_UI_MODE, mode.name)
    }
}

/** The mode the user picked in Settings (not the resolved layout). */
val LocalUiMode = staticCompositionLocalOf { UiMode.AUTO }

/** True when the TV layout is in use, after applying [UiMode] to device detection. */
val LocalIsTvMode = staticCompositionLocalOf { false }
