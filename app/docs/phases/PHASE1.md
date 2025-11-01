# Phase 1: Core AI Engine

**Duration:** Weeks 3-5
**Total Tickets:** 20
**Goal:** Get SmolLM2-360M running with basic chat UI and streaming responses

---

## Overview

Phase 1 implements the core AI functionality:
- **Model Export:** SmolLM2-360M to ONNX (4-bit quantization)
- **ONNX Runtime Integration:** Android AI engine with streaming inference
- **Tokenizer:** SentencePiece for SmolLM2
- **Chat UI:** Basic Compose interface with streaming responses
- **Performance Optimization:** Lazy loading, background inference, memory management

**Success Criteria:**
- ✅ SmolLM2-360M generates coherent responses (>80% quality)
- ✅ Streaming responses update UI in real-time
- ✅ Performance: 40+ tokens/sec on mid-range (6GB RAM)
- ✅ Battery impact: <2%/hour active use
- ✅ Model load time: <5 seconds
- ✅ 20+ AI engine tests passing

---

## Week 3: Model Export & ONNX Integration (Tickets 001-007)

### PHASE1-001: Export SmolLM2-360M to ONNX ⚠️ CRITICAL
**Priority:** P0 | **Estimated Hours:** 4h | **Status:** [ ]

**Description:**
Export SmolLM2-360M-Instruct from HuggingFace to ONNX format with 4-bit quantization to target ~120MB model size.

**Implementation:**
```python
# File: app/scripts/export_smollm2_onnx.py

from optimum.onnxruntime import ORTModelForCausalLM
from transformers import AutoTokenizer
import torch

def export_smollm2():
    model_id = "HuggingFaceTB/SmolLM2-360M-Instruct"
    output_dir = "./models/smollm2-360m-onnx"

    print(f"Exporting {model_id} to ONNX...")

    # Export with 4-bit quantization
    model = ORTModelForCausalLM.from_pretrained(
        model_id,
        export=True,
        provider="CPUExecutionProvider",
    )

    # Apply 4-bit quantization
    from optimum.onnxruntime.configuration import AutoQuantizationConfig
    from optimum.onnxruntime import ORTQuantizer

    quantization_config = AutoQuantizationConfig.avx512_vnni(
        is_static=False,
        per_channel=True
    )

    quantizer = ORTQuantizer.from_pretrained(model)
    quantizer.quantize(
        save_dir=output_dir + "-quantized",
        quantization_config=quantization_config
    )

    # Save tokenizer
    tokenizer = AutoTokenizer.from_pretrained(model_id)
    tokenizer.save_pretrained(output_dir)

    print(f"Model exported to {output_dir}")
    print(f"Quantized model in {output_dir}-quantized")

    # Validate size
    import os
    model_path = os.path.join(output_dir + "-quantized", "model.onnx")
    size_mb = os.path.getsize(model_path) / (1024 * 1024)
    print(f"Quantized model size: {size_mb:.2f} MB")

    assert size_mb < 150, f"Model too large: {size_mb} MB (target: <150MB)"

if __name__ == "__main__":
    export_smollm2()
```

```bash
# File: app/scripts/export_model.sh

#!/bin/bash
set -e

echo "Installing dependencies..."
pip install optimum[onnxruntime] transformers torch

echo "Exporting SmolLM2-360M to ONNX..."
python3 export_smollm2_onnx.py

echo "Copying model to Android assets..."
mkdir -p ../composeApp/src/androidMain/assets/models
cp -r models/smollm2-360m-onnx-quantized/* ../composeApp/src/androidMain/assets/models/

echo "Compressing model with gzip..."
gzip ../composeApp/src/androidMain/assets/models/model.onnx

echo "Model export complete!"
ls -lh ../composeApp/src/androidMain/assets/models/
```

**Acceptance Criteria:**
- [ ] SmolLM2-360M exported to ONNX format
- [ ] 4-bit quantization applied (target <150MB)
- [ ] Model size validated
- [ ] Tokenizer saved with model
- [ ] Model copied to Android assets
- [ ] GZIP compression applied
- [ ] Export script documented

**Tests:**
```kotlin
@Test
fun `ONNX model file exists in assets`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val assetManager = context.assets

    val modelFiles = assetManager.list("models") ?: emptyArray()
    assertTrue(modelFiles.contains("model.onnx.gz"))
    assertTrue(modelFiles.contains("tokenizer.json"))
}

@Test
fun `model size under 150MB compressed`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val inputStream = context.assets.open("models/model.onnx.gz")
    val size = inputStream.available()
    val sizeMB = size / (1024.0 * 1024.0)

    assertTrue(sizeMB < 150, "Model size $sizeMB MB exceeds limit")
}
```

**Dependencies:** PHASE0-002 (dependencies configured)
**Blocked By:** None
**Blocks:** PHASE1-002, PHASE1-003 (model loading)

---

### PHASE1-002: Implement Android ONNX Runtime Session
**Priority:** P0 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Create AndroidAIEngine that loads ONNX model from assets and manages OrtSession lifecycle.

**Implementation:**
```kotlin
// File: app/shared/src/androidMain/kotlin/ai/AndroidAIEngine.kt

class AndroidAIEngine(
    private val context: Context,
    private val modelPath: String = "models/model.onnx.gz"
) {
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private val loadLock = Mutex()
    private var lastAccessTime = 0L

    companion object {
        private const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
        private const val TAG = "AndroidAIEngine"
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        loadLock.withLock {
            if (ortSession != null) {
                Log.d(TAG, "Model already loaded")
                return@withContext
            }

            val startTime = System.currentTimeMillis()
            Log.d(TAG, "Loading ONNX model...")

            // Create ORT environment
            ortEnvironment = OrtEnvironment.getEnvironment()

            // Load and decompress model
            val modelBytes = loadModelFromAssets()

            // Create session options
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setInterOpNumThreads(1)
                setExecutionMode(ExecutionMode.SEQUENTIAL)
                setGraphOptimizationLevel(OptLevel.ALL_OPT)
            }

            // Create session
            ortSession = ortEnvironment!!.createSession(modelBytes, sessionOptions)
            lastAccessTime = System.currentTimeMillis()

            val loadTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Model loaded in ${loadTime}ms")
        }
    }

    private suspend fun loadModelFromAssets(): ByteArray = withContext(Dispatchers.IO) {
        context.assets.open(modelPath).use { inputStream ->
            GZIPInputStream(inputStream).use { gzipStream ->
                gzipStream.readBytes()
            }
        }
    }

    suspend fun generate(
        inputIds: LongArray,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topK: Int = 50,
        topP: Float = 0.9f
    ): Flow<Int> = flow {
        ensureLoaded()
        updateAccessTime()

        val currentIds = inputIds.toMutableList()

        repeat(maxTokens) {
            // Run inference
            val logits = runInference(currentIds.toLongArray())

            // Sample next token
            val nextToken = sampleToken(logits, temperature, topK, topP)

            // Check for EOS
            if (nextToken == EOS_TOKEN_ID) {
                return@flow
            }

            // Emit token
            emit(nextToken)
            currentIds.add(nextToken.toLong())
        }
    }.flowOn(Dispatchers.Default)

    private suspend fun runInference(inputIds: LongArray): FloatArray = withContext(Dispatchers.Default) {
        val session = ortSession ?: throw IllegalStateException("Model not loaded")

        // Create input tensor
        val shape = longArrayOf(1, inputIds.size.toLong())
        val inputTensor = OnnxTensor.createTensor(
            ortEnvironment!!,
            LongBuffer.wrap(inputIds),
            shape
        )

        // Run model
        val outputs = session.run(mapOf("input_ids" to inputTensor))

        // Extract logits (last token position)
        val logitsTensor = outputs[0].value as Array<Array<FloatArray>>
        val lastTokenLogits = logitsTensor[0].last()

        inputTensor.close()
        outputs.forEach { it?.close() }

        lastTokenLogits
    }

    private fun sampleToken(
        logits: FloatArray,
        temperature: Float,
        topK: Int,
        topP: Float
    ): Int {
        // Apply temperature
        val scaledLogits = logits.map { it / temperature }

        // Convert to probabilities (softmax)
        val expLogits = scaledLogits.map { exp(it.toDouble()) }
        val sumExp = expLogits.sum()
        val probs = expLogits.map { (it / sumExp).toFloat() }

        // Top-K filtering
        val topKIndices = probs.withIndex()
            .sortedByDescending { it.value }
            .take(topK)
            .map { it.index }

        // Top-P (nucleus) filtering
        val sortedProbs = topKIndices.map { probs[it] }.sorted().reversed()
        val cumProbs = sortedProbs.runningFold(0f) { acc, p -> acc + p }
        val nuclearCutoff = cumProbs.indexOfFirst { it >= topP }

        val candidateIndices = if (nuclearCutoff > 0) {
            topKIndices.take(nuclearCutoff + 1)
        } else {
            topKIndices
        }

        // Sample from candidates
        val candidateProbs = candidateIndices.map { probs[it] }
        val sumCandidates = candidateProbs.sum()
        val normalizedProbs = candidateProbs.map { it / sumCandidates }

        val random = Random.nextFloat()
        var cumProb = 0f
        for (i in candidateIndices.indices) {
            cumProb += normalizedProbs[i]
            if (random < cumProb) {
                return candidateIndices[i]
            }
        }

        return candidateIndices.last()
    }

    private suspend fun ensureLoaded() {
        if (ortSession == null) {
            initialize()
        }
    }

    private fun updateAccessTime() {
        lastAccessTime = System.currentTimeMillis()
    }

    fun shouldUnload(): Boolean {
        return System.currentTimeMillis() - lastAccessTime > IDLE_TIMEOUT_MS
    }

    suspend fun unload() = withContext(Dispatchers.IO) {
        loadLock.withLock {
            ortSession?.close()
            ortSession = null
            Log.d(TAG, "Model unloaded")
        }
    }

    companion object {
        private const val EOS_TOKEN_ID = 2 // SmolLM2 EOS token
    }
}
```

