//
//  WeightImportTests.swift
//  M1K3MLXTests
//
//  Real filesystem tests for WeightImport, same Sandbox-over-a-temp-directory
//  pattern as WeightIntegrityScanTests — no MLX/Metal in this file (pure IO +
//  the pure verdict core), so nothing here needs the app bundle.
//
//  Most tests inject a `pin:` directly (the same testable-core shape as
//  `WeightIntegrityScan.enforce(directory:pin:repoID:)`) so they run against
//  fixtures without touching PinnedWeights.swift. Only `refusesUnpinnedRepo`
//  exercises the public, repoID-only entry point, because that IS the guard
//  it exists to test: a repoID genuinely absent from the shipped manifest.
//

import Foundation
import Hub
@testable import M1K3MLX
import Testing

struct WeightImportTests {
    /// A temp root that cleans itself up. Unlike WeightIntegrityScanTests'
    /// Sandbox, this one hands out TWO locations — `source` (created, and
    /// populated by the test) and `destination` (deliberately NOT
    /// pre-created, mirroring the real model cache directory before a first
    /// download or import).
    private struct Sandbox: ~Copyable {
        let url: URL

        init() throws {
            url = URL(fileURLWithPath: NSTemporaryDirectory())
                .appendingPathComponent("weight-import-\(UUID().uuidString)")
            try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        }

        func makeSource(_ contents: [String: String]) throws -> URL {
            let dir = url.appendingPathComponent("source")
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            for (name, body) in contents {
                try Data(body.utf8).write(to: dir.appendingPathComponent(name))
            }
            return dir
        }

        var destination: URL {
            url.appendingPathComponent("destination")
        }

        deinit { try? FileManager.default.removeItem(at: url) }
    }

    private static func sha256Hex(_ text: String) -> String {
        // Mirrors the production hash so fixtures stay honest — same helper
        // WeightIntegrityScanTests uses.
        WeightIntegrityScan.sha256Hex(of: Data(text.utf8))
    }

    private static func pin(revision: String = "abc", contents: [String: String]) -> WeightIntegrity.Pin {
        var files: [String: WeightIntegrity.PinnedFile] = [:]
        for (name, body) in contents {
            files[name] = .init(size: Data(body.utf8).count, sha256: sha256Hex(body))
        }
        return .init(revision: revision, files: files)
    }

    // MARK: - 1. Unpinned refusal

