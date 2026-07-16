//
//  ModelCacheIntegrity.swift
//  M1K3MLX
//
//  The pre-load tripwire for a TORN model directory. On 2026-07-16 a load of a
//  partially-written cache dir (weights index promising shards the files
//  didn't back) reached mlx-swift's quantize pass, whose Module.update runs in
//  a non-throwing context — the thrown UpdateError became swift_unexpectedError
//  and took the WHOLE APP down (Quantized.swift:126 → Module.swift:598). The
//  loader cannot be taught to throw from our side, so the defence is upstream
//  of it: verify the directory BEFORE loadContainer, and treat torn state as
//  "delete and let HubApi re-download", never as a crash.
//
//  Deliberately narrow: only the states that actually reach the crashing path
//  count as torn (index references a missing or zero-byte shard; a zero-byte
//  safetensors). Resumable `.incomplete` partials under .cache are NOT torn —
//  HubApi resumes those legitimately. A directory with no weights at all is
//  .empty (the normal pre-download state). No-index-but-weights is .ok — a
//  single-file model without an index cannot be validated, and blocking it
//  would regress legacy caches.
//
//  Signed: Kev + claude-fable-5, 2026-07-16, Confidence 0.85 (the torn→crash
//  chain is stack-trace-evidenced; the verdict rules are pure and pinned; the
//  delete-then-redownload recovery is verify-by-launch). Prior: Unknown
//

import Foundation
import os

private let integrityLog = Logger(subsystem: "app.m1k3", category: "mlx-load")

/// Pure verdict over a model directory's weight files. IO lives in `scan`;
/// the decision is testable without a filesystem.
public enum ModelCacheIntegrity {
    public enum Verdict: Equatable, Sendable {
        /// Weights present and (where an index exists) accounted for.
        case ok
        /// No weight files — the normal state before a first download.
        case empty
        /// The state that crashes the loader: partial weights on disk.
        case torn(reason: String)
    }

    /// The decision core. `indexWeightFiles`: shard filenames referenced by
    /// model.safetensors.index.json (empty when no index).
    /// `safetensorsSizes`: filename → byte size for every *.safetensors present.
    public static func verdict(
        indexWeightFiles: Set<String>,
        safetensorsSizes: [String: Int]
    ) -> Verdict {
        if let empty = safetensorsSizes.first(where: { $0.value == 0 }) {
            return .torn(reason: "zero-byte shard \(empty.key)")
        }
        if !indexWeightFiles.isEmpty {
            let missing = indexWeightFiles.subtracting(safetensorsSizes.keys)
            if !missing.isEmpty {
                // An index present with NO shards at all is a pre-download
                // artifact (config fetched first), not torn — nothing partial
                // to poison the loader; HubApi fetches the shards next.
                if safetensorsSizes.isEmpty { return .empty }
                return .torn(reason: "index references missing shard(s): \(missing.sorted().joined(separator: ", "))")
            }
            return .ok
        }
        return safetensorsSizes.isEmpty ? .empty : .ok
    }

    /// Disk scan → verdict for the model directory `loadContainer` will read.
    public static func scan(directory: URL) -> Verdict {
        let fm = FileManager.default
        guard let names = try? fm.contentsOfDirectory(atPath: directory.path) else { return .empty }
        var sizes: [String: Int] = [:]
        for name in names where name.hasSuffix(".safetensors") {
            let path = directory.appendingPathComponent(name).path
            let attributes = try? fm.attributesOfItem(atPath: path)
            sizes[name] = (attributes?[.size] as? Int) ?? 0
        }
        var indexed: Set<String> = []
        let indexURL = directory.appendingPathComponent("model.safetensors.index.json")
        if let data = try? Data(contentsOf: indexURL),
           let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let weightMap = object["weight_map"] as? [String: String]
        {
            indexed = Set(weightMap.values)
        }
        return verdict(indexWeightFiles: indexed, safetensorsSizes: sizes)
    }

    /// The recovery: when the directory `loadContainer` is about to read is
    /// torn, delete it so HubApi re-downloads cleanly — a fresh multi-GB fetch
    /// beats a guaranteed process death. Returns the verdict for logging.
    @discardableResult
    public static func healBeforeLoad(directory: URL) -> Verdict {
        let result = scan(directory: directory)
        if case let .torn(reason) = result {
            integrityLog.fault(
                "torn model cache at \(directory.lastPathComponent, privacy: .public) (\(reason, privacy: .public)) — deleting for a clean re-download (the alternative is the quantize fatalError)"
            )
            try? FileManager.default.removeItem(at: directory)
        }
        return result
    }
}
