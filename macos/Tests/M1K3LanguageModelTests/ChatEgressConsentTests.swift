//
//  ChatEgressConsentTests.swift
//  M1K3LanguageModelTests
//
//  Pins the dedicated chat-egress consent (Phase 17a): the ladder's
//  `networkAllowed` gate resolves from ITS OWN default-OFF key, never from the
//  web-search toggle (which defaults ON and governs web TOOLS, a different
//  egress category). An absent value is a NO — consent is given, never assumed.
//
//  Signed: Kev + claude-fable-5, 2026-07-08, Confidence 0.9 (the default-OFF
//  semantics are the whole point of the seam; challenger finding folded).
//  Prior: Unknown
//

@testable import M1K3LanguageModel
import Testing

struct ChatEgressConsentTests {
    @Test("no stored value means NO — consent is never assumed")
    func absentIsDenied() {
        #expect(ChatEgressConsent.networkAllowed(persisted: nil) == false)
    }

    @Test("an explicit false stays false")
    func explicitFalseDenied() {
        #expect(ChatEgressConsent.networkAllowed(persisted: false) == false)
    }

    @Test("only an explicit true opens the gate")
    func explicitTrueAllows() {
        #expect(ChatEgressConsent.networkAllowed(persisted: true) == true)
    }

    @Test("the gate feeds the ladder: no consent → a PCC escalation resolves local")
    func deniedGateKeepsEscalationLocal() {
        let floor = LanguageModelDescriptor(
            id: "lil-4b", reach: .onDevice, capabilities: [.toolCalling],
            requiresAppleIntelligence: false, isLocalFloor: true
        )
        let pcc = LanguageModelDescriptor(
            id: "apple-pcc", reach: .privateCloud, capabilities: [.toolCalling]
        )
        let context = LadderContext(
            appleIntelligenceAvailable: true,
            networkAllowed: ChatEgressConsent.networkAllowed(persisted: nil),
            userEscalation: .privateCloud
        )
        let pick = EscalationLadder.select(context, from: [floor, pcc])
        #expect(pick?.id == "lil-4b")
    }
}
