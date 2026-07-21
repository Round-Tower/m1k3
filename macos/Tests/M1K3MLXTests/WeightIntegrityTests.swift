//
//  WeightIntegrityTests.swift
//  M1K3MLXTests
//
//  The pure verdict rules for weight pinning. Every case here is a decision
//  about trust, so each one is pinned explicitly — especially the distinction
//  the whole design turns on: an ABSENT file is a download in progress, a
//  PRESENT file with the wrong bytes is an attack.
//

import Foundation
import M1K3Inference
@testable import M1K3MLX
import Testing

struct WeightIntegrityTests {
    /// A two-file pin standing in for a real repo: one big shard, one small
    /// config. Digests are arbitrary but fixed — the rules under test are
    /// about agreement, not about any particular hash.
    private static let pin = WeightIntegrity.Pin(
        revision: "aaaabbbbccccdddd",
        files: [
            "model.safetensors": .init(size: 2_263_022_417, sha256: "2a73c6c2"),
            "config.json": .init(size: 938, sha256: "574349e5"),
        ]
    )

    private func observed(
        _ pairs: [String: (Int, String)]
    ) -> [String: WeightIntegrity.ObservedFile] {
        pairs.mapValues { WeightIntegrity.ObservedFile(size: $0.0, sha256: $0.1) }
    }

    @Test("no pin for a repo is not a failure — spikes and legacy caches still load")
    func unpinnedRepoPasses() {
        let verdict = WeightIntegrity.verdict(
            pin: nil,
            observed: observed(["whatever.safetensors": (1, "ff")])
        )
        #expect(verdict == .unpinned)
    }

    @Test("every pinned file present with matching size and digest verifies")
    func allMatchingVerifies() {
        let verdict = WeightIntegrity.verdict(
            pin: Self.pin,
            observed: observed([
                "model.safetensors": (2_263_022_417, "2a73c6c2"),
                "config.json": (938, "574349e5"),
            ])
        )
        #expect(verdict == .verified)
    }

    @Test("an absent pinned file is an in-flight download, NOT an alarm")
    func missingFileIsIncomplete() {
        let verdict = WeightIntegrity.verdict(
            pin: Self.pin,
            observed: observed(["config.json": (938, "574349e5")])
        )
        #expect(verdict == .incomplete(missing: ["model.safetensors"]))
    }

    @Test("a present file whose digest disagrees is tampering — refuse")
    func digestMismatchIsTampered() {
        let verdict = WeightIntegrity.verdict(
            pin: Self.pin,
            observed: observed([
                "model.safetensors": (2_263_022_417, "DEADBEEF"),
                "config.json": (938, "574349e5"),
            ])
        )
        #expect(verdict == .tampered(files: ["model.safetensors"]))
    }

    @Test("a size disagreement is conclusive on its own — no digest needed")
    func sizeMismatchIsTampered() {
        let verdict = WeightIntegrity.verdict(
            pin: Self.pin,
            observed: observed([
                "model.safetensors": (99, "2a73c6c2"),
                "config.json": (938, "574349e5"),
            ])
        )
        #expect(verdict == .tampered(files: ["model.safetensors"]))
    }

    @Test("tampering outranks incompleteness — a wrong file beside a missing one still alarms")
    func tamperOutranksMissing() {
        let verdict = WeightIntegrity.verdict(
            pin: Self.pin,
            observed: observed(["config.json": (938, "WRONG")])
        )
        #expect(verdict == .tampered(files: ["config.json"]))
    }

    @Test("every mismatching file is named, sorted, so the log identifies the whole blast radius")
    func reportsAllTamperedFilesSorted() {
        let verdict = WeightIntegrity.verdict(
            pin: Self.pin,
            observed: observed([
                "model.safetensors": (2_263_022_417, "WRONG"),
                "config.json": (938, "ALSO-WRONG"),
            ])
        )
        #expect(verdict == .tampered(files: ["config.json", "model.safetensors"]))
    }

