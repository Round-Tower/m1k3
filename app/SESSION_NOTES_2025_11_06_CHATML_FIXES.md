# Session Notes: ChatML Formatting Fixes & Model Hallucination Investigation
**Date:** 2025-11-06
**Session Focus:** Fix ChatML prompt formatting, add response logging, investigate model behavior

---

## Summary

**Completed:**
- ✅ Fixed ChatML prompt formatting (removed raw string indentation pollution)
- ✅ Fixed system prompt duplication in RAG architecture
- ✅ Added comprehensive response logging (UI + engine level)
- ✅ Verified ChatML format is correct

**Critical Issue Discovered:**
- ❌ Model is completely hallucinating and echoing system prompt instead of responding properly
- ❌ 925 characters of repetitive, looping output for simple "help" query
- ❌ Model ignoring ChatML format despite correct structure

---

## Completed Work

### 1. ChatML Prompt Formatting Fix

**Problem:** Raw string literals were preserving indentation, adding 12 extra spaces per line to ChatML prompts.

**Files Modified:**
- `composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/ai/SmolLM2Engine.kt`

**Changes:**
- Created `buildChatMLPrompt()` function using `StringBuilder` with explicit `\n` characters
- Replaced raw string literals in both `generate()` and `generateStreaming()` methods
- Added debug logging showing full ChatML prompt

**Verification:**
```
✅ Correct format: <|im_start|>system\nYou are M1K3...<|im_end|>\n<|im_start|>user\nhello?<|im_end|>\n<|im_start|>assistant\n
❌ Previous format had 12 spaces before each line
```

**Commit:** Lines 118-148, 252-256, 544-548

---

### 2. System Prompt Duplication Fix

**Problem:** RAG-enriched prompt was being combined with user query, causing system prompt to appear in both system message slot AND user message slot.

**Files Modified:**
- `composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/ui/ChatScreen.kt`

**Changes:**
```kotlin
// BEFORE (BROKEN):
val finalPrompt = "${ragResult.enrichedPrompt}\n\nUser: $prompt\nAssistant:"
aiEngine.generateStreaming(
    systemPrompt = systemPrompt,  // Base instructions
    prompt = finalPrompt,          // RAG + system + "User: question"
)

// AFTER (FIXED):
val enrichedSystemPrompt = if (ragResult.ragApplied) {
    ragResult.enrichedPrompt  // RAG knowledge + instructions
} else {
    systemPrompt  // Just base instructions
}
aiEngine.generateStreaming(
    systemPrompt = enrichedSystemPrompt,  // System instructions only
    prompt = prompt,                       // User question only
)
```

**Result:** System prompt now appears only once, user question is clean (no labels).

**Commit:** Lines 461-488

---

### 3. Comprehensive Response Logging

**Problem:** Hard to debug response quality issues without seeing complete final output.

**Files Modified:**
1. `composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/ui/ChatScreen.kt` (Lines 568-576)
2. `composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/ai/SmolLM2Engine.kt` (Lines 745-750)

**ChatScreen.kt Logging (UI Level):**
```kotlin
println("✅ [RESPONSE-COMPLETE] Generation finished")
println("   📝 Full response: \"$streamedText\"")
println("   📊 Stats: ${streamedText.length} chars / $tokenCount tokens")
println("   ⚡ Performance: ${"%.1f".format(tokensPerSec)} tok/s in ${totalTime}ms")
println("   🔍 Special tokens: ${if (streamedText.contains("<|") || streamedText.contains("|>")) "DETECTED ⚠️" else "clean ✓"}")
if (ragInfo.isNotEmpty()) {
    println("   📚 RAG: $ragInfo")
}
```

**SmolLM2Engine.kt Logging (Engine Level):**
```kotlin
val finalResponseTokens = generatedIds.drop(inputIds.size).toLongArray()
val finalResponseText = tok.decode(finalResponseTokens)
println("   📝 [ENGINE] Full response: \"$finalResponseText\"")
println("   🔍 [ENGINE] Length: ${finalResponseText.length} chars / ${finalResponseTokens.size} tokens")
println("   🧹 [ENGINE] Contains special tokens: ${finalResponseText.contains("<|") || finalResponseText.contains("|>")}")
```

**Benefits:**
- See exact text displayed to user
- Validate special token cleaning
- Compare engine output vs UI output
- Track performance metrics alongside quality

---

## Critical Issue Discovered: Model Hallucination

### Symptoms

**User Query:** "help"

**Expected Response:** Helpful explanation of capabilities

