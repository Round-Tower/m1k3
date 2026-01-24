package app.m1k3.ai.domain.platform

import kotlinx.coroutines.flow.Flow

/**
 * Preferences Store Interface - Abstract key-value storage.
 *
 * Provides type-safe access to user preferences with reactive observation.
 * Platform implementations handle actual persistence (SharedPreferences,
 * DataStore, UserDefaults, etc.).
 *
 * Domain interface - Pure Kotlin, no platform dependencies.
 *
 * **Supported Types:**
 * - Boolean: Feature flags, toggles
 * - String: Text preferences, enum values as strings
 * - Int: Numeric settings, counts
 */
interface PreferencesStoreInterface {

    // ===== Boolean Operations =====

    /**
     * Get a boolean preference.
     *
     * @param key Preference key
     * @param default Default value if not set
     * @return Current value or default
     */
    fun getBoolean(key: String, default: Boolean): Boolean

    /**
     * Set a boolean preference.
     *
     * @param key Preference key
     * @param value Value to store
     */
    fun setBoolean(key: String, value: Boolean)

    /**
     * Observe a boolean preference as a Flow.
     *
     * @param key Preference key
     * @param default Default value if not set
     * @return Flow that emits when preference changes
     */
    fun observeBoolean(key: String, default: Boolean): Flow<Boolean>

    // ===== String Operations =====

    /**
     * Get a string preference.
     *
     * @param key Preference key
     * @param default Default value if not set
     * @return Current value or default
     */
    fun getString(key: String, default: String?): String?

    /**
     * Set a string preference.
     *
     * @param key Preference key
     * @param value Value to store (null removes the preference)
     */
    fun setString(key: String, value: String?)

    // ===== Int Operations =====

    /**
     * Get an integer preference.
     *
     * @param key Preference key
     * @param default Default value if not set
     * @return Current value or default
     */
    fun getInt(key: String, default: Int): Int

    /**
     * Set an integer preference.
     *
     * @param key Preference key
     * @param value Value to store
     */
    fun setInt(key: String, value: Int)

    // ===== Management =====

    /**
     * Check if a preference exists.
     *
     * @param key Preference key
     * @return true if preference exists
     */
    fun contains(key: String): Boolean

    /**
     * Remove a preference.
     *
     * @param key Preference key
     */
    fun remove(key: String)

    /**
     * Clear all preferences.
     */
    fun clear()
}
