# Phase 1.5: Model Evaluation & RAG Optimization

**Duration:** 1 week (Week 7)
**Total Tickets:** 12 tickets (8 original + 4 new)
**Estimated Hours:** 48 hours (revised)
**Priority Distribution:** P0: 6 tickets, P1: 4 tickets, P2: 2 tickets
**Status:** 🟡 In Progress (6/12, 50%)

---

## Overview

Phase 1.5 is a critical sprint inserted between Phase 2 (Memory) and Phase 3 (Knowledge) to address two strategic objectives:

1. **Model Evaluation**: Integrate and benchmark Gemma 3:270m against SmolLM2-360M to select the best on-device model for production
2. **RAG System Fixes**: Repair broken retrieval-augmented generation system with semantic search and prompt guardrails

### Rationale

**Why Phase 1.5 Exists:**
- Phase 2 provides embedding infrastructure (MiniLM-L6 + HNSW) needed for semantic RAG
- Current keyword-based RAG produces irrelevant results (e.g., "teach me about AI" retrieves "online shopping")
- Model selection must happen before building advanced features in Phase 3+
- Fixing RAG foundation prevents building on broken architecture

**Why Now:**
- Phase 2 embeddings confirmed operational (MiniLM-L6 + HNSW working)
- Phase 3 assumes working RAG - must fix before expanding knowledge features
- Gemma 3:270m offers potential improvements: smaller size (120MB vs 180MB), massive context (256K vs 24K), vision capabilities

---

## 🚨 Critical Findings: APK Size Analysis (2025-11-04)

### Problem: Current APK Exceeds 200MB Budget

**Current State:**
```
SmolLM2-360M (q4f16):      180 MB
MiniLM-L6 (embeddings):     90 MB
App code + assets:          20 MB
────────────────────────────────
Total APK Size:            290 MB ❌ (45% over budget!)
```

**Gemma 3:270m Size Reality Check:**
```
Model: google/gemma-3-270m-it (ONNX q4f16)
Expected:  120 MB (based on parameter count)
Actual:    427 MB ❌ (3.5x larger than expected!)

Reasons for size bloat:
1. Vocabulary: 256K tokens (vs SmolLM2 49K) = ~200MB overhead
2. Context embeddings: 256K context requires larger KV cache
3. ONNX export overhead: Additional operators for vision readiness
```

**Critical Decisions Made:**

1. **Defer Gemma 3 to Dynamic Delivery**
   - 427 MB too large for initial APK
   - Ship as downloadable "Advanced Model" feature
   - User opts-in via Settings → AI Models → Download Gemma 3

2. **Pivot to SmolLM2-135M**
   - Smaller sibling: 135M params vs 360M
   - Expected size: ~70-80 MB (vs 180 MB = 56% reduction!)
   - Trade-off: Slightly lower quality for size constraint

3. **Optimize MiniLM Embeddings**
   - Current: all-MiniLM-L6-v2 (90 MB, 384-dim)
   - Target: paraphrase-MiniLM-L3-v2 (50 MB, 384-dim)
   - Alternative: INT8 quantization (90MB → 45 MB)

**Revised Size Budget:**
```
SmolLM2-135M (q4f16):       70 MB  ✅
MiniLM-L3 (optimized):      50 MB  ✅
App code + assets:          20 MB
Knowledge base:             30 MB
────────────────────────────────
Total APK Size:            170 MB  ✅ (15% under budget!)
```

**New Tickets Added:**
- **PHASE1.5-005**: Evaluate SmolLM2-135M vs 360M (quality/performance)
- **PHASE1.5-006**: Optimize MiniLM embeddings (90MB → 50MB)
- **PHASE1.5-007**: Design dynamic model delivery for Gemma 3
- **PHASE1.5-009**: APK size audit and ProGuard optimization

---

## Strategic Decisions

### Decision 1: Gemma 3:270m Only (Not Qwen2.5)
**Rationale:** Focus on thorough comparison rather than spreading effort across 3 models. Qwen2.5:271M can be evaluated in Phase 5 if time permits.

### Decision 2: Ship Only Winning Model
**Rationale:** Keep APK <200MB constraint. Dynamic feature modules add complexity; single best model provides cleaner UX.

### Decision 3: Medium Testing Rigor
**Rationale:** 20 test prompts with automated scoring + manual spot-checks balances confidence with timeline.

### Decision 4: Compress Phase 3 & 5
**Rationale:** Phase 1.5 semantic RAG work reduces Phase 3 scope. Model selection in Phase 1.5 reduces Phase 5 testing.

---

## Critical Problems Being Solved

### Problem 1: Poor RAG Retrieval Quality

**Current State (Broken):**
```
User Query: "Can you teach me about AI?"

Retrieved Facts (WRONG):
1. Category: Academic Skills - "Problem-solving techniques"
2. Category: Academic Skills - "Data analysis methods"
3. Category: Digital Life - "Online shopping platforms"

Similarity Scores: 0.2-0.4 (effectively random)

Model Response:
"AI is related to problem-solving and data analysis. It's used in
online shopping to recommend products..." ❌ (forced to use irrelevant context)
```

**Root Causes:**
1. **Keyword-based retrieval** - Uses SQL `LIKE` and rule-based category matching
2. **No semantic understanding** - Can't tell "AI" in "train" is irrelevant to "AI" query
3. **Zero similarity threshold** - Retrieves anything vaguely matched

**Phase 1.5 Solution:**
```
User Query: "Can you teach me about AI?"

Retrieved Facts (CORRECT):
1. Category: AI/ML - "What is Artificial Intelligence?" (similarity: 0.89)
2. Category: AI/ML - "What is Machine Learning?" (similarity: 0.87)
3. Category: AI/ML - "Neural Networks explained" (similarity: 0.82)

Model Response:
"Artificial Intelligence (AI) is the simulation of human intelligence
by machines... Machine Learning is a subset of AI that enables systems
to learn from experience..." ✅ (uses relevant, high-quality context)
```

**Changes:**
- Replace `KnowledgeRetrievalService` with `SemanticRetrievalService` (MiniLM-L6 embeddings)
- Raise similarity threshold from ~0.0 to 0.6+
- Add 20+ AI/ML facts to knowledge base

---

### Problem 2: Prompt Template Forces Irrelevant Usage

**Current Prompt (PromptEnhancer.kt:55):**
```kotlin
"...prioritize the database information."
// ☝️ Model MUST use retrieved facts, even if irrelevant
```

