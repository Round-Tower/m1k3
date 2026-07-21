//
//  WeightImportDisplayTests.swift
//  M1K3MLXTests
//
//  Pins WeightImportDisplay's two seams: which tiers even offer an import
//  (Mini has no `mlxModelID` — it must never appear), and how a
//  `WeightImport.Outcome` / thrown error reads on screen. No filesystem, no
//  MLX — pure value mapping, same spirit as WeightImportTests next door.
//

import Foundation
@testable import M1K3Inference
@testable import M1K3MLX
import Testing

struct WeightImportDisplayTests {
    // MARK: - Which tiers offer an import

    @Test("Mini is excluded — it has no mlxModelID, nothing to import")
    func excludesMini() {
        let tiers = WeightImportDisplay.importableTiers()
        #expect(!tiers.contains(.mini))
    }

    @Test("every MLX-backed tier is offered")
    func includesEveryMLXTier() {
        let tiers = WeightImportDisplay.importableTiers()
        #expect(tiers.contains(.lil))
        #expect(tiers.contains(.big))
    }

    // MARK: - Outcome → display (success side)

    @Test("installed pluralizes 'file' correctly and promises no re-download")
    func installedMessagePluralizes() {
        let one = WeightImportDisplay.outcome(for: .installed(files: 1))
        let many = WeightImportDisplay.outcome(for: .installed(files: 3))

        guard case let .success(oneMessage) = one, case let .success(manyMessage) = many else {
            Issue.record("expected .success for an .installed outcome")
            return
        }
        #expect(oneMessage.contains("1 file"))
        #expect(!oneMessage.contains("1 files"))
        #expect(manyMessage.contains("3 files"))
        #expect(oneMessage.localizedCaseInsensitiveContains("not re-download")
            || oneMessage.localizedCaseInsensitiveContains("won't re-download")
            || oneMessage.localizedCaseInsensitiveContains("won’t re-download"))
    }

    @Test("alreadyPresent reads as a plain success, not an error")
    func alreadyPresentIsSuccess() {
        let result = WeightImportDisplay.outcome(for: .alreadyPresent)
        guard case .success = result else {
            Issue.record("expected .success for .alreadyPresent")
            return
        }
    }

    // MARK: - Error → display (the verbatim rule)

    @Test("an unpinned-repo error surfaces its own description verbatim")
    func unpinnedErrorIsVerbatim() {
        let error = WeightImportError.unpinned(repoID: "org/repo")
        let result = WeightImportDisplay.outcome(for: error)
        guard case let .failure(message) = result else {
            Issue.record("expected .failure")
            return
        }
        #expect(message == error.description)
    }

    @Test("an incomplete-source error surfaces its own description verbatim")
    func incompleteErrorIsVerbatim() {
        let error = WeightImportError.incomplete(repoID: "org/repo", missing: ["config.json"])
        let result = WeightImportDisplay.outcome(for: error)
        guard case let .failure(message) = result else {
            Issue.record("expected .failure")
            return
        }
        #expect(message == error.description)
    }

    @Test("a tamper error surfaces its own description verbatim — the accusation is deliberate")
    func tamperErrorIsVerbatim() {
        let error = WeightTamperError(repoID: "org/repo", files: ["model.safetensors"])
        let result = WeightImportDisplay.outcome(for: error)
        guard case let .failure(message) = result else {
            Issue.record("expected .failure")
            return
        }
        #expect(message == error.description)
    }

    @Test("an unverifiable-file error surfaces its own description verbatim")
    func unverifiableErrorIsVerbatim() {
        let error = WeightUnverifiableError(repoID: "org/repo", files: ["model.safetensors"])
        let result = WeightImportDisplay.outcome(for: error)
        guard case let .failure(message) = result else {
            Issue.record("expected .failure")
            return
        }
        #expect(message == error.description)
    }

    @Test("an unrecognized error falls back to localizedDescription, not invented copy")
    func unrecognizedErrorFallsBackToLocalizedDescription() {
        // ⚠️ NOT the same as `.description`: NSObject (which NSError bridges
        // to) conforms to CustomStringConvertible with its own verbose dump
        // ("Error Domain=… Code=… …UserInfo={…}") — a naive `error as? any
        // CustomStringConvertible` cast would ALWAYS succeed and return
        // that instead of this. `outcome(unrecognized:)` never casts, so it
        // can't fall into that trap.
        let error = NSError(domain: "test", code: 1, userInfo: [NSLocalizedDescriptionKey: "disk is on fire"])
        let result = WeightImportDisplay.outcome(unrecognized: error)
        guard case let .failure(message) = result else {
            Issue.record("expected .failure")
            return
        }
        #expect(message == error.localizedDescription)
        #expect(message == "disk is on fire")
    }
}