    @Test("unpinned extras alongside pinned files are ignored — upstream may add README/.gitattributes")
    func extraFilesIgnored() {
        let verdict = WeightIntegrity.verdict(
            pin: Self.pin,
            observed: observed([
                "model.safetensors": (2_263_022_417, "2a73c6c2"),
                "config.json": (938, "574349e5"),
                "README.md": (84, "whatever"),
            ])
        )
        #expect(verdict == .verified)
    }

    @Test("an empty directory is incomplete in pinned-file order, not tampered")
    func emptyDirectoryIsIncomplete() {
        let verdict = WeightIntegrity.verdict(pin: Self.pin, observed: [:])
        #expect(verdict == .incomplete(missing: ["config.json", "model.safetensors"]))
    }
}

struct PinnedWeightsTests {
    @Test("both shipping brains are pinned — the tiers a user can actually select")
    func shippingBrainsArePinned() {
        #expect(PinnedWeights.pin(for: "mlx-community/gemma-4-12B-it-4bit") != nil)
        #expect(PinnedWeights.pin(for: "mlx-community/Qwen3-4B-Instruct-2507-4bit") != nil)
    }

    /// The embedder is quieter than the brains but is the same exposure:
    /// third-party weights fetched at runtime and handed to MLX. It routes
    /// through the same `HubApiDownloader.download` choke point, so pinning
    /// it costs nothing extra and leaving it out would be an arbitrary hole.
    @Test("the retrieval embedder is pinned too, not just the chat brains")
    func embedderIsPinned() {
        #expect(PinnedWeights.pin(for: "mlx-community/Qwen3-Embedding-0.6B-4bit-DWQ") != nil)
    }

    @Test("an unknown repo has no pin — spikes and A/B overrides stay loadable")
    func unknownRepoUnpinned() {
        #expect(PinnedWeights.pin(for: "mlx-community/some-experimental-quant") == nil)
    }

    @Test("every pinned revision is a full 40-char git SHA, never a branch name")
    func revisionsAreCommitSHAs() {
        for (repo, pin) in PinnedWeights.all {
            #expect(pin.revision.count == 40, "\(repo) revision is not a full SHA")
            #expect(
                pin.revision.allSatisfy { $0.isHexDigit && !$0.isUppercase },
                "\(repo) revision is not lowercase hex"
            )
        }
    }

    @Test("every pinned digest is a full sha256 and every size is positive")
    func digestsAreWellFormed() {
        for (repo, pin) in PinnedWeights.all {
            for (name, file) in pin.files {
                #expect(file.sha256.count == 64, "\(repo)/\(name) digest is not sha256-length")
                #expect(
                    file.sha256.allSatisfy { $0.isHexDigit && !$0.isUppercase },
                    "\(repo)/\(name) digest is not lowercase hex"
                )
                #expect(file.size > 0, "\(repo)/\(name) has a non-positive size")
            }
        }
    }

    /// The guard that makes pinning survive contact with a model promotion.
    ///
    /// An unpinned repo loads normally by design, so a tier swapped without
    /// re-running `tools/weights/pin_weights.py` would silently ship
    /// unverified weights — a security regression that breaks nothing visible
    /// and would sail through review. Deriving the expectation from
    /// `BrainTier` itself, rather than a hand-listed set, means the swap
    /// itself turns this red. Both brains moved inside 26 hours in July 2026;
    /// assume the next promotion is in a hurry too.
    @Test("every selectable brain's weights are pinned — a promotion cannot silently unpin")
    func everySelectableBrainIsPinned() {
        for tier in BrainTier.allCases {
            guard let modelID = tier.mlxModelID else { continue }
            #expect(
                PinnedWeights.pin(for: modelID) != nil,
                """
                BrainTier.\(tier) ships \(modelID) with no pin. Add it to \
                SHIPPED_REPOS and re-run tools/weights/pin_weights.py.
                """
            )
        }
    }

    @Test("the weight-bearing files are pinned, not just the small configs")
    func weightsThemselvesArePinned() {
        for (repo, pin) in PinnedWeights.all {
            let hasWeights = pin.files.keys.contains { $0.hasSuffix(".safetensors") }
            #expect(hasWeights, "\(repo) pins no .safetensors — the weights are unprotected")
        }
    }
}