**Actual Response (925 chars):**
```
A: You are M1K3 an AI assistant running 90% locally. Be helpful, concise and informative. <I am here to help you find what your privacy is about? How do I know if my data or information matter for the company that provided it in the first place<>
A: You are M1K3 an AI assistant running 90% locally. Be helpful, concise and informative. <ims-a@to(c)n? a=<> user13159:  A&B is an AI assistant running local, concise and informative. <ims-a@to(c)n? a=<> user1359:  How do I know if my data is important for the company that provided it in the first place. <ims-a@to(c)n? a=<> user1395:  How do I know if my data is important for the company that provided it in the first place...
[repeats endlessly with variations]
```

**Performance:** 15.8 tok/s over 19,085ms (301 tokens generated)

### Analysis

**ChatML Format is Correct:**
```
<|im_start|>system\nYou are M1K3, a privacy-first AI assistant running 100% locally. Be helpful, concise, and informative. <|im_end|>\n<|im_start|>user\nhelp<|im_end|>\n<|im_start|>assistant\n
```

**Model Behavior:**
1. ❌ Ignores ChatML structure completely
2. ❌ Echoes system prompt as response (with errors: "90%" instead of "100%")
3. ❌ Adds spurious "A:" prefix (not in training data)
4. ❌ Hallucinates fake HTML tags (`<IMG SRC="/images">`)
5. ❌ Hallucinates fake user IDs (`user13159`, `user1359`, `user1395`)
6. ❌ Loops the same hallucinated content repeatedly
7. ❌ Generates 301 tokens of gibberish instead of stopping

### Possible Root Causes

#### 1. Model Weights Corruption
- SmolLM2-135M ONNX model may be corrupted
- **Test:** Re-export model from HuggingFace checkpoint
- **Likelihood:** Medium (model was working in Phase 1.5)

#### 2. Temperature Too Low (0.5f)
- Low temperature can cause repetition loops
- **Test:** Increase to 0.7f or 0.9f
- **Likelihood:** Low (0.5f is reasonable for chat)

