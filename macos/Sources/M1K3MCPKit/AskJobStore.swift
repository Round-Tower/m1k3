//
//  AskJobStore.swift
//  M1K3MCPKit
//
//  The job registry behind ask_m1k3's submit-and-poll path. A long local turn
//  (web search + a verbose thinker) can out-run the MCP client's ~60s request
//  deadline, so ask_m1k3 no longer blocks on the whole generation: it submits a
//  job here, waits a short grace window for the fast case, and otherwise returns
//  a job id the client fetches later via get_answer. The generation runs
//  detached and writes its result back here when it finishes.
//
//  Pure actor — no inference/store links — so the lifecycle is unit-tested with
//  injected id/clock. Survives across the stateless HTTP transport's per-request
//  sessions because the host builds ONE store and captures it in the tool
//  closures (which outlive every request).
//
//  Signed: Kev + claude-opus-4-8, 2026-07-01, Confidence 0.85 (lifecycle
//  TDD'd; the live generation write-back is verify-by-launch). Prior: Unknown.

import Foundation

public actor AskJobStore {
    public enum Status: Sendable, Equatable {
        case running
        case done(String)
        case error(String)
    }

    private struct Job {
        var status: Status
        var finishedAt: Date?
    }

    /// Terminal jobs older than this are evicted opportunistically on submit —
    /// bounds memory on a long-lived server without a background timer.
    static let jobRetention: TimeInterval = 600

    private let makeID: @Sendable () -> String
    private let now: @Sendable () -> Date
    private var jobs: [String: Job] = [:]

    public init(
        makeID: @escaping @Sendable () -> String = { UUID().uuidString },
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.makeID = makeID
        self.now = now
    }

    /// Register a new running job and return its id. Reaps stale terminal jobs
    /// first so the map can't grow unbounded across a long session.
    public func submit() -> String {
        reap(olderThan: Self.jobRetention)
        let id = makeID()
        jobs[id] = Job(status: .running, finishedAt: nil)
        return id
    }

    public func complete(id: String, result: String) {
        finish(id: id, status: .done(result))
    }

    public func fail(id: String, message: String) {
        finish(id: id, status: .error(message))
    }

    /// The current status, or nil if the id is unknown (never submitted, or reaped).
    public func status(of id: String) -> Status? {
        jobs[id]?.status
    }

    /// Evict terminal (done/error) jobs whose finish time is older than `ttl`.
    /// Running jobs are always kept. Returns how many were removed.
    @discardableResult
    public func reap(olderThan ttl: TimeInterval) -> Int {
        let cutoff = now().addingTimeInterval(-ttl)
        let before = jobs.count
        jobs = jobs.filter { _, job in
            guard let finishedAt = job.finishedAt else { return true } // keep running jobs
            return finishedAt > cutoff
        }
        return before - jobs.count
    }

    /// A terminal transition wins only over `.running` — a late completion can't
    /// clobber an already-failed job, and an unknown id is a safe no-op.
    private func finish(id: String, status: Status) {
        guard var job = jobs[id], case .running = job.status else { return }
        job.status = status
        job.finishedAt = now()
        jobs[id] = job
    }
}