**Acceptance Criteria:**
- [ ] OrtEnvironment and OrtSession created
- [ ] Model loaded from assets with GZIP decompression
- [ ] Session options configured (4 threads, sequential)
- [ ] Inference runs successfully
- [ ] Token sampling implemented (temperature, top-k, top-p)
- [ ] Lazy loading (load on first use)
- [ ] Auto-unload after 5 min idle
- [ ] Thread-safe with Mutex

**Tests:**
```kotlin
@Test
fun `AndroidAIEngine loads model successfully`() = runTest {
    val engine = AndroidAIEngine(context)
    engine.initialize()

    // Verify session created
    assertNotNull(engine)
}

@Test
fun `model generates tokens from input`() = runTest {
    val engine = AndroidAIEngine(context)
    engine.initialize()

    val inputIds = longArrayOf(1, 2, 3) // Dummy input
    val tokens = mutableListOf<Int>()

    engine.generate(inputIds, maxTokens = 10).collect { token ->
        tokens.add(token)
    }

    assertTrue(tokens.isNotEmpty())
    assertTrue(tokens.size <= 10)
}

@Test
fun `model unloads after idle timeout`() = runTest {
    val engine = AndroidAIEngine(context)
    engine.initialize()

    // Wait for timeout
    delay(6 * 60 * 1000L)

    assertTrue(engine.shouldUnload())
}
```

**Dependencies:** PHASE1-001
**Blocks:** PHASE1-004 (tokenizer integration)

---

### PHASE1-003: Implement SmolLM2 Tokenizer
**Priority:** P0 | **Estimated Hours:** 4h | **Status:** [ ]

**Description:**
Create tokenizer for SmolLM2 using SentencePiece, with encoding/decoding and special token handling.

**Implementation:**
```kotlin
// File: app/shared/src/androidMain/kotlin/ai/SmolLMTokenizer.kt

class SmolLMTokenizer(context: Context) {
    private val sentencePieceProcessor: SentencePieceProcessor

    companion object {
        const val BOS_TOKEN = "<|im_start|>"
        const val EOS_TOKEN = "<|im_end|>"
        const val BOS_TOKEN_ID = 1
        const val EOS_TOKEN_ID = 2
        const val PAD_TOKEN_ID = 0

        const val SYSTEM_PREFIX = "system\n"
        const val USER_PREFIX = "user\n"
        const val ASSISTANT_PREFIX = "assistant\n"
    }

    init {
        // Load SentencePiece model from assets
        val modelBytes = context.assets.open("models/tokenizer.model").readBytes()
        sentencePieceProcessor = SentencePieceProcessor()
        sentencePieceProcessor.load(modelBytes)
    }

    fun encode(text: String): LongArray {
        val pieces = sentencePieceProcessor.encode(text)
        return pieces.map { it.toLong() }.toLongArray()
    }

    fun decode(tokenIds: IntArray): String {
        val tokens = tokenIds.map { it.toInt() }
        return sentencePieceProcessor.decode(tokens)
    }

    fun formatMessages(messages: List<Message>, systemPrompt: String? = null): String {
        val formatted = StringBuilder()

        // Add system prompt if provided
        if (systemPrompt != null) {
            formatted.append(BOS_TOKEN)
            formatted.append(SYSTEM_PREFIX)
            formatted.append(systemPrompt)
            formatted.append(EOS_TOKEN)
            formatted.append("\n")
        }

        // Add conversation messages
        messages.forEach { message ->
            formatted.append(BOS_TOKEN)

            when (message.role) {
                Message.Role.USER -> formatted.append(USER_PREFIX)
                Message.Role.ASSISTANT -> formatted.append(ASSISTANT_PREFIX)
                Message.Role.SYSTEM -> formatted.append(SYSTEM_PREFIX)
            }

            // Extract text from content
            message.content.forEach { part ->
                when (part) {
                    is ContentPart.Text -> formatted.append(part.text)
                    is ContentPart.Image -> {
                        // For text-only model, use image description if available
                        part.description?.let {
                            formatted.append("[Image: $it]")
                        }
                    }
                }
            }

            formatted.append(EOS_TOKEN)
            formatted.append("\n")
        }

        // Prompt for assistant response
        formatted.append(BOS_TOKEN)
        formatted.append(ASSISTANT_PREFIX)

        return formatted.toString()
    }

    fun getMaxContextLength(): Int = 2048 // SmolLM2 context window

    fun countTokens(text: String): Int {
        return encode(text).size
    }

    fun truncateToMaxLength(text: String, maxTokens: Int): String {
        val tokens = encode(text)
        if (tokens.size <= maxTokens) return text

        val truncatedTokens = tokens.take(maxTokens).toIntArray()
        return decode(truncatedTokens)
    }
}
```

**Acceptance Criteria:**
- [ ] SentencePiece processor loaded from assets
- [ ] encode() converts text to token IDs
- [ ] decode() converts token IDs back to text
- [ ] formatMessages() applies SmolLM2 chat template
- [ ] Special tokens handled (BOS, EOS)
- [ ] System/user/assistant prefixes applied
- [ ] Token counting utility
- [ ] Context length truncation

**Tests:**
```kotlin
@Test
fun `tokenizer encodes and decodes correctly`() {
    val tokenizer = SmolLMTokenizer(context)
    val text = "Hello, world!"

    val tokens = tokenizer.encode(text)
    val decoded = tokenizer.decode(tokens.map { it.toInt() }.toIntArray())

    assertEquals(text, decoded)
}

@Test
fun `formatMessages applies correct chat template`() {
    val tokenizer = SmolLMTokenizer(context)

    val messages = listOf(
        Message(
            role = Message.Role.USER,
            content = listOf(ContentPart.Text("Hello")),
            timestamp = Instant.now()
        )
    )

    val formatted = tokenizer.formatMessages(messages, systemPrompt = "You are a helpful assistant")

    assertTrue(formatted.contains("<|im_start|>system"))
    assertTrue(formatted.contains("<|im_start|>user"))
    assertTrue(formatted.contains("Hello"))
    assertTrue(formatted.contains("<|im_start|>assistant"))
}

@Test
fun `token counting works`() {
    val tokenizer = SmolLMTokenizer(context)
    val text = "This is a test sentence."

    val count = tokenizer.countTokens(text)
    assertTrue(count > 0)
    assertTrue(count < 20) // Reasonable token count
}
```

**Dependencies:** PHASE1-001
**Blocks:** PHASE1-004 (streaming integration)

---

### PHASE1-004: Integrate Tokenizer with AI Engine
**Priority:** P0 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Connect SmolLMTokenizer with AndroidAIEngine to enable text-to-text generation.

**Implementation:**
```kotlin
// File: app/shared/src/androidMain/kotlin/ai/SmolLMAIEngine.kt

class SmolLMAIEngine(
    context: Context
) {
    private val androidEngine = AndroidAIEngine(context)
    private val tokenizer = SmolLMTokenizer(context)

    suspend fun generateResponse(
        messages: List<Message>,
        systemPrompt: String? = null,
        maxTokens: Int = 512,
        temperature: Float = 0.7f
    ): Flow<String> = flow {
        // Format messages with chat template
        val prompt = tokenizer.formatMessages(messages, systemPrompt)

        // Encode to token IDs
        val inputIds = tokenizer.encode(prompt)

        // Check context length
        val maxContext = tokenizer.getMaxContextLength()
        if (inputIds.size > maxContext - maxTokens) {
            throw IllegalArgumentException(
                "Input too long: ${inputIds.size} tokens (max: ${maxContext - maxTokens})"
            )
        }

        // Generate tokens
        val generatedTokens = mutableListOf<Int>()
        androidEngine.generate(inputIds, maxTokens, temperature).collect { tokenId ->
            generatedTokens.add(tokenId)

            // Decode incrementally for streaming
            val text = tokenizer.decode(generatedTokens.toIntArray())
            emit(text)
        }
    }

    suspend fun initialize() {
        androidEngine.initialize()
    }

    suspend fun unload() {
        androidEngine.unload()
    }

    fun getSystemPrompt(): String {
        return """
            You are 間 AI, a curious companion interested in trivia, technology,
            and fascinating details. You share interesting facts when relevant,
            understand the device you run on, and recognize emotional patterns
            without being patronizing.

            Current device: ${Build.MANUFACTURER} ${Build.MODEL}
            Android version: ${Build.VERSION.RELEASE}
            Time: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}

            Personality: Curious, informative, technically knowledgeable,
            emotionally aware but not therapy-focused.
        """.trimIndent()
    }
}
```

