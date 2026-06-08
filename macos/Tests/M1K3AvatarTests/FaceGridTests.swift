import M1K3Avatar
import Testing

struct FaceGridTests {
    @Test("matrix is 13×11")
    func dimensions() {
        #expect(FaceGrid.cols == 13)
        #expect(FaceGrid.rows == 11)
    }

    @Test("allCells covers the whole matrix exactly once")
    func allCellsCount() {
        let cells = FaceGrid.allCells()
        #expect(cells.count == FaceGrid.cols * FaceGrid.rows)
        let unique = Set(cells.map { "\($0.col),\($0.row)" })
        #expect(unique.count == cells.count)
    }

    @Test("eye + mouth anchors sit inside the grid")
    func anchorsInBounds() {
        for anchor in [FaceGrid.leftEye, FaceGrid.rightEye] {
            #expect((0 ..< FaceGrid.cols).contains(anchor.col))
            #expect((0 ..< FaceGrid.rows).contains(anchor.row))
        }
        #expect((0 ..< FaceGrid.rows).contains(FaceGrid.mouthRow))
        #expect(FaceGrid.mouthCols.allSatisfy { (0 ..< FaceGrid.cols).contains($0) })
    }

    @Test("eyes are symmetric about the centre column")
    func eyesSymmetric() {
        let centre = FaceGrid.centreCol
        #expect(FaceGrid.leftEye.col < centre)
        #expect(FaceGrid.rightEye.col > centre)
        #expect(centre - FaceGrid.leftEye.col == FaceGrid.rightEye.col - centre)
    }
}
