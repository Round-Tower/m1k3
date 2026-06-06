# M1K3 — Mac-Native MVP

## Context

M1K3 today exists as a Python desktop CLI + MCP servers and a Kotlin Multiplatform mobile app (Android shipping, iOS a shell). **There is no Mac-native app.** This plan builds one: a native SwiftUI macOS app that is M1K3's first-class desktop surface — a local, private AI companion with live voice, a knowledge graph, document memory, an embedded agent, and an MCP server so Claude (and other agents) can pull from it.

The strategic unlock from exploration: we are **not** building this from scratch. `the prior knowledge-server project` (`/Users/kevinmurphy/Development/the prior knowledge-server project`) and `the internal call-pipeline project` contain production, eval-gated **Swift** IP for almost every requirement. The MVP is mostly *lift, generalise, and reskin behind a native shell* — not greenfield.

**Key decisions (from Kev, 2026-06-06):**
- **LLM runtime:** keep options open — build **both** an MLX-Swift (Gemma 4) and a **LiteRT-LM (Gemma 4)** backend behind one provider protocol, so we can compare support/capability. **Apple Foundation Models** handles cheap/basic turns. Embeddings stay on MLX-Swift (already proven in the prior knowledge-server project).
- **TTS:** native `AVSpeechSynthesizer` now, behind a protocol; swap in Kokoro post-MVP.
- **Target:** **macOS 26 Tahoe only** → real SwiftUI Liquid Glass + on-device Foundation Models.
- **Avatar:** 3D via **RealityKit/SceneKit**.
- **Call transcription + log (NEW):** M1K3 records/transcribes calls with a searchable, summarised log — exactly like the prior call-pipeline. North star is **one model for all complex work** (ASR + reasoning + summary). Transcription sits behind a **provider protocol** (the prior call-pipeline already has one), WhisperKit/AppleSpeech is the reliable default, and a `GemmaAudioTranscriber` is the spike toward the single-model goal — promote it when it wins on latency/accuracy. *(Superseded in part — see the Gemma 4 update below.)*

---

## Update — 2026-06-06: Gemma 4 shipped (changes the north star from "someday" to "spike now")

**Gemma 4 12B released 2026-06-03** (post-cutoff; web-verified). It is encoder-free multimodal with **native audio** — built-in **speech recognition *and* speaker diarization** — Apache 2.0, on-device. The **edge variants E2B (2B eff.) / E4B (4B eff.)** carry native audio with an encoder **50% smaller than Gemma 3n** and a **40ms frame tuned for low-latency ASR**, sized for phone/laptop memory. Both runtimes M1K3 bet on now support it: **MLX-Swift** (`VincentGourbin/gemma-4-swift-mlx`) and **LiteRT-LM** (`litert-community/gemma-4-12B-it-litert-lm`, text+audio now).

**What this revises in this plan:**
- **"Gemma 4 doesn't exist / single model for ASR is not real yet" → false.** It exists and does ASR + diarization on-device. The inline claims below (and the Risks section) are corrected accordingly.
- **"Diarization stays a separate engine regardless" → no longer a given.** Gemma 4 diarizes natively. This could collapse the P7 calls stack (WhisperKit + FluidAudio + summary LLM) toward **one model** — *pending a benchmark of its diarization quality*.
- **The single-model goal is MVP-grade, not someday.** E4B is purpose-built for low-latency on-device ASR, so it's plausibly viable for **P6 live dictation** too — not just P7 batch. One Gemma 4 E4B could do chat-gen + ASR + diarization + summary, shrinking M1K3's dependency surface (drop WhisperKit + FluidAudio), reusing the MLX runtime + download-UX already built.

**What it does NOT change:** P6 ships **today on WhisperKit + Apple Speech** (committed) — reliable, proven streaming. Gemma 4 audio enters via the `TranscriptionProvider` seam as `GemmaAudioTranscriber` (`isAvailable=false` until it wins a benchmark). **Risk to respect:** days-old; community/v1 Swift audio runtimes; streaming-partial API unproven vs WhisperKit's mature one; unifying concentrates risk in one model. **So: spike + benchmark E4B (latency / accuracy / diarization) before swapping — target E4B, not 12B.** (Spike scaffolding: `scratch/gemma4-audio-spike/`.)

Naming note: the wired MLX *generation* model is `gemma-3-1b-qat-4bit` today; references to "Gemma 4" in the inference tier below are the **upgrade target**, now real.

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

