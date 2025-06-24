package tss.t.tsiptv.core.storage

import kotlinx.coroutines.flow.Flow

/**
 * Interface for key-value storage.
 * This is a platform-independent interface that will have platform-specific implementations.
 */
interface KeyValueStorage {
    /**
     * Stores a string value with the given key.
     *
     * @param key The key to store the value under
     * @param value The string value to store
     */
    suspend fun putString(key: String, value: String)

    /**
     * Retrieves a string value for the given key.
     *
     * @param key The key to retrieve the value for
     * @param defaultValue The default value to return if the key is not found
     * @return The stored string value, or the default value if the key is not found
     */
    suspend fun getString(key: String, defaultValue: String = ""): String

    /**
     * Stores an integer value with the given key.
     *
     * @param key The key to store the value under
     * @param value The integer value to store
     */
    suspend fun putInt(key: String, value: Int)

    /**
     * Retrieves an integer value for the given key.
     *
     * @param key The key to retrieve the value for
     * @param defaultValue The default value to return if the key is not found
     * @return The stored integer value, or the default value if the key is not found
     */
    suspend fun getInt(key: String, defaultValue: Int = 0): Int

    /**
     * Stores a long value with the given key.
     *
     * @param key The key to store the value under
     * @param value The long value to store
     */
    suspend fun putLong(key: String, value: Long)

    /**
     * Retrieves a long value for the given key.
     *
     * @param key The key to retrieve the value for
     * @param defaultValue The default value to return if the key is not found
     * @return The stored long value, or the default value if the key is not found
     */
    suspend fun getLong(key: String, defaultValue: Long = 0L): Long

    /**
     * Stores a float value with the given key.
     *
     * @param key The key to store the value under
     * @param value The float value to store
     */
    suspend fun putFloat(key: String, value: Float)

    /**
     * Retrieves a float value for the given key.
     *
     * @param key The key to retrieve the value for
     * @param defaultValue The default value to return if the key is not found
     * @return The stored float value, or the default value if the key is not found
     */
    suspend fun getFloat(key: String, defaultValue: Float = 0f): Float

    /**
     * Stores a boolean value with the given key.
     *
     * @param key The key to store the value under
     * @param value The boolean value to store
     */
    suspend fun putBoolean(key: String, value: Boolean)

    /**
     * Retrieves a boolean value for the given key.
     *
     * @param key The key to retrieve the value for
     * @param defaultValue The default value to return if the key is not found
     * @return The stored boolean value, or the default value if the key is not found
     */
    suspend fun getBoolean(key: String, defaultValue: Boolean = false): Boolean

    /**
     * Observes changes to a string value for the given key.
     *
     * @param key The key to observe
     * @param defaultValue The default value to emit if the key is not found
     * @return A Flow that emits the current value and any future changes
     */
    fun observeString(key: String, defaultValue: String = ""): Flow<String>

    /**
     * Observes changes to an integer value for the given key.
     *
     * @param key The key to observe
     * @param defaultValue The default value to emit if the key is not found
     * @return A Flow that emits the current value and any future changes
     */
    fun observeInt(key: String, defaultValue: Int = 0): Flow<Int>

    /**
     * Observes changes to a long value for the given key.
     *
     * @param key The key to observe
     * @param defaultValue The default value to emit if the key is not found
     * @return A Flow that emits the current value and any future changes
     */
    fun observeLong(key: String, defaultValue: Long = 0L): Flow<Long>

    /**
     * Observes changes to a float value for the given key.
     *
     * @param key The key to observe
     * @param defaultValue The default value to emit if the key is not found
     * @return A Flow that emits the current value and any future changes
     */
    fun observeFloat(key: String, defaultValue: Float = 0f): Flow<Float>

    /**
     * Observes changes to a boolean value for the given key.
     *
     * @param key The key to observe
     * @param defaultValue The default value to emit if the key is not found
     * @return A Flow that emits the current value and any future changes
     */
    fun observeBoolean(key: String, defaultValue: Boolean = false): Flow<Boolean>

    /**
     * Removes the value for the given key.
     *
     * @param key The key to remove
     */
    suspend fun remove(key: String)

    /**
     * Clears all stored values.
     */
    suspend fun clear()
}
