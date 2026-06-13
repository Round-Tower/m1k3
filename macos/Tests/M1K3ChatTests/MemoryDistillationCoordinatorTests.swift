//
//  MemoryDistillationCoordinatorTests.swift
//  M1K3ChatTests
//
//  Facts → memory items, with both dedupe layers: exact (normalized-hash
//  sourceRef — DocumentIngester's existing no-op-on-existing-sourceRef) and
//  semantic (embedding similarity against stored memories). The semantic
//  layer is exercised with identical strings under the hashing embedder
//  (cosine 1.0) — true paraphrase-dedupe is MLX-verified at ⌘R; these tests
//  prove the mechanism.
//
//  Signed: Kev + claude-fable-5, 2026-06-12, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Chat
@testable import M1K3Knowledge
import Testing

private struct FakeDistiller: MemoryDistilling {
    let result: Result<[String], Error>

    func distill(turns _: [ChatTurn]) async throws -> [String] {
        try result.get()
    }
}

private struct Boom: Error {}

private func makeFixture(
    facts: Result<[String], Error>
) throws -> (MemoryDistillationCoordinator, KnowledgeStore) {
    let store = try KnowledgeStore()
    let embedder = HashingEmbeddingService()
    return (
        MemoryDistillationCoordinator(
            distiller: FakeDistiller(result: facts),
            ingester: DocumentIngester(store: store, embedder: embedder),
            store: store,
            embedder: embedder
        ),
        store
    )
}

private let someTurns = [
    ChatTurn(role: .user, text: "My sister is called Aoife"),
    ChatTurn(role: .assistant, text: "Noted!"),
]

struct MemoryDistillationCoordinatorTests {
    @Test("N facts land as N memory items with distilled provenance")
    func factsBecomeMemories() async throws {
        let (coordinator, store) = try makeFixture(facts: .success([
            "Kev's sister is called Aoife.",
            "The user prefers metric units.",
        ]))
        let written = try await coordinator.distillAndStore(turns: someTurns)
        #expect(written == 2)
        let memories = try store.allItems(kind: .memory)
        #expect(memories.count == 2)
        #expect(memories.allSatisfy { $0.source == .distilled })
        // Facts are searchable — the residency loop covers distilled memory.
        #expect(try store.searchFTS(query: "Aoife").isEmpty == false)
    }

    @Test("a long fact gets a word-boundary title, full text as content")
    func longFactTitleCut() async throws {
        let fact = "The user decided that the memory architecture should live inside the existing knowledge store rather than a separate database."
        let (coordinator, store) = try makeFixture(facts: .success([fact]))
        _ = try await coordinator.distillAndStore(turns: someTurns)
        let memory = try #require(try store.allItems(kind: .memory).first)
        #expect(memory.title.count <= 60)
        // Cut on a word boundary — no mid-word chop.
        #expect(fact.hasPrefix(memory.title.replacingOccurrences(of: "…", with: "")))
        let chunks = try store.chunks(forItem: memory.id)
        #expect(chunks.first?.content == fact)
    }

    @Test("the same fact distilled twice collapses to one row (exact layer)")
    func exactDedupe() async throws {
        let (coordinator, store) = try makeFixture(facts: .success(["Kev lives in Cork."]))
        let first = try await coordinator.distillAndStore(turns: someTurns)
        let second = try await coordinator.distillAndStore(turns: someTurns)
        #expect(first == 1)
        #expect(second == 0)
        #expect(try store.allItems(kind: .memory).count == 1)
    }

    @Test("normalization variants collapse too — case and punctuation don't make new rows")
    func normalizedDedupe() async throws {
        let (first, store) = try makeFixture(facts: .success(["Kev lives in Cork."]))
        _ = try await first.distillAndStore(turns: someTurns)
        // Same store, new coordinator emitting a case/punct variant.
        let variantCoordinator = MemoryDistillationCoordinator(
            distiller: FakeDistiller(result: .success(["kev lives in cork"])),
            ingester: DocumentIngester(store: store, embedder: HashingEmbeddingService()),
            store: store,
            embedder: HashingEmbeddingService()
        )
        let written = try await variantCoordinator.distillAndStore(turns: someTurns)
        #expect(written == 0)
        #expect(try store.allItems(kind: .memory).count == 1)
    }

    @Test("semantic layer: a near-identical stored memory blocks the write")
    func semanticDedupe() async throws {
        // Seed a memory whose NORMALIZED form differs (extra word) so the
        // exact layer misses, but whose embedding is close enough to block.
        let (seeder, store) = try makeFixture(facts: .success(["Kev lives in Cork city Ireland today"]))
        _ = try await seeder.distillAndStore(turns: someTurns)
        let nearCoordinator = MemoryDistillationCoordinator(
            distiller: FakeDistiller(result: .success(["Kev lives in Cork city Ireland"])),
            ingester: DocumentIngester(store: store, embedder: HashingEmbeddingService()),
            store: store,
            embedder: HashingEmbeddingService()
        )
        _ = try await nearCoordinator.distillAndStore(turns: someTurns)
        #expect(try store.allItems(kind: .memory).count == 1)
    }

    @Test("a throwing distiller rethrows — the caller keeps the watermark")
    func distillerThrowRethrows() async throws {
        let (coordinator, _) = try makeFixture(facts: .failure(Boom()))
        await #expect(throws: (any Error).self) {
            try await coordinator.distillAndStore(turns: someTurns)
        }
    }

    @Test("zero facts writes nothing and returns 0")
    func zeroFactsNoWrites() async throws {
        let (coordinator, store) = try makeFixture(facts: .success([]))
        let written = try await coordinator.distillAndStore(turns: someTurns)
        #expect(written == 0)
        #expect(try store.itemCount() == 0)
    }
}
