# PLAN_IOS.md — 間 AI on iOS & macOS

> "Your device is the cloud." On Apple silicon, that line finally pays full rent:
> three on-device brains, Metal-accelerated, OS-embedded, glass-clad, voice-native.

**Status:** Draft · 2026-04-19 · Kev + Claude · Companion to [ARCHITECTURE.md](ARCHITECTURE.md)
**Supersedes:** the "iOS is parked" note in `PlatformModule.ios.kt:11-19`
**Scope:** iOS 18+ primary / iOS 26 SOTA features / iPadOS 26 / macOS 26 Tahoe / watchOS stretch

---

## 0. TL;DR

間 AI today is an Android-only privacy-first on-device assistant with a domain-first
Kotlin Multiplatform core (103 files in `commonMain`, 118 in `androidMain`, **8** in
`iosMain`). The iOS skeleton compiles and loads Compose Multiplatform; nothing runs
behind it. This plan moves iOS from placeholder to first-class — and uses Apple's
specific strengths (Foundation Models, MLX, Liquid Glass, App Intents, Visual
Intelligence, PersonalVoice, Apple Pencil) to make the Apple build *better* than
the Android one in the places where Apple leads.

Non-goals: feature parity in the small stuff. If a capability is strictly better
expressed as a Mac/iOS-native feature (e.g. Siri vs. chat-only prompt), we lean in.

**Nine phases + cross-cutting concerns, ~14–18 weeks of focused work**, with Phase 0
already scoped and revert-safe (see `PR 1 — "Hello, llama.cpp on iOS"` conversation
notes).

---

## 1. Why iOS, Why Now

Three forces converged in 2025-2026:

1. **Apple Intelligence is now extensible.** `FoundationModels` framework (iOS 26)
   exposes a 3B on-device LLM with guided generation, tool calling, and structured
   outputs as a public API. We get a second free brain per device, sandboxed to
   the Secure Enclave-protected model runtime.

2. **MLX hit production.** MLX-Swift is stable, has GGUF-compatible quantizations,
   runs Qwen/Gemma/Llama at Metal speeds, and is what Apple themselves ship. iPhone
   15 Pro+ has the unified memory bandwidth to run a 3B model at 30+ tok/s — we
   already struggle to hit 10 tok/s on Pixel 9a CPU.

3. **Liquid Glass makes it feel alive.** iOS/macOS 26's new design language (glass
   materials, real-time specular, morphing transitions) is a natural fit for a
   private assistant. The dot-matrix hero behind a glass sheet reads as *heirloom
   object* instead of *app chrome*.

Apple's moat here is **integration**: Shortcuts, Siri, Writing Tools, Visual
Intelligence, Focus filters, StandBy, Watch, Spotlight, Control Center, Share
Extensions, Apple Pencil. 間 AI is uniquely positioned because it already
owns a rich Tool system in `domain/tools/` — those Tools were born portable and
map one-to-one to `AppIntent`s.

The competitive pitch becomes: **"The only AI assistant that actually lives inside
iOS, that doesn't call anyone's server, and where your notes are the memory."**

---

## 2. Three-Brain Architecture

The Android app uses one inference backend (llama.cpp via `Ma`). iOS gets three,
chosen by the `ModelRouter` per-query:

| Brain | Backend | When | Why |
|---|---|---|---|
| **Apple** | `FoundationModels` (iOS 26+) | Quick classification, JSON-guided generation, lightweight tool orchestration, summarization | Free compute, 3B quality, Apple handles updates, integrates with Writing Tools + Siri |
| **MLX** | `MLX-Swift` | Chat, longer reasoning, tool calls requiring our persona, RAG over passages | Metal-accelerated, our choice of model, our persona prompts, our grammar-constrained tool calling |
| **Ma** | `llama.cpp` via cinterop | Fallback on iOS <26 / older hardware (A12–A15), parity smoke tests | Same binary behaviour as Android; proves the core path works regardless of Apple stack changes |

All three sit behind `BaseLlmEngine` (already in `commonMain`). The router adds
no new interface — it's a `BaseLlmEngine` that dispatches.

```
           ┌──────────────────────────────────────────────┐
           │ commonMain: BaseLlmEngine                    │
           └──────────────────────────────────────────────┘
                      ▲           ▲            ▲
                      │           │            │
       ┌──────────────┘   ┌───────┘    ┌───────┘
       │                  │            │
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│ AppleFMEngine│   │ MLXEngine    │   │ MaEngine     │
│ (iosMain)    │   │ (iosMain)    │   │ (iosMain)    │
│              │   │              │   │              │
│ SystemLang-  │   │ MLX-Swift    │   │ Kotlin/Native│
│ uageModel    │   │ via Swift    │   │ cinterop →   │
│              │   │ bridge       │   │ ma_core.{a}  │
└──────────────┘   └──────────────┘   └──────────────┘
```

**Routing policy (v1):** query intent → classifier (Apple FM, 30ms) → backend.
Intent buckets:

- `structured_extract` / `classify` / `summarize_short` → **Apple FM** with schema
- `chat` / `reason` / `tool_call` / `rag_answer` → **MLX** (our persona)
- `fallback` / `ios<26` / `simulator` → **Ma**

The router is a commonMain class that takes a `List<BaseLlmEngine>` and a policy
function. Testable with fake engines.

---

## 3. Principles (carry forward)

1. **Domain-first.** Every new capability that isn't Apple-framework-bound lives
   in `commonMain`. iOS-specific code is an adapter, not a re-implementation.
2. **TDD.** Pre-commit hook already blocks untested source outside `scratch/`.
   No bypass with `TDD_SKIP=1`. Write the thin test.
3. **Privacy is verifiable, not claimed.** No telemetry, no analytics, no cloud
   inference. Build-time `ManifestPrivacyTest` equivalent on iOS: an `IpaMetadataAudit`
   that walks `Info.plist` + SPM/Pods manifest and fails if banned SDKs appear.
