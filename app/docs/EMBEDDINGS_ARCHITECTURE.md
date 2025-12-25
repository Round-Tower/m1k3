# Embeddings Architecture - Multi-Source Semantic Search

## Overview

Comprehensive embeddings management system for 間 AI, supporting multiple data sources with unified semantic search capabilities.

**Status:** 🚧 In Development (Phase 3)

---

## Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                      Presentation Layer                      │
│  ChatScreen, EcoStatsScreen, DeviceInsightsScreen, etc.     │
└────────────────────┬────────────────────────────────────────┘
                     │ observes StateFlow
┌────────────────────▼────────────────────────────────────────┐
│                   EmbeddingsViewModel                        │
│  • Manages embedding engine lifecycle                        │
│  • Exposes loading state (Loading/Ready/Error)              │
│  • Provides unified semantic search API                      │
│  • Handles device-adaptive optimization                      │
└────────────────────┬────────────────────────────────────────┘
                     │ uses
┌────────────────────▼────────────────────────────────────────┐
│                  EmbeddingRepository                         │
│  • Loads MiniLM-L6 ONNX model (18MB, 384-dim)              │
│  • Caches embedding engine (singleton pattern)              │
│  • Thread-safe initialization with coroutines               │
│  • Memory-efficient model management                         │
└────────────────────┬────────────────────────────────────────┘
                     │ provides embeddings for
┌────────────────────▼────────────────────────────────────────┐
│              Multi-Source Embedding System                   │
│                                                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐│
│  │ Knowledge Base  │  │  Eco Credits    │  │   Device    ││
│  │   Embeddings    │  │   Embeddings    │  │  Context    ││
│  │                 │  │                 │  │ Embeddings  ││
│  │ • 1,401 docs    │  │ • Savings logs  │  │ • Health    ││
│  │ • 24 categories │  │ • Achievements  │  │ • Wellbeing ││
│  │ • RAG retrieval │  │ • Milestones    │  │ • Location  ││
│  └─────────────────┘  └─────────────────┘  └─────────────┘│
└─────────────────────────────────────────────────────────────┘
```

---

## Components

### 1. EmbeddingRepository

**Responsibility:** Manage embedding engine lifecycle and initialization

**Location:** `app/composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/embedding/EmbeddingRepository.kt`

**API:**
```kotlin
interface EmbeddingRepository {
    /**
     * Initialize embedding engine asynchronously.
     * Thread-safe, idempotent (multiple calls return same engine).
     */
    suspend fun initialize(): Result<EmbeddingEngine>

    /**
     * Get cached embedding engine (null if not initialized).
     */
    fun getEngine(): EmbeddingEngine?

    /**
     * Release resources (called on app termination).
     */
    suspend fun release()

    /**
     * Get initialization status for UI feedback.
     */
    fun getStatus(): EmbeddingLoadStatus
}

data class EmbeddingLoadStatus(
    val state: LoadState,
    val loadTimeMs: Long? = null,
    val error: String? = null
)

enum class LoadState {
    IDLE,           // Not started
    LOADING,        // In progress
    READY,          // Successfully loaded
    ERROR           // Failed to load
}
```

**Implementation Notes:**
- Singleton pattern (single ONNX model instance)
- Thread-safe with `Mutex` or `synchronized`
- Loads on `Dispatchers.IO` to avoid blocking
- Caches engine after first successful load
- Provides detailed error messages for debugging

---

### 2. EmbeddingsViewModel

**Responsibility:** Expose embedding state to UI, manage lifecycle

**Location:** `app/composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/embedding/EmbeddingsViewModel.kt`

**API:**
```kotlin
class EmbeddingsViewModel(
    private val repository: EmbeddingRepository,
    private val scope: CoroutineScope
) {
    // Observable state for UI
    private val _state = MutableStateFlow(EmbeddingState())
    val state: StateFlow<EmbeddingState> = _state.asStateFlow()

    /**
     * Initialize embeddings in background.
     * Called once during app startup.
     */
    fun initialize()

    /**
     * Get embedding engine (null if not ready).
     * Use for RAG, semantic search, etc.
     */
    fun getEngine(): EmbeddingEngine?

    /**
     * Embed text and search across all data sources.
     * Returns top-K results from knowledge, eco, device contexts.
     */
    suspend fun search(
        query: String,
        sources: Set<EmbeddingSource> = setOf(EmbeddingSource.KNOWLEDGE),
        topK: Int = 5
    ): List<EmbeddingSearchResult>

    /**
     * Clean up resources.
     */
    fun release()
}

data class EmbeddingState(
    val loadStatus: EmbeddingLoadStatus = EmbeddingLoadStatus(LoadState.IDLE),
    val modelName: String = "MiniLM-L6-v2",
    val modelSize: String = "18MB",
    val dimensions: Int = 384
)

