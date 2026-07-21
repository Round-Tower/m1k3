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
//  Review: Kev + claude-opus-4-8, 2026-07-21, Confidence 0.9. THE CAVEAT ABOVE
//  WAS WRONG and is retracted: this file imports CryptoKit, Foundation and os
//  and nothing else, so there is no metallib to wall it off — the excuse was
//  inherited reflexively from the rest of M1K3MLX rather than checked against
//  this file's own imports. `WeightIntegrityScanTests` now covers hashing, the
//  receipt round-trip and both throw paths against real temp directories.
//  Three review folds ride with it: receipts moved OUT of the model directory
//  (the download globs include `*.json`, so a hostile repo could otherwise
//  plant one, and the manifest it would need to forge is public); a mid-read
//  I/O error now returns nil instead of a silently-partial digest that would
//  read as tampering forever; and a present-but-unreadable file is now
//  `.unverifiable` rather than taking the benign in-flight path unchecked.
//  Prior: Kev + claude-opus-4-8.
//

import CryptoKit
import Foundation
import os

private let integrityLog = Logger(subsystem: "app.m1k3", category: "weight-integrity")

public enum WeightIntegrityScan {
    /// ⚠️ Receipts live OUTSIDE the model directory, and that is a security
    /// property rather than tidiness.
    ///
    /// The download globs include `*.json`, so anything the remote repo
    /// contains — including a file named exactly like a receipt — lands inside
    /// the model directory. A receipt read from there would therefore be
    /// attacker-supplied, and since `PinnedWeights.swift` ships in a public
    /// repo, the revision and sizes it would need to forge are public too.
    /// Storing receipts in a sibling directory the downloader never writes to
    /// removes the forgery surface entirely, instead of relying on a filename
    /// that happens not to match today's glob list (which upstream controls).
    static let receiptsDirectoryName = ".m1k3-receipts"

    /// Where the receipt for a model directory lives: a sibling of the repo
    /// folder, under the org directory that `HubApi` lays out.
    static func receiptURL(forModelAt directory: URL) -> URL {
        directory
            .deletingLastPathComponent()
            .appendingPathComponent(receiptsDirectoryName)
            .appendingPathComponent("\(directory.lastPathComponent).receipt")
    }

    static func receiptExists(forModelAt directory: URL) -> Bool {
        FileManager.default.fileExists(atPath: receiptURL(forModelAt: directory).path)
    }

    /// Verify the weights for `repoID` against the shipped manifest.
    public static func enforce(directory: URL, repoID: String) throws {
        try enforce(directory: directory, pin: PinnedWeights.pin(for: repoID), repoID: repoID)
    }

    /// Verify `directory` against `pin`.
    ///
    /// Throws `WeightTamperError` when a present file's bytes disagree with the
    /// pin, and `WeightUnverifiableError` when a present file could not be read
    /// to check at all. Unpinned repos and in-flight downloads return normally:
    /// this is a tripwire on the bytes we ship, not a gate on everything
    /// loadable.
    ///
    /// Deliberately does NOT delete on tamper, unlike `ModelCacheIntegrity`'s
    /// torn-cache heal. A torn cache is an accident worth silently repairing; a
    /// digest mismatch is evidence, and re-downloading from the same source
    /// would loop forever while hiding an active attack behind flaky-looking
    /// networking.
    ///
    /// The `pin` is a parameter rather than a lookup so the whole enforcement
    /// path is testable against fixtures without touching the shipped manifest.
    static func enforce(directory: URL, pin: WeightIntegrity.Pin?, repoID: String) throws {
        guard let pin else { return }

        if trustsReceipt(directory: directory, pin: pin) { return }

        var observed: [String: WeightIntegrity.ObservedFile] = [:]
        var unreadable: Set<String> = []
        for (name, expected) in pin.files {
            let fileURL = directory.appendingPathComponent(name)
            // Absent is the ordinary in-flight case and must stay benign, so
            // existence is checked separately from readability: only a file
            // that IS there but resists reading counts as unverifiable.
            guard FileManager.default.fileExists(atPath: fileURL.path) else { continue }
            guard let size = fileSize(at: fileURL) else {
                unreadable.insert(name)
                continue
            }
            // A size disagreement is already conclusive, so skip the hash —
            // this also means a truncated multi-GB shard costs nothing to reject.
            guard size == expected.size else {
                observed[name] = .init(size: size, sha256: "")
                continue
            }
            guard let digest = sha256(of: fileURL) else {
                unreadable.insert(name)
                continue
            }
            observed[name] = .init(size: size, sha256: digest)
        }

        switch WeightIntegrity.verdict(pin: pin, observed: observed, unreadable: unreadable) {
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
        case let .unverifiable(files):
            integrityLog.error(
                """
                cannot verify \(repoID, privacy: .public): unreadable \
                (\(files.joined(separator: ", "), privacy: .public)). Failing closed rather \
                than loading unchecked weights.
                """
            )
            throw WeightUnverifiableError(repoID: repoID, files: files)
        case .verified:
            integrityLog.notice(
                "verified \(repoID, privacy: .public) against pinned revision \(pin.revision, privacy: .public)"
            )
            writeReceipt(directory: directory, pin: pin)
        }
    }

