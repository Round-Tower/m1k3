# Phase 3: Knowledge Systems (Weeks 9-10)

## Overview

**Duration:** 2 weeks (Weeks 9-10)
**Total Tickets:** 15 tickets
**Dependencies:** Phase 2 complete (memory & embedding system functional)

### Goals
- Integrate M1K3's comprehensive knowledge base (1,341+ documents)
- Implement trivia engine with semantic search
- Build device intelligence system (OEM profiles, SoC detection)
- Create response enrichment with contextual facts
- Develop RAG (Retrieval-Augmented Generation) integration

### Success Criteria
- [ ] Trivia engine retrieves relevant facts (>70% relevance score)
- [ ] Device intelligence provides accurate system info
- [ ] AI responses enriched with knowledge base context
- [ ] RAG integration functional with semantic search
- [ ] Knowledge retrieval <100ms @ 1,341 documents
- [ ] All 15 tests passing

---

## Week 9: Trivia Engine & Device Intelligence (Tickets 001-008)

### PHASE3-001: Trivia Engine Architecture ⚠️ CRITICAL

**Priority:** P0 | **Estimated Hours:** 4h | **Status:** [ ]

**Description:**
Design and implement trivia engine that interfaces with M1K3 knowledge base using semantic search.

**Implementation:**
File: `app/shared/src/commonMain/kotlin/ai/ma/knowledge/TriviaEngine.kt`

```kotlin
/**
 * Trivia Engine - Semantic knowledge retrieval from M1K3 comprehensive_knowledge_base.json
 *
 * Retrieves contextually relevant facts from 1,341+ documents across 20 categories.
 */
class TriviaEngine(
    private val triviaRepository: TriviaRepository,
    private val embeddingEngine: CachedEmbeddingEngine,
    private val vectorIndex: HNSWVectorIndex
) {
    /**
     * Retrieve trivia facts relevant to query
     *
     * @param query User query or conversation context
     * @param categories Optional filter by categories (e.g., ["science", "history"])
     * @param topK Number of facts to retrieve (default: 3)
     * @return List of relevant trivia facts with relevance scores
     */
    suspend fun retrieveTrivia(
        query: String,
        categories: List<String>? = null,
        topK: Int = 3
    ): List<TriviaResult> {
        // 1. Embed query
        val queryEmbedding = embeddingEngine.embed(query)

        // 2. Search vector index
        val candidates = vectorIndex.search(
            query = queryEmbedding,
            k = topK * 2, // Over-retrieve for filtering
            filter = categories?.let { CategoryFilter(it) }
        )

        // 3. Load full facts from repository
        val facts = triviaRepository.getFactsByIds(
            candidates.map { it.id }
        )

        // 4. Re-rank by relevance
        val results = facts.zip(candidates).map { (fact, candidate) ->
            TriviaResult(
                fact = fact,
                relevanceScore = candidate.score,
                category = fact.category
            )
        }.sortedByDescending { it.relevanceScore }

        return results.take(topK)
    }

    /**
     * Get random fact from category
     */
    suspend fun getRandomFact(category: String? = null): TriviaFact? {
        return triviaRepository.getRandomFact(category)
    }
}

data class TriviaResult(
    val fact: TriviaFact,
    val relevanceScore: Float,
    val category: String
)
```

**Acceptance Criteria:**
- [ ] TriviaEngine class implemented with semantic search
- [ ] Supports category filtering
- [ ] Returns top-K relevant facts with scores
- [ ] Integrates with HNSW vector index
- [ ] Random fact retrieval implemented

**Tests:**
- [ ] `TriviaEngineTest.kt`: `@Test fun retrieveTrivia_returnsRelevantFacts()`
- [ ] `TriviaEngineTest.kt`: `@Test fun retrieveTrivia_withCategoryFilter()`
- [ ] `TriviaEngineTest.kt`: `@Test fun retrieveTrivia_reranksResults()`
- [ ] `TriviaEngineTest.kt`: `@Test fun getRandomFact_returnsValidFact()`

**Dependencies:**
- PHASE2-005 (HNSW vector index)
- PHASE0-012 (M1K3 knowledge base imported)

**Blocks:** PHASE3-003 (Response enrichment)

---

### PHASE3-002: Trivia Repository Implementation

**Priority:** P1 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Implement repository for accessing trivia facts from SQLDelight TriviaFact table.

**Implementation:**
File: `app/shared/src/commonMain/kotlin/ai/ma/data/repository/TriviaRepository.kt`

```kotlin
interface TriviaRepository {
    suspend fun getFactsByIds(ids: List<String>): List<TriviaFact>
    suspend fun getFactsByCategory(category: String): List<TriviaFact>
    suspend fun getRandomFact(category: String? = null): TriviaFact?
    suspend fun getAllCategories(): List<String>
    suspend fun getFactCount(): Long
}

class TriviaRepositoryImpl(
    private val database: Database
) : TriviaRepository {
    override suspend fun getFactsByIds(ids: List<String>): List<TriviaFact> {
        return database.triviaFactQueries.getByIds(ids).executeAsList()
    }

    override suspend fun getFactsByCategory(category: String): List<TriviaFact> {
        return database.triviaFactQueries.getByCategory(category).executeAsList()
    }

    override suspend fun getRandomFact(category: String?): TriviaFact? {
        return if (category != null) {
            database.triviaFactQueries.getRandomByCategory(category).executeAsOneOrNull()
        } else {
            database.triviaFactQueries.getRandom().executeAsOneOrNull()
        }
    }

    override suspend fun getAllCategories(): List<String> {
        return database.triviaFactQueries.getCategories().executeAsList()
    }

    override suspend fun getFactCount(): Long {
        return database.triviaFactQueries.count().executeAsOne()
    }
}
```

File: `app/shared/src/commonMain/sqldelight/ai/ma/db/TriviaFact.sq`

```sql
-- Additional queries for Phase 3
getByIds:
SELECT * FROM TriviaFact WHERE id IN ?;

getByCategory:
SELECT * FROM TriviaFact WHERE category = ?;

getRandomByCategory:
SELECT * FROM TriviaFact WHERE category = ? ORDER BY RANDOM() LIMIT 1;

getRandom:
SELECT * FROM TriviaFact ORDER BY RANDOM() LIMIT 1;

getCategories:
SELECT DISTINCT category FROM TriviaFact ORDER BY category;

count:
SELECT COUNT(*) FROM TriviaFact;
```

