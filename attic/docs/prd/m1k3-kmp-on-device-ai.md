# M1K3: On-Device AI with Kotlin Multiplatform + Compose

## Implementation Status

**Last Updated:** 2025-12-22
**Status:** 🟢 Phase 2 Complete (Android implementation done)

### Completed ✅

| Component | Location | Notes |
|-----------|----------|-------|
| `OnDeviceAi` interface | `composeApp/src/commonMain/.../ai/ondevice/OnDeviceAi.kt` | Full interface with docs |
| `AiAvailability` sealed class | `composeApp/src/commonMain/.../ai/ondevice/AiAvailability.kt` | 4 states + UnavailableReason |
| `AiResult<T>` sealed class | `composeApp/src/commonMain/.../ai/ondevice/AiResult.kt` | Functional operators |
| `AiErrorCode` enum | `composeApp/src/commonMain/.../ai/ondevice/AiErrorCode.kt` | 8 error types |
| `SummaryStyle` enum | `composeApp/src/commonMain/.../ai/ondevice/SummaryStyle.kt` | BRIEF, BULLETS, DETAILED |
| `MockOnDeviceAi` | `composeApp/src/commonTest/.../ai/ondevice/MockOnDeviceAi.kt` | Full mock for testing |
| `AndroidOnDeviceAi` | `composeApp/src/androidMain/.../ai/ondevice/AndroidOnDeviceAi.kt` | ML Kit → LlamaCpp fallback |
| `LlamaCppFallbackEngine` | `composeApp/src/androidMain/.../ai/ondevice/LlamaCppFallbackEngine.kt` | BaseLlmEngine adapter |
| `MlKitAvailabilityChecker` | `composeApp/src/androidMain/.../ai/ondevice/MlKitAvailabilityChecker.kt` | Interface + stub |
| `MlKitGenAiEngine` | `composeApp/src/androidMain/.../ai/ondevice/MlKitGenAiEngine.kt` | Interface + stub |
| Unit Tests (commonMain) | `composeApp/src/commonTest/.../ai/ondevice/*Test.kt` | 70 tests, 100% pass |
| Unit Tests (androidMain) | `composeApp/src/androidUnitTest/.../ai/ondevice/*Test.kt` | 44 tests, 100% pass |

### In Progress 🔄

| Component | Status | Next Step |
|-----------|--------|-----------|
| `IosOnDeviceAi` | Not started | Swift wrapper + cinterop |
| ML Kit GenAI integration | Stub ready | Integrate when SDK stable |
| App DI integration | Not started | Wire into Koin |

### Implementation Notes

**Naming Convention:** Implementation uses `Ai` prefix (e.g., `AiResult`) instead of `AI` shown in PRD examples. This follows Kotlin naming conventions more closely.

