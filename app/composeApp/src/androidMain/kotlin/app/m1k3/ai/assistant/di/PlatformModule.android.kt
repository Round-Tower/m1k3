package app.m1k3.ai.assistant.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.m1k3.ai.assistant.ai.BaseLlmEngine
import app.m1k3.ai.assistant.ai.LlamaCppEngine
import app.m1k3.ai.assistant.ai.ondevice.AndroidOnDeviceAi
import app.m1k3.ai.assistant.ai.ondevice.LlamaCppFallbackEngine
import app.m1k3.ai.assistant.ai.ondevice.MlKitAvailabilityChecker
import app.m1k3.ai.assistant.ai.ondevice.MlKitGenAiEngine
import app.m1k3.ai.assistant.ai.ondevice.OnDeviceAi
import app.m1k3.ai.assistant.ai.ondevice.RealMlKitAvailabilityChecker
import app.m1k3.ai.assistant.ai.ondevice.RealMlKitGenAiEngine
import app.m1k3.ai.assistant.database.DatabaseFactory
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.platform.DeviceInfoProvider
import app.m1k3.ai.assistant.platform.DeviceInfoProviderInterface
import app.m1k3.ai.assistant.platform.PreferencesStore
import app.m1k3.ai.assistant.platform.PreferencesStoreInterface
import app.m1k3.ai.domain.tools.services.ToolRegistry
import app.m1k3.ai.assistant.tools.AndroidToolRegistry
import org.koin.dsl.module

/**
 * Android platform module
 *
 * Provides Android-specific dependencies:
 * - SQLDelight Android driver
 * - Android Context
 * - AI Engines (OnDeviceAi, ML Kit GenAI, LlamaCpp fallback)
 */
actual val platformModule = module {
    /**
     * DatabaseFactory for Android
     *
     * Uses AndroidSqliteDriver with application context.
     */
    single {
        DatabaseFactory(
            driver = AndroidSqliteDriver(
                schema = MaDatabase.Schema,
                context = get<Context>(),
                name = "ma_ai.db"
            )
        )
    }

    // ===== Platform Abstractions =====

    /**
     * DeviceInfoProvider
     *
     * Provides device information for adaptive generation:
     * - RAM for token limit scaling
     * - Device model for debugging
     * - Battery level for power-aware generation
     */
    single<DeviceInfoProviderInterface> {
        DeviceInfoProvider(get<Context>())
    }

    /**
     * PreferencesStore
     *
     * SharedPreferences wrapper for feature flags and settings.
     * Thread-safe with reactive observation support.
     */
    single<PreferencesStoreInterface> {
        PreferencesStore(get<Context>())
    }

    // ===== AI Engine Layer =====

    /**
     * LlamaCppEngine (BaseLlmEngine implementation)
     *
     * Used as fallback when ML Kit GenAI is not available.
     * Also used directly for fine-grained LLM control.
     */
    single<BaseLlmEngine> {
        LlamaCppEngine(get<Context>())
    }

    /**
     * ML Kit GenAI Availability Checker
     *
     * Checks if Gemini Nano is available on this device.
     * Requirements: Android 14+, Pixel 8+/Samsung S24+, locked bootloader
     */
    single<MlKitAvailabilityChecker> {
        RealMlKitAvailabilityChecker(get<Context>())
    }

    /**
     * ML Kit GenAI Engine
     *
     * Provides Gemini Nano on-device inference when available.
     * Supports Prompt API (alpha) and Summarization API (beta).
     */
    single<MlKitGenAiEngine> {
        RealMlKitGenAiEngine(get<Context>())
    }

    /**
     * LlamaCpp Fallback Engine
     *
     * OnDeviceAi adapter wrapping BaseLlmEngine for older devices.
     * Used when ML Kit GenAI is not available.
     */
    single {
        LlamaCppFallbackEngine(get<BaseLlmEngine>())
    }

    /**
     * AndroidOnDeviceAi
     *
     * Main on-device AI implementation for Android.
     * Automatically uses ML Kit GenAI when available, falls back to LlamaCpp.
     *
     * Usage:
     * ```kotlin
     * val ai: OnDeviceAi = get()
     * when (ai.checkAvailability()) {
     *     is AiAvailability.Available -> // Using Gemini Nano
     *     is AiAvailability.Fallback -> // Using SmolLM2-135M
     * }
     * ```
     */
    single<OnDeviceAi> {
        AndroidOnDeviceAi(
            mlKitChecker = get<MlKitAvailabilityChecker>(),
            mlKitEngine = get<MlKitGenAiEngine>(),
            fallbackEngine = get<LlamaCppFallbackEngine>()
        )
    }

    // ===== Tool Calling Infrastructure =====

    /**
     * Android Tool Registry
     *
     * Registers Android-specific tools:
     * - Device info (battery, time)
     * - System controls (flashlight)
     * - App launchers (camera, browser, settings)
     */
    single<ToolRegistry> {
        AndroidToolRegistry(context = get<Context>())
    }
}
