# Voice on iOS + the Vision Pro experience — challenged plan draft

Status: DRAFT for Kev's calls — challenger-shaped 2026-07-18, not yet begun.
Prior art pattern: brain-at-home (SPEC → auditor pass → phase-gated spikes).

## Kev's instinct (2026-07-18)

> Vision Pro should just be voice only with the avatar — which would be kind
> of cool. Maybe some text as well, but I'm not certain.

## The challenger's verdict: RECONSIDER the specifics, keep the destination

**Voice-forward, not voice-only.** The tab shell is load-bearing infrastructure,
not chrome: it's the only surface for the 2.1GB Lil download progress, first-run
onboarding, mic/Speech TCC grants, the age gate, document ingest, memory
management, and the web-search/egress consent toggles (Phase 17a). You cannot
narrate a download bar or flip a consent switch by speech alone. So: visionOS
ships the SAME tab shell for setup/management, with a voice-forward
default/immersive view layered on top. Kev's immersive spoken-avatar instinct
survives fully; only "delete the tabs" gets cut. "Maybe some text" resolves to
the karaoke caption band (KaraokeReadingText exists on Mac) — captions-on
during speech, no chat log in the voice view.

## The Kokoro/TTS decision (evidence-backed)

- onnxruntime.xcframework at the CURRENT resolved 1.24.2 ships ios-arm64 +
  sim + macos slices and **no visionOS slice** (artifact inspected). Eight
  minor versions added nothing — "wait for upstream" is dead.
- Owning a from-source xrOS onnxruntime fork = permanent maintenance tax.
  Reject as v1 dependency.
- **★ SCOUT K0 RAN 2026-07-18 — VERDICT: FEASIBLE-NOW.** Three independent
  MIT-licensed Swift ports of Kokoro-on-mlx-swift exist:
  **Blaizzy/mlx-audio-swift** (711★, pushed 2026-07-17, full Kokoro v1.0 under
  `Sources/MLXAudioTTS/Models/StyleTTS2/`, modular — take just MLXAudioTTS,
  keep our G2P) · mlalma/kokoro-ios (273★, own LSTM/MLXSTFT/ConvWeighted,
  ~3.3× realtime on iPhone 13 Pro, but pins mlx-swift exact:0.30.2) ·
  mweinbach/kokoro-swift (MLX + CoreML reference). Op coverage: NO GAPS —
  ALBERT/LSTM/conv1d covered by MLXNN, iSTFT hand-rolled on MLXFFT in both
  major ports. onnxruntime-on-visionOS confirmed dead ecosystem-wide
  (ms/onnxruntime#19313, sherpa-onnx#1946). Honest opens: neither package
  declares visionOS (pure mlx-swift → high-confidence compile, unverified);
  weight format differs (safetensors + per-voice .npy vs our staged ONNX +
  voices-v1.0.bin — semantics identical, needs a shim or second artifact);
  bm_daniel ear-check owed; dep-graph fit vs mlx-swift-lm 3.31.4 (vendoring
  the MIT sources sidesteps it).
  **Next concrete step (K1 spike)**: scratch target + MLXAudioTTS (or vendored
  StyleTTS2 sources) + visionOS platforms line; one bm_daniel sentence on
  macOS A/B'd by ear against the ONNX path. Settles dep fit, visionOS
  compile, and quality in one experiment. Swap only KokoroSynthesizer's
  infer() seam; G2P + SpeechChunker stay ours.
- (b) AVSpeech on visionOS demotes to the stopgap while K1 runs; (a) onnx
  fork is DELETED as an option; (4) Mac-as-TTS stays a remote-quality
  fallback only.
- **Reject (c) mute-with-captions**: a voice-first avatar that listens but
  cannot speak contradicts the pitch.

## Landmines named (do not rediscover)

1. **Self-echo**: zero AEC/voiceProcessing anywhere in the repo. Open-mic on
   Vision Pro external speakers hears its own TTS → self-triggering. v1 is
   push-to-talk / tap-to-barge-in (matches the Mac's manual barge-in).
2. **Server-fallback privacy leak**: Apple Speech silently falls back to
   SERVER recognition where supportsOnDeviceRecognition is false — a
   "nothing leaves the device" brand/App-Review landmine on a listening
   surface. Assert on-device, fail LOUD, never silent-fallback.
3. **Thermals**: WhisperKit(ANE) + TTS + volumetric RealityKit (+ MLX) on a
   battery headset is the "demos then melts" risk. The Mac runs this plugged
   in and cooled. Gate on a measured 10-minute burn, not vibes.
4. **AVAudioSession is genuinely unbuilt**: AppleSpeechTranscriber runs a bare
   AVAudioEngine with no session config/interruption/route handling (repo-wide
   zero hits). This is the shared keystone and real work.

## The phase gates (proposed)

- **Spike V0 (run FIRST, throwaway, parallel-safe)**: windowed avatar +
  Apple Speech STT + AVSpeech TTS on visionOS + a 10-minute continuous burn.
  RESULTS.md gates the whole flagship: shared-space mic behaviour, permission
  UX, thermals, self-echo severity. Cheapest honest signal — green iOS voice
  would answer NONE of these.
- **Scout K0 (half-day)**: MLX-Kokoro feasibility. Could delete the onnx-fork
  option before anyone builds it.
- **Phase B (iOS voice, phase-gated like brain-at-home)**:
  - B-A1: AVAudioSession echo spike incl. interruption/route NEGATIVE paths.
  - B-1: STT + captions, push-to-talk.
  - B-2: Kokoro(iOS)-vs-AVSpeech decided on MEASURED iOS thermals.
  - B-3: voice-mode UI (avatar hero + karaoke), composing the Mac's pieces.
- **Phase D (visionOS flagship)**: voice-forward view over the retained shell,
  volumetric avatar, TTS per K0/B-2 outcome.

## Kev's calls needed

1. Voice-forward-over-shell scoping — accept the challenger's cut of
   "voice-only"?
2. Green-light **Spike V0** (visionOS throwaway: STT + AVSpeech + windowed
   avatar + 10-min burn) and **Spike K1** (MLX-Kokoro A/B — K0 scouted
   FEASIBLE-NOW, see above) as the next voice work, before any Phase B
   build-out?
3. MLX-Kokoro adoption shape if K1 passes: SwiftPM dep on mlx-audio-swift
   vs vendoring the MIT StyleTTS2 sources (dep-graph safety)?

_Drafted: Kev + claude-fable-5, 2026-07-18. Challenger evidence: onnxruntime
artifact inspection, AppleSpeechTranscriber.swift:69/109/207, VoiceModeView
manual barge-in, project.yml mobile targets. K0 scout evidence: repo links in
the Kokoro section, all fetched live 2026-07-18. Confidence 0.8 in the shape;
every measured claim gated on its named spike._
