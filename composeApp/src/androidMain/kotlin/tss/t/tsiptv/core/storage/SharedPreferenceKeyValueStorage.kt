package tss.t.tsiptv.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * SharedPreferences implementation of KeyValueStorage.
 * This implementation uses SharedPreferences to persist data between app restarts.
 *
 * @property context The application context
 * @property preferenceName The name of the SharedPreferences file, or null to use the default
 */
class SharedPreferenceKeyValueStorage(
    private val context: Context,
    private val preferenceName: String? = null
) : KeyValueStorage {
    private val prefs: SharedPreferences = if (preferenceName != null) {
        context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    } else {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private val stringFlows = mutableMapOf<String, MutableStateFlow<String>>()
    private val intFlows = mutableMapOf<String, MutableStateFlow<Int>>()
    private val longFlows = mutableMapOf<String, MutableStateFlow<Long>>()
    private val floatFlows = mutableMapOf<String, MutableStateFlow<Float>>()
    private val booleanFlows = mutableMapOf<String, MutableStateFlow<Boolean>>()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when {
            stringFlows.containsKey(key) -> {
                stringFlows[key]?.value = prefs.getString(key, "") ?: ""
            }
            intFlows.containsKey(key) -> {
                intFlows[key]?.value = prefs.getInt(key, 0)
            }
            longFlows.containsKey(key) -> {
                longFlows[key]?.value = prefs.getLong(key, 0L)
            }
            floatFlows.containsKey(key) -> {
                floatFlows[key]?.value = prefs.getFloat(key, 0f)
            }
            booleanFlows.containsKey(key) -> {
                booleanFlows[key]?.value = prefs.getBoolean(key, false)
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override suspend fun putString(key: String, value: String) {
        withContext(Dispatchers.IO) {
            prefs.edit {
                putString(key, value)
            }
        }
    }

    override suspend fun getString(key: String, defaultValue: String): String {
        return withContext(Dispatchers.IO) {
            prefs.getString(key, defaultValue) ?: defaultValue
        }
    }

    override suspend fun putInt(key: String, value: Int) {
        withContext(Dispatchers.IO) {
            prefs.edit {
                putInt(key, value)
            }
        }
    }

    override suspend fun getInt(key: String, defaultValue: Int): Int {
        return withContext(Dispatchers.IO) {
            prefs.getInt(key, defaultValue)
        }
    }

    override suspend fun putLong(key: String, value: Long) {
        withContext(Dispatchers.IO) {
            prefs.edit {
                putLong(key, value)
            }
        }
    }

    override suspend fun getLong(key: String, defaultValue: Long): Long {
        return withContext(Dispatchers.IO) {
            prefs.getLong(key, defaultValue)
        }
    }

    override suspend fun putFloat(key: String, value: Float) {
        withContext(Dispatchers.IO) {
            prefs.edit {
                putFloat(key, value)
            }
        }
    }

    override suspend fun getFloat(key: String, defaultValue: Float): Float {
        return withContext(Dispatchers.IO) {
            prefs.getFloat(key, defaultValue)
        }
    }

    override suspend fun putBoolean(key: String, value: Boolean) {
        withContext(Dispatchers.IO) {
            prefs.edit {
                putBoolean(key, value)
            }
        }
    }

    override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            prefs.getBoolean(key, defaultValue)
        }
    }

    override fun observeString(key: String, defaultValue: String): Flow<String> {
        if (!stringFlows.containsKey(key)) {
            val initialValue = prefs.getString(key, defaultValue) ?: defaultValue
            stringFlows[key] = MutableStateFlow(initialValue)
        }
        return stringFlows[key]!!
    }

    override fun observeInt(key: String, defaultValue: Int): Flow<Int> {
        if (!intFlows.containsKey(key)) {
            val initialValue = prefs.getInt(key, defaultValue)
            intFlows[key] = MutableStateFlow(initialValue)
        }
        return intFlows[key]!!
    }

    override fun observeLong(key: String, defaultValue: Long): Flow<Long> {
        if (!longFlows.containsKey(key)) {
            val initialValue = prefs.getLong(key, defaultValue)
            longFlows[key] = MutableStateFlow(initialValue)
        }
        return longFlows[key]!!
    }

    override fun observeFloat(key: String, defaultValue: Float): Flow<Float> {
        if (!floatFlows.containsKey(key)) {
            val initialValue = prefs.getFloat(key, defaultValue)
            floatFlows[key] = MutableStateFlow(initialValue)
        }
        return floatFlows[key]!!
    }

    override fun observeBoolean(key: String, defaultValue: Boolean): Flow<Boolean> {
        if (!booleanFlows.containsKey(key)) {
            val initialValue = prefs.getBoolean(key, defaultValue)
            booleanFlows[key] = MutableStateFlow(initialValue)
        }
        return booleanFlows[key]!!
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) {
            prefs.edit {
                remove(key)
            }
        }
        stringFlows.remove(key)
        intFlows.remove(key)
        longFlows.remove(key)
        floatFlows.remove(key)
        booleanFlows.remove(key)
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            prefs.edit {
                clear()
            }
        }
        stringFlows.clear()
        intFlows.clear()
        longFlows.clear()
        floatFlows.clear()
        booleanFlows.clear()
    }
}