    // MARK: - Receipt

    /// True when a previous run hashed THESE bytes under THIS revision and
    /// nothing has been rewritten since. The modification-time clause is what
    /// makes the shortcut safe: a re-download from a compromised host bumps
    /// mtime even if the replacement shard is byte-for-byte the same size, so
    /// it forces a fresh hash rather than inheriting the old attestation.
    private static func trustsReceipt(directory: URL, pin: WeightIntegrity.Pin) -> Bool {
        guard
            let data = try? Data(contentsOf: receiptURL(forModelAt: directory)),
            let receipt = try? JSONDecoder().decode(WeightIntegrity.Receipt.self, from: data)
        else { return false }

        return WeightIntegrity.receiptStillValid(
            receipt: receipt,
            pin: pin,
            observed: stamps(in: directory, for: pin)
        )
    }

    private static func writeReceipt(directory: URL, pin: WeightIntegrity.Pin) {
        let receipt = WeightIntegrity.Receipt(
            revision: pin.revision,
            files: stamps(in: directory, for: pin)
        )
        guard let data = try? JSONEncoder().encode(receipt) else { return }
        let destination = receiptURL(forModelAt: directory)
        try? FileManager.default.createDirectory(
            at: destination.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try? data.write(to: destination)
    }

    // MARK: - IO

    /// Size + mtime for each pinned file present on disk.
    private static func stamps(
        in directory: URL,
        for pin: WeightIntegrity.Pin
    ) -> [String: WeightIntegrity.FileStamp] {
        var result: [String: WeightIntegrity.FileStamp] = [:]
        for name in pin.files.keys {
            let path = directory.appendingPathComponent(name).path
            guard
                let attributes = try? FileManager.default.attributesOfItem(atPath: path),
                let size = attributes[.size] as? Int,
                let modified = attributes[.modificationDate] as? Date
            else { continue }
            result[name] = .init(size: size, modified: modified.timeIntervalSinceReferenceDate)
        }
        return result
    }

    private static func fileSize(at url: URL) -> Int? {
        (try? FileManager.default.attributesOfItem(atPath: url.path))?[.size] as? Int
    }

    /// Streaming sha256 — the shards are gigabytes, so never materialise one.
    ///
    /// ⚠️ The read loop is do/catch, NOT `try?`, and the difference is a real
    /// bug that review caught. `FileHandle.read(upToCount:)` returns empty Data
    /// at EOF but THROWS on an I/O error, and `try?` collapsed both into "stop
    /// looping" — so a disk hiccup partway through a 6.7 GB shard finalised a
    /// hash over only the bytes read so far and returned it as if it were the
    /// whole file. That digest would then disagree with the pin and be reported
    /// as tampering, on a path deliberately built never to self-heal: a
    /// transient read error turned into a permanent, accusatory refusal.
    /// A failed read must return nil (unverifiable), never a confident answer.
    private static func sha256(of url: URL) -> String? {
        guard let handle = try? FileHandle(forReadingFrom: url) else { return nil }
        defer { try? handle.close() }
        var hasher = SHA256()
        do {
            while let chunk = try handle.read(upToCount: 8 * 1024 * 1024), !chunk.isEmpty {
                hasher.update(data: chunk)
            }
        } catch {
            return nil
        }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }

    /// sha256 of in-memory bytes. Exists so tests can compute the same digest
    /// the production path does, rather than restating CryptoKit's shape.
    static func sha256Hex(of data: Data) -> String {
        var hasher = SHA256()
        hasher.update(data: data)
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }
}
