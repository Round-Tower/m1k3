import Foundation
@testable import M1K3Kokoro
import Testing

struct KokoroG2PBundledTests {
    @Test("parse builds a lookup table, skipping malformed lines")
    func parseSkipsMalformed() {
        let dict = KokoroG2P.parse("hello\t50,83,54\nnotab\nempty\t\nworld\t65,156\n")
        #expect(dict["hello"] == [50, 83, 54])
        #expect(dict["world"] == [65, 156])
        #expect(dict["notab"] == nil)
        #expect(dict["empty"] == nil)
    }

    @Test("the bundled dictionary loads and reproduces the oracle tokens end-to-end")
    func bundledEndToEnd() throws {
        let g2p = try KokoroG2P.bundled()
        // Real 234k-word espeak-en-gb dict → the exact sequence Python's kokoro_onnx fed.
        #expect(g2p.phonemeTokens("Hello world.") ==
            [50, 83, 54, 156, 83, 135, 16, 65, 156, 87, 158, 54, 46, 4])
        // Contraction coverage (web2 lacks these; added to the generated dict).
        #expect(g2p.phonemeTokens("don't").isEmpty == false)
    }
}
