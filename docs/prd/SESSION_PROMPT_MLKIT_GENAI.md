# Session Prompt: ML Kit GenAI Integration

**Date Created:** 2025-12-22
**Next Session Goal:** Implement real ML Kit GenAI (Gemini Nano) and test on Pixel device

---

## Context

We've completed Phase 2 of the On-Device AI implementation for 間 AI:

### What's Done (114 tests passing)
1. **OnDeviceAi interface** - Platform-agnostic abstraction in commonMain
2. **AiResult<T>** - Sealed class with functional operators (map, fold, onSuccess, onError)
3. **AiAvailability** - 4 states: Available, Downloading, Unavailable(reason), Fallback(engine)
4. **AiErrorCode** - 8 typed error codes
5. **SummaryStyle** - BRIEF, BULLETS, DETAILED
6. **MockOnDeviceAi** - Full mock for testing (70 tests)
7. **LlamaCppFallbackEngine** - Adapter wrapping BaseLlmEngine (17 tests)
8. **AndroidOnDeviceAi** - Main Android impl with ML Kit → LlamaCpp fallback (27 tests)
9. **Thread-safe architecture** - AtomicReference<EngineState>, CAS loop for release()

### What's Stubbed (Needs Implementation)
- `DefaultMlKitAvailabilityChecker` - Always returns `false`
- `StubMlKitGenAiEngine` - Always returns `Unavailable`

---

## Task: Implement Real ML Kit GenAI

### 1. Add Dependencies

In `composeApp/build.gradle.kts`:
```kotlin
// ML Kit GenAI (Gemini Nano on-device)
implementation("com.google.mlkit:genai-prompt:1.0.0-alpha1")
implementation("com.google.mlkit:genai-summarization:1.0.0-beta1")
```

### 2. Implement MlKitAvailabilityChecker

Replace `DefaultMlKitAvailabilityChecker` with real device capability checks:

```kotlin
class RealMlKitAvailabilityChecker(
    private val context: Context
) : MlKitAvailabilityChecker {
    override suspend fun isGenAiAvailable(): Boolean {
        // Check if device supports ML Kit GenAI:
        // - Android 14+
        // - Tensor G3+ (Pixel 8+)
        // - Snapdragon 8 Gen 3+ (Samsung S24+)
        // - Dimensity 9300+ (select MediaTek)
        // Uses GenerativeModel.isAvailable() or similar API
    }
}
```

### 3. Implement MlKitGenAiEngine

Replace `StubMlKitGenAiEngine` with real Gemini Nano integration:

```kotlin
class RealMlKitGenAiEngine(
    private val context: Context
) : MlKitGenAiEngine {
    private var generativeModel: GenerativeModel? = null

    override suspend fun checkAvailability(): AiAvailability {
        // Map ML Kit FeatureStatus to AiAvailability
    }

    override suspend fun downloadModelIfNeeded(): AiResult<Unit> {
        // Download Gemini Nano model if not cached
    }

    override suspend fun generate(prompt: String, config: GenerationConfig): AiResult<String> {
        // Use GenerativeModel.generateContent()
    }

    override fun generateStream(prompt: String, config: GenerationConfig): Flow<AiResult<String>> {
        // Use GenerativeModel.generateContentStream()
    }

    override suspend fun summarize(text: String, style: SummaryStyle): AiResult<String> {
        // Use Summarizer API with style mapping:
        // BRIEF → ONE_BULLET
        // BULLETS → THREE_BULLETS
        // DETAILED → PARAGRAPH
    }
}
```

### 4. Wire into DI (Koin)

Update Koin module to provide real implementations:

```kotlin
single<MlKitAvailabilityChecker> { RealMlKitAvailabilityChecker(androidContext()) }
single<MlKitGenAiEngine> { RealMlKitGenAiEngine(androidContext()) }
single<OnDeviceAi> {
    AndroidOnDeviceAi(
        mlKitChecker = get(),
        mlKitEngine = get(),
        fallbackEngine = LlamaCppFallbackEngine(get<BaseLlmEngine>())
    )
}
```

### 5. Test on Pixel Device

Testing scenarios:
1. **ML Kit Available** - Pixel 9 with Android 15
2. **ML Kit Downloading** - First launch, model download
3. **Fallback** - Older Pixel without Gemini Nano support
4. **Generation** - Text generation quality comparison (ML Kit vs LlamaCpp)
5. **Streaming** - Token-by-token response display
6. **Summarization** - Test all 3 styles

---

## Key Files

| File | Purpose |
|------|---------|
| `AndroidOnDeviceAi.kt` | Main orchestrator (ML Kit → fallback) |
| `MlKitAvailabilityChecker.kt` | Device capability checking |
| `MlKitGenAiEngine.kt` | Gemini Nano wrapper |
| `LlamaCppFallbackEngine.kt` | Fallback for older devices |
| `OnDeviceAi.kt` | Shared interface (commonMain) |

---

## ML Kit GenAI API Reference

### GenerativeModel
```kotlin
val generativeModel = GenerativeModel.getGenerativeModel(
    GenerativeModelConfig.Builder()
        .setPromptApiConfig(PromptApiConfig.Builder().build())
        .build()
)

// Check availability
val status = generativeModel.getModelStatus() // AVAILABLE, DOWNLOADING, NOT_AVAILABLE

// Generate
val response = generativeModel.generateContent(prompt)

// Stream
generativeModel.generateContentStream(prompt).collect { chunk ->
    // Handle streaming tokens
}
```

### Summarizer
```kotlin
val summarizer = Summarizer.getClient(
    SummarizerOptions.Builder()
        .setOutputStyle(OutputStyle.THREE_BULLETS) // or ONE_BULLET, PARAGRAPH
        .build()
)

val result = summarizer.summarize(inputText)
```

---

## TDD Approach

Follow Red-Green-Refactor:

1. **Write integration tests** for RealMlKitGenAiEngine (will fail initially)
2. **Implement** the real engine
3. **Run on device** to verify ML Kit integration
4. **Refactor** based on actual API behavior

Note: Some tests may need `@Ignore` on CI since they require real ML Kit SDK.

---

## Success Criteria

- [ ] ML Kit GenAI dependency added and compiles
- [ ] RealMlKitAvailabilityChecker detects Pixel 9 support
- [ ] RealMlKitGenAiEngine generates text with Gemini Nano
- [ ] Streaming works with token-by-token display
- [ ] Summarization works with all 3 styles
- [ ] Fallback to LlamaCpp works on unsupported devices
- [ ] Tests pass (new integration tests + existing 114)

---

## Reference Documentation

- [ML Kit GenAI Overview](https://developers.google.com/ml-kit/genai)
- [Prompt API Guide](https://developers.google.com/ml-kit/genai/prompt)
- [Summarization API Guide](https://developers.google.com/ml-kit/genai/summarization)
- [Device Requirements](https://developers.google.com/ml-kit/genai/device-requirements)

---

**Ready to implement real ML Kit GenAI! 🚀**
