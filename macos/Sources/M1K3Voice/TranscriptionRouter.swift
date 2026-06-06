//
//  TranscriptionRouter.swift
//  M1K3Voice
//
//  Picks among configured TranscriptionProviders by availability and order —
//  the same simple, tested policy as ProviderRouter (inference). List the
//  primary (WhisperKit, best accuracy) first and Apple Speech (always-available
//  fallback) second; the first available one serves.
//
//  Holds no mutable state (an immutable ordered list of Sendable providers), so
//  it's a value type. It is purely a *selector*: callers capture `activeProvider`
//  once at session start and call start/stop on that captured reference — the
//  router deliberately exposes no session methods, because re-resolving the
//  provider at stop time could target a different engine than start did (e.g. if
//  WhisperKit's model finished loading mid-session).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85,
//  Prior: the internal call-pipeline project TranscriptionRouter (Kev) — simplified to M1K3's
//  ProviderRouter shape (no PerformanceMonitor, no buffer fallback chain).

import Foundation

public struct TranscriptionRouter: Sendable {
    public let providers: [any TranscriptionProvider]

    public init(providers: [any TranscriptionProvider]) {
        self.providers = providers
    }

    /// The provider that would currently serve, if any.
    public var activeProvider: (any TranscriptionProvider)? {
        providers.first { $0.isAvailable }
    }

    public var activeProviderName: String? {
        activeProvider?.name
    }
}