**Package Location:** `app.m1k3.ai.assistant.ai.ondevice` (different from PRD's `com.m1k3.ai`)

**Relationship with BaseLlmEngine:** `OnDeviceAi` wraps platform-native AI with availability awareness. `BaseLlmEngine` provides direct LLM inference for fallback engines like LlamaCpp.

**Thread Safety:** `AndroidOnDeviceAi` uses `AtomicReference<EngineState>` for lock-free reads in generate/summarize. Mutex only protects initialization. `release()` uses a CAS loop to prevent double-release in concurrent scenarios.

**Android Architecture:**
```
AndroidOnDeviceAi (main entry point)
├── MlKitGenAiEngine (Gemini Nano - stub until SDK stable)
└── LlamaCppFallbackEngine (adapter for BaseLlmEngine)
    └── LlamaCppEngine (actual inference via Llamatik)
```

---

## Architecture Overview

This guide implements a unified on-device AI abstraction for KMP that leverages:
- **iOS**: Apple Foundation Models framework (iOS 26+)
- **Android**: ML Kit GenAI APIs with Gemini Nano

The key challenge: Apple's Foundation Models is Swift-only with async/await and macros, while ML Kit is Kotlin-native. We'll use the expect/actual pattern with a Swift wrapper for iOS.

```
┌─────────────────────────────────────────────────────────┐
│                    Compose Multiplatform UI              │
├─────────────────────────────────────────────────────────┤
│                  commonMain (Shared Logic)               │
│  ┌─────────────────────────────────────────────────┐    │
│  │         OnDeviceAI (expect interface)            │    │
│  │  - checkAvailability()                           │    │
│  │  - generateText(prompt)                          │    │
│  │  - generateTextStream(prompt) → Flow<String>     │    │
│  │  - summarize(text)                               │    │
│  └─────────────────────────────────────────────────┘    │
├──────────────────────┬──────────────────────────────────┤
│      androidMain     │           iosMain                │
│  ┌────────────────┐  │  ┌────────────────────────────┐  │
│  │ ML Kit GenAI   │  │  │ Kotlin wrapper calling     │  │
│  │ Prompt API     │  │  │ Swift via cinterop         │  │
│  │ (Kotlin)       │  │  │                            │  │
│  └────────────────┘  │  └────────────────────────────┘  │
│                      │              ↓                   │
│                      │  ┌────────────────────────────┐  │
│                      │  │ Swift Framework            │  │
│                      │  │ (FoundationModelsWrapper)  │  │
│                      │  │ @objc exposed to Kotlin    │  │
│                      │  └────────────────────────────┘  │
└──────────────────────┴──────────────────────────────────┘
```

---

## Project Structure

```
m1k3/
├── build.gradle.kts
├── shared/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/
│       │   └── com/m1k3/ai/
│       │       ├── OnDeviceAI.kt              # expect declarations
│       │       ├── AIResponse.kt              # shared data classes
│       │       └── AIAvailability.kt          # availability states
│       ├── androidMain/kotlin/
│       │   └── com/m1k3/ai/
│       │       └── OnDeviceAI.android.kt      # ML Kit implementation
│       └── iosMain/kotlin/
│           └── com/m1k3/ai/
│               └── OnDeviceAI.ios.kt          # cinterop to Swift
├── iosApp/
│   └── iosApp/
│       └── Swift/
│           └── FoundationModelsWrapper.swift  # Swift wrapper
└── composeApp/                                 # Compose UI
```

---

## Part 1: Common Interface (commonMain)

### AIAvailability.kt

```kotlin
package com.m1k3.ai

sealed class AIAvailability {
    data object Available : AIAvailability()
    data object Downloading : AIAvailability()
    data class Unavailable(val reason: UnavailableReason) : AIAvailability()
    
    enum class UnavailableReason {
        DEVICE_NOT_SUPPORTED,
        MODEL_NOT_READY,
        AI_DISABLED,
        BACKGROUND_USE_BLOCKED,
        QUOTA_EXCEEDED,
        UNKNOWN
    }
}
```

### AIResponse.kt

```kotlin
package com.m1k3.ai

sealed class AIResult<out T> {
    data class Success<T>(val data: T) : AIResult<T>()
    data class Error(val code: AIErrorCode, val message: String) : AIResult<Nothing>()
    
    inline fun <R> map(transform: (T) -> R): AIResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }
    
    fun getOrNull(): T? = (this as? Success)?.data
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw AIException(code, message)
    }
}

enum class AIErrorCode {
    UNAVAILABLE,
    BUSY,
    QUOTA_EXCEEDED,
    CONTENT_FILTERED,
    INPUT_TOO_LONG,
    BACKGROUND_BLOCKED,
    CANCELLED,
    UNKNOWN
}

class AIException(val code: AIErrorCode, message: String) : Exception(message)

data class GenerationConfig(
    val maxOutputTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val stopSequences: List<String> = emptyList()
)
```

### OnDeviceAI.kt (expect declarations)

```kotlin
package com.m1k3.ai

import kotlinx.coroutines.flow.Flow

expect class OnDeviceAI {
    
    /**
     * Check if on-device AI is available.
     * Call this before showing AI-related UI.
     */
    suspend fun checkAvailability(): AIAvailability
    
    /**
     * Request model download if status is Downloadable.
     * Only applicable on Android; iOS downloads automatically.
     */
    suspend fun downloadModelIfNeeded(): AIResult<Unit>
    
    /**
     * Generate text from a prompt (non-streaming).
     */
    suspend fun generateText(
        prompt: String,
        config: GenerationConfig = GenerationConfig()
    ): AIResult<String>
    
    /**
     * Generate text with streaming response.
     * Emits partial results as they're generated.
     */
    fun generateTextStream(
        prompt: String,
        config: GenerationConfig = GenerationConfig()
    ): Flow<AIResult<String>>
    
    /**
     * Summarize text content.
     * On Android: Uses dedicated Summarization API.
     * On iOS: Uses Foundation Models with summarization prompt.
     */
    suspend fun summarize(
        text: String,
        style: SummaryStyle = SummaryStyle.BRIEF
    ): AIResult<String>
    
    /**
     * Get the model name/version for debugging.
     */
    suspend fun getModelInfo(): String
    
    companion object {
        fun create(): OnDeviceAI
    }
}

enum class SummaryStyle {
    BRIEF,      // 1-2 sentences
    BULLETS,    // Bullet points
    DETAILED    // Longer summary
}
```

---

## Part 2: Android Implementation (androidMain)

### build.gradle.kts (shared module)

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
        
        // Configure cinterop for Foundation Models wrapper
        iosTarget.compilations.getByName("main") {
            cinterops {
                create("FoundationModelsWrapper") {
                    defFile("src/iosMain/cinterop/FoundationModelsWrapper.def")
                    includeDirs("${project.rootDir}/iosApp/iosApp/Swift")
                }
            }
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        
        androidMain.dependencies {
            // ML Kit GenAI APIs
            implementation("com.google.mlkit:genai-prompt:1.0.0-alpha1")
            implementation("com.google.mlkit:genai-summarization:1.0.0-beta1")
            implementation("com.google.mlkit:genai-common:1.0.0-beta1")
            
            implementation(libs.kotlinx.coroutines.android)
        }
        
        iosMain.dependencies {
            // iOS uses Swift interop, no additional deps
        }
    }
}

