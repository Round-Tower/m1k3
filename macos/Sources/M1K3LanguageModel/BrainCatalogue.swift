//
//  BrainCatalogue.swift
//  M1K3LanguageModel
//
//  The ROUTE side of ADR 0001: the set of brain rungs M1K3 offers, and a single
//  entry point that asks the EscalationLadder which one to use for a request. This
//  is the product mechanism — NOT a feature flag. New rungs (Apple on-device, PCC,
//  third-parties) are added here; the ladder routes to them by capability + consent.
//
//  Pure: `appleIntelligenceAvailable` is passed in (the app reads the real
//  `SystemLanguageModel.availability`), so the catalogue stays dependency-free and
//  fully testable on macOS 26.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.9 (pure + test-pinned;
//  the live availability read + chat-path use are the app-integration edge).
//  Prior: Kev + claude-opus-4-8
//

import Foundation

/// Standard descriptors for M1K3's brain rungs. Capabilities are conservative
/// floors — a rung can always declare more.
public extension LanguageModelDescriptor {
    /// M1K3's bundled MLX brain — the always-available offline floor.
    static let mlxFloor = LanguageModelDescriptor(
        id: "m1k3-mlx",
        reach: .onDevice,
        capabilities: [.toolCalling, .reasoning],
        requiresAppleIntelligence: false,
        isLocalFloor: true
    )

    /// Apple's on-device `SystemLanguageModel` — usable only where the silicon
    /// supports Apple Intelligence.
    static let appleOnDevice = LanguageModelDescriptor(
        id: "apple-on-device",
        reach: .onDevice,
        capabilities: [.toolCalling, .reasoning, .vision, .guidedGeneration],
        requiresAppleIntelligence: true,
        isLocalFloor: false
    )

    /// Apple Private Cloud Compute — privacy-preserving, opt-in escalation.
    static let privateCloudCompute = LanguageModelDescriptor(
        id: "apple-pcc",
        reach: .privateCloud,
        capabilities: [.toolCalling, .reasoning, .vision, .guidedGeneration]
    )

    /// A named third-party cloud model (e.g. "claude", "gemini") — explicit
    /// per-request escalation. The id is what `Escalation.thirdParty(_:)` matches.
    static func thirdParty(_ id: String) -> LanguageModelDescriptor {
        LanguageModelDescriptor(
            id: id, reach: .thirdParty,
            capabilities: [.toolCalling, .reasoning, .vision, .guidedGeneration]
        )
    }
}

/// The brain rungs M1K3 offers, plus the single routing entry point.
public struct BrainCatalogue: Sendable {
    public let descriptors: [LanguageModelDescriptor]

    public init(descriptors: [LanguageModelDescriptor]) {
        self.descriptors = descriptors
    }

    /// Ask the ladder which rung to use for this request. Routing, never flagging:
    /// offline by default, network only on explicit escalation + egress.
    public func route(_ context: LadderContext) -> LanguageModelDescriptor? {
        EscalationLadder.select(context, from: descriptors)
    }
}

public extension BrainCatalogue {
    /// The standard M1K3 catalogue: the MLX floor + Apple on-device always present;
    /// PCC and third-parties opt-in (a rung being PRESENT doesn't make it reachable —
    /// the ladder still gates it on egress + escalation).
    static func standard(
        includePrivateCloudCompute: Bool = true,
        thirdParties: [String] = ["claude", "gemini"]
    ) -> BrainCatalogue {
        var descriptors: [LanguageModelDescriptor] = [.mlxFloor, .appleOnDevice]
        if includePrivateCloudCompute { descriptors.append(.privateCloudCompute) }
        descriptors.append(contentsOf: thirdParties.map { .thirdParty($0) })
        return BrainCatalogue(descriptors: descriptors)
    }
}
