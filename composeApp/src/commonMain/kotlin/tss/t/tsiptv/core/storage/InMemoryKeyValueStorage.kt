package tss.t.tsiptv.core.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A simple in-memory implementation of KeyValueStorage.
 * This implementation stores values in memory and doesn't persist them between app restarts.
 * It's useful for testing and as a placeholder until platform-specific implementations are created.
 */
class InMemoryKeyValueStorage : KeyValueStorage {
    private val stringValues = mutableMapOf<String, MutableStateFlow<String>>()
    private val intValues = mutableMapOf<String, MutableStateFlow<Int>>()
    private val longValues = mutableMapOf<String, MutableStateFlow<Long>>()
    private val floatValues = mutableMapOf<String, MutableStateFlow<Float>>()
    private val booleanValues = mutableMapOf<String, MutableStateFlow<Boolean>>()

    override suspend fun putString(key: String, value: String) {
        if (stringValues.containsKey(key)) {
            stringValues[key]?.value = value
        } else {
            stringValues[key] = MutableStateFlow(value)
        }
    }

    override suspend fun getString(key: String, defaultValue: String): String {
        return stringValues[key]?.value ?: defaultValue
    }

    override suspend fun putInt(key: String, value: Int) {
        if (intValues.containsKey(key)) {
            intValues[key]?.value = value
        } else {
            intValues[key] = MutableStateFlow(value)
        }
    }

    override suspend fun getInt(key: String, defaultValue: Int): Int {
        return intValues[key]?.value ?: defaultValue
    }

    override suspend fun putLong(key: String, value: Long) {
        if (longValues.containsKey(key)) {
            longValues[key]?.value = value
        } else {
            longValues[key] = MutableStateFlow(value)
        }
    }

    override suspend fun getLong(key: String, defaultValue: Long): Long {
        return longValues[key]?.value ?: defaultValue
    }

    override suspend fun putFloat(key: String, value: Float) {
        if (floatValues.containsKey(key)) {
            floatValues[key]?.value = value
        } else {
            floatValues[key] = MutableStateFlow(value)
        }
    }

    override suspend fun getFloat(key: String, defaultValue: Float): Float {
        return floatValues[key]?.value ?: defaultValue
    }

    override suspend fun putBoolean(key: String, value: Boolean) {
        if (booleanValues.containsKey(key)) {
            booleanValues[key]?.value = value
        } else {
            booleanValues[key] = MutableStateFlow(value)
        }
    }

    override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return booleanValues[key]?.value ?: defaultValue
    }

    override fun observeString(key: String, defaultValue: String): Flow<String> {
        if (!stringValues.containsKey(key)) {
            stringValues[key] = MutableStateFlow(defaultValue)
        }
        return stringValues[key]!!
    }

    override fun observeInt(key: String, defaultValue: Int): Flow<Int> {
        if (!intValues.containsKey(key)) {
            intValues[key] = MutableStateFlow(defaultValue)
        }
        return intValues[key]!!
    }

    override fun observeLong(key: String, defaultValue: Long): Flow<Long> {
        if (!longValues.containsKey(key)) {
            longValues[key] = MutableStateFlow(defaultValue)
        }
        return longValues[key]!!
    }

    override fun observeFloat(key: String, defaultValue: Float): Flow<Float> {
        if (!floatValues.containsKey(key)) {
            floatValues[key] = MutableStateFlow(defaultValue)
        }
        return floatValues[key]!!
    }

    override fun observeBoolean(key: String, defaultValue: Boolean): Flow<Boolean> {
        if (!booleanValues.containsKey(key)) {
            booleanValues[key] = MutableStateFlow(defaultValue)
        }
        return booleanValues[key]!!
    }

    override suspend fun remove(key: String) {
        stringValues.remove(key)
        intValues.remove(key)
        longValues.remove(key)
        floatValues.remove(key)
        booleanValues.remove(key)
    }

    override suspend fun clear() {
        stringValues.clear()
        intValues.clear()
        longValues.clear()
        floatValues.clear()
        booleanValues.clear()
    }
}
