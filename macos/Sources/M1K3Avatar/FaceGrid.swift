//
//  FaceGrid.swift
//  M1K3Avatar
//
//  The LED-matrix geometry for M1K3's pixel-cube face. A fixed grid of cells — the
//  renderer builds one cube per cell once, then lights them (via FaceExpression's
//  per-cell intensity) to "draw" eyes and a mouth that emote. Pure data; no
//  RealityKit, safe to `swift test`.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.85, Prior: Unknown

public enum FaceGrid {
    /// Matrix width / height in cells. Odd width so the face has a true centre column.
    public static let cols = 13
    public static let rows = 11

    /// Pupil "home" positions (origin top-left; col increases right, row down).
    public static let leftEye = (col: 4, row: 3)
    public static let rightEye = (col: 8, row: 3)

    /// The mouth's resting row and horizontal span.
    public static let mouthRow = 7
    public static let mouthCols = 3 ... 9

    /// Centre column (used to mirror the mouth curve).
    public static var centreCol: Int {
        cols / 2
    }

    /// Every cell in the matrix, row-major.
    public static func allCells() -> [(col: Int, row: Int)] {
        var cells: [(col: Int, row: Int)] = []
        cells.reserveCapacity(cols * rows)
        for row in 0 ..< rows {
            for col in 0 ..< cols {
                cells.append((col: col, row: row))
            }
        }
        return cells
    }
}
