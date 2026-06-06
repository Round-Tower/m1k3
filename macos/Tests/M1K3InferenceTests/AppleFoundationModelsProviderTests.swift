//
//  AppleFoundationModelsProviderTests.swift
//  M1K3InferenceTests
//
//  The AFM provider is an OS adapter — generate()/streaming hit Apple
//  Intelligence hardware, so they aren't exercised here. We pin the parts that
//  are deterministic: its identity and that it conforms to the router's seam.
//  `isAvailable` is read but not asserted (it depends on the host machine).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

@testable import M1K3Inference
import Testing

struct AppleFoundationModelsProviderTests {
    @Test("has the expected stable name")
    func name() {
        #expect(AppleFoundationModelsProvider().name == "apple-foundation-models")
    }

    @Test("conforms to InferenceProvider and slots into a router")
    func conformsAndRoutes() {
        let provider = AppleFoundationModelsProvider()
        let router = ProviderRouter(providers: [provider])
        // Reads availability without asserting a value (host-dependent).
        _ = provider.isAvailable
        // When available on this host it is the active provider; either way the
        // call resolves against the protocol seam.
        #expect(router.providers.count == 1)
    }
}
