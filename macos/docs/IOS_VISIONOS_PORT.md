# iOS + visionOS port — the derisk spike (2026-07-06)

> Living doc for bringing the **native SwiftUI M1K3** (this `macos/` app) to iOS and
> visionOS as one shared, RAM-gated adaptive shell. This first pass is a **derisk
> spike**: declare the platforms, prove the library graph compiles for iOS/visionOS,
> guard the few macOS-only leaves, and inventory the real runtime work — *before*
> building the app target / UI / TestFlight lane. Kev's decision (2026-07-06):
> mobile is THIS app, not the KMP `app/` surface — supersedes the "iOS = KMP" note in
> `tools/release/README.md` §3 (reconcile additively there when the shell lands).

## What the spike proved

The package is protocol-seam first, so the macOS lock-in lives in the `M1K3App/`
shell — **not** in the library graph. After declaring `.iOS(.v26)` + `.visionOS(.v26)`
(`Package.swift`) and two small guards, **every library product compiles for iOS**
(verified via `xcodebuild -destination 'generic/platform=iOS Simulator'`, Xcode 26.6):

| Product | iOS | Notes |
|---|---|---|
| M1K3Knowledge, M1K3Memory, M1K3Inference | ✅ | pure logic + GRDB; `MemoryStore` was pre-annotated "works unchanged on iOS" |
| M1K3MLX | ✅ | the full MLX/Metal graph cross-compiles |
| M1K3Kokoro | ✅ | ONNX Runtime (CPU EP today — perf caveat below) |
| M1K3WhisperKit | ✅ | after the M1K3Calls guard |
| M1K3MemoryViz, M1K3Avatar | ✅ | RealityKit; `ConstellationPalette` was already `canImport(AppKit)`-guarded |
| M1K3Chat, M1K3Agent | ✅ | FoundationModels (`@_weakLinked`) needs **no** availability change at deployment floor 26 |
| M1K3Calls | ✅ | after the ScreenCaptureKit guard |
| M1K3MCPKit, M1K3MCP | ✅ | after the `homeDirectoryForCurrentUser` guard |

`swift test --parallel` on macOS stays green (**1749 tests / 258 suites**), so the
platform declaration + guards + memory-band changes are non-regressive to the Mac build.