enum class EmbeddingSource {
    KNOWLEDGE,      // Knowledge base facts (1,401 docs)
    ECO_CREDITS,    // Eco savings history and achievements
    DEVICE_CONTEXT  // Health, wellbeing, location data
}

data class EmbeddingSearchResult(
    val source: EmbeddingSource,
    val content: String,
    val similarity: Float,
    val metadata: Map<String, Any> = emptyMap()
)
```

**Usage Example:**
```kotlin
// In ChatScreen.kt
val embeddingsVM = rememberEmbeddingsViewModel()
val embeddingState by embeddingsVM.state.collectAsState()

// Show loading indicator
when (embeddingState.loadStatus.state) {
    LoadState.LOADING -> Text("Loading embeddings...")
    LoadState.READY -> Text("✅ Ready (${embeddingState.loadStatus.loadTimeMs}ms)")
    LoadState.ERROR -> Text("❌ ${embeddingState.loadStatus.error}")
    LoadState.IDLE -> Text("⚪ Not initialized")
}

// Use for semantic search
scope.launch {
    val results = embeddingsVM.search(
        query = "How to save water?",
        sources = setOf(EmbeddingSource.KNOWLEDGE, EmbeddingSource.ECO_CREDITS),
        topK = 5
    )
    // Process results...
}
```

---

## Multi-Source Embedding System

### Phase 3a: Knowledge Base Embeddings (Current)

**Status:** ✅ Implemented (2025-11-08)

**Data Source:** `comprehensive_knowledge_base.json` (1,401 documents)

**Storage:** SQLite table `TriviaFact`
```sql
CREATE TABLE TriviaFact (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    category TEXT NOT NULL,
    fact TEXT NOT NULL,
    embedding BLOB  -- 384-dim float array (1,536 bytes)
)
```

**Usage:** RAG system with intent classification

**Integration:**
```kotlin
val ragManager = RAGManager(database, embeddingsVM.getEngine()!!)
val result = ragManager.enrichPrompt(userQuery, systemPrompt)
```

---

### Phase 3b: Eco Credits Embeddings (Planned)

**Status:** 🔄 In Design

**Data Sources:**
1. **Savings History:** Water, energy, CO2 saved per message
2. **Achievements:** Milestones (Water Bottle, Bathtub, Pool, Olympic Pool)
3. **Trends:** Weekly/monthly savings patterns
4. **Comparisons:** User savings vs cloud AI baseline

**Storage:** New table `EcoEmbedding`
```sql
CREATE TABLE EcoEmbedding (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    embedding_type TEXT NOT NULL,  -- 'achievement', 'trend', 'comparison'
    content TEXT NOT NULL,          -- Natural language description
    metadata TEXT NOT NULL,         -- JSON: {waterMl, energyWh, co2g, timestamp}
    embedding BLOB                  -- 384-dim float array
)
```

**Example Embeddings:**
- "You've saved 2,500ml of water using local AI - equivalent to 5 water bottles!"
- "Your weekly CO2 savings increased by 15% compared to last week"
- "Achievement unlocked: Bathtub milestone (100L water saved)"

**Use Cases:**
1. **Eco Insights:** "How much water have I saved?" → Retrieves relevant savings facts
2. **Motivation:** Surfaces achievements during chat ("By the way, you just hit 1L saved!")
3. **Trends:** "Am I improving my eco impact?" → Retrieves trend embeddings

**Integration:**
```kotlin
// Search eco embeddings
val ecoResults = embeddingsVM.search(
    query = "How much water have I saved?",
    sources = setOf(EmbeddingSource.ECO_CREDITS),
    topK = 3
)