**Acceptance Criteria:**
- [ ] TriviaRepository interface defined
- [ ] TriviaRepositoryImpl implemented
- [ ] SQLDelight queries added to TriviaFact.sq
- [ ] Batch retrieval by IDs supported
- [ ] Random fact retrieval works

**Tests:**
- [ ] `TriviaRepositoryTest.kt`: `@Test fun getFactsByIds_returnsBatch()`
- [ ] `TriviaRepositoryTest.kt`: `@Test fun getRandomFact_notNull()`
- [ ] `TriviaRepositoryTest.kt`: `@Test fun getAllCategories_returns20()`

**Dependencies:** PHASE0-011 (TriviaFact table schema)

**Blocks:** PHASE3-001 (Trivia engine)

---

### PHASE3-003: Response Enrichment with Trivia

**Priority:** P1 | **Estimated Hours:** 5h | **Status:** [ ]

**Description:**
Integrate trivia engine with AI response generation to enrich answers with contextual facts.

**Implementation:**
File: `app/shared/src/commonMain/kotlin/ai/ma/ai/ResponseEnricher.kt`

```kotlin
/**
 * Response Enrichment - Adds contextual knowledge to AI responses
 *
 * Analyzes user queries and AI responses to inject relevant trivia facts
 * at natural points in the conversation.
 */
class ResponseEnricher(
    private val triviaEngine: TriviaEngine,
    private val sentimentAnalyzer: SentimentAnalyzer? = null
) {
    /**
     * Enrich AI response with relevant trivia
     *
     * @param userQuery Original user query
     * @param aiResponse Generated AI response
     * @param context Conversation context
     * @return Enriched response with trivia injected naturally
     */
    suspend fun enrichResponse(
        userQuery: String,
        aiResponse: String,
        context: ConversationContext
    ): EnrichedResponse {
        // 1. Detect if trivia would enhance response
        if (!shouldEnrich(userQuery, aiResponse, context)) {
            return EnrichedResponse(
                text = aiResponse,
                triviaUsed = emptyList(),
                enrichmentApplied = false
            )
        }

        // 2. Extract key topics from query
        val topics = extractTopics(userQuery)

        // 3. Retrieve relevant trivia
        val trivia = triviaEngine.retrieveTrivia(
            query = userQuery,
            topK = 2
        ).filter { it.relevanceScore > 0.7f }

        if (trivia.isEmpty()) {
            return EnrichedResponse(
                text = aiResponse,
                triviaUsed = emptyList(),
                enrichmentApplied = false
            )
        }

        // 4. Inject trivia naturally
        val enrichedText = injectTrivia(aiResponse, trivia)

        return EnrichedResponse(
            text = enrichedText,
            triviaUsed = trivia,
            enrichmentApplied = true
        )
    }

    private fun shouldEnrich(
        query: String,
        response: String,
        context: ConversationContext
    ): Boolean {
        // Don't enrich if:
        // - Response already long (>300 words)
        // - User asked for brevity
        // - Recent enrichment (< 3 messages ago)
        val wordCount = response.split(" ").size
        return wordCount < 300 &&
               !query.contains("briefly", ignoreCase = true) &&
               context.messagesSinceLastEnrichment >= 3
    }

    private fun extractTopics(query: String): List<String> {
        // Simple keyword extraction
        val stopWords = setOf("the", "is", "at", "which", "on", "a", "an")
        return query.lowercase()
            .split(" ")
            .filter { it !in stopWords && it.length > 3 }
    }

    private fun injectTrivia(
        response: String,
        trivia: List<TriviaResult>
    ): String {
        // Find natural injection point (after first sentence)
        val sentences = response.split(". ")
        if (sentences.size < 2) {
            return response // Too short to inject
        }

        val firstSentence = sentences[0]
        val rest = sentences.drop(1).joinToString(". ")

        val triviaText = trivia.joinToString("\n\n") { result ->
            "**Interesting fact:** ${result.fact.content}"
        }

        return "$firstSentence.\n\n$triviaText\n\n$rest"
    }
}

data class EnrichedResponse(
    val text: String,
    val triviaUsed: List<TriviaResult>,
    val enrichmentApplied: Boolean
)

data class ConversationContext(
    val messagesSinceLastEnrichment: Int = 0,
    val userPreferences: UserPreferences = UserPreferences()
)

data class UserPreferences(
    val prefersVerbose: Boolean = true,
    val triviaEnabled: Boolean = true
)
```

**Acceptance Criteria:**
- [ ] ResponseEnricher class implemented
- [ ] Detects appropriate moments for enrichment
- [ ] Injects trivia naturally into responses
- [ ] Respects user preferences (brevity, frequency)
- [ ] Tracks enrichment frequency

**Tests:**
- [ ] `ResponseEnricherTest.kt`: `@Test fun enrichResponse_injectsTrivia()`
- [ ] `ResponseEnricherTest.kt`: `@Test fun enrichResponse_skipsIfTooRecent()`
- [ ] `ResponseEnricherTest.kt`: `@Test fun enrichResponse_respectsBrevityRequest()`
- [ ] `ResponseEnricherTest.kt`: `@Test fun enrichResponse_skipsLowRelevance()`

**Dependencies:** PHASE3-001 (Trivia engine)

**Blocks:** PHASE3-007 (Integrate with ChatViewModel)

---

### PHASE3-004: Device Intelligence - OEM Profiles ⚠️ CRITICAL

**Priority:** P0 | **Estimated Hours:** 4h | **Status:** [ ]

**Description:**
Create device intelligence system that detects device manufacturer and provides OEM-specific information.

**Implementation:**
File: `app/composeApp/src/androidMain/kotlin/ai/ma/device/DeviceIntelligence.kt`

