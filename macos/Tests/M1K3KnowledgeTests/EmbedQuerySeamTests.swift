//
//  EmbedQuerySeamTests.swift
//  M1K3KnowledgeTests
//
//  The query/document embedding asymmetry seam (PR2 of the keyword-gap fix):
//  `embedQuery` is a PROTOCOL REQUIREMENT with a bare default, so symmetric
//  embedders (hashing, doubles) are untouched while instruction-aware ones
//  override — and the override must dispatch dynamically through
//  `any EmbeddingService` AND survive the Swappable façade (a façade that
//  forgot to forward would silently strip the instruction; that's the one
//  pass-through conformer the challenger named).
//
//  Signed: Kev + claude-fable-5, 2026-07-09, Confidence 0.85, Prior: Unknown
//

import Foundation
@testable import M1K3Knowledge
import Testing

/// A symmetric double — implements only the base requirements, so
/// `embedQuery` comes from the protocol default.
private struct SymmetricEmbedder: EmbeddingService {
    var dimension: Int {
        4
    }

    var fingerprint: String {
        "sym/v1"
    }

    func embed(_ text: String) async throws -> [Float] {
        [Float(text.count), 0, 0, 0]
    }

    func isAvailable() async -> Bool {
        true
    }
}

/// An instruction-aware double — overrides `embedQuery` with a sentinel
/// vector no `embed` call can produce.
private final class InstructedEmbedder: EmbeddingService, @unchecked Sendable {
    var dimension: Int {
        4
    }

    var fingerprint: String {
        "instructed/v1"
    }

    private let lock = NSLock()
    private(set) var queryTexts: [String] = []
    private(set) var embedTexts: [String] = []

    func embed(_ text: String) async throws -> [Float] {
        lock.withLock { embedTexts.append(text) }
        return [1, 0, 0, 0]
    }

    func embedQuery(_ text: String) async throws -> [Float] {
        lock.withLock { queryTexts.append(text) }
        return [0, 1, 0, 0]
    }

    func isAvailable() async -> Bool {
        true
    }
}

struct EmbedQuerySeamTests {
    @Test("a symmetric embedder's embedQuery IS embed — the default keeps hashing untouched")
    func defaultFallsBackToEmbed() async throws {
        let sym = SymmetricEmbedder()
        let viaQuery = try await sym.embedQuery("hello")
        let viaEmbed = try await sym.embed("hello")
        #expect(viaQuery == viaEmbed)
    }

    @Test("an override dispatches dynamically through `any EmbeddingService` — witness table, not extension shadowing")
    func overrideDispatchesThroughExistential() async throws {
        let instructed = InstructedEmbedder()
        let existential: any EmbeddingService = instructed
        let vector = try await existential.embedQuery("who is my sister?")
        #expect(vector == [0, 1, 0, 0])
        #expect(instructed.queryTexts == ["who is my sister?"])
        #expect(instructed.embedTexts.isEmpty)
    }

    @Test("SwappableEmbeddingService FORWARDS embedQuery to the active embedder — the façade can't strip the instruction")
    func swappableForwardsEmbedQuery() async throws {
        let instructed = InstructedEmbedder()
        let swappable = SwappableEmbeddingService(SymmetricEmbedder())
        swappable.setEmbedder(instructed)
        let vector = try await swappable.embedQuery("who is my sister?")
        #expect(vector == [0, 1, 0, 0])
        #expect(instructed.queryTexts == ["who is my sister?"])
    }

    @Test("GroundedSearch embeds the query through embedQuery, never bare embed")
    func groundedSearchUsesEmbedQuery() async throws {
        let store = try KnowledgeStore()
        let instructed = InstructedEmbedder()
        _ = try await GroundedSearch.run(store: store, embedder: instructed, query: "sister name", limit: 3)
        #expect(instructed.queryTexts == ["sister name"])
        #expect(instructed.embedTexts.isEmpty)
    }
}
