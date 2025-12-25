# Streaming Inference Improvement Plan

**Status:** Streaming infrastructure ✅ Working | Output quality ⚠️ Needs improvement
**Date:** 2025-11-02
**Phase:** Phase 1 (Core AI)

---

## Current State

### ✅ What's Working
- **Streaming infrastructure** - Token-by-token generation without crashes
- **KV cache management** - Proper tensor lifecycle prevents SIGSEGV
- **Thread safety** - UI updates safely dispatched to main thread
- **Performance** - 15 tok/s on emulator (20-40 tok/s expected on device)
- **Real-time UI** - Tokens appear immediately, auto-scrolling works

### ⚠️ What Needs Improvement
- **Output quality** - Generated text is incoherent gibberish
- **Example output:** "Welcome to W'm improve balance assist Is or to? something im start My not ..."

---

## Root Cause Analysis

### Possible Issues (Prioritized)

#### 1. **Temperature Sampling Issue** (Most Likely)
**Hypothesis:** Temperature of 0.7 might be causing unstable sampling with the small model.

**Evidence:**
- SmolLM2-360M is a small model (360M params)
- Higher temperature = more randomness
- Could be selecting low-probability tokens

**Solution:**
```kotlin
// Try greedy decoding first (temperature = 0)
aiEngine.generateStreaming(
    prompt = prompt,
    temperature = 0.0f  // Pure argmax
)

// Or much lower temperature
temperature = 0.2f  // Conservative sampling
```

**Priority:** HIGH (easy to test, likely fix)

---

#### 2. **Tokenizer/Detokenizer Mismatch**
**Hypothesis:** The tokenizer might not be correctly encoding/decoding.

**Evidence:**
- Output has random characters and fragments
- "W'm", "im", "start" look like incomplete words

**Tests:**
```kotlin
// Test tokenizer round-trip
val text = "Hello, how are you?"
val tokens = tokenizer.encode(text)
val decoded = tokenizer.decode(tokens)
println("Original: $text")
println("Decoded:  $decoded")
assert(text == decoded) // Should match!
```

**Priority:** HIGH (fundamental issue if broken)

---

#### 3. **Model Export Issue**
**Hypothesis:** The ONNX model export might have corrupted weights.

**Evidence:**
- If greedy decoding and tokenizer tests pass, this is likely

**Solution:**
```bash
# Re-export with optimum-cli (current method)
optimum-cli export onnx \
  --model HuggingFaceTB/SmolLM2-360M-Instruct \
  --task text-generation-with-past \
  --cache_dir ./cache \
  smollm2-360m-onnx/

# Try alternative: Manual PyTorch -> ONNX export
python scripts/export_smollm2_onnx.py \
  --model HuggingFaceTB/SmolLM2-360M-Instruct \
  --output smollm2-360m-manual.onnx \
  --quantize int8
```

**Priority:** MEDIUM (time-consuming to test)

---

#### 4. **KV Cache Shape Mismatch**
**Hypothesis:** KV cache dimensions might be slightly wrong.

**Current config:**
```kotlin
val numLayers = 32
val numHeads = 5
val headDim = 64
```

**Verification:**
```python
from transformers import AutoConfig
config = AutoConfig.from_pretrained("HuggingFaceTB/SmolLM2-360M-Instruct")
print(f"Layers: {config.num_hidden_layers}")
print(f"Heads: {config.num_attention_heads}")
print(f"Head dim: {config.hidden_size // config.num_attention_heads}")
```

**Priority:** LOW (values match model architecture)

---

#### 5. **Position IDs or Attention Mask Issue**
**Hypothesis:** Incorrect position tracking during generation.

**Current logic:**
```kotlin
val positionIds = if (isFirstToken) {
    LongArray(currentIds.size) { it.toLong() }
} else {
    longArrayOf(currentSeqLen.toLong())
}
```

**Test:** Add logging to verify position IDs are monotonically increasing.

**Priority:** LOW (logic looks correct)

---

## Action Plan

### Phase A: Quick Wins (2-4 hours)

**A1. Test Greedy Decoding**
```kotlin
// In ChatScreen.kt, change temperature to 0
aiEngine.generateStreaming(
    prompt = prompt,
    maxTokens = 64,  // Reduce for faster testing
    temperature = 0.0f  // Greedy decoding
)
```
**Expected:** If this produces coherent output, temperature was the issue.

---

**A2. Tokenizer Round-Trip Test**
```kotlin
// Add test in SmolLM2Tokenizer.kt
fun testRoundTrip() {
    val testPhrases = listOf(
        "Hello, how are you?",
        "I am M1K3, your AI assistant.",
        "The quick brown fox jumps over the lazy dog."
    )

    testPhrases.forEach { text ->
        val tokens = encode(text)
        val decoded = decode(tokens)
        println("Original: '$text'")
        println("Decoded:  '$decoded'")
        println("Match: ${text == decoded}")
    }
}
```
**Expected:** All phrases should decode perfectly.