```kotlin
/**
 * Device Intelligence - Detects and provides device-specific information
 *
 * Understands device manufacturer, model, SoC, RAM, and provides
 * contextual device knowledge for AI conversations.
 */
class DeviceIntelligence(
    private val context: Context
) {
    private val oemProfiles = loadOEMProfiles()

    /**
     * Get complete device profile
     */
    fun getDeviceProfile(): DeviceProfile {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val soc = detectSoC()
        val ram = detectRAM()
        val androidVersion = Build.VERSION.RELEASE

        val oemProfile = oemProfiles[manufacturer.lowercase()] ?: OEMProfile.Generic

        return DeviceProfile(
            manufacturer = manufacturer,
            model = model,
            soc = soc,
            ram = ram,
            androidVersion = androidVersion,
            oemProfile = oemProfile,
            capabilities = detectCapabilities()
        )
    }

    /**
     * Detect System-on-Chip (SoC)
     */
    private fun detectSoC(): SoCInfo {
        val hardware = Build.HARDWARE
        val board = Build.BOARD
        val device = Build.DEVICE

        return when {
            hardware.contains("qcom", ignoreCase = true) ->
                SoCInfo("Qualcomm", extractSnapdragonModel(hardware))
            hardware.contains("exynos", ignoreCase = true) ->
                SoCInfo("Samsung", "Exynos ${extractExynosModel(hardware)}")
            hardware.contains("mediatek", ignoreCase = true) ->
                SoCInfo("MediaTek", extractDimensityModel(hardware))
            hardware.contains("kirin", ignoreCase = true) ->
                SoCInfo("Huawei", "Kirin ${extractKirinModel(hardware)}")
            board.contains("ranchu", ignoreCase = true) ->
                SoCInfo("Generic", "Emulator")
            else -> SoCInfo("Unknown", hardware)
        }
    }

    /**
     * Detect device RAM
     */
    private fun detectRAM(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
            as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem
    }

    /**
     * Detect device capabilities
     */
    private fun detectCapabilities(): DeviceCapabilities {
        val packageManager = context.packageManager

        return DeviceCapabilities(
            hasNPU = detectNPU(),
            hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
            hasFingerprintSensor = packageManager.hasSystemFeature(
                PackageManager.FEATURE_FINGERPRINT
            ),
            hasFaceUnlock = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                packageManager.hasSystemFeature(PackageManager.FEATURE_FACE),
            supportsVulkan = packageManager.hasSystemFeature(
                PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, 1
            )
        )
    }

    /**
     * Detect Neural Processing Unit (NPU)
     */
    private fun detectNPU(): Boolean {
        return try {
            val nnapi = context.getSystemService("neuralnetworks") != null
            nnapi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        } catch (e: Exception) {
            false
        }
    }

    private fun extractSnapdragonModel(hardware: String): String {
        // Extract model number from hardware string
        val regex = Regex("(\\d{3,4})")
        return regex.find(hardware)?.value ?: "Unknown"
    }

    private fun extractExynosModel(hardware: String): String {
        val regex = Regex("(\\d{4})")
        return regex.find(hardware)?.value ?: "Unknown"
    }

    private fun extractDimensityModel(hardware: String): String {
        val regex = Regex("(\\d{4})")
        return regex.find(hardware)?.value?.let { "Dimensity $it" } ?: "Unknown"
    }

    private fun extractKirinModel(hardware: String): String {
        val regex = Regex("(\\d{3,4})")
        return regex.find(hardware)?.value ?: "Unknown"
    }

    private fun loadOEMProfiles(): Map<String, OEMProfile> {
        return mapOf(
            "samsung" to OEMProfile(
                name = "Samsung",
                characteristics = "Known for One UI, excellent displays, Knox security",
                commonIssues = "Bloatware, slower updates on budget devices"
            ),
            "google" to OEMProfile(
                name = "Google",
                characteristics = "Stock Android, fastest updates, Tensor AI features",
                commonIssues = "Limited hardware customization"
            ),
            "xiaomi" to OEMProfile(
                name = "Xiaomi",
                characteristics = "MIUI customization, good value, fast charging",
                commonIssues = "Aggressive memory management, ads in system apps"
            ),
            "oneplus" to OEMProfile(
                name = "OnePlus",
                characteristics = "OxygenOS, fast performance, clean UI",
                commonIssues = "ColorOS merger controversies"
            ),
            "oppo" to OEMProfile(
                name = "OPPO",
                characteristics = "ColorOS, camera innovation, VOOC charging",
                commonIssues = "Heavy skin, bloatware"
            ),
            "vivo" to OEMProfile(
                name = "Vivo",
                characteristics = "Funtouch OS, camera features, innovative designs",
                commonIssues = "Software complexity"
            ),
            "motorola" to OEMProfile(
                name = "Motorola",
                characteristics = "Near-stock Android, clean experience",
                commonIssues = "Slower updates, budget hardware"
            ),
            "nothing" to OEMProfile(
                name = "Nothing",
                characteristics = "Nothing OS, transparency, unique design",
                commonIssues = "New brand, limited ecosystem"
            )
        )
    }
}

data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val soc: SoCInfo,
    val ram: Long,
    val androidVersion: String,
    val oemProfile: OEMProfile,
    val capabilities: DeviceCapabilities
)

data class SoCInfo(
    val vendor: String,
    val model: String
)

data class OEMProfile(
    val name: String,
    val characteristics: String,
    val commonIssues: String
) {
    companion object {
        val Generic = OEMProfile(
            name = "Generic",
            characteristics = "Standard Android device",
            commonIssues = "N/A"
        )
    }
}

data class DeviceCapabilities(
    val hasNPU: Boolean,
    val hasCamera: Boolean,
    val hasFingerprintSensor: Boolean,
    val hasFaceUnlock: Boolean,
    val supportsVulkan: Boolean
)
```

**Acceptance Criteria:**
- [ ] DeviceIntelligence class implemented
- [ ] Detects manufacturer, model, SoC, RAM
- [ ] OEM profiles for 8+ manufacturers
- [ ] Capability detection (NPU, camera, biometrics)
- [ ] Works on physical devices and emulators

**Tests:**
- [ ] `DeviceIntelligenceTest.kt`: `@Test fun getDeviceProfile_returnsValidProfile()`
- [ ] `DeviceIntelligenceTest.kt`: `@Test fun detectSoC_identifiesQualcomm()`
- [ ] `DeviceIntelligenceTest.kt`: `@Test fun detectRAM_returnsPositiveValue()`
- [ ] `DeviceIntelligenceTest.kt`: `@Test fun detectCapabilities_checksNPU()`

