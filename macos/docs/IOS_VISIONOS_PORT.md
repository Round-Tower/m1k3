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
  (gemma-4-e4b ≈7 GB at inference exceeds any current mobile budget); Lil only on
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

_Signed: Kev + claude-fable-5, 2026-07-06, Confidence 0.9 (every "compiles for iOS" row
verified by an actual `xcodebuild` per product; macOS `swift test` 1749/258 green proves
non-regression; the two guards + memory band are the only source changes and are
TDD-pinned. Honest caveats: builds are compile-green only — AVAudioSession, the UI shell,
and on-device MLX memory behaviour are Phase-2 verify-by-launch; the 4 GB mobile ceiling
and the ≥16 GB Lil threshold are tunable constants, not yet device-measured). Prior: Unknown._
