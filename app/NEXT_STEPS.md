# 間 AI - Next Steps & Future Improvements

**Created**: 2025-12-25 (Christmas Day Refactoring Session 🎄)
**Updated**: 2025-12-25 (Post-Red Wine Edition 🍷)
**Status**: Post-Beta Planning Document
**Priority**: Medium-term improvements for v1.0+

---

## 🎯 Current Status

✅ **Completed** (Christmas 2025):
- Result<T> error handling migration (100% complete)
- LlamaCppEngine with Gemma 3 270M IQ3_XXS (10+ tok/s)
- Max token limits removed (now 1024-4096, model decides)
- 49 tests passing (36 unit + 13 integration)
- ChatScreen Result handling with .onSuccess/.onFailure

⚠️ **Known Issues & Constraints**:
- Chat responses feel "limited" (small model + aggressive quantization)
- Gemma 3 270M struggles with complex instructions
- IQ3_XXS quantization sacrifices quality for size
- **APK size constraint** - Need small initial download
- **Dynamic model downloads required** - Can't bundle large models

---

## 🤖 Priority 0: Agentic Bootstrap Strategy (GAME CHANGER!)

**Status**: 🍷 Red Wine Brainstorm - Brilliant Idea!
**Concept**: Use a small bundled agent to coordinate downloads AND provide agentic capabilities out of the box!

### **The Vision**:
1. **Bundle FunctionGemma 2B** (~1.2GB Q4_K_M) OR **Gemma 3 270M w/ Function Calling** (~200MB)
2. **Agent helps user download chat models** - Meta! AI helps download better AI!
3. **Provides immediate value** - Tool use, function calling, multi-step reasoning from day 1
4. **Two-model architecture** - Agent (tools/coordination) + Chat (conversation)

### **Architecture Options**:

#### **Option A: Dual Model System** ⭐⭐⭐⭐⭐ (Recommended)
**Bundle**: Gemma 3 270M Q4_K_M (200MB) with function calling via structured prompts
**Download**: Larger chat models on-demand (1B, 3.8B, etc.)

**Roles**:
- **Agent Model** (bundled 270M):
  - Coordinates model downloads
  - File operations (search, organize conversations)
  - Settings management
  - Tool use (calculator, web search via MCP, etc.)
  - Multi-step task planning

- **Chat Model** (downloaded on-demand):
  - Natural conversation
  - Long-form responses
  - RAG-enhanced knowledge retrieval
  - Complex instruction following

**Benefits**:
✅ Small APK (50MB base + 200MB agent = 250MB total)
✅ Immediate agentic capabilities
✅ User chooses chat model quality vs speed
✅ Agent persists, chat model swappable
✅ Two models can collaborate (agent plans, chat executes)

**Implementation**:
```kotlin
class DualModelEngine(context: Context) {
    private val agentEngine = LlamaCppEngine(context, "gemma-3-270m-it-Q4_K_M.gguf") // Bundled
    private val chatEngine = LlamaCppEngine(context, userSelectedModel) // Downloaded

    suspend fun executeAgentTask(task: AgentTask): Result<AgentResponse> {
        // Use function calling for structured output
        val functionPrompt = """
        You are an AI agent. Available functions:
        - download_model(name, size_mb)
        - search_files(query)
        - update_settings(key, value)

        Task: ${task.description}
        Respond with JSON function call.
        """.trimIndent()

        return agentEngine.generate(functionPrompt).map { result ->
            parseAndExecuteFunction(result.text)
        }
    }

    suspend fun chat(message: String): Result<String> {
        return chatEngine.generateStreaming(message) { token ->
            // Stream to UI
        }
    }
}
```

#### **Option B: FunctionGemma 2B** ⭐⭐⭐ (More powerful, larger APK)
**Bundle**: FunctionGemma 2B Q4_K_M (~1.2GB)
**Download**: Optional chat models

**Pros**:
- Native function calling support (better than prompt engineering)
- More capable agent (2B params)
- Designed specifically for tool use

**Cons**:
- Larger APK (50MB + 1.2GB = 1.25GB initial download)
- Slower on low-end devices
- May be overkill for basic coordination tasks

#### **Option C: No Bundling, Agent-First Download** ⭐⭐⭐⭐ (Smallest APK)
**Bundle**: Nothing (50MB APK only)
**Download**: FunctionGemma 2B FIRST, then chat models

**Flow**:
1. User installs app (50MB)
2. First launch: "Downloading AI Agent..." (1.2GB FunctionGemma)
3. Agent ready → helps download chat models
4. Full agentic capabilities available