**Dependencies:** None (platform APIs only)

**Blocks:** PHASE3-005 (Device knowledge integration)

---

### PHASE3-005: Device Knowledge Integration

**Priority:** P1 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Integrate device intelligence with AI responses to provide contextual device information.

**Implementation:**
File: `app/shared/src/commonMain/kotlin/ai/ma/ai/DeviceKnowledgeProvider.kt`

```kotlin
/**
 * Device Knowledge Provider - Provides device context to AI
 *
 * Exposes device information in natural language for AI responses.
 */
class DeviceKnowledgeProvider(
    private val deviceIntelligence: DeviceIntelligence
) {
    /**
     * Get device context summary for AI
     */
    fun getDeviceContext(): String {
        val profile = deviceIntelligence.getDeviceProfile()

        return buildString {
            appendLine("**Your Device:**")
            appendLine("- ${profile.manufacturer} ${profile.model}")
            appendLine("- ${profile.soc.vendor} ${profile.soc.model}")
            appendLine("- ${formatRAM(profile.ram)} RAM")
            appendLine("- Android ${profile.androidVersion}")

            if (profile.oemProfile != OEMProfile.Generic) {
                appendLine("\n**About ${profile.oemProfile.name}:**")
                appendLine(profile.oemProfile.characteristics)
            }

            if (profile.capabilities.hasNPU) {
                appendLine("\n**AI Acceleration:** Your device has an NPU (Neural Processing Unit)")
            }
        }
    }

    /**
     * Answer device-specific questions
     */
    fun answerDeviceQuestion(question: String): String? {
        val profile = deviceIntelligence.getDeviceProfile()
        val lowerQuestion = question.lowercase()

        return when {
            lowerQuestion.contains("processor") || lowerQuestion.contains("chip") -> {
                "Your device has a ${profile.soc.vendor} ${profile.soc.model} processor."
            }

            lowerQuestion.contains("ram") || lowerQuestion.contains("memory") -> {
                "Your device has ${formatRAM(profile.ram)} of RAM."
            }

            lowerQuestion.contains("android version") -> {
                "You're running Android ${profile.androidVersion}."
            }

            lowerQuestion.contains("manufacturer") || lowerQuestion.contains("brand") -> {
                "Your device is made by ${profile.manufacturer}."
            }

            lowerQuestion.contains("npu") || lowerQuestion.contains("ai acceleration") -> {
                if (profile.capabilities.hasNPU) {
                    "Yes! Your device has NPU (Neural Processing Unit) support for AI acceleration."
                } else {
                    "Your device doesn't have a dedicated NPU, but I run efficiently on your CPU."
                }
            }

            else -> null // Not a device question
        }
    }

    private fun formatRAM(bytes: Long): String {
        val gb = bytes / (1024 * 1024 * 1024)
        return "${gb}GB"
    }
}
```

**Acceptance Criteria:**
- [ ] DeviceKnowledgeProvider implemented
- [ ] Provides natural language device context
- [ ] Answers common device questions
- [ ] Formats technical specs readably

**Tests:**
- [ ] `DeviceKnowledgeProviderTest.kt`: `@Test fun getDeviceContext_formatsProperly()`
- [ ] `DeviceKnowledgeProviderTest.kt`: `@Test fun answerDeviceQuestion_processor()`
- [ ] `DeviceKnowledgeProviderTest.kt`: `@Test fun answerDeviceQuestion_ram()`
- [ ] `DeviceKnowledgeProviderTest.kt`: `@Test fun answerDeviceQuestion_unknownReturnsNull()`

**Dependencies:** PHASE3-004 (Device intelligence)

**Blocks:** PHASE3-007 (ChatViewModel integration)

---

### PHASE3-006: RAG (Retrieval-Augmented Generation) Architecture

**Priority:** P0 | **Estimated Hours:** 6h | **Status:** [ ]

**Description:**
Implement RAG system that retrieves relevant knowledge and enhances AI responses with authoritative information.

**Implementation:**
File: `app/shared/src/commonMain/kotlin/ai/ma/knowledge/RAGEngine.kt`

