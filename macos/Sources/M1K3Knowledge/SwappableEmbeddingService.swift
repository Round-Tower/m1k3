//
//  SwappableEmbeddingService.swift
//  M1K3Knowledge
//
//  An EmbeddingService façade whose backing embedder can change at runtime, so
//  the app can switch Hashing (offline fallback) ↔ MLX (semantic) without
//  rebuilding the RAGResponder / DocumentIngester that hold it. Both the query
//  path (RAGResponder) and the ingest path (DocumentIngester) see the swap at
//  once — and a reindex of the store keeps the stored vectors in the same space.
//
//  Mirrors SwappableInferenceProvider / SwappableSpeechProvider: a lock-protected
//  swap so this Sendable type reads safely off the main actor while the
//  @Observable UI drives the change.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown
//
//  Review: Kev + claude-opus-4-8, 2026-07-07 — relocated from M1K3App into
//  M1K3Knowledge (beside the EmbeddingService protocol it implements) so BOTH the
//  macOS app and the iOS/visionOS shell reuse ONE swap façade. It was the last
//  stranded member of the Swappable* family; its siblings already live in the
//  package. Made `public` (+ public init); logic byte-identical to the app copy.
//  Review: Kev + claude-fable-5, 2026-07-16 (concurrency deep pass) — NSLock +
//  `@unchecked Sendable` → `Mutex` + checked `Sendable`, converging on the shape
//  its named siblings (SwappableInferenceProvider / SwappableSpeechProvider)
//  already ship: the compiler now verifies Sendable instead of a review-time
//  hope, so a future added mutable property is a compile error, not a latent
//  race. Behaviour byte-identical; the seam stays pinned by EmbedQuerySeamTests.
//

import Foundation
import Synchronization

public final class SwappableEmbeddingService: EmbeddingService, Sendable {
    private let current: Mutex<any EmbeddingService>

    public init(_ initial: any EmbeddingService) {
        current = Mutex(initial)
    }

    public var active: any EmbeddingService {
        current.withLock { $0 }
    }

    public func setEmbedder(_ embedder: any EmbeddingService) {
        current.withLock { $0 = embedder }
    }

    public var dimension: Int {
        active.dimension
    }

    public var fingerprint: String {
        active.fingerprint
    }

    public func embed(_ text: String) async throws -> [Float] {
        try await active.embed(text)
    }

    public func embedBatch(_ texts: [String]) async throws -> [[Float]] {
        try await active.embedBatch(texts)
    }

    /// Explicit forward — without this the façade would take the protocol
    /// DEFAULT (its own bare `embed`) and silently strip the active
    /// embedder's query instruction. Pinned by EmbedQuerySeamTests.
    public func embedQuery(_ text: String) async throws -> [Float] {
        try await active.embedQuery(text)
    }

    public func isAvailable() async -> Bool {
        await active.isAvailable()
    }
}
