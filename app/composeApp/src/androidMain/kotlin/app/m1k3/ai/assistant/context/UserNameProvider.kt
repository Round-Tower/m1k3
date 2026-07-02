package app.m1k3.ai.assistant.context

import android.accounts.AccountManager
import android.content.Context
import android.util.Log

/**
 * Gets the user's first name from the device Google account.
 *
 * No permission required — AccountManager.getAccounts() is available
 * without special permissions for the app's own account type.
 * We use a heuristic: take the first Google account's display name
 * and extract the first word as the given name.
 */
class UserNameProvider(private val context: Context) {

    companion object {
        private const val TAG = "UserNameProvider"
    }

    fun getUserFirstName(): String? {
        // Check user-set name first (overrides account detection)
        val prefs = context.getSharedPreferences("ma_ai_prefs", Context.MODE_PRIVATE)
        val storedName = prefs.getString("user_name", null)?.trim()
        if (!storedName.isNullOrBlank()) return storedName

        return try {
            val accountManager = AccountManager.get(context)
            // Google accounts
            val accounts = accountManager.getAccountsByType("com.google")
            val name = accounts.firstOrNull()?.name
                ?: accountManager.accounts.firstOrNull()?.name
                ?: return null

            // name might be "jane.doe@example.com" → extract "Jane"
            // or "Kevin Murphy" → extract "Kevin"
            val displayName = if (name.contains('@')) {
                name.substringBefore('@')
                    .replace('.', ' ')
                    .split(' ')
                    .firstOrNull()
                    ?.replaceFirstChar { it.uppercase() }
            } else {
                name.split(' ').firstOrNull()
            }

            displayName?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.d(TAG, "Name fetch failed (expected on some devices): ${e.message}")
            null
        }
    }
}
