//
//  DistilledFactGraphAdapterTests.swift
//  M1K3MemoryChatBridgeTests
//
//  Pins the Chat↔Memory bridge: writing a distilled fact through the
//  DistilledFactGraphWriting seam lands a `.note` node tagged `distilled` in the
//  temporal graph, readable back via the MemoryStore. This is the behaviour that
//  makes chat memory auto-capture reach the graph (not just the corpus) — and it
//  now lives in the package so both shells share it.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-07, Confidence 0.85, Prior: Unknown
//

import M1K3Chat
import M1K3Memory
@testable import M1K3MemoryChatBridge
import Testing

struct DistilledFactGraphAdapterTests {
    @Test func writesDistilledFactAsATaggedNoteNode() async throws {
        let store = try MemoryStore(path: nil)
        let adapter: any DistilledFactGraphWriting = DistilledFactGraphAdapter(store: store)

        try await adapter.writeDistilledFact("Kev takes his coffee black", embedding: [0.1, 0.2, 0.3])

        let all = try store.allMemories()
        let match = all.first { $0.text == "Kev takes his coffee black" }
        #expect(match != nil)
        #expect(match?.source == "distilled")
        #expect(match?.kind == .note)
    }
}
