//
//  AsyncTimeoutTests.swift
//  M1K3MCPKitTests
//
//  The deadline behind ask_m1k3's single-flight lock (test-report F1): a slow
//  operation is cancelled and surfaces TimeoutError; a quick one passes
//  through; an operation's own error wins over the clock.
//

import Foundation
@testable import M1K3MCPKit
import Synchronization
import Testing

struct AsyncTimeoutTests {
    @Test("an operation that finishes in time returns its value")
    func completesInTime() async throws {
        let value = try await withTimeout(seconds: 5) {
            try await Task.sleep(for: .milliseconds(10))
            return 42
        }
        #expect(value == 42)
    }

    @Test("an operation that outlives the deadline throws TimeoutError, fast")
    func timesOut() async {
        let start = ContinuousClock.now
        await #expect(throws: TimeoutError.self) {
            _ = try await withTimeout(seconds: 0.1) {
                try await Task.sleep(for: .seconds(10)) // far past the deadline
                return "should never arrive"
            }
        }
        // Proves we returned on the deadline (0.1s), not after the 10s operation.
        // The bound is half the operation, not a tight multiple of the deadline:
        // the only failure this guards is "blocked for the full 10s", and a loose
        // 5s threshold tolerates CI scheduling jitter that flaked a tighter 2s.
        #expect(start.duration(to: .now) < .seconds(5))
    }

    @Test("timing out cancels the operation")
    func cancelsTheLoser() async {
        let cancelled = Mutex(false)
        await #expect(throws: TimeoutError.self) {
            _ = try await withTimeout(seconds: 0.1) {
                await withTaskCancellationHandler {
                    try? await Task.sleep(for: .seconds(10))
                } onCancel: {
                    cancelled.withLock { $0 = true }
                }
                return "done"
            }
        }
        #expect(cancelled.withLock { $0 } == true)
    }

    @Test("the operation's own error wins over the clock")
    func operationErrorPropagates() async {
        struct Boom: Error {}
        await #expect(throws: Boom.self) {
            _ = try await withTimeout(seconds: 5) {
                throw Boom()
            }
        }
    }
}
