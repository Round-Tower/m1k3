//
//  HuggingFaceBridge.swift
//  M1K3MLX
//
//  mlx-swift-lm 3.x removed its built-in HuggingFace client: loading takes
//  `any Downloader` + `any TokenizerLoader`. The official adapter packages
//  (DePasqualeOrg swift-tokenizers-mlx / swift-hf-api-mlx) are NOT usable here
//  — their `swift-tokenizers` declares a target literally named `Tokenizers`,
//  colliding with WhisperKit's `swift-transformers` target of the same name
//  (probe-verified: "conflicting name: 'Tokenizers'"). So M1K3 bridges the two
//  small protocols to swift-transformers directly — the same library WhisperKit
//  already pins, and the same HubApi the 2.x line used internally, so the
//  existing downloaded weights are reused byte-for-byte:
//
//    LLM weights      → Library/Caches/models/<id>      (2.x defaultHubApi)
//    embedder weights → Documents/huggingface/models/<id> (2.x HubApi())
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.8 (bridge compiles +
//  mirrors 2.30.6's own Load.swift glue incl. the offline/auth fallbacks; the
//  download path is verify-by-launch). Prior: Unknown.
//

import Foundation
import Hub
import MLXLMCommon
import os
import Synchronization
import Tokenizers

/// Multi-GB model fetches were a silent progress fraction — this category says
/// which model is downloading, how far along, how big on disk, and an ETA.
///   log stream --predicate 'subsystem == "dev.murphysig.M1K3" AND category == "model-download"'
private let downloadLog = Logger(subsystem: "dev.murphysig.M1K3", category: "model-download")

/// Decides which download progress updates become log lines: the first, the
/// last, every 5% of progress, or every 10s of wall clock — whichever first.
/// (HubApi's Progress is FILE-weighted, so the fraction jumps per file; the
/// time floor keeps a single huge safetensors file from going silent.)
struct DownloadLogThrottle {
    private var lastFraction: Double?
    private var lastEmit: ContinuousClock.Instant?

    mutating func shouldEmit(fraction: Double, now: ContinuousClock.Instant) -> Bool {
        guard let lastFraction, let lastEmit else {
            lastFraction = fraction
            lastEmit = now
            return true
        }
        let stepped = fraction - lastFraction >= 0.05
        let stale = now - lastEmit >= .seconds(10)
        let finished = fraction >= 1.0 && lastFraction < 1.0
        guard stepped || stale || finished else { return false }
        self.lastFraction = fraction
        self.lastEmit = now
        return true
    }
}

/// `MLXLMCommon.Downloader` over swift-transformers' `HubApi`. Mirrors the
/// snapshot + offline-fallback behaviour of mlx-swift-lm 2.x's `downloadModel`.
struct HubApiDownloader: MLXLMCommon.Downloader {
    let hub: HubApi

    /// Downloads where 2.x's `defaultHubApi` put LLM weights (caches dir).
    static let llmDefault = HubApiDownloader(
        hub: HubApi(downloadBase: FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first)
    )

    /// Downloads where 2.x's `MLXEmbedders.loadModelContainer` default put
    /// embedder weights (Documents/huggingface).
    static let embedderDefault = HubApiDownloader(hub: HubApi())

    func download(
        id: String,
        revision: String?,
        matching patterns: [String],
        useLatest _: Bool,
        progressHandler: @Sendable @escaping (Progress) -> Void
    ) async throws -> URL {
        let repo = Hub.Repo(id: id)
        let localPath = hub.localRepoLocation(repo)
        let startSizeMB = Self.directorySizeMB(localPath)
        downloadLog.notice(
            "snapshot start: \(id, privacy: .public) rev=\(revision ?? "main", privacy: .public) localMB=\(startSizeMB)"
        )
        let clock = ContinuousClock()
        let started = clock.now
        let throttle = Mutex(DownloadLogThrottle())
        do {
            let url = try await hub.snapshot(
                from: repo,
                revision: revision ?? "main",
                matching: patterns,
                progressHandler: { progress in
                    let fraction = progress.fractionCompleted
                    let now = clock.now
                    if throttle.withLock({ $0.shouldEmit(fraction: fraction, now: now) }) {
                        Self.logProgress(id: id, fraction: fraction, elapsed: now - started, localPath: localPath)
                    }
                    progressHandler(progress)
                }
            )
            let totalSeconds = Int((clock.now - started).components.seconds)
            let sizeMB = Self.directorySizeMB(url)
            downloadLog.notice(
                "snapshot done: \(id, privacy: .public) in \(totalSeconds)s sizeMB=\(sizeMB)"
            )
            return url
        } catch Hub.HubClientError.authorizationRequired {
            // Typically "repo doesn't exist on the server" — fall back to any
            // local copy (same behaviour as 2.x).
            downloadLog.warning(
                "snapshot auth-required for \(id, privacy: .public) — using local copy (localMB=\(startSizeMB))"
            )
            return localPath
        } catch {
            let nserror = error as NSError
            if nserror.domain == NSURLErrorDomain, nserror.code == NSURLErrorNotConnectedToInternet {
                downloadLog.warning(
                    "offline — using local copy of \(id, privacy: .public) (localMB=\(startSizeMB))"
                )
                return localPath
            }
            let reason = error.localizedDescription
            downloadLog.error(
                "snapshot failed: \(id, privacy: .public) — \(reason, privacy: .public)"
            )
            throw error
        }
    }

    /// One throttled progress line: percent, elapsed, ETA projected from the
    /// fraction rate, and what's actually on disk so far.
    private static func logProgress(
        id: String, fraction: Double, elapsed: Duration, localPath: URL
    ) {
        let percent = Int(fraction * 100)
        let elapsedSeconds = Int(elapsed.components.seconds)
        let etaSeconds = fraction > 0.001
            ? Int(Double(elapsedSeconds) * (1 - fraction) / fraction)
            : -1
        let sizeMB = directorySizeMB(localPath)
        downloadLog.notice(
            "\(id, privacy: .public): \(percent)% elapsed=\(elapsedSeconds)s eta=\(etaSeconds)s onDiskMB=\(sizeMB)"
        )
    }

    /// Recursive on-disk size in MB — model repos are ~a dozen files, so this
    /// is a handful of stats, cheap enough at the throttled emission rate.
    private static func directorySizeMB(_ url: URL) -> Int {
        let manager = FileManager.default
        guard let enumerator = manager.enumerator(
            at: url,
            includingPropertiesForKeys: [.totalFileAllocatedSizeKey, .fileSizeKey]
        ) else { return 0 }
        var total = 0
        for case let file as URL in enumerator {
            let values = try? file.resourceValues(forKeys: [.totalFileAllocatedSizeKey, .fileSizeKey])
            total += values?.totalFileAllocatedSize ?? values?.fileSize ?? 0
        }
        return total / 1_048_576
    }
}

/// `MLXLMCommon.TokenizerLoader` over swift-transformers' `AutoTokenizer`.
struct TransformersTokenizerLoader: MLXLMCommon.TokenizerLoader {
    func load(from directory: URL) async throws -> any MLXLMCommon.Tokenizer {
        let upstream = try await AutoTokenizer.from(modelFolder: directory)
        return TransformersTokenizerAdapter(upstream: upstream)
    }
}

/// Adapts a swift-transformers tokenizer to `MLXLMCommon.Tokenizer`.
/// `@unchecked Sendable`: the upstream tokenizer is immutable after load and
/// only read concurrently (same contract WhisperKit relies on).
struct TransformersTokenizerAdapter: MLXLMCommon.Tokenizer, @unchecked Sendable {
    let upstream: any Tokenizers.Tokenizer

    func encode(text: String, addSpecialTokens: Bool) -> [Int] {
        upstream.encode(text: text, addSpecialTokens: addSpecialTokens)
    }

    func decode(tokenIds: [Int], skipSpecialTokens: Bool) -> String {
        upstream.decode(tokens: tokenIds, skipSpecialTokens: skipSpecialTokens)
    }

    func convertTokenToId(_ token: String) -> Int? {
        upstream.convertTokenToId(token)
    }

    func convertIdToToken(_ id: Int) -> String? {
        upstream.convertIdToToken(id)
    }

    var bosToken: String? {
        upstream.bosToken
    }

    var eosToken: String? {
        upstream.eosToken
    }

    var unknownToken: String? {
        upstream.unknownToken
    }

    func applyChatTemplate(
        messages: [[String: any Sendable]],
        tools: [[String: any Sendable]]?,
        additionalContext: [String: any Sendable]?
    ) throws -> [Int] {
        try upstream.applyChatTemplate(
            messages: messages,
            tools: tools,
            additionalContext: additionalContext
        )
    }
}