**Phase 1.5 Solution:**
```kotlin
"""
Please provide a helpful, accurate answer using the knowledge above
**only if it is directly relevant to the user's question**.

Guidelines:
- If retrieved knowledge addresses the question, use it in your response
- If tangentially related, acknowledge briefly then use general knowledge
- If not relevant, ignore it completely and answer from general knowledge
- Prioritize answering the user's actual question over using the database
"""
```

**Impact:** Model can now ignore low-quality retrievals instead of generating nonsense.

---

### Problem 3: Max Tokens Too Short for Educational Queries

**Current Behavior:**
```kotlin
// SmolLM2Engine.kt - Device-adaptive but CAPPED
fun getOptimalMaxTokens(): Int {
    return when {
        deviceRamGB >= 12 -> 512   // Too short for "teach me"
        deviceRamGB >= 6 -> 256    // Cuts off mid-explanation
        // ...
    }
}
```

**Example Failure:**
```
User: "Can you teach me about quantum entanglement?"
Response: "Quantum entanglement is a phenomenon where particles become
connected such that the state of one particle affects..." [CUTOFF AT 256 TOKENS]
```

**Phase 1.5 Solution:**
```kotlin
// Adaptive based on query type
val config = AdaptiveGeneration.getConfig(query, deviceRamGB)

// Educational queries:
if (query.contains("teach me")) {
    maxTokens = 1024  // Full explanation (8GB device)
    temperature = 0.6f
}

// Casual chat:
if (isCasualChat(query)) {
    maxTokens = 256   // Short, friendly
    temperature = 0.7f
}
```

**Impact:** "Teach me" queries get 1024 tokens for complete explanations.

---

## Tickets

### PHASE1.5-001: Gemma 3:270m ONNX Export & Quantization ⚠️ DEFERRED

**Priority:** P0 → P2 (Deferred to dynamic delivery)
**Estimated Hours:** 6h
**Status:** [DEFERRED] Export tooling created, model too large for APK

**Description:**
Export Gemma 3:270m from HuggingFace to ONNX format with INT4 quantization for mobile deployment.

**Model Specs:**
- **Parameters:** 270M (vs SmolLM2 360M - smaller!)
- **Context:** 256K tokens (vs SmolLM2 24K - 10.6x larger!)
- **Size:** ~120MB quantized (vs SmolLM2 180MB - 33% smaller!)
- **Capabilities:** Text + Vision (Phase 4+ ready)

**Technical Approach:**
```python
# export_gemma3_270m.py
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
from optimum.onnxruntime import ORTModelForCausalLM
from optimum.onnxruntime.configuration import AutoQuantizationConfig

def export_gemma3_270m():
    model_id = "google/gemma-3-270m-it"
    output_dir = "models/gemma3-270m-onnx"

    # Load model
    model = AutoModelForCausalLM.from_pretrained(
        model_id,
        torch_dtype=torch.float32,
        low_cpu_mem_usage=True
    )
    tokenizer = AutoTokenizer.from_pretrained(model_id)

    # Export to ONNX
    ort_model = ORTModelForCausalLM.from_pretrained(
        model_id,
        export=True,
        provider="CPUExecutionProvider"
    )

    # INT4 quantization
    quantization_config = AutoQuantizationConfig.arm64(
        is_static=False,
        per_channel=True
    )
    ort_model.quantize(
        save_dir=output_dir,
        quantization_config=quantization_config
    )

    # Save tokenizer
    tokenizer.save_pretrained(output_dir)

    print(f"✅ Export complete: {output_dir}")
    print(f"📊 Model size: {get_size_mb(output_dir)} MB")
```

**Acceptance Criteria:**
- [ ] Gemma 3:270m exported to ONNX format
- [ ] INT4 quantization applied successfully
- [ ] Model size <150MB (target: ~120MB)
- [ ] Validation test generates coherent text
- [ ] Tokenizer files exported alongside model
- [ ] Export script documented with usage

**Tests:**
- [ ] `test_gemma3_export.py`: Validates ONNX model structure
- [ ] `test_gemma3_inference.py`: Tests basic generation
- [ ] `test_model_size.py`: Confirms <150MB constraint

**Files Created:**
- `app/export_gemma3_270m.py` - Export script
- `app/validate_gemma3.py` - Validation script
- `app/models/gemma3-270m-onnx/` - Output directory

**Dependencies:** None (can start immediately)
**Blocks:** PHASE1.5-002 (Gemma3 engine implementation)

---

### PHASE1.5-002: Gemma3Engine Implementation

**Priority:** P0
**Estimated Hours:** 5h
**Status:** [✅ COMPLETE] Framework created, ready for dynamic delivery

**Description:**
Create Gemma3Engine class paralleling SmolLM2Engine architecture for fair comparison.

**Implementation:**

File: `app/composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/ai/Gemma3Engine.kt`

**Key Features:**
- Device-adaptive context windows (2K-32K based on RAM)
- Streaming generation with token-by-token callbacks
- KV cache management for efficient inference
- Proper resource cleanup (try-finally blocks)

**API Surface (matches SmolLM2Engine):**
```kotlin
class Gemma3Engine(private val context: Context) {
    suspend fun initialize()
    suspend fun generate(prompt: String, maxTokens: Int): GenerationResult
    suspend fun generateStreaming(
        prompt: String,
        maxTokens: Int,
        onToken: (String) -> Unit
    ): GenerationResult
    fun close()
}
```

**Device-Adaptive Context:**
```kotlin
private fun getOptimalContextWindow(): Int {
    return when {
        deviceRamGB >= 12 -> 32000  // Larger than SmolLM2 (24K)
        deviceRamGB >= 8 -> 24000
        deviceRamGB >= 6 -> 12000
        deviceRamGB >= 4 -> 6000
        else -> 2000
    }
}
```

**Acceptance Criteria:**
- [ ] Gemma3Engine matches SmolLM2Engine API
- [ ] Supports streaming generation
- [ ] Device-adaptive context windows
- [ ] KV cache management functional
- [ ] Tokenizer integration working
- [ ] Resource cleanup (no leaks)

**Tests:**
- [ ] `Gemma3EngineTest.kt`: Unit tests for generation
- [ ] `Gemma3StreamingTest.kt`: Streaming inference
- [ ] `Gemma3ResourceTest.kt`: Memory leak validation

**Files Created:**
- `app/composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/ai/Gemma3Engine.kt`
- `app/composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/ai/Gemma3Tokenizer.kt`
- `app/composeApp/src/androidTest/kotlin/app/m1k3/ai/assistant/ai/Gemma3EngineTest.kt`

