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
import Tokenizers

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
        do {
            return try await hub.snapshot(
                from: repo,
                revision: revision ?? "main",
                matching: patterns,
                progressHandler: progressHandler
            )
        } catch Hub.HubClientError.authorizationRequired {
            // Typically "repo doesn't exist on the server" — fall back to any
            // local copy (same behaviour as 2.x).
            return hub.localRepoLocation(repo)
        } catch {
            let nserror = error as NSError
            if nserror.domain == NSURLErrorDomain, nserror.code == NSURLErrorNotConnectedToInternet {
                return hub.localRepoLocation(repo)
            }
            throw error
        }
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
