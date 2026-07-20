# V0 spike — visionOS fundamentals (simulator slice)

Ran: 2026-07-18. Gate for: the voice-forward visionOS direction (PLAN-DRAFT.md).
Scope honesty: the sim can prove launch/render/layout; mic/STT, AVSpeech felt
quality, thermals, and self-echo are HEADSET-OWED — named below, not claimed.

## What ran

- Created the first Vision Pro simulator on this Mac (device type
  `Apple-Vision-Pro-4K`; the non-4K type is incompatible with xrOS 26.5).
- Built `M1K3visionOS` for the visionOS sim (BUILD SUCCEEDED, first local
  visionOS build — previously CI-compile-only), installed, wrote the
  `hasChosenBrain`/`selectedBrain=mini` defaults (bundle id
  `app.m1k3.visionos`), launched.

## Results

| Gate | Outcome |
|---|---|
| Shell runs on visionOS | **YES — FIRST RUN EVER.** Window renders in the sim's living room: wordmark, empty-state copy, input bar, tab surface. Screenshot: session scratch `v0-visionos-first-run.png` (sent to Kev) |
| Runtime errors | **NONE** — Metal-simulator connections active, RealityKit CoreRE metallib loads (8ms), no app faults in `log show` |
| MLXAudioTTS on visionOS | (from K1) **compiles clean** against the visionOS Simulator SDK |

## Findings (eyeball, Kev's read owed)

1. **The avatar hero renders dark/near-invisible — ROOT-CAUSED (code-read,
   2026-07-18 late):** `AvatarView.swift:108-111` frames the face with an
   in-scene `PerspectiveCamera` at `[0,0,2.6]`. visionOS IGNORES in-scene
   cameras — shared-space RealityView renders content at true world scale with
   the user's eyes as the camera — so the ~1.3m-wide face grid sits unframed
   in the window volume and nothing readable lands on screen. This is the
   exact "camera-based framing may need a visionOS branch" risk the recon
   flagged. Fix shape (voice-forward work, not done here): `#if os(visionOS)`
   skip the camera + scale the grid root to fit the view bounds (RealityView
   proposed size / GeometryReader3D), or promote the avatar to a volumetric
   scene for Phase D. Affects CompanionAvatarView the same way (same framing
   pattern).
2. **Tab presentation looks wrong**: a row of dark rounded squares across the
   window top rather than the visionOS leading-edge tab ornament —
   `.sidebarAdaptable` may need a visionOS-specific arm. Layout-polish item
   for the voice-forward view work.

## Headset-owed (cannot be answered here — the real V0 questions)

Shared-space mic sustain for continuous STT · AVSpeech felt quality (the
stopgap voice) · 10-minute thermal burn (STT + TTS + RealityKit ± MLX) ·
self-echo severity on external speakers (no AEC exists in the repo) ·
on-device-recognition assert (the server-fallback privacy landmine).

## Verdict

**Sim slice: PASS with 2 visual findings.** The shell is real on visionOS, not
just compile-green. The voice-forward build can proceed against the sim for
layout; the four headset-owed questions gate the flagship and need hardware
(or TestFlight-on-Vision-Pro once the iOS lane exists — Phase E).

_Signed: Kev + claude-fable-5, 2026-07-18 (V0 sim slice), Confidence 0.8
(launch/render/log claims machine-verified this session; the two visual
findings are one-screenshot eyeball reads; every headset question explicitly
deferred, not answered). Prior: none (new spike)._
