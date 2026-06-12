#!/usr/bin/env python3
"""Blender headless: GLB -> one USDZ per animation clip, for M1K3 companions.

Why per-clip files: macOS's system OpenUSD glTF reader keeps only the FIRST
animation clip, and clip NAMES die in the USD round-trip — so the FILENAME is
the clip identity. M1K3 loads one mesh + N clip files and cross-binds the
animations onto a single rig at runtime (CompanionAvatarView / scratch/usdz-probe).

Usage (Blender 4.4+ required; the system usdcat is NOT a substitute):
    /Applications/Blender.app/Contents/MacOS/Blender -b -P export_clips.py -- \
        <model.glb> <outdir> [Clip1,Clip2,...]

  - <model.glb>   source model (e.g. app/3d/.../Gecko_Animations.glb)
  - <outdir>      directory to write <Clip>.usdz files into (created if absent)
  - clips         OPTIONAL comma list; omit to export every action found.
                  Quaternius dialect: Idle_A,Idle_B,Walk,Run,Jump,Fear,Sit,Clicked
                  Khronos Fox dialect: Survey,Walk,Run
                  (M1K3 only PLAYS the gait-mapped subset — see ClipMapper —
                   but extra clips are harmless, just bytes.)

Output: ~1.3 MB per Quaternius model (8 clips; mesh+texture duplicated per file),
Fox ~390 KB (3 clips). Models import Z-up (Blender) and M1K3 corrects to RealityKit
Y-up IN THE VIEW (CompanionAvatarView.blenderZUpCorrection), not here.

Verify after: Quick Look each .usdz, or scratch/usdz-probe/out/rkprobe for a
headless RealityKit load + animation inventory.

Signed: Kev + claude-opus-4-8, 2026-06-12, Confidence 0.8 (proven on 9 models /
67 clips as the scratch spike; productionised out of scratch/usdz-probe).
Prior: Kev + claude-fable-5 (scratch/usdz-probe/export_clips.py).
"""

import sys

import bpy


def main() -> int:
    if "--" not in sys.argv:
        print("PROBE-FAIL: pass args after `--` (see the docstring for usage)")
        return 2
    argv = sys.argv[sys.argv.index("--") + 1 :]
    if len(argv) < 2:
        print("PROBE-FAIL: need <model.glb> <outdir> [Clip1,Clip2,...]")
        return 2
    src, outdir = argv[0], argv[1]
    wanted = argv[2].split(",") if len(argv) > 2 else None

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
        print(f"PROBE-OK: {out} frames {int(start)}-{int(end)}")
        exported += 1

    print(f"PROBE-DONE: exported {exported}, skipped {len(skipped)}"
          + (f" ({', '.join(skipped)})" if skipped else ""))
    # Non-zero only if NOTHING exported — a partial run (some clips missing) is
    # still useful and shouldn't fail a batch.
    return 0 if exported else 1


if __name__ == "__main__":
    sys.exit(main())
