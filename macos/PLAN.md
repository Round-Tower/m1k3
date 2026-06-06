# M1K3 — Mac-Native MVP

## Context

M1K3 today exists as a Python desktop CLI + MCP servers and a Kotlin Multiplatform mobile app (Android shipping, iOS a shell). **There is no Mac-native app.** This plan builds one: a native SwiftUI macOS app that is M1K3's first-class desktop surface — a local, private AI companion with live voice, a knowledge graph, document memory, an embedded agent, and an MCP server so Claude (and other agents) can pull from it.

The strategic unlock from exploration: we are **not** building this from scratch. `the prior knowledge-server project` (`/Users/kevinmurphy/Development/the prior knowledge-server project`) and `the internal call-pipeline project` contain production, eval-gated **Swift** IP for almost every requirement. The MVP is mostly *lift, generalise, and reskin behind a native shell* — not greenfield.

**Key decisions (from Kev, 2026-06-06):**
- **LLM runtime:** keep options open — build **both** an MLX-Swift (Gemma 4) and a **LiteRT-LM (Gemma 4)** backend behind one provider protocol, so we can compare support/capability. **Apple Foundation Models** handles cheap/basic turns. Embeddings stay on MLX-Swift (already proven in the prior knowledge-server project).
- **TTS:** native `AVSpeechSynthesizer` now, behind a protocol; swap in Kokoro post-MVP.
- **Target:** **macOS 26 Tahoe only** → real SwiftUI Liquid Glass + on-device Foundation Models.
- **Avatar:** 3D via **RealityKit/SceneKit**.
- **Call transcription + log (NEW):** M1K3 records/transcribes calls with a searchable, summarised log — exactly like the prior call-pipeline. North star is **one model for all complex work** (ASR + reasoning + summary). Reality check: the Gemma *text* model can't do ASR; only the **audio-capable multimodal Gemma (3n / "e4b" line)** can, and its mature on-device audio path is **LiteRT/MediaPipe** — so this reinforces the LiteRT bet. Live streaming partials, word timing, and speaker diarization are still better served by purpose-built engines today. Therefore: transcription sits behind a **provider protocol** (the prior call-pipeline already has one), WhisperKit/AppleSpeech is the reliable default, and a `GemmaAudioTranscriber` is a spike toward the single-model goal — promote it when it wins on latency/accuracy.

---

## Architecture

A new SwiftUI app `M1K3.app` (macOS 26), composed of focused local SwiftPM packages. Business logic lives in testable packages (TDD); the app target is a thin shell. Proven files are **vendored** from the internal prior projects into M1K3 packages (not cross-repo path deps — the prior knowledge-server project's core drags in Hummingbird/InternalServerKit we don't need), each carrying a MurphySig review documenting the port.

```
m1k3/macos/                         ← NEW (sibling to app/iosApp)
├── M1K3.xcodeproj                  ← app shell, macOS 26, SwiftUI Liquid Glass
├── Packages/
│   ├── M1K3Inference/              ← provider protocol + 3 backends
│   ├── M1K3Knowledge/              ← embeddings + semantic store + graph + docs
│   ├── M1K3Voice/                  ← transcription protocol + TTS protocol
│   ├── M1K3Calls/                  ← call session model, log, diarization, summary (← the prior call-pipeline)
│   ├── M1K3Agent/                  ← ReAct loop + tools
│   ├── M1K3MCP/                    ← MCP server exposing the knowledge graph
│   └── M1K3Avatar/                 ← RealityKit emotion-driven avatar
└── M1K3App/                        ← SwiftUI views, chat UI, glass styling
```

### The pluggable inference layer (the crux)

Unify on **the prior call-pipeline's `InferenceProvider`** (cleaner than the prior knowledge-server project's `InferenceService` for swap/benchmark):

```swift
public protocol InferenceProvider: Sendable {
    var name: String { get }
    var isAvailable: Bool { get }
    func generate(prompt: String) async throws -> String
    func generateStreaming(prompt: String) -> AsyncStream<String>
}
```

Backends (all conform; router picks by task + availability):
- **`AppleFoundationModelsProvider`** — lift from `internal-call-pipeline-sources/Providers/AppleFoundationModelsProvider.swift`. Cheap/basic turns, `LanguageModelSession` streaming. macOS 26 native.
- **`MLXGemmaProvider`** — Gemma 4 generation via `MLXLLM`/`MLXLMCommon` from `mlx-swift-lm` (same package family the prior knowledge-server project uses for `MLXEmbedders`). Truly in-process, Metal.
- **`LiteRTGemmaProvider`** — Gemma 4 via LiteRT-LM. **Greenfield, highest risk** (no Swift IP, C++ engine). MVP approach: spike in `m1k3/macos/scratch/litert/` first (C-bridge or local sidecar), promote to a real provider only once it generates. Until then it reports `isAvailable = false` and the router skips it — nothing else blocks on it.
- A `RuntimeBenchmark` harness drives the same prompt set through MLX vs LiteRT vs AFM to capture the comparison Kev wants (tokens/sec, latency, quality).