    @Test("refuses an unpinned repo — there is nothing to verify an imported folder against")
    func refusesUnpinnedRepo() throws {
        let sandbox = try Sandbox()
        let source = try sandbox.makeSource(["model.safetensors": "weights"])

        #expect(throws: WeightImportError.self) {
            try WeightImport.importWeights(
                from: source, repoID: "org/definitely-unpinned-fixture", into: sandbox.destination
            )
        }
        #expect(!FileManager.default.fileExists(atPath: sandbox.destination.path))
    }

    // MARK: - 2. Tampered source

    @Test("refuses a source folder whose bytes disagree with the pin, and copies nothing")
    func refusesTamperedSource() throws {
        let sandbox = try Sandbox()
        // Same length as the pin's expected content, different bytes — a
        // digest disagreement, not a size one.
        let source = try sandbox.makeSource(["model.safetensors": "poisoned!"])
        let pin = Self.pin(contents: ["model.safetensors": "weights!!"])

        #expect(throws: WeightTamperError.self) {
            try WeightImport.importWeights(from: source, pin: pin, repoID: "org/repo", into: sandbox.destination)
        }
        #expect(!FileManager.default.fileExists(atPath: sandbox.destination.path))
    }

    // MARK: - 3. Incomplete source

    @Test("refuses a source folder missing pinned files, and copies nothing")
    func refusesIncompleteSource() throws {
        let sandbox = try Sandbox()
        // Only one of the two pinned files exists at the source.
        let source = try sandbox.makeSource(["model.safetensors": "weights"])
        let pin = Self.pin(contents: ["model.safetensors": "weights", "config.json": "{}"])

        #expect(throws: WeightImportError.self) {
            try WeightImport.importWeights(from: source, pin: pin, repoID: "org/repo", into: sandbox.destination)
        }
        #expect(!FileManager.default.fileExists(atPath: sandbox.destination.path))
    }

    // MARK: - 4. Installs a verified folder

    @Test("installs a verified source folder, copying every pinned file")
    func installsVerifiedSource() throws {
        let sandbox = try Sandbox()
        let source = try sandbox.makeSource(["model.safetensors": "weights", "config.json": "{}"])
        let pin = Self.pin(contents: ["model.safetensors": "weights", "config.json": "{}"])

        let outcome = try WeightImport.importWeights(
            from: source, pin: pin, repoID: "org/repo", into: sandbox.destination
        )

        #expect(outcome == .installed(files: 2))
        let installed = try Data(contentsOf: sandbox.destination.appendingPathComponent("model.safetensors"))
        #expect(String(data: installed, encoding: .utf8) == "weights")
        let config = try Data(contentsOf: sandbox.destination.appendingPathComponent("config.json"))
        #expect(String(data: config, encoding: .utf8) == "{}")
    }

    // MARK: - 5. HubApi-compatible download metadata (the crux)

    @Test("writes HubApi-compatible download metadata HubApi's own parser can read back")
    func writesDownloadMetadata() throws {
        let sandbox = try Sandbox()
        let source = try sandbox.makeSource(["model.safetensors": "weights"])
        let revision = "1111111111111111111111111111111111111111"
        let pin = Self.pin(revision: revision, contents: ["model.safetensors": "weights"])

        _ = try WeightImport.importWeights(from: source, pin: pin, repoID: "org/repo", into: sandbox.destination)

        let metadataURL = sandbox.destination
            .appendingPathComponent(".cache/huggingface/download/model.safetensors.metadata")
        #expect(FileManager.default.fileExists(atPath: metadataURL.path))

        // Not just "a file with 3 lines" — HubApi's OWN reader must accept it,
        // since a format that only OUR string-splitting understands is
        // exactly the kind of drift that reintroduces the 2026-07-16
        // full-re-download incident this file exists to prevent.
        let metadata = try #require(
            try HubApiDownloader.llmDefault.hub.readDownloadMetadata(metadataPath: metadataURL)
        )
        #expect(metadata.commitHash == revision)
        #expect(metadata.etag == Self.sha256Hex("weights"))
    }

    // MARK: - 6. Idempotent

    @Test("importing over an already-verified destination is a no-op — .alreadyPresent, no recopy")
    func idempotentOverVerifiedDestination() throws {
        let sandbox = try Sandbox()
        let source = try sandbox.makeSource(["model.safetensors": "weights"])
        let pin = Self.pin(contents: ["model.safetensors": "weights"])

        let first = try WeightImport.importWeights(
            from: source, pin: pin, repoID: "org/repo", into: sandbox.destination
        )
        #expect(first == .installed(files: 1))

        // Destroy the source BEFORE the second call. A verified destination
        // must not need the source at all — that is exactly what "no
        // recopying gigabytes" means: the second call can't be reading
        // source to decide, because source is gone.
        try FileManager.default.removeItem(at: source)

        let second = try WeightImport.importWeights(
            from: source, pin: pin, repoID: "org/repo", into: sandbox.destination
        )
        #expect(second == .alreadyPresent)
    }

    // MARK: - 7. Never half-installed

    @Test("a copy failure partway through leaves no trace at destination")
    func installFailurePartwayLeavesNoTrace() throws {
        let sandbox = try Sandbox()
        // "config.json" is pinned but deliberately absent from source — this
        // stands in for a crash/IO failure mid-copy (copyItem throws)
        // without needing to defeat the pre-copy verify gate first. Calling
        // `install` directly (below the verify gate) isolates exactly the
        // atomicity contract under test: ANY failure between the first file
        // landing and the last must not leave partial bytes at `destination`.
        let source = try sandbox.makeSource(["model.safetensors": "weights"])
        let pin = Self.pin(contents: ["model.safetensors": "weights", "config.json": "{}"])

        #expect(throws: (any Error).self) {
            _ = try WeightImport.install(pin: pin, source: source, repoID: "org/repo", destination: sandbox.destination)
        }

        #expect(!FileManager.default.fileExists(atPath: sandbox.destination.path))
        // No orphaned staging directory left behind beside it either.
        let siblings = (try? FileManager.default.contentsOfDirectory(atPath: sandbox.url.path)) ?? []
        #expect(!siblings.contains { $0.hasPrefix(".m1k3-import-") })
    }

    // MARK: - 8. Source untouched

    @Test("never touches the source folder — copy, never move")
    func leavesSourceUntouched() throws {
        let sandbox = try Sandbox()
        let source = try sandbox.makeSource(["model.safetensors": "weights"])
        let pin = Self.pin(contents: ["model.safetensors": "weights"])

        _ = try WeightImport.importWeights(from: source, pin: pin, repoID: "org/repo", into: sandbox.destination)

        let stillThere = try Data(contentsOf: source.appendingPathComponent("model.safetensors"))
        #expect(String(data: stillThere, encoding: .utf8) == "weights")
    }

    // MARK: - Convenience destination resolution

    @Test("the convenience destination is exactly where HubApiDownloader would have downloaded to")
    func defaultDestinationMatchesDownloader() {
        let repoID = "org/repo"
        #expect(
            WeightImport.defaultDestination(for: repoID)
                == HubApiDownloader.llmDefault.hub.localRepoLocation(Hub.Repo(id: repoID))
        )
    }
}
