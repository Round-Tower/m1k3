//
//  MemoryDistillationCoordinator.swift
//  M1K3Chat
//
//  Distilled facts → memory items, deduped twice on the way in:
//  1. EXACT — sourceRef = sha256 of the normalized fact; DocumentIngester
//     already no-ops on an existing sourceRef, so the same fact re-distilled
//     across sessions collapses to one row for free, forever.
//  2. SEMANTIC — embed the fact, vector-search stored memories, skip when a
//     near-duplicate (cosine ≥ 0.90) already exists. Catches paraphrases the
//     hash can't ("Kev's sister is Aoife" / "Aoife is Kev's sister").
//
//  Signed: Kev + claude-fable-5, 2026-06-12, Confidence 0.85 (mechanism
//  test-pinned; the 0.90 dedupe bar is an empirical starting point —
//  refine from MEMEVAL self-similarity stats if it over/under-merges).
//  Prior: Unknown

import CryptoKit
import Foundation
import M1K3Agent
import M1K3Inference
import M1K3Knowledge
import os

public struct MemoryDistillationCoordinator: Sendable {
    private static let log = Logger(subsystem: M1K3Log.subsystem, category: "memory-distill")
    /// Cosine above which a stored memory counts as "already known".
    static let semanticDedupeThreshold: Float = 0.90
    static let maxTitleLength = 60

    private let distiller: any MemoryDistilling
    private let ingester: DocumentIngester
    private let store: KnowledgeStore
    private let embedder: any EmbeddingService

    public init(
        distiller: any MemoryDistilling,
        ingester: DocumentIngester,
        store: KnowledgeStore,
        embedder: any EmbeddingService
    ) {
        self.distiller = distiller
        self.ingester = ingester
        self.store = store
        self.embedder = embedder
    }

    /// Distill the slice and store what's new. Returns the number of facts
    /// actually written (dedupe skips don't count). Rethrows distiller
    /// failure so the caller withholds the watermark and retries the slice.
    @discardableResult
    public func distillAndStore(turns: [ChatTurn]) async throws -> Int {
        let facts = try await distiller.distill(turns: turns)
        guard !facts.isEmpty else {
            Self.log.info("distilled 0 facts from \(turns.count) turn(s)")
            return 0
        }
        var written = 0
        for fact in facts {
            if try await isSemanticDuplicate(fact) {
                Self.log.debug("skip (semantic dup): \(LogPreview.preview(fact, max: 60), privacy: .public)")
                continue
            }
            let result = try await ingester.ingest(
                title: Self.title(for: fact),
                text: fact,
                sourceRef: Self.factSourceRef(fact),
                kind: .memory,
                source: .distilled
            )
            if result.wasDeduped {
                Self.log.debug("skip (exact dup): \(LogPreview.preview(fact, max: 60), privacy: .public)")
            } else {
                written += 1
                Self.log.info("remembered: \(LogPreview.preview(fact, max: 80), privacy: .public)")
            }
        }
        Self.log.info("distillation wrote \(written)/\(facts.count) fact(s)")
        return written
    }

    private func isSemanticDuplicate(_ fact: String) async throws -> Bool {
        guard let vector = try? await embedder.embed(fact) else { return false }
        let near = try store.searchVector(queryVector: vector, limit: 5)
        return near.contains {
            $0.kind == .memory && ($0.similarity ?? 0) >= Self.semanticDedupeThreshold
        }
    }

    /// Stable identity for the exact-dedupe layer: hash of the normalized
    /// fact, so capitalisation/punctuation variants collapse.
    static func factSourceRef(_ fact: String) -> String {
        let normalized = MemoryFactNormalizer.normalize(fact)
        let digest = SHA256.hash(data: Data(normalized.utf8))
        let hex = digest.map { String(format: "%02x", $0) }.joined()
        return "memory-fact:\(hex)"
    }

    /// Short facts ARE their titles; long ones cut at a word boundary.
    static func title(for fact: String) -> String {
        guard fact.count > maxTitleLength else { return fact }
        let prefix = fact.prefix(maxTitleLength)
        let cut = prefix.lastIndex(of: " ").map { prefix[..<$0] } ?? prefix
        return String(cut) + "…"
    }
}
