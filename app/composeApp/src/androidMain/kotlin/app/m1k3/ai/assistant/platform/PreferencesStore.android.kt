package app.m1k3.ai.assistant.platform

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android implementation of PreferencesStore using SharedPreferences.
 *
 * Provides thread-safe access to app preferences with reactive observation support.
 */
actual class PreferencesStore(
    context: Context,
    name: String = "ma_ai_prefs"
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        name,
        Context.MODE_PRIVATE
    )

    // ===== Boolean =====

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        return prefs.getBoolean(key, default)
    }

    actual fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    actual fun observeBoolean(key: String, default: Boolean): Flow<Boolean> = callbackFlow {
        // Emit initial value
        trySend(getBoolean(key, default))

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                trySend(getBoolean(key, default))
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)

        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // ===== String =====

    actual fun getString(key: String, default: String?): String? {
        return prefs.getString(key, default)
    }

    actual fun setString(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, value).apply()
        }
    }

    // ===== Int =====

    actual fun getInt(key: String, default: Int): Int {
        return prefs.getInt(key, default)
    }

    actual fun setInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    // ===== Operations =====

    actual fun contains(key: String): Boolean {
        return prefs.contains(key)
    }

    actual fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    actual fun clear() {
        prefs.edit().clear().apply()
    }
}
