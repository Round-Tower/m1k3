# Companion pipeline — GLB → USDZ-per-clip → RealityKit

Turns a rigged, animated GLB into the per-clip USDZ assets M1K3's 3D companions
load. Productionised out of `scratch/usdz-probe/` (which proved it on 9 models /
67 clips). The `/add-companion` skill drives this end-to-end; this README is the
ground truth it leans on.

## The one-paragraph why

macOS's system OpenUSD glTF reader keeps only the **first** animation clip, and
clip **names die** in the USD round-trip. So we export **one USDZ per clip** and
treat the **filename as the clip identity**. At runtime M1K3 loads one mesh +
N clip files and **cross-binds** each animation onto a single rig
(`CompanionAvatarView` / `scratch/usdz-probe`). One mesh + N clip files is the
architecture.

## Convert

```bash
/Applications/Blender.app/Contents/MacOS/Blender -b -P export_clips.py -- \
  "<model.glb>" "<outdir>" <Clip1,Clip2,...>
```

- **Blender 4.4+ is the converter** — the system `usdcat` is NOT a substitute
  (it keeps only clip 1). The script handles Blender 4.4 slotted actions.
- Omit the clip list to export every action in the file.
- Exit code is non-zero only if *nothing* exported; a partial run (some named
  clips absent) still writes what it found and succeeds.

### Clip dialects

M1K3 maps an `AvatarState` to a gait, then a dialect maps the gait to a clip
name (`ClipMapper` / `CompanionDialect`). Export at least the clips the dialect
names:

| Dialect      | Clips to export                                      | Source                                   |
|--------------|------------------------------------------------------|------------------------------------------|
| `quaternius` | `Idle_A,Idle_B,Walk,Run,Jump,Fear,Sit,Clicked`       | `app/3d/Quirky-Series-FREE-Animals.../Animations/<Name>_Animations.glb` |
| `fox`        | `Survey,Walk,Run`                                    | Khronos Fox (`site/vendor/Fox.glb`)      |

Extra clips are harmless (just bytes — M1K3 only *plays* the gait-mapped subset).
`Sit`/`Clicked` are exported for forward-looking gaits but not yet played.

## Verify

- **Quick Look** each `.usdz` (Finder → space) — confirms mesh, materials,
  and that the animation plays.
- **Headless RealityKit load + inventory:** `scratch/usdz-probe/out/rkprobe`
  (`swift run -c release rkprobe <files...>`).

## Wire into the app

1. Copy the `.usdz` files to `macos/Sources/M1K3Avatar/Companions/<Name>/`.
   They're **package resources** (`Package.swift` `resources: [.copy("Companions")]`)
   — Xcode re-resolves on its own, **no xcodegen** needed.
2. Add a `CompanionSpec.<name>` (id = the folder name) and append it to
   `CompanionSpec.all`. The Settings picker auto-detects it via
   `CompanionAssets.isInstalled` — no picker wiring.
3. `swift test --filter CompanionTests` — `everyEmittedClipIsBundled` fails loudly
   if the spec's clips don't cover every gait the dialect can emit.

## Gotchas (hard-won — do not rediscover)

- **Z-up → Y-up:** models import Z-up (Blender) and load standing on their nose
  in RealityKit. The correction is **in the view**
  (`CompanionAvatarView.blenderZUpCorrection`, −90° about X), not baked into the
  asset — uniform across companions because they share this export. A Y-up source
  (some Sketchfab exports) would need that constant per-spec.
- **Filename = clip identity.** Clip names become generic in USD; never rely on
  the in-USD animation name.
- **Size:** ~1.3 MB per Quaternius model (8 clips, mesh+texture duplicated per
  file); Fox ~390 KB. If it ever matters: USD composition (one mesh layer +
  per-clip animation layers + `usdzip`) is the optimisation, not yet needed.
- **New app-target *files*** (e.g. splitting `CompanionAvatarView.swift` out of
  `AvatarView.swift`) DO need `xcodegen` — and Xcode must be quit first (it
  desyncs an open project). Adding *assets* to the package does not.

Signed: Kev + claude-opus-4-8, 2026-06-12, Confidence 0.85 (converter re-proven
end-to-end on Gecko during productionisation; wire-in steps match the shipped Fox).
Prior: Kev + claude-fable-5 (scratch/usdz-probe/README.md).
