//
//  MenuBarGlyph.swift
//  M1K3App
//
//  M1K3's status-bar mark. Drawn from a pixel grid (not an asset) so it stays
//  crisp at any menu-bar height and ships as a TEMPLATE image — macOS tints it
//  for light/dark + vibrancy. The "M" replicates the website favicon's 5×5
//  phosphor mark (site/favicon.svg) so the bar matches the brand; the face is an
//  A/B alternative. VERIFY-BY-LAUNCH: purely visual, judged by eye at ⌘R.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.7, Prior: Unknown

import AppKit

/// Which mark the menu-bar item shows. Raw values are the persisted @AppStorage
/// choice; `CaseIterable` drives the Settings picker.
enum MenuBarGlyphStyle: String, CaseIterable, Identifiable {
    case pixelM
    case pixelFace

    static let storageKey = "menuBar.glyphStyle"

    var id: String {
        rawValue
    }

    var label: String {
        switch self {
        case .pixelM: "Pixel M"
        case .pixelFace: "Pixel face"
        }
    }

    /// Pixel grid, top row first. "#" is a lit pixel, anything else is empty.
    var grid: [String] {
        switch self {
        case .pixelM:
            // Exactly the favicon's 5×5 "M" (site/favicon.svg).
            ["#...#",
             "##.##",
             "#.#.#",
             "#...#",
             "#...#"]
        case .pixelFace:
            // Two eyes + an upturned mouth — M1K3's pixel face, minimised.
            [".....",
             ".#.#.",
             ".....",
             "#...#",
             ".###."]
        }
    }

    /// Render the grid to a template NSImage sized to fit `pointSize` (the menu-
    /// bar height). Cells are floored to whole points so pixels stay sharp.
    /// `@MainActor`: `NSImage.lockFocus()` is main-thread-only. Memoised by
    /// (style, size) — the same glyph is asked for on every render pass, so the
    /// drawing context cost is paid once.
    @MainActor
    func image(pointSize: CGFloat = 16) -> NSImage {
        let cacheKey = "\(rawValue)@\(pointSize)"
        if let cached = Self.cache[cacheKey] { return cached }

        let rows = grid.count
        let cols = grid.map(\.count).max() ?? 0
        // Empty grid can't be drawn — lockFocus on a zero-dimension image is
        // undefined. The hardcoded grids never hit this; guard anyway.
        guard rows > 0, cols > 0 else { return NSImage() }
        let cell = max(1, (pointSize / CGFloat(max(rows, cols))).rounded(.down))
        let size = NSSize(width: cell * CGFloat(cols), height: cell * CGFloat(rows))

        let image = NSImage(size: size)
        image.lockFocus()
        NSColor.black.setFill()
        for (rowIndex, row) in grid.enumerated() {
            for (colIndex, char) in row.enumerated() where char == "#" {
                // NSImage origin is bottom-left; grid row 0 is the top row.
                NSRect(x: CGFloat(colIndex) * cell,
                       y: CGFloat(rows - 1 - rowIndex) * cell,
                       width: cell, height: cell).fill()
            }
        }
        image.unlockFocus()
        image.isTemplate = true // let macOS tint for light/dark menu bars
        Self.cache[cacheKey] = image
        return image
    }

    @MainActor private static var cache: [String: NSImage] = [:]
}
