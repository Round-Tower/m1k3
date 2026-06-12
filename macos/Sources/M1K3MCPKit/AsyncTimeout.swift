//
//  AsyncTimeout.swift
//  M1K3MCPKit
//
//  A deadline around an async operation. The MCP `ask_m1k3` tool is
//  single-flight (one local generation at a time); a runaway or pathologically
//  slow generation used to hold that lock for MINUTES with no way to release
//  it — a client-side timeout never cancels the server-side work (test-report
//  F1, observed live: a ~5-minute wedge that bounced every later ask).
//
//  This races the operation against a timer in a task group: whichever finishes
//  first wins, and leaving the group cancels the loser. On expiry the operation
//  task is cancelled — for the ask path that propagates through the response
//  stream's onTermination into the agent loop, actually STOPPING the generation
//  and freeing the lock, not just abandoning a still-running task.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-12, Confidence 0.85 (helper
//  test-pinned; live generation-cancellation is verify-at-⌘R — the MLX loop
//  honours Task cancellation per iteration). Prior: the MCP test report
//  2026-06-11 (F1).
//

import Foundation

/// Thrown by `withTimeout` when the operation outlives its deadline.
public struct TimeoutError: Error, CustomStringConvertible, Equatable {
    public let seconds: Double
    public init(seconds: Double) {
        self.seconds = seconds
    }

    public var description: String {
        "operation timed out after \(seconds)s"
    }
}

/// Run `operation`, but throw `TimeoutError` (and cancel it) if it runs longer
/// than `seconds`. The operation's own thrown error propagates unchanged when
/// it loses the race to its own failure rather than the clock.
public func withTimeout<T: Sendable>(
    seconds: Double,
    operation: @escaping @Sendable () async throws -> T
) async throws -> T {
    try await withThrowingTaskGroup(of: T.self) { group in
        group.addTask { try await operation() }
        group.addTask {
            try await Task.sleep(for: .seconds(seconds))
            throw TimeoutError(seconds: seconds)
        }
        defer { group.cancelAll() } // cancel the loser on any exit (win, timeout, throw)
        // The first task to finish decides the outcome; `next()` rethrows the
        // timer's TimeoutError or the operation's own error.
        guard let result = try await group.next() else {
            throw TimeoutError(seconds: seconds)
        }
        return result
    }
}
