//
//  SingleFlightLoader.swift
//  M1K3Inference
//
//  A single-flight async loader: concurrent callers share ONE in-flight load
//  instead of each kicking off their own. This replaces the check-then-act
//  NSLock MLXGemmaProvider used to cache its model container — that pattern read
//  the cache under the lock, released it, then ran the slow load with no lock
//  held, so two callers (a Settings preload racing the first generate) could both
//  pass the "not loaded yet" check and redundantly download the ~1GB container.
//
//  An actor serialises entry; the first caller starts the load and stores its
//  Task before suspending, so every reentrant caller awaits that same Task. On
//  success the Task is kept and returns its value instantly to later callers; on
//  failure the slot is cleared so a subsequent call retries rather than replaying
//  a stale error forever. Lives in the seam module (no Metal), so the concurrency
//  contract is proven under `swift test`, not stuck behind the MLX launch wall.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-08, Confidence 0.85, Prior: Unknown

import Foundation

/// Coalesces concurrent loads of a single expensive, cacheable value into one
/// in-flight operation. Generic over the loaded `Value` (e.g. an MLX
/// `ModelContainer`); the operation receives a `progress` handler so a download
/// can report a 0...1 fraction to whichever caller started it.
public actor SingleFlightLoader<Value: Sendable> {
    public typealias Operation =
        @Sendable (_ progress: @escaping @Sendable (Double) -> Void) async throws -> Value

    private let operation: Operation
    private var task: Task<Value, Error>?

    public init(operation: @escaping Operation) {
        self.operation = operation
    }

    /// Return the loaded value, starting the operation if it isn't already in
    /// flight. Concurrent callers await the same load. `progress` is wired to the
    /// operation only for the call that actually starts it; later callers that
    /// join an in-flight (or completed) load just receive the result.
    public func value(
        progress: @escaping @Sendable (Double) -> Void = { _ in }
    ) async throws -> Value {
        if let task {
            return try await task.value
        }
        let operation = self.operation
        let task = Task { try await operation(progress) }
        self.task = task
        do {
            return try await task.value
        } catch {
            // Clear the failed load so the next caller retries instead of
            // re-throwing this cached error indefinitely.
            self.task = nil
            throw error
        }
    }
}
