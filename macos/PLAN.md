# M1K3 — Mac-Native MVP

## Context

M1K3 today exists as a Python desktop CLI + MCP servers and a Kotlin Multiplatform mobile app (Android shipping, iOS a shell). **There is no Mac-native app.** This plan builds one: a native SwiftUI macOS app that is M1K3's first-class desktop surface — a local, private AI companion with live voice, a knowledge graph, document memory, an embedded agent, and an MCP server so Claude (and other agents) can pull from it.

The strategic unlock from exploration: we are **not** building this from scratch. `the prior knowledge-server project` (`$HOME/Development/the prior knowledge-server project`) and `the internal call-pipeline project` contain production, eval-gated **Swift** IP for almost every requirement. The MVP is mostly *lift, generalise, and reskin behind a native shell* — not greenfield.

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
- **The single-model goal is a P7 play, not P6 (corrected after the spike, 2026-06-06).** Reading the real `gemma-4-swift-mlx` source: Gemma 4 audio is **batch, file-based, ≤30s, via a low-level multimodal path — no streaming API**. So it is **NOT viable for P6 live dictation** (WhisperKit stays on the mic button); it fits **P7 batch call transcription**, where one Gemma 4 E4B could transcribe + (prompt-)diarize + summarise a ≤30s window, potentially shrinking the P7 stack. Diarization quality is the unverified deciding metric. See `scratch/gemma4-audio-spike/SPIKE.md`.

**What it does NOT change:** P6 ships **today on WhisperKit + Apple Speech** (committed) — reliable, proven streaming. Gemma 4 audio enters via the `TranscriptionProvider` seam as `GemmaAudioTranscriber` (`isAvailable=false` until it wins a benchmark). **Risk to respect:** days-old; community/v1 Swift audio runtimes; streaming-partial API unproven vs WhisperKit's mature one; unifying concentrates risk in one model. **So: spike + benchmark E4B (latency / accuracy / diarization) before swapping — target E4B, not 12B.** (Spike scaffolding: `scratch/gemma4-audio-spike/`.)

Naming note: the wired MLX *generation* model is `gemma-3-1b-qat-4bit` today; references to "Gemma 4" in the inference tier below are the **upgrade target**, now real.

---

## Update — 2026-06-10: Tool calling shipped (prompt-ReAct) → next: NATIVE tool calling per backend

**Shipped (`feat/agent-tool-calls`, 2026-06-09/10):** every chat turn now runs the `LocalAgent`
ReAct loop with real tools — `web_search` (DuckDuckGo, IA → lite fallback), `fetch_page`
(readable-text extraction), `datetime`, `system_status`, `search_knowledge` — behind a privacy
toggle (web tools not injected when off), with live activity labels, a deterministic
`Web sources:` block, unified-logging diagnostics, and a fallback that synthesises from
gathered observations when the model fumbles the conclusion format. Verified end-to-end
on-device (Boston weather → real forecast links + sources).

**The lesson from running it on two brains:** tool-calling reliability is **per-model dialect**.
AFM (Mini) follows the ReAct text protocol; Gemma-3n (Big) announces tools in prose
("I need to search the web") without emitting `ACTION:` — it was trained on its own native
tool-call format, not ours. Mitigations shipped (format reminder, scaffolding-strip,
repeat-guard, implicit-conclusion, observation-rescue fallback) make ReAct a *workable floor*
on any model — but the ceiling is teaching each runtime its own dialect.

**The native path is real on both wired runtimes (verified in source, 2026-06-10):**
- **MLX:** our pinned `mlx-swift-lm` (2.30.6) ships the *whole* native tool-calling stack, not
  just the input half. **Input:** `ChatSession(model, instructions:, tools: [ToolSpec])` injects
  JSON tool specs via the model's chat template. **Output (the surprise — already built, NOT ours):**
  `MLXLMCommon/Tool/` provides `ToolCallProcessor` (streaming detector), a `GemmaFunctionParser`
  for Gemma's `<start_function_call>call:name{…}<end_function_call>` dialect (+ json/lfm2/xml/glm4/
  kimiK2/minimaxM2 parsers), `ToolCallFormat.infer(from: modelType)` (auto-selects `gemma`), and a
  `Generation` enum whose streaming loop emits `.toolCall(ToolCall)` **inline** alongside `.chunk(text)`.
  So the "output half is ours to build, per model family" framing was wrong: at our current pin the
  library parses Gemma tool-calls into structured `ToolCall`s for us. **No version bump needed — the
  2.x/3.x WhisperKit/swift-transformers clash stays unarmed.** What's left is wiring, not parsing
  (see 12c, revised). *Source-verified probe, 2026-06-10 (continued session).*
