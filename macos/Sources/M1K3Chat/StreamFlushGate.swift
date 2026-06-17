//
//  StreamFlushGate.swift
//  M1K3Chat
//
//  Coalesces a high-frequency token stream down to roughly display rate before
//  it touches @Observable state. A fast local model emits dozens of chunks a
//  second; without this, every chunk mutated `ChatSession.messages` and
//  re-invalidated the whole transcript ForEach — the visible-reasoning jank.
//  The frequency was the killer (it also drove the bionic AttributedString
//  rebuild per token); gating the flushes fixes it across every reading mode.
//
//  Time-based, not count-based: token rate varies by backend (AFM snapshots vs
//  delta streams), so a fixed "every N tokens" would stutter on one and lag on
//  another. Pure + clock-injected so the cadence is unit-tested without real
//  time, and the final authoritative update in ChatSession always runs after the
//  loop, so a coalesced-out tail never loses the settled answer.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-17, Confidence 0.85 (the reducer is
//  TDD'd; the live feel — 50ms ≈ 20Hz reads smooth, not laggy — is verify-by-eye
//  at ⌘R). Prior: Unknown.

import Foundation

/// Decides whether enough wall-clock has passed to flush a streaming update to
/// the UI. The first call always flushes (tokens start immediately); subsequent
/// calls flush at most once per `interval`.
public struct StreamFlushGate {
    /// ~20Hz. Fast enough to read as live streaming, slow enough that a 100-tok/s
    /// model invalidates the transcript ~20× a second instead of ~100×.
    public static let defaultInterval: Duration = .milliseconds(50)

    private let interval: Duration
    private var lastFlush: ContinuousClock.Instant?

    public init(interval: Duration = StreamFlushGate.defaultInterval) {
        self.interval = interval
    }

    /// True when a flush is due at `now` (records the instant); false to coalesce
    /// this token into the next flush. `now` is injected so cadence is testable.
    public mutating func shouldFlush(at now: ContinuousClock.Instant) -> Bool {
        if let last = lastFlush, now - last < interval { return false }
        lastFlush = now
        return true
    }
}
