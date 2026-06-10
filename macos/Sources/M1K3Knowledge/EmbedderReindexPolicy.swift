//
//  EmbedderReindexPolicy.swift
//  M1K3Knowledge
//
//  Decides when the store's vectors must be re-embedded. Stored vectors are
//  only comparable to query vectors from the SAME embedder + kernel generation
//  (an mlx-swift bump changes the embedding kernels, silently shifting the
//  space). The store remembers the fingerprint that produced its vectors; this
//  policy turns "what's stored vs what's running" into a reindex decision.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.85, Prior: Unknown
//  Context: nil-marker + existing vectors deliberately reindexes — a pre-marker
//  store upgrading straight across a kernel bump must re-embed; adopting the
//  current fingerprint silently would compare incompatible spaces forever.
//

public enum EmbedderReindexPolicy {
    /// Whether the store's vectors need re-embedding with the current embedder.
    /// - Parameters:
    ///   - stored: the fingerprint recorded with the existing vectors (nil =
    ///     store predates fingerprinting, provenance unknown).
    ///   - current: the running embedder's fingerprint.
    ///   - embeddingCount: how many vectors exist (0 = nothing to migrate).
    public static func needsReindex(stored: String?, current: String, embeddingCount: Int) -> Bool {
        guard embeddingCount > 0 else { return false }
        return stored != current
    }
}