### Knowledge core (lift from the prior knowledge-server project, near-verbatim)

- **Embeddings:** `MLXEmbeddingService.swift` (+ `EmbeddingService`, `EmbeddingWorker`) — `MLXEmbedders`, Metal GPU, `nomic-embed-text-v1.5` (bge_small fallback). API: `embed/embedBatch/isAvailable`.
- **Semantic store / KG:** `SemanticStore.swift`, `SemanticStore+Documents.swift`, `SemanticModels.swift`, `SemanticMigrator.swift`, `VectorMath.swift`, `RRFFusion.swift` — GRDB SQLite, FTS5 + vector cosine, Reciprocal Rank Fusion hybrid search. `KnowledgeGraphBuilder.swift` → nodes/edges.
- **Documents:** `DocumentChunker.swift` (heading-aware), `PDFTextExtractor.swift` (PDFKit), `DocumentStore.swift` (SHA256 dedupe), `CitationValidator.swift` (strips hallucinated cites).
- **RAG prompt:** `ChatPromptBuilder.swift` + `ChatRAGRetriever.swift` — documents-first prompt structuring, provider-agnostic, feeds whichever `InferenceProvider` is active.

### Voice — transcription behind a provider protocol

Reuse **the prior call-pipeline's `TranscriptionProvider` + `TranscriptionRouter`** (`internal-call-pipeline-sources/Transcription/`, `Providers/{WhisperKitProvider,AppleSpeechTranscriber}.swift`) rather than the prior knowledge-server project's single engine — the prior call-pipeline's is already pluggable and gives us the swap/compare seam Kev wants:

- **`WhisperKitProvider`** (default, reliable) — live streaming via `AsyncStream<TranscriptSegment>`, word timing. Plus `AppleSpeechTranscriber` (on-device `SFSpeechRecognizer`) as the lighter live option; router falls back.
- **`GemmaAudioTranscriber`** (spike → the single-model north star) — audio-capable multimodal Gemma (3n/e4b) via the **LiteRT/MediaPipe** audio path. Same `TranscriptionProvider` protocol, so it drops in once it beats Whisper. Starts in `scratch/`, `isAvailable=false` until proven — nothing blocks on it.
- **TTS:** new `SpeechProvider` protocol → `AVSpeechProvider` (wrap the prior knowledge-server project's `SpeechSynthesizer.swift`) for MVP; `KokoroSpeechProvider` (bridge to m1k3 Python TTS) post-MVP. Avatar lip-sync off TTS amplitude/word callbacks.

### Call transcription & log (lift the prior call-pipeline's call subsystem whole)

M1K3 records and logs calls with searchable, summarised history — the prior call-pipeline already *is* this system. Lift near-verbatim into `M1K3Calls`:

- **Data model:** `CallSession`, `TranscriptSegment`, `QuickSummary`/`CallSummary`, `KeyMoment`, `ReasoningStep`, `SpeakerSegment`/`SpeakerProfile` (`internal-call-pipeline-sources/{Models,Protocols}/`).
- **Persistence:** `SQLiteCallPersistence` + `EncryptedCallPersistence` (AES-256-GCM, Keychain key) — privacy-by-default, matches the M1K3 ethos.
- **Diarization (who-said-what):** `DiarizationRouter` → `FluidDiarizationProvider` (CoreML/ANE) + stereo fallback + `DiarizationAligner`. *Note: diarization stays a dedicated engine even after `GemmaAudioTranscriber` lands — a single LLM doesn't separate speakers reliably today.*
- **Summarisation:** `SummarizationPipeline` two-stage — Tier 1 quick summary via **Apple Foundation Models** (<1s), Tier 2 deep analysis (action items, risk flags) via **Gemma 4** through our `InferenceProvider`. This is the "single model for complex work" payoff: Gemma 4 does the reasoning/summary; AFM does the cheap first pass.
- **UI:** `SessionHistoryView`, `SessionDetailView`, `TranscriptView` (speaker-grouped ticker), `SummaryView`, `AgentReasoningView` — reskinned in Liquid Glass.
- **Knowledge integration (the M1K3 twist):** each finished call becomes a node in the **knowledge graph** and its transcript flows into the **semantic store** as documents/observations — so calls are searchable via RAG, the local agent's tools, and the MCP server. This is what makes it M1K3 and not just the prior call pipeline on Mac.

### Local agent + tools

Generalise the prior call-pipeline's `the prior domain ReAct agent` ReAct loop into `LocalAgent` (Thought→Action→Observation, `maxIterations` default 5). Reuse the `AgentTool` protocol verbatim:

```swift
public protocol AgentTool: Sendable {
    var name: String { get }
    var description: String { get }
    var parameters: [ToolParameter] { get }
    func execute(input: [String: String]) async throws -> ToolResult
}
```

MVP tools: `SearchKnowledgeTool`, `QueryGraphTool`, `GetDocumentTool`, `ListDocumentsTool` (all thin wrappers over `M1K3Knowledge`).

### MCP server (so Claude can pull from M1K3)

New `M1K3MCP` package using the official **`modelcontextprotocol/swift-sdk`**, stdio transport. Exposes the same knowledge-tools as MCP tools/resources: `search_knowledge`, `query_graph`, `get_document`, `list_documents`. Registers into Claude Desktop/Code via config. (PriorKnowledgeServer's existing `/v1/graph`, `/v1/documents`, `/v1/search` HTTP routes are the behavioural reference for what each tool returns.)

### Avatar

`M1K3Avatar` — RealityKit `RealityView` hosting one USDZ avatar (convert a model from the THREE.js `web-avatar` set to USDZ). Driven by an `EmotionState` enum (port the `ToolEmotionMap` concept), with idle/breathing/blink + amplitude-driven mouth movement from the TTS stream. SceneKit fallback if RealityKit blendshape control is fiddly.

### UI / Liquid Glass

SwiftUI, macOS 26, native `.glassEffect` / `GlassEffectContainer` for the real Liquid Glass look (replaces the Android `GlassmorphicModifier` — same design language, native API). Chat view: streaming message bubbles, a transcript ticker during voice input, the avatar as a persistent companion pane, a document drawer, and a settings pane to pick the active inference runtime (AFM / MLX / LiteRT) for live comparison.

---

## Build phases (each TDD: red → green → refactor; sign significant decisions with MurphySig)

0. **Scaffold** — Xcode project (macOS 26), empty packages, CI build green. Add deps: `mlx-swift-lm`, `GRDB.swift`, `WhisperKit`, `swift-sdk`.
1. **Knowledge core** — vendor + test embeddings, semantic store, hybrid search, KG builder. *Verify:* ingest text → embed → hybrid-search returns it.
2. **Inference layer** — `InferenceProvider` + `AppleFoundationModelsProvider` + `MLXGemmaProvider` + router. *Verify:* same prompt streams from both; router falls back.
3. **LiteRT spike** — prove Gemma 4 generation via LiteRT-LM in `scratch/`; promote to `LiteRTGemmaProvider` if viable; run `RuntimeBenchmark`. *Verify:* benchmark table MLX vs LiteRT vs AFM.
4. **Documents** — PDF/text import, chunk, embed, store; citation validation. *Verify:* drop a PDF → ask about it → grounded answer with valid citations.
5. **Chat UI + Liquid Glass** — streaming chat, runtime picker, glass styling. *Verify:* end-to-end RAG chat in the app.
6. **Transcription layer** — `TranscriptionProvider` + WhisperKit/AppleSpeech + router into chat voice input. *Verify:* speak → transcript ticker → answer.
7. **Call log** — lift `M1K3Calls` (session model, encrypted SQLite, diarization, two-stage summary, history UI); wire calls → knowledge graph. *Verify:* record a 2-party call → live diarized transcript → AFM quick + Gemma deep summary → logged, searchable, and answerable via RAG.
8. **TTS** — `AVSpeechProvider` behind protocol; spoken responses. *Verify:* answer is spoken.
9. **Avatar** — RealityKit avatar, emotion states, amplitude lip-sync. *Verify:* avatar emotes + mouths the TTS.
10. **Agent + MCP** — `LocalAgent` ReAct loop with tools (incl. calls/knowledge); `M1K3MCP` stdio server. *Verify:* agent answers a multi-step question using tools; Claude Code connects to the MCP server and pulls knowledge + call data.
11. **GemmaAudio spike** (parallel, non-blocking) — prove Gemma-3n-audio ASR via LiteRT in `scratch/`; promote to `GemmaAudioTranscriber` if it beats WhisperKit on latency/accuracy. *Verify:* transcription benchmark Whisper vs Gemma-audio.

---

## Files to reuse (vendor + MurphySig review)

| Capability | Source (read-only IP) |
|---|---|
| Inference protocol, AFM provider, ReAct loop, AgentTool | `the internal call-pipeline project/Sources/the internal call-pipeline core/{Providers/AppleFoundationModelsProvider,Agent/the prior domain ReAct agent}.swift` |
| Embeddings (MLX) | `the prior knowledge-server project/PriorKnowledgeServerServer/Sources/the internal knowledge-server core/{MLXEmbeddingService,EmbeddingService,EmbeddingWorker}.swift` |
| Semantic store + hybrid search + KG | `the prior knowledge-server project/.../the internal knowledge-server core/{SemanticStore,SemanticStore+Documents,SemanticModels,SemanticMigrator,VectorMath,RRFFusion,KnowledgeGraphBuilder}.swift` |
| Documents + RAG prompt | `the prior knowledge-server project/.../the internal knowledge-server core/{DocumentChunker,PDFTextExtractor,DocumentStore,CitationValidator,ChatPromptBuilder,ChatRAGRetriever}.swift` |
| Transcription (pluggable) | `internal-call-pipeline-sources/Transcription/TranscriptionRouter.swift`, `Providers/{WhisperKitProvider,AppleSpeechTranscriber}.swift` |
| Call log: model + persistence | `internal-call-pipeline-sources/{Models/{TranscriptSegment,Summary,ReasoningStep,SpeakerModels,SpeakerGroup,KeyMoment},Protocols/CallPersistence,Persistence/{SQLiteCallPersistence,EncryptedCallPersistence}}.swift` |
| Call log: diarization + summary + UI | `internal-call-pipeline-sources/{Diarization/*,Summarization/SummarizationPipeline,Views/{TranscriptView,SummaryView,History/*,Agent/AgentReasoningView}}.swift` |
| TTS seed | `the prior knowledge-server project/.../the internal knowledge-server core/SpeechSynthesizer.swift` |
| Avatar model + emotion mapping | `m1k3/src/web-avatar/` (GLB→USDZ), `m1k3/app/.../avatar/ToolEmotionMap.kt` (concept) |

**Provenance:** vendored files keep their original MurphySig and gain a review block documenting the macOS port. New files get `Signed: Kev + claude-opus-4-8, 2026-06-06, Prior: Unknown` per the no-fabrication rule.

---

## Risks / open items

- **LiteRT on macOS/Swift is unproven** — no official Swift bindings; the C++ LiteRT-LM engine likely needs a C-shim or sidecar. De-risked by phasing it as a spike behind the protocol (MLX is the reliable Gemma path; LiteRT can fail without blocking the MVP).
- **mlx-swift-lm LLM generation** — ✅ *de-risked at compile level* (2026-06-06). `MLXLLM` (`ChatSession` + `LLMModelFactory`) is wired in `MLXGemmaProvider` (default Gemma 3 1B QAT-4bit) and the app builds with it linked. ⏳ remaining: **first on-device generation** (download + stream). Gemma *4* doesn't exist in the registry — Gemma 3 / 3n are latest; "Gemma 4" in this plan reads as "the MLX Gemma tier". **Two gotchas locked in:** (1) embeddings default to `.bge_small` not nomic — MLXEmbedders' nomic loader has a weight-key mismatch (the prior knowledge-server project's lesson); (2) `xcodebuild` needs `xcodebuild -downloadComponent MetalToolchain` once (`swift build` doesn't). The MLX backends live in one isolated **`M1K3MLX`** target (both embedder + provider), not the originally-sketched `M1K3Embeddings`.
- **"Single model" for ASR is not yet real for the *text* Gemma** — only the audio-multimodal Gemma (3n/e4b) can transcribe, and only via an immature on-device audio runtime (LiteRT path). Diarization stays a separate engine regardless. The MVP therefore ships on WhisperKit + FluidAudio; the single-model goal is a tracked spike, not an MVP dependency.
- **macOS 26-only** narrows the test surface to Tahoe machines — acceptable for a personal MVP, revisit before any wider release.
- **Call recording legal/consent** — recording calls has consent obligations; the app must make recording explicit and consented (one of M1K3's privacy principles). Surface a clear recording indicator + consent gate.
- **Placement** — assumes `m1k3/macos/`. If you'd rather it be its own repo, that's a one-line change to the scaffold.

## Verification (end-to-end)

1. Launch `M1K3.app`. Drop in a PDF → it ingests + embeds.
2. Speak a question about the PDF → WhisperKit transcribes → RAG retrieves → active runtime (AFM/MLX/LiteRT, selectable) streams a grounded, citation-valid answer → AVSpeech speaks it → avatar emotes + lip-syncs.
3. Record a 2-party call → live diarized transcript ticker → on end, AFM quick summary + Gemma 4 deep analysis (action items/risk flags) → call appears in history, searchable, and answerable in chat via RAG.
4. Ask a multi-step question → `LocalAgent` runs the ReAct loop using `SearchKnowledgeTool`/`QueryGraphTool` (incl. call data).
5. From Claude Code, connect to the `M1K3MCP` stdio server → call `search_knowledge` → confirm it returns documents **and** logged calls from M1K3's graph.
6. Open the runtime picker, run `RuntimeBenchmark` → review the MLX vs LiteRT vs AFM comparison (and Whisper vs Gemma-audio if the spike landed).
7. `swift test` green across all packages; no new warnings.