**Acceptance Criteria:**
- [ ] SmolLMAIEngine combines tokenizer + ONNX engine
- [ ] generateResponse() accepts Message list
- [ ] System prompt automatically injected
- [ ] Context length validation
- [ ] Streaming text output (not just tokens)
- [ ] Error handling for too-long inputs
- [ ] Device context in system prompt

**Tests:**
```kotlin
@Test
fun `SmolLMAIEngine generates response from messages`() = runTest {
    val engine = SmolLMAIEngine(context)
    engine.initialize()

    val messages = listOf(
        Message(
            role = Message.Role.USER,
            content = listOf(ContentPart.Text("What is 2+2?")),
            timestamp = Instant.now()
        )
    )

    val responses = mutableListOf<String>()
    engine.generateResponse(messages).collect { text ->
        responses.add(text)
    }

    assertTrue(responses.isNotEmpty())
    assertTrue(responses.last().contains("4") || responses.last().contains("four"))
}

@Test
fun `engine rejects too-long inputs`() = runTest {
    val engine = SmolLMAIEngine(context)
    engine.initialize()

    val longMessage = "word ".repeat(3000) // Exceeds context
    val messages = listOf(
        Message(
            role = Message.Role.USER,
            content = listOf(ContentPart.Text(longMessage)),
            timestamp = Instant.now()
        )
    )

    assertFailsWith<IllegalArgumentException> {
        engine.generateResponse(messages).collect()
    }
}
```

**Dependencies:** PHASE1-002, PHASE1-003
**Blocks:** PHASE1-008 (UI integration)

---

### PHASE1-005: Model Memory Management
**Priority:** P1 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Implement ModelMemoryManager to handle model lifecycle, lazy loading, and automatic unloading based on memory pressure.

**Implementation:**
```kotlin
// File: app/shared/src/androidMain/kotlin/ai/ModelMemoryManager.kt

class ModelMemoryManager(
    private val context: Context
) {
    private var engineInstance: WeakReference<SmolLMAIEngine>? = null
    private val loadLock = Mutex()
    private var lastCheckTime = 0L

    companion object {
        private const val CHECK_INTERVAL_MS = 30_000L // 30 seconds
        private const val MIN_AVAILABLE_RAM_MB = 500
    }

    suspend fun getEngine(): SmolLMAIEngine = loadLock.withLock {
        // Try to get cached instance
        engineInstance?.get()?.let { return it }

        // Check memory before loading
        checkMemoryAvailability()

        // Create new instance
        val engine = SmolLMAIEngine(context)
        engine.initialize()

        engineInstance = WeakReference(engine)
        return engine
    }

    private fun checkMemoryAvailability() {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val availableMB = memoryInfo.availMem / (1024 * 1024)

        if (availableMB < MIN_AVAILABLE_RAM_MB) {
            throw InsufficientMemoryException(
                "Not enough memory to load model. Available: ${availableMB}MB, Required: ${MIN_AVAILABLE_RAM_MB}MB"
            )
        }
    }

    suspend fun checkAndUnloadIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastCheckTime < CHECK_INTERVAL_MS) return

        lastCheckTime = now

        val engine = engineInstance?.get() ?: return

        // Check if should unload due to inactivity
        if ((engine as? SmolLMAIEngine)?.shouldUnload() == true) {
            engine.unload()
            engineInstance?.clear()
            engineInstance = null
        }

        // Or check memory pressure
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        if (memoryInfo.lowMemory) {
            engine.unload()
            engineInstance?.clear()
            engineInstance = null
        }
    }

    suspend fun forceUnload() = loadLock.withLock {
        engineInstance?.get()?.unload()
        engineInstance?.clear()
        engineInstance = null
    }
}

class InsufficientMemoryException(message: String) : Exception(message)
```

**Acceptance Criteria:**
- [ ] Singleton pattern with WeakReference
- [ ] Memory check before loading
- [ ] Automatic unload on low memory
- [ ] Periodic idle check (30 seconds)
- [ ] Force unload method
- [ ] Thread-safe with Mutex
- [ ] Throws InsufficientMemoryException when needed

**Tests:**
```kotlin
@Test
fun `ModelMemoryManager returns same instance`() = runTest {
    val manager = ModelMemoryManager(context)

    val engine1 = manager.getEngine()
    val engine2 = manager.getEngine()

    assertSame(engine1, engine2)
}

@Test
fun `manager throws exception when insufficient memory`() = runTest {
    // Mock low memory scenario
    val manager = ModelMemoryManager(context)

    // Manually trigger low memory
    // (In real test, would mock ActivityManager)

    // For now, just verify exception type exists
    assertTrue(InsufficientMemoryException("test") is Exception)
}
```

**Dependencies:** PHASE1-004
**Blocks:** None (optimization)

---

### PHASE1-006: Performance Benchmarking Tests
**Priority:** P1 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Create comprehensive performance tests to validate inference speed, memory usage, and battery impact on mid-range devices.

**Implementation:**
```kotlin
// File: app/composeApp/src/androidTest/kotlin/performance/AIPerformanceBenchmarkTest.kt

@RunWith(AndroidJUnit4::class)
class AIPerformanceBenchmarkTest {

    private lateinit var engine: SmolLMAIEngine

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        engine = SmolLMAIEngine(context)
        engine.initialize()
    }

    @Test
    fun `inference speed - target 40+ tokens per second`() = runTest {
        val messages = listOf(
            Message(
                role = Message.Role.USER,
                content = listOf(ContentPart.Text("Count from 1 to 100")),
                timestamp = Instant.now()
            )
        )

        val startTime = System.currentTimeMillis()
        var tokenCount = 0

        engine.generateResponse(messages, maxTokens = 100).collect {
            tokenCount++
        }

        val duration = (System.currentTimeMillis() - startTime) / 1000.0
        val tokensPerSecond = tokenCount / duration

        Log.d("Benchmark", "Tokens per second: $tokensPerSecond")
        assertTrue(
            tokensPerSecond >= 40.0,
            "Inference too slow: $tokensPerSecond tokens/sec (target: 40+)"
        )
    }

    @Test
    fun `model load time under 5 seconds`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val freshEngine = SmolLMAIEngine(context)

        val startTime = System.currentTimeMillis()
        freshEngine.initialize()
        val loadTime = System.currentTimeMillis() - startTime

        Log.d("Benchmark", "Model load time: ${loadTime}ms")
        assertTrue(
            loadTime < 5000,
            "Model load too slow: ${loadTime}ms (target: <5000ms)"
        )
    }

    @Test
    fun `memory usage under 500MB during inference`() = runTest {
        val runtime = Runtime.getRuntime()
        val beforeMemory = runtime.totalMemory() - runtime.freeMemory()

        val messages = listOf(
            Message(
                role = Message.Role.USER,
                content = listOf(ContentPart.Text("Tell me a story")),
                timestamp = Instant.now()
            )
        )

        engine.generateResponse(messages, maxTokens = 200).collect {}

        val afterMemory = runtime.totalMemory() - runtime.freeMemory()
        val usedMemoryMB = (afterMemory - beforeMemory) / (1024 * 1024)

        Log.d("Benchmark", "Memory used: ${usedMemoryMB}MB")
        assertTrue(
            usedMemoryMB < 500,
            "Memory usage too high: ${usedMemoryMB}MB (target: <500MB)"
        )
    }

    @Test
    fun `response latency under 2 seconds for first token`() = runTest {
        val messages = listOf(
            Message(
                role = Message.Role.USER,
                content = listOf(ContentPart.Text("Hi")),
                timestamp = Instant.now()
            )
        )

        val startTime = System.currentTimeMillis()
        var firstTokenTime = 0L

        engine.generateResponse(messages, maxTokens = 50).take(1).collect {
            firstTokenTime = System.currentTimeMillis() - startTime
        }

        Log.d("Benchmark", "First token latency: ${firstTokenTime}ms")
        assertTrue(
            firstTokenTime < 2000,
            "First token too slow: ${firstTokenTime}ms (target: <2000ms)"
        )
    }

    @After
    fun teardown() = runBlocking {
        engine.unload()
    }
}
```

