//
//  M1K3Model.swift
//  M1K3Agent
//
//  The `LanguageModelDescribing` conformance (ADR 0001): a brain as a describable
//  model that vends a `M1K3ModelExecutor` over M1K3's real `ToolTurnSession`. This
//  is the rung the EscalationLadder selects and the framework (on macOS 27) drives.
//  `makeExecutor()` is synchronous — the async session work is deferred into the
//  executor's first `respond`, matching Apple's lifecycle.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.85 (descriptor + wiring
//  TDD'd end-to-end incl. the real ToolCallingProvider path; live MLX is the
//  verify-at-launch edge). Prior: Kev + claude-opus-4-8
//

import Foundation
import M1K3Inference
import M1K3LanguageModel

/// A M1K3 brain presented as a `LanguageModelDescribing`. Holds the registry
/// descriptor (what the ladder chooses on) and a session factory (how the executor
/// reaches the real engine).
public struct M1K3Model: LanguageModelDescribing {
    public let descriptor: LanguageModelDescriptor
    private let systemPrompt: String?
    private let sessionFactory: @Sendable () async throws -> any ToolTurnSession

    /// Primary init — inject any session factory (real provider, or a fake in tests).
    public init(
        descriptor: LanguageModelDescriptor,
        systemPrompt: String? = nil,
        sessionFactory: @escaping @Sendable () async throws -> any ToolTurnSession
    ) {
        self.descriptor = descriptor
        self.systemPrompt = systemPrompt
        self.sessionFactory = sessionFactory
    }

    public func makeExecutor() -> any LanguageModelExecuting {
        M1K3ModelExecutor(systemPrompt: systemPrompt, makeSession: sessionFactory)
    }
}

public extension M1K3Model {
    /// Real-provider wiring: build the session factory from a `ToolCallingProvider`
    /// (e.g. the MLX brain). The factory defers `makeToolTurnSession` (async) to the
    /// executor's first request, so a fresh session — and its KV cache — is created
    /// per executor and reused across that conversation's turns.
    init(
        descriptor: LanguageModelDescriptor,
        provider: any ToolCallingProvider,
        tools: [ToolDefinition],
        options: ToolTurnOptions = .default,
        systemPrompt: String? = nil
    ) {
        self.init(descriptor: descriptor, systemPrompt: systemPrompt) {
            try await provider.makeToolTurnSession(tools: tools, options: options)
        }
    }
}