4. **The device is the cloud.** User-initiated network only (model downloads,
   web search). Mirrors ADR-0006. Apple's Private Cloud Compute stays **off** for
   Foundation Models — we configure `SystemLanguageModel` with the `.onDeviceOnly`
   option. If Apple ever removes that option, we drop back to MLX.
5. **Sign decisions with MurphySig.** Architecture choices get ADRs; non-trivial
   Swift-interop gotchas get memory entries.
6. **Accessibility is not a phase, it's an invariant.** Every exit criterion
   includes a11y checks. Dyslexia-friendly by default (generous line-height,
   no justified text, audio-first mode genuinely audio-first). `isReduceMotion`
   disables glass specular + dot-matrix blink. VoiceOver labels every affordance.
   Kev-built tools for neurodivergent users can't afford to be clumsy for them.

---

## 4. Phase Plan

Phases are sequenced for dependencies and de-risking — not parallel. Each phase
is a week or two of focused work and ends in a shippable TestFlight build (or an
internal tag if not user-visible).

### Phase 0 — Inference Spike (Path B) · 1–2 days

**Goal:** prove llama.cpp runs on iOS simulator from Kotlin code.

**Exit criteria:**
- `MaInferenceBackend` moved to `commonMain`
- `ma_bridge.cpp` split into portable `ma_core.{cpp,h}` + `ma_bridge_jni.cpp`
- llama.cpp submodule relocated to `composeApp/src/nativeShared/llama.cpp`
- Android builds green against the new path (no behaviour change)
- `libma_core.a` + `libllama.a` + `libggml*.a` cross-compile for iosArm64 and
  iosSimulatorArm64 via CMake iOS toolchain
- Kotlin/Native cinterop `ma.def` binds to `ma_core.h`
- `MaNativeBridge.ios.kt` implements `MaInferenceBackend`
- `iosTest/MaNativeBridgeSmokeTest.kt` loads Qwen3-0.6B Q4_K_M, generates 10
  tokens, releases. Green on iOS simulator

**Out of scope:** streaming callbacks, `generateChat`, UI, everything else.

**See also:** conversation notes 2026-04-19 for step-by-step, traps, timing.

### Phase 1 — Core Parity · 1 week

**Goal:** every non-UI `commonMain` dependency has an iOS `actual`. Fake data
can drive a full chat loop on iOS simulator.

**Work:**

| Gap | Solution |
|---|---|
| `SQLCipher` encryption | Add `net.zetetic.SQLCipher` via CocoaPods; swap `NativeSqliteDriver` for `NativeSqliteDriver` with `SupportSQLiteOpenHelper.Factory`-style pragma injection. Parity test: `SqlCipherEncryptionTestIos` mirrors the Android trip-wire. ADR-0007. |
| `EmbeddingEngine` | `onnxruntime-objc` Pod; port `MiniLmEmbeddingEngine` byte-for-byte (the ONNX model and tokenizer are the same files). Gemma embeddings similarly. |
| `PassageEmbedder` → `EngineBackedPassageEmbedder` | Already commonMain-safe; wires on iOS once `EmbeddingEngine` actual exists. |
| `HttpModelDownloadManager` + WorkManager `ModelDownloadWorker` | `URLSession` background configuration + `BGProcessingTask`. iOS version does not need `WorkManager`; survives OS suspension via background URLSession. |
| Passphrase storage | **Keychain** with `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`. Optional `LAContext` biometric gating for sensitive tool invocations (health, future payments). Parity with Android keystore-wrapped approach. |
| Model file storage | Models land in App Support directory, then `NSURLIsExcludedFromBackupKey = true` — otherwise users lose 5GB iCloud quota to our 2GB GGUFs. Passages DB lives in the App Group container so Share Extension and widgets can read non-sensitive metadata without duplicating it. |
| `DateTimeProvider`, `DeviceInfoProvider`, `PreferencesStore` | Trivial actuals. `NSUserDefaults` for preferences. |
| `NotificationListenerService` equivalent | **Not possible on iOS.** Drop gracefully — users get tool adapters that work (timer, flashlight, etc.), Android-only tools return `ToolUnavailable`. |

**Exit criteria:**
- All `commonMain` services have iOS actuals or documented `ToolUnavailable`
- Encrypted DB trip-wire green on iOS simulator
- Model download sets the backup-exclusion flag; verified by a post-download
  `URLResourceValues` read in test
- Keychain round-trip of passphrase survives app reinstall (with biometric gate
  optional, off by default)
- VoiceOver reads the chat transcript in order; Dynamic Type at XXXL doesn't
  break layout
- Can import a note via `UIDocumentPickerViewController` → chat retrieves it via
  passage embeddings
- Smoke test: "open app, import 3 notes, ask question answerable from them,
  chat streams answer" — Ma backend only, no Apple FM / MLX yet

### Phase 2 — Voice (TTS + STT) · 1 week

iOS voice is genuinely easier than Android; it's also where Apple's stack is
nicer than anything we could build.

**TTS layers (ordered by preference):**

1. **PersonalVoice** (iOS 17+) — if the user has recorded one, use it. Requires
   `com.apple.developer.speech.personal-voice` entitlement and explicit user
   consent via `AVSpeechSynthesisVoice.requestPersonalVoiceAuthorization`.
   This is the killer demo: "間 AI sounds like the user's grandmother, who
   recorded PersonalVoice before she passed."
2. **Premium / Enhanced AVSpeech voices** — user can download system voices
   (Ava, Evan, etc.). Default path.
3. **Kokoro ONNX** (parity with Android) — bundled fallback. Ensures consistent
   voice across platforms if the user wants it. `onnxruntime-objc` handles this.

**STT:**

- Primary: `SFSpeechRecognizer` with `requiresOnDeviceRecognition = true`. iOS 13+
  supports fully offline STT for English, more languages added yearly.
