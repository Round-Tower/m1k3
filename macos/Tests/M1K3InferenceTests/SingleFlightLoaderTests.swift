//
//  SingleFlightLoaderTests.swift
//  M1K3InferenceTests
//
//  Contract tests for the single-flight async loader — the pure concurrency
//  primitive behind "don't download the ~1GB MLX container twice". The old
//  MLXGemmaProvider used a check-then-act NSLock: read the cache under the lock,
//  release it, then do the slow load with no lock held — so two concurrent
//  callers (a Settings preload racing the first generate) both saw nil and both
//  loaded. This actor makes concurrent callers share ONE load by construction,
//  and it's Metal-free, so the race fix is provable under `swift test`.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-08, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Inference
import Testing

private actor InvocationCounter {
    private(set) var count = 0
    func bump() -> Int {
        count += 1
        return count
    }
}

private final class ProgressCollector: @unchecked Sendable {
    private let lock = NSLock()
    private var values: [Double] = []
    func record(_ value: Double) {
        lock.withLock { values.append(value) }
    }

    var recorded: [Double] {
        lock.withLock { values }
    }
}

private enum LoaderTestError: Error { case boom }

struct SingleFlightLoaderTests {
    @Test("concurrent callers share one load (the double-load race fix)")
    func concurrentCallersShareOneLoad() async throws {
        let counter = InvocationCounter()
        let loader = SingleFlightLoader<Int> { _ in
            _ = await counter.bump()
            try? await Task.sleep(for: .milliseconds(30))
            return 42
        }

        async let first = loader.value()
        async let second = loader.value()
        async let third = loader.value()
        let results = try await [first, second, third]

        #expect(results == [42, 42, 42])
        #expect(await counter.count == 1) // operation ran exactly once
    }

    @Test("a successful load is cached for later callers")
    func successIsCached() async throws {
        let counter = InvocationCounter()
        let loader = SingleFlightLoader<Int> { _ in
            _ = await counter.bump()
            return 7
        }

        _ = try await loader.value()
        _ = try await loader.value()
        _ = try await loader.value()

        #expect(await counter.count == 1)
    }

    @Test("a failed load clears the slot so a retry runs again")
    func failureAllowsRetry() async throws {
        let counter = InvocationCounter()
        let loader = SingleFlightLoader<Int> { _ in
            let attempt = await counter.bump()
            if attempt == 1 { throw LoaderTestError.boom }
            return 99
        }

        await #expect(throws: LoaderTestError.self) { _ = try await loader.value() }
        let retried = try await loader.value()

        #expect(retried == 99)
        #expect(await counter.count == 2) // failed once, then succeeded
    }

    @Test("progress from the operation reaches the caller's handler")
    func forwardsProgress() async throws {
        let collector = ProgressCollector()
        let loader = SingleFlightLoader<Int> { progress in
            progress(0.5)
            progress(1.0)
            return 1
        }

        _ = try await loader.value { collector.record($0) }

        #expect(collector.recorded == [0.5, 1.0])
    }
}
