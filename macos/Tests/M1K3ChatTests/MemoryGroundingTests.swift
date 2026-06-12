//
//  MemoryGroundingTests.swift
//  M1K3ChatTests
//
//  The WHAT I KNOW ABOUT YOU block: memory hits (kind .memory) are injected
//  as uncited personal facts between KNOWLEDGE and RULES. The first two tests
//  pin the no-memory output BYTE-IDENTICALLY to the pre-memory implementation
//  (captured verbatim 2026-06-12) — documents/calls/notes grounding must not
//  move by a single character.
//
//  Signed: Kev + claude-fable-5, 2026-06-12, Confidence 0.9, Prior: Unknown

import Foundation
import M1K3Agent
@testable import M1K3Chat
import M1K3Inference
@testable import M1K3Knowledge
import Testing

private let allTools: Set<String> = ["web_search", "search_knowledge", "fetch_page"]

/// Local scripted provider (AgentRAGResponderTests' fake is file-private).
private final class ScriptedProvider: InferenceProvider, @unchecked Sendable {
    let name = "memory-scripted"
    let isAvailable = true
    private let lock = NSLock()
    private var responses: [String]
    private(set) var prompts: [String] = []

    init(_ responses: [String]) {
        self.responses = responses
    }

    private func next(_ prompt: String) -> String {
        lock.withLock {
            prompts.append(prompt)
            return responses.isEmpty ? "" : responses.removeFirst()
        }
    }

    func generate(prompt: String) async throws -> String {
        next(prompt)
    }

    func generateStreaming(prompt: String) -> AsyncStream<String> {
        let response = next(prompt)
        return AsyncStream { continuation in
            continuation.yield(response)
            continuation.finish()
        }
    }

    var allPrompts: [String] {
        lock.withLock { prompts }
    }
}

private func docHit() -> ChunkHit {
    ChunkHit(
        chunkID: UUID(), itemID: UUID(), itemTitle: "Plant Notes", kind: .document,
        heading: "3.2 Seals", content: "The hydraulic seal failed.", similarity: 0.8
    )
}

private func memHit(_ content: String) -> ChunkHit {
    ChunkHit(
        chunkID: UUID(), itemID: UUID(), itemTitle: "Kev's sister", kind: .memory,
        heading: nil, content: content, similarity: 0.7
    )
}

struct MemoryGroundingTests {
    // MARK: - Byte-identical pins (no memories → pre-memory output, verbatim)

    @Test("react grounding with chunks and no memories is byte-identical to the pre-memory output")
    func reactPinnedVerbatim() {
        let expected = """
        KNOWLEDGE (the user's own documents, calls, notes):
        1. [Plant Notes §3.2 Seals]
        The hydraulic seal failed.

        RULES:
        - If the KNOWLEDGE already answers the question, reply IMMEDIATELY \
        starting with "CONCLUSION:" — do not use tools.
        - Cite knowledge sources inline with citation tokens like \
        [Title §heading]; never invent citations.
        - Use at most two tool calls, never repeating one with the same argument.
        - For current or external information — weather, news, prices, \
        anything happening now — use web_search. search_knowledge only \
        finds documents already stored on this Mac.
        - web_search returns links and snippets. For details \
        (like an actual forecast), run fetch_page on the most \
        relevant result URL, then conclude from the page text.
        """
        let actual = AgentRAGResponder.grounding(
            chunks: [docHit()], toolNames: allTools, style: .react
        )
        #expect(actual == expected)
    }

    @Test("native grounding with no chunks and no memories is byte-identical to the pre-memory output")
    func nativeEmptyPinnedVerbatim() {
        let expected = """
        No stored knowledge was injected — the user's documents are \
        NOT in this context. If the question could concern their \
        stored documents, calls, or notes, call search_knowledge; \
        otherwise answer directly.

        RULES:
        - Pure small talk — greetings, banter — needs no tools or knowledge; just reply. \
        A question about the current world is NOT small talk, even phrased casually.
        - If the KNOWLEDGE above answers the question, answer from it directly.
        - Cite knowledge sources inline with citation tokens like \
        [Title §heading]; never invent citations.
        - Never repeat a tool call with the same argument.
        - For current or external information — weather, news, prices, \
        anything happening now — use web_search. search_knowledge only \
        finds documents already stored on this Mac.
        - web_search returns links and snippets. For details \
        (like an actual forecast), run fetch_page on the most \
        relevant result URL, then conclude from the page text.
        """
        let actual = AgentRAGResponder.grounding(
            chunks: [], toolNames: allTools, style: .native
        )
        #expect(actual == expected)
    }

