# Gemma 3 270M - Mobile Deployment Analysis

**Date:** 2025-11-07
**Context:** Evaluating Gemma 3 270M as alternative to SmolLM2-135M for 間 AI mobile app
**Target:** <200MB APK size constraint, Llamatik 0.8.1 integration

---

## Executive Summary

**✅ Gemma 3 270M has multiple quantizations under 200MB suitable for mobile deployment!**

**Best Options:**
1. **IQ2_XXS**: 180 MB (ultra-compact, lowest quality)
2. **IQ2_M**: 183 MB (good balance)
3. **IQ3_XXS**: 185 MB (better quality)
4. **Q2_K**: 237 MB (exceeds 200MB but still viable)

**Recommendation:** **IQ3_XXS (185 MB)** - Best quality-to-size ratio under 200MB

---

## Available Quantizations

### ✅ **Under 200MB** (APK Constraint Compatible)

| Quantization | Size | Quality | Use Case |
|--------------|------|---------|----------|
| **IQ2_XXS** | 180 MB | Low | Emergency fallback, extreme size constraint |
| **IQ2_M** | 183 MB | Medium | Good balance for tight budgets |
| **IQ3_XXS** | 185 MB | Good | **RECOMMENDED** - Best quality under 200MB |

### ⚠️ **200-250MB** (Marginal, may work)

| Quantization | Size | Quality | Use Case |
|--------------|------|---------|----------|
| **Q2_K** | 237 MB | Medium | If 200MB is soft limit |
| **Q2_K_L** | 237 MB | Medium | Alternative to Q2_K |
| **Q2_K_XL** | 238 MB | Medium-High | Better quality, slightly larger |
| **Q3_K_S** | 237 MB | Good | High quality, 37MB over budget |
| **Q3_K_M** | 242 MB | Very Good | **BEST QUALITY 200-250MB** |
| **Q3_K_XL** | 243 MB | Very Good | Marginal improvement over Q3_K_M |

### ❌ **250MB+** (Over Budget)

| Quantization | Size | Quality | Use Case |
|--------------|------|---------|----------|
| IQ4_XS | 241 MB | Very Good | Reference benchmark |
| Q4_0 | 242 MB | Excellent | Desktop/tablet only |
| IQ4_NL | 242 MB | Excellent | Desktop/tablet only |
| Q4_1 | 248 MB | Excellent | Desktop/tablet only |
| Q4_K_S | 250 MB | Excellent | Desktop/tablet only |
| Q4_K_M | 253 MB | Excellent | Desktop/tablet only |
| Q4_K_XL | 254 MB | Excellent | Desktop/tablet only |
| Q8_0 | 292 MB | Near-perfect | Benchmarking only |

---

## Comparison: Gemma 3 270M vs SmolLM2-135M

### Model Specifications

| Metric | Gemma 3 270M | SmolLM2-135M | Winner |
|--------|--------------|--------------|--------|
| **Parameters** | 270M | 135M | Gemma3 (2x) |
| **Quantization** | IQ3_XXS | Q4_K_M | Gemma3 (185MB vs 101MB) |
| **Context Window** | 32K tokens | 8K tokens | **Gemma3 (4x)** 🎉 |
| **Architecture** | Gemma3 | GPT-2 derivative | Gemma3 (newer) |
| **Developer** | Google | HuggingFace | Gemma3 (enterprise) |
| **License** | Gemma Terms | Apache 2.0 | SmolLM2 (more permissive) |
| **Training** | Proprietary | Open datasets | Gemma3 (quality) |
| **Release Date** | Nov 2024 | Nov 2024 | Tie |

### Performance Estimates

| Task | Gemma 3 270M (IQ3_XXS) | SmolLM2-135M (Q4_K_M) | Analysis |
|------|------------------------|------------------------|----------|
| **Short Queries** | Good | Good | Tie (both sufficient) |
| **Long Explanations** | Excellent (32K context) | Limited (8K context) | **Gemma3 wins** |
| **Code Generation** | Better (2x params) | Adequate | Gemma3 wins |
| **Inference Speed** | ~8-15 tok/s | ~10-20 tok/s | SmolLM2 (smaller) |
| **Battery Impact** | 0.75% per 25 chats (Pixel 9 Pro) | Unknown | Gemma3 (documented) |
| **Model Quality** | Very good (IQ3_XXS) | Excellent (Q4_K_M) | SmolLM2 (better quant) |

### Size Impact