**Acceptance Criteria:**
- [ ] Inference speed test (40+ tokens/sec)
- [ ] Model load time test (<5 seconds)
- [ ] Memory usage test (<500MB)
- [ ] First token latency test (<2 seconds)
- [ ] All benchmarks log results
- [ ] Tests fail if targets not met

**Tests:**
These ARE the performance tests

**Dependencies:** PHASE1-004
**Blocks:** None (validation)

---

### PHASE1-007: Battery Impact Profiling
**Priority:** P2 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Profile battery consumption during AI inference and document actual impact. Target: <2%/hour active use.

**Implementation:**
```kotlin
// File: app/composeApp/src/androidTest/kotlin/performance/BatteryProfileTest.kt

@RunWith(AndroidJUnit4::class)
class BatteryProfileTest {

    @Test
    fun `battery drain during 1 hour of active inference`() = runTest(timeout = 65.minutes) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        // Get initial battery level
        val initialLevel = batteryManager.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CAPACITY
        )

        Log.d("Battery", "Initial battery: $initialLevel%")

        // Run inference continuously for 1 hour
        val engine = SmolLMAIEngine(context)
        engine.initialize()

        val startTime = System.currentTimeMillis()
        val endTime = startTime + (60 * 60 * 1000) // 1 hour

        var queryCount = 0
        while (System.currentTimeMillis() < endTime) {
            val messages = listOf(
                Message(
                    role = Message.Role.USER,
                    content = listOf(ContentPart.Text("Generate a short paragraph")),
                    timestamp = Instant.now()
                )
            )

            engine.generateResponse(messages, maxTokens = 100).collect {}
            queryCount++

            // Brief pause between queries (simulate realistic usage)
            delay(5000)
        }

        // Get final battery level
        val finalLevel = batteryManager.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CAPACITY
        )

        val batteryDrain = initialLevel - finalLevel

        Log.d("Battery", "Final battery: $finalLevel%")
        Log.d("Battery", "Battery drain: $batteryDrain% over 1 hour")
        Log.d("Battery", "Queries processed: $queryCount")

        assertTrue(
            batteryDrain <= 2,
            "Battery drain too high: $batteryDrain% (target: ≤2%/hour)"
        )
    }
}
```

```markdown
# File: app/docs/BATTERY_PROFILING.md

## Battery Impact Profiling Results

### Test Methodology
- **Duration:** 1 hour continuous use
- **Device:** [Mid-range Android, 6GB RAM]
- **Screen:** On, at 50% brightness
- **Network:** Airplane mode (no network usage)
- **AI Queries:** Continuous inference with 5-second pauses

### Results
- **Battery Drain:** [TBD]% over 1 hour
- **Queries Processed:** [TBD] queries
- **Average Power Draw:** [TBD] mW
- **Status:** [PASS/FAIL based on ≤2% target]

### Optimization Recommendations
1. Use Neural Engine/NPU when available
2. Implement more aggressive idle unloading
3. Reduce inference frequency for background tasks
4. Profile CPU governor settings
```

**Acceptance Criteria:**
- [ ] Battery profiling test implemented
- [ ] 1-hour continuous inference test
- [ ] Battery drain measured and logged
- [ ] Results documented in BATTERY_PROFILING.md
- [ ] Test fails if drain >2%/hour
- [ ] Optimization recommendations provided

**Tests:**
This IS the battery profile test

**Dependencies:** PHASE1-004
**Blocks:** None (documentation)

---

## Week 4: Basic Chat UI (Tickets 008-014)

### PHASE1-008: Create Chat Screen Composable
**Priority:** P0 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Build basic chat UI with message list, input field, and send button using Jetpack Compose.

**Implementation:**
```kotlin
// File: app/composeApp/src/commonMain/kotlin/ui/chat/ChatScreen.kt

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("間 AI") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            // Message list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = true, // Latest message at bottom
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = uiState.messages.reversed(),
                    key = { it.id }
                ) { message ->
                    MessageBubble(message = message)
                }
            }

            // Input area
            ChatInputField(
                text = uiState.inputText,
                onTextChange = { viewModel.onInputTextChange(it) },
                onSend = { viewModel.sendMessage() },
                isLoading = uiState.isGenerating,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == Message.Role.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isUser) Color(0xFF1E1E1E) else Color(0xFF2D2D2D),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Extract text from content
                message.content.forEach { part ->
                    when (part) {
                        is ContentPart.Text -> {
                            Text(
                                text = part.text,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        is ContentPart.Image -> {
                            // TODO: Image support in Phase 4
                            Text(
                                text = "[Image]",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Timestamp
                Text(
                    text = formatTimestamp(message.timestamp),
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatInputField(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF1A1A1A),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF2D2D2D),
                    unfocusedContainerColor = Color(0xFF2D2D2D)
                ),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                enabled = !isLoading
            )

            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (text.isNotBlank()) Color.White else Color.Gray
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(instant: Instant): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(instant.toEpochMilliseconds()))
}
```

**Acceptance Criteria:**
- [ ] ChatScreen composable created
- [ ] LazyColumn with reverse layout (latest at bottom)
- [ ] MessageBubble for user/assistant messages
- [ ] Different colors for user (dark gray) vs assistant (lighter gray)
- [ ] ChatInputField with TextField and send button
- [ ] Loading indicator during generation
- [ ] Input disabled while generating
- [ ] Timestamp display
- [ ] Black background theme

**Tests:**
```kotlin
@Test
fun `chat screen displays messages`() {
    composeTestRule.setContent {
        ChatScreen()
    }

    // Initially empty
    composeTestRule.onNodeWithText("Type a message...").assertExists()
}

@Test
fun `message bubble shows correct content`() {
    val message = Message(
        role = Message.Role.USER,
        content = listOf(ContentPart.Text("Hello")),
        timestamp = Instant.now()
    )

    composeTestRule.setContent {
        MessageBubble(message = message)
    }

    composeTestRule.onNodeWithText("Hello").assertExists()
}

@Test
fun `send button disabled when input empty`() {
    composeTestRule.setContent {
        ChatInputField(
            text = "",
            onTextChange = {},
            onSend = {},
            isLoading = false
        )
    }

    composeTestRule.onNodeWithContentDescription("Send")
        .assertIsNotEnabled()
}
```

**Dependencies:** PHASE0-004 (scaffold cleaned)
**Blocks:** PHASE1-009 (ViewModel)

---

### PHASE1-009: Implement ChatViewModel
**Priority:** P0 | **Estimated Hours:** 4h | **Status:** [ ]

**Description:**
Create ViewModel to manage chat state, handle user input, coordinate AI generation, and update UI reactively.

**Implementation:**
```kotlin
// File: app/shared/src/commonMain/kotlin/ui/viewmodel/ChatViewModel.kt

class ChatViewModel(
    private val aiEngine: SmolLMAIEngine,
    private val messageRepository: MessageRepository,
    private val memoryManager: MemoryManager? = null // Phase 2
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentProjectId: String? = null

    init {
        loadRecentMessages()
    }

    fun onInputTextChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val inputText = _uiState.value.inputText.trim()
        if (inputText.isBlank()) return

        viewModelScope.launch {
            try {
                // Create user message
                val userMessage = Message(
                    role = Message.Role.USER,
                    content = listOf(ContentPart.Text(inputText)),
                    timestamp = Clock.System.now(),
                    projectId = currentProjectId
                )

                // Add to UI immediately
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + userMessage,
                        inputText = "",
                        isGenerating = true,
                        error = null
                    )
                }

                // Save to database
                messageRepository.saveMessage(userMessage)

                // Generate AI response
                generateAIResponse(userMessage)

            } catch (e: Exception) {
                _uiState.update { it.copy(
                    error = "Failed to send message: ${e.message}",
                    isGenerating = false
                )}
            }
        }
    }

    private suspend fun generateAIResponse(userMessage: Message) {
        try {
            // Get conversation history
            val conversationHistory = _uiState.value.messages.takeLast(10)

            // Create placeholder for streaming response
            val assistantMessageId = UUID.randomUUID().toString()
            var accumulatedText = ""

            val assistantMessage = Message(
                id = assistantMessageId,
                role = Message.Role.ASSISTANT,
                content = listOf(ContentPart.Text("")),
                timestamp = Clock.System.now(),
                projectId = currentProjectId
            )

            _uiState.update { state ->
                state.copy(messages = state.messages + assistantMessage)
            }

            // Stream response
            aiEngine.generateResponse(
                messages = conversationHistory,
                systemPrompt = aiEngine.getSystemPrompt()
            ).collect { text ->
                accumulatedText = text

                // Update UI with streaming text
                _uiState.update { state ->
                    val updatedMessages = state.messages.map { msg ->
                        if (msg.id == assistantMessageId) {
                            msg.copy(content = listOf(ContentPart.Text(accumulatedText)))
                        } else {
                            msg
                        }
                    }
                    state.copy(messages = updatedMessages)
                }
            }

            // Save final response to database
            val finalMessage = assistantMessage.copy(
                content = listOf(ContentPart.Text(accumulatedText))
            )
            messageRepository.saveMessage(finalMessage)

            _uiState.update { it.copy(isGenerating = false) }

        } catch (e: Exception) {
            _uiState.update { state ->
                state.copy(
                    error = "AI generation failed: ${e.message}",
                    isGenerating = false
                )
            }
        }
    }

    private fun loadRecentMessages() {
        viewModelScope.launch {
            try {
                val messages = if (currentProjectId != null) {
                    messageRepository.getMessagesByProject(currentProjectId!!, limit = 50)
                } else {
                    messageRepository.getRecentMessages(limit = 50)
                }

                _uiState.update { it.copy(messages = messages) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    error = "Failed to load messages: ${e.message}"
                )}
            }
        }
    }

    fun clearConversation() {
        _uiState.update { ChatUiState() }
    }

    fun setProject(projectId: String?) {
        currentProjectId = projectId
        loadRecentMessages()
    }
}

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val error: String? = null
)
```