- **`WhisperKitProvider`** (default, reliable) — live streaming, isolated in `M1K3WhisperKit`. Plus `AppleSpeechTranscriber` (on-device `SFSpeechRecognizer`, the day-one path) in `M1K3Voice`; `TranscriptionRouter` selects. **✅ Shipped 2026-06-06 (Phase 6).**
- **`GemmaAudioTranscriber`** (spike → the single-model north star) — **Gemma 4 E4B** native audio via **MLX-Swift** (`gemma-4-swift-mlx`) or **LiteRT-LM**. Same `TranscriptionProvider` seam, so it drops in once it beats WhisperKit on a benchmark. Scaffolded in `scratch/gemma4-audio-spike/`, `isAvailable=false` until proven — nothing blocks on it.
- **TTS:** new `SpeechProvider` protocol → `AVSpeechProvider` (wrap the prior knowledge-server project's `SpeechSynthesizer.swift`) for MVP; `KokoroSpeechProvider` (bridge to m1k3 Python TTS) post-MVP. Avatar lip-sync off TTS amplitude/word callbacks.

### Call transcription & log (lift the prior call-pipeline's call subsystem whole)

M1K3 records and logs calls with searchable, summarised history — the prior call-pipeline already *is* this system. Lift near-verbatim into `M1K3Calls`:

- **Data model:** `CallSession`, `TranscriptSegment`, `QuickSummary`/`CallSummary`, `KeyMoment`, `ReasoningStep`, `SpeakerSegment`/`SpeakerProfile` (`internal-call-pipeline-sources/{Models,Protocols}/`).
- **Persistence:** `SQLiteCallPersistence` + `EncryptedCallPersistence` (AES-256-GCM, Keychain key) — privacy-by-default, matches the M1K3 ethos.
- **Diarization (who-said-what):** `DiarizationRouter` → `FluidDiarizationProvider` (CoreML/ANE) + stereo fallback + `DiarizationAligner`. *Revised 2026-06-06: Gemma 4 diarizes natively, so the dedicated engine is no longer a given — if the E4B spike's diarization quality holds, this whole sub-stack could fold into the one model. Lift the prior call-pipeline's diarization for the MVP, but benchmark Gemma 4 before committing to maintain it.*
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
6. **Transcription layer** — ✅ **shipped 2026-06-06.** `TranscriptionProvider` + `TranscriptionRouter` + `AppleSpeechTranscriber` (M1K3Voice) + `WhisperKitProvider` (M1K3WhisperKit) + mic toggle/ticker, auto-send on stop. *Verify (by launch):* speak → ticker → auto-sends → answer.
7. **Call log** — lift `M1K3Calls` (session model, encrypted SQLite, diarization, two-stage summary, history UI); wire calls → knowledge graph. *Verify:* record a 2-party call → live diarized transcript → AFM quick + Gemma deep summary → logged, searchable, and answerable via RAG.
8. **TTS** — `AVSpeechProvider` behind protocol; spoken responses. *Verify:* answer is spoken.
9. **Avatar** — RealityKit avatar, emotion states, amplitude lip-sync. *Verify:* avatar emotes + mouths the TTS.
10. **Agent + MCP** — `LocalAgent` ReAct loop with tools (incl. calls/knowledge); `M1K3MCP` stdio server. *Verify:* agent answers a multi-step question using tools; Claude Code connects to the MCP server and pulls knowledge + call data.
11. **GemmaAudio spike** (parallel, non-blocking; **elevated 2026-06-06**) — prove **Gemma 4 E4B** native-audio ASR **+ diarization** via MLX-Swift (or LiteRT) in `scratch/gemma4-audio-spike/`; promote to `GemmaAudioTranscriber` behind the seam if it beats WhisperKit on latency/accuracy. If diarization holds, re-plan **Phase 7** around one model (challenger pass first). *Verify:* benchmark table — WhisperKit vs Gemma 4 E4B (live latency, WER, diarization DER).

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
- **mlx-swift-lm LLM generation** — ✅ *de-risked at compile level* (2026-06-06). `MLXLLM` (`ChatSession` + `LLMModelFactory`) is wired in `MLXGemmaProvider` (default Gemma 3 1B QAT-4bit) and the app builds with it linked. ⏳ remaining: **first on-device generation** (download + stream). **Gemma 4 now exists** (shipped 2026-06-03 — see the Gemma 4 update at the top); the wired generation model is still `gemma-3-1b-qat-4bit`, with **Gemma 4 E4B as the upgrade target** (and the unification candidate for ASR + diarization + summary). **Two gotchas locked in:** (1) embeddings default to `.bge_small` not nomic — MLXEmbedders' nomic loader has a weight-key mismatch (the prior knowledge-server project's lesson); (2) `xcodebuild` needs `xcodebuild -downloadComponent MetalToolchain` once (`swift build` doesn't). The MLX backends live in one isolated **`M1K3MLX`** target (both embedder + provider), not the originally-sketched `M1K3Embeddings`.
- **"Single model" for ASR — now real, pending benchmark (revised 2026-06-06).** Gemma 4 E2B/E4B do native audio ASR **+ diarization** on-device via MLX-Swift or LiteRT. The MVP still **ships on WhisperKit + Apple Speech** (proven, low-latency streaming today); Gemma 4 E4B is a **tracked spike behind the `TranscriptionProvider` seam**, not an MVP dependency. Promote it only if it beats WhisperKit on latency/accuracy (and its diarization holds for P7). Risk if we unify: one model gates ASR, chat, and calls — benchmark before betting the stack.
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