#### 3. KV Cache Corruption
- Previous generations may have corrupted KV cache state
- KV cache not being properly reset between generations
- **Test:** Force KV cache reset on each generation
- **Likelihood:** High (we're reusing KV cache across messages)

#### 4. Context Window Overflow
- Previous conversation history pushing context beyond model limits
- SmolLM2-135M has 8K context, but we may be exceeding it
- **Test:** Clear conversation history, test with fresh session
- **Likelihood:** High (71 messages loaded from database)

#### 5. Tokenizer Mismatch
- ChatML special tokens not properly trained into model
- Model doesn't understand `<|im_start|>`, `<|im_end|>` markers
- **Test:** Try different chat template (Llama format, plain prompt)
- **Likelihood:** Low (SmolLM2 uses ChatML natively)

#### 6. ONNX Runtime Issue
- ONNX inference session corrupted
- Memory management bug in streaming generation
- **Test:** Non-streaming generation, restart app
- **Likelihood:** Medium (streaming has complex state management)

### Evidence from Logs

**Token Generation is Working:**
```
Token #0: "A" ✅
Token #1: ":" ✅
Token #2: " You" ✅
Token #3: " are" ✅
Token #4: " M" ✅
Token #5: "1" ✅
...
```

**Tokenization is Correct:**
- GPT-2 BPE working (space character 'Ġ' detected)
- 74 tokens for prompt (reasonable)
- Per-token decoding successful

**Special Token Cleaning is Working:**
- No `<|im_start|>` or `<|im_end|>` in final output
- UI safeguard filtering correctly

**The Issue is Model Behavior, Not Infrastructure:**
- ChatML format ✅
- Tokenizer ✅
- Streaming ✅
- Special token filtering ✅
- **Model understanding of chat format ❌**

---

## Recommended Next Steps (Priority Order)

### 1. Test with Fresh Session (Highest Priority)
**Action:** Force clear conversation history, restart app, test single message
**Hypothesis:** KV cache or context overflow from 71 previous messages
**Test:**
```kotlin
// In ChatScreen.kt, temporarily clear history on app start
chatViewModel.clearConversation()
```

### 2. Force KV Cache Reset
**Action:** Ensure KV cache is reset between each generation, not reused
**Hypothesis:** Corrupted KV cache from previous generation causing echo
**Test:**
```kotlin
// In SmolLM2Engine.kt generateStreaming(), line 568
var pastKeyValues: MutableMap<String, OnnxTensor>? = null  // Already correct
// But verify we're not reusing cache from ChatViewModel state
```

### 3. Increase Temperature
**Action:** Test with temperature 0.7f, 0.9f, 1.0f
**Hypothesis:** Low temperature causing deterministic repetition
**Test:**
```kotlin
// In ChatScreen.kt, line 490
temperature = 0.9f,  // Was 0.5f
```

### 4. Test Non-Streaming Generation
**Action:** Use `generate()` instead of `generateStreaming()`
**Hypothesis:** Streaming state management causing issues
**Test:**
```kotlin
// Temporarily switch to non-streaming
val response = aiEngine.generate(
    systemPrompt = enrichedSystemPrompt,
    prompt = prompt,
    maxTokens = 256,
    temperature = 0.7f
)
```

### 5. Verify Model Checkpoint
**Action:** Re-download SmolLM2-135M-Instruct from HuggingFace
**Hypothesis:** Model weights corrupted during export or download
**Test:**
```bash
# Re-export ONNX model
python export_smollm2_onnx.py --model HuggingFaceTB/SmolLM2-135M-Instruct
```

### 6. Test Alternative Chat Templates
**Action:** Try Llama-style or plain prompt format
**Hypothesis:** Model not properly fine-tuned for ChatML
**Test:**
```kotlin
// Try Llama format:
"<s>[INST] <<SYS>>\n$systemPrompt\n<</SYS>>\n\n$userPrompt [/INST]"

// Or plain format:
"System: $systemPrompt\n\nUser: $userPrompt\n\nAssistant:"
```

---

## Session Artifacts

### Modified Files
1. `composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/ai/SmolLM2Engine.kt`
   - Lines 118-148: `buildChatMLPrompt()` function
   - Lines 252-256: Fixed `generate()` method
   - Lines 544-548: Fixed `generateStreaming()` method
   - Lines 745-750: Added engine-level response logging

2. `composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/ui/ChatScreen.kt`
   - Lines 461-488: Fixed system prompt duplication
   - Lines 568-576: Added UI-level response logging

### Test Results

**ChatML Format:** ✅ PASS
```
<|im_start|>system\nYou are M1K3, a privacy-first AI assistant running 100% locally. Be helpful, concise, and informative. <|im_end|>\n<|im_start|>user\nhello?<|im_end|>\n<|im_start|>assistant\n
```

**System Prompt Duplication:** ✅ FIXED
- System prompt appears only once in system message slot
- User question is clean (no labels, no duplication)

**Response Logging:** ✅ WORKING
- UI-level logs show complete response text
- Engine-level logs show decoded tokens
- Performance metrics tracked (15.8 tok/s)

**Model Response Quality:** ❌ CRITICAL FAILURE
- 925 characters of hallucinated repetition
- Model echoing system prompt instead of responding
- Fake HTML tags, user IDs, infinite loops

---

## Open Questions

1. **Why did this break?** Model was working in Phase 1.5 (streaming inference milestone). What changed?
   - iOS font integration? No, fonts don't affect inference
   - RAG guardrails optimization? No, RAG wasn't applied to "help" query
   - ChatML formatting fix? Unlikely, format is now MORE correct
   - **Most likely:** Accumulated conversation history (71 messages) corrupting KV cache

2. **Is this SmolLM2-135M specific?** Would SmolLM2-360M behave differently?
   - 135M has less capacity, more prone to hallucination
   - 360M might handle chat format better
   - **Test needed:** Try both models side-by-side

3. **Is ChatML the right format?** Maybe SmolLM2 wasn't trained on ChatML?
   - HuggingFace model card says "Instruct" variant uses ChatML
   - But behavior suggests model doesn't understand format
   - **Test needed:** Try alternative formats (Llama, plain)

4. **Is the ONNX export correct?** Maybe quantization broke something?
   - 4-bit quantization could introduce errors
   - ChatML tokens might not quantize well
   - **Test needed:** Try FP16 or FP32 export

---

## Environment State

**App Version:** Phase 1.5 + ChatML fixes
**Model:** SmolLM2-135M-Instruct (4-bit quantized ONNX)
**Tokenizer:** GPT-2 BPE (49,152 vocab)
**Device:** Pixel 6 Pro (Android 14)
**Conversation History:** 71 messages in database
**Context Window:** 8K tokens (SmolLM2-135M spec)
**Temperature:** 0.5f
**Max Tokens:** Device-adaptive (likely 256 for mid-range)

**Build Status:** ✅ Successful (1m 26s)
**Install Status:** ✅ Deployed to device
**APK Size:** Not measured this session

---

## Next Session TODO

### Immediate (Session Start)
1. ✅ Clear conversation history (force fresh KV cache)
2. ✅ Test single "hello" message
3. ✅ Check logs for hallucination

### If Still Hallucinating
4. ⬜ Increase temperature to 0.9f
5. ⬜ Test non-streaming generation
6. ⬜ Add KV cache reset logging
7. ⬜ Test with Llama chat template

### If Still Broken
8. ⬜ Re-export SmolLM2-135M ONNX model
9. ⬜ Try SmolLM2-360M (previous working model)
10. ⬜ Test plain prompt format (no chat template)

### Long-Term Investigation
11. ⬜ Analyze conversation history context overflow
12. ⬜ Implement conversation summarization/pruning
13. ⬜ Add KV cache state validation
14. ⬜ Test different quantization levels (FP16, INT8, FP32)

---

## Relevant Code Locations

### ChatML Prompt Building
- `SmolLM2Engine.kt:118-148` - `buildChatMLPrompt()` function
- `SmolLM2Engine.kt:544-548` - Streaming generation call site
- `SmolLM2Engine.kt:252-256` - Non-streaming generation call site

### System Prompt Handling
- `ChatScreen.kt:461-488` - RAG prompt enrichment
- `RAGManager.kt:71-132` - `enrichPrompt()` method

### Response Logging
- `ChatScreen.kt:568-576` - UI-level completion logging
- `SmolLM2Engine.kt:745-750` - Engine-level decoding logging

### KV Cache Management
- `SmolLM2Engine.kt:568-570` - KV cache initialization
- `SmolLM2Engine.kt:600-720` - KV cache update loop
- `SmolLM2Engine.kt:735-738` - Resource cleanup

### Tokenizer
- `SmolLM2Tokenizer.kt:83-119` - `encode()` method
- `SmolLM2Tokenizer.kt:138-224` - `decode()` method
- `SmolLM2Tokenizer.kt:207-218` - Special token cleanup

---

## Logs Archive

**ChatML Format Verification:**
```
🔍 [CHATML-STREAMING] Formatted prompt (187 chars)
🔍 [CHATML-STREAMING] Preview: <|im_start|>system\nYou are M1K3, a privacy-first AI assistant running 100% locally. Be helpful, concise, and informative. <|im_end|>\n<|im_start|>user\nhelp<|im_end|>\n<|im_start|>assistant\n
```

**Model Hallucination Output:**
```
✅ [RESPONSE-COMPLETE] Generation finished
   📝 Full response: "A: You are M1K3 an AI assistant running 90% locally. Be helpful, concise and informative. <I am here to help you find what your privacy is about? How do I know if my data or information matter for the company that provided it in the first place<>
A: You are M1K3 an AI assistant running 90% locally. Be helpful, concise and informative. <ims-a@to(c)n? a=<> user13159:  A&B is an AI assistant running local, concise and informative..."
   📊 Stats: 925 chars / 301 tokens
   ⚡ Performance: 15.8 tok/s in 19085ms
   🔍 Special tokens: clean ✓
```

**First 20 Generated Tokens:**
```
Token #0: "A" (ID=49)
Token #1: ":" (ID=42)
Token #2: " You" (ID=1206)
Token #3: " are" (ID=359)
Token #4: " M" (ID=372)
Token #5: "1" (ID=1)
Token #6: "K" (ID=75)
Token #7: "3" (ID=3)
Token #8: " an" (ID=281)
Token #9: " AI" (ID=9552)
Token #10: " assistant" (ID=8443)
Token #11: " running" (ID=2614)
Token #12: " " (ID=220)
Token #13: "9" (ID=9)
Token #14: "0" (ID=0)
Token #15: "%" (ID=37)
Token #16: " locally" (ID=17843)
Token #17: "." (ID=13)
Token #18: " Be" (ID=1355)
Token #19: " helpful" (ID=7613)
Token #20: "," (ID=28)
```

---

## Conclusion

**Good News:**
- ChatML formatting is now correct (no whitespace pollution)
- System prompt duplication is fixed
- Comprehensive logging is in place
- Infrastructure is solid (tokenizer, streaming, special token filtering)

**Bad News:**
- Model is completely broken (hallucinating instead of responding)
- 925 characters of repetitive gibberish for simple "help" query
- Model echoing system prompt instead of understanding it
- Critical show-stopper for Phase 2 progress

**Most Likely Cause:**
- Conversation history overflow (71 messages) corrupting KV cache
- Model seeing same prompts repeatedly, learning to echo instead of respond

**Next Session Priority:**
1. Clear conversation history
2. Test with fresh session
3. If still broken, increase temperature and test non-streaming
4. If still broken, re-export model or try SmolLM2-360M

**Impact on Roadmap:**
- Phase 2 (Memory & Embedding) is blocked until model behavior is fixed
- Cannot proceed with conversation history if model can't handle basic responses
- May need to revert to SmolLM2-360M or investigate ONNX export issues

---

**Session End:** 2025-11-06
**Status:** ChatML fixes complete, critical model hallucination issue discovered
**Next Session:** Debug model behavior, test KV cache reset, investigate conversation history overflow
