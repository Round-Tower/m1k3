#!/usr/bin/env swift
//
//  preview_usdz.swift — headless USDZ → PNG preview (SceneKit + Metal offscreen).
//
//  The QA step that needs no screen: renders each clip file to an image so a
//  converted companion can be eyeballed from a terminal, over SSH, in CI, or
//  while the Mac is locked (born in the 2026-07-09 companion jam, sending the
//  Inkfish to a pub). Quick Look shows animation but needs a GUI session;
//  rkprobe proves animations exist but shows nothing — this shows the MESH,
//  materials, and the pose a clip strikes at `--time`. CAVEAT: SceneKit's
//  offscreen snapshot does NOT evaluate skinned (skeletal) animation, so for
//  rigged companions every `--time` renders the bind pose — this tool shows
//  LOOK, not MOTION. Motion ground truth is `rkprobe --tick` (headless
//  RealityKit playback); proven 2026-07-11 when identical frames at two times
//  nearly mis-called the frozen-clip diagnosis.
//
//  Usage:
//    ./preview_usdz.swift <in.usdz>... [-o <outdir>] [--size N] [--time T]
//
//  Writes <clipname>.png beside each input (or into -o). SceneKit's default
//  camera does the framing — skinned-mesh bounding boxes lie (bind pose at
//  origin), so hand-rolled cameras aim at empty water; the default camera
//  frames the rendered scene and is the reliable path.
//
//  Signed: Kev + claude-fable-5, 2026-07-09, Confidence 0.85 (rendered the
//  Fox, Inkfish, and Sparrow first try; default-camera framing chosen after
//  two hand-rolled cameras missed — see the jam dir for the corpses).
//  Prior: scratch/jam-2026-07-09-1852/render2.swift (Kev + claude-fable-5).
//

import AppKit
import SceneKit

var inputs: [URL] = []
var outDir: URL?
var size = 1100
var time = 0.25

var iterator = CommandLine.arguments.dropFirst().makeIterator()
while let arg = iterator.next() {
    switch arg {
    case "-o": outDir = iterator.next().map { URL(fileURLWithPath: $0, isDirectory: true) }
    case "--size", "--time":
        // A non-numeric value would otherwise be silently eaten (and a .usdz
        // that followed it dropped from the render list) — fail loudly instead.
        guard let raw = iterator.next() else { fatalError("\(arg) needs a value") }
        if arg == "--size", let n = Int(raw) { size = n }
        else if arg == "--time", let t = Double(raw) { time = t }
        else { fatalError("\(arg) got a non-numeric value: \(raw)") }
    default: inputs.append(URL(fileURLWithPath: arg))
    }
}

guard !inputs.isEmpty else {
    print("usage: preview_usdz.swift <in.usdz>... [-o outdir] [--size N] [--time seconds]")
    exit(2)
}

if let outDir {
    try? FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true)
}

guard let device = MTLCreateSystemDefaultDevice() else {
    print("✗ no Metal device — preview needs a GPU")
    exit(1)
}

var failures = 0
for input in inputs {
    do {
        let scene = try SCNScene(url: input, options: [.checkConsistency: false])
        scene.background.contents = NSColor(calibratedRed: 0.07, green: 0.08, blue: 0.12, alpha: 1)

        let key = SCNNode()
        key.light = SCNLight()
        key.light!.type = .directional
        key.light!.intensity = 1400
        key.eulerAngles = SCNVector3(-0.9, 0.6, 0)
        scene.rootNode.addChildNode(key)
        let ambient = SCNNode()
        ambient.light = SCNLight()
        ambient.light!.type = .ambient
        ambient.light!.intensity = 500
        scene.rootNode.addChildNode(ambient)

        let renderer = SCNRenderer(device: device, options: nil)
        renderer.scene = scene
        // pointOfView stays nil → SceneKit's default camera frames the scene.
        let image = renderer.snapshot(
            atTime: time,
            with: CGSize(width: size, height: size),
            antialiasingMode: .multisampling4X
        )
        guard let tiff = image.tiffRepresentation,
              let rep = NSBitmapImageRep(data: tiff),
              let png = rep.representation(using: .png, properties: [:])
        else {
            print("✗ \(input.lastPathComponent): encode failed")
            failures += 1
            continue
        }
        let name = input.deletingPathExtension().lastPathComponent + ".png"
        let out = (outDir ?? input.deletingLastPathComponent()).appendingPathComponent(name)
        try png.write(to: out)
        print("✓ \(out.path) (\(png.count / 1024) KB)")
    } catch {
        print("✗ \(input.lastPathComponent): \(error.localizedDescription)")
        failures += 1
    }
}

exit(failures == 0 ? 0 : 1)
