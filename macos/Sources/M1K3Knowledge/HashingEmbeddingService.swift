//
//  HashingEmbeddingService.swift
//  M1K3Knowledge
//
//  A deterministic, dependency-free embedder: bag-of-words hashing (FNV-1a) into
//  a fixed-dimension term-frequency vector. Texts sharing words land near each
//  other under cosine similarity.
//
//  NOT semantic — it captures keyword/co-occurrence overlap, not meaning
//  ("car" and "automobile" are orthogonal). Real semantic search needs the MLX
//  embedder (nomic-embed-text-v1.5, Phase 1b). This exists so M1K3 has a working
//  vector/hybrid-search path *today*: a graceful offline fallback, a deterministic
//  test double, and something for the app shell to run before MLX is wired.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown
//  Context: Promoted from a test fixture to a first-class fallback so hybrid
//  search isn't blocked on the heavy MLX build.

import Foundation

public struct HashingEmbeddingService: EmbeddingService {
    public let dimension: Int

    public init(dimension: Int = 256) {
        self.dimension = dimension
    }

    public func embed(_ text: String) async throws -> [Float] {
        var vector = [Float](repeating: 0, count: dimension)
        for token in Self.tokenize(text) {
            vector[Self.bucket(token, dimension: dimension)] += 1
        }
        return vector
    }

    public func isAvailable() async -> Bool {
        true
    }

    // MARK: - Internals (exposed for unit verification)

    static func tokenize(_ text: String) -> [String] {
        text.lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
    }

    static func bucket(_ token: String, dimension: Int) -> Int {
        var hash: UInt64 = 1_469_598_103_934_665_603 // FNV-1a offset basis
        for byte in token.utf8 {
            hash ^= UInt64(byte)
            hash = hash &* 1_099_511_628_211 // FNV prime
        }
        return Int(hash % UInt64(dimension))
    }
}