    // MARK: - The memory block

    @Test("memories render as an uncited block between KNOWLEDGE and RULES")
    func memoryBlockBetweenKnowledgeAndRules() throws {
        let out = AgentRAGResponder.grounding(
            chunks: [docHit()],
            memories: [memHit("Kev's sister is called Aoife."), memHit("Prefers metric units.")],
            toolNames: allTools, style: .native
        )
        let knowledgeAt = try #require(out.range(of: "KNOWLEDGE ("))
        let memoryAt = try #require(out.range(of: "WHAT I KNOW ABOUT YOU"))
        let rulesAt = try #require(out.range(of: "RULES:"))
        #expect(knowledgeAt.lowerBound < memoryAt.lowerBound)
        #expect(memoryAt.lowerBound < rulesAt.lowerBound)
        // Bulleted facts, no citation labels — these are not citable sources.
        #expect(out.contains("- Kev's sister is called Aoife."))
        #expect(out.contains("- Prefers metric units."))
        #expect(!out.contains("[Kev's sister"))
        // Told not to cite them.
        #expect(out.contains("do not cite"))
    }

    @Test("memory-only grounding keeps the no-knowledge hint, then the memory block")
    func memoryOnlyKeepsHint() throws {
        let out = AgentRAGResponder.grounding(
            chunks: [],
            memories: [memHit("Kev's sister is called Aoife.")],
            toolNames: allTools, style: .native
        )
        let hintAt = try #require(out.range(of: "No stored knowledge was injected"))
        let memoryAt = try #require(out.range(of: "WHAT I KNOW ABOUT YOU"))
        let rulesAt = try #require(out.range(of: "RULES:"))
        #expect(hintAt.lowerBound < memoryAt.lowerBound)
        #expect(memoryAt.lowerBound < rulesAt.lowerBound)
    }

    @Test("no memories → no WHAT I KNOW ABOUT YOU block at all")
    func noMemoriesNoBlock() {
        let out = AgentRAGResponder.grounding(
            chunks: [docHit()], toolNames: allTools, style: .react
        )
        #expect(!out.contains("WHAT I KNOW ABOUT YOU"))
    }

    // MARK: - End to end through answerStreaming

    @Test("a stored memory reaches the prompt as a personal fact and the turn's sources")
    func memoryFlowsEndToEnd() async throws {
        let store = try KnowledgeStore()
        let embedder = HashingEmbeddingService()
        let ingester = DocumentIngester(store: store, embedder: embedder)
        try await ingester.ingest(
            title: "Kev's sister",
            text: "Kev's sister is called Aoife.",
            kind: .memory,
            source: .user
        )
        let provider = ScriptedProvider(["CONCLUSION: Aoife."])
        let responder = AgentRAGResponder(
            store: store, embedder: embedder, provider: provider, tools: []
        )

        // Strong lexical overlap so the hashing embedder clears the memory bar
        // (threshold mechanics are pinned in GroundingGateTests).
        let (sources, stream) = try await responder.answerStreaming("sister is called Aoife?")
        _ = await collectStream(stream)

        #expect(sources.contains { $0.kind == .memory })
        let prompt = try #require(provider.allPrompts.first)
        #expect(prompt.contains("WHAT I KNOW ABOUT YOU"))
        #expect(prompt.contains("- Kev's sister is called Aoife."))
    }
}

private func collectStream(_ stream: AsyncStream<String>) async -> String {
    var text = ""
    for await chunk in stream {
        text = chunk.hasPrefix(text) ? chunk : text + chunk
    }
    return text
}
