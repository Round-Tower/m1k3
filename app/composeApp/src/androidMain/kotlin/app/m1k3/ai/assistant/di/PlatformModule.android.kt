package app.m1k3.ai.assistant.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.m1k3.ai.assistant.ai.BaseLlmEngine
import app.m1k3.ai.assistant.ai.LlamaCppEngine
import app.m1k3.ai.assistant.ai.download.HttpModelDownloadManager
import app.m1k3.ai.domain.ai.ModelDownloadManager
import app.m1k3.ai.assistant.ai.ondevice.AndroidOnDeviceAi
import app.m1k3.ai.assistant.ai.ondevice.LlamaCppFallbackEngine
import app.m1k3.ai.assistant.ai.ondevice.MlKitAvailabilityChecker
import app.m1k3.ai.assistant.ai.ondevice.MlKitGenAiEngine
import app.m1k3.ai.assistant.ai.ondevice.OnDeviceAi
import app.m1k3.ai.assistant.ai.ondevice.RealMlKitAvailabilityChecker
import app.m1k3.ai.assistant.ai.ondevice.RealMlKitGenAiEngine
import app.m1k3.ai.assistant.database.DatabaseFactory
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.platform.DateTimeProvider
import app.m1k3.ai.assistant.platform.DeviceInfoProvider
import app.m1k3.ai.assistant.platform.DeviceInfoProviderInterface
import app.m1k3.ai.assistant.platform.PreferencesStore
import app.m1k3.ai.domain.platform.DateTimeProviderInterface
import app.m1k3.ai.assistant.platform.PreferencesStoreInterface
import app.m1k3.ai.domain.chat.services.UnifiedPromptBuilder
import app.m1k3.ai.domain.tools.services.ToolRegistry
import app.m1k3.ai.assistant.tools.AndroidToolRegistry
import app.m1k3.ai.assistant.app.AndroidDatabaseInitializer
import app.m1k3.ai.assistant.app.IDatabaseInitializer
import app.m1k3.ai.assistant.app.InitializationViewModel
import app.m1k3.ai.assistant.app.LoggerAdapter
import app.m1k3.ai.assistant.utils.Logger
import app.m1k3.ai.assistant.rag.RAGManager
import app.m1k3.ai.assistant.memory.MemoryManager
import app.m1k3.ai.assistant.embedding.EmbeddingEngine
import app.m1k3.ai.assistant.chat.ChatScreenViewModel
import app.m1k3.ai.assistant.coding.CodeGenerationViewModel
import app.m1k3.ai.assistant.history.ConversationRepository
import app.m1k3.ai.assistant.history.SearchRepository
import app.m1k3.ai.assistant.history.ExportManager
import app.m1k3.ai.assistant.history.HistoryViewModel
import app.m1k3.ai.assistant.eco.EcoMetricsRepository
import app.m1k3.ai.assistant.eco.EcoStatsViewModel
import app.m1k3.ai.assistant.embedding.EmbeddingEngineManager
import app.m1k3.ai.assistant.embedding.EmbeddingEngineManagerImpl
import app.m1k3.ai.assistant.embedding.EmbeddingModelManager
import app.m1k3.ai.assistant.tts.AudioEffectsProcessor
import app.m1k3.ai.assistant.tts.AudioPlayer
import app.m1k3.ai.assistant.tts.KokoroTtsEngine
import app.m1k3.ai.domain.ai.AiCoreModelPreference
import app.m1k3.ai.domain.ai.LlmModel
import kotlinx.coroutines.launch
import app.m1k3.ai.domain.tts.TtsEngine
import app.m1k3.ai.domain.tts.Voice
import app.m1k3.ai.domain.usecases.chat.LlmOutputProcessor
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.*
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
     * DateTimeProvider
     *
     * Provides date/time context for prompts:
     * - Current time for context-aware greetings
     * - Locale for formatting
     */
    single<DateTimeProviderInterface> {
        DateTimeProvider()
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

    // ===== Embedding Engine =====

    /**
     * EmbeddingEngineManager
     *
     * Manages lifecycle and initialization of embedding engines.
     * Provides thread-safe singleton pattern with lazy model loading.
     *
     * Must call initialize() in MainActivity to load the ONNX model.
     */
    single<EmbeddingEngineManager> {
        EmbeddingEngineManagerImpl(get<Context>())
    }

    /**
     * EmbeddingEngine
     *
     * Provides text-to-vector embeddings for semantic search and RAG.
     * Uses EmbeddingModelManager to select between MiniLM (default) and Gemma (optional).
     *
     * Model selection:
     * - MiniLM-L6-v2 (384-dim, 80MB, built-in)
     * - Embedding Gemma (512-dim, 180MB, dynamic module)
     *
     * Note: Engine is created but NOT loaded. Call EmbeddingEngineManager.initialize() in MainActivity to load model.
     */
    single<EmbeddingEngine> {
        val manager = EmbeddingModelManager(get<Context>())
        manager.getEmbeddingEngine()
    }

    // ===== TTS Engine Layer =====

    /**
     * AudioEffectsProcessor
     *
     * DSP effects for TTS audio output (RadioChat, Intercom, etc.).
     * Stateless — singleton is fine.
     */
    single { AudioEffectsProcessor() }

    /**
     * AudioPlayer
     *
     * AudioTrack-based playback for synthesized audio.
     * 24kHz mono PCM float (Kokoro native format).
     */
    single { AudioPlayer() }

    /**
     * TtsEngine (Kokoro)
     *
     * On-device text-to-speech via ONNX Runtime.
     * INT8 quantized model (~90MB). Daniel voice default.
     *
     * Note: Call loadModel() before first synthesis.
     */
    single<TtsEngine> {
        KokoroTtsEngine(get<Context>())
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
     * Provides Gemini Nano / Gemma 4 on-device inference when available.
     * Supports Prompt API and AICore Developer Preview.
     *
     * Default: STABLE (Gemini Nano). Can be recreated with PREVIEW_SPEED
     * (Gemma 4 E2B) or PREVIEW_FULL (Gemma 4 E4B) via AndroidOnDeviceAi.
     */
    single<MlKitGenAiEngine> {
        RealMlKitGenAiEngine(get<Context>(), AiCoreModelPreference.STABLE)
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
            fallbackEngine = get<LlamaCppFallbackEngine>(),
            mlKitEngineFactory = { preference ->
                RealMlKitGenAiEngine(get<Context>(), preference)
            }
        )
    }

    // ===== Model Download Manager =====

    /**
     * HttpModelDownloadManager
     *
     * Downloads GGUF model files from HuggingFace to internal storage.
     * Used for large models (Gemma 4 E2B) that can't be bundled in APK.
     */
    single<ModelDownloadManager> {
        HttpModelDownloadManager(get<Context>())
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

    // ===== RAG & Memory Layer =====

    /**
     * RAGManager
     *
     * Provides RAG (Retrieval-Augmented Generation) capabilities.
     * Uses EmbeddingEngine for semantic search over knowledge base.
     */
    single<RAGManager> {
        RAGManager(
            database = get<MaDatabase>(),
            embeddingEngine = get<EmbeddingEngine>()
        )
    }

    // ===== MemoryManager (TODO: Not yet implemented) =====
    // MemoryManager is not registered yet because it requires additional dependencies.
    // ChatScreenViewModel uses getOrNull<MemoryManager>() which will return null.
    // TODO: Implement MemoryRepository with projectId scoping, then register MemoryManager

    // ===== Initialization Layer =====

    /**
     * AndroidDatabaseInitializer
     *
     * Handles database initialization and knowledge import.
     * Registered as singleton to ensure single initialization flow.
     */
    single<IDatabaseInitializer> {
        val logger = Logger.withTag("DatabaseInitializer")
        AndroidDatabaseInitializer(
            context = get<Context>(),
            logger = LoggerAdapter(logger)
        )
    }

    // ===== ViewModel Layer =====

    /**
     * InitializationViewModel
     *
     * Manages app initialization state (knowledge import).
     * Database is created by Koin and injected.
     * Registered as ViewModel for proper lifecycle management.
     */
    viewModel {
        InitializationViewModel(
            database = get<MaDatabase>(),
            databaseInitializer = get<IDatabaseInitializer>()
        )
    }

    /**
     * ChatScreenViewModel (optional projectId parameter)
     *
     * Main chat interface ViewModel with full dependency injection.
     * Accepts optional projectId via parametersOf(), defaults to "default".
     *
     * Usage:
     * ```kotlin
     * // With default projectId
     * val chatViewModel = koinViewModel<ChatScreenViewModel>()
     *
     * // With custom projectId
     * val chatViewModel = koinViewModel<ChatScreenViewModel> {
     *     parametersOf("my-project")
     * }
     * ```
     */
    viewModel { params ->
        val projectId = params.getOrNull<String>() ?: "default"
        val context = get<Context>()
        val ttsEngine = get<TtsEngine>()
        val audioPlayer = get<AudioPlayer>()

        ChatScreenViewModel(
            aiEngine = get<BaseLlmEngine>(),
            conversationRepo = get<ConversationRepository>(),
            ecoMetricsRepo = get<EcoMetricsRepository>(),
            database = get<MaDatabase>(),
            deviceInfo = get<DeviceInfoProviderInterface>(),
            preferences = get<PreferencesStoreInterface>(),
            projectId = projectId,
            memoryManager = getOrNull<MemoryManager>(),
            ragManager = get<RAGManager>(),
            toolRegistry = get<ToolRegistry>(),
            processLlmOutput = get<LlmOutputProcessor>(),
            dateTimeProvider = get<DateTimeProviderInterface>(),
            engineFactory = { model ->
                val downloadManager = get<ModelDownloadManager>()
                val overridePath = downloadManager.getModelPath(model.id)
                LlamaCppEngine(context, model, overrideModelPath = overridePath)
            },
            isModelDownloaded = { model ->
                // Bundled models (minRamGB == 0) are always "downloaded"
                if (model.minRamGB == 0) true
                else get<ModelDownloadManager>().isModelAvailable(model.id)
            },
            downloadModel = { model, onProgress ->
                val httpManager = get<ModelDownloadManager>() as HttpModelDownloadManager
                @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    httpManager.download(model).collect { progress ->
                        val state = when (progress) {
                            is app.m1k3.ai.assistant.ai.download.DownloadProgress.Starting ->
                                app.m1k3.ai.assistant.chat.ModelDownloadState.Starting(model.displayName)
                            is app.m1k3.ai.assistant.ai.download.DownloadProgress.InProgress ->
                                app.m1k3.ai.assistant.chat.ModelDownloadState.InProgress(
                                    modelName = model.displayName,
                                    progressPercent = progress.progressPercent,
                                    downloadedMB = progress.bytesDownloaded / 1_000_000,
                                    totalMB = progress.totalBytes / 1_000_000
                                )
                            is app.m1k3.ai.assistant.ai.download.DownloadProgress.Complete ->
                                app.m1k3.ai.assistant.chat.ModelDownloadState.Complete(model.displayName)
                            is app.m1k3.ai.assistant.ai.download.DownloadProgress.Failed ->
                                app.m1k3.ai.assistant.chat.ModelDownloadState.Failed(model.displayName, progress.error)
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onProgress(state)
                        }
                    }
                }
            },
            onSpeakText = { text ->
                if (text.isBlank()) return@ChatScreenViewModel
                if (!ttsEngine.isLoaded) ttsEngine.loadModel()
                val result = ttsEngine.synthesize(text, Voice.default)
                when (result) {
                    is app.m1k3.ai.domain.tts.TtsResult.Success -> {
                        audioPlayer.play(result.audio)
                    }
                    is app.m1k3.ai.domain.tts.TtsResult.Error -> {
                        throw RuntimeException("TTS failed: ${result.message}")
                    }
                }
            }
        )
    }

    /**
     * CodeGenerationViewModel
     *
     * Handles code generation features.
     * Requires Android Context for engine creation.
     */
    viewModel {
        CodeGenerationViewModel(
            context = get<Context>()
        )
    }

    /**
     * EcoStatsViewModel
     *
     * Manages environmental impact statistics and tracking.
     * Shows energy, water, and carbon savings from local AI inference.
     */
    viewModel {
        EcoStatsViewModel(
            repository = get<EcoMetricsRepository>()
        )
    }

    /**
     * HistoryViewModel
     *
     * Manages conversation history UI state.
     * Handles search, export, and conversation management.
     */
    viewModel {
        HistoryViewModel(
            conversationRepository = get<ConversationRepository>(),
            searchRepository = get<SearchRepository>(),
            exportManager = get<ExportManager>()
        )
    }
}
