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

    @Test("numbers, units, and acronyms are spoken against the real dictionary")
    func bundledFallbackChain() throws {
        let g2p = try KokoroG2P.bundled()
        // Every expansion word (fifteen/celsius/twenty/six…) must resolve in
        // the shipped dict — this is the line that used to be silently mute.
        let weather = g2p.annotatedTokens("It is 15°C in 2026.")
        #expect(weather.words.allSatisfy { !$0.tokenRange.isEmpty })
        // Mixed alphanumeric spells out (em one kay three), not silence.
        #expect(g2p.phonemeTokens("M1K3").isEmpty == false)
        // Percentages and decimals speak too.
        #expect(g2p.phonemeTokens("3.5% today").isEmpty == false)
    }
}
