//
//  SwappableInferenceProviderTests.swift
//  M1K3InferenceTests
//
//  Contract tests for the runtime-swappable inference façade — the single MLX
//  slot that re-points Lil ↔ Big without rebuilding the RAG/runtime stack that
//  holds it. Promoted from the app target (PR #10 follow-up, issue #11) so the
//  swap logic is provable under `swift test`.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-09, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Inference
import Testing

/// A trivial provider that reports its own name + availability and echoes them
/// back from generate, so a swap is observable through the façade.
private struct StubProvider: InferenceProvider {
    let name: String
    let isAvailable: Bool

    func generate(prompt _: String) async throws -> String {
        name
    }

    func generateStreaming(prompt _: String) -> AsyncStream<String> {
        AsyncStream { continuation in
            continuation.yield(name)
            continuation.finish()
        }
    }
}

struct SwappableInferenceProviderTests {
    @Test("starts on the initial provider")
    func startsOnInitial() async throws {
        let swappable = SwappableInferenceProvider(StubProvider(name: "lil", isAvailable: true))

        #expect(swappable.active.name == "lil")
        #expect(swappable.isAvailable == true)
        #expect(try await swappable.generate(prompt: "hi") == "lil")
    }

    @Test("setProvider re-points the active backend (and its availability)")
    func setProviderSwapsBackend() async throws {
        let swappable = SwappableInferenceProvider(StubProvider(name: "lil", isAvailable: false))
        #expect(swappable.isAvailable == false)

        swappable.setProvider(StubProvider(name: "big", isAvailable: true))

        #expect(swappable.active.name == "big")
        #expect(swappable.isAvailable == true)
        #expect(try await swappable.generate(prompt: "hi") == "big")
    }

    @Test("concurrent reads and swaps are race-free")
    func concurrentReadsAndSwapsAreRaceFree() async {
        let swappable = SwappableInferenceProvider(StubProvider(name: "p0", isAvailable: true))

        // Hammer reads while swapping the backing provider from another task. The
        // lock means every read sees some valid provider name, never a torn value.
        await withTaskGroup(of: Void.self) { group in
            for index in 1 ... 50 {
                group.addTask { swappable.setProvider(StubProvider(name: "p\(index)", isAvailable: true)) }
                group.addTask {
                    let name = swappable.active.name
                    #expect(name.hasPrefix("p"))
                }
            }
        }
    }
}