**Dependencies:** PHASE1.5-001 (ONNX export)
**Blocks:** PHASE1.5-003 (Model registry)

---

### PHASE1.5-003: Multi-Model Registry & Selection

**Priority:** P1
**Estimated Hours:** 4h
**Status:** [ ]

**Description:**
Create model registry for managing multiple AI engines (SmolLM2, Gemma3, future Qwen2.5). Allows runtime model switching.

**Architecture:**

```kotlin
sealed class AIModel(
    val id: String,
    val displayName: String,
    val params: String,
    val sizeMB: Int,
    val contextWindow: Int,
    val capabilities: Set<ModelCapability>
) {
    object SmolLM2_360M : AIModel(
        id = "smollm2-360m",
        displayName = "SmolLM2 360M",
        params = "360M",
        sizeMB = 180,
        contextWindow = 24000,
        capabilities = setOf(ModelCapability.TEXT_GENERATION)
    )

    object Gemma3_270M : AIModel(
        id = "gemma3-270m",
        displayName = "Gemma 3 270M",
        params = "270M",
        sizeMB = 120,
        contextWindow = 256000,
        capabilities = setOf(
            ModelCapability.TEXT_GENERATION,
            ModelCapability.VISION  // Phase 4+
        )
    )
}

enum class ModelCapability {
    TEXT_GENERATION,
    VISION,
    MULTI_MODAL
}
```

**Features:**
- Factory pattern for engine instantiation
- Lazy initialization (only load active model)
- Settings-based model selection
- Memory isolation (only 1 model loaded at a time)

**Acceptance Criteria:**
- [ ] Registry manages SmolLM2 and Gemma3 engines
- [ ] Lazy loading (only active model in memory)
- [ ] Model switching without app restart
- [ ] Metadata includes capabilities, size, context
- [ ] Settings integration for default model

**Tests:**
- [ ] `ModelRegistryTest.kt`: Model loading/switching
- [ ] `ModelRegistryTest.kt`: Memory isolation validation

**Files Created:**
- `app/shared/src/commonMain/kotlin/ai/ma/ai/ModelRegistry.kt`
- `app/shared/src/commonMain/kotlin/ai/ma/ai/AIModel.kt`
- `app/shared/src/commonTest/kotlin/ai/ma/ai/ModelRegistryTest.kt`

**Dependencies:** PHASE1.5-002 (Gemma3Engine)
**Blocks:** PHASE1.5-004 (Comparison framework)

---

### PHASE1.5-004: Model Comparison & Benchmarking Framework ⚠️ CRITICAL

**Priority:** P0
**Estimated Hours:** 5h
**Status:** [ ]

**Description:**
Create objective testing framework to compare SmolLM2 vs Gemma3 across quality, speed, and size metrics.

**Evaluation Dimensions:**

**1. Performance Benchmarks:**
- Tokens per second (emulator + device)
- First token latency
- Memory usage (ActivityManager)
- Battery impact (8-hour simulation)

**2. Quality Metrics:**
- Response coherence (heuristic scoring)
- Instruction following (20 test prompts)
- Factual accuracy (spot-check validation)
- RAG utilization (uses retrieved knowledge appropriately)

**3. Resource Metrics:**
- Model size (MB)
- RAM consumption (peak + sustained)
- Context window capacity
- Capabilities (text, vision, multi-modal)

**Test Suite (20 Prompts):**

**Category 1: Educational (5 prompts)**
- "Can you teach me about AI?"
- "Explain quantum entanglement in simple terms"
- "How does photosynthesis work?"
- "What is the difference between ML and deep learning?"
- "Teach me about blockchain technology"

**Category 2: Technical (5 prompts)**
- "Write a Python function to reverse a string"
- "Debug this code: [code snippet]"
- "Explain how HTTP works"
- "What is Big O notation?"
- "How do you optimize database queries?"

**Category 3: Conversational (5 prompts)**
- "Hello, how are you?"
- "Tell me a joke"
- "What's your favorite color?"
- "Can you help me plan my day?"
- "I'm feeling stressed about work"

**Category 4: Factual (5 prompts)**
- "Who invented the telephone?"
- "What is the capital of France?"
- "When did World War II end?"
- "What is the speed of light?"
- "Who wrote Hamlet?"

**Scoring System:**
```kotlin
data class ModelScore(
    val performance: PerformanceScore,
    val quality: QualityScore,
    val resources: ResourceScore,
    val totalScore: Double  // Weighted average
)

data class PerformanceScore(
    val tokensPerSecond: Float,
    val firstTokenLatencyMs: Long,
    val memoryUsageMB: Int,
    val score: Double  // 0-100
)

data class QualityScore(
    val coherence: Double,       // 0-1
    val instructionFollowing: Double,  // 0-1
    val factualAccuracy: Double, // 0-1
    val ragUtilization: Double,  // 0-1
    val score: Double  // 0-100
)
```

**Decision Criteria:**
- If Gemma3 wins on 2/3 dimensions → **Make default**
- If SmolLM2 wins → Keep SmolLM2 default
- If tie → Prefer smaller model (Gemma3 120MB vs SmolLM2 180MB)

**Acceptance Criteria:**
- [ ] Performance benchmarks (tokens/sec, latency, memory)
- [ ] Quality evaluation (20 test prompts)
- [ ] RAG-specific comparison (knowledge utilization)
- [ ] Automated comparison report (Markdown)
- [ ] Final recommendation with justification

**Tests:**
- [ ] `ModelComparisonTest.kt`: Full comparison suite
- [ ] `PerformanceBenchmarkTest.kt`: Speed/resource tests
- [ ] `QualityEvaluationTest.kt`: Response quality scoring

**Files Created:**
- `app/composeApp/src/androidTest/kotlin/ai/ma/benchmark/ModelComparisonTest.kt`
- `app/composeApp/src/androidTest/kotlin/ai/ma/benchmark/PerformanceBenchmarkTest.kt`
- `app/composeApp/src/androidTest/kotlin/ai/ma/benchmark/QualityEvaluationTest.kt`
- `app/docs/MODEL_COMPARISON_REPORT.md` - Final results

**Dependencies:** PHASE1.5-003 (Model registry)
**Blocks:** Final model selection decision

---

### PHASE1.5-005: Fix RAG Retrieval Quality ⚠️ CRITICAL

**Priority:** P0
**Estimated Hours:** 4h
**Status:** [ ]

**Description:**
Replace keyword-based RAG with semantic embeddings using Phase 2 infrastructure (MiniLM-L6 + HNSW).

