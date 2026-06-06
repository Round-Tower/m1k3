//
//  RAGResponderTests.swift
//  M1K3ChatTests
//
//  The grounded-answer loop end-to-end: ingest → ask → retrieve → prompt →
//  generate. Uses a real store + the hashing embedder + a provider that records
//  the prompt it was handed, so we can assert the retrieved knowledge actually
//  reached the model.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3Chat
import M1K3Inference
import M1K3Knowledge
import Testing

// MARK: - Provider that records the prompt + returns a fixed answer

private final class RecordingProvider: InferenceProvider, @unchecked Sendable {
    let name = "recording"
    let isAvailable = true
    let answer: String
    private let lock = NSLock()
    private var _lastPrompt: String?
    var lastPrompt: String? {
        lock.withLock { _lastPrompt }
    }

    init(answer: String) {
        self.answer = answer
    }

    func generate(prompt: String) async throws -> String {
        lock.withLock { _lastPrompt = prompt }
        return answer
    }

    func generateStreaming(prompt: String) -> AsyncStream<String> {
        lock.withLock { _lastPrompt = prompt }
        let answer = answer
        return AsyncStream { continuation in
            for word in answer.split(separator: " ") {
                continuation.yield(String(word) + " ")
            }
            continuation.finish()
        }
    }
}

// MARK: - Fixture

private func ingestedStore() async throws -> (KnowledgeStore, HashingEmbeddingService) {
    let store = try KnowledgeStore()
    let embedder = HashingEmbeddingService()
    let ingester = DocumentIngester(store: store, embedder: embedder)
    try await ingester.ingest(
        title: "Plant Notes",
        pages: [DocumentPage(pageNumber: 1, text: "3.2 Seals\nThe hydraulic seal on the conveyor failed under load.")]
    )
    try await ingester.ingest(
        title: "Safety",
        pages: [DocumentPage(pageNumber: 1, text: "4.1 PPE\nOperators must wear gloves near the press.")]
    )
    return (store, embedder)
}

// MARK: - Tests

struct RAGResponderTests {
    @Test("answers and returns the chunks it was grounded in")
    func groundedAnswer() async throws {
        let (store, embedder) = try await ingestedStore()
        let provider = RecordingProvider(answer: "The hydraulic seal failed under load.")
        let rag = RAGResponder(store: store, embedder: embedder, provider: provider)

        let response = try await rag.answer("What failed on the conveyor?")
        #expect(response.answer == "The hydraulic seal failed under load.")
        #expect(!response.sources.isEmpty)
        // The top source is the seal chunk, not the safety chunk.
        #expect(response.sources.first?.content.contains("hydraulic seal") == true)
    }

    @Test("the retrieved knowledge actually reaches the model in the prompt")
    func knowledgeInPrompt() async throws {
        let (store, embedder) = try await ingestedStore()
        let provider = RecordingProvider(answer: "ok")
        let rag = RAGResponder(store: store, embedder: embedder, provider: provider)

        _ = try await rag.answer("Tell me about the seal")
        let prompt = try #require(provider.lastPrompt)
        #expect(prompt.contains("KNOWLEDGE:"))
        #expect(prompt.contains("hydraulic seal"))
        #expect(prompt.contains("[Plant Notes §3.2 Seals]"))
        #expect(prompt.contains("USER: Tell me about the seal"))
    }

    @Test("empty store still answers, with no sources and a no-context prompt")
    func emptyStore() async throws {
        let store = try KnowledgeStore()
        let embedder = HashingEmbeddingService()
        let provider = RecordingProvider(answer: "I found nothing in your documents.")
        let rag = RAGResponder(store: store, embedder: embedder, provider: provider)

        let response = try await rag.answer("anything?")
        #expect(response.sources.isEmpty)
        #expect(provider.lastPrompt?.contains("No stored knowledge matched") == true)
    }

    @Test("respects topK")
    func topK() async throws {
        let (store, embedder) = try await ingestedStore()
        let provider = RecordingProvider(answer: "ok")
        let rag = RAGResponder(store: store, embedder: embedder, provider: provider, topK: 1)
        let response = try await rag.answer("seal or gloves")
        #expect(response.sources.count <= 1)
    }

    @Test("streaming yields the answer and resolves sources up front")
    func streaming() async throws {
        let (store, embedder) = try await ingestedStore()
        let provider = RecordingProvider(answer: "the seal failed")
        let rag = RAGResponder(store: store, embedder: embedder, provider: provider)

        let (sources, stream) = try await rag.answerStreaming("what failed?")
        #expect(!sources.isEmpty)
        var collected = ""
        for await chunk in stream {
            collected += chunk
        }
        #expect(collected.contains("seal"))
    }
}
