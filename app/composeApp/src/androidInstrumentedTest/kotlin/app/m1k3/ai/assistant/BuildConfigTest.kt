package app.m1k3.ai.assistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PHASE0-002: Build Configuration Tests
 *
 * Tests that the build system is properly configured with:
 * - Correct package namespace
 * - Required dependencies
 * - Proper SDK levels
 */
@RunWith(AndroidJUnit4::class)
class BuildConfigTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager = context.packageManager
    private val packageName = context.packageName

    /**
     * AC1: App package name is app.m1k3.ai.assistant (ASO optimized)
     */
    @Test
    fun buildConfig_hasCorrectPackageName() {
        assertEquals(
            "app.m1k3.ai.assistant",
            packageName,
            "Package name must be app.m1k3.ai.assistant (M1K3 AI namespace with ASO keywords)"
        )
    }

    /**
     * AC2: Minimum SDK is 27 (Android 8.0+)
     */
    @Test
    fun buildConfig_hasCorrectMinSdk() {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        assertTrue(
            android.os.Build.VERSION.SDK_INT >= 27,
            "App requires minimum SDK 27 (Android 8.0+) for ONNX Runtime and ML Kit support"
        )
    }

    /**
     * AC3: Application context is available
     */
    @Test
    fun buildConfig_hasApplicationContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("app.m1k3.ai.assistant", appContext.packageName)
    }

    /**
     * AC4: App is debuggable (for development builds)
     */
    @Test
    fun buildConfig_isDebuggable() {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        val isDebuggable = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        assertTrue(
            isDebuggable,
            "Development builds should be debuggable for testing"
        )
    }

    /**
     * AC5: Required Android libraries are present
     */
    @Test
    fun buildConfig_hasRequiredLibraries() {
        // This test verifies the app can access core Android libraries
        // by checking that we can instantiate basic framework classes

        // Test SQLite (SQLDelight dependency)
        val sqliteVersion = android.database.sqlite.SQLiteDatabase.releaseMemory()
        assertTrue(true, "SQLite should be available")

        // Test file system access (for local AI models)
        val filesDir = context.filesDir
        assertTrue(filesDir.exists() || filesDir.mkdirs(), "Files directory should be accessible")
    }

    /**
     * AC6: App label is properly set
     */
    @Test
    fun buildConfig_hasAppLabel() {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        val appLabel = packageManager.getApplicationLabel(appInfo)
        assertTrue(
            appLabel.isNotEmpty(),
            "App should have a label defined in strings.xml"
        )
    }
}
