# Session Notes: Gemma 3 270M + Llamatik 0.9.0 Fix

**Date:** 2025-12-23
**Status:** RESOLVED

## Problem Summary

Gemma 3 270M was returning empty responses ("...") or garbage output (regurgitating system prompts) when using Llamatik 0.9.0's `generateWithContextStream()` API.

## Root Cause

**Llamatik's `generateWithContextStream(system, context, user)` builds a chat template with `<start_of_turn>system` which Gemma 3 does NOT support.**

From Llamatik 0.8.1 source code (`LlamaBridge.android.kt`):

```kotlin
private fun buildChatPrompt(systemPrompt: String, contextBlock: String, userPrompt: String): String {
    return buildString {
        append("<start_of_turn>system\n")   // <-- WRONG! Gemma 3 has NO system role!
        append(systemPrompt.trim())
        append("\n<end_of_turn>\n")
        append("<start_of_turn>user\n")
        // ...
    }
}
```

**Gemma 3 only supports two roles:** `user` and `model`. The `system` role is not supported per [Google's Gemma documentation](https://ai.google.dev/gemma/docs/core/prompt-structure).

## Solution

### 1. Use `generateStream()` instead of `generateWithContextStream()`

The `generateStream(prompt, callback)` method takes a RAW prompt without any templating, allowing us to build the correct Gemma 3 template ourselves.

### 2. Build Proper Gemma 3 Chat Template

Created `buildGemma3ChatPrompt()` function:

```kotlin
private fun buildGemma3ChatPrompt(
    systemPrompt: String,
    context: String,
    userQuery: String
): String = buildString {
    // Gemma 3 template: <bos><start_of_turn>user\n...<end_of_turn>\n<start_of_turn>model\n
    append("<bos><start_of_turn>user\n")

    // System instructions go INTO the user message (no system role!)
    if (systemPrompt.isNotBlank()) {
        append(systemPrompt.trim())
        append("\n\n")
    }

    // Context/facts
    if (context.isNotBlank() && !context.contains("0 facts")) {
        append(context.trim())
        append("\n\n")
    }

    // User query
    append(userQuery.trim())

    // End user turn, start model turn
    append("<end_of_turn>\n<start_of_turn>model\n")
}
```

### 3. Update Stop Tokens

Changed from mixed format:
```kotlin
val stopTokens = listOf("<end_of_turn>", "</s>", "<|endoftext|>", "<|im_end|>")
```

To Gemma 3 only:
```kotlin
val stopTokens = listOf("<end_of_turn>", "<eos>")
```

### 4. Simplified System Prompts

Complex numbered-list prompts caused small models to regurgitate instructions:

```kotlin
// BEFORE (model regurgitated this)
"Use a natural-dialogue strategy: " +
"1) Acknowledge the user's message " +
"2) Respond naturally and briefly " +
"3) Be friendly but not verbose..."

// AFTER
"Be friendly and concise."
```

## Files Modified

- `app/composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/ai/LlamaCppEngine.kt`
  - Changed from `generateWithContextStream()` to `generateStream()`
  - Added `buildGemma3ChatPrompt()` function
  - Updated stop tokens to Gemma 3 only

- `app/composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/ui/ChatScreen.kt`
  - Simplified query-type system prompt hints

## Results

| Metric | Before | After |
|--------|--------|-------|
| Token Generation | 0 tokens | 10+ tok/s |
| Response Quality | Empty or garbage | Proper responses |
| Stop Token Handling | Broken | Working |

## Key Learnings

1. **Always check library templates** - Llamatik's templating was incompatible with Gemma 3
2. **Gemma 3 has NO system role** - Must use only `user` and `model` roles
3. **Small models need simple prompts** - Complex instructions get regurgitated
4. **Use raw prompt APIs when possible** - Gives full control over template format

## References

- [Google Gemma Prompt Structure](https://ai.google.dev/gemma/docs/core/prompt-structure)
- [llama.cpp Wiki - Templates](https://github.com/ggml-org/llama.cpp/wiki/Templates-supported-by-llama_chat_apply_template)
- [Gemma 3 Context Shift Issue #12357](https://github.com/ggml-org/llama.cpp/issues/12357)

## Migration Notes for Future Models

When adding new model support:

1. Check if model supports system role
2. Verify chat template format matches model's training
3. Use `generateStream()` with custom template for full control
4. Test with simple prompts first before adding complexity
5. Monitor for stop token issues (wrong tokens = truncation or infinite generation)
