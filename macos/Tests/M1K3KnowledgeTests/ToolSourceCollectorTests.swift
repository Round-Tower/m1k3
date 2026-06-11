//
//  ToolSourceCollectorTests.swift
//  M1K3KnowledgeTests
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.9, Prior: Unknown
//

import Foundation
@testable import M1K3Knowledge
import Testing

struct ToolSourceCollectorTests {
    private func hit(_ content: String) -> ChunkHit {
        ChunkHit(
            chunkID: UUID(), itemID: UUID(), itemTitle: "Doc", kind: .document,
            heading: nil, content: content
        )
    }

    @Test("records accumulate and drain resets")
    func recordAndDrain() {
        let collector = ToolSourceCollector()
        collector.record([hit("a")])
        collector.record([hit("b"), hit("c")])

        let drained = collector.drain()
        #expect(drained.map(\.content) == ["a", "b", "c"])
        #expect(collector.drain().isEmpty)
    }
}
