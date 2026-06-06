# M1K3 Mac MVP — Implementation Tracker

Living status for the Mac-native MVP. Pairs with:
- **`macos/PLAN.md`** — the *what* and *why* (approved 11-phase plan).
- **`.claude/project-memory.md`** — per-session narrative (decisions, gotchas).

Update this file as phases move. Keep it scannable.

**Toolchain:** Swift 6.3.2 · Xcode 26.5 · target `arm64-apple-macosx26.0` · swift-tools 6.2
**Run tests:** `cd macos && swift test` · **Branch:** `feat/mac-mvp`

---

## Status at a glance

| # | Phase | State | Notes |
|---|-------|-------|-------|
| 0 | Scaffold | ✅ done | SwiftPM multi-module package |
| 1 | Knowledge core | 🟢 logic done | store + graph; `MLXEmbeddingService` (bge_small) wired as a swappable store embedder + `reindexEmbeddings` (safe Hashing↔MLX swap, persisted); ⏳ on-device embed verify |
| 2 | Inference layer | 🟢 mostly done | protocol + router + AFM + **`MLXGemmaProvider`** (Gemma 3, MLXLLM) wired to runtime picker; ⏳ on-device gen verify · LiteRT spike |
| 3 | LiteRT Gemma spike | ⬜ not started | needs MLX/runtime session |
| 4 | Documents + RAG | 🟢 logic done | ingest (chunk/PDF/embed/store) + RAG (embed→hybrid→prompt→answer+sources, streaming); ⏳ citation validation wiring (needs citation-scheme decision) |
| 5 | Chat UI + Liquid Glass | 🟢 shell done | XcodeGen app target; chat→RAG, drop→ingest, speak, settings; real `.glassEffect`. ⏳ voice input (P6) |
| 6 | Transcription (pluggable) | ⬜ not started | WhisperKit dep (heavy) |
| 7 | Call log (M1K3Calls) | ⬜ not started | lift the prior call-pipeline call subsystem |
| 8 | TTS (AVSpeech) | 🟢 done | SpeechProvider + AVSpeechProvider + SpeechUtterance; ⏳ Kokoro swap (post-MVP) |
| 9 | Avatar (RealityKit) | ⬜ not started | GLB→USDZ, emotion states |
| 10 | Local agent + MCP | 🟢 agent + MCP done | ReAct LocalAgent + AgentTool + search/list/get tools; **M1K3MCP stdio server** (swift-sdk) live — Claude pulls search_knowledge/list_documents/get_document; ⏳ QueryGraphTool (blocked on entity extraction / NER) |
| 11 | GemmaAudio ASR spike | ⬜ not started | non-blocking, LiteRT path |

Legend: ✅ done · 🟢 logic done (deferred adapter) · 🟡 partial · ⬜ not started · ⏳ remaining

---

## Modules (Swift package)

| Target | Deps | Status |
|--------|------|--------|
| `M1K3Knowledge` | GRDB + PDFKit | VectorMath, RRFFusion, EmbeddingService(protocol) + HashingEmbeddingService(fallback), KnowledgeItem/Chunk, KnowledgeStore (FTS5+vector+RRF, fetch/list), KnowledgeGraphBuilder, DocumentChunker, DocumentPage, CitationValidator, PDFTextExtractor, DocumentIngester |
| `M1K3Inference` | — | InferenceProvider, ProviderRouter, AppleFoundationModelsProvider |
| `M1K3Agent` | M1K3Inference | AgentTool + ToolParameter/ToolResult, LocalAgent (ReAct loop) |
| `M1K3KnowledgeTools` | M1K3Agent + M1K3Knowledge | SearchKnowledgeTool, ListDocumentsTool, GetDocumentTool (⏳ hybrid search variant; QueryGraphTool) |
| `M1K3Chat` | M1K3Knowledge + M1K3Inference | ChatPromptBuilder (in Knowledge) + RAGResponder (embed→hybrid→prompt→answer+sources, streaming) + `RAGResponding` seam + `ChatSession` (@MainActor @Observable, self-normalising token fold) |
| `M1K3MLX` | M1K3Knowledge + M1K3Inference + mlx-swift-lm | ✅ `MLXEmbeddingService` (bge_small, [Float]) + `MLXGemmaProvider` (Gemma 3 1B 4-bit, MLXLLM). Heavy Metal target, isolated. Default embedder NOT nomic (the prior knowledge-server project weight-key gotcha). ⏳ on-device runtime verify |
| `M1K3MCPKit` / `M1K3MCP` | swift-sdk + M1K3Knowledge | ✅ stdio server (library + thin executable) exposing search_knowledge/list_documents/get_document; FTS-only (no embedder in CLI); container-aware store path (`M1K3_STORE_PATH` override). Verified live. |
| `M1K3Voice` | AVFoundation (+ WhisperKit later) | SpeechProvider + SpeechUtterance + AVSpeechProvider; ⏳ TranscriptionProvider (WhisperKit, heavy) |
| `M1K3Calls` | M1K3Knowledge + … | ⏳ CallSession, encrypted SQLite, diarization, summary |
| `M1K3Avatar` | RealityKit | ⏳ emotion-driven avatar |
| `M1K3App` (Xcode) | all (+ M1K3MLX) | ✅ SwiftUI shell (XcodeGen `project.yml`), Liquid Glass, chat/import/speak/settings; runtime picker hot-swaps AFM ↔ MLX Gemma (`RuntimeInferenceProvider` façade); Settings switches Hashing ↔ MLX **embeddings** (`SwappableEmbeddingService` + persisted reindex); macOS 26, app-sandboxed |

