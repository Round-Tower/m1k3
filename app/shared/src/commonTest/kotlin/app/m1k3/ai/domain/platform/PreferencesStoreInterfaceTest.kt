package app.m1k3.ai.domain.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for PreferencesStoreInterface.
 *
 * Uses a mock implementation to verify the interface contract.
 */
class PreferencesStoreInterfaceTest {

    private class MockPreferencesStore : PreferencesStoreInterface {
        private val booleanPrefs = mutableMapOf<String, Boolean>()
        private val stringPrefs = mutableMapOf<String, String?>()
        private val intPrefs = mutableMapOf<String, Int>()

        override fun getBoolean(key: String, default: Boolean): Boolean =
            booleanPrefs[key] ?: default

        override fun setBoolean(key: String, value: Boolean) {
            booleanPrefs[key] = value
        }

        override fun observeBoolean(key: String, default: Boolean): Flow<Boolean> =
            flowOf(getBoolean(key, default))

        override fun getString(key: String, default: String?): String? =
            if (key in stringPrefs) stringPrefs[key] else default

        override fun setString(key: String, value: String?) {
            if (value != null) {
                stringPrefs[key] = value
            } else {
                stringPrefs.remove(key)
            }
        }

        override fun getInt(key: String, default: Int): Int =
            intPrefs[key] ?: default

        override fun setInt(key: String, value: Int) {
            intPrefs[key] = value
        }

        override fun remove(key: String) {
            booleanPrefs.remove(key)
            stringPrefs.remove(key)
            intPrefs.remove(key)
        }

        override fun clear() {
            booleanPrefs.clear()
            stringPrefs.clear()
            intPrefs.clear()
        }

        override fun contains(key: String): Boolean =
            key in booleanPrefs || key in stringPrefs || key in intPrefs
    }

    @Test
    fun `boolean operations work correctly`() {
        val store = MockPreferencesStore()

        assertFalse(store.getBoolean("test_key", false))

        store.setBoolean("test_key", true)
        assertTrue(store.getBoolean("test_key", false))
    }

    @Test
    fun `string operations work correctly`() {
        val store = MockPreferencesStore()

        assertNull(store.getString("test_key", null))
        assertEquals("default", store.getString("test_key", "default"))

        store.setString("test_key", "value")
        assertEquals("value", store.getString("test_key", null))
    }

    @Test
    fun `setString with null removes preference`() {
        val store = MockPreferencesStore()

        store.setString("test_key", "value")
        assertTrue(store.contains("test_key"))

        store.setString("test_key", null)
        assertFalse(store.contains("test_key"))
    }

    @Test
    fun `int operations work correctly`() {
        val store = MockPreferencesStore()

        assertEquals(0, store.getInt("test_key", 0))

        store.setInt("test_key", 42)
        assertEquals(42, store.getInt("test_key", 0))
    }

    @Test
    fun `remove removes key`() {
        val store = MockPreferencesStore()

        store.setBoolean("test_key", true)
        assertTrue(store.contains("test_key"))

        store.remove("test_key")
        assertFalse(store.contains("test_key"))
    }

    @Test
    fun `clear removes all keys`() {
        val store = MockPreferencesStore()

        store.setBoolean("bool_key", true)
        store.setString("string_key", "value")
        store.setInt("int_key", 42)

        store.clear()

        assertFalse(store.contains("bool_key"))
        assertFalse(store.contains("string_key"))
        assertFalse(store.contains("int_key"))
    }

    @Test
    fun `observeBoolean returns flow`() = runTest {
        val store = MockPreferencesStore()
        store.setBoolean("test_key", true)

        val value = store.observeBoolean("test_key", false).first()

        assertTrue(value)
    }
}
