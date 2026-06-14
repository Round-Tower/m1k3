//
//  BrainCatalogueTests.swift
//  M1K3LanguageModelTests
//
//  Pins the route side of ADR 0001: the standard catalogue's rungs and that routing
//  honours capability + consent end to end.
//

@testable import M1K3LanguageModel
import Testing

struct BrainCatalogueTests {
    private func ctx(ai: Bool, net: Bool, esc: Escalation) -> LadderContext {
        LadderContext(appleIntelligenceAvailable: ai, networkAllowed: net, userEscalation: esc)
    }

    @Test("the standard catalogue carries floor + Apple on-device + PCC + named third-parties")
    func standardRungs() {
        let ids = BrainCatalogue.standard().descriptors.map(\.id)
        #expect(ids.contains("m1k3-mlx"))
        #expect(ids.contains("apple-on-device"))
        #expect(ids.contains("apple-pcc"))
        #expect(ids.contains("claude"))
        #expect(ids.contains("gemini"))
    }

    @Test("exactly one local floor is declared")
    func oneFloor() {
        let floors = BrainCatalogue.standard().descriptors.filter(\.isLocalFloor)
        #expect(floors.count == 1)
        #expect(floors.first?.id == "m1k3-mlx")
    }

    @Test("old device, offline → routes to the MLX floor")
    func routesFloorOnOldDevice() {
        let pick = BrainCatalogue.standard().route(ctx(ai: false, net: false, esc: .none))
        #expect(pick?.id == "m1k3-mlx")
    }

    @Test("new device, offline → routes to Apple on-device")
    func routesAppleOnNewDevice() {
        let pick = BrainCatalogue.standard().route(ctx(ai: true, net: false, esc: .none))
        #expect(pick?.id == "apple-on-device")
    }

    @Test("explicit Claude escalation with egress → routes to Claude")
    func routesClaudeOnEscalation() {
        let pick = BrainCatalogue.standard().route(ctx(ai: true, net: true, esc: .thirdParty("claude")))
        #expect(pick?.id == "claude")
    }

    @Test("THE INVARIANT through the catalogue: escalation without egress stays on-device")
    func catalogueHonoursEgressGate() {
        let pick = BrainCatalogue.standard().route(ctx(ai: true, net: false, esc: .thirdParty("claude")))
        #expect(pick?.reach.isOffline == true)
    }

    @Test("a catalogue WITHOUT PCC falls back to local on a private-cloud escalation")
    func noPCCFallsBackLocal() {
        let catalogue = BrainCatalogue.standard(includePrivateCloudCompute: false)
        let pick = catalogue.route(ctx(ai: true, net: true, esc: .privateCloud))
        #expect(pick?.reach.isOffline == true)
    }
}