// Inject into system prompt
val ecoContext = ecoResults.joinToString("\n") { it.content }
systemPrompt += "\n\nUser's Eco Impact:\n$ecoContext"
```

---

### Phase 3c: Device Context Embeddings (Planned)

**Status:** 🔄 In Design

**Data Sources:**

#### 1. Health Data (via Android Health Connect API)
- **Steps:** Daily step count, trends
- **Sleep:** Sleep duration, quality patterns
- **Heart Rate:** Resting HR, exercise HR
- **Activity:** Workout types, duration, frequency

**Privacy:** All data stays local, no cloud sync

**Example Embeddings:**
- "User walked 12,347 steps today (above average for weekdays)"
- "Sleep quality declined this week (6.2 hours avg vs 7.5 hours normal)"
- "User completed 3 workouts this week (running, yoga, strength training)"

#### 2. Digital Wellbeing (via Android UsageStats API)
- **Screen Time:** Daily/weekly usage patterns
- **App Usage:** Top apps, time spent
- **Focus Modes:** Work hours, break times
- **Interruptions:** Notification frequency

**Example Embeddings:**
- "User's screen time decreased 15% this week (target: -20%)"
- "Most active app: ChatGPT (2.5h/day) - now replaced by 間 AI (0h cloud usage!)"
- "Focus mode enabled 4x this week (2h avg sessions)"

#### 3. Location Context (via Android Location API - Optional)
- **Places:** Home, work, gym (geocoded, no raw coordinates stored)
- **Commute:** Transit patterns, commute time trends
- **Context-Aware:** At gym → fitness tips, at home → relaxation mode

**Privacy:** Only semantic locations stored (e.g., "gym"), not GPS coordinates

**Example Embeddings:**
- "User is at gym (detected via WiFi fingerprint) - likely interested in workout tips"
- "Commute time increased by 10 minutes this week (traffic patterns)"
- "User spent 70% of time at home this week (work-from-home detected)"

**Storage:** New table `DeviceContextEmbedding`
```sql
CREATE TABLE DeviceContextEmbedding (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    context_type TEXT NOT NULL,     -- 'health', 'wellbeing', 'location'
    content TEXT NOT NULL,           -- Natural language summary
    metadata TEXT NOT NULL,          -- JSON: {date, metric, value, trend}
    embedding BLOB,                  -- 384-dim float array
    timestamp INTEGER NOT NULL       -- Unix timestamp
)
```

**Use Cases:**
1. **Personalized Responses:** "I'm tired" → Retrieves sleep data → "You slept 5.5h last night, below your 7h average. Try a 20-min nap?"
2. **Context-Aware Tips:** At gym → Retrieves workout history → "You haven't done leg day in 2 weeks!"
3. **Proactive Insights:** Screen time spike → "Your screen time increased 40% this week. Need a digital detox plan?"

**Integration:**
```kotlin
// Search device context
val deviceResults = embeddingsVM.search(
    query = "I'm feeling tired lately",
    sources = setOf(EmbeddingSource.DEVICE_CONTEXT),
    topK = 3
)

// Inject into system prompt
val deviceContext = deviceResults.joinToString("\n") { it.content }
systemPrompt += "\n\nDevice Insights:\n$deviceContext"
```

---

## Privacy & Security

### Data Retention Policies
1. **Knowledge Base:** Permanent (part of app bundle)
2. **Eco Credits:** 1 year retention (configurable)
3. **Device Context:**
   - Health: 90 days (configurable: 30/60/90/365 days)
   - Wellbeing: 30 days
   - Location: 7 days (most privacy-sensitive)

### Encryption
- All embeddings stored with SQLCipher AES-256
- No cloud backup (100% local)
- User can clear device context anytime (Settings → Privacy → Clear Device Context)

### Permissions
- Health: Android Health Connect API (user grants access)
- Wellbeing: UsageStats API (user grants access in Settings)
- Location: Optional, OFF by default (user explicitly enables)

### Transparency
- Settings screen shows:
  - Number of embeddings per source
  - Storage size per source
  - Last update timestamp
  - "View Raw Data" option (JSON export)

---

## Device-Adaptive Configuration

### RAM-Based Optimization
| Device RAM | Max Embeddings | HNSW M | efConstruction |
|------------|----------------|--------|----------------|
| <4GB       | 500 total      | 8      | 100            |
| 4-6GB      | 1,000 total    | 12     | 150            |
| 6-8GB      | 2,000 total    | 16     | 200            |
| 8-12GB     | 5,000 total    | 20     | 250            |
| 12GB+      | 10,000 total   | 24     | 300            |

### Source Prioritization
```kotlin
when (deviceRamGB) {
    in 0..4 -> setOf(EmbeddingSource.KNOWLEDGE)  // Only knowledge base
    in 4..6 -> setOf(EmbeddingSource.KNOWLEDGE, EmbeddingSource.ECO_CREDITS)
    in 6..8 -> setOf(EmbeddingSource.KNOWLEDGE, EmbeddingSource.ECO_CREDITS, EmbeddingSource.DEVICE_CONTEXT)
    else -> EmbeddingSource.values().toSet()  // All sources enabled
}
```

---

## Implementation Roadmap

### Phase 3a: Foundation (Current Sprint)
- [x] MiniLM-L6 ONNX engine
- [x] Knowledge base embeddings (1,401 docs)
- [x] RAG system with intent classification
- [ ] **Create EmbeddingRepository**
- [ ] **Create EmbeddingsViewModel**
- [ ] **Refactor ChatScreen to use ViewModel**

### Phase 3b: Eco Credits Integration (Next Sprint)
- [ ] Design `EcoEmbedding` schema
- [ ] Generate embeddings for savings history
- [ ] Achievement embeddings ("Bathtub milestone unlocked!")
- [ ] Trend embeddings (weekly/monthly patterns)
- [ ] Integrate into chat system prompt

### Phase 3c: Device Context Integration (Future Sprint)
- [ ] Android Health Connect integration
- [ ] UsageStats API integration (digital wellbeing)
- [ ] Location context (optional, privacy-focused)
- [ ] Generate context embeddings
- [ ] Privacy settings UI (retention, permissions)

### Phase 4: Multi-Source Search (Future)
- [ ] Unified search API across all sources
- [ ] Source weighting (e.g., 60% knowledge, 30% eco, 10% device)
- [ ] Hybrid retrieval (semantic + keyword)
- [ ] Cross-source reasoning ("You saved 2L water AND walked 10k steps!")

---

## Testing Strategy

### Unit Tests
- `EmbeddingRepositoryTest`: Initialization, caching, thread-safety
- `EmbeddingsViewModelTest`: State management, search API
- `MultiSourceSearchTest`: Cross-source retrieval, ranking

### Integration Tests
- Load 10K embeddings, verify memory usage <100MB
- Search performance: <100ms @ 5K embeddings
- Concurrent access: 10 threads searching simultaneously

### UI Tests
- Loading states (IDLE → LOADING → READY)
- Error handling (model file missing, ONNX Runtime crash)
- Device-adaptive source selection

---

## Performance Targets

| Metric | Target | Validation |
|--------|--------|------------|
| **Model Load Time** | <3 seconds | Stopwatch measurement |
| **Search Latency** | <100ms @ 5K embeddings | Benchmark test |
| **Memory Usage** | <50MB per 1K embeddings | Android Profiler |
| **Embedding Generation** | <50ms per text | Performance test |
| **Concurrent Searches** | 10 threads without blocking | Load test |

---

## Migration Plan

### Step 1: Extract EmbeddingRepository
```kotlin
// Before: Inline initialization in ChatScreen
val embeddingEngine = remember(context) {
    runBlocking { /* load model */ }
}