```kotlin
/**
 * RAG Engine - Retrieval-Augmented Generation
 *
 * Retrieves relevant documents from knowledge base and integrates
 * them into AI response generation for factual, grounded answers.
 */
class RAGEngine(
    private val triviaEngine: TriviaEngine,
    private val memoryManager: MemoryManager,
    private val deviceKnowledge: DeviceKnowledgeProvider,
    private val aiEngine: SmolLM2Engine
) {
    /**
     * Generate RAG-enhanced response
     *
     * @param query User query
     * @param conversationHistory Recent messages for context
     * @return AI response enhanced with retrieved knowledge
     */
    suspend fun generateResponse(
        query: String,
        conversationHistory: List<Message>,
        projectId: String? = null
    ): RAGResponse {
        // 1. Detect query intent
        val intent = detectIntent(query)

        // 2. Retrieve relevant knowledge
        val retrievedKnowledge = retrieveKnowledge(query, intent)

        // 3. Assemble context (knowledge + memory + device info)
        val context = assembleContext(
            query = query,
            knowledge = retrievedKnowledge,
            conversationHistory = conversationHistory,
            projectId = projectId
        )

        // 4. Generate AI response with context
        val aiResponse = aiEngine.generateWithContext(
            query = query,
            context = context
        )

        // 5. Post-process (citations, formatting)
        val finalResponse = postProcess(aiResponse, retrievedKnowledge)

        return RAGResponse(
            text = finalResponse,
            knowledgeSources = retrievedKnowledge,
            intent = intent,
            confidenceScore = calculateConfidence(retrievedKnowledge)
        )
    }

    private fun detectIntent(query: String): QueryIntent {
        val lower = query.lowercase()

        return when {
            lower.contains("my device") || lower.contains("this phone") ->
                QueryIntent.DEVICE_QUESTION

            lower.contains("how to") || lower.contains("explain") ->
                QueryIntent.EXPLANATION

            lower.contains("fact") || lower.contains("tell me about") ->
                QueryIntent.FACTUAL

            lower.contains("?") ->
                QueryIntent.QUESTION

            else ->
                QueryIntent.CONVERSATION
        }
    }

    private suspend fun retrieveKnowledge(
        query: String,
        intent: QueryIntent
    ): RetrievedKnowledge {
        return when (intent) {
            QueryIntent.DEVICE_QUESTION -> {
                val deviceInfo = deviceKnowledge.answerDeviceQuestion(query)
                RetrievedKnowledge(
                    trivia = emptyList(),
                    deviceInfo = deviceInfo,
                    memories = emptyList()
                )
            }

            QueryIntent.FACTUAL, QueryIntent.EXPLANATION -> {
                val trivia = triviaEngine.retrieveTrivia(
                    query = query,
                    topK = 3
                )
                RetrievedKnowledge(
                    trivia = trivia,
                    deviceInfo = null,
                    memories = emptyList()
                )
            }

            QueryIntent.QUESTION, QueryIntent.CONVERSATION -> {
                // Retrieve both trivia and memories
                val trivia = triviaEngine.retrieveTrivia(query, topK = 2)
                val memories = memoryManager.retrieveMemories(query, topK = 5)

                RetrievedKnowledge(
                    trivia = trivia,
                    deviceInfo = null,
                    memories = memories
                )
            }
        }
    }

    private suspend fun assembleContext(
        query: String,
        knowledge: RetrievedKnowledge,
        conversationHistory: List<Message>,
        projectId: String?
    ): String {
        val contextBuilder = StringBuilder()

        // Add system prompt
        contextBuilder.appendLine("You are 間 AI, a helpful privacy-focused assistant.")
        contextBuilder.appendLine()

        // Add retrieved knowledge
        if (knowledge.trivia.isNotEmpty()) {
            contextBuilder.appendLine("**Relevant Knowledge:**")
            knowledge.trivia.forEach { result ->
                contextBuilder.appendLine("- ${result.fact.content} (${result.category})")
            }
            contextBuilder.appendLine()
        }

        if (knowledge.deviceInfo != null) {
            contextBuilder.appendLine("**Device Information:**")
            contextBuilder.appendLine(knowledge.deviceInfo)
            contextBuilder.appendLine()
        }

        if (knowledge.memories.isNotEmpty()) {
            contextBuilder.appendLine("**Conversation Memory:**")
            knowledge.memories.forEach { memory ->
                contextBuilder.appendLine("- ${memory.content}")
            }
            contextBuilder.appendLine()
        }

        // Add recent conversation
        contextBuilder.appendLine("**Conversation:**")
        conversationHistory.takeLast(5).forEach { message ->
            val role = if (message.isUser) "User" else "Assistant"
            contextBuilder.appendLine("$role: ${message.content}")
        }

        return contextBuilder.toString()
    }

    private fun postProcess(
        response: String,
        knowledge: RetrievedKnowledge
    ): String {
        // Add subtle source attribution
        var processed = response

        if (knowledge.trivia.isNotEmpty() &&
            knowledge.trivia.any { it.relevanceScore > 0.8f }) {
            processed += "\n\n_Sources: 間 knowledge base_"
        }

        return processed
    }

    private fun calculateConfidence(knowledge: RetrievedKnowledge): Float {
        return when {
            knowledge.deviceInfo != null -> 0.95f
            knowledge.trivia.isNotEmpty() ->
                knowledge.trivia.maxOf { it.relevanceScore }
            knowledge.memories.isNotEmpty() -> 0.8f
            else -> 0.6f
        }
    }
}

data class RAGResponse(
    val text: String,
    val knowledgeSources: RetrievedKnowledge,
    val intent: QueryIntent,
    val confidenceScore: Float
)

data class RetrievedKnowledge(
    val trivia: List<TriviaResult>,
    val deviceInfo: String?,
    val memories: List<Memory>
)

enum class QueryIntent {
    DEVICE_QUESTION,
    FACTUAL,
    EXPLANATION,
    QUESTION,
    CONVERSATION
}
```

**Acceptance Criteria:**
- [ ] RAGEngine implemented with intent detection
- [ ] Retrieves from multiple knowledge sources
- [ ] Assembles context within token budget
- [ ] Generates responses with citations
- [ ] Confidence scoring functional

**Tests:**
- [ ] `RAGEngineTest.kt`: `@Test fun generateResponse_deviceQuestion()`
- [ ] `RAGEngineTest.kt`: `@Test fun generateResponse_factualQuery()`
- [ ] `RAGEngineTest.kt`: `@Test fun detectIntent_identifiesCorrectly()`
- [ ] `RAGEngineTest.kt`: `@Test fun assembleContext_staysWithinBudget()`

**Dependencies:**
- PHASE3-001 (Trivia engine)
- PHASE3-005 (Device knowledge)
- PHASE2-011 (Memory manager)

**Blocks:** PHASE3-007 (ChatViewModel integration)

---

### PHASE3-007: Integrate Knowledge Systems with ChatViewModel ⚠️ CRITICAL

**Priority:** P0 | **Estimated Hours:** 4h | **Status:** [ ]

**Description:**
Integrate trivia engine, device intelligence, and RAG engine with ChatViewModel for enhanced conversations.

**Implementation:**
File: `app/composeApp/src/commonMain/kotlin/ai/ma/viewmodel/ChatViewModel.kt` (modification)

