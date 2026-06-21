//
//  ParkedRecordingsTests.swift
//  M1K3CallsTests
//
//  Pins the parked-recording read: only finished `.caf` recordings count (never a
//  `.partial` staging file), deterministically ordered, and the waiting message
//  pluralises — so a call recorded before transcription is ready is recoverable,
//  not silently lost.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-21, Confidence 0.9, Prior: Unknown
//

@testable import M1K3Calls
import Testing

struct ParkedRecordingsTests {
    @Test("only finished .caf files count — .partial staging is excluded")
    func excludesPartials() {
        let listing = ["m1k3-call-A.caf", "m1k3-call-B.caf.partial", "notes.txt", "m1k3-call-C.caf"]
        #expect(ParkedRecordings.pending(in: listing) == ["m1k3-call-A.caf", "m1k3-call-C.caf"])
    }

    @Test("empty / no recordings → nothing pending")
    func emptyListing() {
        #expect(ParkedRecordings.pending(in: []).isEmpty)
        #expect(ParkedRecordings.pending(in: ["a.txt", "b.partial"]).isEmpty)
    }

    @Test("a stray .caf without our prefix is ignored (not fed to the pipeline)")
    func ignoresForeignCaf() {
        #expect(ParkedRecordings.pending(in: ["random.caf", "m1k3-call-X.caf"]) == ["m1k3-call-X.caf"])
    }

    @Test("waiting message pluralises")
    func waitingMessagePluralises() {
        #expect(ParkedRecordings.waitingMessage(count: 1) == "1 recording waiting — enable call transcription to process.")
        #expect(ParkedRecordings.waitingMessage(count: 3) == "3 recordings waiting — enable call transcription to process.")
    }
}
