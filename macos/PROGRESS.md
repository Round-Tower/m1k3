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
| 1 | Knowledge core | 🟢 logic done | store + graph; ⏳ MLX embedder deferred |
| 2 | Inference layer | 🟢 logic done | protocol + router + AFM; ⏳ MLX/LiteRT Gemma deferred |
| 3 | LiteRT Gemma spike | ⬜ not started | needs MLX/runtime session |
| 4 | Documents + RAG | 🟡 partial | chunker + citation validator done; ⏳ PDFKit + ingester |
| 5 | Chat UI + Liquid Glass | ⬜ not started | needs Xcode app target |
| 6 | Transcription (pluggable) | ⬜ not started | WhisperKit dep (heavy) |
| 7 | Call log (M1K3Calls) | ⬜ not started | lift the prior call-pipeline call subsystem |
| 8 | TTS (AVSpeech) | ⬜ not started | AVSpeech now, Kokoro later |
| 9 | Avatar (RealityKit) | ⬜ not started | GLB→USDZ, emotion states |
| 10 | Local agent + MCP | 🟢 agent done | ReAct LocalAgent + AgentTool done; ⏳ knowledge tools + MCP server |
| 11 | GemmaAudio ASR spike | ⬜ not started | non-blocking, LiteRT path |

Legend: ✅ done · 🟢 logic done (deferred adapter) · 🟡 partial · ⬜ not started · ⏳ remaining

---

## Modules (Swift package)

| Target | Deps | Status |
|--------|------|--------|
| `M1K3Knowledge` | GRDB | VectorMath, RRFFusion, EmbeddingService(protocol), KnowledgeItem/Chunk, KnowledgeStore (FTS5+vector+RRF), KnowledgeGraphBuilder, DocumentChunker, DocumentPage, CitationValidator |
| `M1K3Inference` | — | InferenceProvider, ProviderRouter, AppleFoundationModelsProvider |
| `M1K3Agent` | M1K3Inference | AgentTool + ToolParameter/ToolResult, LocalAgent (ReAct loop) |
| `M1K3KnowledgeTools` | M1K3Agent + M1K3Knowledge | SearchKnowledgeTool (FTS-backed; ⏳ hybrid w/ embedder) |
| `M1K3Embeddings` | M1K3Knowledge + mlx-swift-lm | ⏳ MLXEmbeddingService (nomic-embed-text-v1.5) |
| `M1K3MCP` | swift-sdk + M1K3Knowledge | ⏳ stdio server |
| `M1K3Voice` | WhisperKit + AVFoundation | ⏳ TranscriptionProvider + SpeechProvider |
| `M1K3Calls` | M1K3Knowledge + … | ⏳ CallSession, encrypted SQLite, diarization, summary |
| `M1K3Avatar` | RealityKit | ⏳ emotion-driven avatar |
| `M1K3App` (Xcode) | all | ⏳ SwiftUI shell, Liquid Glass |

---

## Test count

Run `cd macos && swift test`. Last green: **77 tests, 11 suites** — incl. an
END-TO-END integration (`SearchKnowledgeTool`): LocalAgent → `ACTION:
search_knowledge` → FTS over a real `KnowledgeStore` → concludes from real content.

---

## Deferred buckets (each wants a focused session)

1. **MLX runtime session** — `M1K3Embeddings` (nomic-embed-text-v1.5) + `MLXGemmaProvider` + LiteRT spike + `RuntimeBenchmark`. Heavy first build (`mlx-swift-lm`, MetalToolchain, weight downloads).
2. **App shell** — Xcode SwiftUI macOS 26 target consuming the package; Liquid Glass; chat view on AFM. First runnable window.
3. **Heavy-dep features** — WhisperKit transcription (P6), the prior call-pipeline call subsystem (P7), RealityKit avatar (P9), swift-sdk MCP server (P10b).

---

## Next up

- Finish `M1K3Agent` ReAct `LocalAgent` (this session).
- Then pick a deferred bucket: MLX runtime (turns stack real) · app shell (visible) · keep porting pure logic.
- Push branch `feat/mac-mvp` when ready (local-only so far).
