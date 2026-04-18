# Next Session — 2026-04-18

## Completed This Session (6 commits)

### `af8e893f` feat(db): ToolExecution, FTS5, MemoryManager DI, embedding fix
1. **Notification ID collision fix** — `forEachIndexed` + index suffix
2. **ToolExecution table** — 10-column table, UUID IDs, IO dispatcher, 8 tests
3. **MemoryManager wired** — Koin DI with projectId scoping
4. **FTS5 full-text search** — added then removed (see next commit)
5. **Gemma format token stripping** — `cleanStreamingToken()` wired into LlamaCppEngine
6. **Embedding engine fix** — shared instance between Koin and EmbeddingEngineManagerImpl

### `2e76db92` fix(db): Remove FTS5 + web search IO dispatcher
- **FTS5 crash on Pixel 9a** — `no such module: fts5` — device SQLite lacks FTS5
- Removed all FTS5 virtual tables, triggers, queries. Migration 3.sqm is no-op
- **WebSearchExecutor** — wrapped in `withContext(Dispatchers.IO)` (was NetworkOnMainThreadException)

### `fc6b2e6f` perf(inference): ARM dotprod+i8mm, batch threads, context tuning
- `GGML_CPU_ARM_ARCH=armv8.2-a+dotprod+i8mm` — 4-8x faster int8/int4 matmul
- Batch threads 4→6 for prompt prefill
- Context window 4096→2048 for sub-4B models
- Q8_0 KV cache attempted but causes context creation failure — TODO

### `55162717` fix(chat): Streaming in tool path + web search IO
- **ChatWithToolsUseCase** — `flow` → `channelFlow` with `trySend(ChatEvent.Streaming)`
- Removed `token.trim()` that was stripping natural whitespace

### `1bd0454b` fix(chat): Strip split format tokens + robust regex
- `cleanFormatTokens()` on accumulated text at finalization (catches split tokens)
- Regex handles mangled variants: `</ startofturn>`, `</start_of_turn>`, `<toolcall>`
- Temporary: prompt dump to `files/debug_prompt.txt` for inspection

### Key Metrics
- DB: 9 tables, schema v4, 3 migrations
- Tests: 812 pass, 13 pre-existing failures (GenerationConfig*)
- Embedding engine loads in ~500ms on Pixel 9a
- Inference: ~0.7 tok/s on Qwen 0.8B (needs investigation — see below)

## Next Up

### Priority 1: ChatFormat output token abstraction (refactor)
- Move format token stripping from universal regex → per-ChatFormat patterns
- Add `outputStripPatterns: List<Regex>` to `ChatFormat` sealed class
- `cleanStreamingToken()` uses active model's patterns instead of monster regex
- ~30-45 min, prevents regex from growing with each new model

### Priority 2: Investigate 0.7 tok/s on Qwen 0.8B
- Pixel 9a (Tensor G4) should do 10-20 tok/s on a 0.8B Q4_K_M model
- ARM dotprod+i8mm compiled in but may need native library clean rebuild
- Check: `./gradlew clean` then rebuild to force native recompilation
- Check: are threads landing on efficiency cores? Thread affinity might help
- Q8_0 KV cache would help but causes `llama_new_context_with_model()` failure

### Priority 3: WAL mode + aggregate reconciliation
- `PRAGMA journal_mode=WAL` for concurrent read/write
- `reconcileProjectStats()` query to fix drifted counters

### Priority 4: Wire embedding + vector repos into MemoryManager
- `embeddingRepository` and `vectorSearchRepository` still null
- Register `AndroidEmbeddingEngine` as `EmbeddingRepository` in PlatformModule
- Register `AndroidVectorSearchEngine` as `VectorSearchRepository`
- Unblocks `createMemoriesFromMessage()` and `retrieveRelevantMemories()`

### Priority 5: Prompt budget optimization
- Welcome prompt is ~700 tokens (full M1K3 ethos + 15 tool names + format instructions)
- That's 35% of the 2048 context window before the model thinks
- Consider: shorter ethos, tool names only when relevant, drop format instructions

### Cleanup
- Remove `debug_prompt.txt` dump from LlamaCppEngine (temporary debug aid)
- Remove `println` debug statements in ChatWithToolsUseCase (lines ~146, 150, 158)
- Pre-existing: SQLCipher not wired (schema says AES-256, driver is plain), GlobalScope download

## Gotchas & Blockers

- **Two ChatWithToolsUseCase classes**: composeApp one is active (now uses `channelFlow`)
- **Qwen 0.6B/0.8B tool limitations**: Ignores web_search. Force-execute handles this
- **Gemma drops underscores**: `<toolcall>` → `<tool_call>` handled by normalizer
- **Kermit Logger silent**: Use `println()` for debug
- **Pixel 9a**: WiFi ADB as `adb-59021JEBF12282-mSpoqw`, USB as `59021JEBF12282`
- **FTS5 not available**: Pixel 9a SQLite lacks FTS5 module. Don't re-add without runtime check

## Continuation Prompt

> We're building M1K3 — a theatrical villain AI assistant running entirely on-device (Android, KMP). This session shipped 6 commits: ToolExecution history table, MemoryManager DI wiring, embedding engine shared-instance fix, ARM dotprod+i8mm optimizations, streaming in tool-calling path (channelFlow), and format token stripping (split-token reassembly via cleanFormatTokens).
>
> FTS5 was added then removed — Pixel 9a's SQLite lacks the module. Web search fixed (Dispatchers.IO). Context window right-sized to 2048 for sub-4B models. DB is 9 tables, schema v4.
>
> Next priorities: (1) ChatFormat output token abstraction — move from universal regex to per-format patterns, (2) investigate 0.7 tok/s on Qwen 0.8B — should be 10-20x faster, (3) WAL mode, (4) wire embedding repos into MemoryManager for semantic memory, (5) prompt budget optimization (~700 token system prompt eating 35% of context).
>
> Key gotchas: FTS5 unavailable on device, two ChatWithToolsUseCase classes (composeApp uses channelFlow), debug_prompt.txt dump still in LlamaCppEngine (remove after inspection), Kermit logger silent (use println).
