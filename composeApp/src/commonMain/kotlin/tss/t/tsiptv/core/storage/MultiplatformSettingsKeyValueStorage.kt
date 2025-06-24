package tss.t.tsiptv.core.storage

import com.russhwolf.settings.Settings
import com.russhwolf.settings.coroutines.FlowSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Multiplatform Settings implementation of KeyValueStorage.
 * This implementation uses Multiplatform Settings to persist data between app restarts.
 *
 * @property settingsFactory The factory to create Settings instances
 * @property name The name of the settings
 */
class MultiplatformSettingsKeyValueStorage(
    private val settingsFactory: SettingsFactory,
    private val name: String
) : KeyValueStorage {
    private val settings: Settings = settingsFactory.createSettings(name)

    // Use MutableStateFlow to observe changes
    private val stringFlows = mutableMapOf<String, MutableStateFlow<String>>()
    private val intFlows = mutableMapOf<String, MutableStateFlow<Int>>()
    private val longFlows = mutableMapOf<String, MutableStateFlow<Long>>()
    private val floatFlows = mutableMapOf<String, MutableStateFlow<Float>>()
    private val booleanFlows = mutableMapOf<String, MutableStateFlow<Boolean>>()

    override suspend fun putString(key: String, value: String) {
        settings.putString(key, value)
        stringFlows[key]?.value = value
    }

    override suspend fun getString(key: String, defaultValue: String): String {
        return settings.getString(key, defaultValue)
    }

    override suspend fun putInt(key: String, value: Int) {
        settings.putInt(key, value)
        intFlows[key]?.value = value
    }

    override suspend fun getInt(key: String, defaultValue: Int): Int {
        return settings.getInt(key, defaultValue)
    }

    override suspend fun putLong(key: String, value: Long) {
        settings.putLong(key, value)
        longFlows[key]?.value = value
    }

    override suspend fun getLong(key: String, defaultValue: Long): Long {
        return settings.getLong(key, defaultValue)
    }

    override suspend fun putFloat(key: String, value: Float) {
        settings.putFloat(key, value)
        floatFlows[key]?.value = value
    }

    override suspend fun getFloat(key: String, defaultValue: Float): Float {
        return settings.getFloat(key, defaultValue)
    }

    override suspend fun putBoolean(key: String, value: Boolean) {
        settings.putBoolean(key, value)
        booleanFlows[key]?.value = value
    }

    override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return settings.getBoolean(key, defaultValue)
    }

    override fun observeString(key: String, defaultValue: String): Flow<String> {
        if (!stringFlows.containsKey(key)) {
            val initialValue = settings.getString(key, defaultValue)
            stringFlows[key] = MutableStateFlow(initialValue)
        }
        return stringFlows[key]!!
    }

    override fun observeInt(key: String, defaultValue: Int): Flow<Int> {
        if (!intFlows.containsKey(key)) {
            val initialValue = settings.getInt(key, defaultValue)
            intFlows[key] = MutableStateFlow(initialValue)
        }
        return intFlows[key]!!
    }

    override fun observeLong(key: String, defaultValue: Long): Flow<Long> {
        if (!longFlows.containsKey(key)) {
            val initialValue = settings.getLong(key, defaultValue)
            longFlows[key] = MutableStateFlow(initialValue)
        }
        return longFlows[key]!!
    }

    override fun observeFloat(key: String, defaultValue: Float): Flow<Float> {
        if (!floatFlows.containsKey(key)) {
            val initialValue = settings.getFloat(key, defaultValue)
            floatFlows[key] = MutableStateFlow(initialValue)
        }
        return floatFlows[key]!!
    }

    override fun observeBoolean(key: String, defaultValue: Boolean): Flow<Boolean> {
        if (!booleanFlows.containsKey(key)) {
            val initialValue = settings.getBoolean(key, defaultValue)
            booleanFlows[key] = MutableStateFlow(initialValue)
        }
        return booleanFlows[key]!!
    }

    override suspend fun remove(key: String) {
        settings.remove(key)
        stringFlows.remove(key)
        intFlows.remove(key)
        longFlows.remove(key)
        floatFlows.remove(key)
        booleanFlows.remove(key)
    }

    override suspend fun clear() {
        settings.clear()
        stringFlows.clear()
        intFlows.clear()
        longFlows.clear()
        floatFlows.clear()
        booleanFlows.clear()
    }
}
