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
| 9 | Avatar (RealityKit) | 🟡 core done, ⌘R pending | `M1K3Avatar` target: 26 tests (emotion/activity/state/tool-map/animation-resolver/controller); `AvatarView` (app target, RealityKit+SF Symbol placeholder); avatar wired into AppEnvironment (listening→thinking→speaking→idle); Sparrow.usdz conversion (Step 0) still needed |
| 10 | Local agent + MCP | 🟢 agent + MCP done | ReAct LocalAgent + AgentTool + search/list/get tools; **M1K3MCP stdio server** (swift-sdk) live — Claude pulls search_knowledge/list_documents/get_document; ⏳ QueryGraphTool (blocked on entity extraction / NER) |
| 11 | GemmaAudio ASR spike | ⬜ not started | non-blocking, LiteRT path |

Legend: ✅ done · 🟢 logic done (deferred adapter) · 🟡 partial · ⬜ not started · ⏳ remaining

---

## Modules (Swift package)

| Target | Deps | Status |
|--------|------|--------|
| `M1K3Knowledge` | GRDB + PDFKit | VectorMath, RRFFusion, EmbeddingService(protocol) + HashingEmbeddingService(fallback), KnowledgeItem/Chunk, KnowledgeStore (FTS5+vector+RRF, fetch/list), KnowledgeGraphBuilder, DocumentChunker, DocumentPage, CitationValidator, PDFTextExtractor, DocumentIngester |
| `M1K3Inference` | — | InferenceProvider, BrainTier, AppleFoundationModelsProvider |
| `M1K3Agent` | M1K3Inference | AgentTool + ToolParameter/ToolResult, LocalAgent (ReAct loop) |
| `M1K3KnowledgeTools` | M1K3Agent + M1K3Knowledge | SearchKnowledgeTool, ListDocumentsTool, GetDocumentTool (⏳ hybrid search variant; QueryGraphTool) |
| `M1K3Chat` | M1K3Knowledge + M1K3Inference | ChatPromptBuilder (in Knowledge) + RAGResponder (embed→hybrid→prompt→answer+sources, streaming) + `RAGResponding` seam + `ChatSession` (@MainActor @Observable, self-normalising token fold) |
| `M1K3MLX` | M1K3Knowledge + M1K3Inference + mlx-swift-lm | ✅ `MLXEmbeddingService` (bge_small, [Float]) + `MLXGemmaProvider` (Gemma 3 1B 4-bit, MLXLLM). Heavy Metal target, isolated. Default embedder NOT nomic (the prior knowledge-server project weight-key gotcha). ⏳ on-device runtime verify |
| `M1K3MCPKit` / `M1K3MCP` | swift-sdk + M1K3Knowledge | ✅ stdio server (library + thin executable) exposing search_knowledge/list_documents/get_document; FTS-only (no embedder in CLI); container-aware store path (`M1K3_STORE_PATH` override). Verified live. |
| `M1K3Voice` | AVFoundation (+ WhisperKit later) | SpeechProvider + SpeechUtterance + AVSpeechProvider; ⏳ TranscriptionProvider (WhisperKit, heavy) |
| `M1K3Calls` | M1K3Knowledge + … | ⏳ CallSession, encrypted SQLite, diarization, summary |
| `M1K3Avatar` | (system frameworks only) | ✅ pure: `AvatarEmotion`/`AvatarActivity`/`AvatarState`/`ToolEmotionMapper`/`AnimationResolver`/`AvatarController` (26 tests). `AvatarView` (RealityKit) lives in app target. ⏳ Step 0: Sparrow.usdz (GLB→USDZ Reality Composer Pro conversion) |
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
`xcodebuild -downloadComponent MetalToolchain` once. The product default
`gemma-3-1b-it-qat-4bit` is NOT commonly cached — first selection used to stall
on a slow download with **no UI feedback**. ✅ **Fixed**: selecting MLX Gemma now
preloads the weights and streams a real download % to Settings + the toolbar (see
the download-UX entry below). Follow-up: prefer an already-cached model when one
is present to skip the first-run download entirely.

**App build:** `cd macos && xcodegen generate && xcodebuild build -scheme M1K3
-destination 'platform=macOS,arch=arm64' CODE_SIGNING_ALLOWED=NO | xcbeautify`.
`project.yml` is the source of truth; the `.xcodeproj` is gitignored + regenerated.

---

## MLX download UX (2026-06-06) ✅

Selecting **MLX Gemma 3** no longer hangs silently while ~1GB of weights stream in.