android {
    namespace = "com.m1k3.ai"
    compileSdk = 35
    
    defaultConfig {
        minSdk = 26  // ML Kit GenAI requires API 26+
    }
}
```

### OnDeviceAI.android.kt

```kotlin
package com.m1k3.ai

import android.content.Context
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.GenerativeModelConfig
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.summarization.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual class OnDeviceAI private constructor(
    private val context: Context
) {
    private val generativeModel: GenerativeModel by lazy {
        Generation.getClient()
    }
    
    actual suspend fun checkAvailability(): AIAvailability {
        return suspendCancellableCoroutine { cont ->
            generativeModel.checkStatus()
                .addOnSuccessListener { status ->
                    val availability = when (status) {
                        FeatureStatus.AVAILABLE -> AIAvailability.Available
                        FeatureStatus.DOWNLOADING -> AIAvailability.Downloading
                        FeatureStatus.DOWNLOADABLE -> AIAvailability.Downloading // Trigger download
                        FeatureStatus.UNAVAILABLE -> AIAvailability.Unavailable(
                            AIAvailability.UnavailableReason.DEVICE_NOT_SUPPORTED
                        )
                        else -> AIAvailability.Unavailable(
                            AIAvailability.UnavailableReason.UNKNOWN
                        )
                    }
                    cont.resume(availability)
                }
                .addOnFailureListener { e ->
                    cont.resume(
                        AIAvailability.Unavailable(AIAvailability.UnavailableReason.UNKNOWN)
                    )
                }
        }
    }
    
    actual suspend fun downloadModelIfNeeded(): AIResult<Unit> {
        return suspendCancellableCoroutine { cont ->
            generativeModel.download(object : DownloadCallback {
                override fun onDownloadStarted(bytesDownloaded: Long) {
                    // Could emit progress here
                }
                
                override fun onDownloadProgress(bytesDownloaded: Long) {
                    // Progress tracking
                }
                
                override fun onDownloadCompleted() {
                    cont.resume(AIResult.Success(Unit))
                }
                
                override fun onDownloadFailed(e: Exception) {
                    cont.resume(AIResult.Error(
                        AIErrorCode.UNAVAILABLE,
                        e.message ?: "Download failed"
                    ))
                }
            })
        }
    }
    
    actual suspend fun generateText(
        prompt: String,
        config: GenerationConfig
    ): AIResult<String> {
        return try {
            val modelConfig = GenerativeModelConfig.builder()
                .setTemperature(config.temperature)
                .setTopK(config.topK)
                .setMaxOutputTokens(config.maxOutputTokens)
                .build()
            
            val response = suspendCancellableCoroutine<String> { cont ->
                generativeModel.generateContent(prompt, modelConfig)
                    .addOnSuccessListener { result ->
                        cont.resume(result.text ?: "")
                    }
                    .addOnFailureListener { e ->
                        cont.resumeWithException(e)
                    }
            }
            AIResult.Success(response)
        } catch (e: Exception) {
            mapException(e)
        }
    }
    
    actual fun generateTextStream(
        prompt: String,
        config: GenerationConfig
    ): Flow<AIResult<String>> = callbackFlow {
        val modelConfig = GenerativeModelConfig.builder()
            .setTemperature(config.temperature)
            .setTopK(config.topK)
            .setMaxOutputTokens(config.maxOutputTokens)
            .build()
        
        val streamingCallback = object : com.google.mlkit.genai.prompt.StreamingCallback {
            override fun onPartialResult(partialResult: String) {
                trySend(AIResult.Success(partialResult))
            }
            
            override fun onComplete(fullResult: String) {
                // Final result already sent via partial
                close()
            }
            
            override fun onError(e: Exception) {
                trySend(mapException(e))
                close()
            }
        }
        
        generativeModel.generateContentStream(prompt, modelConfig, streamingCallback)
        
        awaitClose { 
            // Cleanup if needed
        }
    }
    
    actual suspend fun summarize(
        text: String,
        style: SummaryStyle
    ): AIResult<String> {
        return try {
            val outputType = when (style) {
                SummaryStyle.BRIEF -> OutputType.ONE_BULLET
                SummaryStyle.BULLETS -> OutputType.THREE_BULLETS
                SummaryStyle.DETAILED -> OutputType.PARAGRAPH
            }
            
            val options = SummarizerOptions.builder(context)
                .setInputType(InputType.ARTICLE)
                .setOutputType(outputType)
                .setLanguage(Language.ENGLISH)
                .build()
            
            val summarizer = Summarization.getClient(options)
            
            // Check availability first
            val status = suspendCancellableCoroutine<Int> { cont ->
                summarizer.checkFeatureStatus()
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            
            if (status != FeatureStatus.AVAILABLE) {
                return AIResult.Error(
                    AIErrorCode.UNAVAILABLE,
                    "Summarization not available on this device"
                )
            }
            
            val request = SummarizationRequest.builder(text).build()
            
            val result = suspendCancellableCoroutine<String> { cont ->
                summarizer.runInference(request) { summary ->
                    cont.resume(summary)
                }
            }
            
            AIResult.Success(result)
        } catch (e: Exception) {
            mapException(e)
        }
    }
    
    actual suspend fun getModelInfo(): String {
        return try {
            val baseModel = generativeModel.baseModelName
            "Gemini Nano ($baseModel)"
        } catch (e: Exception) {
            "Gemini Nano (unknown version)"
        }
    }
    
    private fun mapException(e: Exception): AIResult.Error {
        val message = e.message ?: "Unknown error"
        val code = when {
            message.contains("BUSY", ignoreCase = true) -> AIErrorCode.BUSY
            message.contains("QUOTA", ignoreCase = true) -> AIErrorCode.QUOTA_EXCEEDED
            message.contains("BACKGROUND", ignoreCase = true) -> AIErrorCode.BACKGROUND_BLOCKED
            message.contains("CONTENT", ignoreCase = true) -> AIErrorCode.CONTENT_FILTERED
            message.contains("INPUT", ignoreCase = true) -> AIErrorCode.INPUT_TOO_LONG
            else -> AIErrorCode.UNKNOWN
        }
        return AIResult.Error(code, message)
    }
    
    actual companion object {
        private var instance: OnDeviceAI? = null
        private lateinit var appContext: Context
        
        fun initialize(context: Context) {
            appContext = context.applicationContext
        }
        
        actual fun create(): OnDeviceAI {
            return instance ?: OnDeviceAI(appContext).also { instance = it }
        }
    }
}
```

---

## Part 3: iOS Implementation

### Step 1: Swift Wrapper (FoundationModelsWrapper.swift)

This must be placed in your Xcode project and compiled as part of the iOS app.

```swift
import Foundation
import FoundationModels

/// Callback protocol for Kotlin to receive results
@objc public protocol AICompletionCallback: AnyObject {
    func onSuccess(_ result: String)
    func onError(_ code: Int, _ message: String)
}

/// Streaming callback for token-by-token output
@objc public protocol AIStreamCallback: AnyObject {
    func onToken(_ token: String)
    func onComplete(_ fullResult: String)
    func onError(_ code: Int, _ message: String)
}

/// Main wrapper class exposed to Kotlin via @objc
@objc public class FoundationModelsWrapper: NSObject {
    
    private var session: LanguageModelSession?
    
    @objc public override init() {
        super.init()
    }
    
    // MARK: - Availability
    
    @objc public static func checkAvailability() -> Int {
        switch SystemLanguageModel.default.availability {
        case .available:
            return 0  // Available
        case .unavailable(let reason):
            switch reason {
            case .appleIntelligenceNotEnabled:
                return 2  // AI_DISABLED
            case .deviceNotEligible:
                return 1  // DEVICE_NOT_SUPPORTED
            case .modelNotReady:
                return 3  // MODEL_NOT_READY
            @unknown default:
                return 99 // UNKNOWN
            }
        @unknown default:
            return 99
        }
    }
    
    @objc public static func getModelInfo() -> String {
        return "Apple Foundation Models (~3B on-device)"
    }
    
    // MARK: - Text Generation
    
    @objc public func generateText(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: AICompletionCallback
    ) {
        Task {
            do {
                let session = LanguageModelSession()
                self.session = session
                
                let response = try await session.respond(
                    to: prompt,
                    options: .init(temperature: Double(temperature))
                )
                
                await MainActor.run {
                    callback.onSuccess(response.content)
                }
            } catch {
                await MainActor.run {
                    let (code, message) = self.mapError(error)
                    callback.onError(code, message)
                }
            }
        }
    }
    
    @objc public func generateTextStream(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: AIStreamCallback
    ) {
        Task {
            do {
                let session = LanguageModelSession()
                self.session = session
                
                var fullResult = ""
                
                for try await partialResponse in session.streamResponse(
                    to: prompt,
                    options: .init(temperature: Double(temperature))
                ) {
                    let token = partialResponse.content
                    fullResult = token  // Cumulative
                    await MainActor.run {
                        callback.onToken(token)
                    }
                }
                
                await MainActor.run {
                    callback.onComplete(fullResult)
                }
            } catch {
                await MainActor.run {
                    let (code, message) = self.mapError(error)
                    callback.onError(code, message)
                }
            }
        }
    }
    
    // MARK: - Summarization
    
    @objc public func summarize(
        text: String,
        style: Int,  // 0=brief, 1=bullets, 2=detailed
        callback: AICompletionCallback
    ) {
        let stylePrompt: String
        switch style {
        case 0:
            stylePrompt = "Summarize the following in 1-2 sentences:\n\n"
        case 1:
            stylePrompt = "Summarize the following as 3-5 bullet points:\n\n"
        default:
            stylePrompt = "Provide a detailed summary of the following:\n\n"
        }
        
        let fullPrompt = stylePrompt + text
        
        generateText(
            prompt: fullPrompt,
            maxTokens: 512,
            temperature: 0.3,
            callback: callback
        )
    }
    
    // MARK: - Structured Output (Bonus: Using @Generable)
    
    /// For structured outputs, define Swift structs with @Generable
    /// and expose specific generation methods
    
    @objc public func extractEntities(
        from text: String,
        callback: AICompletionCallback
    ) {
        Task {
            do {
                let session = LanguageModelSession()
                
                // Using the content tagging adapter for entity extraction
                let response = try await session.respond(
                    to: "Extract key entities (people, places, organizations) from: \(text)\n\nReturn as JSON array.",
                    options: .init(temperature: 0.1)
                )
                
                await MainActor.run {
                    callback.onSuccess(response.content)
                }
            } catch {
                await MainActor.run {
                    let (code, message) = self.mapError(error)
                    callback.onError(code, message)
                }
            }
        }
    }
    
    // MARK: - Cancel
    
    @objc public func cancel() {
        session = nil  // Session cleanup
    }
    
    // MARK: - Error Mapping
    
    private func mapError(_ error: Error) -> (Int, String) {
        let message = error.localizedDescription
        
        // Map Foundation Models errors to our codes
        if message.contains("content") || message.contains("safety") {
            return (4, message)  // CONTENT_FILTERED
        } else if message.contains("busy") || message.contains("overloaded") {
            return (1, message)  // BUSY
        } else if message.contains("cancelled") {
            return (6, message)  // CANCELLED
        } else {
            return (99, message) // UNKNOWN
        }
    }
}
```

### Step 2: Bridging Header (for Obj-C visibility)

Create `FoundationModelsWrapper-Bridging-Header.h`:

```objc
// This header exposes Swift to Objective-C
// The Swift file uses @objc so it's auto-exported
```

Ensure your Xcode project has "Defines Module" set to YES.

### Step 3: cinterop Definition (iosMain/cinterop/FoundationModelsWrapper.def)

```
language = Objective-C
headers = FoundationModelsWrapper-Swift.h

# Path to your compiled framework
libraryPaths = ../iosApp/build/Release-iphoneos
linkerOpts = -framework FoundationModels
```

### Step 4: Kotlin iOS Implementation (OnDeviceAI.ios.kt)

```kotlin
package com.m1k3.ai

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*
import FoundationModelsWrapper.*  // From cinterop
import kotlin.coroutines.resume

actual class OnDeviceAI private constructor() {
    
    private val wrapper = FoundationModelsWrapper()
    
    actual suspend fun checkAvailability(): AIAvailability {
        val code = FoundationModelsWrapper.checkAvailability()
        return when (code) {
            0 -> AIAvailability.Available
            1 -> AIAvailability.Unavailable(AIAvailability.UnavailableReason.DEVICE_NOT_SUPPORTED)
            2 -> AIAvailability.Unavailable(AIAvailability.UnavailableReason.AI_DISABLED)
            3 -> AIAvailability.Unavailable(AIAvailability.UnavailableReason.MODEL_NOT_READY)
            else -> AIAvailability.Unavailable(AIAvailability.UnavailableReason.UNKNOWN)
        }
    }
    
    actual suspend fun downloadModelIfNeeded(): AIResult<Unit> {
        // iOS handles model availability automatically
        // Just check if available
        return when (checkAvailability()) {
            is AIAvailability.Available -> AIResult.Success(Unit)
            else -> AIResult.Error(
                AIErrorCode.UNAVAILABLE,
                "Model not available on this device"
            )
        }
    }
    
    actual suspend fun generateText(
        prompt: String,
        config: GenerationConfig
    ): AIResult<String> = suspendCancellableCoroutine { cont ->
        
        val callback = object : NSObject(), AICompletionCallbackProtocol {
            override fun onSuccess(result: String) {
                cont.resume(AIResult.Success(result))
            }
            
            override fun onErrorWithCode(code: Int, message: String) {
                cont.resume(AIResult.Error(mapErrorCode(code), message))
            }
        }
        
        wrapper.generateTextWithPrompt(
            prompt = prompt,
            maxTokens = config.maxOutputTokens,
            temperature = config.temperature,
            callback = callback
        )
        
        cont.invokeOnCancellation {
            wrapper.cancel()
        }
    }
    
    actual fun generateTextStream(
        prompt: String,
        config: GenerationConfig
    ): Flow<AIResult<String>> = callbackFlow {
        
        val callback = object : NSObject(), AIStreamCallbackProtocol {
            override fun onToken(token: String) {
                trySend(AIResult.Success(token))
            }
            
            override fun onComplete(fullResult: String) {
                // Final complete signal
                close()
            }
            
            override fun onErrorWithCode(code: Int, message: String) {
                trySend(AIResult.Error(mapErrorCode(code), message))
                close()
            }
        }
        
        wrapper.generateTextStreamWithPrompt(
            prompt = prompt,
            maxTokens = config.maxOutputTokens,
            temperature = config.temperature,
            callback = callback
        )
        
        awaitClose {
            wrapper.cancel()
        }
    }
    
    actual suspend fun summarize(
        text: String,
        style: SummaryStyle
    ): AIResult<String> = suspendCancellableCoroutine { cont ->
        
        val styleCode = when (style) {
            SummaryStyle.BRIEF -> 0
            SummaryStyle.BULLETS -> 1
            SummaryStyle.DETAILED -> 2
        }
        
        val callback = object : NSObject(), AICompletionCallbackProtocol {
            override fun onSuccess(result: String) {
                cont.resume(AIResult.Success(result))
            }
            
            override fun onErrorWithCode(code: Int, message: String) {
                cont.resume(AIResult.Error(mapErrorCode(code), message))
            }
        }
        
        wrapper.summarizeWithText(
            text = text,
            style = styleCode,
            callback = callback
        )
        
        cont.invokeOnCancellation {
            wrapper.cancel()
        }
    }
    
    actual suspend fun getModelInfo(): String {
        return FoundationModelsWrapper.getModelInfo()
    }
    
    private fun mapErrorCode(code: Int): AIErrorCode = when (code) {
        0 -> AIErrorCode.UNAVAILABLE
        1 -> AIErrorCode.BUSY
        2 -> AIErrorCode.QUOTA_EXCEEDED
        4 -> AIErrorCode.CONTENT_FILTERED
        5 -> AIErrorCode.INPUT_TOO_LONG
        6 -> AIErrorCode.CANCELLED
        else -> AIErrorCode.UNKNOWN
    }
    
    actual companion object {
        private var instance: OnDeviceAI? = null
        
        actual fun create(): OnDeviceAI {
            return instance ?: OnDeviceAI().also { instance = it }
        }
    }
}
```

---

## Part 4: Compose UI Integration

### AIViewModel.kt (shared)

```kotlin
package com.m1k3.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m1k3.ai.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AIViewModel : ViewModel() {
    
    private val ai = OnDeviceAI.create()
    
    private val _uiState = MutableStateFlow(AIUiState())
    val uiState: StateFlow<AIUiState> = _uiState.asStateFlow()
    
    init {
        checkAvailability()
    }
    
    private fun checkAvailability() {
        viewModelScope.launch {
            val availability = ai.checkAvailability()
            _uiState.update { it.copy(availability = availability) }
            
            // Auto-download on Android if needed
            if (availability == AIAvailability.Downloading) {
                ai.downloadModelIfNeeded()
                // Re-check after download
                val newAvailability = ai.checkAvailability()
                _uiState.update { it.copy(availability = newAvailability) }
            }
        }
    }
    
    fun generateResponse(prompt: String, streaming: Boolean = true) {
        if (_uiState.value.availability != AIAvailability.Available) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, response = "", error = null) }
            
            if (streaming) {
                ai.generateTextStream(prompt)
                    .collect { result ->
                        when (result) {
                            is AIResult.Success -> {
                                _uiState.update { it.copy(response = result.data) }
                            }
                            is AIResult.Error -> {
                                _uiState.update { 
                                    it.copy(error = result.message, isGenerating = false) 
                                }
                            }
                        }
                    }
                _uiState.update { it.copy(isGenerating = false) }
            } else {
                when (val result = ai.generateText(prompt)) {
                    is AIResult.Success -> {
                        _uiState.update { 
                            it.copy(response = result.data, isGenerating = false) 
                        }
                    }
                    is AIResult.Error -> {
                        _uiState.update { 
                            it.copy(error = result.message, isGenerating = false) 
                        }
                    }
                }
            }
        }
    }
    
    fun summarize(text: String, style: SummaryStyle = SummaryStyle.BRIEF) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }
            
            when (val result = ai.summarize(text, style)) {
                is AIResult.Success -> {
                    _uiState.update { 
                        it.copy(response = result.data, isGenerating = false) 
                    }
                }
                is AIResult.Error -> {
                    _uiState.update { 
                        it.copy(error = result.message, isGenerating = false) 
                    }
                }
            }
        }
    }
}

