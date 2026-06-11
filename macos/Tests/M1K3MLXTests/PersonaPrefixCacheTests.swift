//
//  PersonaPrefixCacheTests.swift
//  M1K3MLXTests
//
//  The persona prefix cache key: a cached system-block prefill is only valid
//  for the exact (model × tools × persona text) it was rendered from — Qwen
//  renders TOOLS inside the SYSTEM block, so a different tool set is a
//  different prefix, and a profile update (future) changes the persona hash.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.85, Prior: Unknown
//

import Foundation
@testable import M1K3MLX
import Testing

struct PersonaPrefixCacheTests {
    @Test("the key fingerprints model, tools, and persona text")
    func keyComponents() {
        let base = PersonaCacheKey(
            modelID: "mlx-community/Qwen3.5-2B-4bit",
            toolNames: ["web_search", "datetime"],
            personaText: "You are M1K3."
        )
        let sameDifferentOrder = PersonaCacheKey(
            modelID: "mlx-community/Qwen3.5-2B-4bit",
            toolNames: ["datetime", "web_search"],
            personaText: "You are M1K3."
        )
        #expect(base == sameDifferentOrder) // tool ORDER must not matter

        let differentTools = PersonaCacheKey(
            modelID: "mlx-community/Qwen3.5-2B-4bit",
            toolNames: ["datetime"],
            personaText: "You are M1K3."
        )
        #expect(base != differentTools)

        let differentPersona = PersonaCacheKey(
            modelID: "mlx-community/Qwen3.5-2B-4bit",
            toolNames: ["web_search", "datetime"],
            personaText: "You are M1K3, updated."
        )
        #expect(base != differentPersona)
    }

    @Test("a mismatched key yields no snapshot; invalidate clears the slot")
    func missAndInvalidate() {
        let store = PersonaPrefixCache()
        let key = PersonaCacheKey(modelID: "m", toolNames: [], personaText: "p")
        #expect(store.snapshot(for: key) == nil)

        store.store([], tokenIDs: Array(0 ..< 42), for: key)
        #expect(store.snapshot(for: key)?.tokenCount == 42)
        #expect(store.snapshot(for: key)?.tokenIDs == Array(0 ..< 42))

        let otherKey = PersonaCacheKey(modelID: "m", toolNames: ["x"], personaText: "p")
        #expect(store.snapshot(for: otherKey) == nil)

        store.invalidate()
        #expect(store.snapshot(for: key) == nil)
    }
}