- **Pure core (`M1K3Inference`):** `ModelLoadState` (`idle → downloading(fraction) →
  ready / failed`, fraction clamped 0...1) + a dyslexia-friendly `label(modelName:)`
  formatter + a `ModelPreloading` seam. 6 new tests in `M1K3InferenceTests` —
  `swift test`-able with zero Metal (147 tests / 29 suites green).
- **Progress threaded through MLX:** `MLXGemmaProvider` gained `prepare(progress:)`
  and forwards `loadContainer`'s `progressHandler`; `MLXEmbeddingService` gained an
  optional `onLoadProgress` (the embeddings switch now shows real download %).
- **App glue:** `AppEnvironment.modelLoad` preloads on picker selection;
  `SettingsView` shows a `ProgressView(value:)` row; `ContentView`'s toolbar shows
  "Downloading Gemma 3… NN%" instead of a dead status dot.
- **Honesty fix:** runtime labels said "Gemma **4**" (doesn't exist) → **Gemma 3**.
- **Verify-by-launch:** the % bar itself is on-device only — confirm at ⌘R with
  uncached weights.

## Voice input — Phase 6 (2026-06-06) ✅ (verify-by-launch)

Tap the mic in the chat bar → live transcript streams into the input bar → tap
again (or the recogniser settles) → auto-sends to chat. Both engines behind one
seam, on-device only.

- **Pure core (`M1K3Voice`, tested):** `TranscriptSegment` (slim — no call-domain
  speaker/sentiment), `TranscriptionProvider` (live-session API), `TranscriptionRouter`
  (an availability-ordered selector), `TranscriptAccumulator` (partial→final fold).
  12 new tests → 159/33 green.
- **`AppleSpeechTranscriber` (`M1K3Voice`):** SFSpeechRecognizer + AVAudioEngine,
  system-framework, zero-dep, **on-device forced** (privacy floor). The day-one path.
- **`WhisperKitProvider` (`M1K3WhisperKit`, isolated target like M1K3MLX):** WhisperKit
  `AudioStreamTranscriber`; unavailable until its model loads, so the router uses
  Apple Speech until you enable WhisperKit in Settings. New dep: `argmaxinc/WhisperKit`.
- **App:** `AppEnvironment` dictation glue (router over [WhisperKit, AppleSpeech],
  auto-send on stop, `enableWhisperKit` download); mic toggle + ticker in `ContentView`;
  Voice section in `SettingsView`. Entitlement `device.audio-input` + mic/speech usage
  strings in `project.yml`.
- **Post-review fix:** a lock-inversion **deadlock** in `AppleSpeechTranscriber.stopListening`
  (NSLock held across `audioEngine.stop()`) — caught by `code-quality-reviewer`, fixed
  (engine ops moved outside the lock). Router's unsound `stopListening()` removed.

## Calls — Phase 7, model-agnostic core (2026-06-06) ✅ (pure, headless)

The reusable IP the challenger pointed at: the **seam**, not the model. `M1K3Calls`
package — transcribe → diarize → align → summarise, every stage behind a protocol,
no engine linked, **18 tests / 4 suites green** (→ 177/37 total).

- **Seams (`CallProviders.swift`):** `BatchTranscriptionProvider` (← WhisperKit-batch ·
  Gemma-4 shadow) + `DiarizationProvider` (← FluidAudio · stereo). File-based.
- **`DiarizationAligner`** (pure, 8 tests): overlap-based speaker attribution — the
  algorithm that makes "transcribe with X, diarize with Y" deterministic + swappable.
- **`SummarizationPipeline`** (pure, 3 tests): two-stage, **error-isolated** quick (AFM)
  + deep (Gemma-as-TEXT — the challenger-blessed safe win) over `InferenceProvider`;
  `CallSummaryParser` (free text → structured `CallSummary`).
- **`CallIntelligencePipeline`** (4 tests): composes the lot against fakes — diarization
  + summary optional + isolated; a finished `CallSession`.
- **Calls → knowledge graph ✅** (the M1K3 twist; 7 tests): `CallChunker` (speaker-grouped,
  summary-leads, char-budget split) + `CallIngester` (mirrors `DocumentIngester`: chunk →
  embed → store a `.call` item, dedupe on the call UUID). **END-TO-END proven on the real
  in-memory store** — an indexed call is found by hybrid search, tagged `.call`, in the same
  index as documents. So RAG / agent tools / MCP answer over calls too.
- **Encrypted persistence ✅** (10 tests): `CallPersistence` seam + `GRDBCallPersistence`
  (mirrors `KnowledgeStore`'s GRDB idiom) + a pluggable `CallSessionCoder` (JSON ↔
  **AES-256-GCM**). Privacy-by-default: only `started_at` is plaintext; title/transcript/
  speakers/summaries live in the encrypted `payload` blob. **Encryption-at-rest VERIFIED** —
  reopen the raw SQLite, scan the bytes, no plaintext (+ a positive control proving the scan
  works, + wrong-key-fails).
- **Keychain key provider ✅** (6 tests): `KeyStore` seam + `StoredKeyProvider` (get-or-create a
  256-bit key, stable across calls + launches, `reset()` to rotate) + `KeychainKeyStore` (Security,
  device-only/after-first-unlock, verify-by-launch). Tested against an in-memory fake; an end-to-end
  test proves the provider's key drives the encrypted round-trip. **The encryption story is now
  closed:** Keychain key → `EncryptedCallCoder` → encrypted-at-rest, all linked + tested.
- **App wiring ✅** (transcript-import path; 5 importer tests, app builds): `AppEnvironment`
  composes the whole stack — `KeychainKeyStore → StoredKeyProvider → EncryptedCallCoder →
  GRDBCallPersistence` + `CallIngester` (same graph) + `SummarizationPipeline` (AFM quick /
  active-runtime deep). `TranscriptImporter` (pure) parses "Speaker: line" text → import a
  transcript → summarised + encrypted + indexed + shown. `CallsView` + `CallDetailView`
  (Liquid Glass) + toolbar button. The headless, no-mic entry point to the full P7 feature.
- **Consent gate + recording capture ✅** (5 consent tests; app builds): `RecordingConsentGate`
  (pure — `.once` / `.remembered` scopes, `ConsentStore` seam, `UserDefaultsConsentStore`, logged
  `ConsentDecision`) so recording is never silent/implicit. `AudioRecorder` seam +
  `MicAudioRecorder` (AVAudioEngine → `.caf`, lock-not-held-across-`engine.stop()`, verify-by-launch).
  App: consent-gated **Record call** toolbar button + confirmation dialog ("you're responsible for
  consent; on-device only") + a red **Recording** indicator. Stop holds the audio file.
- **Engines deferred** (the heavy/device parts): WhisperKit-batch + FluidAudio (the prior call-pipeline lift) — the
  transcribe-on-stop step that turns a recording into a `CallSession` (feeds the SAME pipeline the
  import path proves). Gemma-4-shadow (post-benchmark).

## Deferred buckets (each wants a focused session)

1. **MLX runtime session** — `M1K3Embeddings` (nomic-embed-text-v1.5) + `MLXGemmaProvider` + LiteRT spike + `RuntimeBenchmark`. Heavy first build (`mlx-swift-lm`, MetalToolchain, weight downloads). Wires into the runtime picker (already stubbed in `SettingsView`).
2. ~~**App shell**~~ ✅ done — XcodeGen target, Liquid Glass, chat on AFM.
3. **Heavy-dep features** — WhisperKit transcription (P6), the prior call-pipeline call subsystem (P7), RealityKit avatar (P9), swift-sdk MCP server (P10b).
4. **First *signed sandboxed launch*** — the real milestone the unit tests can't see (challenger's flag). Confirm GRDB writes into the App-Support container, FoundationModels availability under entitlements, AVSpeech under sandbox. Build compiles today; runtime not yet exercised on-device.

---

## Backlog — consolidated TODO (2026-06-06, after a big session)

Status: P6 voice **shipped**; P7 calls built **end-to-end except one link** (consent →
record → ??? → pipeline → encrypt → graph → UI). 210 tests / 44 suites green. 14 feature
commits on `feat/mac-mvp` (PR #9). The pure brain is done; what's left is mostly device + UI.

### 🔴 Closes the mic path (the one missing P7 link)
- [ ] **WhisperKit-batch `BatchTranscriptionProvider`** — `whisperKit.transcribe(audioPath:)` → `[CallTranscriptSegment]`. Compiles headless; runs verify-by-launch.
- [ ] Wire `AppEnvironment.stopRecording()` → transcribe `lastRecordingURL` → the **existing** `CallIntelligencePipeline` → persist + index. (Import path already proves the tail.)
- [ ] **`FluidDiarizationProvider`** (CoreML/ANE, ~17% DER) + stereo fallback behind `DiarizationProvider`. (Device.)

### 🔴 Kev's device session (Tuesday, ⌘R — verify-by-launch)
- [ ] First **signed sandboxed boot** (the real milestone): GRDB in container, FoundationModels under entitlements, AVSpeech + mic under sandbox.
- [ ] Verify **P6 voice** (grant mic + Speech) and **transcript import** end-to-end in the running app.
- [ ] **Gemma-4 E4B diarization-continuity probe** — the `scratch/gemma4-audio-spike/SPIKE.md` kill-test (3–5 min 2-speaker clip, isolated package, eyeball speaker continuity FIRST).
- [ ] On-device **MLX gen+embed** verify (the download-progress bar with uncached weights); **register MCP** per `docs/MCP_SETUP.md`.

### 🟡 Engineering-quality follow-ups (mostly headless)
- [ ] **Citation wiring** — `CitationValidator` → `RAGResponder` (finishes Phase 4; pure TDD, ~30–45 min).
- [ ] **`MLXGemmaProvider.ensureLoaded` → actor** (kill the check-then-act double-load race the reviewer flagged).
- [ ] **MLX preload cancellation** (switch away mid-download) + **cached-default model** (prefer an already-cached model over `gemma-3-1b-qat-4bit`).
- [ ] **`security-auditor` pass** on the call crypto once the Keychain provider is app-integrated (key mgmt is where crypto fails).
- [ ] Tests for the AppleSpeech mic-permission-denied cleanup path (throwing fake) + a self-healing `isListening` timeout (P6 review, low).
- [ ] Persist the recorded `.caf` into the app container (not temp); consider adding an `audioPath` to `CallSession`.

### 🎨 Design & Accessibility — Mac-native pass (full detail in `UI_AUDIT.md`)
**Quick wins DONE (2026-06-07):** icon-only button labels (send · mic · trash · speak),
colour-only status → combined VoiceOver label + `record.circle.fill` symbol, 3 hardcoded font
sizes → Dynamic-Type-safe, `.monospacedDigit()` on counters, hierarchical symbol-rendering pass.
**Deferred (the substance):**
- [ ] **Reduce Transparency** — solid-surface fallback when `accessibilityReduceTransparency` (biggest win for the all-glass UI).
- [ ] **VoiceOver chat structure** — `accessibilityElement(.combine)` + composed labels on `MessageView` turns ("You said…", "M1K3: …, 3 sources").
- [ ] **Reduce Motion** gate on the scroll animation + any `.symbolEffect`.
- [ ] **Increase Contrast** — `.secondary`/`.tertiary` over glass likely <4.5:1; stronger fg + less translucency when `colorSchemeContrast == .increased`.
- [ ] **Dynamic Type** — let fixed sheet frames grow; test at `.accessibility5`.
- [ ] **Live announcements** — `AccessibilityNotification.Announcement` for streaming + status flips.
- [ ] **Hit targets** — mic/send button areas ≥ ~28pt; **focus on present** (move VoiceOver focus to sheet headers, label sheets as modals).
- [ ] **Craft** — tasteful `.symbolEffect` (pulse/variableColor, Reduce-Motion-gated); type-token + symbol-token helpers; SF Pro + SF Symbols in the Figma mockup once the plan is upgraded.
- Verify: VoiceOver (⌘F5) · Xcode Accessibility Inspector · toggle the Accessibility system settings.

### 🟢 Future phases / spikes
- [ ] **Avatar (P9)** — RealityKit emotion-driven avatar + TTS lip-sync.
- [ ] **LiteRT spike (P3)** — now real: `litert-community/gemma-4-12B-it-litert-lm` (text+audio).
- [ ] **GemmaAudio (P11)** — batch shadow `BatchTranscriptionProvider` if the E4B probe wins; then re-plan P7 diarization on one model (challenger pass).
- [ ] **QueryGraphTool** — blocked on entity extraction (NER); graph builder is pure, nothing populates entities.
- [ ] **Kokoro TTS** swap (post-MVP, behind `SpeechProvider`).

### 🧹 Ship hygiene
- [x] Merge **PR #9** (Mac MVP). ✅ 2026-06-06.
- [ ] **Add `SLACK_WEBHOOK_URL` repo secret** → lights up the CI Slack build-status
      list (the `notify` job in `ci.yml` no-ops until it's set). Reuse the
      dyslexia-ai-server webhook (same channel) or make a new one for a dedicated
      `#m1k3-ci`. Set it with:
      `gh secret set SLACK_WEBHOOK_URL -R Round-Tower/m1k3` (paste when prompted).
- [ ] Review + merge **PR #10** (call subsystem + a11y + Slack CI) once ⌘R clears it.
- [ ] ~23G `~/.cache/huggingface` cleanup pass.