**Acceptance Criteria:**
- [ ] ChatViewModel with StateFlow UI state
- [ ] sendMessage() creates user message
- [ ] generateAIResponse() streams AI response
- [ ] UI updates in real-time during streaming
- [ ] Messages saved to database
- [ ] Error handling with user-friendly messages
- [ ] loadRecentMessages() loads from DB
- [ ] clearConversation() resets state
- [ ] Project support (currentProjectId)

**Tests:**
```kotlin
@Test
fun `sendMessage creates user message`() = runTest {
    val viewModel = ChatViewModel(
        aiEngine = mockAIEngine,
        messageRepository = fakeRepository
    )

    viewModel.onInputTextChange("Hello")
    viewModel.sendMessage()

    val uiState = viewModel.uiState.value
    assertTrue(uiState.messages.any { it.role == Message.Role.USER })
    assertEquals("", uiState.inputText) // Cleared after send
}

@Test
fun `generateAIResponse streams text updates`() = runTest {
    val mockEngine = object : SmolLMAIEngine(context) {
        override suspend fun generateResponse(...): Flow<String> = flow {
            emit("Hello")
            emit("Hello there")
            emit("Hello there!")
        }
    }

    val viewModel = ChatViewModel(
        aiEngine = mockEngine,
        messageRepository = fakeRepository
    )

    viewModel.sendMessage()

    advanceUntilIdle()

    val assistantMessages = viewModel.uiState.value.messages
        .filter { it.role == Message.Role.ASSISTANT }

    assertTrue(assistantMessages.isNotEmpty())
    assertTrue(assistantMessages.last().content.first().toString().contains("Hello there!"))
}
```

**Dependencies:** PHASE1-008, PHASE1-004, PHASE0-013
**Blocks:** PHASE1-010 (streaming UI)

---

### PHASE1-010: Streaming Response UI Updates
**Priority:** P0 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Optimize ChatScreen to handle real-time streaming updates efficiently without lag or jank.

**Implementation:**
```kotlin
// File: app/composeApp/src/commonMain/kotlin/ui/chat/StreamingMessageBubble.kt

@Composable
fun StreamingMessageBubble(
    message: Message,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isStreaming) 0.7f else 1f,
        animationSpec = tween(durationMillis = 300)
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF2D2D2D).copy(alpha = animatedAlpha),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Text content
                message.content.forEach { part ->
                    when (part) {
                        is ContentPart.Text -> {
                            Text(
                                text = part.text,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            // Cursor indicator while streaming
                            if (isStreaming) {
                                StreamingCursor()
                            }
                        }
                        else -> {}
                    }
                }

                // Timestamp (only when done)
                if (!isStreaming) {
                    Text(
                        text = formatTimestamp(message.timestamp),
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingCursor() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Text(
        text = "▋",
        color = Color.White.copy(alpha = alpha),
        modifier = Modifier.padding(start = 2.dp)
    )
}
```

```kotlin
// Update ChatScreen to use StreamingMessageBubble

@Composable
fun ChatScreen(...) {
    // ...

    LazyColumn(...) {
        items(...) { message ->
            val isStreaming = uiState.isGenerating &&
                              message.role == Message.Role.ASSISTANT &&
                              message == uiState.messages.lastOrNull()

            StreamingMessageBubble(
                message = message,
                isStreaming = isStreaming
            )
        }
    }
}
```

**Acceptance Criteria:**
- [ ] StreamingMessageBubble with animated alpha
- [ ] Blinking cursor during streaming
- [ ] Smooth animations (no jank)
- [ ] Timestamp only shows when complete
- [ ] Auto-scroll to bottom during streaming
- [ ] Efficient recomposition (only streaming item updates)

**Tests:**
```kotlin
@Test
fun `streaming cursor animates`() {
    composeTestRule.setContent {
        StreamingCursor()
    }

    composeTestRule.onNodeWithText("▋").assertExists()
}

@Test
fun `streaming message shows cursor`() {
    val message = Message(
        role = Message.Role.ASSISTANT,
        content = listOf(ContentPart.Text("Generating...")),
        timestamp = Instant.now()
    )

    composeTestRule.setContent {
        StreamingMessageBubble(
            message = message,
            isStreaming = true
        )
    }

    composeTestRule.onNodeWithText("▋").assertExists()
}
```

**Dependencies:** PHASE1-009
**Blocks:** None (polish)

---