- **AFM:** the macOS 26 FoundationModels framework has a native `Tool` protocol —
  `LanguageModelSession` can call Swift tools directly.

<!-- Signed: Kev + claude-opus-4-8, 2026-06-10, Confidence 0.9, Prior: Unknown.
     Source-probe correction: mlx-swift-lm 2.30.6 (git-tag-confirmed on disk) ships the full
     native tool-call OUTPUT stack (ToolCallProcessor + GemmaFunctionParser + Generation.toolCall),
     not just the ChatSession(tools:) input half. 12c is therefore wiring, not parser-building, and
     needs no version bump. Confidence 0.9: API surface read directly from the checkout; the one
     unverified link is whether Gemma-3n-E4B emits the call:name{…} format at runtime (that's 12d). -->


**Why it matters for the product:** M1K3 must work on **any device, agnostic of memory/compute**
— that's what the Mini/Lil/Big brain tiers are for. Tool calling has to scale the same way:
**native dialect where the runtime supports it, prompt-ReAct as the universal fallback** for
runtimes/models with no tool support (and as the proven baseline). One `AgentTool` definition,
three projections: FoundationModels `Tool` (AFM), `ToolSpec` chat-template JSON (MLX),
ReAct text list (floor).

**Design sketch (Phase 12):** a capability refinement on the inference seam —
`protocol ToolCallingProvider: InferenceProvider { func generate(prompt:, tools: [ToolDefinition])
async throws -> ToolTurn }` where `ToolTurn` is `.text(String)` or `.toolCalls([ParsedToolCall])`.
`LocalAgent` detects the capability: native loop (structured calls, no text parsing) when the
active provider conforms, today's ReAct loop otherwise. Same `AgentTool` execution, same
activity/logging/citation pipeline either way — the loop semantics (repeat-guard, iteration cap,
observation rescue) are dialect-independent and stay shared.

---

## Update — 2026-06-12: MCP polish shipped (5 test-report fixes) → async `ask_m1k3` is the next ceiling

**Shipped (TDD, 849 tests / 150 suites green, app builds):** the five findings from the
2026-06-11 live MCP test loop —
- **`ask_m1k3` deadline** (`AsyncTimeout.swift` — `withTimeout`): a runaway/slow generation no
  longer wedges the single-flight lock for minutes. Races the ask against a 120s deadline in a
  task group; on expiry it **cancels the generation** (propagates via the response stream's
  `onTermination` → `turnTask.cancel()` → `LocalAgent`'s per-iteration `checkCancellation`), the
  lock releases, and the caller gets an honest "took too long, try again."
- **`get_voice_status` exposes `answering`** — an in-flight ask is now visible (was reporting
  `in_conversation:false` mid-ask).
- **`get_document`**: explicit "indexed by title only" note for chunkless items (no more bare
  header); **6000-char window + `offset` paging** with a resume footer (was an unbounded firehose).
- **`ask_m1k3` Sources**: deduped + relevance-ranked in `HeadlessAsk` (presentation-only; the
  shared `GroundingGate` is untouched).

**Correction to the record:** the project-memory note "mcp swift-sdk Server processes requests
SERIALLY (0.12.1)" is **inaccurate**. `Server.swift:235` dispatches each request in its own task
("separate task to avoid blocking the receive loop") and `LocalMCPHTTPServer` is one task per
connection with actor reentrancy across the `handleRequest` await — so requests **interleave**.
The `answering` field is genuinely live-pollable mid-ask.

**Next (deferred — the async ceiling for long answers):** the deadline is a *backstop*; it cancels
**legitimately-long** answers at 120s. Two principled upgrades, in scope order:
1. **Async job model (fire-and-forget + poll)** — `ask_m1k3` spawns a detached `Task`, returns a
   ticket immediately (`{"ask_id", "status":"thinking"}`); a new `get_ask_result(id)` tool polls →
   `thinking` / grounded answer / `failed`. The single-flight lock lives on **the job**, not the
   HTTP request, so long answers **complete** instead of being cancelled and no connection is held
   for minutes. Same pattern `speak`'s fire-and-forget already uses. **Cost: two-call ergonomics**
   for the visiting agent — don't pay it pre-emptively; build only if 120s actually bites at ⌘R.
   Interim mitigation: bump the deadline to ~180s.
2. **Streaming transport + MCP progress notifications** — the *one-call-with-liveness* answer. The
   SDK already supports it (`ProgressNotification`, `progressToken` in `CallTool._meta`,
   `Tools.swift:366`), but pushing notifications needs an **open back-channel**; our
   `StatelessHTTPServerTransport` is `Connection: close`, one request per connection
   (`LocalMCPHTTPServer.swift:100`) — nothing to push down. Requires an SSE/streamable-HTTP
   transport. Largest scope; revisit if/when multi-client v2 lands (it needs the same plumbing).

**Empty-answer arc (live test, 2026-06-12):** the small brain (Lil/Qwen3.5-2B) returned
fast-but-empty (`emptyAnswer`) even on trivial asks. Traced to TWO compounding bugs:
- **SHIPPED — the floor:** `HeadlessAsk` no longer throws on an empty turn; it degrades to an honest
  message (`emptyAnswerMessage(didReason:)`) so a visiting agent never sees a bare "Error:
  emptyAnswer". The mechanism is `ReasoningSplit` correctly reducing a think-with-no-conclusion
  stream to "" — common on the 2B, rare on the 9B (same pipeline, weaker model).
- **DEFERRED — the root:** the empty-conclusion fallback (`AgentRAGResponder.streamFallback`) calls
  the **bare** `provider.generateStreaming`, which has no thinking parameter and so uses the
  provider's construction-time `thinkingEnabled: true` (`AppEnvironment.swift:276`) — re-opening
  Qwen's pre-opened `<think>` (`MLXGemmaProvider.swift:243`) **regardless of the turn's fast-mode**.
  So a fast turn's rescue path re-thinks (defeating fast-mode) AND a weak model thinking-without-
  answering manufactures the very empty the fallback exists to prevent. Fix: thread `thinkingEnabled`
  into the fallback generation (the bare `generateStreaming` seam can't carry it today — needs a
  thinking-aware streaming entry, or a no-think provider for the ask path). Touches shared streaming;
  verify-by-launch.

**Also carried from the test loop:** CitationValidator is ALL-CAPS-only (mixed-case titles pass
unvalidated — `HeadlessAskTests` comment); multi-client MCP (Stateful-per-session v2);
`GroundingGate.chunkThreshold` per-query normalisation (the off-topic-but-above-0.62 citation noise
this polish only *presentation*-fixed, not retrieval-fixed).

## Update — 2026-06-13: PR #20 MERGED to master + access-readiness pass (the repo goes invitation-only)

**The convergence the whole branch was building toward landed.** `feat/polish-setlist`
(55 commits, +76k/−15.7k, 235 files — the entire arc since the last master merge:
privacy/sandbox hardening, companions, in-app MCP, memory architecture, voice-first,
embeddings swap, persona) is **merged to master** (merge commit `fba01c5f`, 2026-06-13).
CI green; pr-reviewer verdict MERGE-READY, no blockers.

**Worked P0→P5 one by one (an "access-readiness" sprint — prepping the repo to be shared
invitation-only):**
- **P0 — privacy hygiene (`1a8318f8`):** the repo was tracking **private runtime data** — the
  personal `databases/m1k3_conversations.duckdb(.wal)`, `.m1k3_session.json`, and generated
  test reports (which embed absolute paths). Untracked all 14 (kept on disk) + sealed
  `.gitignore`. Anonymized `/Users/kevinmurphy` across 10 tracked docs → `$M1K3_ROOT`/`$HOME`
  via `scripts/sweep_hardcoded_paths.sh`; two FUNCTIONAL files got real (not placeholder) fixes:
  `.mcp.json` `PYTHONPATH` → `"."` (cwd is repo root — the absolute path was leaky AND unneeded),
  `tests/python/test_tts_content_parsing.py` derives root from `__file__`.
- **P1 — CI de-flake (`c301acc1`):** `AsyncTimeoutTests` "throws TimeoutError, fast" flaked
  (deadline 0.1s, op 10s, but a secondary wall-clock guard demanded `elapsed < 2s`; CI jitter hit
  2.81s — same branch alternated pass/fail). The `#expect(throws:)` already proves the deadline
  fired; the timing line only guards "didn't block 10s". Bound 2s → **5s** (half the op; ~50×
  deadline headroom). Green.
- **P2 — access/legal (`5d1b70df`):** committed the proprietary **LICENSE** + **ACCESS.md** +
  **ACCESS_AGREEMENT.md** (the README claimed "invitation-only" but the enforcing terms were
  untracked); reconciled stray `package.json` license fields (root ISC, unified-suite MIT →
  `"SEE LICENSE IN LICENSE"` + `private:true`).
- **P3 — launch artifacts (`73604bc7` + `fc283cfd`):** `marketing/` growth plan, `assets/`
  (app-icon + labyrinth icons), `make site`; Quaternius (CC0) credit for the Gecko/Inkfish/Colobus
  companions the site ships.
- **P4 — persona exemplar-bleed (`1a57eaa0`):** the 4B parroted the `voiceExemplars` verbatim,
  leaking the literal `USER:` label into greetings — because they were formatted as `USER:/M1K3:`
  chat turns (a pattern the weak model *continues*). Reframed as quoted illustrations
  (`- Asked X: <M1K3 line>`, no turn scaffolding) + a "never print a speaker label" guard; tests
  pinned in lockstep. Behavioural no-bleed is verify-by-feel at ⌘R.
- **P5 — merge** (above).

**Decisions / gotchas (do NOT rediscover):**
- **History scrub DEFERRED to a dedicated post-merge op (Kev's call).** The conversation DB is gone
  from HEAD but still in **git history** — and it was introduced way back in **master's** history
  (`075ce65b`), so a complete scrub is a full-repo `git filter-repo` rewrite of master + ~9 local /
  ~15 remote branches (incl. the unpushed `fix/gemma-embeddings`), then force-push. Doing it mid-sprint
  would orphan in-flight work; the plan is to scrub AFTER #20 is merged (✅) AND `fix/gemma-embeddings`
  lands, then re-clone. **This is the one real privacy item still owed before any invite.**
- **`sweep_hardcoded_paths.sh` used bash-4 `mapfile`** → "command not found" on stock macOS (bash 3.2).
  Rewrote to a `while-read` loop. (Pattern: any committed `.sh` must run on /bin/bash 3.2.)
- **The `.gitignore` global `*.png` trap struck again** — `git add assets/` would have silently dropped
  all 19 icon PNGs. Added `!assets/**/*.png`. (Same family as the `site/**` and `.claude/skills`
  re-include traps; always `git diff --cached --name-only | grep png` after staging image assets.)
- **Merging to master is permission-gated** — the harness blocks `gh pr merge` to the default branch
  without explicit user authorization (correct guardrail; do not work around it with a raw `git push`).

**Carry-forward (P4 tail — still owed, blocked on hardware / Xcode-closed / data tasks):**
- threshold re-tune (`GroundingGate.chunkThreshold`/`memoryThreshold` are bge-era provisionals — owed an
  ABSEP/MEMEVAL re-measure on the new Qwen3 embedder) · file-length extractions
  (`KnowledgeStore+Search.swift` at 511 lines, AppEnvironment, AvatarView — need Xcode-closed xcodegen) ·
  `MLXGemmaProvider→MLXBrainProvider` rename (misnomer; loads Qwen too) · notifications quick-win ·
  CMUdict dict swap (proper TTS fix) · Phase 3 memory (consolidation/recency/contradiction) ·
  malformed citation leak on Lil-4B (`§3 * 10 = 30`) · Mini agent-loop timeout profiling · idle-fan/GPU.
- **Launch infra (Kev's):** point `m1k3.app` at a host, deploy `site/`, 301 the alts; signed `.dmg`
  (blocks Homebrew cask); MCP registry submissions; demo video.

<!-- Signed: Kev + claude-opus-4-8, 2026-06-13, Confidence 0.9 — P0–P5 access-readiness sprint;
     PR #20 merged to master (fba01c5f), CI green, pr-reviewer MERGE-READY; all 7 session commits
     pushed + on master. History scrub correctly deferred (full-repo rewrite, would orphan in-flight
     work). Persona no-bleed is verify-by-feel at ⌘R. Prior: Kev + claude-fable-5 (this file). -->

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

*Update 2026-06-10:* shipped and wired into EVERY chat turn via `AgentRAGResponder`
(retrieve-first grounding preserved, fallback to plain RAG). Tool set grew a self-contained
`M1K3AgentTools` target: `WebSearchTool` (DDG), `FetchPageTool`, `DateTimeTool`,
`SystemStatusTool` — web tools behind a Settings privacy toggle. Tool-call *dialect* is
prompt-ReAct today; native per-backend dialects are Phase 12 (see the 2026-06-10 update).

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
11. **GemmaAudio spike** (parallel, non-blocking; **investigated 2026-06-06** — see `scratch/gemma4-audio-spike/SPIKE.md`). Finding: Gemma 4 audio is **batch ≤30s, no streaming** → a **P7** tool, **not** the P6 mic button. Next: benchmark **Gemma 4 E4B batch** transcription + prompt-diarization vs WhisperKit+FluidAudio on a ≤30s clip; if WER + diarization DER hold, re-plan **Phase 7** around one model (challenger pass first). Prototype in an ISOLATED package (version-conflict risk with our pinned mlx-swift-lm). *Verify:* batch benchmark table — WER, diarization DER, transcribe+summarise-in-one-pass.
12. **Native tool calling per backend** (the multi-model play — see the 2026-06-10 update). One `AgentTool` definition, projected into each runtime's native dialect; prompt-ReAct stays as the universal floor so M1K3 keeps working on any model on any device.
    - **12a — seam:** ✅ **shipped 2026-06-10.** `ToolCallingProvider: InferenceProvider` with `supportsToolCalls` (RUNTIME capability, not just type) + `continueToolTurn(messages: [ToolMessage], tools: [ToolDefinition]) → ToolTurn`. Types: `ToolDefinition`/`ToolParameterDefinition`, `ParsedToolCall` (args `[String: JSONValue]`, typed wire), `ToolTurn` (.text/.toolCalls), `ToolMessage` (.user/.assistant/.toolResult). `LocalAgent.run()` dispatches native-vs-ReAct; the two loops live in `LocalAgent+Native.swift` / `LocalAgent+ReAct.swift` and SHARE the dispatch core (repeat-guard, unknown-tool steering, cap, trace, events) in `LocalAgent.swift`. 13 new tests (no model — fakes). **Challenger-driven design changes from the original sketch:** (1) a typed `[ToolMessage]` transcript, NOT a `prompt: String` — feeding native tool-results back as prose is off-distribution and would churn the seam in 12c (the array maps straight onto mlx's `UserInput(chat:)`); (2) typed `JSONValue` args, flattened to String only at the `AgentTool.execute` edge; (3) `supportsToolCalls` runtime flag defuses the `infer != "gemma"` silent-`.json` trap — a wrapper whose model can't parse reports false → ReAct floor. ReAct path byte-identical (all prior tests green).
    - **12b — AFM adapter:** FoundationModels `Tool` protocol bridge for Mini (likely the cleanest: the framework executes Swift tools natively).
    - **12c — MLX adapter (revised 2026-06-10 after source probe — much smaller than planned):** the
      version question is ANSWERED — our pin **2.30.6 already ships the Gemma output parser** (`ToolCallProcessor`
      + `GemmaFunctionParser`, emitting `.toolCall(ToolCall)` inline from the `Generation` stream). **No bump,
      no WhisperKit clash.** So 12c is no longer "build + test a parser"; it's wiring: (1) map our `AgentTool`
      → mlx `ToolSpec` (the `Tool<Input,Output>` init builds the JSON-schema); (2) point `MLXGemmaProvider` at
      the generation path that yields the `Generation` enum (not the text-collapsing `ChatSession.respond`) so
      we *see* `.toolCall`; (3) set/infer `toolCallFormat = .gemma`; (4) surface `.toolCall` as our `ToolTurn.toolCalls`
      back into the shared `LocalAgent` loop. The parser is the library's; the tests we owe are on the *mapping*.
      **Gotcha (spike-caught):** `ToolCallFormat.infer` matches `model_type == "gemma"` *exactly* — Gemma-3/3n
      fall through to `.json` and silently never parse. Step (3) must set `toolCallFormat = .gemma` **explicitly**,
      not rely on infer. Compile-proven against the pin in `scratch/mlx-toolcall/` (the whole API surface builds);
      the runtime `.toolCall`-fires check is verify-by-launch in the app (MLX needs the app-bundle metallib — a bare
      `swift run`/`swift test` can't init the GPU), which also makes it the natural home for the 12d emission check.
      ✅ **shipped 2026-06-10.** `MLXToolCalling.swift`: pure `MLXToolMapping` (ToolDefinition→ToolSpec, ToolMessage→
      Chat.Message incl. native call-echo, library `ToolCall`→`ParsedToolCall`, `MLXLMCommon.JSONValue`→ours) + the
      `MLXGemmaProvider: ToolCallingProvider` conformance (`continueToolTurn` drives `ModelContainer.generate` →
      `Generation` stream, collecting `.chunk`/`.toolCall`). **Multi-model from day one (Kev's "all brain types"):**
      `resolveToolCallFormat` keys the dialect off the model FAMILY — Gemma→`.gemma`, Qwen/Llama→`.json` — and
      `supportsToolCalls` is true only for recognised families (unknown → ReAct floor). Capability forwarded through
      `SwappableInferenceProvider` + `RuntimeInferenceProvider` so the native loop actually fires in-app for the
      selected MLX brain. 15 new tests on the pure mappers + façade forwarding; `continueToolTurn` (real model) stays
      verify-by-launch. The 12d emission check (does Gemma-3n/Qwen actually emit the format on-device) rides ⌘R.
    - **12d — benchmark:** same tool-task set through native vs ReAct per brain tier (call-format compliance rate, latency, wasted iterations) — promote native per-backend only where it wins, exactly like the transcription seam.
    - *Verify:* Big (Gemma) calls `web_search` natively on the weather question with zero format coaching; Mini does the same via FoundationModels tools; a no-tool-support model still answers via ReAct.

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
- **Tool-call dialect is per-model (learned on-device 2026-06-10).** Prompt-ReAct works as the floor everywhere but compliance varies (AFM follows it; Gemma-3n announces tools in prose — mitigated with format reminders + observation-rescue fallback). Native dialects (Phase 12) raise the ceiling. **Update (source probe, 2026-06-10):** for MLX the per-backend *parsing* surface we feared owning is **already in `mlx-swift-lm` 2.30.6** (`ToolCallProcessor` + `GemmaFunctionParser` + 6 other dialects, emitting structured `.toolCall`) — so 12c is wiring, not parsing, and needs no version bump. The remaining empirical risk is **12d only**: whether Gemma-3n-E4B actually *emits* the `call:name{…}` format on-device (a runtime benchmark). The seam keeps ReAct as the fallback so no model is ever locked out. Benchmark before promoting, same doctrine as transcription.
- **RAG grounding relevance — ✅ SHIPPED 2026-06-10 (`feat/ttft-reasoning-retrieval`).** `GroundingGate` filters retrieve-first injection on vector cosine similarity (NOT rrfScore — rank fusion carries no absolute relevance); `searchHybrid` backfills similarity onto fused hits. Nothing topical → nothing injected, and the prompt tells the model its documents are NOT in context and to call `search_knowledge` (now full hybrid search; the hits it retrieves flow into sources + the citation allow-list via `ToolSourceCollector`). Thresholds (`GroundingGate.chunkThreshold`, 0.45 starting point for bge-small) are logged per-hit (category `responder`) — **tune on real queries at ⌘R, then update the constants.**
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