---

## Test count

Run `cd macos && swift test`. Last green: **137 tests, 27 suites** (~70ms).
Highlights: agent→store integration (`SearchKnowledgeTool`), full doc ingest
(PDF→extract→chunk→embed→store→search), the RAG brain (`RAGResponder`:
ask→embed→hybrid→documents-first prompt→grounded answer + sources, streaming),
`ChatSession` (8 tests: turn shape, cumulative+delta token fold, source attach,
error path, blank-input guard), and `M1K3MLX` fast conformance. All fast tests
run on the HashingEmbeddingService fallback — no MLX required.

**⚠️ MLX runtime boundary (2026-06-06, confirmed):** MLX runs **only from an
xcodebuild product** (the `.app`). SwiftPM never compiles mlx-swift's Metal
kernels — there is no `.metallib` anywhere in `.build` — so `swift test` AND
`swift run` both abort with "Failed to load the default metallib". The `.app`'s
headless self-test (`M1K3_SELFTEST=1`, streams to `/tmp/m1k3_selftest.log`) also
needs a **live, unlocked GUI session** to fire its SwiftUI `.task`. So **on-device
MLX (gen + embed) is verified by launching M1K3.app interactively** — `open
M1K3.xcodeproj`, ⌘R, pick MLX, ask. A future xcodebuild command-line-tool target
could verify headlessly (metallib + no GUI). Also: first MLX build needs
`xcodebuild -downloadComponent MetalToolchain` once. And the product default
`gemma-3-1b-it-qat-4bit` is NOT commonly cached — first selection stalls on a
slow download with no UI feedback (tracked gap).

**App build:** `cd macos && xcodegen generate && xcodebuild build -scheme M1K3
-destination 'platform=macOS,arch=arm64' CODE_SIGNING_ALLOWED=NO | xcbeautify`.
`project.yml` is the source of truth; the `.xcodeproj` is gitignored + regenerated.

---

## Deferred buckets (each wants a focused session)

1. **MLX runtime session** — `M1K3Embeddings` (nomic-embed-text-v1.5) + `MLXGemmaProvider` + LiteRT spike + `RuntimeBenchmark`. Heavy first build (`mlx-swift-lm`, MetalToolchain, weight downloads). Wires into the runtime picker (already stubbed in `SettingsView`).
2. ~~**App shell**~~ ✅ done — XcodeGen target, Liquid Glass, chat on AFM.
3. **Heavy-dep features** — WhisperKit transcription (P6), the prior call-pipeline call subsystem (P7), RealityKit avatar (P9), swift-sdk MCP server (P10b).
4. **First *signed sandboxed launch*** — the real milestone the unit tests can't see (challenger's flag). Confirm GRDB writes into the App-Support container, FoundationModels availability under entitlements, AVSpeech under sandbox. Build compiles today; runtime not yet exercised on-device.

---

## Next up

- **Run it on-device.** `xcodegen generate && open M1K3.xcodeproj`, sign with Kev's team, launch. First sandboxed boot is the milestone — drop a PDF, ask, hear it. Watch the three runtime-only risks above.
- Then a deferred bucket: **MLX runtime** (turns the stack real, lights up the runtime picker) is the highest-leverage next move.
- Push branch `feat/mac-mvp` when ready (local-only, ~17 commits so far).