data class AIUiState(
    val availability: AIAvailability = AIAvailability.Unavailable(
        AIAvailability.UnavailableReason.MODEL_NOT_READY
    ),
    val isGenerating: Boolean = false,
    val response: String = "",
    val error: String? = null
)
```

### AIScreen.kt (Compose Multiplatform)

```kotlin
package com.m1k3.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.m1k3.ai.AIAvailability

@Composable
fun AIScreen(
    viewModel: AIViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var prompt by remember { mutableStateOf("") }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Availability Banner
        AvailabilityBanner(uiState.availability)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Input
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Ask M1K3") },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.availability == AIAvailability.Available,
            minLines = 3
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Actions
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.generateResponse(prompt) },
                enabled = uiState.availability == AIAvailability.Available 
                    && prompt.isNotBlank() 
                    && !uiState.isGenerating
            ) {
                if (uiState.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Generate")
            }
            
            OutlinedButton(
                onClick = { viewModel.summarize(prompt) },
                enabled = uiState.availability == AIAvailability.Available 
                    && prompt.isNotBlank() 
                    && !uiState.isGenerating
            ) {
                Text("Summarize")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Error
        uiState.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Response
        AnimatedVisibility(visible = uiState.response.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    text = uiState.response,
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
private fun AvailabilityBanner(availability: AIAvailability) {
    val (color, text) = when (availability) {
        is AIAvailability.Available -> {
            MaterialTheme.colorScheme.primaryContainer to "✓ On-device AI ready"
        }
        is AIAvailability.Downloading -> {
            MaterialTheme.colorScheme.tertiaryContainer to "⬇ Downloading AI model..."
        }
        is AIAvailability.Unavailable -> {
            MaterialTheme.colorScheme.errorContainer to when (availability.reason) {
                AIAvailability.UnavailableReason.DEVICE_NOT_SUPPORTED -> 
                    "✗ Device not supported for on-device AI"
                AIAvailability.UnavailableReason.AI_DISABLED -> 
                    "✗ Enable Apple Intelligence in Settings"
                AIAvailability.UnavailableReason.MODEL_NOT_READY -> 
                    "⏳ AI model initializing..."
                else -> "✗ On-device AI unavailable"
            }
        }
    }
    
    Surface(
        color = color,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
```

---

## Part 5: Gradle Configuration Summary

### Root build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}
```

### libs.versions.toml

```toml
[versions]
kotlin = "2.1.0"
agp = "8.7.0"
compose-multiplatform = "1.7.0"
coroutines = "1.9.0"
mlkit-genai-prompt = "1.0.0-alpha1"
mlkit-genai-summarization = "1.0.0-beta1"
mlkit-genai-common = "1.0.0-beta1"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
mlkit-genai-prompt = { module = "com.google.mlkit:genai-prompt", version.ref = "mlkit-genai-prompt" }
mlkit-genai-summarization = { module = "com.google.mlkit:genai-summarization", version.ref = "mlkit-genai-summarization" }
mlkit-genai-common = { module = "com.google.mlkit:genai-common", version.ref = "mlkit-genai-common" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
androidApplication = { id = "com.android.application", version.ref = "agp" }
androidLibrary = { id = "com.android.library", version.ref = "agp" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

---

## Key Implementation Notes

### iOS Specifics

1. **Minimum iOS Version**: iOS 26 (Foundation Models requires this)
2. **Device Requirements**: A17 Pro+ or M1+ with 8GB RAM
3. **Swift Interop**: Pure Swift APIs need @objc wrappers
4. **No cinterop for Macros**: Swift's @Generable macro can't be used from Kotlin directly - handle structured output in Swift and return JSON

### Android Specifics

1. **Minimum SDK**: API 26
2. **Device Requirements**: Tensor G3+, Snapdragon 8 Gen 3+, etc. with 8GB RAM
3. **Foreground Only**: Background use is blocked
4. **Quota Limits**: Handle `BUSY` and `QUOTA_EXCEEDED` with exponential backoff

### Cross-Platform Considerations

1. **Token Limits**: Both platforms ~4K tokens combined - validate input length in common code
2. **Offline**: Both work fully offline once model is downloaded
3. **Safety**: Neither allows disabling safety guardrails
4. **Streaming**: Both support token-by-token streaming
5. **Availability Check**: Always check before showing AI UI

### Alternative: SKIE for Better Swift Interop

Consider using [SKIE](https://skie.touchlab.co/) for:
- Automatic Flow → AsyncSequence bridging
- suspend fun → async/await translation
- Sealed class exhaustive switching

This would simplify the iOS bridging significantly but adds a build dependency.

---

## Testing Strategy

```kotlin
// commonTest
class OnDeviceAITest {
    @Test
    fun availabilityCheckReturnsValidState() = runTest {
        val ai = OnDeviceAI.create()
        val availability = ai.checkAvailability()
        
        // Should be one of the valid states
        assertTrue(
            availability is AIAvailability.Available ||
            availability is AIAvailability.Downloading ||
            availability is AIAvailability.Unavailable
        )
    }
}
```

For device testing, use:
- **Android**: Pixel 9+ or Samsung S24+ with real device
- **iOS**: iPhone 15 Pro+ or M1+ iPad with iOS 26 beta