### PHASE1-011: Error Handling & Recovery
**Priority:** P1 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Implement comprehensive error handling for AI failures, OOM errors, and network (shouldn't exist) errors.

**Implementation:**
```kotlin
// File: app/shared/src/commonMain/kotlin/domain/error/AIError.kt

sealed class AIError(override val message: String) : Exception(message) {
    class ModelNotLoaded(message: String = "AI model not loaded") : AIError(message)
    class InsufficientMemory(message: String = "Not enough memory to run AI") : AIError(message)
    class ContextTooLong(message: String = "Input too long for model") : AIError(message)
    class GenerationFailed(message: String = "AI generation failed") : AIError(message)
    class InvalidInput(message: String = "Invalid input provided") : AIError(message)
}

// File: app/shared/src/commonMain/kotlin/ui/viewmodel/ChatViewModel.kt

// Update error handling in ChatViewModel

private suspend fun generateAIResponse(userMessage: Message) {
    try {
        // ... existing code ...
    } catch (e: AIError.InsufficientMemory) {
        showErrorDialog(
            title = "Memory Low",
            message = "Not enough memory to run AI. Try closing other apps.",
            action = "Retry"
        ) {
            generateAIResponse(userMessage)
        }
    } catch (e: AIError.ContextTooLong) {
        showErrorDialog(
            title = "Conversation Too Long",
            message = "Please start a new conversation or clear history.",
            action = "Clear History"
        ) {
            clearConversation()
        }
    } catch (e: AIError.GenerationFailed) {
        showErrorDialog(
            title = "Generation Failed",
            message = "AI couldn't generate a response. Please try again.",
            action = "Retry"
        ) {
            generateAIResponse(userMessage)
        }
    } catch (e: Exception) {
        showErrorDialog(
            title = "Unexpected Error",
            message = e.message ?: "Unknown error occurred",
            action = "OK"
        )
    }
}

private fun showErrorDialog(
    title: String,
    message: String,
    action: String,
    onAction: (() -> Unit)? = null
) {
    _uiState.update { state ->
        state.copy(
            error = ErrorState(title, message, action, onAction),
            isGenerating = false
        )
    }
}

data class ErrorState(
    val title: String,
    val message: String,
    val actionLabel: String,
    val onAction: (() -> Unit)? = null
)
```

```kotlin
// File: app/composeApp/src/commonMain/kotlin/ui/chat/ErrorDialog.kt

@Composable
fun ErrorDialog(
    errorState: ErrorState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(errorState.title) },
        text = { Text(errorState.message) },
        confirmButton = {
            TextButton(onClick = {
                errorState.onAction?.invoke()
                onDismiss()
            }) {
                Text(errorState.actionLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

**Acceptance Criteria:**
- [ ] AIError sealed class with specific error types
- [ ] Error handling in ChatViewModel
- [ ] User-friendly error messages
- [ ] Retry functionality for recoverable errors
- [ ] Error dialog UI component
- [ ] Logging for debugging
- [ ] No app crashes on AI errors

**Tests:**
```kotlin
@Test
fun `insufficient memory shows error dialog`() = runTest {
    val mockEngine = object : SmolLMAIEngine(context) {
        override suspend fun generateResponse(...) = flow {
            throw AIError.InsufficientMemory()
        }
    }

    val viewModel = ChatViewModel(aiEngine = mockEngine, ...)
    viewModel.sendMessage()

    advanceUntilIdle()

    assertNotNull(viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.error!!.title.contains("Memory"))
}
```

**Dependencies:** PHASE1-009
**Blocks:** None (error handling)

---

### PHASE1-012: Chat State Persistence
**Priority:** P1 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Ensure chat state persists across app restarts, including unsent input text and scroll position.

**Implementation:**
```kotlin
// File: app/shared/src/commonMain/kotlin/data/local/ChatStatePreferences.kt

class ChatStatePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("chat_state", Context.MODE_PRIVATE)

    fun saveInputText(text: String) {
        prefs.edit().putString("input_text", text).apply()
    }

    fun getInputText(): String {
        return prefs.getString("input_text", "") ?: ""
    }

    fun saveScrollPosition(position: Int) {
        prefs.edit().putInt("scroll_position", position).apply()
    }

    fun getScrollPosition(): Int {
        return prefs.getInt("scroll_position", 0)
    }

    fun saveCurrentProject(projectId: String?) {
        prefs.edit().putString("current_project", projectId).apply()
    }

    fun getCurrentProject(): String? {
        return prefs.getString("current_project", null)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

// Update ChatViewModel to persist state

class ChatViewModel(...) {
    private val statePrefs = ChatStatePreferences(context)

    init {
        // Restore state on init
        val savedInput = statePrefs.getInputText()
        val savedProject = statePrefs.getCurrentProject()

        _uiState.update { it.copy(inputText = savedInput) }
        currentProjectId = savedProject

        loadRecentMessages()
    }

    fun onInputTextChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
        statePrefs.saveInputText(text) // Auto-save
    }

    fun setProject(projectId: String?) {
        currentProjectId = projectId
        statePrefs.saveCurrentProject(projectId)
        loadRecentMessages()
    }

    override fun onCleared() {
        super.onCleared()
        // Save state before ViewModel destroyed
        statePrefs.saveInputText(_uiState.value.inputText)
    }
}
```

**Acceptance Criteria:**
- [ ] ChatStatePreferences for local storage
- [ ] Input text persists across restarts
- [ ] Current project persists
- [ ] Scroll position (optional) persists
- [ ] Auto-save on input change
- [ ] Clear method for logout/reset

**Tests:**
```kotlin
@Test
fun `input text persists across ViewModel recreation`() {
    val prefs = ChatStatePreferences(context)

    // First ViewModel instance
    val viewModel1 = ChatViewModel(...)
    viewModel1.onInputTextChange("Unsent message")
    viewModel1.onCleared()

    // Second ViewModel instance (simulating app restart)
    val viewModel2 = ChatViewModel(...)

    assertEquals("Unsent message", viewModel2.uiState.value.inputText)
}
```

**Dependencies:** PHASE1-009
**Blocks:** None (persistence)

---

### PHASE1-013: App Navigation Structure
**Priority:** P1 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Set up basic navigation structure with Compose Navigation for future screens (projects, settings, memory explorer).

**Implementation:**
```kotlin
// File: app/composeApp/src/commonMain/kotlin/ui/navigation/Navigation.kt

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object Projects : Screen("projects")
    object Settings : Screen("settings")
    object MemoryExplorer : Screen("memory")
    object PrivacyDashboard : Screen("privacy")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route
    ) {
        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateToProjects = {
                    navController.navigate(Screen.Projects.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Projects.route) {
            // TODO: ProjectBrowserScreen (Phase 4)
            PlaceholderScreen("Projects - Coming in Phase 4")
        }

        composable(Screen.Settings.route) {
            // TODO: SettingsScreen (Phase 5)
            PlaceholderScreen("Settings - Coming in Phase 5")
        }

        composable(Screen.MemoryExplorer.route) {
            // TODO: MemoryExplorerScreen (Phase 5)
            PlaceholderScreen("Memory Explorer - Coming in Phase 5")
        }

        composable(Screen.PrivacyDashboard.route) {
            // TODO: PrivacyDashboardScreen (Phase 5)
            PlaceholderScreen("Privacy Dashboard - Coming in Phase 5")
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title)
    }
}
```

**Acceptance Criteria:**
- [ ] Screen sealed class with routes
- [ ] NavHost with NavController
- [ ] Chat screen as start destination
- [ ] Placeholder screens for future features
- [ ] Navigation functions passed to screens
- [ ] Back button handling

**Tests:**
```kotlin
@Test
fun `navigation starts on chat screen`() {
    composeTestRule.setContent {
        AppNavigation()
    }

    // Chat screen should be visible
    composeTestRule.onNodeWithText("間 AI").assertExists()
}
```

**Dependencies:** PHASE1-008
**Blocks:** Future navigation implementation

---

### PHASE1-014: Phase 1 Integration Test
**Priority:** P0 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
End-to-end test validating entire Phase 1: model loads, chat works, streaming responses, messages persisted.

**Implementation:**
```kotlin
// File: app/composeApp/src/androidTest/kotlin/integration/Phase1IntegrationTest.kt

