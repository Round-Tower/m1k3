//
//  PixelFont.swift
//  M1K3App
//
//  M1K3's bundled type faces, registered at launch via CoreText so `Font.custom`
//  resolves them regardless of where the bundler drops the files in Resources:
//    • Silkscreen (OFL) — the pixel accent face for wordmark / accent moments.
//    • OpenDyslexic (OFL) — the optional "Dyslexia-friendly" reading mode.
//  Call sites are `Font.pixel(_:)` and `Font.dyslexic(_:)`.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-08, Confidence 0.8, Prior: Unknown
//  Review: Kev + claude-sonnet-4-6, 2026-06-08 — generalised the single-font
//  registrar into BundledFonts (Silkscreen + OpenDyslexic) for the reading modes.

import CoreText
import SwiftUI

enum BundledFonts {
    /// Family names (for `Font.custom`).
    static let pixelFamily = "Silkscreen"
    static let dyslexicFamily = "OpenDyslexic"

    /// Every bundled face file (without extension) and its extension.
    private static let faces: [(name: String, ext: String)] = [
        ("Silkscreen-Regular", "ttf"),
        ("Silkscreen-Bold", "ttf"),
        ("OpenDyslexic-Regular", "otf"),
        ("OpenDyslexic-Bold", "otf"),
    ]

    /// Register all bundled faces. Idempotent — re-registering a URL is a no-op.
    static func register() {
        for face in faces {
            guard let url = Bundle.main.url(forResource: face.name, withExtension: face.ext) else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }
}

extension Font {
    /// M1K3's pixel accent face. `fixedSize` keeps the pixels crisp (Dynamic Type
    /// scaling renders bitmap-style fonts fuzzy) — reserve it for short accents.
    static func pixel(_ size: CGFloat) -> Font {
        .custom(BundledFonts.pixelFamily, fixedSize: size)
    }

    /// OpenDyslexic — the dyslexia-friendly reading face. Uses `size:` (not fixed)
    /// so it still scales with Dynamic Type for body reading.
    static func dyslexic(_ size: CGFloat) -> Font {
        .custom(BundledFonts.dyslexicFamily, size: size)
    }
}
