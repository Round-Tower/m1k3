//
//  EscalationLadderTests.swift
//  M1K3LanguageModelTests
//
//  Pins the consent-gated selection policy (ADR 0001): the offline default, the
//  egress hard gate, and explicit-escalation routing. These assertions ARE the
//  offline-ethos guarantee.
//

@testable import M1K3LanguageModel
import Testing

private enum Fixtures {
    static let mlxFloor = LanguageModelDescriptor(
        id: "lil-4b", reach: .onDevice, capabilities: [.toolCalling, .reasoning],
        requiresAppleIntelligence: false, isLocalFloor: true
    )
    static let appleOnDevice = LanguageModelDescriptor(
        id: "apple-fm", reach: .onDevice, capabilities: [.toolCalling, .reasoning, .vision],
        requiresAppleIntelligence: true, isLocalFloor: false
    )
    static let pcc = LanguageModelDescriptor(
        id: "apple-pcc", reach: .privateCloud, capabilities: [.toolCalling, .reasoning, .vision]
    )
    static let claude = LanguageModelDescriptor(
        id: "claude", reach: .thirdParty, capabilities: [.toolCalling, .reasoning, .vision]
    )
    static let gemini = LanguageModelDescriptor(
        id: "gemini", reach: .thirdParty, capabilities: [.toolCalling, .reasoning, .vision]
    )
    static let all = [mlxFloor, appleOnDevice, pcc, claude, gemini]
}

struct EscalationLadderTests {
    private func ctx(
        ai: Bool, net: Bool, esc: Escalation
    ) -> LadderContext {
        LadderContext(appleIntelligenceAvailable: ai, networkAllowed: net, userEscalation: esc)
    }

    @Test("old device, offline default → the MLX floor (device spectrum)")
    func oldDeviceOfflineFloor() {
        let pick = EscalationLadder.select(ctx(ai: false, net: false, esc: .none), from: Fixtures.all)
        #expect(pick == Fixtures.mlxFloor)
        #expect(pick?.reach.isOffline == true)
    }

    @Test("new device, offline default → Apple on-device")
    func newDeviceOfflineApple() {
        let pick = EscalationLadder.select(ctx(ai: true, net: false, esc: .none), from: Fixtures.all)
        #expect(pick == Fixtures.appleOnDevice)
    }

    @Test("network ON but no escalation → still on-device (ethos holds)")
    func networkOnNoEscalationStaysLocal() {
        let pick = EscalationLadder.select(ctx(ai: true, net: true, esc: .none), from: Fixtures.all)
        #expect(pick?.reach == .onDevice)
    }

    @Test("user escalates to private cloud → PCC")
    func escalatePrivateCloud() {
        let pick = EscalationLadder.select(ctx(ai: true, net: true, esc: .privateCloud), from: Fixtures.all)
        #expect(pick == Fixtures.pcc)
    }

    @Test("user asks a named third party → that provider, not another")
    func escalateThirdPartyByName() {
        let pick = EscalationLadder.select(
            ctx(ai: true, net: true, esc: .thirdParty("claude")), from: Fixtures.all
        )
        #expect(pick == Fixtures.claude)
    }

    @Test("THE INVARIANT: escalation without the egress switch → stays on-device")
    func escalationWithoutEgressStaysLocal() {
        let pick = EscalationLadder.select(
            ctx(ai: true, net: false, esc: .thirdParty("claude")), from: Fixtures.all
        )
        #expect(pick?.reach == .onDevice)
        #expect(pick == Fixtures.appleOnDevice)
    }

    @Test("missing escalation target falls back to LOCAL, never another network model")
    func missingTargetFallsBackToLocalNotNetwork() {
        // Ask for a provider that isn't in the catalogue.
        let pick = EscalationLadder.select(
            ctx(ai: true, net: true, esc: .thirdParty("nope")), from: Fixtures.all
        )
        #expect(pick?.reach == .onDevice, "must NOT silently route to a different cloud model")
    }

    @Test("private-cloud escalation with no PCC present falls back to local")
    func privateCloudAbsentFallsBackLocal() {
        let noPCC = [Fixtures.mlxFloor, Fixtures.appleOnDevice, Fixtures.claude]
        let pick = EscalationLadder.select(ctx(ai: true, net: true, esc: .privateCloud), from: noPCC)
        #expect(pick?.reach == .onDevice)
    }

    @Test("Apple Intelligence claimed but no Apple model present → the floor")
    func appleClaimedButAbsentUsesFloor() {
        let noApple = [Fixtures.mlxFloor, Fixtures.pcc, Fixtures.claude]
        let pick = EscalationLadder.select(ctx(ai: true, net: false, esc: .none), from: noApple)
        #expect(pick == Fixtures.mlxFloor)
    }

    @Test("empty catalogue → nil (degenerate, documents the floor invariant)")
    func emptyCatalogueIsNil() {
        #expect(EscalationLadder.select(ctx(ai: true, net: true, esc: .privateCloud), from: []) == nil)
    }
}

struct GenerationChannelTests {
    @Test("answer and reasoning are routed to separate sinks")
    func separateSinks() {
        let channel = GenerationChannel()
        channel.appendReasoning("<think>weighing it</think>")
        channel.appendText("Here you go.")
        channel.updateUsage(inputTokens: 42)
        #expect(channel.answer == "Here you go.")
        #expect(channel.reasoning == "<think>weighing it</think>")
        #expect(channel.answer.contains("<think>") == false)
        #expect(channel.inputTokenCount == 42)
    }
}

struct ReachTests {
    @Test("only on-device is offline")
    func offlineClassification() {
        #expect(Reach.onDevice.isOffline)
        #expect(Reach.privateCloud.isOffline == false)
        #expect(Reach.thirdParty.isOffline == false)
    }
}