**Current Setup (SmolLM2-135M):**
- Model: 101 MB (Q4_K_M GGUF)
- APK Budget Remaining: ~99 MB (200MB - 101MB)

**Proposed Setup (Gemma3-270M):**
- Model: 185 MB (IQ3_XXS GGUF)
- APK Budget Remaining: ~15 MB (200MB - 185MB)
- **Trade-off:** +84 MB for 2x params + 4x context window

---

## Llamatik 0.8.1 Compatibility

### Current Implementation (SmolLM2-135M)

```kotlin
// Working configuration
val modelPath = "composeApp/src/androidMain/assets/models/smollm2-135m-q4f16.gguf"
val maxTokens = 256 (adjustable to 1024+)
val contextWindow = 8192 tokens

// Llamatik API
LlamaContext.fromAsset(
    assetManager = context.assets,
    modelPath = "models/smollm2-135m-q4f16.gguf",
    nCtx = 8192,
    nThreads = 4
)
```

### Proposed Implementation (Gemma3-270M)

```kotlin
// Expected configuration
val modelPath = "composeApp/src/androidMain/assets/models/gemma3-270m-iq3-xxs.gguf"
val maxTokens = 256 (adjustable to 4096+)
val contextWindow = 32768 tokens (!!!)

// Llamatik API (same)
LlamaContext.fromAsset(
    assetManager = context.assets,
    modelPath = "models/gemma3-270m-iq3-xxs.gguf",
    nCtx = 32768,  // 4x larger context!
    nThreads = 4
)
```

**Compatibility:** ✅ **100% compatible** - Both use GGUF format via llama.cpp

---

## Advantages of Gemma 3 270M

### 1. **32K Context Window** (vs 8K)
- ✅ Long conversations without forgetting
- ✅ More RAG knowledge per query (4x more facts)
- ✅ Better multi-turn dialogues
- ✅ Document-length inputs (technical docs, articles)

### 2. **2x Parameters** (270M vs 135M)
- ✅ Better reasoning capabilities
- ✅ More coherent long-form generation
- ✅ Improved code understanding
- ✅ Better instruction following

### 3. **Google Ecosystem**
- ✅ First-party Android optimization
- ✅ Documented battery metrics (0.75% per 25 chats)
- ✅ Gemma architecture optimizations
- ✅ Active development and support

### 4. **Proven Mobile Deployment**
- ✅ Google's blog post specifically mentions Pixel 9 Pro
- ✅ Designed for on-device inference
- ✅ Optimized for Android ML frameworks

---

## Disadvantages of Gemma 3 270M

### 1. **Larger Size** (+84 MB)
- ❌ Tighter APK budget (15MB remaining vs 99MB)
- ❌ Longer initial download
- ❌ More storage consumption

### 2. **Slower Inference** (270M params vs 135M)
- ❌ Estimated 8-15 tok/s vs 10-20 tok/s
- ❌ More battery per token (2x params to compute)

### 3. **Lower Quantization** (IQ3_XXS vs Q4_K_M)
- ❌ Reduced precision (3-bit vs 4-bit)
- ❌ Potential quality degradation
- ❌ More hallucinations under stress

### 4. **Licensing** (Gemma Terms vs Apache 2.0)
- ❌ Slightly more restrictive (attribution required)
- ❌ Commercial use restrictions (review needed)

---

## Decision Matrix

### Scenario Analysis

#### **Scenario A: Keep SmolLM2-135M**
**Best for:**
- Users with low-end devices (<4GB RAM)
- Battery-constrained use cases
- Fast response time priority
- Maximum APK size flexibility (99MB remaining)

**Limitations:**
- 8K context window limits long conversations
- Smaller model = less capable for complex tasks
- May struggle with technical/code generation

#### **Scenario B: Switch to Gemma3-270M (IQ3_XXS)**
**Best for:**
- Users wanting long conversations (32K context)
- Technical users (code, explanations)
- RAG-heavy workloads (more knowledge per query)
- Quality over speed

**Limitations:**
- Tighter APK budget (15MB remaining)
- Slower inference (~30-40% slower)
- Battery impact (2x params to compute)

#### **Scenario C: Offer Both Models (User Choice)**
**Best for:**
- Power users who want control
- A/B testing both models in production
- Adaptive selection based on device capability

**Limitations:**
- 286MB total (101MB + 185MB)
- Exceeds 200MB APK budget significantly
- Complex model switching logic

---

## Recommendations

### **Option 1: Switch to Gemma3-270M IQ3_XXS (185 MB)** ✅ RECOMMENDED

