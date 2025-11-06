# Session Notes: RAG Fix & Llamatik Integration (2025-11-06)

## Summary
Fixed RAG knowledge duplication issue after Llamatik 0.8.1 migration. RAG was passing both enriched system prompt (with retrieved facts) AND static knowledge context, causing confusion.

## Commits Created

### 1. Llamatik 0.8.1 Migration (8f4e204)
**feat(ai): Migrate to Llamatik 0.8.1 with BaseLlmEngine abstraction**

- Migrated from InferKt 0.0.2 (native crash SIGABRT) to stable Llamatik 0.8.1
- Created BaseLlmEngine interface for AI engine abstraction (177 lines)
- Rewritten LlamaCppEngine using Llamatik API (353 lines)
- Added 36 test cases + MockLlmEngine for deterministic testing
- GenerationConfig with graceful degradation
- Trade-off: Lost fine-grained control (temp, topP, topK) but gained stability
- Workaround: Prompt engineering for behavioral control

### 2. Documentation Update (9563a45)
**docs: Update CLAUDE.md with Llamatik 0.8.1 milestone**

- Added migration milestone to CLAUDE.md
- Updated AI/ML Stack section with migration history v1→v2→v3
- Current phase: AI Engine Migration & Abstraction (IN PROGRESS)

### 3. RAG Duplication Fix (ffbbd51)
**fix(rag): Prevent knowledge duplication when RAG is active**

**Problem:**
- When RAG retrieved facts, BOTH enriched prompt (with facts) AND static knowledge context were passed
- Llamatik received redundant context in both system and context parameters

**Solution:**
- ChatScreen now conditionally passes `knowledgeContext` only when RAG didn't retrieve anything
- When RAG is active: `enrichedSystemPrompt` (with facts), `knowledgeContext=null`
- When RAG is not active: default prompt + static knowledge context summary
- Added detailed logging to track system/context params (first 150 chars)

**Changes:**
- `ChatScreen.kt:515`: `knowledgeContext = if (ragResult.ragApplied) null else knowledgeContext`
- `LlamaCppEngine.kt`: Enhanced logging for debugging

## Migration History

### v1: ONNX Runtime (Failed)
- SmolLM2-135M severe hallucinations
- Tokenizer issues caused incoherent output

### v2: InferKt 0.0.2 (Crashed)
- Native crash: `SIGABRT in llama_batch_free`
- Memory corruption in JNI layer
- Attempted but unstable

### v3: Llamatik 0.8.1 (SUCCESS)
- ✅ Stable - no crashes
- ✅ Simple API - `generateWithContextStream(system, context, user, onDelta, onDone, onError)`
- ✅ Active maintenance (updated 6 days ago)
- ✅ Network inference capability (future feature)
- ⚠️ No sampling control (temp, topP, topK)
- 🔧 Prompt engineering workaround

## Technical Details

### BaseLlmEngine Interface
```kotlin
interface BaseLlmEngine {
    suspend fun initialize()
    suspend fun generate(prompt: String, config: GenerationConfig): GenerationResult
    suspend fun generateStreaming(prompt: String, config: GenerationConfig, onToken: (String) -> Unit)
    fun getOptimalMaxTokens(): Int
    fun release()
}
```

### Llamatik API Usage
```kotlin
LlamaBridge.generateWithContextStream(
    system = buildSystemPrompt(config),  // M1K3 identity + behavior guidance
    context = config.knowledgeContext ?: "",  // KB summary OR RAG facts
    user = prompt,  // User's question
    onDelta = { token -> onToken(token) },
    onDone = { continuation.resume(Unit) },
    onError = { error -> println("Error: $error") }
)
```

### RAG Flow (Fixed)
1. User asks question: "What is the capital of France?"
2. RAGManager retrieves geography facts from KB
3. Creates enriched system prompt with facts
4. ChatScreen checks `ragResult.ragApplied`:
   - If true: Pass enriched prompt, knowledgeContext=null
   - If false: Pass default prompt, knowledgeContext=static summary
5. LlamaCppEngine logs both system & context for debugging
6. Llamatik generates response using facts

## Testing Status

### Completed
- ✅ APK builds successfully with Llamatik
- ✅ App launches without crashes
- ✅ Knowledge base loads (1,401 documents)
- ✅ Commits created with detailed documentation

### Pending (Blocked by Disk Space)
- ⏳ Build APK with RAG fix
- ⏳ Test RAG query: "What is the capital of France?"
- ⏳ Test non-RAG query: "Hello"
- ⏳ Verify logs show proper system/context separation
- ⏳ Run BaseLlmEngineTest suite (36 tests)

## Disk Space Issue
- **Problem**: 100% disk usage (899MB free out of 926GB)
- **Cause**: 83GB gradle caches
- **Solution**: Clean caches before next build

```bash
# Clean old gradle caches
rm -rf ~/.gradle/caches/transforms-*
rm -rf ~/.gradle/caches/modules-2/files-2.1/androidx.*
./gradlew clean
```

## Next Steps

1. **Free up disk space** (critical)
2. **Build APK** with RAG fix: `./gradlew :composeApp:installDebug`
3. **Test RAG active**: Ask "What is the capital of France?" - should retrieve geography facts
4. **Test RAG inactive**: Ask "Hello" - should use static KB summary
5. **Check logs**: Verify system/context separation in logcat
6. **Run tests**: `./gradlew test` for BaseLlmEngineTest suite

## Files Modified

### New Files
- `composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/ai/BaseLlmEngine.kt` (177 lines)
- `composeApp/src/commonTest/kotlin/app/m1k3/ai/assistant/ai/BaseLlmEngineTest.kt` (36 tests)
- `composeApp/src/commonTest/kotlin/app/m1k3/ai/assistant/ai/MockLlmEngine.kt` (180 lines)

### Modified Files
- `composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/ai/LlamaCppEngine.kt` (rewritten for Llamatik)
- `composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/ui/ChatScreen.kt` (RAG conditional logic)
- `composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/MainActivity.kt` (BaseLlmEngine type)
- `composeApp/build.gradle.kts` (Llamatik dependency)
- `settings.gradle.kts` (JitPack repository)
- `CLAUDE.md` (milestone update)

## Architectural Improvements

### Before (ONNX/InferKt)
- Concrete engine dependency (tight coupling)
- No abstraction layer
- Crashes with InferKt
- Hallucinations with ONNX

### After (Llamatik + BaseLlmEngine)
- Abstract interface (loose coupling)
- Easy engine swapping
- Stable inference
- MockLlmEngine for testing
- Graceful degradation for limited APIs

## Prompt Engineering for Control

Since Llamatik doesn't expose temperature/sampling, we use prompt engineering:

```kotlin
val behaviorGuidance = when {
    temperature < 0.4f -> "\n\nIMPORTANT: Be concise, factual, and direct."
    temperature > 0.7f -> "\n\nIMPORTANT: Be creative, imaginative, and expansive."
    else -> ""  // Balanced default
}
```

This compensates for lost API control while maintaining stability.

---

**Last Updated**: 2025-11-06 22:45 PST
**Status**: RAG fix committed, pending rebuild (disk space issue)
**Next Session**: Clean disk, rebuild, test RAG functionality
