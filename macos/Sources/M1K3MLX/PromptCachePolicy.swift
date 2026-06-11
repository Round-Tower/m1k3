//
//  PromptCachePolicy.swift
//  M1K3MLX
//
//  Pure naming/invalidation policy for persisted KV prefixes (upstream
//  savePromptCache/loadPromptCache .safetensors files). The filename IS the
//  fingerprint: model id, rendered prefix text, tool set, KV quantization
//  params, and the MLX kernel generation all hash into it, so any drift makes
//  the old file a plain cache miss — no version checks, no migration. Same
//  doctrine as EmbedderReindexPolicy's kernel fingerprint.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.8, Prior: Unknown
//

import CryptoKit
import Foundation

public enum PromptCachePolicy {
    /// Everything that makes a saved KV prefix valid to reuse. Any component
    /// changing means the prefix bytes on disk no longer match what the live
    /// session would have prefilled.
    public struct Fingerprint: Sendable, Equatable {
        public let modelID: String
        public let prefixText: String
        public let toolNames: [String]
        /// KV quantization params: a QuantizedKVCache file is not
        /// interchangeable with a plain KVCacheSimple one.
        public let kvBits: Int?
        public let kvGroupSize: Int
        /// MLX kernel generation (the embedder-fingerprint pattern): bumped
        /// kernels can change numerics, so a prefix computed by old kernels
        /// should not seed new-kernel generation.
        public let kernelTag: String

        public init(
            modelID: String,
            prefixText: String,
            toolNames: [String],
            kvBits: Int?,
            kvGroupSize: Int,
            kernelTag: String
        ) {
            self.modelID = modelID
            self.prefixText = prefixText
            self.toolNames = toolNames
            self.kvBits = kvBits
            self.kvGroupSize = kvGroupSize
            self.kernelTag = kernelTag
        }
    }

    /// Flat, deterministic, filesystem-safe name. The hash collapses every
    /// component (tool order normalised) so lookup is a plain file-exists.
    public static func fileName(for fingerprint: Fingerprint) -> String {
        // Unit separator: cannot appear in any component, so joined fields
        // cannot collide across boundaries.
        let separator = "\u{1F}"
        let canonical = [
            fingerprint.modelID,
            fingerprint.prefixText,
            fingerprint.toolNames.sorted().joined(separator: separator),
            fingerprint.kvBits.map(String.init) ?? "none",
            String(fingerprint.kvGroupSize),
            fingerprint.kernelTag,
        ].joined(separator: separator)
        let digest = SHA256.hash(data: Data(canonical.utf8))
        let hex = digest.prefix(8).map { String(format: "%02x", $0) }.joined()
        return "prompt-prefix-\(hex).safetensors"
    }
}
