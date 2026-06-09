//
//  FaceExpression.swift
//  M1K3Avatar
//
//  The pure animation brain for the pixel-cube face. Given the AvatarState and a
//  time, returns each matrix cell's brightness (0…1) and a small depth pop. The
//  renderer multiplies the brightness by the emotion's accent colour, so "drawing"
//  the face is just lighting the right cells: two pupils that blink/look, and a
//  mouth that curves with emotion and opens when speaking.
//
//  Deterministic — every animation phase is derived from `time` (no RNG), so it's
//  fully unit-testable with no RealityKit.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.8, Prior: Unknown

import Foundation

public enum FaceExpression {
    /// Dim baseline so the whole matrix reads as a faint LED grid behind the face.
    private static let background: Float = 0.06

    /// Brightness 0…1 for a cell. Background unless the cell is part of an eye or
    /// the mouth, in which case it lights fully — with a subtle per-cell LED
    /// flicker scaled by brightness, so lit features shimmer like real LEDs while
    /// the dark backdrop stays calm.
    public static func intensity(col: Int, row: Int, state: AvatarState, time: Double) -> Float {
        let feature = max(
            eyeIntensity(col: col, row: row, state: state, time: time),
            mouthIntensity(col: col, row: row, state: state, time: time)
        )
        let base = max(background, feature)
        // ~7 Hz reads as live LED shimmer while staying smooth at the 30 fps the
        // view renders (faster would alias into a slow wobble).
        let flicker = 1 + 0.06 * Float(sin(time * 7 + cellPhase(col: col, row: row)))
        return min(max(base * flicker, 0), 1)
    }

    /// A fixed per-cell phase so each LED flickers slightly out of step (organic,
    /// not a uniform pulse). Deterministic — no RNG.
    private static func cellPhase(col: Int, row: Int) -> Double {
        (Double(col) * 12.9898 + Double(row) * 78.233)
            .truncatingRemainder(dividingBy: 6.2831853)
    }

    /// A subtle depth pop for lit features, giving the face a little life. Flat for
    /// background cells so the matrix backdrop stays still.
    public static func displacement(col _: Int, row _: Int, state: AvatarState, time: Double) -> Float {
        // A UNIFORM breath (same Z for every cell) so eyes + mouth stay perfectly
        // aligned — per-cell bobbing made the features drift out of register. A
        // little quicker + deeper while active.
        let speed = state.activity.isActive ? 2.4 : 1.4
        let depth: Float = state.activity.isActive ? 0.018 : 0.012
        return depth * Float(sin(time * speed))
    }

    // MARK: - Eyes

    private static func eyeIntensity(col: Int, row: Int, state: AvatarState, time: Double) -> Float {
        max(
            oneEye(col: col, row: row, anchor: FaceGrid.leftEye, state: state, time: time),
            oneEye(col: col, row: row, anchor: FaceGrid.rightEye, state: state, time: time)
        )
    }

    private static func oneEye(
        col: Int, row: Int,
        anchor: (col: Int, row: Int),
        state: AvatarState, time: Double
    ) -> Float {
        // Closed (blink or sleepy): a flat 3-cell line at the eye row.
        if isBlinking(time: time) || state.emotion == .sleepy {
            return (row == anchor.row && abs(col - anchor.col) <= 1) ? 0.9 : 0
        }

        // Happy / love / excited: an upturned arc ^ instead of a round pupil.
        if state.emotion == .happy || state.emotion == .excited || state.emotion == .love {
            let onArc = (row == anchor.row && abs(col - anchor.col) == 1)
                || (row == anchor.row - 1 && col == anchor.col)
            return onArc ? 1 : 0
        }

        // Round pupil, drifting up + side-to-side while thinking.
        var pupilCol = anchor.col
        var pupilRow = anchor.row
        if state.activity == .thinking {
            pupilRow -= 1
            pupilCol += Int(sin(time * 1.3).rounded()) // -1 / 0 / +1
        }
        return (col == pupilCol && row == pupilRow) ? 1 : 0
    }

    /// A short blink window (~140 ms every 3.5 s), derived from `time`.
    private static func isBlinking(time: Double) -> Bool {
        let phase = time.truncatingRemainder(dividingBy: 3.5)
        return phase >= 0 && phase < 0.14
    }

    // MARK: - Mouth

    private static func mouthIntensity(col: Int, row: Int, state: AvatarState, time: Double) -> Float {
        guard FaceGrid.mouthCols.contains(col) else { return 0 }
        return mouthRows(col: col, state: state, time: time).contains(row) ? 1 : 0
    }

    /// The rows lit for the mouth at a given column: a curve (smile/frown/flat)
    /// plus extra rows below while speaking, so the mouth visibly opens.
    private static func mouthRows(col: Int, state: AvatarState, time: Double) -> Set<Int> {
        let distance = abs(col - FaceGrid.centreCol)
        // +1 smile (ends rise), -1 frown (ends fall), 0 flat.
        let curl = curlSign(for: state.emotion)
        let curveRow = FaceGrid.mouthRow - curl * (distance / 2)

        var rows: Set<Int> = [curveRow]
        if state.activity == .speaking {
            // Opens 1…3 rows, oscillating with time → looks like talking.
            let open = 1 + Int((1.5 * (0.5 + 0.5 * sin(time * 9))).rounded())
            for extra in 1 ... open {
                rows.insert(curveRow + extra)
            }
        }
        return rows
    }

    private static func curlSign(for emotion: AvatarEmotion) -> Int {
        switch emotion {
        case .happy, .excited, .love: 1
        case .sad, .angry: -1
        default: 0
        }
    }
}