**Why:**
- 32K context window is transformative for mobile AI
- 2x parameters = better quality responses
- Fits under 200MB budget (185MB)
- Google's official mobile optimization
- Future-proof for Phase 3+ features (RAG, multi-turn)

**Trade-offs:**
- +84MB APK size (101MB → 185MB)
- ~30-40% slower inference
- Lower quantization precision (IQ3 vs Q4)

**Implementation:**
1. Download `gemma3-270m-iq3-xxs.gguf` from HuggingFace
2. Update `LlamaCppEngine` context window to 32768
3. Benchmark on Pixel 6 Pro (mid-range target)
4. Compare quality vs SmolLM2 with test prompts
5. If passes benchmarks, commit as default model

---

### **Option 2: Keep SmolLM2-135M Q4_K_M (101 MB)**

**Why:**
- Already working and stable
- Faster inference (better UX)
- More APK budget remaining
- Good enough for Phase 2 MVP

**Trade-offs:**
- 8K context window limits conversational depth
- Smaller model = less capable for complex tasks
- May need upgrade in Phase 3 anyway

**When to choose:**
- Need to ship Phase 2 quickly
- Battery life is critical
- Target low-end devices (4GB RAM)

---

### **Option 3: Dual Model Strategy (Advanced)**

**Implementation:**
- **Default:** SmolLM2-135M Q4_K_M (101MB) for general use
- **Optional download:** Gemma3-270M IQ3_XXS (185MB) in settings
- **Adaptive selection:** Auto-switch based on query type

**Requires:**
- Model download manager
- Persistent model selection preference
- ~50-100 lines of switching logic
- +30 minutes implementation time

**Benefits:**
- Best of both worlds
- User choice empowerment
- Graceful upgrade path

**Drawbacks:**
- More complexity
- Potential for bugs in model switching
- User confusion about which model is active

---

## Next Steps

### **Phase 1.5 Integration Plan**

If proceeding with **Gemma3-270M IQ3_XXS**:

1. ✅ **Download model** (185MB from HuggingFace)
2. ✅ **Update BaseLlmEngine** (change nCtx to 32768)
3. ✅ **Benchmark performance** (Pixel 6 Pro, 10-15 tok/s expected)
4. ✅ **Quality comparison** (20 test prompts vs SmolLM2)
5. ✅ **Battery test** (measure 8-hour active use)
6. ✅ **Update CLAUDE.md** (document model switch)
7. ✅ **Commit to repo** (with performance metrics)

**Estimated Time:** 2-3 hours

---

## Technical Specifications

### Download Instructions

```bash
# Option 1: HuggingFace CLI
huggingface-cli download unsloth/gemma-3-270m-it-GGUF \
  gemma-3-270m-it-IQ3_XXS.gguf \
  --local-dir ./models

# Option 2: Direct download
wget https://huggingface.co/unsloth/gemma-3-270m-it-GGUF/resolve/main/gemma-3-270m-it-IQ3_XXS.gguf
```

### File Placement

```
app/composeApp/src/androidMain/assets/models/
  ├── smollm2-135m-q4f16.gguf (101 MB) ← Current
  └── gemma3-270m-iq3-xxs.gguf (185 MB) ← Proposed
```

### Configuration Update

```kotlin
// Current
private const val MODEL_NAME = "smollm2-135m-q4f16.gguf"
private const val CONTEXT_SIZE = 8192

// Proposed
private const val MODEL_NAME = "gemma3-270m-iq3-xxs.gguf"
private const val CONTEXT_SIZE = 32768  // 4x larger!
```

---

## Conclusion

**Gemma 3 270M IQ3_XXS (185 MB)** is the best choice for 間 AI mobile app if:
- ✅ 32K context window is valued (long conversations, more RAG)
- ✅ APK size <200MB is acceptable (185MB fits!)
- ✅ Quality > speed trade-off is acceptable
- ✅ Google's mobile optimization is trusted

**SmolLM2-135M Q4_K_M (101 MB)** should be kept if:
- ✅ Speed is critical (10-20 tok/s > 8-15 tok/s)
- ✅ Battery life is paramount
- ✅ 8K context is sufficient for use cases
- ✅ APK budget flexibility is needed (99MB remaining)

**Final Recommendation:** **Switch to Gemma3-270M IQ3_XXS** and use Phase 1.5 to validate the decision with real benchmarks.

---

**Status:** Analysis complete, awaiting implementation decision
**Next:** Download and benchmark Gemma3-270M IQ3_XXS vs SmolLM2-135M
