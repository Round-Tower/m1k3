//
//  MemoryAffinity.swift
//  M1K3Memory
//
//  Derives TOPICAL edges between memories that share salient words — so the
//  constellation reads as a web even where the graph store has no explicit
//  relations yet (knowledge-base seeds arrive unlinked). An affinity edge means
//  "these two mention the same thing" (e.g. both name Aoife) — honest and
//  explainable, not decorative. Pure + deterministic; no embedder needed.
//
//  These are SOFT edges (relation "shared-topic"), distinct from the hard typed
//  edges the store records (supersedes / about-person / …). The view unions both:
//  hard edges are the real graph, affinity edges are the topical scaffold.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.82 (token-overlap is a
//  poor-man's similarity — good enough to thread a constellation; the semantic
//  upgrade is cosine over the stored embeddings, a later pass). Prior: Unknown.

import Foundation

public enum MemoryAffinity {
    /// Link memories sharing at least `minShared` salient tokens, capped at
    /// `maxPerNode` strongest links each so a ubiquitous word can't make a
    /// hairball. Undirected, deduped, deterministic (ties broken by id).
    public static func edges(
        among memories: [Memory],
        minShared: Int = 1,
        maxPerNode: Int = 4
    ) -> [MemoryEdge] {
        guard memories.count > 1 else { return [] }
        let tokensByID = Dictionary(
            uniqueKeysWithValues: memories.map { ($0.id, salientTokens($0.text)) }
        )

        // Score every unordered pair by shared-token count.
        struct Candidate { let a: UUID; let b: UUID; let weight: Int }
        var candidates: [Candidate] = []
        for i in 0 ..< memories.count {
            for j in (i + 1) ..< memories.count {
                let a = memories[i].id, b = memories[j].id
                let shared = tokensByID[a]!.intersection(tokensByID[b]!).count
                if shared >= minShared {
                    candidates.append(Candidate(a: a, b: b, weight: shared))
                }
            }
        }

        // Keep each node's strongest `maxPerNode` links (counted both ends), then
        // emit each surviving pair once.
        let ranked = candidates.sorted {
            if $0.weight != $1.weight { return $0.weight > $1.weight }
            return ($0.a.uuidString, $0.b.uuidString) < ($1.a.uuidString, $1.b.uuidString)
        }
        var degree: [UUID: Int] = [:]
        var kept: [(UUID, UUID)] = []
        for c in ranked {
            guard (degree[c.a] ?? 0) < maxPerNode, (degree[c.b] ?? 0) < maxPerNode else { continue }
            degree[c.a, default: 0] += 1
            degree[c.b, default: 0] += 1
            kept.append((c.a, c.b))
        }
        // Fixed timestamp: affinity edges are DERIVED, not events — a wall-clock
        // stamp would make the output non-deterministic (and they're never stored).
        let epoch = Date(timeIntervalSince1970: 0)
        return kept.map { MemoryEdge(fromID: $0.0, toID: $0.1, relation: "shared-topic", createdAt: epoch) }
    }

    /// Lowercased alphanumeric tokens, longer than two chars, minus a small
    /// stopword set — what's left is "salient" enough to mean a shared topic.
    static func salientTokens(_ text: String) -> Set<String> {
        let raw = text.lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { $0.count > 2 && !stopwords.contains($0) }
        return Set(raw)
    }

    private static let stopwords: Set<String> = [
        "the", "and", "for", "are", "was", "were", "you", "your", "his", "her",
        "she", "him", "they", "them", "with", "that", "this", "from", "has", "have",
        "had", "but", "not", "all", "any", "can", "out", "use", "via", "who", "how",
        "what", "when", "where", "why", "its", "our", "their",
    ]
}