```kotlin
class ChatViewModel(
    private val aiEngine: SmolLM2Engine,
    private val messageRepository: MessageRepository,
    private val memoryManager: MemoryManager,
    private val ragEngine: RAGEngine, // NEW
    private val responseEnricher: ResponseEnricher // NEW
) : ViewModel() {

    // ... existing code ...

    /**
     * Send message with RAG enhancement
     */
    fun sendMessage(content: String) {
        viewModelScope.launch {
            val userMessage = Message(
                id = UUID.randomUUID().toString(),
                content = content,
                isUser = true,
                timestamp = Clock.System.now(),
                projectId = _currentProject.value?.id
            )

            // Add to UI immediately
            _messages.value += userMessage

            // Save to database
            messageRepository.createMessage(userMessage)

            // Generate RAG-enhanced response
            _isGenerating.value = true

            try {
                val ragResponse = ragEngine.generateResponse(
                    query = content,
                    conversationHistory = _messages.value,
                    projectId = _currentProject.value?.id
                )

                // Optionally enrich with trivia
                val enrichedResponse = responseEnricher.enrichResponse(
                    userQuery = content,
                    aiResponse = ragResponse.text,
                    context = ConversationContext(
                        messagesSinceLastEnrichment = calculateMessagesSinceEnrichment()
                    )
                )

                val assistantMessage = Message(
                    id = UUID.randomUUID().toString(),
                    content = enrichedResponse.text,
                    isUser = false,
                    timestamp = Clock.System.now(),
                    projectId = _currentProject.value?.id
                )

                _messages.value += assistantMessage
                messageRepository.createMessage(assistantMessage)

                // Create memories from both messages
                memoryManager.createMemoriesFromMessage(userMessage)
                memoryManager.createMemoriesFromMessage(assistantMessage)

            } catch (e: Exception) {
                _error.value = "Failed to generate response: ${e.message}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private fun calculateMessagesSinceEnrichment(): Int {
        // Count messages since last enrichment
        return _messages.value
            .takeLastWhile { !it.content.contains("**Interesting fact:**") }
            .size
    }
}
```

**Acceptance Criteria:**
- [ ] RAG engine integrated into message flow
- [ ] Response enrichment optional (configurable)
- [ ] Knowledge sources tracked per message
- [ ] Confidence scores stored in metadata
- [ ] Existing tests still pass

**Tests:**
- [ ] `ChatViewModelTest.kt`: `@Test fun sendMessage_usesRAG()`
- [ ] `ChatViewModelTest.kt`: `@Test fun sendMessage_enrichesWithTrivia()`
- [ ] `ChatViewModelTest.kt`: `@Test fun sendMessage_deviceQuestion()`

**Dependencies:**
- PHASE3-006 (RAG engine)
- PHASE3-003 (Response enricher)

**Blocks:** PHASE3-008 (Integration test)

---

### PHASE3-008: Phase 3 Integration Test

**Priority:** P0 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Comprehensive integration test validating all knowledge systems working together.

**Implementation:**
File: `app/shared/src/commonTest/kotlin/ai/ma/integration/Phase3IntegrationTest.kt`

```kotlin
@Test
fun phase3Integration_knowledgeSystemsWorkTogether() = runTest {
    // Setup
    val database = createTestDatabase()
    val triviaRepository = TriviaRepositoryImpl(database)
    val embeddingEngine = createTestEmbeddingEngine()
    val vectorIndex = createTestVectorIndex()
    val deviceIntelligence = createMockDeviceIntelligence()

    // Populate knowledge base
    seedTestTrivia(triviaRepository, count = 50)

    // Create knowledge systems
    val triviaEngine = TriviaEngine(triviaRepository, embeddingEngine, vectorIndex)
    val deviceKnowledge = DeviceKnowledgeProvider(deviceIntelligence)
    val memoryManager = createTestMemoryManager()
    val aiEngine = createTestAIEngine()
    val ragEngine = RAGEngine(triviaEngine, memoryManager, deviceKnowledge, aiEngine)

    // Test 1: Factual query
    val factualResponse = ragEngine.generateResponse(
        query = "Tell me about quantum physics",
        conversationHistory = emptyList()
    )
    assertNotNull(factualResponse.knowledgeSources.trivia)
    assertTrue(factualResponse.confidenceScore > 0.7f)
    assertEquals(QueryIntent.FACTUAL, factualResponse.intent)

    // Test 2: Device query
    val deviceResponse = ragEngine.generateResponse(
        query = "What processor does my device have?",
        conversationHistory = emptyList()
    )
    assertNotNull(deviceResponse.knowledgeSources.deviceInfo)
    assertTrue(deviceResponse.confidenceScore > 0.9f)
    assertEquals(QueryIntent.DEVICE_QUESTION, deviceResponse.intent)

    // Test 3: Conversational query with memory
    val memory = Memory(
        id = "mem1",
        content = "User likes science fiction",
        embedding = embeddingEngine.embed("science fiction").toList(),
        messageId = "msg1",
        importance = 0.8f,
        timestamp = Clock.System.now()
    )
    memoryManager.storeMemory(memory)

    val conversationalResponse = ragEngine.generateResponse(
        query = "Tell me something interesting",
        conversationHistory = emptyList()
    )
    assertTrue(conversationalResponse.knowledgeSources.memories.isNotEmpty() ||
               conversationalResponse.knowledgeSources.trivia.isNotEmpty())

    // Test 4: Trivia retrieval quality
    val triviaResults = triviaEngine.retrieveTrivia("space exploration", topK = 3)
    assertEquals(3, triviaResults.size)
    assertTrue(triviaResults.all { it.relevanceScore > 0.0f })

    // Test 5: Response enrichment
    val enricher = ResponseEnricher(triviaEngine)
    val enriched = enricher.enrichResponse(
        userQuery = "What is AI?",
        aiResponse = "AI stands for Artificial Intelligence.",
        context = ConversationContext(messagesSinceLastEnrichment = 5)
    )
    // May or may not enrich based on relevance
    assertTrue(enriched.text.isNotEmpty())

    println("✅ Phase 3 integration test passed")
}

private fun seedTestTrivia(repository: TriviaRepository, count: Int) {
    // Seed diverse trivia across categories
    val categories = listOf(
        "science", "history", "technology", "mathematics", "space"
    )
    // Implementation details...
}
```

**Acceptance Criteria:**
- [ ] Tests RAG engine end-to-end
- [ ] Validates trivia retrieval quality (>70% relevance)
- [ ] Tests device intelligence integration
- [ ] Verifies memory integration
- [ ] Confirms response enrichment

**Tests:**
- [ ] `Phase3IntegrationTest.kt`: `@Test fun phase3Integration_knowledgeSystemsWorkTogether()`
- [ ] `Phase3IntegrationTest.kt`: `@Test fun knowledgeRetrieval_performance()` (<100ms @ 1,341 docs)

**Dependencies:** All Phase 3 tickets

**Blocks:** Phase 4 start