**visionOS** (`generic/platform=visionOS Simulator`, representative spot-check): M1K3MLX,
M1K3Calls, M1K3MCPKit, M1K3Chat, M1K3Avatar all ✅. **One dependency gap, not our code:**
`M1K3Kokoro` ❌ — the prebuilt `onnxruntime.xcframework` (Microsoft's SPM package) **ships
no visionOS slice** ("no library for this platform was found"). It has an iOS slice (Kokoro
builds for iOS), just not xrOS. Phase-2 options: exclude M1K3Kokoro from the visionOS
product and fall back to AVSpeech TTS on Vision Pro, or wait for an upstream xrOS binary.
Nothing in M1K3's own source blocks visionOS.

## The two real compile breaks the probe found (now fixed)

1. **`ScreenCaptureKit`** — `Sources/M1K3Calls/StereoCallRecorder.swift`. Far-end
   (system-audio) call capture is ScreenCaptureKit, which is macOS-only (no iOS/visionOS
   equivalent; ReplayKit is a different foreground-consent model). The whole recorder is
   now `#if canImport(ScreenCaptureKit)`-guarded (it's only self-consumed). On mobile the
   feature is simply absent — a Phase-2 product decision, not a silent stub.
2. **`homeDirectoryForCurrentUser`** — `Sources/M1K3MCPKit/MCPServer.swift:33`,
   unavailable on iOS. Now `#if os(macOS)` uses it (byte-identical Mac behaviour); iOS/
   visionOS take `NSHomeDirectory()` (the app's home *is* its container there).

## Memory band + tier gate (shipped in the spike, TDD'd)

The iOS jetsam budget is a fraction of physical RAM, so the Mac-tuned budgets would
push a mobile app toward jetsam. Two pure, tested policy changes (desktop stays the
default — zero Mac behaviour change):

- **`MLXMemoryBudget.DeviceProfile{.desktop,.mobile}`** — `.mobile` caps the MLX
  back-pressure ceiling at 4 GB (vs the 12 GB Mac companion ceiling), so a 4-bit 4B
  brain + KV fits and MLX yields before the OS jetsams. Applied via `#if os(iOS) ||
  os(visionOS)` in `applyOnce`.
- **`BrainTier.recommended(…, platform:)`** — `.mobile` never recommends Big
  (gemma-4-12B ≈7.4 GB at inference exceeds any current mobile budget); Lil only on
  ≥16 GB (iPad Pro / Vision Pro); everything smaller stays on **Mini** (Apple
  Foundation Models, no MLX footprint). iPhones therefore land on Mini by design.

Both ceilings are tunable and marked verify-by-launch (confirm against
`os_proc_available_memory()` on real devices when the shell lands).

## Phase 2 — the shared adaptive shell (NOT in this spike)

The library graph is portable; the remaining work is the front end and the runtime seams:

1. **AVAudioSession lifecycle** (the top item) — absent repo-wide (0 hits). iOS/visionOS
   need a `.playAndRecord` session + interruption/route handling behind the existing
   `SpeechProvider`/`TranscriptionProvider` seams before mic or playback works. Compiles
   today, silent at runtime.
2. **The app shell** — `M1K3App/` is an AppKit menu-bar companion (`NSApplicationDelegateAdaptor`,
   `MenuBarExtra`, single-`Window`/`Settings` scenes, `.hiddenTitleBar`, launch-at-login).
   A fresh SwiftUI scene graph per platform; `NSColor`→`UIColor`, `NSImage`→`UIImage`, four
   `NSViewRepresentable`→`UIViewRepresentable` wrappers (QuickLook/WebKit/GlassBackground/Artifact),
   `NSPasteboard`/`NSWorkspace` call sites.
3. **In-app MCP server** — `LocalMCPHTTPServer` (NWListener on 127.0.0.1:4242) compiles on
   iOS but its "resident, peer-reachable" premise is meaningless there (no desktop agents
   dialing in; background sockets get suspended). Keep on macOS (+ visionOS-windowed);
   exclude from the iOS shell surface.
4. **Kokoro EP** — no CoreML execution provider wired (CPU only); acceptable for the spike,
   flag for on-device TTS perf.
5. **Entitlements / Info.plist** — drop the macOS `app-sandbox` / `audioanalyticsd`
   mach-lookup exceptions; iOS mic is gated by `NSMicrophoneUsageDescription` (already
   present) + AVAudioSession; add `UIBackgroundModes` (audio) if voice must persist.
6. **App target + scheme + icons + Xcode Cloud iOS/visionOS archive → TestFlight lane**
   (the existing lane is macOS-only).
7. **visionOS spatial treatment** — the 3D avatar + memory constellation are spatial-native;
   the differentiated "wow" that neither Mac nor phone gives.

## Full UI parity roadmap (planned 2026-07-06, after the on-device harness worked)

The compile spike + the running harness (`M1K3iOSApp/`, real engine + real pixel-face
avatar on an iPhone 17 Pro, both Mini and Lil verified on device) prove the engine and
the brand port. The road to a shipping iOS/visionOS app with UI parity:

**Keystone — split `AppEnvironment`.** The macOS composition root imports AppKit and
wires every store/provider/controller; it's the one thing everything depends on.
Extract a portable `M1K3Core` (stores, embedder, brains, chat + voice controllers,
memory) from the macOS-only shell (NSApp lifecycle, menu bar, windows, System Settings
deep-links). Then the Mac app and the iOS/visionOS shell wire the SAME core. Delicate —
it's load-bearing and only tested through the Mac app today.

- **Phase A — Real chat (the spine).** Replace the harness's raw `generate` with the
  `M1K3Chat` pipeline via the core: streaming (face speaks token-by-token), RAG +
  documents-first, native tool-calling, multi-conversation history, copy, reading modes
  (bionic/dyslexia focus reader), generation stats, artifact/WebView panel (UIViewRepresentable).
- **Phase B — Voice.** AVAudioSession lifecycle (the one true blocker) behind the
  SpeechProvider/TranscriptionProvider seams; voice mode (avatar hero + karaoke +
  push-to-talk), Kokoro TTS (iOS ✓; visionOS needs the ONNX xrOS slice or AVSpeech
  fallback), WhisperKit/Apple STT, interruption/route handling.
- **Phase C — Shell & navigation (iOS-native, not a port).** TabView/NavigationStack
  for Chat / Memories / Documents / Settings; onboarding + capability ladder. The
  menu-bar companion's iOS soul: Home/Lock-Screen widgets, App Intents/Shortcuts
  (already in the codebase), a Live Activity for long thinks, a Control Center control.
- **Phase D — Spatial (visionOS), the flagship.** The avatar as a volumetric companion +
  the 3D memory constellation (M1K3MemoryViz, already green) as a walkable field.
- **Phase E — Distribution.** iOS/visionOS entitlements (drop macOS sandbox/mach-lookup,
  add background-audio if voice persists), icon renditions, an Xcode Cloud iOS/visionOS →
  TestFlight lane, device-tune the memory cap (os_proc_available_memory).

Sequencing: keystone → A is the spine; B and C parallelize once the core exists; D is the
flagship follow; E rides alongside. Not ported: the in-app MCP HTTP server (meaningless
on iOS — no desktop agents dialing in); stays macOS (+ visionOS-windowed).

_Signed: Kev + claude-opus-4-8, 2026-07-06, Confidence 0.9 (every "compiles for iOS" row
verified by an actual `xcodebuild` per product; macOS `swift test` 1749/258 green proves
non-regression; the two guards + memory band are the only source changes and are
TDD-pinned. Honest caveats: builds are compile-green only — AVAudioSession, the UI shell,
and on-device MLX memory behaviour are Phase-2 verify-by-launch; the 4 GB mobile ceiling
and the ≥16 GB Lil threshold are tunable constants, not yet device-measured). Prior: Unknown._

---

## 2026-07-06 (later) — the shared shell SHIPPED (Phase A + C, iOS **and** visionOS compile-green)

The derisk harness has grown into the **real, multi-screen shared adaptive shell** —
not a mock, not the throwaway harness. The lead call was deliberate: rather than the
risky keystone surgery on the Mac's `AppEnvironment` (the shipping product's composition
root, AppKit-bound and only tested through the Mac app — I can't runtime-verify it without
a device), the shell gets its **own** portable composition root that wires the SAME
`swift test`-covered package graph. **The macOS `AppEnvironment` is untouched → zero
regression risk to the shipping Mac product** (proved: 1749/258 still green).

### What shipped (`M1K3iOSApp/`, one target per platform)

- **`AppCore.swift`** — the mobile composition root. Wires `KnowledgeStore` (hybrid RAG),
  the temporal `MemoryStore`, `HashingEmbeddingService`, a `SwappableInferenceProvider`
  slot (Mini = Apple Foundation Models, Lil = MLX Qwen3-4B, re-pointed on brain switch so
  the transcript survives), the always-on tool-calling `AgentRAGResponder` (its own iOS
  factory — knowledge + web tools; no CoolHead/voice plumbing), a persisted `ChatSession`
  (`GRDBChatHistoryStore` + `ProviderConversationTitler`), `DocumentIngester`, and the
  pixel-face avatar. Container paths use `NSHomeDirectory()`/`applicationSupportDirectory`
  (no macOS-only `homeDirectoryForCurrentUser`).
- **`ChatScreen.swift`** — the spine: real grounded streaming chat over `ChatSession`, the
  avatar as hero→dock, brain-load progress, asymmetric bubbles.
- **`RootView.swift`** — first-run onboarding gate → `TabView` (Chat / Memories / Documents
  / Settings), iOS-native navigation (not a port of the menu-bar companion).
- **`DocumentsScreen`** (list + `fileImporter` ingest + delete over the real ingester),
  **`MemoriesScreen`** (live count + hybrid `MemoryStore.recall`), **`SettingsScreen`**
  (mobile-safe brain picker, web-search toggle, AFM availability, about),
  **`OnboardingScreen`** (`BrainTier.recommended(platform:.mobile)` — iPhones land on Mini,
  ≥16 GB iPad Pro / Vision Pro can pick Lil), **`MessageBubble`**, **`GlassCompat`**.
- **`project.yml`** — `M1K3iOS` deps expanded to the full portable pipeline; new
  **`M1K3visionOS`** target + scheme sharing the exact source list & deps (YAML anchors).

### The three portability fixes this pass found (compile-verified, not asserted)

1. **`IOKit` is macOS-only** — `M1K3AgentTools/SystemStatusProviding.swift` imported
   `IOKit.ps` for the battery lane (the spike's table never covered `M1K3AgentTools`). Now
   `#if canImport(IOKit)`-guarded; macOS byte-identical, iOS/visionOS return `nil` battery
   (already `Optional` — nil on a desktop Mac too, so the tool degrades cleanly). A
   `UIDevice` battery lane is a follow (it needs MainActor hops the nonisolated seam avoids).
2. **`glassEffect(_:in:)` is unavailable on visionOS** — the shell's glass chips route
   through a `.m1k3Glass(cornerRadius:tint:)` helper: Liquid Glass on iOS, `.regularMaterial`
   on visionOS. One call site to evolve when the Phase-D spatial treatment lands.
3. **`@Sendable` responder closures can't read MainActor statics** — the persistence keys
   are `nonisolated static let` (the Mac `AppEnvironment`'s own fix).

### Verification (this session)

| Gate | Result |
|---|---|
| `xcodebuild -scheme M1K3iOS -destination 'generic/platform=iOS Simulator'` | **BUILD SUCCEEDED**, 0 errors |
| `xcodebuild -scheme M1K3visionOS -destination 'generic/platform=visionOS Simulator'` | **BUILD SUCCEEDED**, 0 errors |
| `swift test --parallel` (macOS non-regression) | **1749 tests / 258 suites passed** |

The MLX Metal graph links for **both** the iOS and visionOS simulators (the storm ran per
arch). Verification ceiling is **compile-green** — the simulator can't run MLX (no Metal
for it), so on-device run is verify-owed, same as the spike.

### Still to do (honestly device/runtime-gated — NOT claimed done)

- **Phase B — Voice.** `AVAudioSession` lifecycle behind the `SpeechProvider`/
  `TranscriptionProvider` seams; Kokoro TTS (iOS ✓; visionOS needs the ONNX xrOS slice or
  an AVSpeech fallback), WhisperKit/Apple STT. Not wired in the shell.
- **On-device run** — MLX generation, memory behaviour under the 4 GB mobile ceiling, the
  streaming feel, first-run onboarding, AFM availability on real AI-off hardware.
- **Phase D — Spatial (visionOS flagship)** — volumetric avatar + walkable memory
  constellation. The shell renders as a window today; `m1k3Glass` is the seam to upgrade.
- **Phase E — Distribution** — iOS/visionOS icons, entitlements, an Xcode Cloud →
  TestFlight lane (the current lane is macOS-only), device-tune the memory cap.
- Trivial: the entry file is still named `M1K3iOSHarnessApp.swift` (now holds
  `struct M1K3iOSApp`); a rename is cosmetic and deferred to avoid re-verifying both builds.

### Review pass (folded before sign-off)

A `code-quality-reviewer` pass on `AppCore` + the screens caught that the hand-ported
brain-swap flow had dropped the Mac's hard-won guards. Fixed (all re-compiled green):
- **Data-loss (critical):** Return-key `send()` only checked non-empty, not `canSend` — a
  Return while warming/streaming cleared the draft then no-op'd, eating the message. Now
  guarded on `canSend`.
- **Stale progress clobber:** a `warmGeneration` token now invalidates an abandoned warm's
  late progress callbacks (a switch-to-Mini mid-download could otherwise render "Waking
  Mini… N%").
- **No-op guard:** re-selecting the already-warm brain no longer tears down its KV/persona
  cache; **`releaseMemory()`** is called on every discarded MLX provider (it was leaking a
  Metal allocation against the 4 GB mobile ceiling); the cold-launch double-instance is
  gone (warm reuses the slot's provider).
- **Readiness honesty:** Chat now shows *why* it can't answer (AFM unavailable / brain
  warming) instead of a silently-disabled button; the ingest banner auto-dismisses and a
  0-chunk ingest reads as "no indexable text," not a success.

Deferred (noted, non-blocking): make AFM availability Observation-tracked (poll on
foreground); the reviewer's principled call — extract the brain-swap state machine into a
shared, `swift test`-covered package type both apps use, rather than hand-porting the Mac's
logic a third time. That's the right next refactor.

_Signed: Kev + claude-opus-4-8, 2026-07-06 (shell delivery), Confidence 0.85 (both platforms
BUILD SUCCEEDED via real xcodebuild with the true exit code read from the log — NOT a
wrapper's exit, per the standing false-success trap; macOS 1749/258 green proves the one
package edit is non-regressive; every API was pinned against the Mac's ground-truth wiring
before writing. Honest caveats: compile-green only — on-device MLX run, voice, and the
spatial flagship are named verify-owed, not done; the shell is uncommitted in the working
tree, as the goal framed it — committing fires the TestFlight-adjacent pipeline and is
Kev's call). Prior: Kev + claude-fable-5 (the spike + harness)._

---

## Addendum — 2026-07-07: the DRY relocation + mobile memory ON (commit 1fcf13f5)

The "refactor a third time" the delivery block flagged got done. Two app-target-only
helpers moved DOWN into the package so both shells share one copy:

- **`SwappableEmbeddingService`** → `M1K3Knowledge` (beside the `EmbeddingService`
  protocol; the last stranded member of the Swappable\* family). No new dep edge.
- **`DistilledFactGraphAdapter`** → a **new leaf module `M1K3MemoryChatBridge`**
  (deps exactly `[M1K3Chat, M1K3Memory]`). NOT folded into `M1K3Chat` — that would
  force `Chat→Memory` and break the documented "Chat must not depend on Memory" seam.
  Nothing depends back on the bridge, so it's cycle-free.

With the adapter shared, **`AppCore` now wires the same `MemoryDistillationCoordinator`
the Mac uses** (`memoryAutoCaptureKey`, default ON): durable facts distil from chat into
the corpus AND mirror into the temporal graph. `MemoriesScreen` recall is the read side;
this is the write side. So the Memories tab is real on mobile now, not a placeholder.
(Runtime firing is verify-by-launch — the `ChatSession` scheduling is package-pinned,
the `AppCore` glue has no iOS test bundle.) Verified: `swift test` 1752/260 + Mac/iOS/
visionOS xcodebuild all BUILD SUCCEEDED.

_Signed: Kev + claude-opus-4-8, 2026-07-07 (DRY + mobile memory), Confidence 0.85._

---

## Addendum — 2026-07-18: the Mac-feel aesthetic pass (feat/ios-aesthetic-pass)

First pass at closing the LOOK gap Kev named ("the iOS app doesn't have the Mac
aesthetic we nailed"). All composition of already-shared, already-TDD'd pieces —
no new policy logic:

- **Reactive avatar backdrop** (`M1K3iOSApp/ChatBackdrop.swift`): once a
  conversation starts, the pixel face stops shrinking to a 76pt dock and becomes
  the full-bleed background — bloom when idle, recede (dim/blur/scale) while an
  answer streams or the user is composing. Drives the shared
  `ChatBackdropTreatment` (package-tested); one RealityView at a time (the hero
  hands off to the backdrop). `isComposing` is broader than the Mac's
  `!draft.isEmpty` — keyboard focus alone recedes, because the keyboard
  shortens the viewport on a phone.
- **Reading modes on mobile**: `ReadingMode.swift` + `ReadingText.swift` joined
  `&mobileShellSources`, with the OpenDyslexic-{Regular,Bold}.otf resources
  (`BundledFonts.register()` already listed them; the resource lines make the
  registration real). Settings gains a Reading section (picker + live preview).
- **FOLLOWUPS chips rendered**: the shared `ChatSession` was already populating
  `message.followUps` on iOS — `MessageBubble` now renders the chips
  (`.complete`-gated, tap-to-send via `core.send`), with the Mac's
  `LegibilityScrim` treatment on flat assistant turns over the live backdrop.
  Autoscroll also fires on chip arrival (no text change at `.complete`).
- **Platform-honest copy**: `BrainTier.detail` for Lil said "Runs entirely on
  your Mac" on an iPhone (caught on-simulator) — now `#if os(macOS)` branched.

Review folds (multi-lens review, all adversarially confirmed): chip taps now
share the input bar's `isResponding` gate (a mid-stream tap was silently eaten
AND the avatar epilogue bloomed the backdrop over streaming text); the backdrop
is opt-out-able (Settings → Appearance) and Reduce Transparency disables it;
and `ChatBackdropTreatment.animatesMotion` finally has a consumer —
`AvatarView`/`CRTOverlay` gained a `paused` flag (recede/still/Reduce Motion
render one frame and stop the 30fps clocks), wired on BOTH platforms
(`ChatBackdrop` + the Mac's `AvatarChatBackground` via `AvatarSurface`;
companion/constellation surfaces are a logged follow-up).

Verify-owed (named): backdrop legibility + bloom/recede feel on device; the
paused-face look on both platforms (one crisp frame, no drift); CRT canvas +
full-bleed RealityView thermals on A-series in the BLOOM state (receded/still
are now quiet); OpenDyslexic rendering on device; chip frequency on the mobile
ladder (Mini/Lil emit FOLLOWUPS less reliably than Big).

_Signed: Kev + claude-fable-5, 2026-07-18 (Mac-feel pass), Confidence 0.85
(composition of TDD'd shared pieces; sim-verified live — bloom/recede/scrim/
streaming seen working; on-device feel + thermals verify-owed as named).
Prior: Kev + claude-opus-4-8 (the shell this restyles)._
