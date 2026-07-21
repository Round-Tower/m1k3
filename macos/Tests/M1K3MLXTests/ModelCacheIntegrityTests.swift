//
//  ModelCacheIntegrityTests.swift
//  M1K3MLXTests
//
//  Pins the torn-cache verdicts (the 2026-07-16 quantize-fatalError class) and
//  the heal path — a real on-disk fixture per verdict, no MLX/Metal needed.
//

import Foundation
@testable import M1K3MLX
import Testing

struct ModelCacheIntegrityTests {
    // MARK: - Pure verdicts

    @Test("index fully backed by shards → ok")
    func completeIsOK() {
        let verdict = ModelCacheIntegrity.verdict(
            indexWeightFiles: ["model-00001-of-00002.safetensors", "model-00002-of-00002.safetensors"],
            safetensorsSizes: [
                "model-00001-of-00002.safetensors": 1000,
                "model-00002-of-00002.safetensors": 900,
            ]
        )
        #expect(verdict == .ok)
    }

    @Test("index references a shard the disk doesn't have → TORN (the crash state)")
    func missingShardIsTorn() {
        let verdict = ModelCacheIntegrity.verdict(
            indexWeightFiles: ["model-00001-of-00002.safetensors", "model-00002-of-00002.safetensors"],
            safetensorsSizes: ["model-00001-of-00002.safetensors": 1000]
        )
        guard case let .torn(reason) = verdict else {
            Issue.record("expected torn, got \(verdict)")
            return
        }
        #expect(reason.contains("model-00002-of-00002.safetensors"))
    }

    @Test("a zero-byte shard is torn even when every index entry exists")
    func zeroByteShardIsTorn() {
        let verdict = ModelCacheIntegrity.verdict(
            indexWeightFiles: ["model.safetensors"],
            safetensorsSizes: ["model.safetensors": 0]
        )
        guard case .torn = verdict else {
            Issue.record("expected torn, got \(verdict)")
            return
        }
    }

    @Test("index present but NO shards yet is the pre-download state, not torn")
    func indexBeforeShardsIsEmpty() {
        // HubApi fetches config/index first; judging that moment torn would
        // delete a healthy in-progress download on every retry.
        let verdict = ModelCacheIntegrity.verdict(
            indexWeightFiles: ["model-00001-of-00002.safetensors"],
            safetensorsSizes: [:]
        )
        #expect(verdict == .empty)
    }

    @Test("no index, weights present → ok (single-file legacy caches can't be validated)")
    func noIndexWithWeightsIsOK() {
        let verdict = ModelCacheIntegrity.verdict(
            indexWeightFiles: [],
            safetensorsSizes: ["model.safetensors": 500]
        )
        #expect(verdict == .ok)
    }

    @Test("nothing at all → empty (the normal first-download state)")
    func bareIsEmpty() {
        #expect(ModelCacheIntegrity.verdict(indexWeightFiles: [], safetensorsSizes: [:]) == .empty)
    }

    // MARK: - Disk scan + heal

    private func makeModelDir(_ files: [String: Data]) throws -> URL {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("integrity-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        for (name, data) in files {
            try data.write(to: dir.appendingPathComponent(name))
        }
        return dir
    }

    private func indexJSON(shards: [String]) -> Data {
        let map = Dictionary(uniqueKeysWithValues: shards.enumerated().map { ("w\($0.offset)", $0.element) })
        return try! JSONSerialization.data(withJSONObject: ["weight_map": map])
    }