---

## Week 10: RAG Polish & Knowledge UI (Tickets 009-015)

### PHASE3-009: Knowledge Base Statistics API

**Priority:** P2 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Create API for accessing knowledge base statistics (category counts, fact totals, etc.).

**Implementation:**
File: `app/shared/src/commonMain/kotlin/ai/ma/knowledge/KnowledgeStats.kt`

```kotlin
data class KnowledgeStats(
    val totalFacts: Long,
    val categoryBreakdown: Map<String, Int>,
    val lastUpdated: Instant?,
    val vectorIndexSize: Int,
    val averageEmbeddingTime: Long // ms
)

class KnowledgeStatsProvider(
    private val triviaRepository: TriviaRepository,
    private val vectorIndex: HNSWVectorIndex
) {
    suspend fun getStats(): KnowledgeStats {
        val totalFacts = triviaRepository.getFactCount()
        val categories = triviaRepository.getAllCategories()
        val categoryBreakdown = categories.associateWith { category ->
            triviaRepository.getFactsByCategory(category).size
        }

        return KnowledgeStats(
            totalFacts = totalFacts,
            categoryBreakdown = categoryBreakdown,
            lastUpdated = null, // Would track in metadata table
            vectorIndexSize = vectorIndex.size(),
            averageEmbeddingTime = 50L // From benchmarks
        )
    }
}
```

**Acceptance Criteria:**
- [ ] KnowledgeStats data class defined
- [ ] Statistics provider implemented
- [ ] Retrieves category breakdown
- [ ] Includes vector index metrics

**Tests:**
- [ ] `KnowledgeStatsProviderTest.kt`: `@Test fun getStats_returnsAccurateCounts()`

**Dependencies:** PHASE3-002 (Trivia repository)

---

### PHASE3-010: Knowledge Browser UI (Basic)

**Priority:** P2 | **Estimated Hours:** 4h | **Status:** [ ]

**Description:**
Create UI for browsing knowledge base categories and viewing individual facts.

**Implementation:**
File: `app/composeApp/src/commonMain/kotlin/ai/ma/ui/knowledge/KnowledgeBrowserScreen.kt`

```kotlin
@Composable
fun KnowledgeBrowserScreen(
    viewModel: KnowledgeBrowserViewModel
) {
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val facts by viewModel.facts.collectAsState()

    Column {
        // Category chips
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                FilterChip(
                    selected = category == selectedCategory,
                    onClick = { viewModel.selectCategory(category) },
                    label = { Text(category) }
                )
            }
        }

        // Facts list
        LazyColumn {
            items(facts) { fact ->
                FactCard(fact = fact)
            }
        }
    }
}

@Composable
fun FactCard(fact: TriviaFact) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = fact.category.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = fact.content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
```

**Acceptance Criteria:**
- [ ] Knowledge browser screen implemented
- [ ] Category filtering functional
- [ ] Fact cards display content properly
- [ ] Responsive layout

**Tests:**
- [ ] `KnowledgeBrowserScreenTest.kt`: `@Test fun categorySelection_filtersFacts()`

**Dependencies:** PHASE3-002 (Trivia repository)

---

### PHASE3-011: Search Knowledge Base

**Priority:** P2 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Add search functionality to knowledge browser with semantic search.

**Implementation:**
File: `app/composeApp/src/commonMain/kotlin/ai/ma/viewmodel/KnowledgeBrowserViewModel.kt`

```kotlin
class KnowledgeBrowserViewModel(
    private val triviaRepository: TriviaRepository,
    private val triviaEngine: TriviaEngine
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<TriviaResult>>(emptyList())
    val searchResults: StateFlow<List<TriviaResult>> = _searchResults.asStateFlow()

    fun search(query: String) {
        _searchQuery.value = query

        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            val results = triviaEngine.retrieveTrivia(
                query = query,
                topK = 20
            )
            _searchResults.value = results
        }
    }
}
```

**Acceptance Criteria:**
- [ ] Search input field added to UI
- [ ] Semantic search using trivia engine
- [ ] Results show relevance scores
- [ ] Debounced search (300ms)

**Tests:**
- [ ] `KnowledgeBrowserViewModelTest.kt`: `@Test fun search_returnsRelevantResults()`

**Dependencies:** PHASE3-001 (Trivia engine)

---

### PHASE3-012: RAG Settings & Toggles

**Priority:** P2 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Add user settings to enable/disable RAG features and trivia enrichment.

**Implementation:**
File: `app/shared/src/commonMain/kotlin/ai/ma/domain/model/Settings.kt` (modification)

```kotlin
data class Settings(
    // ... existing fields ...

    // RAG settings
    val ragEnabled: Boolean = true,
    val triviaEnrichmentEnabled: Boolean = true,
    val deviceKnowledgeEnabled: Boolean = true,
    val enrichmentFrequency: EnrichmentFrequency = EnrichmentFrequency.MEDIUM
)

enum class EnrichmentFrequency {
    OFF,
    LOW,      // Every 5+ messages
    MEDIUM,   // Every 3+ messages
    HIGH      // Every message (if relevant)
}
```

**Acceptance Criteria:**
- [ ] RAG toggle in settings
- [ ] Trivia enrichment frequency control
- [ ] Device knowledge toggle
- [ ] Settings persist to database

**Tests:**
- [ ] `SettingsRepositoryTest.kt`: `@Test fun ragSettings_persistCorrectly()`

**Dependencies:** PHASE0-008 (Settings table)

---

### PHASE3-013: Trivia Fact Favorites

**Priority:** P2 | **Estimated Hours:** 3h | **Status:** [ ]

**Description:**
Allow users to favorite trivia facts for quick access.

**Implementation:**
File: `app/shared/src/commonMain/sqldelight/ai/ma/db/TriviaFact.sq` (modification)

```sql
-- Add favorite column
ALTER TABLE TriviaFact ADD COLUMN isFavorite INTEGER DEFAULT 0;

-- Queries
setFavorite:
UPDATE TriviaFact SET isFavorite = ? WHERE id = ?;

getFavorites:
SELECT * FROM TriviaFact WHERE isFavorite = 1 ORDER BY title;
```

