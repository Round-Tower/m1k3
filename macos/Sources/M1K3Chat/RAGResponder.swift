//
//  RAGResponder.swift
//  M1K3Chat
//
//  The grounded-answer brain: embed the question → hybrid-search the knowledge
//  store → build a documents-first prompt → generate via the active
//  InferenceProvider. Returns the answer plus the chunks it was grounded in, so
//  the UI can show sources and the user can trust the provenance.
//
//  This is the seam where every M1K3 surface (chat, voice, the MCP server)
//  reaches the model with the user's own knowledge as context. Provider-agnostic
//  (AFM now, MLX Gemma / LiteRT later) and embedder-agnostic (Hashing now, MLX
//  later) — both arrive via protocols.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation
import M1K3Inference
import M1K3Knowledge

public struct RAGResponse: Sendable, Equatable {
    public let answer: String
    /// The chunks retrieved as grounding, in rank order.
    public let sources: [ChunkHit]

    public init(answer: String, sources: [ChunkHit]) {
        self.answer = answer
        self.sources = sources
    }
}

public struct RAGResponder: Sendable {
    private let store: KnowledgeStore
    private let embedder: any EmbeddingService
    private let provider: any InferenceProvider
    private let topK: Int

    public init(
        store: KnowledgeStore,
        embedder: any EmbeddingService,
        provider: any InferenceProvider,
        topK: Int = 5
    ) {
        self.store = store
        self.embedder = embedder
        self.provider = provider
        self.topK = topK
    }

    /// Retrieve grounding for `question` (shared by the blocking + streaming
    /// paths) — embed, then hybrid-search.
    private func retrieve(for question: String) async throws -> (chunks: [ChunkHit], prompt: String) {
        let queryVector = try await embedder.embed(question)
        let chunks = try store.searchHybrid(query: question, queryVector: queryVector, limit: topK)
        let prompt = ChatPromptBuilder.build(chunks: chunks, userMessage: question)
        return (chunks, prompt)
    }

    /// Answer `question`, grounded in retrieved knowledge.
    public func answer(_ question: String) async throws -> RAGResponse {
        let (chunks, prompt) = try await retrieve(for: question)
        let answer = try await provider.generate(prompt: prompt)
        return RAGResponse(answer: answer, sources: chunks)
    }

    /// Stream the answer. Returns the resolved `sources` immediately (retrieval
    /// happens up front) plus a token stream for the generated text.
    public func answerStreaming(
        _ question: String
    ) async throws -> (sources: [ChunkHit], stream: AsyncStream<String>) {
        let (chunks, prompt) = try await retrieve(for: question)
        return (chunks, provider.generateStreaming(prompt: prompt))
    }
}
