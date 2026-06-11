//
//  RetryTests.swift
//  M1K3InferenceTests
//
//  withRetry retries an async op on TRANSIENT failures (network timeouts, etc.)
//  with backoff, and propagates non-transient errors immediately. Pins call
//  counts + the transient-error predicate; backoff uses a zero base delay so the
//  tests don't actually sleep.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-10, Confidence 0.9, Prior: Unknown

import Foundation
@testable import M1K3Inference
import Synchronization
import Testing

private struct CustomError: Error {}

private let fast = RetryPolicy(maxAttempts: 3, baseDelay: .zero)

struct WithRetryTests {
    @Test("succeeds on the first attempt → one call")
    func firstTry() async throws {
        let calls = Mutex(0)
        let value = try await withRetry(policy: fast) {
            calls.withLock { $0 += 1 }
            return 42
        }
        #expect(value == 42)
        #expect(calls.withLock { $0 } == 1)
    }

    @Test("retries a transient failure, then succeeds")
    func retriesThenSucceeds() async throws {
        let calls = Mutex(0)
        let value = try await withRetry(policy: fast) {
            let attempt = calls.withLock { $0 += 1; return $0 }
            if attempt < 2 { throw URLError(.timedOut) }
            return "ok"
        }
        #expect(value == "ok")
        #expect(calls.withLock { $0 } == 2)
    }

    @Test("a non-transient error throws immediately, no retry")
    func nonTransientNoRetry() async {
        let calls = Mutex(0)
        await #expect(throws: CustomError.self) {
            try await withRetry(policy: fast) {
                calls.withLock { $0 += 1 }
                throw CustomError()
            }
        }
        #expect(calls.withLock { $0 } == 1)
    }

    @Test("exhausts all attempts on persistent transient failure")
    func exhaustsAttempts() async {
        let calls = Mutex(0)
        await #expect(throws: URLError.self) {
            try await withRetry(policy: fast) {
                calls.withLock { $0 += 1 }
                throw URLError(.networkConnectionLost)
            }
        }
        #expect(calls.withLock { $0 } == 3) // maxAttempts
    }

    @Test("onRetry fires once per retry (not on the final failure path's success)")
    func onRetryCallbacks() async throws {
        let retries = Mutex(0)
        let calls = Mutex(0)
        _ = try await withRetry(
            policy: fast,
            onRetry: { _, _ in retries.withLock { $0 += 1 } },
            operation: {
                let attempt = calls.withLock { $0 += 1; return $0 }
                if attempt < 3 { throw URLError(.timedOut) }
                return 0
            }
        )
        #expect(retries.withLock { $0 } == 2) // retried after attempts 1 and 2
    }
}

struct TransientNetworkErrorTests {
    @Test("recognises transient URL errors")
    func transient() {
        #expect(RetryPolicy.isTransientNetworkError(URLError(.timedOut)))
        #expect(RetryPolicy.isTransientNetworkError(URLError(.networkConnectionLost)))
        #expect(RetryPolicy.isTransientNetworkError(URLError(.cannotConnectToHost)))
        #expect(RetryPolicy.isTransientNetworkError(URLError(.dnsLookupFailed)))
    }

    @Test("does not retry non-transient or non-network errors")
    func nonTransient() {
        #expect(!RetryPolicy.isTransientNetworkError(URLError(.badURL)))
        #expect(!RetryPolicy.isTransientNetworkError(URLError(.unsupportedURL)))
        #expect(!RetryPolicy.isTransientNetworkError(CancellationError()))
        #expect(!RetryPolicy.isTransientNetworkError(CustomError()))
    }
}
