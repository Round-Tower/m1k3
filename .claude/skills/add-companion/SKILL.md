---
name: add-companion
description: Add a new 3D companion creature to the M1K3 Mac app, end to end. Use when Kev says "add a companion", "ship the gecko/colobus/etc", "new companion", or wants to convert a GLB model into a playable voice-mode companion. Drives the GLB→USDZ-per-clip pipeline, wires the CompanionSpec, and verifies.
allowed-tools: Read, Edit, Write, Bash, Grep, Glob
---

# Add a companion

Take a rigged GLB → a playable opt-in voice-mode companion in the M1K3 Mac app.
Companions are an **opt-in skin for voice mode** — the procedural pixel face stays
M1K3's default brand face everywhere else. Pipeline ground truth:
`macos/tools/companion-pipeline/README.md`. The hard half (GLB→USDZ→RealityKit) is
proven; this skill is the repeatable wire-up so the gotchas don't get rediscovered.

## Before you start

- **Pick the model.** Quaternius pack: `app/3d/Quirky-Series-FREE-Animals-v1.4 2/3D Files/GLTF/Animations/<Name>_Animations.glb`. Khronos Fox: `site/vendor/Fox.glb`.
- **Pick the dialect** (decides which clips to export and how gaits map):
  - `quaternius` → clips `Idle_A,Idle_B,Walk,Run,Jump,Fear,Sit,Clicked`
  - `fox` → clips `Survey,Walk,Run`
  - A new vocabulary → add a `case` to `CompanionDialect.clipName(for:)` first
    (every `CompanionGait` must map to a clip the model actually has).
- **Confirm Xcode state** if you'll split view files later: `pgrep -x Xcode`. Adding
  *assets* needs no xcodegen; new *app-target Swift files* do (quit Xcode first).

## Steps

### 1. Convert (Blender 4.4 headless)

```bash
OUT="macos/Sources/M1K3Avatar/Companions/<Name>"
mkdir -p "$OUT"
/Applications/Blender.app/Contents/MacOS/Blender -b -P macos/tools/companion-pipeline/export_clips.py -- \
  "<model.glb>" "$OUT" <Clip1,Clip2,...>
```

Expect `PROBE-OK` per clip and `PROBE-DONE: exported N`. The system `usdcat` is NOT
a substitute (keeps only clip 1). Models import Z-up — that's corrected in the view,
not the asset (see gotchas).

### 2. QA the render

- **Headless image preview** (works with no GUI/locked Mac):
  `macos/tools/companion-pipeline/preview_usdz.swift "$OUT"/*.usdz -o /tmp/previews`
  — renders each clip to a PNG; eyeball mesh/materials/pose.
- **Quick Look** each `.usdz` (it shows mesh + plays the animation), OR
- headless RealityKit inventory: `cd scratch/usdz-probe/out/rkprobe && swift run -c release rkprobe "$OUT"/*.usdz` — proves animations were harvested (the Gecko failure mode).

Watch for: missing/black materials, a frozen mesh (no animation harvested), wildly
wrong scale. If a clip looks broken, re-export just that clip.

### 3. Add the spec

Edit `macos/Sources/M1K3Avatar/CompanionSpec.swift`:

```swift
public static let <name> = CompanionSpec(
    id: "<Name>",            // MUST equal the Companions/<Name> folder
    displayName: "<Name>",
    dialect: .quaternius,    // or .fox
    clips: ["Idle_A", "Idle_B", "Walk", "Run", "Jump", "Fear", "Sit", "Clicked"]
)
```

Append it to `CompanionSpec.all`. **No picker wiring** — `SettingsView`'s
`companionSection` filters `all` by `CompanionAssets.isInstalled`, so a bundled
companion appears automatically. Default selection (pixel face) is unchanged.

### 4. Verify

```bash
cd macos && swift test --filter CompanionTests
```

- `everyEmittedClipIsBundled` fails loudly if the spec's `clips` don't cover every
  gait the dialect emits — fix the `clips` list or the dialect mapping.
- `installedReflectsBundle` only asserts Fox/Gecko today; extend it if you want the
  new companion pinned as installed.

Then **verify-by-launch** (the renderer is never unit-tested): build the app
(`xcodebuild build -project macos/M1K3.xcodeproj -scheme M1K3 -destination 'platform=macOS' CODE_SIGNING_ALLOWED=NO | xcbeautify`),
⌘R → Settings → Voice companion → pick the new one → ⌘⇧V. Confirm it stands upright,
is lit, animates, and shifts gait as M1K3 listens/thinks/speaks.

### 5. Commit

New `.usdz` assets + the `CompanionSpec` edit. The pixel face and chat are untouched.
Sign significant additions (MurphySig).

## Gotchas (do NOT rediscover)

- **Z-up → Y-up** is corrected **in the view** (`CompanionAvatarView.blenderZUpCorrection`,
  −90° X), uniform because all companions share the Blender export. A Y-up source needs
  it per-spec — that's the future `zUpCorrection` flag, not built yet.
- **Filename = clip identity** (names die in USD). The folder/file names are the contract.
- **`baseYaw` / `targetSize`** in `CompanionAvatarView` are by-eye; a new model may sit
  facing the wrong way — flip `baseYaw`'s sign or nudge `targetSize`. These are the only
  per-look knobs.
- **`AvatarView.swift` is already >400 lines.** When it grows, extract
  `CompanionAvatarView.swift` + `CompanionScene.swift` — but that needs xcodegen (quit
  Xcode first; the desync trap).
- **Bundled ⊋ emittable is fine** (`Sit`/`Clicked` load but aren't played yet) — extra
  clips are just bytes. Trim only if size matters.

## Reference

- Pipeline: `macos/tools/companion-pipeline/{export_clips.py,README.md}`
- Pure cores: `macos/Sources/M1K3Avatar/{CompanionSpec,ClipMapper,CompanionAssets}.swift`
- Renderer: `macos/M1K3App/AvatarView.swift` (`CompanionAvatarView`)
- Picker/wiring: `SettingsView.companionSection`, `VoiceModeView.avatar`
- Spike provenance: `scratch/usdz-probe/README.md`