**Acceptance Criteria:**
- [ ] Favorite toggle added to fact cards
- [ ] Favorites section in knowledge browser
- [ ] Favorite status persists

**Tests:**
- [ ] `TriviaRepositoryTest.kt`: `@Test fun setFavorite_persistsCorrectly()`

**Dependencies:** PHASE3-010 (Knowledge browser UI)

---

### PHASE3-014: Knowledge Base Update Mechanism

**Priority:** P1 | **Estimated Hours:** 4h | **Status:** [ ]

**Description:**
Create mechanism to update knowledge base from new JSON exports (future-proofing).

**Implementation:**
File: `app/shared/src/commonMain/kotlin/ai/ma/knowledge/KnowledgeBaseUpdater.kt`

```kotlin
class KnowledgeBaseUpdater(
    private val database: Database,
    private val embeddingEngine: CachedEmbeddingEngine,
    private val vectorIndex: HNSWVectorIndex
) {
    suspend fun updateFromJSON(jsonContent: String): UpdateResult {
        val newFacts = parseKnowledgeBase(jsonContent)

        return withContext(Dispatchers.IO) {
            database.transaction {
                var added = 0
                var updated = 0

                newFacts.forEach { fact ->
                    val existing = database.triviaFactQueries
                        .getById(fact.id)
                        .executeAsOneOrNull()

                    if (existing == null) {
                        // New fact - embed and insert
                        val embedding = embeddingEngine.embed(fact.content)
                        database.triviaFactQueries.insert(
                            id = fact.id,
                            title = fact.title,
                            content = fact.content,
                            category = fact.category,
                            source = fact.source,
                            embedding = embedding.toList().toByteArray()
                        )
                        vectorIndex.add(fact.id, embedding)
                        added++
                    } else if (existing.content != fact.content) {
                        // Update changed fact
                        val embedding = embeddingEngine.embed(fact.content)
                        database.triviaFactQueries.update(
                            id = fact.id,
                            content = fact.content,
                            embedding = embedding.toList().toByteArray()
                        )
                        vectorIndex.update(fact.id, embedding)
                        updated++
                    }
                }

                UpdateResult(
                    added = added,
                    updated = updated,
                    total = newFacts.size
                )
            }
        }
    }

    private fun parseKnowledgeBase(json: String): List<TriviaFact> {
        // Parse M1K3 knowledge base JSON format
        // Implementation details...
        return emptyList()
    }
}

data class UpdateResult(
    val added: Int,
    val updated: Int,
    val total: Int
)
```

**Acceptance Criteria:**
- [ ] Parses M1K3 JSON format
- [ ] Detects new vs updated facts
- [ ] Re-embeds updated content
- [ ] Updates vector index
- [ ] Transactional (all-or-nothing)

**Tests:**
- [ ] `KnowledgeBaseUpdaterTest.kt`: `@Test fun updateFromJSON_addsNewFacts()`
- [ ] `KnowledgeBaseUpdaterTest.kt`: `@Test fun updateFromJSON_updatesChanged()`

**Dependencies:** PHASE3-001 (Trivia engine)

---

### PHASE3-015: Documentation: Knowledge Systems

**Priority:** P2 | **Estimated Hours:** 2h | **Status:** [ ]

**Description:**
Document knowledge systems architecture, RAG workflow, and usage guidelines.

**Implementation:**
File: `app/docs/KNOWLEDGE_SYSTEMS.md`

```markdown
# Knowledge Systems Architecture

## Overview
間 AI integrates M1K3's comprehensive knowledge base (1,341+ documents, 20 categories)
with device intelligence and conversation memory to provide contextual, grounded AI responses.

## Components

### 1. Trivia Engine
- **Purpose**: Semantic search over knowledge base
- **Technology**: MiniLM-L6 embeddings + HNSW vector index
- **Performance**: <100ms retrieval @ 1,341 documents

### 2. Device Intelligence
- **Purpose**: Understands user's device (manufacturer, SoC, capabilities)
- **Provides**: Contextual device information for troubleshooting

### 3. RAG Engine
- **Purpose**: Retrieval-Augmented Generation
- **Workflow**:
  1. Detect query intent
  2. Retrieve relevant knowledge (trivia/device/memory)
  3. Assemble context within token budget
  4. Generate AI response with citations

### 4. Response Enrichment
- **Purpose**: Inject interesting facts into conversations
- **Frequency**: Configurable (off/low/medium/high)

## Usage

### Enable RAG
```kotlin
val ragEngine = RAGEngine(triviaEngine, memoryManager, deviceKnowledge, aiEngine)
val response = ragEngine.generateResponse("Tell me about quantum computing")
```

### Query Device Knowledge
```kotlin
val deviceInfo = deviceKnowledge.answerDeviceQuestion("What processor do I have?")
```

## Performance
- Trivia retrieval: <100ms @ 1,341 docs
- RAG response: ~2-3s (including AI inference)
- Memory overhead: ~50MB (embeddings + index)
```

**Acceptance Criteria:**
- [ ] Architecture overview documented
- [ ] Component responsibilities clear
- [ ] Usage examples provided
- [ ] Performance benchmarks listed

**Dependencies:** All Phase 3 tickets

---

## Phase 3 Summary

### Tickets by Priority
- **P0 (Critical):** 4 tickets (001, 004, 006, 007)
- **P1 (Important):** 4 tickets (002, 003, 005, 014)
- **P2 (Enhancement):** 7 tickets (009, 010, 011, 012, 013, 015)

### Key Deliverables
1. ✅ Trivia engine with semantic search
2. ✅ Device intelligence (OEM profiles, SoC detection)
3. ✅ RAG integration with intent detection
4. ✅ Response enrichment with contextual facts
5. ✅ Knowledge browser UI
6. ✅ Settings & toggles for RAG features

### Testing Requirements
- **Unit tests:** 30+ tests across knowledge components
- **Integration test:** Phase 3 end-to-end validation
- **Performance:** <100ms knowledge retrieval @ 1,341 docs

### Documentation
- `KNOWLEDGE_SYSTEMS.md`: Architecture and usage guide

---

**Next Phase:** [Phase 4: Multi-Modal & Projects](PHASE4.md) (20 tickets, Weeks 11-12)