    @Test("scan reads a real torn directory as torn — and heal clears the load path")
    func scanAndHealTornDirectory() throws {
        let dir = try makeModelDir([
            "config.json": Data("{}".utf8),
            "model.safetensors.index.json": indexJSON(
                shards: ["model-00001-of-00002.safetensors", "model-00002-of-00002.safetensors"]
            ),
            "model-00001-of-00002.safetensors": Data(repeating: 1, count: 64),
            // shard 2 missing — the exact 2026-07-16 crash shape
        ])
        defer {
            try? FileManager.default.removeItem(at: dir)
            try? FileManager.default.removeItem(at: ModelCacheIntegrity.quarantineURL(for: dir))
        }

        guard case .torn = ModelCacheIntegrity.scan(directory: dir) else {
            Issue.record("expected torn scan")
            return
        }
        guard case .torn = ModelCacheIntegrity.healBeforeLoad(directory: dir) else {
            Issue.record("expected torn heal verdict")
            return
        }
        // The load path must be clear, so HubApi re-downloads cleanly.
        #expect(!FileManager.default.fileExists(atPath: dir.path))
    }

    /// The change of policy, and the reason for it: a torn directory still
    /// holds gigabytes the user paid bandwidth and time for. Deleting it bets
    /// the whole download on the re-download succeeding — and weights live in
    /// `Library/Caches`, which the system may purge under pressure, so "this
    /// user needs the weights again" is a state the app can cause by itself.
    /// Moving the partial aside keeps the recovery while keeping the bytes.
    @Test("heal QUARANTINES a torn directory rather than destroying the bytes")
    func healQuarantinesRatherThanDeletes() throws {
        let dir = try makeModelDir([
            "model.safetensors.index.json": indexJSON(
                shards: ["model-00001-of-00002.safetensors", "model-00002-of-00002.safetensors"]
            ),
            "model-00001-of-00002.safetensors": Data(repeating: 1, count: 64),
        ])
        let quarantine = ModelCacheIntegrity.quarantineURL(for: dir)
        defer {
            try? FileManager.default.removeItem(at: dir)
            try? FileManager.default.removeItem(at: quarantine)
        }

        _ = ModelCacheIntegrity.healBeforeLoad(directory: dir)

        #expect(!FileManager.default.fileExists(atPath: dir.path))
        #expect(FileManager.default.fileExists(
            atPath: quarantine.appendingPathComponent("model-00001-of-00002.safetensors").path
        ))
    }

    /// Quarantine must be bounded. A second tear cannot be allowed to stack
    /// another copy of a multi-GB partial beside the first.
    @Test("a second tear replaces the quarantine instead of accumulating copies")
    func quarantineIsBounded() throws {
        let quarantineHolder = try makeModelDir(["stale.safetensors": Data(repeating: 9, count: 8)])
        let dir = try makeModelDir([
            "model.safetensors.index.json": indexJSON(shards: ["a.safetensors", "b.safetensors"]),
            "a.safetensors": Data(repeating: 1, count: 64),
        ])
        let quarantine = ModelCacheIntegrity.quarantineURL(for: dir)
        try? FileManager.default.removeItem(at: quarantine)
        try FileManager.default.moveItem(at: quarantineHolder, to: quarantine)
        defer {
            try? FileManager.default.removeItem(at: dir)
            try? FileManager.default.removeItem(at: quarantine)
        }

        _ = ModelCacheIntegrity.healBeforeLoad(directory: dir)

        // The older quarantine is gone, replaced by this tear's partial.
        #expect(!FileManager.default.fileExists(atPath: quarantine.appendingPathComponent("stale.safetensors").path))
        #expect(FileManager.default.fileExists(atPath: quarantine.appendingPathComponent("a.safetensors").path))
    }

    @Test("reclaiming drops the quarantine once a good copy is in place")
    func reclaimDropsQuarantine() throws {
        let dir = try makeModelDir(["model.safetensors": Data(repeating: 1, count: 8)])
        let quarantine = ModelCacheIntegrity.quarantineURL(for: dir)
        try? FileManager.default.removeItem(at: quarantine)
        try FileManager.default.createDirectory(at: quarantine, withIntermediateDirectories: true)
        defer {
            try? FileManager.default.removeItem(at: dir)
            try? FileManager.default.removeItem(at: quarantine)
        }

        ModelCacheIntegrity.reclaimQuarantine(for: dir)

        #expect(!FileManager.default.fileExists(atPath: quarantine.path))
    }

    @Test("heal leaves a complete directory alone")
    func healLeavesCompleteAlone() throws {
        let dir = try makeModelDir([
            "model.safetensors.index.json": indexJSON(shards: ["model.safetensors"]),
            "model.safetensors": Data(repeating: 1, count: 64),
        ])
        defer { try? FileManager.default.removeItem(at: dir) }

        #expect(ModelCacheIntegrity.healBeforeLoad(directory: dir) == .ok)
        #expect(FileManager.default.fileExists(atPath: dir.path))
    }

    @Test("heal leaves an absent/empty directory alone (first download)")
    func healLeavesEmptyAlone() {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("integrity-absent-\(UUID().uuidString)")
        #expect(ModelCacheIntegrity.healBeforeLoad(directory: dir) == .empty)
    }

    @Test("missing shard WITH resumable .incomplete parts is a paused download — never torn")
    func missingShardWithResumablePartsIsResuming() {
        // The review catch: shard 1 renamed-complete + shard 2 still resumable
        // is the ordinary state of ANY interrupted multi-shard download. Torn
        // here would delete gigabytes of healthy partial on every retry. The
        // crash state has NO .incomplete (HubApi believed the snapshot done).
        let verdict = ModelCacheIntegrity.verdict(
            indexWeightFiles: ["model-00001-of-00002.safetensors", "model-00002-of-00002.safetensors"],
            safetensorsSizes: ["model-00001-of-00002.safetensors": 1000],
            hasResumableParts: true
        )
        #expect(verdict == .resuming)
    }

    @Test("scan detects .incomplete under .cache and heal leaves the resumable download alone")
    func healLeavesResumableDownloadAlone() throws {
        let dir = try makeModelDir([
            "model.safetensors.index.json": indexJSON(
                shards: ["model-00001-of-00002.safetensors", "model-00002-of-00002.safetensors"]
            ),
            "model-00001-of-00002.safetensors": Data(repeating: 1, count: 64),
        ])
        defer { try? FileManager.default.removeItem(at: dir) }
        let staging = dir.appendingPathComponent(".cache/huggingface/download")
        try FileManager.default.createDirectory(at: staging, withIntermediateDirectories: true)
        try Data(repeating: 2, count: 8).write(
            to: staging.appendingPathComponent("model-00002-of-00002.safetensors.abc123.incomplete")
        )

        #expect(ModelCacheIntegrity.healBeforeLoad(directory: dir) == .resuming)
        #expect(FileManager.default.fileExists(atPath: dir.path))
    }

    @Test("foreign .metadata WITHOUT .incomplete does not protect — that is the crash state")
    func metadataAloneDoesNotProtect() throws {
        // The 2026-07-16 poisoned pre-seed left .cache/…/*.metadata but no
        // .incomplete — HubApi believed the snapshot complete and loaded a
        // missing-shard dir into the quantize fatalError. Metadata alone must
        // NOT read as resumable.
        let dir = try makeModelDir([
            "model.safetensors.index.json": indexJSON(
                shards: ["model-00001-of-00002.safetensors", "model-00002-of-00002.safetensors"]
            ),
            "model-00001-of-00002.safetensors": Data(repeating: 1, count: 64),
        ])
        defer { try? FileManager.default.removeItem(at: dir) }
        let staging = dir.appendingPathComponent(".cache/huggingface/download")
        try FileManager.default.createDirectory(at: staging, withIntermediateDirectories: true)
        try Data("etag".utf8).write(
            to: staging.appendingPathComponent("model-00002-of-00002.safetensors.metadata")
        )

        guard case .torn = ModelCacheIntegrity.healBeforeLoad(directory: dir) else {
            Issue.record("expected torn — metadata without incomplete is the crash state")
            return
        }
        #expect(!FileManager.default.fileExists(atPath: dir.path))
    }
}
