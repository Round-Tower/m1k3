//
//  WhisperKitProviderTests.swift
//  M1K3WhisperKitTests
//
//  WhisperKit is a CoreML/mic OS adapter — model download + live transcription
//  are verified by launching the app, not here. We pin the deterministic parts:
//  identity, the "unavailable until a model loads" contract, and that it slots
//  into the TranscriptionProvider seam (the routing logic is tested in M1K3Voice).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import M1K3Voice
@testable import M1K3WhisperKit
import Testing

struct WhisperKitProviderTests {
    @Test("has the expected stable name")
    func name() {
        #expect(WhisperKitProvider().name == "WhisperKit")
    }

    @Test("is unavailable until a model is loaded")
    func unavailableBeforeModelLoad() {
        // No prepareModel call → no model → router should prefer Apple Speech.
        #expect(WhisperKitProvider().isAvailable == false)
    }

    @Test("starting without a loaded model throws, not crashes")
    func startWithoutModelThrows() {
        #expect(throws: WhisperKitProviderError.self) {
            _ = try WhisperKitProvider().startListening()
        }
    }

    @Test("conforms to TranscriptionProvider and slots into a router")
    func conformsToSeam() {
        let provider: any TranscriptionProvider = WhisperKitProvider()
        let router = TranscriptionRouter(providers: [provider])
        // Unavailable (no model) → router resolves nothing, no crash.
        #expect(router.activeProvider == nil)
    }
}
