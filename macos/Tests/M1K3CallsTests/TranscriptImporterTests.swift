//
//  TranscriptImporterTests.swift
//  M1K3CallsTests
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

@testable import M1K3Calls
import Testing

struct TranscriptImporterTests {
    @Test("speaker-prefixed lines parse into attributed, ordered segments")
    func speakerLines() {
        let segs = TranscriptImporter.parse("Customer: I was double charged\nAgent: Issuing a refund")
        #expect(segs.count == 2)
        #expect(segs[0].speaker == "Customer")
        #expect(segs[0].text == "I was double charged")
        #expect(segs[1].speaker == "Agent")
        #expect(segs[1].startTime > segs[0].startTime) // order preserved
    }

    @Test("blank lines are skipped")
    func skipsBlanks() {
        #expect(TranscriptImporter.parse("A: hi\n\n\nB: bye").count == 2)
    }

    @Test("a line without a speaker prefix is unattributed")
    func noPrefix() {
        let segs = TranscriptImporter.parse("just some narration without a speaker")
        #expect(segs.count == 1)
        #expect(segs[0].speaker == nil)
    }

    @Test("only the first colon splits — colons in content are preserved")
    func colonInContent() {
        let segs = TranscriptImporter.parse("Agent: the ratio is 3:1 today")
        #expect(segs[0].speaker == "Agent")
        #expect(segs[0].text == "the ratio is 3:1 today")
    }

    @Test("a colon not followed by whitespace is not a speaker (e.g. a URL)")
    func urlNotSpeaker() {
        let segs = TranscriptImporter.parse("see https://example.com/page")
        #expect(segs[0].speaker == nil)
        #expect(segs[0].text == "see https://example.com/page")
    }
}