**Current vs New:**

| Aspect | Current (Keyword) | New (Semantic) |
|--------|-------------------|----------------|
| **Method** | SQL `LIKE` + rules | MiniLM-L6 embeddings |
| **Index** | None | HNSW vector index |
| **Threshold** | ~0.0 (accepts all) | 0.6+ (high quality only) |
| **Example** | "AI" → "online shopping" | "AI" → "What is AI?" |
| **Similarity** | 0.2-0.4 | 0.7-0.9 |

**Implementation:**

File: `app/composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/knowledge/SemanticRetrievalService.kt`

```kotlin
class SemanticRetrievalService(
    private val database: MaDatabase,
    private val embeddingEngine: EmbeddingEngine,  // Phase 2
    private val vectorIndex: VectorSearchManager    // Phase 2
) {
    suspend fun retrieve(
        query: String,
        limit: Int = 3,
        minSimilarity: Float = 0.6f  // High threshold!
    ): List<RetrievedFact> {
        // 1. Embed query with MiniLM-L6
        val queryEmbedding = embeddingEngine.embed(query)

        // 2. Search HNSW index
        val searchResults = vectorIndex.searchKnowledge(
            queryEmbedding = queryEmbedding,
            topK = limit * 2,
            minSimilarity = minSimilarity
        )

        // 3. Load facts from database
        val factIds = searchResults.map { it.factId }
        val facts = database.triviaFactQueries
            .getByIds(factIds)
            .executeAsList()

        // 4. Combine with similarity scores
        return facts.map { fact ->
            val similarity = searchResults
                .find { it.factId == fact.id }?.similarity ?: 0f
            RetrievedFact(
                fact = fact,
                relevanceScore = (similarity * 0.7) + (fact.importance * 0.3),
                similarityScore = similarity  // NEW: expose for debugging
            )
        }
        .filter { it.similarityScore >= minSimilarity }
        .sortedByDescending { it.relevanceScore }
        .take(limit)
    }
}
```

**Acceptance Criteria:**
- [ ] Semantic search using MiniLM-L6 embeddings
- [ ] HNSW vector index integration
- [ ] Similarity threshold raised to 0.6+
- [ ] Retrieval debug info shows similarity scores
- [ ] Test: "teach me about AI" retrieves AI facts (>0.7)

**Tests:**
- [ ] `SemanticRetrievalTest.kt`: AI query retrieves AI facts
- [ ] `SemanticRetrievalTest.kt`: Irrelevant results filtered (<0.6)
- [ ] `SemanticRetrievalTest.kt`: Compare old vs new quality

**Files Created:**
- `app/composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/knowledge/SemanticRetrievalService.kt`
- `app/composeApp/src/commonTest/kotlin/app/m1k3/ai/assistant/knowledge/SemanticRetrievalTest.kt`

**Dependencies:** PHASE2-001 through PHASE2-006 (embeddings operational)
**Blocks:** PHASE1.5-006 (Prompt fixes)

---

### PHASE1.5-006: Fix Prompt Template with Relevance Guardrails ⚠️ CRITICAL

**Priority:** P0
**Estimated Hours:** 3h
**Status:** [ ]

**Description:**
Fix broken prompt template that forces model to use irrelevant knowledge. Add guardrails allowing model to ignore bad retrievals.

**Current Problem:**
```kotlin
// PromptEnhancer.kt line 55 (CURRENT):
"...prioritize the database information."
// Forces model to use irrelevant knowledge!
```

**New Prompt Template:**
```kotlin
"""
Retrieved knowledge from database:

1. [Relevance: 89%]
   Q: What is Artificial Intelligence?
   A: AI is the simulation of human intelligence by machines...

2. [Relevance: 87%]
   Q: What is Machine Learning?
   A: ML is a subset of AI that enables systems to learn...

User question: Can you teach me about AI?

Please provide a helpful, accurate answer using the knowledge above
**only if it is directly relevant to the user's question**.

Guidelines:
- If retrieved knowledge addresses the question, use it in your response
- If tangentially related, acknowledge briefly then use general knowledge
- If not relevant, ignore it completely and answer from general knowledge
- Prioritize answering the user's actual question over using the database

Focus on being helpful and accurate above all else.
"""
```

**Key Changes:**
1. **Show similarity scores** (transparency)
2. **Explicit permission to ignore** irrelevant knowledge
3. **Prioritize user's question** over database usage
4. **Pre-filter low-similarity facts** before injection (<0.6 filtered out)

**Acceptance Criteria:**
- [ ] Prompt instructs model to ignore irrelevant knowledge
- [ ] Similarity scores shown in prompt
- [ ] Pre-filters facts <0.6 similarity
- [ ] Guidelines prioritize user's question
- [ ] Test: Irrelevant knowledge doesn't pollute response

**Tests:**
- [ ] `PromptEnhancerTest.kt`: Low-similarity facts filtered
- [ ] `PromptEnhancerTest.kt`: Prompt includes relevance instructions
- [ ] `PromptEnhancerTest.kt`: Similarity scores in context

**Files Modified:**
- `app/composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/knowledge/PromptEnhancer.kt`

**Files Created:**
- `app/composeApp/src/commonTest/kotlin/app/m1k3/ai/assistant/knowledge/PromptEnhancerTest.kt`

**Dependencies:** PHASE1.5-005 (Semantic retrieval with similarity scores)
**Blocks:** Integration testing

---

### PHASE1.5-007: Adaptive Generation Parameters

**Priority:** P1
**Estimated Hours:** 3h
**Status:** [ ]

**Description:**
Make generation parameters adaptive based on query type. Educational queries need 1024 tokens, casual chat uses 256.

**Current Problem:**
```kotlin
// SmolLM2Engine.kt - Device-adaptive but CAPPED
fun getOptimalMaxTokens(): Int {
    return when {
        deviceRamGB >= 12 -> 512   // Too short for "teach me"
        deviceRamGB >= 6 -> 256    // Cuts off explanations
        // ...
    }
}
```

**New Approach:**
```kotlin
// Query-type aware
val config = AdaptiveGeneration.getConfig(query, deviceRamGB)

// Educational: "teach me", "explain", "how does X work"
QueryType.EDUCATIONAL → maxTokens = 1024 (8GB), 768 (6GB), 512 (4GB)

// Technical: coding, debugging
QueryType.TECHNICAL → maxTokens = 768 (8GB), 512 (6GB), 384 (4GB)

// Conversational: casual chat, greetings
QueryType.CONVERSATIONAL → maxTokens = 384 (6GB), 256 (4GB)

// Factual: "what is", "who invented"
QueryType.FACTUAL → maxTokens = 512 (6GB), 384 (4GB)
```

