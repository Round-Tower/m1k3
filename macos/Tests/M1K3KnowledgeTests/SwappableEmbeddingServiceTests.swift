//
//  SwappableEmbeddingServiceTests.swift
//  M1K3KnowledgeTests
//
//  Pins the runtime-swap façade after its relocation from M1K3App into the
//  package (so both shells share it): a setEmbedder() call must be seen by the
//  next embed()/dimension/fingerprint read, and the pre-swap holder is unaffected.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-07, Confidence 0.85, Prior: Unknown
//

@testable import M1K3Knowledge
import Testing

private struct MarkerEmbedder: EmbeddingService {
    let mark: Float
    let dim: Int
    let tag: String
    var dimension: Int {
        dim
    }

    var fingerprint: String {
        tag
    }

    func embed(_: String) async throws -> [Float] {
        [mark]
    }

    func embedBatch(_ texts: [String]) async throws -> [[Float]] {
        texts.map { _ in [mark] }
    }

    func isAvailable() async -> Bool {
        true
    }
}

struct SwappableEmbeddingServiceTests {
    @Test func swapTakesEffectOnNextRead() async throws {
        let swap = SwappableEmbeddingService(MarkerEmbedder(mark: 1, dim: 4, tag: "a"))
        #expect(swap.dimension == 4)
        #expect(swap.fingerprint == "a")
        #expect(try await swap.embed("x") == [1])

        swap.setEmbedder(MarkerEmbedder(mark: 2, dim: 8, tag: "b"))
        #expect(swap.dimension == 8)
        #expect(swap.fingerprint == "b")
        #expect(try await swap.embed("x") == [2])
        #expect(try await swap.embedBatch(["x", "y"]) == [[2], [2]])
    }

    @Test func activeExposesTheCurrentBacking() {
        let swap = SwappableEmbeddingService(MarkerEmbedder(mark: 1, dim: 4, tag: "a"))
        #expect(swap.active.fingerprint == "a")
        swap.setEmbedder(MarkerEmbedder(mark: 2, dim: 8, tag: "b"))
        #expect(swap.active.fingerprint == "b")
    }
}