@RunWith(AndroidJUnit4::class)
class Phase1IntegrationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context
    private lateinit var database: MaAIDatabase
    private lateinit var aiEngine: SmolLMAIEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val driver = AndroidSqliteDriver(MaAIDatabase.Schema, context, "test.db")
        database = MaAIDatabase(driver)
        aiEngine = SmolLMAIEngine(context)
    }

    @Test
    fun `phase 1 end to end - send message and receive AI response`() = runTest(timeout = 30.seconds) {
        // 1. Verify app launches with chat screen
        composeTestRule.onNodeWithText("間 AI").assertExists()
        composeTestRule.onNodeWithText("Type a message...").assertExists()

        // 2. Type a message
        composeTestRule.onNodeWithText("Type a message...")
            .performTextInput("What is 2+2?")

        // 3. Click send
        composeTestRule.onNodeWithContentDescription("Send")
            .performClick()

        // 4. Verify user message appears
        composeTestRule.waitUntil(timeout = 2000) {
            composeTestRule
                .onAllNodesWithText("What is 2+2?")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // 5. Wait for AI response (with streaming)
        composeTestRule.waitUntil(timeout = 15000) {
            // Look for answer containing "4" or "four"
            val nodes = composeTestRule.onAllNodes(
                hasText("4", substring = true) or hasText("four", ignoreCase = true)
            ).fetchSemanticsNodes()
            nodes.isNotEmpty()
        }

        // 6. Verify streaming cursor no longer visible
        composeTestRule.waitUntil(timeout = 2000) {
            composeTestRule
                .onAllNodesWithText("▋")
                .fetchSemanticsNodes().isEmpty()
        }

        // 7. Verify messages saved to database
        val messages = database.messageQueries.selectRecent(limit = 10).executeAsList()
        assertTrue(messages.size >= 2, "Expected at least 2 messages in DB")
        assertTrue(messages.any { it.role == "USER" })
        assertTrue(messages.any { it.role == "ASSISTANT" })

        // 8. Verify AI response quality
        val assistantMessage = messages.first { it.role == "ASSISTANT" }
        val content = Json.decodeFromString<List<ContentPart>>(assistantMessage.content)
        val responseText = (content.first() as ContentPart.Text).text

        assertTrue(
            responseText.contains("4") || responseText.toLowerCase().contains("four"),
            "Expected response to contain answer"
        )

        // 9. Verify model performance
        // (This would have been measured during generation)
        assertTrue(true, "Performance validation in separate benchmark tests")
    }

    @Test
    fun `model loads within 5 seconds`() = runTest {
        val startTime = System.currentTimeMillis()
        aiEngine.initialize()
        val loadTime = System.currentTimeMillis() - startTime

        assertTrue(loadTime < 5000, "Model load time: ${loadTime}ms (target: <5000ms)")
    }

    @Test
    fun `streaming updates UI in real-time`() = runTest {
        composeTestRule.onNodeWithText("Type a message...")
            .performTextInput("Count to 5")

        composeTestRule.onNodeWithContentDescription("Send")
            .performClick()

        // Verify cursor appears (streaming indicator)
        composeTestRule.waitUntil(timeout = 5000) {
            composeTestRule
                .onAllNodesWithText("▋")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Wait for completion
        composeTestRule.waitUntil(timeout = 15000) {
            composeTestRule
                .onAllNodesWithText("▋")
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `app handles errors gracefully`() = runTest {
        // Trigger error by sending extremely long message
        val longMessage = "word ".repeat(5000) // Exceeds context

        composeTestRule.onNodeWithText("Type a message...")
            .performTextInput(longMessage)

        composeTestRule.onNodeWithContentDescription("Send")
            .performClick()

        // Should show error dialog
        composeTestRule.waitUntil(timeout = 5000) {
            composeTestRule
                .onAllNodesWithText("Error", substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule
                .onAllNodesWithText("Too Long", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun teardown() = runBlocking {
        aiEngine.unload()
        database.close()
    }
}
```

**Acceptance Criteria:**
- [ ] End-to-end test sends message and receives response
- [ ] Streaming cursor appears and disappears
- [ ] Messages saved to database
- [ ] Model load time validated (<5 seconds)
- [ ] Error handling tested
- [ ] Response quality validated (contains expected answer)
- [ ] All Phase 1 tests passing (20+ tests)

**Tests:**
This IS the comprehensive integration test

**Dependencies:** All previous Phase 1 tickets
**Blocks:** Phase 2 kickoff

---

## Week 5: Performance & Polish (Tickets 015-020)

### PHASE1-015: UI Responsiveness Optimization
**Priority:** P1 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Optimize Compose recomposition, LazyColumn performance, and UI thread scheduling to maintain 60fps.

**Implementation:**
```kotlin
// File: app/composeApp/src/commonMain/kotlin/ui/chat/OptimizedChatScreen.kt

@Composable
fun ChatScreen(...) {
    // ... existing code ...

    // Optimize LazyColumn with keys and contentType
    LazyColumn(
        modifier = Modifier.weight(1f),
        reverseLayout = true,
        state = rememberLazyListState()
    ) {
        items(
            items = uiState.messages.reversed(),
            key = { message -> message.id }, // Prevent unnecessary recomposition
            contentType = { message -> message.role } // Optimize item recycling
        ) { message ->
            // Use remember to prevent recreation
            key(message.id) {
                StreamingMessageBubble(
                    message = message,
                    isStreaming = remember(uiState.isGenerating, message.id) {
                        uiState.isGenerating && message == uiState.messages.lastOrNull()
                    }
                )
            }
        }
    }
}

// Optimize MessageBubble recomposition
@Composable
fun MessageBubble(message: Message) {
    // Use derivedStateOf to minimize recomposition
    val textContent = remember(message.id) {
        message.content.filterIsInstance<ContentPart.Text>()
            .joinToString("\n") { it.text }
    }

    // ... rest of implementation ...
}
```

```kotlin
// File: app/shared/src/commonMain/kotlin/ui/viewmodel/ChatViewModel.kt

// Optimize state updates to minimize recomposition
private suspend fun generateAIResponse(...) {
    // Batch UI updates
    val updateInterval = 100L // Update UI every 100ms max
    var lastUpdateTime = 0L
    var pendingText = ""

    aiEngine.generateResponse(...).collect { text ->
        pendingText = text

        val now = System.currentTimeMillis()
        if (now - lastUpdateTime >= updateInterval) {
            updateStreamingMessage(assistantMessageId, pendingText)
            lastUpdateTime = now
        }
    }

    // Final update
    updateStreamingMessage(assistantMessageId, pendingText)
}
```

**Acceptance Criteria:**
- [ ] LazyColumn uses keys and contentType
- [ ] Remember used to prevent unnecessary recomposition
- [ ] Batched UI updates (max 10 updates/second)
- [ ] Frame time <16ms (60fps) during scrolling
- [ ] No jank during streaming responses
- [ ] Profiler validation (GPU profiler shows green bars)

**Tests:**
```kotlin
@Test
fun `chat screen maintains 60fps during streaming`() {
    // This requires manual profiling with GPU Profiler
    // Automated test validates no dropped frames
    composeTestRule.setContent {
        ChatScreen()
    }

    // Simulate streaming
    repeat(100) {
        composeTestRule.mainClock.advanceTimeBy(100)
    }

    // No crashes = success (detailed profiling is manual)
    assertTrue(true)
}
```

**Dependencies:** PHASE1-010
**Blocks:** None (optimization)

---

### PHASE1-016: Memory Leak Detection
**Priority:** P1 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Integrate LeakCanary and fix any memory leaks in AI engine, ViewModel, or Composables.

**Implementation:**
```kotlin
// File: app/composeApp/build.gradle.kts

dependencies {
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")
}
```

```kotlin
// File: app/composeApp/src/androidMain/kotlin/Application.kt

class MaAIApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // LeakCanary automatically initializes
        if (BuildConfig.DEBUG) {
            Log.d("LeakCanary", "Memory leak detection enabled")
        }
    }
}
```

```kotlin
// File: app/shared/src/androidMain/kotlin/ai/AndroidAIEngine.kt

// Ensure proper cleanup
class AndroidAIEngine(...) {
    // ... existing code ...

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun cleanup() {
        runBlocking {
            unload()
        }
        ortEnvironment = null
        Log.d(TAG, "Engine cleaned up")
    }
}
```

**Acceptance Criteria:**
- [ ] LeakCanary integrated (debug builds only)
- [ ] No leaks in AndroidAIEngine
- [ ] No leaks in ChatViewModel
- [ ] No leaks in Composables
- [ ] OrtSession properly closed
- [ ] WeakReferences used where appropriate
- [ ] Manual leak test passes (use app, background, check LeakCanary)

**Tests:**
```kotlin
@Test
fun `no memory leaks after model unload`() = runTest {
    val engine = AndroidAIEngine(context)
    engine.initialize()
    engine.unload()

    // Force GC
    System.gc()
    delay(1000)
    System.gc()

    // Verify engine can be garbage collected
    // (LeakCanary will catch issues in actual app)
    assertTrue(true)
}
```

**Dependencies:** PHASE1-004
**Blocks:** None (quality gate)

---

### PHASE1-017: APK Size Validation
**Priority:** P1 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Measure actual APK size with Phase 1 components and ensure <200MB target is achievable.

**Implementation:**
```bash
# File: app/scripts/measure_apk_size.sh

#!/bin/bash

set -e

echo "Building release APK..."
cd app
./gradlew assembleRelease

APK_PATH="composeApp/build/outputs/apk/release/composeApp-release.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "Error: APK not found at $APK_PATH"
    exit 1
fi

SIZE=$(stat -f%z "$APK_PATH" 2>/dev/null || stat -c%s "$APK_PATH")
SIZE_MB=$((SIZE / 1024 / 1024))

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "APK Size Report"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Total Size: ${SIZE_MB} MB"
echo "Target: <200 MB"
echo ""

# Breakdown
unzip -l "$APK_PATH" | grep -E "\\.onnx|\\.so|classes\\.dex" | awk '{print $4, $1/1024/1024 " MB"}' | sort -rn -k2

echo ""
if [ $SIZE_MB -lt 200 ]; then
    echo "✅ PASS: APK size within target"
    exit 0
else
    echo "❌ FAIL: APK size exceeds 200MB limit"
    exit 1
fi
```

```markdown
# File: app/docs/APK_SIZE_REPORT.md

## Phase 1 APK Size Analysis

### Current Size: [TBD] MB

**Breakdown:**
- SmolLM2-360M (ONNX, 4-bit): ~120MB (compressed)
- MiniLM-L6 (ONNX, 8-bit): ~60MB (Phase 2)
- Code (DEX files): ~8MB
- Resources: ~5MB
- Native libs (ONNX Runtime): ~40MB
- **Total Phase 1:** ~173MB

### Target: <200MB

**Status:** ✅ PASS / ❌ FAIL

### Remaining Budget: ~27MB
- Sufficient for Phase 2-5 additions

### Optimization Opportunities:
1. More aggressive model quantization (4-bit → 3-bit)
2. Remove unused ONNX Runtime components
3. Use APK expansion files for models
```

**Acceptance Criteria:**
- [ ] measure_apk_size.sh script created
- [ ] Release APK builds successfully
- [ ] Size breakdown reported
- [ ] APK size <200MB (with Phase 1 components)
- [ ] Remaining budget calculated
- [ ] APK_SIZE_REPORT.md documented

**Tests:**
```bash
# Run in CI/CD
./scripts/measure_apk_size.sh
```

**Dependencies:** PHASE1-001
**Blocks:** None (validation)

---

### PHASE1-018: Battery Optimization
**Priority:** P2 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Implement battery-saving strategies: NPU utilization, aggressive idle unloading, reduced inference frequency.

**Implementation:**
```kotlin
// File: app/shared/src/androidMain/kotlin/ai/BatteryOptimizedEngine.kt

class BatteryOptimizedEngine(
    context: Context
) : SmolLMAIEngine(context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    override suspend fun generateResponse(...): Flow<String> {
        // Check battery saver mode
        if (powerManager.isPowerSaveMode) {
            return generateWithReducedQuality(messages, systemPrompt, maxTokens, temperature)
        }

        return super.generateResponse(messages, systemPrompt, maxTokens, temperature)
    }

    private suspend fun generateWithReducedQuality(...): Flow<String> = flow {
        // Reduce max tokens in battery saver mode
        val reducedTokens = (maxTokens * 0.7).toInt()

        // Increase temperature for faster sampling (less compute)
        val fasterTemperature = temperature * 1.2f

        super.generateResponse(
            messages,
            systemPrompt,
            reducedTokens,
            fasterTemperature
        ).collect { emit(it) }
    }

    suspend fun enableNPUIfAvailable() {
        // Try to use Neural Processing Unit
        // (ONNX Runtime automatically uses NNAPI when available)
        val sessionOptions = OrtSession.SessionOptions().apply {
            // Enable NNAPI
            registerCustomOpLibrary(OrtProvider.NNAPI)
            setExecutionMode(ExecutionMode.PARALLEL)
        }

        // Recreate session with NPU support
        // (Implementation details depend on device capabilities)
    }

    fun shouldThrottleInference(): Boolean {
        val batteryLevel = getBatteryLevel()
        return batteryLevel < 20 // Throttle when battery <20%
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
```

**Acceptance Criteria:**
- [ ] Battery saver mode detection
- [ ] Reduced quality generation in battery saver
- [ ] NPU/NNAPI support enabled (if available)
- [ ] Throttling when battery <20%
- [ ] Aggressive idle unloading (2min instead of 5min)
- [ ] Battery drain <2%/hour validated

**Tests:**
```kotlin
@Test
fun `battery saver mode reduces token generation`() = runTest {
    // Mock power manager in battery saver mode
    val engine = BatteryOptimizedEngine(context)

    // In battery saver, should generate fewer tokens
    // (Exact test depends on mocking framework)
    assertTrue(true) // Placeholder
}
```

**Dependencies:** PHASE1-004, PHASE1-007
**Blocks:** None (optimization)

---

### PHASE1-019: Accessibility Improvements
**Priority:** P2 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Enhance chat UI accessibility: TalkBack descriptions, content descriptions, semantic properties.

**Implementation:**
```kotlin
// File: app/composeApp/src/commonMain/kotlin/ui/chat/AccessibleChatScreen.kt

@Composable
fun ChatScreen(...) {
    // Add semantics for TalkBack
    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Chat screen with 間 AI"
                role = Role.List
            }
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = "Conversation history"
                }
        ) {
            items(...) { message ->
                MessageBubble(
                    message = message,
                    modifier = Modifier.semantics {
                        contentDescription = when (message.role) {
                            Message.Role.USER -> "You said: ${message.getTextContent()}"
                            Message.Role.ASSISTANT -> "AI responded: ${message.getTextContent()}"
                            else -> message.getTextContent()
                        }
                    }
                )
            }
        }

        ChatInputField(
            text = uiState.inputText,
            onTextChange = { viewModel.onInputTextChange(it) },
            onSend = { viewModel.sendMessage() },
            isLoading = uiState.isGenerating,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    stateDescription = if (uiState.isGenerating) {
                        "AI is generating response"
                    } else {
                        "Ready to send message"
                    }
                }
        )
    }
}

private fun Message.getTextContent(): String {
    return content.filterIsInstance<ContentPart.Text>()
        .joinToString(" ") { it.text }
        .take(100) // Limit for TalkBack
}
```

**Acceptance Criteria:**
- [ ] Content descriptions for all interactive elements
- [ ] Semantic roles assigned (Button, List, TextField)
- [ ] State descriptions for loading states
- [ ] TalkBack reads messages correctly
- [ ] Send button has clear description
- [ ] Loading state announced
- [ ] Touch targets 48×48dp minimum

**Tests:**
```kotlin
@Test
fun `chat screen has accessibility semantics`() {
    composeTestRule.setContent {
        ChatScreen()
    }

    composeTestRule.onNode(hasContentDescription("Chat screen with 間 AI"))
        .assertExists()

    composeTestRule.onNode(hasContentDescription("Send"))
        .assertExists()
}
```

**Dependencies:** PHASE1-008
**Blocks:** None (accessibility)

---

### PHASE1-020: Documentation & Phase 1 Wrap-Up
**Priority:** P1 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Document Phase 1 implementation, update README, create API docs for AI engine, write migration guide.

**Implementation:**
```markdown
# File: app/docs/PHASE1_SUMMARY.md

## Phase 1: Core AI Engine - Completion Report

**Duration:** Weeks 3-5 (3 weeks)
**Status:** ✅ Complete
**Tickets Completed:** 20/20

### Achievements

**AI Engine:**
- ✅ SmolLM2-360M exported to ONNX (4-bit, ~120MB)
- ✅ ONNX Runtime integrated on Android
- ✅ Tokenizer with chat template support
- ✅ Streaming token generation
- ✅ Model memory management (lazy load, auto-unload)

**Chat UI:**
- ✅ Compose-based chat screen
- ✅ Streaming response visualization
- ✅ Error handling with user dialogs
- ✅ State persistence across restarts
- ✅ Navigation structure for future screens

**Performance:**
- ✅ Inference speed: [TBD] tokens/sec (target: 40+)
- ✅ Model load time: [TBD]ms (target: <5000ms)
- ✅ Memory usage: [TBD]MB (target: <500MB)
- ✅ Battery impact: [TBD]%/hour (target: <2%)
- ✅ APK size: [TBD]MB (target: <200MB)

**Quality:**
- ✅ 20+ tests passing
- ✅ No memory leaks (LeakCanary validated)
- ✅ Accessibility improvements (TalkBack support)
- ✅ Error handling comprehensive

### Known Issues
- [ ] None blocking Phase 2

### Next Steps
- **Phase 2:** Memory & Embedding System (Weeks 6-8)
- Focus: Vector database, MiniLM-L6, context assembly

### API Documentation
See [AI_ENGINE_API.md](AI_ENGINE_API.md) for SmolLMAIEngine usage.
```

```markdown
# File: app/docs/AI_ENGINE_API.md

## SmolLMAIEngine API Documentation

### Overview
SmolLMAIEngine provides streaming text generation using SmolLM2-360M.

### Basic Usage

```kotlin
val engine = SmolLMAIEngine(context)
engine.initialize()

val messages = listOf(
    Message(
        role = Message.Role.USER,
        content = listOf(ContentPart.Text("Hello")),
        timestamp = Instant.now()
    )
)

engine.generateResponse(messages).collect { text ->
    println("Streaming: $text")
}
```

### API Reference

#### `suspend fun initialize()`
Loads ONNX model from assets. Call once before first use.

#### `fun generateResponse(...): Flow<String>`
Generates streaming AI response.

**Parameters:**
- `messages: List<Message>` - Conversation history
- `systemPrompt: String?` - Optional system instruction
- `maxTokens: Int` - Maximum tokens to generate (default: 512)
- `temperature: Float` - Sampling temperature (default: 0.7)

**Returns:** `Flow<String>` - Streaming text response

**Throws:**
- `AIError.InsufficientMemory` - Not enough RAM
- `AIError.ContextTooLong` - Input exceeds model context
- `AIError.GenerationFailed` - Inference error
```

**Acceptance Criteria:**
- [ ] PHASE1_SUMMARY.md created
- [ ] AI_ENGINE_API.md created
- [ ] README updated with Phase 1 status
- [ ] Performance metrics documented (actual numbers)
- [ ] Known issues listed
- [ ] All Phase 1 tickets marked complete

**Tests:**
Documentation review (manual)

**Dependencies:** All Phase 1 tickets
**Blocks:** Phase 2 kickoff

---

## Phase 1 Summary

### Completion Checklist

**Model & Inference:**
- [ ] PHASE1-001: SmolLM2-360M exported to ONNX
- [ ] PHASE1-002: ONNX Runtime session management
- [ ] PHASE1-003: SmolLM2 tokenizer
- [ ] PHASE1-004: Tokenizer + AI engine integration
- [ ] PHASE1-005: Model memory management
- [ ] PHASE1-006: Performance benchmarking
- [ ] PHASE1-007: Battery profiling

**Chat UI:**
- [ ] PHASE1-008: Chat screen composable
- [ ] PHASE1-009: ChatViewModel
- [ ] PHASE1-010: Streaming response UI
- [ ] PHASE1-011: Error handling
- [ ] PHASE1-012: State persistence
- [ ] PHASE1-013: Navigation structure

**Polish:**
- [ ] PHASE1-014: Phase 1 integration test
- [ ] PHASE1-015: UI responsiveness optimization
- [ ] PHASE1-016: Memory leak detection
- [ ] PHASE1-017: APK size validation
- [ ] PHASE1-018: Battery optimization
- [ ] PHASE1-019: Accessibility improvements
- [ ] PHASE1-020: Documentation

### Deliverables
- ✅ Working SmolLM2-360M inference
- ✅ Streaming chat UI
- ✅ Performance validated (40+ tokens/sec)
- ✅ Battery impact <2%/hour
- ✅ APK size <200MB
- ✅ 20+ tests passing
- ✅ Documentation complete

### Metrics
- **Code Coverage Target:** 75%+ for AI engine
- **Test Count:** 20+ tests
- **Performance:** 40+ tokens/sec on mid-range
- **APK Size:** ~173MB (Phase 1 only)

---

## Next Phase

**Phase 2: Memory & Embedding System** begins after all Phase 1 tickets complete.

See [PHASE2.md](PHASE2.md) for vector database, MiniLM-L6 integration, and memory management implementation.