**Temperature Adaptation:**
```kotlin
val temperature = when (queryType) {
    QueryType.TECHNICAL -> 0.3f      // Deterministic code
    QueryType.FACTUAL -> 0.5f        // Consistent facts
    QueryType.EDUCATIONAL -> 0.6f    // Engaging explanations
    QueryType.CONVERSATIONAL -> 0.7f // Natural conversation
}
```

**Acceptance Criteria:**
- [ ] Query type detection (4 categories)
- [ ] Adaptive max_tokens (query + device)
- [ ] Temperature varies by query type
- [ ] User preference multiplier (concise/verbose)
- [ ] Debug info shows reasoning

**Tests:**
- [ ] `AdaptiveGenerationTest.kt`: "teach me" gets 1024 tokens (8GB)
- [ ] `AdaptiveGenerationTest.kt`: Casual chat gets 256 tokens
- [ ] `AdaptiveGenerationTest.kt`: Temperature appropriate

**Files Created:**
- `app/shared/src/commonMain/kotlin/ai/ma/ai/AdaptiveGeneration.kt`
- `app/shared/src/commonTest/kotlin/ai/ma/ai/AdaptiveGenerationTest.kt`

**Dependencies:** None (can run in parallel)
**Blocks:** ChatViewModel integration

---

### PHASE1.5-008: Add AI/ML Content to Knowledge Base

**Priority:** P2
**Estimated Hours:** 2h
**Status:** [ ]

**Description:**
Expand knowledge base with 20+ AI/ML educational facts so "teach me about AI" queries retrieve relevant content.

**Content Coverage:**
- AI fundamentals (definition, history, applications)
- Machine Learning basics (supervised, unsupervised, reinforcement)
- Neural networks and deep learning
- Natural Language Processing (NLP)
- Computer Vision
- On-device AI and edge computing
- AI ethics and limitations
- Common AI architectures (transformers, CNNs, RNNs)

**Example Facts:**
```json
{
  "category": "ai_ml",
  "facts": [
    {
      "id": "ai_ml_001",
      "question": "What is Artificial Intelligence?",
      "answer": "Artificial Intelligence (AI) is the simulation of human intelligence by machines. It includes learning, reasoning, and self-correction. AI powers virtual assistants, recommendation systems, autonomous vehicles, and much more.",
      "importance": 0.95,
      "tags": ["ai", "definition", "basics"]
    },
    {
      "id": "ai_ml_005",
      "question": "What is On-Device AI?",
      "answer": "On-device AI runs AI models directly on smartphones or computers without sending data to the cloud. Benefits include privacy, faster responses, and offline functionality. This assistant is 100% on-device for your privacy.",
      "importance": 0.92,
      "tags": ["on-device", "edge ai", "privacy", "local"]
    }
    // ... 18 more facts
  ]
}
```

**Import Process:**
1. Create JSON file with 20+ AI/ML facts
2. Run KnowledgeBaseImporter (Phase 0 infrastructure)
3. Embed all facts with MiniLM-L6
4. Add to HNSW vector index
5. Verify retrieval with test queries

**Acceptance Criteria:**
- [ ] 20+ AI/ML facts added to knowledge base
- [ ] Facts cover fundamentals through advanced topics
- [ ] Facts embedded in vector index
- [ ] Test: "teach me about AI" retrieves AI facts (>0.8 similarity)

**Tests:**
- [ ] `KnowledgeBaseTest.kt`: AI/ML facts imported
- [ ] `SemanticSearchTest.kt`: AI queries retrieve AI facts

**Files Created:**
- `app/composeApp/src/androidMain/assets/knowledge/ai_ml_knowledge.json`
- `app/composeApp/src/androidTest/kotlin/app/m1k3/ai/assistant/knowledge/AIMLKnowledgeTest.kt`

**Dependencies:** PHASE1.5-005 (Semantic retrieval for testing)
**Blocks:** None (improves RAG quality)

---

### PHASE1.5-005 (NEW): Evaluate SmolLM2-135M Model ⚠️ CRITICAL

**Priority:** P0
**Estimated Hours:** 6h
**Status:** [ ] Not started

**Description:**
Export and benchmark SmolLM2-135M against our current SmolLM2-360M to determine if the smaller model is acceptable for the 200MB APK constraint.

**Model Specs:**
- **Parameters:** 135M (vs 360M - 62% smaller)
- **Expected Size:** ~70-80 MB q4f16 (vs 180 MB - 56% reduction!)
- **Context:** Same 24K tokens
- **Trade-off:** Slightly lower quality for size savings

**Technical Approach:**
```python
# export_smollm2_135m.py
from transformers import AutoModelForCausalLM, AutoTokenizer
from optimum.onnxruntime import ORTModelForCausalLM

model_id = "HuggingFaceTB/SmolLM2-135M-Instruct"
output_dir = "models/smollm2-135m-onnx"

# Export to ONNX with q4f16 quantization
ort_model = ORTModelForCausalLM.from_pretrained(
    model_id,
    export=True,
    provider="CPUExecutionProvider"
)
# Quantize and save
```

**Comparison Metrics:**
1. **Performance**: Tokens/sec, first token latency, memory usage
2. **Quality**: 20-prompt test suite (same as PHASE1.5-004)
3. **Size**: Actual APK size with 135M vs 360M

**Acceptance Criteria:**
- [ ] SmolLM2-135M exported to ONNX (q4f16)
- [ ] Model size confirmed <80 MB
- [ ] Performance benchmark vs 360M (expect faster due to size)
- [ ] Quality comparison (accept if >85% of 360M quality)
- [ ] Final recommendation: 135M vs 360M for production

**Decision Criteria:**
- If 135M quality >85% of 360M → **Use 135M** (meets size budget)
- If 135M quality <85% of 360M → Keep 360M, optimize elsewhere

**Tests:**
- [ ] `SmolLM2ComparisonTest.kt`: Side-by-side benchmark
- [ ] `QualityRegressionTest.kt`: 20-prompt comparison
- [ ] `APKSizeTest.kt`: Measure actual APK sizes

**Files Created:**
- `app/export_smollm2_135m.py`
- `app/models/smollm2-135m-onnx/`
- `app/docs/SMOLLM2_135M_EVAL.md` - Comparison report

**Dependencies:** None (parallel track)
**Blocks:** Final model selection for Phase 3+