- Mic button in `ChatInputBar` already exists (commonMain); iOS implements the
  `expect class VoiceRecognizer` actual.
- Live transcription visible in the input field as the user speaks (the way
  Apple's Dictation works). This is already the UX Kev liked on Android.

**Extras (cheap wins):**

- Spatial audio rendering for TTS (`AVAudioEnvironmentNode`) — makes M1K3's voice
  "come from the screen" on AirPods with head tracking. Subtle, delightful.
- Voice activity detection via `AVAudioSession` silence detection to auto-stop
  recording. Feels magical after tap-to-talk.

**Exit criteria:**
- Tap mic → see live transcript → model responds with voice
- PersonalVoice works end-to-end on a device that has one enrolled
- Audio-first mode genuinely audio-first: no peek at transcript, VoiceOver-
  compatible, works in screen-off state
- Android TTS/STT unchanged

### Phase 3 — Foundation Models Integration · 1 week

Apple's `FoundationModels` framework is iOS/macOS 26-only. The API surface we
need:

```swift
import FoundationModels

let session = LanguageModelSession(model: .default) {
    SystemInstructions("You are 間 AI, a dry, sharp, on-device companion...")
}

let response = try await session.respond(
    to: userMessage,
    generating: AnswerSchema.self  // @Generable Swift struct
)
```

**Integration strategy:**

- New `AppleFoundationModelsEngine : BaseLlmEngine` in `iosMain`.
- Bridges to Swift via a `@_cdecl` shim — Swift can't be called from Kotlin/Native
  directly, but we can expose a C-ABI entrypoint from Swift and bind via cinterop.
  Alternative: expose via `MainViewControllerKt` and let SwiftUI own the session,
  with async callbacks into Kotlin.
- **Structured outputs.** Our `ToolCallGrammarBuilder` already produces a schema;
  translate it to `@Generable` / `GenerationSchema` once — Apple FM enforces the
  schema at the decoder level, same guarantee as llama.cpp's lazy GBNF.
- **Privacy flag:** always pass `.onDeviceOnly` to prevent Private Cloud Compute
  dispatch. Lock this in a wrapper; never expose the raw Apple API.

**Routing:**

- Intent classifier (`QueryIntentClassifier`) runs on Apple FM — single-digit-ms,
  free compute. The result tells us which engine to send the actual request to.
- "Simple" structured queries ("Extract the event time from this message") stay
  on Apple FM — quality is excellent and we don't need our persona.
- "Chat" queries go to MLX (Phase 4) or Ma (fallback).

**Writing Tools extensibility (iOS 18.2+):**

- Register 間 AI as a Writing Tools provider so users can invoke it from any
  text field. "Ask 間 AI" becomes a system-wide action.
- `AppIntents`-based registration via `TextSuggestionIntent`.

**Exit criteria:**
- Apple FM answers "Extract event from this email" via `@Generable` schema,
  returns typed Kotlin data class in commonMain
- Router picks Apple FM for classification tasks, MLX/Ma for chat
- Device <iOS 26 silently falls through to MLX/Ma only; no crash

### Phase 4 — MLX-Swift Fast Path · 2 weeks

**Goal:** Metal-accelerated inference of our own Qwen/Gemma models. Target 30+
tok/s on iPhone 15 Pro+ for a 1.7B model.

**Approach:**

- MLX-Swift SPM package exposed to Kotlin via the same Swift-interop pattern as
  Foundation Models.
- Model conversion: we already ship GGUF; MLX uses `.safetensors`-style NPZ.
  The `mlx_lm.convert` pipeline runs on desktop — we produce MLX artifacts
  alongside our GGUFs at build/release time.
- Storage: MLX models are larger on disk than their GGUF Q4 equivalents but
  decode faster. Ship one per tier, signed, verified by hash on download.
- Grammar-constrained tool calling: MLX-LM has a `logits_processor` hook; port
  our GBNF → logits-mask path. Alternative: Apple FM's `@Generable` for simple
  cases, MLX free-form for chat, and use the `LlmOutputSanitizer` (already in
  commonMain) to strip any malformed tool-call XML.
- Streaming: MLX-Swift's `generate_step` is a Swift `AsyncStream` — bridge to
  Kotlin Flow via `callbackFlow`.

**Model tiers on iOS (MLX-first):**

| Tier | Model | Disk | Perf (est.) on iPhone 15 Pro |
|---|---|---|---|
| Mini | Qwen3-0.6B MLX 4-bit | ~500MB | 60+ tok/s |
| Lil | Qwen3-1.7B MLX 4-bit | ~1.1GB | 30-40 tok/s |
| Big | Gemma 4 E2B MLX 4-bit | ~2.1GB | 15-20 tok/s |

**Exit criteria:**
- MLX backend generates at ≥3x Ma-on-iOS baseline for the same model
- Same grammar-constrained tool calling works (either via logits mask or
  sanitizer + prompt)
- Model download pipeline fetches MLX artifacts from our HF mirror
- Benchmark harness published: `iosTest/MlxBenchmark.kt` runs 100-token
  generations across tiers, logs tok/s

### Phase 5 — OS Embedding · 2 weeks

This is where 間 AI stops being "an app" and starts being "a resident of your
phone." Each surface is a separate mini-feature, but they all draw from the same
`domain/tools/` registry and `AppleFoundationModelsEngine` for fast classification.

#### 5.1 App Intents

Every tool in `domain/tools/services/ToolRegistry` becomes an `AppIntent`:

```swift
struct AskM1K3Intent: AppIntent {
    static let title: LocalizedStringResource = "Ask 間 AI"
    @Parameter(title: "Your question") var question: String
    static var openAppWhenRun: Bool = false

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let reply = try await M1K3Router.shared.respond(to: question)
        return .result(dialog: IntentDialog(reply))
    }
}
```

Consequences, free, all via one registration:

- **Siri**: "Hey Siri, ask 間 AI what's on my calendar today"
- **Shortcuts**: Users build workflows with M1K3 as a step
- **Spotlight**: Type a question in Spotlight, get M1K3 answer inline
- **Control Center**: Pin M1K3 to a Control Center slot (iOS 18+)
- **Action Button** (iPhone 15 Pro+): Long-press action → M1K3

#### 5.2 Widgets

- **Home Screen widget (sm/md/lg):** last conversation preview + quick-ask button.
- **Lock Screen widget:** a dot-matrix face that breathes. Tap to open mic.
- **StandBy widget:** ambient persona display — the dot-matrix hero at full scale,
  idling, blinking. The face becomes an ornament on a docked phone.
- **Interactive widget (iOS 17+):** quick-reply buttons without opening app.

Built in `widgetkit/` Swift target, reads from App Group container (`group.app.m1k3.shared`)
for conversation state. No KMP code in widgets — they're pure SwiftUI leaves.

#### 5.3 Live Activities + Dynamic Island

When a long operation is running (model download, RAG over large corpus, TTS
synthesis), surface it as a Live Activity:

- Download: "Lil M1K3 · 34% · 2m remaining" in Dynamic Island
- Active conversation: a tiny pulsing dot-matrix face in the Island
- Speech synthesis in progress: voice icon + level meter

#### 5.4 Visual Intelligence (iOS 26+)

iPhone 16+ has a Camera Control button that launches Visual Intelligence.
Register 間 AI as a *visual response provider*: point camera at a thing, press
hold, 間 AI answers in our voice/persona instead of Google/ChatGPT. This is
gigantic for on-device differentiation.

Requires `VisualIntelligenceQueryIntent` conformance (Apple's new schema).

#### 5.5 Focus Filters

Adjust 間 AI's persona and tool set based on the user's Focus mode:

- **Sleep Focus:** quiet voice, no notifications, reduced-brightness UI
- **Work Focus:** calendar/email tools prioritised, concise answers
- **Personal Focus:** relaxed persona, full tool set

Implemented via `SetFocusFilterIntent`.

#### 5.6 Share Extension

"Send to 間 AI" from anywhere: URL, text, image, PDF. Handler ingests content
into the Passage pipeline automatically. Closes the "my corpus" loop without
making the user open the app.

#### 5.7 Spotlight Donation

Donate `CSSearchableItem`s for each chat — users can search past conversations
from Spotlight. Privacy-safe because it's local index only.

**Exit criteria:**
- 5 tools exposed as AppIntents (time, battery, flashlight, web search,
  ask-question) — callable from Siri
- Widget + Lock Screen widget ship in TestFlight
- Visual Intelligence handler registered (device-dependent smoke test)
- Share Extension ingests text/PDF into passages

### Phase 6 — Liquid Glass Polish · 1 week

Liquid Glass (iOS 26) is SwiftUI-native; Compose Multiplatform doesn't yet have
first-class bindings. Two paths:

**Path A (preferred):** selective SwiftUI chrome. The chat hero and settings screen
stay Compose (design system consistency with Android). The *system chrome* —
navigation bar, toolbar, share sheet, keyboard accessory — uses SwiftUI with
`.glassEffect(.regular, in: .capsule)` etc. Compose content shows through
like any other content layer.

**Path B:** wait for JetBrains to ship `compose.glass` modifiers. Unknown ETA.
Don't block on this.

**Opportunities:**

- **Dot-matrix hero behind a glass sheet.** The orange pixels rendering *through*
  the glass material is the aesthetic endgame. Compose Canvas + SwiftUI overlay.
- **Morphing glass transitions** for mode changes (chat ↔ settings ↔ docs).
- **Specular highlights** respond to device motion via `.accelerometer()` inputs —
  the face literally catches the light as you tilt your phone. Disabled when
  `isReduceMotion` is set.
- **Glass-backed Dynamic Island expansion** for conversation state.

**Exit criteria:**
- App UI passes Apple HIG Liquid Glass guidelines (subjective review)
- No regression in Compose-only surfaces
- A/B screenshot comparison shows visible material differentiation
- Reduce Motion honoured: specular off, transitions cross-fade not morph

### Phase 7 — macOS 26 Tahoe · 1 week

Compose Multiplatform already has a JVM/Desktop target in this repo; macOS gets
a richer native skin on top:

- **Menu bar app** option: 間 AI in the menu bar, click for instant pop-up chat.
- **Services menu:** text selection → "Send to 間 AI" from any Mac app.
- **Universal Clipboard** handoff from iPhone.
- **Handoff / NSUserActivity:** start a chat on iPhone, finish on Mac — free
  continuity once AppIntents exist (Phase 5).
- **Shortcuts.app on Mac:** same AppIntents, desktop context (file paths, larger
  clipboard, AppleScript bridging).
- **Larger model tier:** Macs have 16GB+ unified memory; offer a 7B MLX tier
  ("Mega M1K3", Qwen3-7B 4-bit, ~4.5GB).
- **Spotlight integration on Mac** via Core Spotlight.
- **Multi-window** — chat in one window, documents in another.
- **Keyboard-first.** Cmd+/, Cmd+K for quick actions, full keyboard navigation.

**Exit criteria:**
- `.dmg` built and notarized
- 間 AI runs as menu bar app, survives login restart
- Services menu entry works in any Cocoa text view
- Handoff handover demoed iPhone → Mac

### Phase 8 — watchOS Companion · stretch (2 weeks)

- **Complication:** the dot-matrix face on an Infograph / Modular face.
- **Quick dictation:** tap complication → dictate → answer via speaker or AirPods.
- **Audio-only conversations.** Watch doesn't get a chat transcript UI; it's a
  voice loop. Leans into dyslexia-friendly audio-first UX Kev already values.
- **Spatial audio** responses via connected AirPods.
- **WatchConnectivity** framework handles iPhone ↔ Watch state. The actual model
  stays on iPhone; Watch is a dictation-and-playback shell.

Gated on Phase 2 (voice) and Phase 5.1 (AppIntents). Possibly slipped to a later
milestone — the juice is unclear.

---

## 5. Cross-cutting Concerns

These aren't phases; they span every phase. Ignore them at your peril.

### 5.1 iPad as First-Class, Not "iOS on a Big Screen"

iPadOS 26 is a different device with different affordances. 間 AI on iPad
should take them:

- **Apple Pencil markup on source content before ingestion.** Import a PDF →
  mark it up with Pencil → only annotated ranges land in the Passage index.
  Turns 間 AI into an active-reading tool, not a dumb reader.
- **Stage Manager multi-window.** Chat in one window, documents in another,
  globe in a third. Our design already supports this on Android via windowed
  mode; iPad just needs `UISceneConfiguration` variants.
- **External display.** iPad → monitor → keyboard = poor-man's Mac. Treat this
  as a Mac-grade UX, not a zoomed iPad. Already covered by Phase 7 lessons.
- **Drag & drop** from Files into chat for instant passage ingestion. Also
  drag a chat bubble out to a note.
- **Keyboard shortcuts** (hardware keyboard) — Cmd+N new chat, Cmd+K focus
  input, Cmd+Shift+M mic, Cmd+, Settings.

**Bakes into:** Phase 1 (permissions + file import via document picker already
covers iPad Files.app); Phase 5 (widgets scale to iPad home screen); Phase 6
(Liquid Glass on iPad looks even better on a bigger canvas).

### 5.2 Onboarding & Permission Choreography

Launch-time permission machine-gunning is the #1 cause of users saying "no" to
everything and crippling the app. Our permissions:

| Permission | When asked | Why that moment |
|---|---|---|
| Mic | First tap of mic button | User intent is unambiguous |
| Notifications | Never auto. Only if user enables a feature that needs them | We prefer to not ask at all |
| HealthKit | First invocation of a health tool ("how many steps today") | Zero-hypothesis: many users never hit this |
| Location | First "nearby" tool ("weather", "where am I") | Context-precise justification |
| PersonalVoice consent | Explicit Settings toggle, not first-run | Requires considered thought |
| Camera | Visual Intelligence handler or document-import OCR | User-initiated |
| Contacts / Calendar | First related tool invocation | Pattern |

First-run flow (3–5 screens max):

1. **Hello.** Dot-matrix face fades in. One sentence: "I live here. No cloud."
2. **Model choice.** Pick a tier. Show disk size, estimated speed, Wi-Fi-only
   toggle. No defaults imposed.
3. **Download.** Foreground `URLSessionBackgroundConfiguration`; user can
   background the app. Live Activity surfaces progress.
4. **Optional: voice.** "Want voice? We'll ask for mic the first time you tap
   it." Single button: "Got it."
5. **Ready.** Open chat.

No account. No email. No sign-in. No pairing code.

### 5.3 CloudKit Sync — Decision, Opt-in

"Zero network" collides with "my notes should be on my iPad too." CloudKit's
private database is end-to-end encrypted with Advanced Data Protection on —
it is the *only* privacy-honest sync path Apple offers.

**Default:** OFF. Local-only persistence, consistent with the ethos.

**Opt-in:** Settings → "Sync across your devices?" Explicit copy:
> "Your notes and chats sync through your iCloud account, end-to-end encrypted.
> We never see them. Apple never sees them. Turn on Advanced Data Protection
> in iCloud Settings for the strongest guarantee."

**What syncs:**
- Passages (text + embeddings)
- Conversation transcripts
- Settings (persona, model tier preference, UI toggles)

**What never syncs:**
- Model files (too big — each device downloads its own)
- Voice samples used for PersonalVoice (Apple handles those separately)
- Anything ML Kit on Android might capture (transitive, Apple doesn't care)

**Architecture:** `CloudKitPassageMirror` in `iosMain` wraps `PassageRepository`,
mirrors writes to `CKPrivateDatabase`. `CKQuerySubscription` drives incremental
pulls. Conflict resolution: last-write-wins, with a local "this record was
edited elsewhere" badge if the user is actively viewing when an update lands.

**Fallback:** if CloudKit is disabled at the Apple account level, surface a
one-line Settings note and degrade silently.

### 5.4 MetricKit Instead of Sentry/Firebase

We banned third-party analytics and crash reporting at the manifest level
(`IpaMetadataAudit` gates this in CI). How do we debug crashes in the wild?

**Apple's MetricKit.** Subscribe `MXMetricManager` to `MXCrashDiagnostic`,
`MXDiskWriteExceptionDiagnostic`, `MXHangDiagnostic`, `MXCPUExceptionDiagnostic`.
Apple delivers payload daily. Zero third-party code, zero extra network, stays
local until the user explicitly shares.

**User-facing flow:**
- Settings → "Diagnostics" shows recent crash count, latest timestamp, one
  sentence per diagnostic ("app hung while generating token on 2026-04-18").
- "Share with developer" button: creates an email with the raw diagnostic
  payload attached. User reviews before sending. No background upload, ever.
- Opt-in summary to an anonymous endpoint remains available but **off by
  default** and disclosed in Settings with the exact URL.

**ADR-0011:** "MetricKit as sole diagnostic surface; no Sentry/Firebase." Ship
with this doc locked.

### 5.5 CI/CD, Code Signing, TestFlight

Apple signing eats days without a plan.

**Signing:**
- `fastlane match` with a private Git repo for certs and profiles. Encrypted
  with a passphrase that also lives in 1Password (team secret).
- App Store Connect API key (`AppStoreConnect_AuthKey.p8`) in GitHub Secrets.
- Alternative (solo dev): manual provisioning with profiles committed to the
  `.gitignore`'d `iosApp/Configuration/` dir, annually rotated.

**CI (GitHub Actions):**

```yaml
on: [pull_request]
jobs:
  simulator-tests:
    runs-on: macos-15
    steps:
      - checkout + submodules
      - ./gradlew :composeApp:iosSimulatorArm64Test
      - xcodebuild test ... | xcbeautify   # enforced by project rule
on: { push: { tags: [v*] } }
jobs:
  archive:
    runs-on: macos-15
    steps:
      - match appstore
      - ./scripts/build-ios-libs.sh         # Phase 0 CMake driver
      - xcodebuild archive -scheme iosApp -archivePath ... | xcbeautify
      - pilot upload (TestFlight)
```

**Auto-increment build numbers** from `git rev-list --count HEAD`. No manual
bumping.

**Distribution:**
- **Internal TestFlight:** unlimited, team only, every tag.
- **External TestFlight:** up to 10k; phased rollout for major versions.
- **Production App Store:** phased release (7-day ramp).

**Risk:** first time through, budget 1–2 days of signing-fiddle before CI is
green end-to-end.

### 5.6 Background, Memory, Lifecycle

iOS is more aggressive than Android about reclaiming memory from backgrounded
apps. Our MLX/Ma model context can be 1–3 GB resident — a prime eviction target.

- **On foreground:** check if context handle is still valid. If not, reload
  with a visible "warming up..." shimmer for up to 2s. Not a spinner.
- **On background:** free KV cache proactively (keep the model weights
  mmap'd but release transient buffers).
- **Background tasks** (`BGProcessingTask`) for model-file integrity checks
  overnight; never for inference.
- **Background audio** session category when TTS is active so it keeps
  playing after the screen locks.
- **Memory warnings** (`didReceiveMemoryWarningNotification`) trigger a
  graceful "context released; next message will reload" state.

### 5.7 Localization & Regional Gotchas

Android already has multi-language support (project has a `localization-specialist`
agent and skill). iOS needs parity:

- `.xcstrings` catalogs (Xcode 15+) for UI strings, exported from Android
  string resources with a porting script.
- Per-locale `SFSpeechRecognizer` languages — iOS supports dozens fully offline.
- `AVSpeechSynthesisVoice` voices per locale. User's default voice follows OS
  language.
- **Foundation Models language availability is regional.** On device, check
  `SystemLanguageModel.isAvailable(for: locale)` and fall through to MLX/Ma
  if the locale isn't supported.
- **Visual Intelligence** is US-English first. Region-gate the handler
  registration.
- **RTL** (Arabic, Hebrew): Compose Multiplatform handles this in-engine;
  SwiftUI overlays (Phase 6 glass chrome) need `environment(\.layoutDirection, .rightToLeft)`
  forwarding.

**ADR-0012:** "iOS i18n via xcstrings + shared source-of-truth with Android."

---

## 6. Privacy & Trust

Same story as Android, one extra complexity: Apple Foundation Models can dispatch
to **Private Cloud Compute** for queries that exceed the on-device model. Our
position:

- Always pass `.onDeviceOnly` to `SystemLanguageModel` initializers.
- Surface this decision in Settings → Privacy: "Apple's on-device AI only. We
  never send your data to Apple's Private Cloud, even when Apple offers to."
- If Apple removes `.onDeviceOnly` or it stops working, disable the Apple FM
  backend immediately and fall through to MLX/Ma. Automated: version-gate the
  engine availability behind a build check at init.

Build-time audits:

- **`IpaMetadataAudit` (iOS)** — mirrors `ManifestPrivacyTest`. Walks the
  final `.ipa`'s frameworks + Podfile.lock; fails if `Firebase*`, `Crashlytics`,
  `GoogleAnalytics*`, `Mixpanel`, `Amplitude`, `Bugsnag`, or any analytics SDK
  appears.
- **Entitlements diff gate** — any new entitlement must come with an ADR and
  a line in Settings → Privacy explaining what it's for.
- **Info.plist keys explained inline** — every `NSMicrophoneUsageDescription`
  et al. has user-readable copy that matches the product honestly.

---

## 7. Dependency Matrix

| Dependency | Min iOS | Integration | Risk |
|---|---|---|---|
| Foundation Models | 26.0 | C shim from Swift, cinterop | **High** — API may churn through 26.x |
| MLX-Swift | 17.0 (Metal 3) | SPM, Swift↔Kotlin bridge | Medium — API stable since 2024 |
| SQLCipher | 15.0 | CocoaPods | Low |
| onnxruntime-objc | 15.0 | CocoaPods | Low |
| Speech framework | 13.0 | Native | Low |
| PersonalVoice | 17.0 | Entitlement + consent | Medium — entitlement approval |
| AppIntents | 16.0 (basic), 17.0 (full), 18.0 (latest), 26.0 (Assistant schemas) | Swift target | Low |
| WidgetKit Interactive | 17.0 | Swift target | Low |
| Live Activities | 16.1 | Swift target | Low |
| Visual Intelligence | 26.0 + iPhone 16 Pro | Swift shim | **High** — new API, device-gated |
| Writing Tools | 18.2 | Swift extension | Medium — extensibility API new |
| Liquid Glass | 26.0 | SwiftUI only | Medium |
| CloudKit | 15.0 | Native | Low (code) / Medium (testing) |
| MetricKit | 14.0 | Native | Low |
| WatchConnectivity | 9.0 (watchOS) | Native | Low |
| CocoaPods | — | Required for SQLCipher, ONNX | Low but annoying |
| Fastlane + match | — | Ruby toolchain | Low |

---

## 8. Risk Register

**R1 — Kotlin ↔ Swift interop tax.**
Foundation Models and MLX are Swift-only. Kotlin/Native has cinterop for C/ObjC
but not for Swift directly. We bridge via `@_cdecl` C shims. If this proves too
brittle, fallback is to host all LLM logic in Swift and expose just the Kotlin
domain types via the framework. Costs: domain logic disperses. Probability:
Medium. Mitigation: build one working shim in Phase 3 before committing to it
for all three engines.

**R2 — llama.cpp iOS cross-compile.**
CMake toolchain for iOS has sharp edges (bitcode, minos, universal binaries,
lipo vs xcframework). Phase 0 de-risks this. If it stalls more than 2 days,
fall back to llama.cpp's own `Package.swift` integration and skip `ma_core` on
iOS — but that loses our streaming/UTF-8/grammar layer. **Hard preference for
the ma_core path.** Mitigation: timebox Phase 0 step 4 at 4h; pivot if over.

**R3 — Apple Foundation Models private API changes.**
iOS 26.x is new. APIs may rename between 26.0 → 26.1 → 26.2. Mitigation: wrap
every Apple FM call in a versioned facade; have MLX fallback ready.

**R4 — PersonalVoice entitlement approval.**
Apple gate-keeps this entitlement. Could be denied. Mitigation: voice stack
works without it (Phase 2 premium voices + Kokoro). PersonalVoice is a bonus
that moves TestFlight demos, not a launch blocker.

**R5 — App Store review for "AI companion".**
Apple has tightened rules on unsupervised AI behaviour; companion apps that
invoke OS capabilities (camera, health) need clear user initiation.
Mitigation: every tool invocation shows a confirmation chip in chat (already
the Android UX) and is logged in `ToolExecution` table. Clear paper trail.

**R6 — Compose Multiplatform on iOS performance.**
Scrolling, heavy canvas, WebView integration. Profile early. Mitigation: if a
surface underperforms, SwiftUI-ify just that surface (e.g. conversation list).
Hybrid is fine.

**R7 — Binary size.**
ma_core + llama.cpp + MLX + ONNX + SQLCipher + dot-matrix assets + WebView
bundle. iOS limit (cellular download) is 200MB uncompressed. Mitigation: split
app thinning, ship MLX models as downloads (not bundled), defer Ma to fallback
only.

**R8 — Legal / licensing.**
llama.cpp MIT, MLX MIT, Kokoro Apache, Qwen tongyi-qianwen/Apache, Gemma terms
reviewed. Add `iosApp/Acknowledgements/` generated at build time.

**R9 — Signing / provisioning mishaps.**
Expired certs, compromised `match` repo, rotated keys mid-release, entitlement
drift between dev/staging/prod. Mitigation: `match` in a private Git repo
that only CI and Kev have read access to; a calendar reminder a month before
cert expiry; a CI smoke job that just verifies signing works without
archiving, runnable on any PR.

**R10 — Memory eviction during long sessions.**
iOS kills backgrounded apps aggressively; a 2GB MLX context gets dropped and
the user returns to a frozen shimmer. Mitigation: memory-warning handlers
release KV cache proactively and cleanly reload on foreground. Explicit
"warming up" UI, not a silent wait.

---

## 9. Open Questions

- [ ] **Q1:** Do we ship a single Catalyst app, a real macOS SwiftUI app, or
  JVM-based Compose Desktop on Mac? Leaning **real macOS app** via Compose MP's
  macOS target (it exists but is alpha). If too unstable, Catalyst is the safe
  middle. JVM is a no-go for "resident of the OS" feel.
- [ ] **Q2:** Foundation Models session lifecycle — do we reuse a single session
  across chats or recreate per query? Reuse is faster but leaks context;
  recreate is clean but slower. Measure in Phase 3.
- [ ] **Q3:** MLX model format: we currently ship GGUF for Android. Bundle MLX
  alongside, or convert on-device from GGUF at first launch? Converting at
  launch is slow (minutes). Shipping MLX too doubles download size. Probably
  **bundle separate tiers per platform**; user picks.
- [ ] **Q4:** Does the Apple FM 3B model need our persona prompt, or is it off-
  limits for persona (since it's "Apple's" model)? Test: same system prompt,
  does M1K3-the-character come through? If yes, delightful. If no, use FM only
  for structured tasks.
- [ ] **Q5:** StandBy face — static emotion, or animated idle loop? Power
  budget: StandBy displays are low-refresh; aggressive animation drains
  overnight. Probably **2 Hz breathe, emotion static**.
- [ ] **Q6:** Passage embedding model on iOS — same MiniLM ONNX, or swap to
  Apple's `NLContextualEmbedding` (NaturalLanguage framework, 512-dim, zero
  download cost)? Latter is attractive for disk savings but loses cross-
  platform embedding-space parity. Probably keep MiniLM for corpus portability.
- [ ] **Q7:** **Monetization.** Free forever? One-time purchase? Tip jar?
  Subscription for the larger model tier on Mac? This choice ripples into
  model-tier decisions (subsidised larger models?), App Store category, the
  persona itself (an ad-free assistant reads different from a paid one).
  Must be resolved before Phase 5 ships — AppIntents are a user-trust surface
  and changing commerce posture after shipping them is messy. **Decision gate
  before starting Phase 5.**

---

## 10. Success Metrics

**Phase exit is not the same as success.** These measure whether we built the
right thing:

| Metric | Target | Why |
|---|---|---|
| Cold-start to first token | < 3s (MLX), < 5s (Ma) | User patience for on-device AI |
| Chat streaming rate | ≥ 25 tok/s (iPhone 15 Pro, MLX, 1.7B) | Feels snappier than Android |
| Apple FM classification latency | < 150ms | Worth routing through it |
| PersonalVoice utilisation | ≥ 10% of users who have one enrolled | Demo moment lands |
| AppIntents daily use | ≥ 20% of DAU invoke via Siri/Shortcuts/Widget | OS embedding justified |
| Visual Intelligence handoffs | ≥ 5% of iPhone 16+ users try it | Hero feature adoption |
| TestFlight crash-free rate | ≥ 99.5% | Kotlin/Native + Swift bridge stability |
| Privacy audit | 0 new analytics SDKs shipped | Trust guarantee holds |
| App size (uncompressed install) | < 180MB base, models downloaded | Store policy + sanity |
| VoiceOver coverage | 100% of interactive elements labelled | Accessibility invariant |
| CloudKit opt-in rate | No target — purely user-driven | Consent integrity |

---

## 11. Sequencing & ADRs

**Near-term commits:**

1. ADR-0007: "Three-brain architecture for iOS" (FM + MLX + Ma) — this plan in
   condensed form.
2. ADR-0008: "ma_core split — portable C API" — the Phase 0 refactor.
3. ADR-0009: "SQLCipher on iOS via CocoaPods" — Phase 1 crypto parity.
4. ADR-0010: "Apple Foundation Models: on-device only" — privacy commitment.
5. ADR-0011: "MetricKit as sole diagnostic surface; no Sentry/Firebase."
6. ADR-0012: "iOS i18n via xcstrings + shared source-of-truth with Android."

**Stacked PR plan:**

- PR #1: `MaInferenceBackend` → commonMain (no-op for Android, unblocks iOS).
- PR #2: `ma_core` split (keeps Android green; iOS added later).
- PR #3: iOS cross-compile + cinterop + first iOS smoke test (Phase 0 complete).
- PR #4: `EmbeddingEngine.ios` + `SqlCipher.ios` + Keychain passphrase
  + backup-exclusion (Phase 1).
- PR #5: iOS voice (Phase 2).
- PR #6: CI/CD + signing + first TestFlight (blocker: must land before any
  external demo).
- PR #7+: per-feature after Phase 1 lands.

Each PR revert-safe; Android behaviour unchanged until explicit cutover.

---

## 12. Appendix: File-Level Moves (Phase 0 & 1)

```
# Phase 0
composeApp/src/androidMain/kotlin/.../ai/ma/MaInferenceBackend.kt
  → composeApp/src/commonMain/kotlin/.../ai/ma/MaInferenceBackend.kt

composeApp/src/androidMain/cpp/llama.cpp
  → composeApp/src/nativeShared/llama.cpp

composeApp/src/androidMain/cpp/ma_bridge.cpp (split)
  → composeApp/src/nativeShared/ma/ma_core.{cpp,h}      (portable)
  + composeApp/src/androidMain/cpp/ma_bridge_jni.cpp    (JNI shim)

new: composeApp/src/nativeShared/ma.def
new: composeApp/src/iosMain/kotlin/.../ai/ma/MaNativeBridge.kt
new: composeApp/src/iosTest/kotlin/.../ai/ma/MaNativeBridgeSmokeTest.kt
new: scripts/build-ios-libs.sh  (CMake iOS toolchain driver)

# Phase 1
new: composeApp/src/iosMain/kotlin/.../embedding/MiniLmEmbeddingEngine.ios.kt
new: composeApp/src/iosMain/kotlin/.../database/IosDatabaseFactory.kt (SQLCipher)
new: composeApp/src/iosMain/kotlin/.../download/IosModelDownloadManager.kt
new: composeApp/src/iosMain/kotlin/.../security/KeychainPassphraseStore.kt
new: composeApp/src/iosMain/kotlin/.../storage/ModelStorage.kt (backup-exclusion)
update: composeApp/build.gradle.kts (cinterop config, pod integrations)
new: iosApp/Podfile (SQLCipher, onnxruntime-objc)
new: iosApp/iosApp/Info.plist entitlements (App Group, Keychain sharing)

# Phase 1 ADRs
new: app/docs/adr/0007-three-brain-ios.md
new: app/docs/adr/0008-ma-core-portable.md
new: app/docs/adr/0009-sqlcipher-ios.md
new: app/docs/adr/0010-apple-foundation-models-on-device-only.md
new: app/docs/adr/0011-metrickit-sole-diagnostic.md
new: app/docs/adr/0012-ios-i18n-xcstrings.md

# Cross-cutting infrastructure
new: .github/workflows/ios-simulator-tests.yml
new: .github/workflows/ios-archive-testflight.yml
new: fastlane/Fastfile (match, pilot)
new: fastlane/Matchfile
new: scripts/port-android-strings-to-xcstrings.sh
```

---

## 13. What "Done" Looks Like (Product Vision)

A Kev-shaped user picks up a new iPhone. They install 間 AI. First launch:

1. Dot-matrix face fades up behind Liquid Glass. "Hi. I'm 間. I live here. No
   cloud. Your notes are my memory."
2. They pick a model tier, download starts in the background as a Live
   Activity. They carry on with their day.
3. Later: they say "hey Siri, ask 間 what's on my mind" — Siri hands off,
   間 answers in their own PersonalVoice.
4. They add the StandBy widget; that night, docked, the face breathes gently
   on the bedside.
5. Morning: they point the camera at a mystery plant, long-press Camera
   Control — 間 identifies it and adds it to their plant journal.
6. On the iPad, they mark up a PDF with Apple Pencil — only the highlighted
   passages land in their searchable corpus. They ask a question about it;
   same answer is available seamlessly on the iPhone because CloudKit sync
   is on (their explicit choice).
7. Share sheet from Safari sends three articles into the passage corpus.
   Shortcut runs a "morning brief" AppIntent.
8. Evening, they say "write me an email declining this meeting", selection
   in Mail opens Writing Tools → "Ask 間 AI" → drafted in their voice.
9. Watch complication shows the face blink. They tap, speak, answer in their
   AirPods spatial-audio mix.

All on-device. Zero network for inference. Costs nothing recurring. Can't be
shut down. Can't be trained on. Works in airplane mode on a glacier. Yours.

That's the iOS bet.

---

*MurphySig: kev+claude / confidence 0.75 / 2026-04-19*
*Confidence tempered by R1 (Swift interop) and R3 (FM API churn) being
the real unknowns. Phases 0-2 are 0.9 confidence; 3-4 are 0.75; 5+ is
0.6 until we're building against shipping iOS 26.x GM.*
