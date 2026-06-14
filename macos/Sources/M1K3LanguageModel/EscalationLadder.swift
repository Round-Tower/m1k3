//
//  EscalationLadder.swift
//  M1K3LanguageModel
//
//  The consent-gated model-selection policy (ADR 0001). Pure and total: given the
//  device's capability, the global egress switch, and the user's per-request
//  escalation, it picks exactly one model — and NEVER a network model unless the
//  user explicitly escalated AND egress is allowed. This is where M1K3's three
//  goals (device spectrum · offline ethos · inference-at-user's-request) become code.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.9 (pure policy, fully
//  test-pinned; proven first in scratch/wwdc26-languagemodel). Prior: Unknown
//

import Foundation

/// What the user has explicitly opted into for THIS request. `.none` is the default
/// and keeps everything on-device.
public enum Escalation: Sendable, Equatable {
    case none
    case privateCloud
    case thirdParty(String)
}

/// The inputs the ladder decides from.
public struct LadderContext: Sendable, Equatable {
    /// Device can run Apple's on-device `SystemLanguageModel`.
    public var appleIntelligenceAvailable: Bool
    /// Global egress switch (M1K3's existing web-search toggle). Hard gate.
    public var networkAllowed: Bool
    /// The user's explicit, per-request escalation.
    public var userEscalation: Escalation

    public init(
        appleIntelligenceAvailable: Bool,
        networkAllowed: Bool,
        userEscalation: Escalation
    ) {
        self.appleIntelligenceAvailable = appleIntelligenceAvailable
        self.networkAllowed = networkAllowed
        self.userEscalation = userEscalation
    }
}

public enum EscalationLadder {
    /// Pick the model. Rules (in order):
    ///  1. Offline default — the best on-device model: Apple on-device where the
    ///     silicon allows, otherwise M1K3's local MLX floor. No consent needed.
    ///  2. Egress hard gate — if `networkAllowed` is false, never leave the device.
    ///  3. Escalation — only an explicit user escalation reaches a network rung;
    ///     a missing target falls back to the local choice (never silently downgraded
    ///     to a *different* network model).
    ///
    /// Returns `nil` only when the catalogue has no usable on-device model AND no
    /// matching escalation target (in practice the local floor is always present).
    public static func select(
        _ context: LadderContext,
        from catalogue: [LanguageModelDescriptor]
    ) -> LanguageModelDescriptor? {
        let local = bestLocal(in: catalogue, appleIntelligence: context.appleIntelligenceAvailable)

        // Egress hard gate: no network model without the switch on.
        guard context.networkAllowed else { return local }

        switch context.userEscalation {
        case .none:
            return local
        case .privateCloud:
            return catalogue.first { $0.reach == .privateCloud } ?? local
        case let .thirdParty(provider):
            let match = catalogue.first { $0.reach == .thirdParty && $0.id == provider }
            return match ?? local
        }
    }

    /// Best on-device model: prefer Apple's on-device model when available, else the
    /// declared local floor, else any other on-device model.
    private static func bestLocal(
        in catalogue: [LanguageModelDescriptor],
        appleIntelligence: Bool
    ) -> LanguageModelDescriptor? {
        let onDevice = catalogue.filter { $0.reach == .onDevice }
        if appleIntelligence, let apple = onDevice.first(where: { $0.requiresAppleIntelligence }) {
            return apple
        }
        // Apple model unusable (or absent) → the always-available floor.
        return onDevice.first { $0.isLocalFloor }
            ?? onDevice.first { !$0.requiresAppleIntelligence }
            ?? onDevice.first
    }
}
