#!/usr/bin/env python3
"""Blender headless: GLB -> one USDZ per animation clip, for M1K3 companions.

Why per-clip files: macOS's system OpenUSD glTF reader keeps only the FIRST
animation clip, and clip NAMES die in the USD round-trip — so the FILENAME is
the clip identity. M1K3 loads one mesh + N clip files and cross-binds the
animations onto a single rig at runtime (CompanionAvatarView / scratch/usdz-probe).

Usage (Blender 4.4+ required; the system usdcat is NOT a substitute):
    /Applications/Blender.app/Contents/MacOS/Blender -b -P export_clips.py -- \
        <model.glb> <outdir> [Clip1,Clip2,...] [--retime N]

  - <model.glb>   source model (e.g. app/3d/.../Gecko_Animations.glb)
  - <outdir>      directory to write <Clip>.usdz files into (created if absent)
  - clips         OPTIONAL comma list; omit to export every action found.
                  Quaternius dialect: Idle_A,Idle_B,Walk,Run,Jump,Fear,Sit,Clicked
                  Khronos Fox dialect: Survey,Walk,Run
                  (M1K3 only PLAYS the gait-mapped subset — see ClipMapper —
                   but extra clips are harmless, just bytes.)
  - --retime N    OPTIONAL keyframe stretch. The Quirky Series pack ships EVERY
                  take compressed to 11 frames / 0.417 s (GLB, FBX, and the Unity
                  meta all agree — it's how the pack is authored), so a full walk
                  cycle plays in under half a second and reads as broken on-device
                  (the 2026-06-21 Gecko "doesn't animate" failure, root-caused
                  2026-07-10). N=4 stretches those takes to ~1.67 s. Khronos Fox
                  has real durations — leave it at the default 1.0.

Output: ~1.3 MB per Quaternius model (8 clips; mesh+texture duplicated per file),
Fox ~390 KB (3 clips). Models import Z-up (Blender) and M1K3 corrects to RealityKit
Y-up IN THE VIEW (CompanionAvatarView.blenderZUpCorrection), not here.

Verify after: Quick Look each .usdz, or scratch/usdz-probe/out/rkprobe for a
headless RealityKit load + animation inventory.

Signed: Kev + claude-opus-4-8, 2026-06-12, Confidence 0.8 (proven on 9 models /
67 clips as the scratch spike; productionised out of scratch/usdz-probe).
Prior: Kev + claude-fable-5 (scratch/usdz-probe/export_clips.py).
"""

import os
import sys

import bpy


def main() -> int:
    if "--" not in sys.argv:
        print("PROBE-FAIL: pass args after `--` (see the docstring for usage)")
        return 2
    argv = sys.argv[sys.argv.index("--") + 1 :]
    retime = 1.0
    if "--retime" in argv:
        i = argv.index("--retime")
        retime = float(argv[i + 1])
        del argv[i : i + 2]
    if len(argv) < 2:
        print("PROBE-FAIL: need <model.glb> <outdir> [Clip1,Clip2,...] [--retime N]")
        return 2
    src, outdir = argv[0], argv[1]
    wanted = argv[2].split(",") if len(argv) > 2 else None
    # The docstring promises this; Blender's own error when it's missing is a
    # baffling "couldn't move from temporary location".
    os.makedirs(outdir, exist_ok=True)

    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=src)

    armatures = [o for o in bpy.data.objects if o.type == "ARMATURE"]
    if not armatures:
        print("PROBE-FAIL: no armature found — is this a rigged, animated GLB?")
        return 1
    arm = armatures[0]
    if arm.animation_data is None:
        arm.animation_data_create()

    actions = {a.name: a for a in bpy.data.actions}
    print("PROBE actions found:", sorted(actions))
    names = wanted or sorted(actions)

    exported, skipped = 0, []
    for name in names:
        action = actions.get(name)
        if action is None:
            print(f"PROBE-SKIP: no action named {name}")
            skipped.append(name)
            continue
        arm.animation_data.action = action
        # Blender 4.4 slotted actions: an action needs its slot assigned too.
        if hasattr(arm.animation_data, "action_slot") and action.slots:
            arm.animation_data.action_slot = action.slots[0]
        if retime != 1.0:
            # Stretch in keyframe space (not scene fps) so the USD export still
            # bakes a sample per frame — Blender's spline interpolation fills the
            # stretched span instead of RealityKit lerping 11 sparse samples.
            for fc in action.fcurves:
                for kp in fc.keyframe_points:
                    kp.co.x *= retime
                    kp.handle_left.x *= retime
                    kp.handle_right.x *= retime
        start, end = action.frame_range
        bpy.context.scene.frame_start = int(start)
        bpy.context.scene.frame_end = max(int(end), int(start) + 1)
        out = f"{outdir}/{name}.usdz"
        bpy.ops.wm.usd_export(
            filepath=out,
            export_animation=True,
            export_armatures=True,
            export_materials=True,
            selected_objects_only=False,
        )
        fps = bpy.context.scene.render.fps
        seconds = (bpy.context.scene.frame_end - int(start)) / fps
        short = "  PROBE-WARN: <0.5s — compressed take? see --retime" if seconds < 0.5 else ""
        print(f"PROBE-OK: {out} frames {int(start)}-{int(end)} (~{seconds:.2f}s){short}")
        exported += 1

    print(f"PROBE-DONE: exported {exported}, skipped {len(skipped)}"
          + (f" ({', '.join(skipped)})" if skipped else ""))
    # Non-zero only if NOTHING exported — a partial run (some clips missing) is
    # still useful and shouldn't fail a batch.
    return 0 if exported else 1


if __name__ == "__main__":
    sys.exit(main())
