//
//  WeightIntegrity.swift
//  M1K3MLX
//
//  Supply-chain tripwire for downloaded model weights. Every brain M1K3 runs
//  is fetched at runtime from a third-party host (HuggingFace), and until this
//  file existed we fetched `main` — whatever that pointed at, whenever the
//  user happened to onboard — and handed the bytes straight to MLX. A bad day
//  upstream (host compromise, a hijacked org account, a force-pushed repo)
//  meant arbitrary weights executing on the user's machine with no tripwire.
//
//  The defence is two-part and both parts are needed:
//    1. PIN A REVISION, so "what we ship" stops being a moving target. Note
//       this also turns a real, already-observed behaviour into an explicit
//       one: upstream repos change under us (the 2026-07-09 gemma chat-template
//       fix was expected to arrive purely via a weights re-pull, with no code
//       change and no review on our side). Pinning makes that a decision.
//    2. VERIFY THE BYTES against digests committed to THIS repo. Fetching the
//       expected hash from the same host that serves the file proves nothing —
//       whoever can swap the file can swap its published hash. The manifest
//       must travel with our source, signed and reviewed, or it is theatre.
//
//  ⚠️ The verdicts are deliberately NOT symmetric with ModelCacheIntegrity's,
//  and the difference is the point. A TORN cache is an accident, so it heals
//  silently: delete, re-download, carry on. TAMPERING is not an accident, so
//  it must NEVER auto-heal — silently re-fetching from the same poisoned
//  source would spin an infinite re-download loop that hides an active attack
//  behind what looks like flaky networking. Tampering refuses, loudly, and
//  the error is deliberately not retryable (`withRetry`'s default
//  `isTransientNetworkError` won't match it, so the load fails to
//  `AppReadiness.failed` instead of looping).
//
//  The other asymmetry that matters: an ABSENT pinned file is NOT tampering.
//  Mid-download, most files are absent — that is the overwhelmingly common
//  case, and treating it as an attack would alarm on every first run. Absent
//  is `.incomplete`; present-but-wrong is `.tampered`.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-21, Confidence 0.9 (the verdict
//  rules are pure and exhaustively pinned; the shipped manifest's digests were
//  computed from the on-disk snapshots that produced the 2026-07-15/16 eval
//  results AND independently agree with HuggingFace's published LFS oids for
//  all five large files — two parties confirming the same bytes). Honest
//  caveat: the refuse-on-tamper path is verify-by-launch like every MLX path
//  here, and pinning is bootstrapped from bytes we already trusted, which is
//  the irreducible trust-on-first-use step every pinning scheme has.
//  Prior: Unknown
//

import Foundation

/// Pure trust verdict over a model directory, against a manifest pinned in
/// our own source. IO (hashing, disk walking) lives in `WeightIntegrityScan`;
/// the decision is testable without a filesystem or a network.
public enum WeightIntegrity {
    /// One pinned file: the size is a cheap pre-filter (a disagreement is
    /// already conclusive, no hashing required) and the digest is the proof.
    public struct PinnedFile: Equatable, Sendable {
        public let size: Int
        public let sha256: String

        public init(size: Int, sha256: String) {
            self.size = size
            self.sha256 = sha256
        }
    }

    /// A repo pinned to one immutable commit plus the digests of the files we
    /// actually download from it.
    public struct Pin: Equatable, Sendable {
        /// Full 40-char commit SHA — never a branch name. A branch is exactly
        /// the moving target this type exists to remove.
        public let revision: String
        public let files: [String: PinnedFile]

        public init(revision: String, files: [String: PinnedFile]) {
            self.revision = revision
            self.files = files
        }
    }

    public struct ObservedFile: Equatable, Sendable {
        public let size: Int
        public let sha256: String

        public init(size: Int, sha256: String) {
            self.size = size
            self.sha256 = sha256
        }
    }

    public enum Verdict: Equatable, Sendable {
        /// No manifest entry for this repo. Deliberately permissive: eval
        /// A/B overrides and the spike checkpoints must stay loadable, and
        /// refusing every unpinned repo would make the instrument unusable.
        /// Pinning protects what we SHIP; it is not an allowlist.
        case unpinned
        /// Every pinned file is present and its bytes are the expected bytes.
        case verified
        /// Pinned files not on disk yet — a download in progress, benign.
        case incomplete(missing: [String])
        /// Files present whose bytes disagree with the manifest. Refuse.
        case tampered(files: [String])
    }

    /// The decision core. `observed` covers the files actually on disk; files
    /// present but not pinned are ignored (upstream is free to add a README).
    ///
    /// Ordering rule: tampering outranks incompleteness. A half-downloaded
    /// directory that ALSO contains one wrong file is an attack in progress,
    /// not a slow download, and must not be reported as the benign case.
    public static func verdict(
        pin: Pin?,
        observed: [String: ObservedFile]
    ) -> Verdict {
        guard let pin else { return .unpinned }

        var missing: [String] = []
        var tampered: [String] = []

        for (name, expected) in pin.files {
            guard let actual = observed[name] else {
                missing.append(name)
                continue
            }
            if actual.size != expected.size || actual.sha256 != expected.sha256 {
                tampered.append(name)
            }
        }

        if !tampered.isEmpty { return .tampered(files: tampered.sorted()) }
        if !missing.isEmpty { return .incomplete(missing: missing.sorted()) }
        return .verified
    }
}

/// Thrown when downloaded weights disagree with the pinned manifest. Not a
/// network error by construction, so `withRetry` will not spin on it.
public struct WeightTamperError: Error, CustomStringConvertible, Sendable {
    public let repoID: String
    public let files: [String]

    public init(repoID: String, files: [String]) {
        self.repoID = repoID
        self.files = files
    }

    public var description: String {
        """
        Refusing to load \(repoID): downloaded weights do not match the digests \
        pinned in this build (\(files.joined(separator: ", "))). The files were \
        NOT deleted — they are evidence. This is not retried on purpose.
        """
    }
}
