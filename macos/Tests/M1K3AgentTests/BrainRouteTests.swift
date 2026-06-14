//
//  BrainRouteTests.swift
//  M1K3AgentTests
//
//  Pins the app-facing routing map (ADR 0001): descriptor → BrainRoute, and that
//  the policy + egress invariant hold through this seam.
//

@testable import M1K3Agent
import Testing

struct BrainRouteTests {
    @Test("default (capable device, offline, no prefs) → M1K3 floor, not AFM")
    func defaultsToFloor() {
        let route = M1K3BrainRouter.route(
            appleIntelligenceAvailable: true, networkAllowed: false, preferAppleOnDevice: false
        )
        #expect(route == .mlxFloor)
    }

    @Test("opt into Apple on-device on capable silicon → appleOnDevice")
    func appleWhenPreferred() {
        let route = M1K3BrainRouter.route(
            appleIntelligenceAvailable: true, networkAllowed: false, preferAppleOnDevice: true
        )
        #expect(route == .appleOnDevice)
    }

    @Test("prefer Apple but no Apple Intelligence → floor")
    func appleUnavailableFallsToFloor() {
        let route = M1K3BrainRouter.route(
            appleIntelligenceAvailable: false, networkAllowed: false, preferAppleOnDevice: true
        )
        #expect(route == .mlxFloor)
    }

    @Test("explicit third-party escalation WITH egress → that provider")
    func thirdPartyEscalation() {
        let route = M1K3BrainRouter.route(
            appleIntelligenceAvailable: true, networkAllowed: true,
            preferAppleOnDevice: false, escalation: .thirdParty("claude")
        )
        #expect(route == .thirdParty("claude"))
    }

    @Test("THE INVARIANT through the router: escalation without egress stays local")
    func escalationWithoutEgressStaysLocal() {
        let route = M1K3BrainRouter.route(
            appleIntelligenceAvailable: true, networkAllowed: false,
            preferAppleOnDevice: false, escalation: .thirdParty("claude")
        )
        #expect(route == .mlxFloor)
    }

    @Test("private-cloud escalation WITH egress → privateCloud")
    func privateCloudEscalation() {
        let route = M1K3BrainRouter.route(
            appleIntelligenceAvailable: true, networkAllowed: true,
            preferAppleOnDevice: false, escalation: .privateCloud
        )
        #expect(route == .privateCloud)
    }
}