---

### PHASE1.5-006 (NEW): Optimize MiniLM Embedding Model ⚠️ CRITICAL

**Priority:** P0
**Estimated Hours:** 4h
**Status:** [ ] Not started

**Description:**
Reduce MiniLM embedding model size from 90 MB to 50-60 MB through model swapping or quantization.

**Current State:**
```
Model: sentence-transformers/all-MiniLM-L6-v2
Size: 90 MB (ONNX fp32)
Dimensions: 384
Performance: 122ms embedding generation
```

**Optimization Options:**

**Option 1: Smaller Model (Recommended)**
```
Model: sentence-transformers/paraphrase-MiniLM-L3-v2
Size: ~50 MB (ONNX fp32)
Dimensions: 384 (same!)
Trade-off: Slightly lower semantic accuracy
Benefit: Plug-in replacement (same API)
```

**Option 2: INT8 Quantization**
```
Model: all-MiniLM-L6-v2 (INT8)
Size: ~45 MB (50% reduction)
Dimensions: 384
Trade-off: Minimal accuracy loss (<2%)
Benefit: Keep same model, just quantized
```

**Option 3: Hybrid (Best of Both)**
```
Model: paraphrase-MiniLM-L3-v2 (INT8)
Size: ~25 MB (72% reduction!)
Trade-off: Moderate accuracy loss (~5-8%)
Benefit: Maximum size savings
```

**Implementation:**
```kotlin
// Test all three options
val models = listOf(
    "paraphrase-MiniLM-L3-v2" to "fp32",
    "all-MiniLM-L6-v2" to "int8",
    "paraphrase-MiniLM-L3-v2" to "int8"
)

// Benchmark semantic retrieval quality
for ((model, quant) in models) {
    val precision = testSemanticRetrieval(model, quant)
    val size = measureModelSize(model, quant)
    println("$model ($quant): ${precision}% precision, ${size}MB")
}
```

**Acceptance Criteria:**
- [ ] Evaluate 3 optimization options (L3-fp32, L6-int8, L3-int8)
- [ ] Semantic retrieval quality test (>90% precision maintained)
- [ ] Model size confirmed <60 MB
- [ ] Performance acceptable (<150ms embedding)
- [ ] Final recommendation with justification

**Decision Criteria:**
- If L3-fp32 precision >92% → **Use L3-fp32** (50 MB, safest)
- If L6-int8 precision >95% → **Use L6-int8** (45 MB, best quality)
- If APK still over budget → **Use L3-int8** (25 MB, aggressive)

**Tests:**
- [ ] `EmbeddingModelComparisonTest.kt`: Quality benchmarks
- [ ] `EmbeddingSizeTest.kt`: Measure actual sizes
- [ ] `SemanticRetrievalRegressionTest.kt`: End-to-end retrieval test

**Files Created:**
- `app/export_minilm_optimized.py` - Export script
- `app/docs/MINILM_OPTIMIZATION.md` - Comparison report

**Dependencies:** PHASE1.5-005 (semantic retrieval working)
**Blocks:** APK size finalization

---

### PHASE1.5-007 (NEW): Design Dynamic Model Delivery System

**Priority:** P1
**Estimated Hours:** 5h
**Status:** [ ] Not started

**Description:**
Design architecture for downloading and managing large AI models (like Gemma 3:270m @ 427 MB) after app installation.

**Requirements:**
1. **User Experience**
   - Settings → AI Models → Browse available models
   - Show model specs: size, params, context, capabilities
   - Download progress (MB downloaded / total MB)
   - Pause/resume support
   - WiFi-only option (prevent cellular data usage)

2. **Storage Management**
   - Detect available storage before download
   - Warn if <1GB free space
   - Allow model deletion to reclaim space
   - Track model usage (swap out unused models)

3. **Model Switching**
   - Hot-swap between models (no app restart)
   - Graceful fallback if model load fails
   - Model validation (checksum verification)

**Architecture:**

```kotlin
// ModelDownloadManager.kt
class ModelDownloadManager(
    private val context: Context,
    private val downloadManager: DownloadManager
) {
    suspend fun downloadModel(
        model: DownloadableModel,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File>

    suspend fun verifyModel(modelFile: File): Boolean
    suspend fun deleteModel(modelId: String)
    fun getInstalledModels(): List<InstalledModel>
}

// DownloadableModel.kt
sealed class DownloadableModel(
    val id: String,
    val displayName: String,
    val sizeMB: Int,
    val downloadUrl: String,
    val checksum: String
) {
    object Gemma3_270M : DownloadableModel(
        id = "gemma3-270m",
        displayName = "Gemma 3 270M (Advanced)",
        sizeMB = 427,
        downloadUrl = "https://huggingface.co/.../model_q4f16.onnx",
        checksum = "sha256:..."
    )
}
```

**UI Flow:**
```
Settings → AI Models
  ├─ Installed Models
  │  └─ SmolLM2 135M (Active) [70 MB]
  │
  └─ Available for Download
     ├─ Gemma 3 270M [427 MB]  [Download]
     │  ✓ 256K context window
     │  ✓ Vision capabilities
     │  ⚠️ Requires 500 MB free space
     │
     └─ SmolLM2 360M [180 MB]  [Download]
        ✓ Larger than 135M, better quality
```

**Acceptance Criteria:**
- [ ] Architecture design document
- [ ] ModelDownloadManager API spec
- [ ] Settings UI mockups
- [ ] Storage management strategy
- [ ] Model registry integration plan
- [ ] Security considerations (HTTPS, checksums)

**Deliverables:**
- `app/docs/DYNAMIC_MODEL_DELIVERY.md` - Design doc
- `app/docs/diagrams/model_download_flow.svg` - User flow diagram
- `app/shared/src/commonMain/kotlin/ai/ma/models/ModelDownloadManager.kt` - Interface

**Dependencies:** PHASE1.5-003 (Model registry exists)
**Blocks:** Future Gemma 3 integration (Phase 5+)

---

### PHASE1.5-009 (NEW): APK Size Optimization Audit

**Priority:** P1
**Estimated Hours:** 3h
**Status:** [ ] Not started

**Description:**
Comprehensive audit of APK size with ProGuard optimization, asset compression, and unused resource removal.

**Optimization Checklist:**

