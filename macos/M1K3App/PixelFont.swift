//
//  PixelFont.swift
//  M1K3App
//
//  M1K3's pixel accent face — Silkscreen (OFL, bundled). Used sparingly for
//  wordmark / accent moments, never body text (pixel fonts don't scale legibly).
//  Registered at launch via CoreText so it works regardless of where the bundler
//  drops the .ttf in Resources; `Font.pixel(_:)` is the call site.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-08, Confidence 0.8, Prior: Unknown

import CoreText
import SwiftUI

enum PixelFont {
    static let familyName = "Silkscreen"

    /// Register the bundled Silkscreen faces so `Font.custom` can resolve them.
    /// Idempotent — re-registering an already-registered URL is a harmless no-op.
    static func register() {
        for face in ["Silkscreen-Regular", "Silkscreen-Bold"] {
            guard let url = Bundle.main.url(forResource: face, withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }
}

extension Font {
    /// M1K3's pixel accent face. `fixedSize` keeps the pixels crisp (Dynamic Type
    /// scaling renders bitmap-style fonts fuzzy) — reserve it for short accents.
    static func pixel(_ size: CGFloat) -> Font {
        .custom(PixelFont.familyName, fixedSize: size)
    }
}