// After: Use repository
val repository = EmbeddingRepository(context)
scope.launch {
    repository.initialize()
}
```

### Step 2: Create EmbeddingsViewModel
```kotlin
// In ChatScreen
val embeddingsVM = rememberEmbeddingsViewModel()
val state by embeddingsVM.state.collectAsState()

when (state.loadStatus.state) {
    LoadState.LOADING -> ShowLoadingSpinner()
    LoadState.READY -> EnableRAGFeatures()
    LoadState.ERROR -> ShowErrorMessage()
}
```

### Step 3: Migrate RAGManager
```kotlin
// Before: Pass embedding engine directly
val ragManager = RAGManager(database, embeddingEngine)

// After: Get engine from ViewModel
val ragManager = remember(embeddingsVM) {
    embeddingsVM.getEngine()?.let { RAGManager(database, it) }
}
```

---

## API Surface for Clients

### ChatScreen
```kotlin
val embeddingsVM = rememberEmbeddingsViewModel()

// RAG with knowledge base
val ragManager = remember(embeddingsVM.getEngine()) {
    embeddingsVM.getEngine()?.let { RAGManager(database, it) }
}

// Search knowledge
val knowledgeResults = embeddingsVM.search(query, sources = setOf(KNOWLEDGE))
```

### EcoStatsScreen
```kotlin
val embeddingsVM = rememberEmbeddingsViewModel()

// Search eco history
val ecoResults = embeddingsVM.search(
    query = "water savings this month",
    sources = setOf(ECO_CREDITS)
)

// Display insights
ecoResults.forEach { result ->
    Text(result.content)  // "You saved 5L of water this month!"
}
```

### DeviceInsightsScreen (Future)
```kotlin
val embeddingsVM = rememberEmbeddingsViewModel()

// Search health + wellbeing
val deviceResults = embeddingsVM.search(
    query = "my health trends",
    sources = setOf(DEVICE_CONTEXT),
    topK = 10
)

// Display personalized insights
deviceResults.forEach { result ->
    InsightCard(result.content, result.metadata)
}
```

---

## Conclusion

This architecture provides:
1. ✅ **Separation of concerns** - Repository/ViewModel pattern
2. ✅ **Scalability** - Multi-source embeddings (knowledge, eco, device)
3. ✅ **Privacy-first** - Local-only, encrypted, user-controlled retention
4. ✅ **Device-adaptive** - RAM-based optimization
5. ✅ **Testable** - Clean interfaces, dependency injection
6. ✅ **Future-proof** - Easy to add new embedding sources

**Next Steps:** Implement Phase 3a (EmbeddingRepository + EmbeddingsViewModel)

---

**Last Updated:** 2025-11-11
**Author:** 間 AI Development Team
**Status:** 🚧 In Development
