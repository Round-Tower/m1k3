//
//  StreamFoldTests.swift
//  M1K3InferenceTests
//
//  Pins the snapshot-vs-delta normalisation rule all stream consumers share
//  (ChatSession.fold, StreamingReasoningSplitter, ConclusionStreamSplitter).
//
//  Signed: Kev + claude-fable-5, 2026-07-02, Confidence 0.9, Prior: Unknown
//

@testable import M1K3Inference
import Testing

struct StreamFoldTests {
    @Test("a cumulative snapshot replaces the accumulated text")
    func snapshotReplaces() {
        #expect(StreamFold.fold(current: "Hello", chunk: "Hello wor") == "Hello wor")
        #expect(StreamFold.delta(current: "Hello", chunk: "Hello wor") == " wor")
    }

    @Test("a plain delta appends")
    func deltaAppends() {
        #expect(StreamFold.fold(current: "Hello", chunk: " world") == "Hello world")
        #expect(StreamFold.delta(current: "Hello", chunk: " world") == " world")
    }

    @Test("empty current treats the first chunk identically under both readings")
    func emptyCurrent() {
        // "" is a prefix of everything, so the snapshot branch fires — and
        // produces the same result the delta branch would. The rule needs no
        // special case for the first chunk.
        #expect(StreamFold.fold(current: "", chunk: "Hi") == "Hi")
        #expect(StreamFold.delta(current: "", chunk: "Hi") == "Hi")
    }

    @Test("an identical snapshot contributes nothing new")
    func identicalSnapshot() {
        #expect(StreamFold.fold(current: "same", chunk: "same") == "same")
        #expect(StreamFold.delta(current: "same", chunk: "same") == "")
    }
}
