package app.m1k3.ai.assistant.mocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Test implementation of PreferencesStore for testing.
 *
 * Provides an in-memory preference store that doesn't require
 * Android context or SharedPreferences.
 *
 * **Usage:**
 * ```kotlin
 * val prefs = TestPreferencesStore()
 * prefs.setBoolean("rag_enabled", true)
 * assertEquals(true, prefs.getBoolean("rag_enabled", false))
 *
 * // Test reactive updates
 * prefs.observeBoolean("key", false).test {
 *     assertEquals(false, awaitItem())
 *     prefs.setBoolean("key", true)
 *     assertEquals(true, awaitItem())
 * }
 * ```
 */
class TestPreferencesStore {
    // In-memory storage
    private val storage = mutableMapOf<String, Any?>()

    // StateFlow for reactive updates
    private val updateFlow = MutableStateFlow(0L)

    // ===== Boolean =====

    fun getBoolean(key: String, default: Boolean): Boolean {
        return storage[key] as? Boolean ?: default
    }

    fun setBoolean(key: String, value: Boolean) {
        storage[key] = value
        notifyChange()
    }

    fun observeBoolean(key: String, default: Boolean): Flow<Boolean> {
        return updateFlow.map { getBoolean(key, default) }
    }

    // ===== String =====

    fun getString(key: String, default: String?): String? {
        return if (storage.containsKey(key)) {
            storage[key] as? String
        } else {
            default
        }
    }

    fun setString(key: String, value: String?) {
        if (value == null) {
            storage.remove(key)
        } else {
            storage[key] = value
        }
        notifyChange()
    }

    // ===== Int =====

    fun getInt(key: String, default: Int): Int {
        return storage[key] as? Int ?: default
    }

    fun setInt(key: String, value: Int) {
        storage[key] = value
        notifyChange()
    }

    // ===== Operations =====

    fun contains(key: String): Boolean {
        return storage.containsKey(key)
    }

    fun remove(key: String) {
        storage.remove(key)
        notifyChange()
    }

    fun clear() {
        storage.clear()
        notifyChange()
    }

    // ===== Test Helpers =====

    /**
     * Get all stored preferences for verification.
     */
    fun getAllPreferences(): Map<String, Any?> = storage.toMap()

    /**
     * Set multiple preferences at once for test setup.
     */
    fun setAll(preferences: Map<String, Any?>) {
        storage.putAll(preferences)
        notifyChange()
    }

    /**
     * Reset to default state (empty).
     */
    fun reset() {
        clear()
    }

    private fun notifyChange() {
        updateFlow.value = System.currentTimeMillis()
    }

    companion object {
        /**
         * Create with RAG enabled (common test scenario).
         */
        fun withRagEnabled() = TestPreferencesStore().apply {
            setBoolean("rag_enabled", true)
        }

        /**
         * Create with debug mode enabled.
         */
        fun withDebugMode() = TestPreferencesStore().apply {
            setBoolean("debug_mode", true)
        }
    }
}