---

**A3. Single Token Test**
```kotlin
// Generate just 1 token to isolate the issue
aiEngine.generateStreaming(
    prompt = "Hello",
    maxTokens = 1,
    temperature = 0.0f
) { token ->
    println("First token: '$token'")
}
```
**Expected:** Should be a sensible continuation like "!" or ","

---

### Phase B: Model Validation (4-8 hours)

**B1. Python Reference Implementation**
```python
# scripts/test_smollm2_reference.py
from transformers import AutoTokenizer, AutoModelForCausalLM

model = AutoModelForCausalLM.from_pretrained(
    "HuggingFaceTB/SmolLM2-360M-Instruct"
)
tokenizer = AutoTokenizer.from_pretrained(
    "HuggingFaceTB/SmolLM2-360M-Instruct"
)

prompt = "Hey"
inputs = tokenizer(prompt, return_tensors="pt")
outputs = model.generate(**inputs, max_new_tokens=50, do_sample=False)
print(tokenizer.decode(outputs[0]))
```
**Expected:** Compare output with Android app's greedy decoding.

---

**B2. ONNX Model Validation**
```python
# scripts/test_onnx_inference.py
import onnxruntime as ort
import numpy as np

session = ort.InferenceSession("smollm2-360m-q4f16.onnx")
inputs = {
    "input_ids": np.array([[19556]]),  # "Hello"
    "attention_mask": np.array([[1]]),
    "position_ids": np.array([[0]])
}

# Add empty KV cache inputs
for i in range(32):
    inputs[f"past_key_values.{i}.key"] = np.zeros((1, 5, 0, 64), dtype=np.float16)
    inputs[f"past_key_values.{i}.value"] = np.zeros((1, 5, 0, 64), dtype=np.float16)

outputs = session.run(None, inputs)
logits = outputs[0][0, -1, :]
next_token = np.argmax(logits)
print(f"Next token ID: {next_token}")
print(f"Next token: {tokenizer.decode([next_token])}")
```
**Expected:** Should match Android app's first token.

---

**B3. Re-export Model**
```bash
# Try different export settings
optimum-cli export onnx \
  --model HuggingFaceTB/SmolLM2-360M-Instruct \
  --task text-generation-with-past \
  --atol 1e-4 \
  --framework pt \
  smollm2-360m-onnx-v2/
```

---

### Phase C: Advanced Debugging (8-16 hours)

**C1. Logits Analysis**
```kotlin
// In sampleNextToken(), log top-5 predictions
val topK = logits.indices
    .sortedByDescending { logits[it] }
    .take(5)

topK.forEach { idx ->
    val token = tokenizer.decode(longArrayOf(idx.toLong()))
    println("Top token: $idx = '$token' (logit=${logits[idx]})")
}
```
**Expected:** Top tokens should be sensible words, not random IDs.

---

**C2. Compare PyTorch vs ONNX Logits**
- Run same input through PyTorch model and ONNX model
- Compare logit distributions
- Look for numerical differences

---

**C3. Attention Weights Visualization**
- Extract attention weights from ONNX outputs
- Verify they're not NaN or all zeros
- Check if attention patterns make sense

---

## Success Criteria

### Minimum Viable Quality
- Greedy decoding (temp=0) produces grammatically correct English
- Tokenizer round-trip test passes 100%
- First token is sensible continuation of prompt

### Production Quality
- Temperature 0.7 produces coherent (if creative) text
- Responses are contextually relevant to prompts
- No hallucinations or repetitive loops

---

## Timeline Estimate

| Phase | Tasks | Duration | Confidence |
|-------|-------|----------|------------|
| **A: Quick Wins** | Greedy test, tokenizer test | 2-4 hours | HIGH (80% chance of fix) |
| **B: Model Validation** | Python reference, ONNX test | 4-8 hours | MEDIUM (50% chance of fix) |
| **C: Advanced** | Logits analysis, re-export | 8-16 hours | LOW (fallback if A+B fail) |

**Recommended:** Start with Phase A. If greedy decoding works, we know temperature is the issue. If tokenizer test fails, we know encoding/decoding is broken.

---

## Next Steps

1. **Immediate:** Test greedy decoding (temperature=0.0f)
2. **Short-term:** Tokenizer round-trip validation
3. **Medium-term:** Python reference comparison
4. **Long-term:** Consider alternative models (SmolLM2-135M, Phi-2)

---

**Last Updated:** 2025-11-02
**Status:** Ready for Phase A testing
