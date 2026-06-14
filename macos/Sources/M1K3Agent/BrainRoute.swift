//
//  BrainRoute.swift
//  M1K3Agent
//
//  The app-facing routing entry point (ADR 0001). Wraps the pure
//  BrainCatalogue/EscalationLadder and maps the chosen descriptor onto a small
//  enum the app can switch on WITHOUT importing M1K3LanguageModel (the app links
//  M1K3Agent, not the pure module). Routing, not flagging — see ADR 0001.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.9 (mapping test-pinned;
//  the live read of SystemLanguageModel.availability is the app edge). Prior: Kev + claude-opus-4-8
//

import Foundation
import M1K3LanguageModel

/// Which brain rung the ladder chose, in terms the app maps to a `BrainTier`.
public enum BrainRoute: Sendable, Equatable {
    case mlxFloor
    case appleOnDevice
    case privateCloud
    case thirdParty(String)
}

public enum M1K3BrainRouter {
    /// Resolve the brain for a request from device capability + consent. The app
    /// passes the live `SystemLanguageModel.availability` and its egress/prefs; the
    /// ladder decides. Network rungs require both egress AND explicit escalation.
    public static func route(
        appleIntelligenceAvailable: Bool,
        networkAllowed: Bool,
        preferAppleOnDevice: Bool,
        escalation: Escalation = .none,
        catalogue: BrainCatalogue = .standard()
    ) -> BrainRoute {
        let context = LadderContext(
            appleIntelligenceAvailable: appleIntelligenceAvailable,
            networkAllowed: networkAllowed,
            userEscalation: escalation,
            preferAppleOnDevice: preferAppleOnDevice
        )
        guard let descriptor = catalogue.route(context) else { return .mlxFloor }
        switch descriptor.id {
        case LanguageModelDescriptor.appleOnDevice.id:
            return .appleOnDevice
        case LanguageModelDescriptor.privateCloudCompute.id:
            return .privateCloud
        case LanguageModelDescriptor.mlxFloor.id:
            return .mlxFloor
        default:
            return descriptor.reach == .thirdParty ? .thirdParty(descriptor.id) : .mlxFloor
        }
    }
}
