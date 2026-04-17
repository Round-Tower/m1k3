# Next Session — 2026-04-17

## Completed This Session

### Priority 1: Notification ID Collision Fix
- `ChatScreenViewModel:1204` — `forEach` → `forEachIndexed`, ID now `"${category}_${timestamp}_${index}"`
- Prevents silent data loss when multiple context snapshots arrive in same millisecond

### Priority 2: Tool Execution History Table
- **New `ToolExecution.sq`** — 10-column table (tool_id, query, result, success, execution_time_ms, timestamp, message_id, project_id)
- **Migration `2.sqm`** (v2→v3) — creates table + 2 indexes for existing installs
- **`ToolExecutionDataSource`** — 8 query methods (record, getByToolId, getToolUsageStats, etc.)
- **8 tests** in `ToolExecutionDataSourceTest` — all passing
- **Wired** from `ChatScreenViewModel.persistToolExecutions()` — every tool call now persisted
- Uses UUID for IDs (no collision risk), runs on `Dispatchers.IO` (off main thread)
- Registered as singleton in AppModule, injected via PlatformModule

### Priority 3: MemoryManager Wired into DI
- `MemoryRanker` registered as singleton in AppModule
- `MemoryManager` created inline in PlatformModule ViewModel factory (scoped to projectId)
- Basic ops work: `getMemoryCount()`, `getRecentMemories()`, `pinMemory()`
- `createMemoriesFromMessage()` needs embedding engine (now fixed in Priority 6)

### Priority 4: FTS5 Full-Text Search
- **`MessageFts`** — FTS5 virtual table with porter stemmer + unicode61 tokenizer
- **`TriviaFactFts`** — FTS5 for question + answer columns
- **3 sync triggers each** — INSERT/DELETE/UPDATE keep FTS in lockstep with source tables
- **Migration `3.sqm`** (v3→v4) — creates FTS tables, populates from existing data, installs triggers
- **5 FTS5 queries** in Message.sq, **2 FTS5 queries** in TriviaFact.sq
- **SearchRepository upgraded** — FTS5-first with LIKE fallback on error
- `toFtsQuery()` sanitizes user input + quotes FTS5 reserved words (`AND`/`OR`/`NOT`/`NEAR`)

### Priority 5: Strip Gemma Format Tokens
- `cleanStreamingToken()` existed in `StringUtils.kt` but was **never called**
- Wired into `LlamaCppEngine.generateStreaming()` — strips `<start_of_turn>`, `<end_of_turn>`, ChatML tokens, etc.
- Handles tokenizer-split fragments, leading whitespace at generation start
- Single regex pass per token for performance

### Priority 6: Fix Embedding Engine Loading
- **Root cause**: Two separate `EmbeddingEngine` instances — one in Koin (unloaded, held by SemanticRetrievalService), one in EmbeddingEngineManagerImpl (loaded by MainActivity, unused by others)
- **Fix**: `EmbeddingEngineManagerImpl` now accepts `sharedEngine` parameter — PlatformModule passes the same Koin singleton
- `initialize()` now calls `loadModel()` on the shared instance → SemanticRetrievalService automatically gets a loaded engine
- **Unblocks**: RAG semantic search, MemoryManager create/retrieve (once embedding + vector repos wired)

### Code Quality Fixes (from reviewer)
- FTS5 keyword injection: `AND`/`OR`/`NOT`/`NEAR` quoted in `toFtsQuery()`
- Main-thread DB write: `persistToolExecutions` wrapped in `Dispatchers.IO`
- ID collision: Tool execution IDs use UUID instead of timestamp+index

### DB Schema Summary
- **10 tables** (was 8): + ToolExecution + MessageFts + TriviaFactFts
- **Schema v4** (was v2): 3 migrations total (1.sqm, 2.sqm, 3.sqm)
- **6 triggers** for FTS sync (3 per FTS table)

## Next Up — Remaining Gaps

### Priority 7: WAL Mode + Aggregate Reconciliation
- Enable `PRAGMA journal_mode=WAL` for concurrent read/write
- Add `reconcileProjectStats()` query to fix drifted counters

### Wire Embedding + Vector repos into MemoryManager
- MemoryManager DI is wired but `embeddingRepository` and `vectorSearchRepository` are null
- Need to register `AndroidEmbeddingEngine` as `EmbeddingRepository` adapter in PlatformModule
- Need to register `AndroidVectorSearchEngine` as `VectorSearchRepository`
- Then `createMemoriesFromMessage()` and `retrieveRelevantMemories()` will work

### Future: Search UI
- Surface FTS5 results in chat (e.g., "show me all web searches" → query ToolExecution table)
- Tool usage analytics dashboard

### Pre-existing Issues (not introduced by us)
- SQLCipher: Schema comments promise AES-256 but driver is plain `AndroidSqliteDriver`
- `println` debug statements in `ChatWithToolsUseCase.kt` (lines ~146, 150, 158)
- `GlobalScope.launch` for model download in PlatformModule ViewModel factory
- 13 pre-existing test failures in `GenerationConfigBuilderTest`/`GenerationConstantsTest`

## Gotchas & Blockers

- **Two ChatWithToolsUseCase classes**: `app/composeApp/.../chat/usecase/` (active) vs `app/shared/.../domain/usecases/chat/` (domain). Edit the composeApp one.
- **Qwen 0.6B tool limitations**: Ignores web_search. Force-execute handles this.
- **Gemma drops underscores**: `<toolcall>` → `<tool_call>` handled by normalizer.
- **Kermit Logger silent**: Use `println()` for debug.
- **Pixel 9a**: USB `59021JEBF12282`

## Continuation Prompt

> We're building M1K3 — a theatrical villain AI assistant running entirely on-device (Android, KMP). This session shipped 6 priorities: (1) notification ID collision fix, (2) ToolExecution history table with UUID IDs + IO dispatcher, (3) MemoryManager wired into Koin DI, (4) FTS5 full-text search with BM25 ranking + porter stemmer, (5) Gemma format token stripping via cleanStreamingToken() in LlamaCppEngine, (6) embedding engine loading fix — shared engine instance between Koin and EmbeddingEngineManagerImpl.
>
> DB is 10 tables, schema v4, 3 migrations. SemanticRetrievalService now gets a loaded embedding engine. FTS5 has FTS-first + LIKE fallback with reserved keyword quoting. Tool execution persists to DB on IO thread.
>
> Remaining: WAL mode (Priority 7), wire embedding + vector repos into MemoryManager (for full semantic memory), pre-existing issues (SQLCipher not wired, println debug, GlobalScope download).
