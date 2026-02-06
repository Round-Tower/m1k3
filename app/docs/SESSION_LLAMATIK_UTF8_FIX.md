# Session Notes: Llamatik JNI UTF-8 Crash + Dynamic Tool Loading

**Date:** 2025-02-06
**Status:** RESOLVED

## Problems Addressed

### 1. System Prompt Leaking into Chat Output
User reported the system prompt appearing in AI responses. Root cause: double-formatting of pre-formatted prompts.

### 2. Tool Token Overhead
All 11 tools were injected into every prompt (~150 tokens) even for queries like "Tell me about Ireland" that need no tools.

### 3. JNI UTF-8 Crash (Critical)
Fatal SIGABRT when model generated multi-byte UTF-8 characters (box-drawing, smart quotes):
```
JNI DETECTED ERROR IN APPLICATION: input is not valid Modified UTF-8
    input: '0xe2 0x94' / '0x93' / '0xaa'
    in call to NewStringUTF
    from LlamaBridge.nativeGenerateStream / nativeGenerateWithContextStream / generateWithContext
```

## Root Cause Analysis

### JNI Crash Chain
1. **ALL Llamatik streaming APIs** (`nativeGenerateStream`, `nativeGenerateWithContextStream`) call `NewStringUTF` per token
2. llama.cpp tokenizer can emit tokens that split multi-byte UTF-8 characters
3. Partial sequences (e.g., `0xe2 0x94` without 3rd byte) → `NewStringUTF` rejects → `SIGABRT`
4. Fatal signal kills the process — cannot be caught in Kotlin

### Double-Formatting Cascade
1. `ChatWithToolsUseCase` → `UnifiedPromptBuilder.build()` → pre-formatted prompt with Gemma3/FalconH1 tokens
2. `LlamaCppEngine` passes to `generateWithContext(system, context, user)`
3. Native C++ `generateWithContext` applies its own Gemma-style template on top
4. Model sees garbled tokens → hallucinates → generates garbage with invalid UTF-8 bytes
5. `NewStringUTF` crashes even on the non-streaming API return value

## Solutions

### Fix 1: Dynamic Tool Loading
Changed `ChatWithToolsUseCase.buildPromptWithTools()`:
```kotlin
// BEFORE: All tools always
val availableTools = toolRegistry.getAvailableTools()

// AFTER: Only relevant tools (0-3 based on query)
val relevantTools = toolRegistry.getRelevantTools(userPrompt, maxTools = 3)
```

Result: "Tell me about Ireland" → 0 tools (0 tokens). "What time?" → just `get_current_time` (~30 tokens).

### Fix 2: Correct API Routing
Pre-formatted prompts must NOT go through `generateWithContext` (which double-wraps):

```kotlin
val rawResponse = if (isAlreadyFormatted(prompt)) {
    // Pre-formatted: pass through without wrapping
    LlamaBridge.generate(prompt)
} else {
    // Raw: let native code apply model's chat template
    LlamaBridge.generateWithContext(system, context, user)
}
```

### Fix 3: Simulated Streaming
ALL streaming APIs crash on partial UTF-8. Solution: use non-streaming API, then emit word-by-word:
```kotlin
val rawResponse = LlamaBridge.generate(prompt)  // Complete response
val words = response.split("(?<=\\s)|(?=\\s)".toRegex())
words.forEach { onToken(it) }  // Simulate streaming
```

Trade-off: Full response generates before any text appears. Acceptable for small models (fast inference).

## Llamatik API Matrix

| API | JNI Method | Applies Template? | UTF-8 Safe? |
|-----|------------|-------------------|-------------|
| `generate(prompt)` | direct | NO | YES* |
| `generateWithContext(s,c,u)` | direct | YES (Gemma-style) | YES* |
| `generateStream(prompt, cb)` | `nativeGenerateStream` | NO | **NO** |
| `generateStreamWithContext(s,c,u,cb)` | wraps `generateStream` | YES (Kotlin) | **NO** |
| `generateWithContextStream(s,c,u,cb)` | `nativeGenerateWithContextStream` | YES (C++) | **NO** |

*Non-streaming APIs are UTF-8 safe IF the model produces valid output (no hallucination from bad prompts).

## Files Modified

### `LlamaCppEngine.kt`
- `generate()`: Routes pre-formatted → `LlamaBridge.generate()`, raw → `generateWithContext()`
- `generateStreaming()`: Uses non-streaming API + word-by-word emission
- Added `stripStopTokens()` helper
- Removed streaming-related imports (AtomicBoolean, suspendCancellableCoroutine)

### `ChatWithToolsUseCase.kt` (composeApp)
- `buildPromptWithTools()`: `getAvailableTools()` → `getRelevantTools(userPrompt, maxTools=3)`

### Tests Added
- `ChatFormatterTest.kt`: Gemma3/FalconH1 format detection, consolidated prompt structure
- `ChatWithToolsUseCaseTest.kt`: Dynamic tool loading verification, MockToolRegistry with call tracking

## Key Learnings

1. **JNI NewStringUTF is fragile** — rejects partial UTF-8, causes non-catchable SIGABRT
2. **Llamatik's "WithContext" APIs apply templates** — don't double-wrap pre-formatted prompts
3. **Model hallucination produces invalid bytes** — fix the prompt, not the crash
4. **Simulated streaming is acceptable** — small models are fast, UX impact minimal
5. **Dynamic tool loading saves tokens** — 67-100% reduction for non-tool queries

## Future Improvements

1. **Llamatik PR**: Add UTF-8 buffering in native streaming callbacks before `NewStringUTF`
2. **True streaming**: If Llamatik fixes UTF-8, re-enable real token-by-token streaming
3. **Tool relevance tuning**: Improve `ToolFilter` keyword/category scoring

## References

- [JNI NewStringUTF Spec](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/functions.html#NewStringUTF)
- Llamatik 0.13.0 source: `~/.gradle/caches/.../library-android-0.13.0-sources.jar`
- Previous session: `docs/SESSION_GEMMA3_LLAMATIK_FIX.md`
