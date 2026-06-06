//
//  ProviderRouter.swift
//  M1K3Inference
//
//  Picks among configured InferenceProviders by availability and order, with
//  fall-through on failure. This is the tested logic of the inference layer;
//  the providers themselves are thin OS/runtime adapters. Holds no mutable
//  state (an immutable ordered list of Sendable providers), so it's a value
//  type — no actor needed.
//
//  Routing policy (deliberately simple for the MVP):
//    - Providers are tried in declaration order: list AFM first for cheap turns,
//      MLX Gemma next as the main brain, LiteRT as the spike.
//    - `generate` skips unavailable providers and falls through to the next on
//      error, surfacing the last error only if everything fails.
//    - `generateStreaming` streams from the first available provider.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation

public struct ProviderRouter: Sendable {
    public let providers: [any InferenceProvider]

    public init(providers: [any InferenceProvider]) {
        self.providers = providers
    }

    /// The provider that would currently serve, if any.
    public var activeProvider: (any InferenceProvider)? {
        providers.first { $0.isAvailable }
    }

    public var activeProviderName: String? {
        activeProvider?.name
    }

    /// Generate, trying available providers in order and falling through on
    /// error. Throws `.noProviderAvailable` if none are available, or the last
    /// backend error if every available provider failed.
    public func generate(prompt: String) async throws -> String {
        var lastError: Error?
        var anyAvailable = false
        for provider in providers where provider.isAvailable {
            anyAvailable = true
            do {
                return try await provider.generate(prompt: prompt)
            } catch {
                lastError = error
                continue
            }
        }
        if let lastError { throw lastError }
        _ = anyAvailable
        throw InferenceError.noProviderAvailable
    }

    /// Stream from the first available provider. Returns an immediately-finished
    /// stream when nothing is available (callers see an empty sequence).
    public func generateStreaming(prompt: String) -> AsyncStream<String> {
        guard let provider = activeProvider else {
            return AsyncStream { $0.finish() }
        }
        return provider.generateStreaming(prompt: prompt)
    }
}