**Benefits**:
✅ Smallest initial APK (50MB)
✅ Best agent capabilities (native function calling)
✅ Clear separation: agent vs chat

**Drawbacks**:
⚠️ Longer first-launch wait (1.2GB download)
⚠️ No offline use until download completes

---

### **Function Calling Implementation** (Option A - Recommended)

**Structured Output via Prompting**:
```kotlin
// AgentPromptBuilder.kt
object AgentPromptBuilder {
    fun buildFunctionCallingPrompt(
        task: String,
        availableFunctions: List<FunctionDefinition>
    ): String {
        return """
        <bos><start_of_turn>system
        You are M1K3 Agent, a helpful AI assistant with function calling capabilities.
        You can call functions to help users accomplish tasks.

        Available Functions:
        ${availableFunctions.joinToString("\n") { it.toJsonSchema() }}

        Instructions:
        1. Analyze the user's task
        2. Choose the appropriate function(s) to call
        3. Respond ONLY with valid JSON function calls
        4. Format: {"function": "function_name", "arguments": {...}}
        <end_of_turn>
        <start_of_turn>user
        Task: $task
        <end_of_turn>
        <start_of_turn>model
        """.trimIndent()
    }
}

// FunctionDefinition.kt
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, ParameterSchema>
) {
    fun toJsonSchema(): String = """
    {
      "name": "$name",
      "description": "$description",
      "parameters": ${parameters.toJson()}
    }
    """.trimIndent()
}

// Built-in functions
val DOWNLOAD_MODEL = FunctionDefinition(
    name = "download_model",
    description = "Download a new AI model from HuggingFace",
    parameters = mapOf(
        "model_name" to ParameterSchema("string", "Name of model (e.g., 'gemma-3-1b-it-Q4_K_M')"),
        "url" to ParameterSchema("string", "HuggingFace download URL"),
        "size_mb" to ParameterSchema("number", "File size in MB")
    )
)

val SEARCH_CONVERSATIONS = FunctionDefinition(
    name = "search_conversations",
    description = "Search through user's chat history",
    parameters = mapOf(
        "query" to ParameterSchema("string", "Search query"),
        "limit" to ParameterSchema("number", "Max results to return")
    )
)

val UPDATE_SETTINGS = FunctionDefinition(
    name = "update_settings",
    description = "Update app settings",
    parameters = mapOf(
        "setting_key" to ParameterSchema("string", "Setting to update"),
        "value" to ParameterSchema("any", "New value")
    )
)
```

