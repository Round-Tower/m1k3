# Gemma 3 270M Deployment Status

**Date:** 2025-11-07
**Status:** ❌ REVERTED TO SMOLLM2-135M - Q2_K QUANTIZATION TOO AGGRESSIVE

**Final Decision:** SmolLM2-135M Q4_K_M (101 MB) remains the production model

---

## Deployment Summary

### ✅ Completed Steps

1. **Downloaded Model** ✅
   - File: `gemma-3-270m-it-UD-IQ3_XXS.gguf`
   - Size: 176 MB (slightly smaller than advertised 185 MB)
   - Location: `composeApp/src/androidMain/assets/models/`

2. **Updated LlamaCppEngine.kt** ✅
   - Changed model filename to `gemma-3-270m-it-UD-IQ3_XXS.gguf`
   - Updated documentation (32K context window noted)
   - Added logging for Gemma 3 initialization
   - Compilation successful (BUILD SUCCESSFUL in 5s)

3. **Deployed to Emulator** ✅
   - Device: Pixel 9a (AVD) - Android 16
   - APK installed successfully (2m 7s)
   - Logcat monitoring active

---

## What's Different (SmolLM2 → Gemma3)

| Aspect | SmolLM2-135M | Gemma 3 270M | Change |
|--------|--------------|--------------|--------|
| **Model File** | `smollm2-135m-q4.gguf` | `gemma-3-270m-it-UD-IQ3_XXS.gguf` | Changed |
| **File Size** | 101 MB | 176 MB | +75 MB |
| **Parameters** | 135M | 270M | 2x larger |
| **Context Window** | 8K tokens | 32K tokens | 4x larger! |
| **Quantization** | Q4_K_M | IQ3_XXS | Lower precision |
| **Engine** | Llamatik 0.8.1 | Llamatik 0.8.1 | Same |

---

## Next Steps for User

### 🧪 **Test 1: Model Loading**

**Action:**
1. Launch the 間 AI app on the emulator
2. Navigate to the Chat screen
3. Send any message (e.g., "Hello!")

**Expected Behavior:**
- First launch: "📥 Copying Gemma 3 270M GGUF to internal storage (one-time operation)..."
- "📖 Loading model with Llamatik..."
- "✅ [LlamaCppEngine] Initialization complete"
- Model should load in ~10-20 seconds (first time)

**Look for in logs:**
```
🚀 [LlamaCppEngine] Starting initialization...
   Model: IQ3_XXS quantization (176 MB)
   Context: 32K tokens
   ✓ Gemma 3 270M already in storage (176 MB)
   📖 Loading model with Llamatik...
✅ [LlamaCppEngine] Initialization complete
```

---

### 🧪 **Test 2: Simple Query**

**Action:**
Send: "What is 2+2?"

**Expected Behavior:**
- Response should appear quickly
- Look for performance metrics in logs:
  ```
  ✅ [LlamaCppEngine] Generation complete
     Response length: XXX chars
     Tokens: XX
     Time: XXXms (X.X tok/s)
  ```

**Target Performance:**
- Inference speed: 8-15 tok/s on emulator
- Response quality: Should be coherent

---

### 🧪 **Test 3: Long Context Test**

**Action:**
Send a long prompt (e.g., "Tell me a detailed story about AI"):

**Expected Behavior:**
- Gemma3's 32K context window should allow longer responses
- Should not cut off prematurely
- Response should remain coherent throughout

---

### 🧪 **Test 4: Technical Query**

**Action:**
Send: "Explain how neural networks work"

**Expected Behavior:**
- Gemma3 (270M params) should provide better quality explanation than SmolLM2 (135M)
- Response should be detailed and accurate

---

## Performance Targets

| Metric | Target | Notes |
|--------|--------|-------|
| **Model Load Time** | <30s | First time only (copy + load) |
| **Inference Speed** | 8-15 tok/s | Emulator (slower than device) |
| **Response Quality** | Better than SmolLM2 | 2x params = better reasoning |
| **Context Handling** | 32K tokens | 4x larger than SmolLM2 |
| **Memory Usage** | <2GB | Should be manageable |

---

## Monitoring Commands

**Check Logs:**
```bash
adb logcat | grep -E "(LlamaCppEngine|Gemma|MODEL)" --color
```

**Check Memory:**
```bash
adb shell dumpsys meminfo app.m1k3.ai.assistant
```

