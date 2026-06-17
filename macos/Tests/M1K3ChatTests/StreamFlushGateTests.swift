//
//  StreamFlushGateTests.swift
//  M1K3ChatTests
//
//  The token-stream coalescer behind the visible-reasoning jank fix.
//

import Foundation
@testable import M1K3Chat
import Testing

struct StreamFlushGateTests {
    @Test("the first token always flushes — streaming starts immediately")
    func firstFlushes() {
        var gate = StreamFlushGate(interval: .milliseconds(50))
        #expect(gate.shouldFlush(at: .now) == true)
    }

    @Test("a token within the interval is coalesced, not flushed")
    func withinIntervalCoalesces() {
        var gate = StreamFlushGate(interval: .milliseconds(50))
        let t0 = ContinuousClock.now
        #expect(gate.shouldFlush(at: t0) == true)
        #expect(gate.shouldFlush(at: t0 + .milliseconds(10)) == false)
        #expect(gate.shouldFlush(at: t0 + .milliseconds(49)) == false)
    }

    @Test("once the interval elapses the next token flushes again")
    func afterIntervalFlushes() {
        var gate = StreamFlushGate(interval: .milliseconds(50))
        let t0 = ContinuousClock.now
        #expect(gate.shouldFlush(at: t0) == true)
        #expect(gate.shouldFlush(at: t0 + .milliseconds(50)) == true)
        #expect(gate.shouldFlush(at: t0 + .milliseconds(60)) == false) // resets to the last flush
        #expect(gate.shouldFlush(at: t0 + .milliseconds(100)) == true)
    }

    @Test("a burst of tokens collapses to one flush per interval")
    func burstCollapses() {
        var gate = StreamFlushGate(interval: .milliseconds(50))
        let t0 = ContinuousClock.now
        var flushes = 0
        // 200 tokens arriving 1ms apart over 200ms → ~5 flushes (one per 50ms),
        // not 200. That's the whole point: invalidation rate decoupled from token rate.
        for ms in 0 ..< 200 where gate.shouldFlush(at: t0 + .milliseconds(ms)) {
            flushes += 1
        }
        #expect(flushes >= 4 && flushes <= 6)
    }
}