**Function Executor**:
```kotlin
// AgentExecutor.kt
class AgentExecutor(
    private val context: Context,
    private val database: MaDatabase
) {
    suspend fun execute(functionCall: FunctionCall): Result<FunctionResult> {
        return when (functionCall.function) {
            "download_model" -> downloadModel(
                functionCall.arguments["model_name"] as String,
                functionCall.arguments["url"] as String,
                functionCall.arguments["size_mb"] as Int
            )

            "search_conversations" -> searchConversations(
                functionCall.arguments["query"] as String,
                functionCall.arguments["limit"] as? Int ?: 10
            )

            "update_settings" -> updateSettings(
                functionCall.arguments["setting_key"] as String,
                functionCall.arguments["value"]
            )

            else -> Result.failure(
                IllegalArgumentException("Unknown function: ${functionCall.function}")
            )
        }
    }

    private suspend fun downloadModel(
        name: String,
        url: String,
        sizeMB: Int
    ): Result<FunctionResult> = withContext(Dispatchers.IO) {
        try {
            val downloader = ModelDownloader(context)
            downloader.download(url, name) { progress ->
                // Update UI progress
            }.map { file ->
                FunctionResult.success(
                    "Successfully downloaded $name (${sizeMB}MB) to ${file.absolutePath}"
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

---

### **User Experience Flow**:

**First Launch** (Option A - Dual Model):
1. App opens → "Setting up M1K3 Agent..." (copies 200MB model from assets)
2. Agent ready in ~10 seconds
3. Welcome screen:
   > "Hi! I'm M1K3 Agent 🤖
   >
   > I can help you download chat models and manage your conversations.
   >
   > Would you like to download a chat model now?
   > - Gemma 3 1B (750MB) - Balanced quality & speed ✅ Recommended
   > - Phi-3.5 Mini (2GB) - Best quality, slower
   > - Skip for now - Use agent-only mode"

**Agent-Only Mode**:
- User: "Search my conversations for 'pizza recipe'"
- Agent: *Calls search_conversations function*
- Agent: "Found 3 conversations about pizza recipes from last week..."

**Dual-Model Mode** (after chat model downloaded):
- User: "Tell me a story about a robot"
- App: *Routes to chat model* (better at creative long-form)
- User: "Find that story I wrote last week"
- App: *Routes to agent model* (tool use for search)

---

### **Implementation Timeline**:

**Week 1** (Agent Foundation):
- Create `AgentPromptBuilder.kt` with function calling prompts
- Create `FunctionDefinition.kt` and built-in functions
- Create `AgentExecutor.kt` for function execution
- Test structured output with Gemma 3 270M

**Week 2** (Model Download System):
- Create `ModelDownloader.kt` with HuggingFace API
- Create `ModelManager.kt` for installed models
- Add progress UI for downloads
- Test download → install → switch flow

**Week 3** (Dual Model Architecture):
- Create `DualModelEngine.kt` wrapper
- Implement intelligent routing (task type → model selection)
- Create agent-first onboarding flow
- Add model selection UI

**Week 4** (Polish & Testing):
- Test all function calls
- Add more built-in functions (calculator, unit conversion, etc.)
- Optimize prompt engineering for 270M
- Beta testing with real users

---

## 📊 Priority 1: Model Upgrades (Immediate Impact)

### **Option A: Better Quantized Gemma 3 270M** ⭐ (Quickest)
**Effort**: 15 minutes
**Impact**: 30-50% quality improvement

**Action**:
1. Download `gemma-3-270m-it-Q4_K_M.gguf` (~200MB, 4-bit)
2. Replace in `app/src/main/assets/models/`
3. Update `LlamaCppEngine.kt:129`:
   ```kotlin
   val modelFile = File(context.filesDir, "gemma-3-270m-it-Q4_K_M.gguf")
   ```

**Link**: https://huggingface.co/bartowski/gemma-3-270m-it-GGUF

---

### **Option B: Upgrade to Gemma 3 1B** ⭐⭐⭐ (Recommended)
**Effort**: 1 hour
**Impact**: 3-4x quality improvement, still fast (5-8 tok/s)

**Specs**:
- Size: ~750MB (Q4_K_M quantization)
- Quality: Much better instruction following, coherence
- Speed: 5-8 tok/s (still very usable)
- Context: 32K tokens (same as 270M)

**Action**:
1. Download `gemma-3-1b-it-Q4_K_M.gguf` (~750MB)
2. Replace model file in assets
3. Update `LlamaCppEngine.kt:129`:
   ```kotlin
   val modelFile = File(context.filesDir, "gemma-3-1b-it-Q4_K_M.gguf")
   ```
4. Update logs/comments to reflect "Gemma 3 1B" instead of "270M"

**Link**: https://huggingface.co/bartowski/gemma-3-1b-it-GGUF

---

### **Option C: Phi-3.5 Mini (3.8B)** ⭐⭐⭐⭐⭐ (Best Quality)
**Effort**: 1-2 hours (testing needed)
**Impact**: Near GPT-3.5 quality, game-changing improvement

**Specs**:
- Size: ~2GB (Q4_K_M)
- Quality: State-of-the-art for small models
- Speed: 2-4 tok/s (slower but acceptable)
- Context: **128K tokens!** (4x Gemma 3)
- From: Microsoft - extremely well-tuned

**Action**:
1. Download `Phi-3.5-mini-instruct-Q4_K_M.gguf` (~2GB)
2. Test on mid-range device (6GB+ RAM recommended)
3. May need to adjust maxTokens for slower generation
4. Update branding to highlight "Powered by Microsoft Phi-3.5"

**Link**: https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF

---

### **Option D: Qwen2.5 1.5B** ⭐⭐⭐⭐ (Balanced Alternative)
**Effort**: 1 hour
**Impact**: Better than Gemma 3 1B, excellent coding abilities

**Specs**:
- Size: ~900MB (Q4_K_M)
- Quality: Better instruction following than Gemma 3 1B
- Speed: 4-6 tok/s
- Context: 32K tokens
- Bonus: Excellent at coding, technical content

**Link**: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF

---

## 🎨 Priority 2: Model Selection UI (UX Improvement)

**Effort**: 1-2 days
**Impact**: Empowers users to choose quality vs speed

### **Features**:
- Settings screen: Model selector dropdown
- Options:
  - "Fast" - Gemma 3 270M (176MB, 10+ tok/s)
  - "Balanced" - Gemma 3 1B (750MB, 5-8 tok/s) ✅ Default
  - "Quality" - Phi-3.5 Mini (2GB, 2-4 tok/s)
- Auto-download from HuggingFace on first use
- Device recommendations (2GB RAM → 270M, 6GB+ → 1B, 8GB+ → Phi-3.5)
- Model info cards (size, speed, quality comparison)

### **Implementation**:
1. Create `ModelConfig.kt`:
   ```kotlin
   sealed class ModelConfig(
       val displayName: String,
       val fileName: String,
       val sizeBytes: Long,
       val recommendedRamGB: Int,
       val huggingFaceRepo: String
   ) {
       object Fast : ModelConfig(
           "Gemma 3 270M (Fast)",
           "gemma-3-270m-it-Q4_K_M.gguf",
           200_000_000,
           2,
           "bartowski/gemma-3-270m-it-GGUF"
       )

       object Balanced : ModelConfig(
           "Gemma 3 1B (Recommended)",
           "gemma-3-1b-it-Q4_K_M.gguf",
           750_000_000,
           4,
           "bartowski/gemma-3-1b-it-GGUF"
       )

       object Quality : ModelConfig(
           "Phi-3.5 Mini (Best Quality)",
           "Phi-3.5-mini-instruct-Q4_K_M.gguf",
           2_000_000_000,
           6,
           "bartowski/Phi-3.5-mini-instruct-GGUF"
       )
   }
   ```

2. Create `ModelDownloader.kt` (using HuggingFace Hub API)
3. Update `SettingsScreen.kt` with model selector
4. Update `LlamaCppEngine.kt` to accept `modelConfig` parameter

---

## 🔧 Priority 3: Llamatik vs Direct llama.cpp Decision

**Timeline**: Post-beta user feedback → v1.0 decision

### **Stay with Llamatik IF**:
✅ Performance is acceptable (it is - 10+ tok/s for 270M)
✅ Model swapping works well (GGUF is standardized)
✅ No critical missing features
✅ Stability > fine-grained control
✅ User feedback is positive

### **Switch to Direct llama.cpp IF**:
🔴 Users demand advanced sampling controls (temp, top_p, top_k, min_p)
🔴 Performance bottlenecks emerge
🔴 Llamatik bugs block us
🔴 Need custom quantization or GPU acceleration
🔴 Want to optimize for specific hardware (Mali GPU, Adreno, etc.)

### **Hybrid Approach** (v2.0 consideration):
- Keep Llamatik as "Stable" option
- Add direct llama.cpp as "Advanced" option
- Let power users choose in settings
- Best of both worlds!

### **Implementation Effort**:
- **Llamatik** (current): ✅ Done, stable
- **Direct llama.cpp**: 2-3 weeks (JNI, NDK, CMake, testing)

---

## 🚀 Priority 4: Configuration Improvements

### **A. Adaptive System Prompts**
**Current**: Fixed system prompt for all models
**Proposed**: Model-aware prompts

```kotlin
private fun buildCleanSystemPrompt(
    config: GenerationConfig,
    modelSize: ModelSize
): String {
    return when (modelSize) {
        ModelSize.TINY -> "You are M1K3. Be helpful and concise."
        ModelSize.SMALL -> "You are M1K3, a helpful AI assistant. Be accurate and concise."
        ModelSize.MEDIUM -> "You are M1K3 (Mike), a helpful AI assistant running locally on ${getDeviceContext()}. Provide accurate, helpful responses."
        ModelSize.LARGE -> config.systemPrompt ?: "You are M1K3 (Mike), your user's personal AI assistant running 100% locally on their ${getDeviceContext()}. You have access to a comprehensive knowledge base and can help with a wide range of tasks. Be helpful, accurate, and conversational."
    }
}
```

### **B. Context Window Utilization**
**Current**: Not tracking context usage
**Proposed**: Show context usage in UI

- Display "Context: 1,234 / 32,768 tokens" in chat
- Warn when approaching limit
- Auto-summarize old messages when context fills up

### **C. Generation Interruption**
**Current**: Can't stop generation mid-stream
**Proposed**: Add "Stop" button in UI

- Cancel coroutine for generation
- Update LlamaCppEngine to respect cancellation
- Show partial response up to interruption point

---

## 🧪 Priority 5: Testing & Quality

### **A. Model Performance Benchmarks**
Create `ModelBenchmarkTest.kt`:
- Test all model variants (270M, 1B, 3.8B)
- Measure tokens/sec on reference devices
- Quality metrics (coherence, instruction following)
- Context window stress tests (up to 32K tokens)

### **B. Integration Tests**
- Multi-turn conversation tests
- RAG + model interaction tests
- Error recovery scenarios
- Memory leak detection (LeakCanary)

### **C. Real Device Testing**
**Target Devices**:
- Low-end: 2GB RAM Android 8.0 (API 27)
- Mid-range: 6GB RAM Android 12
- High-end: 12GB+ RAM Android 14
- iOS: iPhone 12+ (iOS 15+) - when KMP iOS support ready

---

## 📱 Priority 6: UX Improvements

### **A. Response Quality Indicators**
Show user when response might be limited:
- "⚡ Fast model - responses may be brief"
- "🧠 Quality model - generating thoughtful response..."
- Tooltip explaining model trade-offs

### **B. Regeneration Options**
- "Regenerate with more detail" button
- "Make this longer" / "Make this shorter" quick actions
- Save favorite responses

### **C. Conversation Management**
- Export conversations as markdown/PDF
- Search within conversations
- Tag/categorize chats
- Archive old conversations

---

## 🔮 Priority 7: Advanced Features (v2.0+)

### **A. Multi-Modal Support**
- Image understanding with LLaVA models
- Vision + text unified conversations
- Already have ML Kit Vision - integrate with LLM

### **B. Fine-Tuning Support**
- Allow users to fine-tune models on their data
- LoRA adapters for personalization
- Privacy-preserving local fine-tuning

### **C. Voice Integration**
- Speech-to-text (already planned in roadmap)
- Text-to-speech for responses
- Voice-only mode for hands-free use

### **D. Agentic Capabilities**
- Function calling / tool use
- Multi-step reasoning
- Code execution sandbox

---

## 📋 Immediate Action Items (Next Session)

**High Priority** (Do first):
1. ✅ Increase max token limits (DONE - now 1024-4096)
2. 🔲 Upgrade to Gemma 3 1B Q4_K_M (~1 hour)
3. 🔲 Test response quality improvement
4. 🔲 Update CLAUDE.md with new model info

**Medium Priority** (This week):
5. 🔲 Create model selection UI mockups
6. 🔲 Research HuggingFace Hub API for downloads
7. 🔲 Benchmark Gemma 3 1B vs 270M
8. 🔲 Gather user feedback on response quality

**Low Priority** (Post-beta):
9. 🔲 Evaluate Phi-3.5 Mini vs Gemma 3 1B
10. 🔲 Decide on Llamatik vs direct llama.cpp
11. 🔲 Plan model selection architecture
12. 🔲 Create comprehensive model benchmark suite

---

## 📊 Success Metrics

**Beta v0.1.0 Goals**:
- User satisfaction with response quality: >80%
- Average response time: <10 seconds
- App crash rate: <0.1%
- Model inference failures: <1%

**v1.0 Goals**:
- Multiple model options available
- Users can choose quality vs speed
- Response quality rated 4+ stars (out of 5)
- Supports conversations >10 turns without degradation

---

## 🤝 Decision Framework

When evaluating improvements, ask:
1. **Does it improve user experience?** (UX first)
2. **Can we ship it this week?** (Velocity matters)
3. **Does it risk stability?** (Don't break what works)
4. **Is it reversible?** (Prefer safe experiments)
5. **What's the maintenance cost?** (Sustainability check)

**Bias toward action**: Ship small improvements frequently rather than big changes rarely.

---

## 📚 Resources

**Model Repositories**:
- Gemma 3: https://huggingface.co/bartowski/gemma-3-270m-it-GGUF
- Phi-3.5: https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF
- Qwen2.5: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF

**Documentation**:
- Gemma 3 Prompt Format: https://ai.google.dev/gemma/docs/core/prompt-structure
- llama.cpp: https://github.com/ggerganov/llama.cpp
- Llamatik: https://github.com/turtton/llamatik
- GGUF Format: https://github.com/ggerganov/ggml/blob/master/docs/gguf.md

**Benchmarks**:
- Open LLM Leaderboard: https://huggingface.co/spaces/open-llm-leaderboard/open_llm_leaderboard
- Mobile AI Benchmark: https://ai-benchmark.com/ranking.html

---

**Last Updated**: 2025-12-25
**Next Review**: After beta user feedback
**Owner**: Lead Engineer (Claude + Development Team)

🎄 **Merry Christmas! Let's build something amazing in 2026!** 🎄
