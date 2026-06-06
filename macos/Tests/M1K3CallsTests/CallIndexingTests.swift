//
//  CallIndexingTests.swift
//  M1K3CallsTests
//
//  The M1K3 twist, proven end-to-end on the REAL in-memory KnowledgeStore: a
//  finished call is chunked, embedded (dep-free Hashing), and becomes searchable
//  in the same index as documents — so RAG/agents/MCP can answer over calls.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Calls
import M1K3Knowledge
import Testing

struct CallChunkerTests {
    private func seg(_ text: String, _ speaker: String?) -> CallTranscriptSegment {
        CallTranscriptSegment(text: text, startTime: 0, speaker: speaker)
    }

    @Test("consecutive same-speaker turns group into one chunk")
    func groupsSameSpeaker() {
        let session = CallSession(startedAt: Date(timeIntervalSince1970: 0), title: "c", segments: [
            seg("one", "A"), seg("two", "A"), seg("three", "B"),
        ])
        let chunks = CallChunker.chunk(session: session)
        #expect(chunks.count == 2)
        #expect(chunks[0].heading == "A")
        #expect(chunks[0].content == "one two")
        #expect(chunks[1].heading == "B")
    }

    @Test("the summary leads as its own chunk when present")
    func summaryLeads() {
        let session = CallSession(
            startedAt: Date(timeIntervalSince1970: 0), title: "c",
            segments: [seg("hi", "A")],
            fullSummary: CallSummary(overview: "A short call.", actionItems: ["Follow up"])
        )
        let chunks = CallChunker.chunk(session: session)
        #expect(chunks.first?.heading == "Summary")
        #expect(chunks.first?.content.contains("A short call.") == true)
        #expect(chunks.first?.content.contains("Follow up") == true)
    }

    @Test("a long single-speaker run splits at the char budget")
    func splitsLongRun() {
        let long = (0 ..< 20).map { seg("line\($0) padding padding padding", "A") }
        let session = CallSession(startedAt: Date(timeIntervalSince1970: 0), title: "c", segments: long)
        let chunks = CallChunker.chunk(session: session, maxChars: 100)
        #expect(chunks.count > 1)
        #expect(chunks.allSatisfy { $0.content.count <= 100 || !$0.content.contains(" ") })
    }

    @Test("an empty, unsummarised call yields no chunks")
    func emptyYieldsNothing() {
        let session = CallSession(startedAt: Date(timeIntervalSince1970: 0), title: "c")
        #expect(CallChunker.chunk(session: session).isEmpty)
    }
}

struct CallIngesterTests {
    private func sampleCall() -> CallSession {
        CallSession(
            startedAt: Date(timeIntervalSince1970: 0),
            title: "Billing call",
            segments: [
                CallTranscriptSegment(text: "I was double charged for the hydraulic pump", startTime: 0, speaker: "Customer"),
                CallTranscriptSegment(text: "Let me issue a refund", startTime: 5, speaker: "Agent"),
            ],
            fullSummary: CallSummary(overview: "Customer double-charged; refund issued.")
        )
    }

    @Test("a call is indexed as a .call item with its chunks")
    func indexesAsCall() async throws {
        let store = try KnowledgeStore()
        let result = try await CallIngester(store: store, embedder: HashingEmbeddingService()).ingest(sampleCall())
        #expect(!result.wasDeduped)
        #expect(result.chunkCount > 0)
        let calls = try store.allItems(kind: .call)
        #expect(calls.count == 1)
        #expect(calls.first?.title == "Billing call")
    }

    @Test("END TO END: an indexed call is findable by hybrid search, tagged .call")
    func callIsSearchable() async throws {
        let store = try KnowledgeStore()
        let embedder = HashingEmbeddingService()
        try await CallIngester(store: store, embedder: embedder).ingest(sampleCall())

        let query = "hydraulic pump refund"
        let hits = try store.searchHybrid(query: query, queryVector: await embedder.embed(query), limit: 5)
        #expect(!hits.isEmpty)
        #expect(hits.contains { $0.kind == .call })
    }

    @Test("re-ingesting the same call is a deduped no-op")
    func dedupes() async throws {
        let store = try KnowledgeStore()
        let ingester = CallIngester(store: store, embedder: HashingEmbeddingService())
        let call = sampleCall()
        _ = try await ingester.ingest(call)
        let second = try await ingester.ingest(call)
        #expect(second.wasDeduped)
        #expect(try store.allItems(kind: .call).count == 1)
    }
}