**Check Model File:**
```bash
adb shell ls -lh /data/user/0/app.m1k3.ai.assistant/files/gemma-3-270m-it-UD-IQ3_XXS.gguf
```

---

## Known Issues to Watch For

1. **Model Copy Takes Time**
   - First launch: 10-30 seconds to copy 176 MB from assets
   - Progress logged but no UI indicator (could add in future)

2. **IQ3_XXS Quantization**
   - Lower precision than Q4_K_M
   - May have slight quality degradation vs SmolLM2
   - Trade-off: Size (176 MB vs 253 MB Q4_K_M)

3. **Llamatik Limitations**
   - No temperature/sampling control
   - Relies on prompt engineering
   - Context window handled automatically by Llamatik

---

## Rollback Plan (If Issues)

If Gemma3 has critical issues:

1. **Quick Rollback:**
   ```bash
   # In LlamaCppEngine.kt line 89:
   val modelFile = File(context.filesDir, "smollm2-135m-q4.gguf")

   # Line 95:
   context.assets.open("models/smollm2-135m-q4.gguf").use { input ->
   ```

2. **Rebuild and redeploy:**
   ```bash
   ./gradlew :composeApp:installDebug
   ```

3. **Delete Gemma3 model from device:**
   ```bash
   adb shell rm /data/user/0/app.m1k3.ai.assistant/files/gemma-3-270m-it-UD-IQ3_XXS.gguf
   ```

---

## Success Criteria

**Gemma3 deployment is successful if:**
- ✅ Model loads without crashes
- ✅ Inference speed: 8-15+ tok/s on emulator
- ✅ Response quality: Equal or better than SmolLM2
- ✅ No hallucinations or gibberish
- ✅ Stop tokens work correctly (clean responses)
- ✅ Memory usage acceptable (<2GB)

**If all criteria met:** Proceed with device testing (Pixel 6 Pro)
**If issues found:** Debug or rollback to SmolLM2

---

## Gemma3 Experiment Results (2025-11-07)

### Test Results: Q2_K Quantization

**Performance:**
- ❌ 0-5 token responses (mostly empty or single characters)
- ❌ Inconsistent generation (sometimes works, mostly fails)
- ❌ Context limited to 4096 tokens (not 32K as advertised)
- ✅ Model loaded successfully (no crashes)
- ✅ File size: 226 MB (under 250MB budget)

**Root Cause:**
Q2_K (2-bit) quantization is **too aggressive** for Gemma3-270M. The model loses most of its capability at this precision level.

**Comparison: Gemma3 Q2_K vs SmolLM2 Q4_K_M**

| Metric | Gemma3 270M Q2_K | SmolLM2-135M Q4_K_M | Winner |
|--------|------------------|---------------------|--------|
| **Tokens Generated** | 0-5 tokens | 340-640 tokens | SmolLM2 |
| **Speed** | N/A (too few tokens) | 45-47 tok/s | SmolLM2 |
| **Quality** | Unusable | Coherent, clean | SmolLM2 |
| **Size** | 226 MB | 101 MB | SmolLM2 |
| **Quantization** | 2-bit (poor) | 4-bit (good) | SmolLM2 |
| **Context Window** | 4096 tokens | 8192 tokens | SmolLM2 |
| **Chat Template** | Complex (Gemma3-specific) | Simple | SmolLM2 |

### Lessons Learned

1. **Quantization matters more than model size**
   - SmolLM2 Q4_K_M (135M @ 4-bit) >> Gemma3 Q2_K (270M @ 2-bit)
   - Quality trumps parameter count at aggressive quantizations

2. **Llamatik context limits**
   - Reports 4096 token context (not model's native 32K)
   - May be Llamatik 0.8.1 limitation

3. **Chat template compatibility**
   - Gemma3's `<start_of_turn>` format incompatible with Llamatik's system parameter
   - SmolLM2's simpler format works better with Llamatik

### Future Recommendations

**If pursuing Gemma3 in future:**
- Try **Q4_K_M quantization** (253 MB) for proper quality
- Accept 27% size increase over 200MB budget
- Test with newer Llamatik version for 32K context

**Current Decision:**
**Keep SmolLM2-135M Q4_K_M** - proven stable, fast (45 tok/s), coherent responses

---

**Status:** ✅ REVERTED TO SMOLLM2-135M Q4_K_M
**Performance:** 45-47 tok/s, 340-640 tokens/response, clean stop token detection
**Next:** Continue with Phase 2 development using SmolLM2