**1. ProGuard/R8 Optimization**
```gradle
// build.gradle.kts
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

**2. Asset Compression**
- ONNX models: Already quantized (no further compression)
- Knowledge base JSON: gzip compression (~30% reduction)
- Tokenizer files: Remove unused special tokens

**3. Unused Resource Removal**
- Scan for unused drawable resources
- Remove unused string translations
- Remove unused AndroidX library features

**4. Native Library Stripping**
- Keep only arm64-v8a (primary Android target)
- Remove x86/x86_64 (emulator-only, 40% size reduction)
- Use APK splits for multi-architecture support

**Size Analysis:**
```
Before Optimization:
  App code:         50 MB
  ONNX Runtime:     30 MB (includes x86)
  Resources:        10 MB
  ──────────────────────
  Base APK:         90 MB

After Optimization:
  App code:         30 MB (ProGuard)
  ONNX Runtime:     18 MB (arm64 only)
  Resources:         6 MB (stripped)
  ──────────────────────
  Base APK:         54 MB ✅ (40% reduction!)
```

**Revised Total APK Size:**
```
Base APK (optimized):     54 MB
SmolLM2-135M:             70 MB
MiniLM-L3 (int8):         25 MB
Knowledge base:           20 MB (gzipped)
──────────────────────────────
Total APK:               169 MB ✅ (16% under budget!)
```

**Acceptance Criteria:**
- [ ] ProGuard enabled with optimizations
- [ ] Native libraries stripped to arm64-v8a only
- [ ] Unused resources removed (lint analysis)
- [ ] Knowledge base gzip compressed
- [ ] APK Analyzer report generated
- [ ] Final APK size <170 MB

**Tests:**
- [ ] `APKSizeTest.kt`: Measure release APK size
- [ ] `ProGuardTest.kt`: Verify no runtime crashes
- [ ] `ResourceUsageTest.kt`: Validate all used resources present

**Files Modified:**
- `app/composeApp/build.gradle.kts` - ProGuard config
- `app/composeApp/proguard-rules.pro` - Keep rules

**Files Created:**
- `app/docs/APK_SIZE_REPORT.md` - Optimization results

**Dependencies:** PHASE1.5-005, PHASE1.5-006 (final models selected)
**Blocks:** Phase 6 release preparation

---

## Testing Strategy

### Unit Tests (40%)
- Model registry loading/switching
- Adaptive generation config calculation
- Semantic retrieval similarity filtering
- Prompt template formatting

### Integration Tests (30%)
- End-to-end RAG pipeline (query → retrieval → prompt → generation)
- Model switching without memory leaks
- Embedding + vector search integration

### Performance Tests (20%)
- Tokens/sec benchmarks (SmolLM2 vs Gemma3)
- Memory usage profiling
- First token latency measurement
- Battery impact simulation

### Quality Tests (10%)
- 20-prompt comparison suite
- RAG utilization evaluation
- Response coherence scoring
- Instruction following validation

---

## Success Criteria

### Model Comparison Success
**Gemma 3:270m vs SmolLM2-360M:**

| Metric | SmolLM2 Baseline | Gemma3 Target |
|--------|------------------|---------------|
| **Performance** | | |
| Tokens/sec | 15-20 (emulator) | 25-35 (goal) |
| First token | ~500ms | <400ms |
| Memory | ~300MB | <250MB |
| **Quality** | | |
| Coherence | Baseline | ≥Baseline |
| Instruction following | Baseline | ≥Baseline |
| **Resource** | | |
| Model size | 180MB | ~120MB ✅ |
| Context | 24K tokens | 256K tokens ✅ |
| Capabilities | Text only | Text + Vision ✅ |

**Decision:** Ship model that wins on 2/3 dimensions (performance, quality, resource).

---

### RAG Improvement Success

**Before (Current):**
```
Query: "can you teach me about ai?"
Retrieved: ["problem-solving", "data analysis", "online shopping"]
Similarity: 0.2-0.4
Response: Nonsensical blend ❌
```

**After (Phase 1.5):**
```
Query: "can you teach me about ai?"
Retrieved: ["What is AI?", "What is ML?", "Neural Networks"]
Similarity: 0.85-0.92
Response: Coherent AI/ML explanation ✅
```

**Metrics:**
- Semantic retrieval precision@3: >90% (vs <30% keyword)
- Average similarity score: >0.75 (vs 0.3 keyword)
- User-perceived quality: "Much better" (manual validation)

---

### Generation Quality Success

**Educational Query Test:**
```
Query: "Can you teach me about quantum entanglement?"

Before (256 tokens):
"Quantum entanglement is a phenomenon where particles become
connected such that the state of one..." [CUTOFF]

After (1024 tokens):
"Quantum entanglement is a fascinating phenomenon in quantum physics
where two or more particles become correlated in such a way that the
state of one particle instantaneously affects the state of another,
regardless of the distance between them.

To understand this better, imagine two particles that interact and
then separate. Even though they are now far apart, measuring a property
of one particle (like its spin) instantly determines the corresponding
property of the other particle. This seems to violate the speed of
light limit, but actually no information is transmitted...

[FULL EXPLANATION CONTINUES FOR ~800-1000 TOKENS]

