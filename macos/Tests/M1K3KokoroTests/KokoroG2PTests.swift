import Foundation
@testable import M1K3Kokoro
import Testing

/// Tests the pure ASSEMBLY logic of KokoroG2P with an injected mini-dictionary —
/// the dictionary is swappable data; the word/space/punctuation/pad-wrap assembly is
/// the tested IP. Token values for `hello`/`world`/`don't` are the real espeak-en-gb
/// sequences captured from the kokoro_onnx oracle, so the headline case asserts the
/// exact model input the Python reference produced.
struct KokoroG2PTests {
    /// Real oracle tokens (espeak en-gb), plus two synthetic single-token words.
    private let dict: [String: [Int]] = [
        "hello": [50, 83, 54, 156, 83, 135],
        "world": [65, 156, 87, 158, 54, 46],
        "don't": [46, 156, 83, 135, 56, 62],
        "aa": [101],
        "bb": [102],
    ]

    private func g2p() -> KokoroG2P {
        KokoroG2P(dictionary: dict)
    }

    @Test("'Hello world.' reproduces the exact oracle token sequence (unwrapped + wrapped)")
    func helloWorldExact() {
        let mapper = g2p()
        // Unwrapped: hello + space(16) + world + '.'(4) == 14 tokens (the style index).
        #expect(mapper.phonemeTokens("Hello world.") ==
            [50, 83, 54, 156, 83, 135, 16, 65, 156, 87, 158, 54, 46, 4])
        // Model input adds the [0 … 0] pad wrap.
        #expect(mapper.modelTokens("Hello world.") ==
            [0, 50, 83, 54, 156, 83, 135, 16, 65, 156, 87, 158, 54, 46, 4, 0])
    }

    @Test("words are separated by the space token, punctuation attaches with no leading space")
    func spacingAndPunctuation() {
        let mapper = g2p()
        #expect(mapper.phonemeTokens("aa bb") == [101, 16, 102])
        #expect(mapper.phonemeTokens("aa, bb!") == [101, 3, 16, 102, 5])
    }

    @Test("OOV words are skipped without leaving a double space")
    func oovSkipped() {
        let mapper = g2p()
        #expect(mapper.phonemeTokens("aa zzzqx bb") == [101, 16, 102])
        #expect(mapper.phonemeTokens("zzzqx aa") == [101])
    }

    @Test("contractions are looked up with their apostrophe intact")
    func contraction() {
        let mapper = g2p()
        #expect(mapper.phonemeTokens("don't") == [46, 156, 83, 135, 56, 62])
    }

    @Test("surrounding quotes/apostrophes are trimmed for lookup")
    func trimsEdgeApostrophes() {
        let mapper = g2p()
        #expect(mapper.phonemeTokens("'hello'") == [50, 83, 54, 156, 83, 135])
    }

    @Test("leading punctuation/whitespace produces no leading space token")
    func noLeadingSpace() {
        let mapper = g2p()
        #expect(mapper.phonemeTokens("  aa") == [101])
        // The comma itself gets no leading space, but (per espeak) the following word
        // does — oracle: "Hello, world." → [...135, 3, 16, 65...].
        #expect(mapper.phonemeTokens(", aa") == [3, 16, 101])
    }

    @Test("empty / whitespace-only input yields just the pad wrap")
    func emptyInput() {
        let mapper = g2p()
        #expect(mapper.phonemeTokens("") == [])
        #expect(mapper.modelTokens("   ") == [0, 0])
    }

    @Test("token count is capped at the model max (510), breaking on a word boundary")
    func capsAtMax() {
        // 200 copies of a 6-token word + spaces would be ~1400 tokens → must cap.
        let many = Array(repeating: "hello", count: 200).joined(separator: " ")
        let toks = g2p().phonemeTokens(many)
        #expect(toks.count <= KokoroG2P.maxTokens)
        // Never ends mid-word: the last emitted token is the final phoneme of `hello`.
        #expect(toks.last == 135)
    }
}
