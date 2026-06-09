import Foundation
import M1K3Avatar
import Testing

struct FaceExpressionTests {
    /// A time when the face is NOT blinking (phase ≥ 0.14 within the 3.5s cycle).
    private let openTime = 1.0
    /// A time inside the blink window (phase < 0.14).
    private let blinkTime = 0.05

    private func neutral(_ activity: AvatarActivity = .idle) -> AvatarState {
        AvatarState(emotion: .neutral, activity: activity)
    }

    @Test("intensity is clamped to 0...1 across the whole matrix")
    func intensityClamped() {
        let states = [
            neutral(.idle), neutral(.thinking), neutral(.speaking),
            AvatarState(emotion: .happy, activity: .idle),
            AvatarState(emotion: .sad, activity: .speaking),
            AvatarState(emotion: .sleepy, activity: .idle),
        ]
        for state in states {
            for time in stride(from: 0.0, through: 4.0, by: 0.21) {
                for cell in FaceGrid.allCells() {
                    let value = FaceExpression.intensity(col: cell.col, row: cell.row, state: state, time: time)
                    #expect(value >= 0)
                    #expect(value <= 1)
                }
            }
        }
    }

    @Test("pupils are lit when the eyes are open")
    func pupilsLitWhenOpen() {
        let left = FaceExpression.intensity(
            col: FaceGrid.leftEye.col, row: FaceGrid.leftEye.row, state: neutral(), time: openTime
        )
        #expect(left > 0.5)
    }

    @Test("a blink changes the eye shape (outer eye cell lights only when closed)")
    func blinkChangesEye() {
        // The cell beside the pupil is part of the closed-eye line, but dark when open.
        let beside = (col: FaceGrid.leftEye.col - 1, row: FaceGrid.leftEye.row)
        let whileOpen = FaceExpression.intensity(col: beside.col, row: beside.row, state: neutral(), time: openTime)
        let whileBlinking = FaceExpression.intensity(col: beside.col, row: beside.row, state: neutral(), time: blinkTime)
        #expect(whileOpen < 0.5)
        #expect(whileBlinking > 0.5)
    }

    @Test("happy curves the mouth up, sad curves it down (opposite ends)")
    func smileVersusFrown() throws {
        let endCol = FaceGrid.mouthCols.lowerBound // an outer mouth column
        let happy = AvatarState(emotion: .happy, activity: .idle)
        let sad = AvatarState(emotion: .sad, activity: .idle)

        let happyRow = litMouthRow(col: endCol, state: happy, time: openTime)
        let sadRow = litMouthRow(col: endCol, state: sad, time: openTime)

        // Row increases downward, so a smile's end sits ABOVE (smaller row) the frown's.
        #expect(happyRow != nil)
        #expect(sadRow != nil)
        #expect(try #require(happyRow) < sadRow!)
    }

    @Test("speaking opens the mouth, and the opening animates over time")
    func speakingAnimatesMouth() {
        let speaking = AvatarState(emotion: .neutral, activity: .speaking)
        let centre = FaceGrid.centreCol
        let counts = Set(stride(from: 0.0, through: 1.4, by: 0.1).map { time in
            litMouthCellCount(col: centre, state: speaking, time: time)
        })
        // The lit-row count at the mouth centre is not constant — it opens/closes.
        #expect(counts.count > 1)
        // And it opens beyond the single resting row at least sometimes.
        #expect((counts.max() ?? 0) > 1)
    }

    @Test("output is deterministic")
    func deterministic() {
        let state = AvatarState(emotion: .happy, activity: .speaking)
        for cell in FaceGrid.allCells() {
            let a = FaceExpression.intensity(col: cell.col, row: cell.row, state: state, time: 2.3)
            let b = FaceExpression.intensity(col: cell.col, row: cell.row, state: state, time: 2.3)
            #expect(a == b)
        }
    }

    // MARK: - Helpers

    /// The mouth lives below the eyes; scan only the lower band so eye cells (rows
    /// 2–3, incl. the happy arc that reaches the mouth's end columns) don't count.
    private static let mouthBand = 5 ..< FaceGrid.rows

    /// The first lit mouth row at a column, or nil.
    private func litMouthRow(col: Int, state: AvatarState, time: Double) -> Int? {
        Self.mouthBand.first { row in
            FaceExpression.intensity(col: col, row: row, state: state, time: time) > 0.5
        }
    }

    /// How many mouth-band rows are lit at a column.
    private func litMouthCellCount(col: Int, state: AvatarState, time: Double) -> Int {
        Self.mouthBand.filter { row in
            FaceExpression.intensity(col: col, row: row, state: state, time: time) > 0.5
        }.count
    }
}
