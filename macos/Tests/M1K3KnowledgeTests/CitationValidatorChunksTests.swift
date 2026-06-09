import Foundation
@testable import M1K3Knowledge
import Testing

/// Tests the RAG-grounding overload: citations are validated against the retrieved
/// chunks (the model may only cite what it was shown), not the whole store.
struct CitationValidatorChunksTests {
    private func hit(_ title: String, _ heading: String?) -> ChunkHit {
        ChunkHit(chunkID: UUID(), itemID: UUID(), itemTitle: title,
                 kind: .document, heading: heading, content: "x")
    }

    @Test("keeps citations that match a retrieved chunk, strips the invented ones")
    func validatesAgainstChunks() async {
        let chunks = [hit("ICH-Q7", "5.2 Cleaning"), hit("Plant Notes", "3.2 Seals")]
        let text = "Follow (ICH-Q7 §5.2 Cleaning) and [FAKE §9.9 Nope]."
        let result = await CitationValidator.validate(responseText: text, against: chunks)
        #expect(result.validated == [Citation(source: "ICH-Q7", heading: "5.2 Cleaning")])
        #expect(result.stripped == [Citation(source: "FAKE", heading: "9.9 Nope")])
        #expect(!result.cleanedText.contains("FAKE"))
        #expect(result.cleanedText.contains("ICH-Q7"))
    }

    @Test("a citation whose heading doesn't match its chunk is stripped")
    func headingMismatchStripped() async {
        let chunks = [hit("ICH-Q7", "5.2 Cleaning")]
        let result = await CitationValidator.validate(
            responseText: "See (ICH-Q7 §9.9 Wrong).", against: chunks
        )
        #expect(result.validated.isEmpty)
        #expect(result.stripped == [Citation(source: "ICH-Q7", heading: "9.9 Wrong")])
    }

    @Test("no chunks ⇒ every citation is stripped")
    func noChunksStripsAll() async {
        let result = await CitationValidator.validate(
            responseText: "Per (ABC §1 Intro).", against: []
        )
        #expect(result.validated.isEmpty)
        #expect(result.stripped.count == 1)
    }

    @Test("stripping tidies the gap it leaves — no double space, no space before punctuation")
    func stripTidiesWhitespace() async {
        let result = await CitationValidator.validate(
            responseText: "Clean it but not [FAKE §9 Nope]. Then rinse.", against: []
        )
        #expect(result.cleanedText == "Clean it but not. Then rinse.")
        #expect(!result.cleanedText.contains("  "))
    }

    @Test("a citation cited twice is reported once and removed everywhere")
    func deduplicatesRepeatedCitation() async {
        let result = await CitationValidator.validate(
            responseText: "[FAKE §1 A] said it; later [FAKE §1 A] again.", against: []
        )
        #expect(result.stripped == [Citation(source: "FAKE", heading: "1 A")])
        #expect(!result.cleanedText.contains("FAKE"))
    }
}
