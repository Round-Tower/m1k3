//
//  EmbeddingService.swift
//  M1K3Knowledge
//
//  Protocol seam for text embeddings. The store and indexer depend only on
//  this abstraction — the on-device MLX implementation (nomic-embed-text on
//  Metal) lives in the separate M1K3Embeddings target so the heavy MLX/Metal
//  build never blocks the core knowledge tests, which run against a
//  deterministic fake.
//
//  Vectors are [Float] to match VectorMath (and the BLOB serialization format)
//  with no Double↔Float conversion at the storage boundary.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown
//  Context: Generalised from the prior knowledge-server's EmbeddingServiceProtocol ([Double], Ollama
//  + MLX impls). M1K3 standardises on [Float] and a single on-device backend.

import Foundation

/// Produces embedding vectors for text. Implementations are expected to be
/// deterministic for a given input within a model version.
public protocol EmbeddingService: Sendable {
    /// Dimensionality of the vectors this service produces (e.g. 768).
    var dimension: Int { get }

    /// Stable identity of the vector space this service produces: embedder
    /// family + model + kernel generation (e.g. "mlx/bge-small-en-v1.5/
    /// mlx-swift-0.30"). Vectors from different fingerprints are NOT
    /// comparable; the store records this with its vectors so a mismatch can
    /// trigger a re-index (see `EmbedderReindexPolicy`).
    var fingerprint: String { get }

    /// Embed a single string into a vector of `dimension` floats.
    func embed(_ text: String) async throws -> [Float]

    /// Embed multiple strings. Default implementation maps `embed` over the
    /// inputs; backends with a native batch path should override.
    func embedBatch(_ texts: [String]) async throws -> [[Float]]

    /// Embed a USER QUERY (as opposed to document/fact content, which always
    /// goes through `embed`). Instruction-aware embedders (Qwen3-Embedding)
    /// override this to apply their asymmetric query instruction
    /// (`EmbeddingText.forQuery`); the default is identical to `embed`, so
    /// symmetric embedders (hashing fallback, test doubles) are untouched.
    /// A requirement (not just an extension) so overrides dispatch through
    /// the witness table on `any EmbeddingService`.
    ///
    /// ⚠️ Residual risk, by design: a future QUERY call site that calls
    /// `embed` instead of `embedQuery` compiles clean and silently
    /// under-retrieves — no seam shape prevents that; the KEYEVAL harness
    /// and review are the guard.
    func embedQuery(_ text: String) async throws -> [Float]

    /// Whether the backend is ready to serve (model downloaded/loaded).
    func isAvailable() async -> Bool
}

public extension EmbeddingService {
    func embedQuery(_ text: String) async throws -> [Float] {
        try await embed(text)
    }

    func embedBatch(_ texts: [String]) async throws -> [[Float]] {
        var out: [[Float]] = []
        out.reserveCapacity(texts.count)
        for text in texts {
            try out.append(await embed(text))
        }
        return out
    }
}

public enum EmbeddingError: Error, Sendable {
    case unavailable
    case dimensionMismatch(expected: Int, got: Int)
    case emptyInput
}