This concept has profound implications for quantum computing, quantum
cryptography, and our understanding of reality itself."
```

**Metrics:**
- Educational query completeness: >90% (vs <40% with 256-token cap)
- User satisfaction: "Complete answer" (vs "Cut off mid-thought")

---

## Integration with Existing Phases

### Phase 2 (Memory) → Phase 1.5
**What Phase 1.5 Uses:**
- ✅ MiniLM-L6 embedding engine (PHASE2-001 through PHASE2-004)
- ✅ HNSW vector index (PHASE2-005, PHASE2-006)
- ✅ VectorSearchManager API for semantic search

**Confirmed Ready:** Phase 2 embeddings + vector search operational.

---

### Phase 1.5 → Phase 3 (Knowledge)
**What Phase 3 Inherits:**
- ✅ Working semantic RAG system (no need to build from scratch)
- ✅ Prompt guardrails (reusable for advanced knowledge features)
- ✅ Model selection finalized (no more "which model?" questions)
- ✅ Expanded AI/ML knowledge base

**Compression Justification:** Phase 3 can compress from 2 weeks to 1.5 weeks because:
- Semantic RAG done in Phase 1.5
- Trivia engine just needs UI (retrieval exists)
- Device intelligence independent workstream

---

### Phase 1.5 → Phase 5 (Polish)
**What Phase 5 Inherits:**
- ✅ Model comparison framework (reusable for Qwen2.5 evaluation)
- ✅ Adaptive generation (no tuning needed)
- ✅ Performance benchmarks (baseline established)

**Compression Justification:** Phase 5 can compress from 3 weeks to 2.5 weeks because:
- Model selection done
- Performance baselines established
- Focus shifts to accessibility, analytics

---

## Timeline Impact

### Original 16-Week Plan
- Weeks 1-2: Phase 0 (Foundation)
- Weeks 3-5: Phase 1 (Core AI) ✅ COMPLETE
- Weeks 6-8: Phase 2 (Memory)
- Weeks 9-10: Phase 3 (Knowledge)
- Weeks 11-12: Phase 4 (Multi-Modal)
- Weeks 13-15: Phase 5 (Polish)
- Week 16: Phase 6 (Release)

### Revised 16-Week Plan
- Weeks 1-2: Phase 0 ✅
- Weeks 3-5: Phase 1 ✅ COMPLETE
- Week 6: Phase 2 🟡 IN PROGRESS (~56%)
- **Week 7: Phase 1.5 ⭐ NEW**
- Weeks 8-9: Phase 3 (compressed from 2 weeks)
- Weeks 10-11: Phase 4
- Weeks 12-14: Phase 5 (compressed from 3 weeks)
- Weeks 15-16: Phase 6

**Net Impact:** +0 weeks (accommodated through compression)

---

## Risk Management

### Risk 1: Gemma3 Export Failure
**Likelihood:** Medium
**Impact:** High
**Mitigation:**
- Fallback: Use pre-quantized Gemma3 from HuggingFace
- Alternative: Test Qwen2.5:271M instead

### Risk 2: Semantic RAG No Better
**Likelihood:** Low (embeddings proven in Phase 2)
**Impact:** High
**Mitigation:**
- Validate embedding quality first
- Fallback: Hybrid retrieval (semantic + keyword)

### Risk 3: APK Size Exceeds 200MB
**Likelihood:** Low (shipping only winner)
**Impact:** Medium
**Mitigation:**
- Ship only winning model
- Gemma3: 120MB (under budget with base app)
- SmolLM2: 180MB (still under 200MB)

### Risk 4: Schedule Slip
**Likelihood:** Medium
**Impact:** Medium
**Mitigation:**
- De-scope PHASE1.5-008 (AI/ML knowledge) if needed
- De-scope PHASE1.5-007 (adaptive generation) if needed
- Minimum viable: 001-006 (model comparison + RAG fixes)

---

## Deliverables

### Code Deliverables
1. **Gemma3 Integration**
   - `export_gemma3_270m.py` - ONNX export script
   - `Gemma3Engine.kt` - Android inference engine
   - `Gemma3Tokenizer.kt` - Tokenizer integration

2. **Multi-Model System**
   - `ModelRegistry.kt` - Model management
   - `AIModel.kt` - Model metadata
   - Settings UI for model selection

3. **RAG Improvements**
   - `SemanticRetrievalService.kt` - Semantic search
   - `PromptEnhancer.kt` - Updated with guardrails
   - `AdaptiveGeneration.kt` - Query-aware parameters

4. **Knowledge Base**
   - `ai_ml_knowledge.json` - 20+ AI/ML facts

5. **Testing**
   - `ModelComparisonTest.kt` - Benchmark framework
   - `SemanticRetrievalTest.kt` - RAG quality tests
   - 8+ test files across all tickets

---

### Documentation Deliverables
1. **MODEL_COMPARISON_REPORT.md**
   - Performance benchmarks (tokens/sec, latency, memory)
   - Quality evaluation (20 prompts, scoring)
   - Final recommendation with justification

2. **RAG_IMPROVEMENTS.md**
   - Problem statement (current failures)
   - Solution architecture
   - Before/after examples
   - Retrieval quality metrics

3. **Updated PROJECT_MANAGEMENT.md**
   - Add Phase 1.5 to roadmap
   - Update timeline (compressed phases)
   - Update progress tracking

---

## Ticket Dependencies Graph

```
PHASE1.5-001 (Gemma3 Export)
    ↓
PHASE1.5-002 (Gemma3Engine)
    ↓
PHASE1.5-003 (Model Registry)
    ↓
PHASE1.5-004 (Comparison Framework)
    ↓
FINAL DECISION (Week 7 end)

PHASE2 (Embeddings + Vector Index) ✅ READY
    ↓
PHASE1.5-005 (Semantic Retrieval)
    ↓
PHASE1.5-006 (Prompt Guardrails)
    ↓
INTEGRATION TESTING

PHASE1.5-007 (Adaptive Generation) [Parallel]
PHASE1.5-008 (AI/ML Knowledge) [Parallel]
```

---

## Week 7 Schedule

**Monday-Tuesday (Days 1-2):**
- PHASE1.5-001: Gemma3 ONNX export (6h)
- PHASE1.5-008: AI/ML knowledge (2h, parallel)

**Wednesday-Thursday (Days 3-4):**
- PHASE1.5-002: Gemma3Engine (5h)
- PHASE1.5-005: Semantic retrieval (4h, parallel)

**Friday (Day 5):**
- PHASE1.5-003: Model registry (4h)
- PHASE1.5-006: Prompt guardrails (3h, parallel)

**Saturday-Sunday (Days 6-7):**
- PHASE1.5-004: Model comparison (5h)
- PHASE1.5-007: Adaptive generation (3h, parallel)
- **Final:** Model selection decision + docs

**Total:** 32h / 7 days = ~4-5 hours/day (sustainable)

---

## Open Questions

**Q1: What if Gemma3 and SmolLM2 tie?**
**A:** Prefer Gemma3 (smaller size: 120MB vs 180MB, larger context: 256K vs 24K).

**Q2: Can we test Qwen2.5:271M later?**
**A:** Yes, Phase 5 (weeks 12-14) has time for additional model evaluation if desired.

**Q3: What if APK with Gemma3 still exceeds 200MB?**
**A:** Base app + Gemma3 = ~100MB + 120MB = 220MB (slightly over). Options:
- Aggressive pruning (float16 embeddings, smaller tokenizer)
- Relax constraint to 220MB for beta
- Dynamic feature modules (Phase 6 optimization)

**Q4: What if semantic RAG is only marginally better?**
**A:** Still ship it - even 20% improvement is worth it. Fallback: Hybrid retrieval (semantic + keyword fusion).

---

## Status Updates

**Last Updated:** 2025-11-04
**Status:** 🔴 Not Started (0/8, 0%)
**Next Milestone:** PHASE1.5-001 (Gemma3 export) - Week 7 begins

---

**Phase Lead:** [TBD]
**Reviewers:** [TBD]
**Target Completion:** End of Week 7
