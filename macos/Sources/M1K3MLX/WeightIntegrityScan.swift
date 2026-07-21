//
//  WeightIntegrityScan.swift
//  M1K3MLX
//
//  The IO half of `WeightIntegrity`: hash what is on disk, ask the pure core
//  for a verdict, and refuse the load if the bytes disagree with the manifest.
//
//  WHERE THIS RUNS, AND WHY IT MATTERS. Enforcement sits inside
//  `HubApiDownloader.download` — the single choke point every weight fetch
//  passes through, including `loadContainer`'s own (the factories take our
//  downloader as their `Downloader`). That placement is load-bearing: it is
//  after the snapshot completes but before any factory reads a tensor, which
//  is the only window where refusal actually prevents poisoned weights from
//  being loaded. Checking before the download instead would pass trivially on
//  a first run (empty directory) and leave onboarding — the exact case this
//  work exists to protect — unguarded.
//
//  All three of `download`'s exits are covered, including the offline and
//  auth-required fallbacks that hand back a pre-existing local copy. A cache
//  poisoned on a previous run must not become trusted merely because the
//  network is down this time.
//
//  COST. Hashing ~6.7 GB on every launch would be indefensible, so a verified
//  directory writes a receipt naming the revision and the sizes it verified;
//  a later load with the same revision and unchanged sizes trusts it and skips
//  hashing. This is sound under the threat model, which is a POISONED
//  DOWNLOAD, not local tampering: an attacker who can already write inside our
//  sandbox container owns the app regardless of what we hash. The receipt is a
//  cache of "these bytes were checked at acquisition", nothing more.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-21, Confidence 0.85 (the verdict
//  rules are pure and pinned; the choke-point placement is verified against
//  the factories' Downloader seam; the receipt's reuse condition is explicit
//  about its threat model). Honest caveat: refusal and the receipt round-trip
//  are verify-by-launch — MLX paths cannot run under `swift test` (metallib
//  wall), so this is the same standing caveat as every other file in M1K3MLX.
//  Prior: Unknown
//

import CryptoKit
import Foundation
import os

private let integrityLog = Logger(subsystem: "app.m1k3", category: "weight-integrity")

public enum WeightIntegrityScan {
    /// Written beside the weights once their digests have been checked, so the
    /// multi-GB hash is paid at acquisition rather than on every launch.
    static let receiptName = ".m1k3-weight-receipt.json"

    /// Verify `directory` against the manifest for `repoID`.
    ///
    /// Throws `WeightTamperError` — and ONLY that — when a present file's bytes
    /// disagree with the pin. Unpinned repos, in-flight downloads and unreadable
    /// directories all return normally: this is a tripwire on the bytes we ship,
    /// not a gate on everything that can be loaded.
    ///
    /// Deliberately does NOT delete on tamper, unlike `ModelCacheIntegrity`'s
    /// torn-cache heal. A torn cache is an accident worth silently repairing; a
    /// digest mismatch is evidence, and re-downloading from the same source
    /// would loop forever while hiding an active attack behind flaky-looking
    /// networking.
    public static func enforce(directory: URL, repoID: String) throws {
        guard let pin = PinnedWeights.pin(for: repoID) else { return }

        if trustsReceipt(directory: directory, pin: pin) { return }

        var observed: [String: WeightIntegrity.ObservedFile] = [:]
        for (name, expected) in pin.files {
            let fileURL = directory.appendingPathComponent(name)
            guard let size = fileSize(at: fileURL) else { continue }
            // A size disagreement is already conclusive, so skip the hash —
            // this also means a truncated multi-GB shard costs nothing to reject.
            guard size == expected.size else {
                observed[name] = .init(size: size, sha256: "")
                continue
            }
            guard let digest = sha256(of: fileURL) else { continue }
            observed[name] = .init(size: size, sha256: digest)
        }

        switch WeightIntegrity.verdict(pin: pin, observed: observed) {
        case .unpinned, .incomplete:
            // Nothing to attest yet — a download still in flight. No receipt.
            return
        case let .tampered(files):
            integrityLog.fault(
                """
                REFUSING \(repoID, privacy: .public): downloaded weights disagree with the \
                pinned manifest (\(files.joined(separator: ", "), privacy: .public)). \
                Files left in place as evidence; not retried.
                """
            )
            throw WeightTamperError(repoID: repoID, files: files)
        case .verified:
            integrityLog.notice(
                "verified \(repoID, privacy: .public) against pinned revision \(pin.revision, privacy: .public)"
            )
            writeReceipt(directory: directory, pin: pin)
        }
    }

    // MARK: - Receipt

    private struct Receipt: Codable {
        let revision: String
        let sizes: [String: Int]
    }

    /// True when a previous run verified THIS revision and every pinned file is
    /// still present at the size that was verified. Sizes are checked against
    /// the pin as well as the receipt, so a stale receipt from an older pin can
    /// never vouch for the current one.
    private static func trustsReceipt(directory: URL, pin: WeightIntegrity.Pin) -> Bool {
        guard
            let data = try? Data(contentsOf: directory.appendingPathComponent(receiptName)),
            let receipt = try? JSONDecoder().decode(Receipt.self, from: data),
            receipt.revision == pin.revision
        else { return false }

        for (name, expected) in pin.files {
            guard
                receipt.sizes[name] == expected.size,
                fileSize(at: directory.appendingPathComponent(name)) == expected.size
            else { return false }
        }
        return true
    }

    private static func writeReceipt(directory: URL, pin: WeightIntegrity.Pin) {
        let receipt = Receipt(
            revision: pin.revision,
            sizes: pin.files.mapValues(\.size)
        )
        guard let data = try? JSONEncoder().encode(receipt) else { return }
        try? data.write(to: directory.appendingPathComponent(receiptName))
    }

    // MARK: - IO

    private static func fileSize(at url: URL) -> Int? {
        (try? FileManager.default.attributesOfItem(atPath: url.path))?[.size] as? Int
    }

    /// Streaming sha256 — the shards are gigabytes, so never materialise one.
    private static func sha256(of url: URL) -> String? {
        guard let handle = try? FileHandle(forReadingFrom: url) else { return nil }
        defer { try? handle.close() }
        var hasher = SHA256()
        while let chunk = try? handle.read(upToCount: 8 * 1024 * 1024), !chunk.isEmpty {
            hasher.update(data: chunk)
        }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }
}
