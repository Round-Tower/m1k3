//
//  DownloadLogThrottleTests.swift
//  M1K3MLXTests
//
//  Model downloads are multi-GB and the progress handler fires constantly —
//  the throttle decides which updates become log lines: the first, the last,
//  every 5% of progress, or every 10s of wall clock, whichever comes first.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.9, Prior: Unknown
//

import Foundation
@testable import M1K3MLX
import Testing

struct DownloadLogThrottleTests {
    private let start = ContinuousClock.now

    @Test("the first update always emits")
    func firstEmits() {
        var throttle = DownloadLogThrottle()
        let first = throttle.shouldEmit(fraction: 0.0, now: start)
        #expect(first)
    }

    @Test("small, quick increments stay quiet")
    func quietBetweenSteps() {
        var throttle = DownloadLogThrottle()
        _ = throttle.shouldEmit(fraction: 0.10, now: start)
        let second = throttle.shouldEmit(fraction: 0.12, now: start.advanced(by: .seconds(1)))
        let third = throttle.shouldEmit(fraction: 0.14, now: start.advanced(by: .seconds(2)))
        #expect(!second)
        #expect(!third)
    }

    @Test("a 5% jump emits")
    func fractionStepEmits() {
        var throttle = DownloadLogThrottle()
        _ = throttle.shouldEmit(fraction: 0.10, now: start)
        let stepped = throttle.shouldEmit(fraction: 0.151, now: start.advanced(by: .seconds(1)))
        #expect(stepped)
    }

    @Test("ten quiet seconds emit even without progress")
    func timeStepEmits() {
        var throttle = DownloadLogThrottle()
        _ = throttle.shouldEmit(fraction: 0.10, now: start)
        let stale = throttle.shouldEmit(fraction: 0.101, now: start.advanced(by: .seconds(11)))
        #expect(stale)
    }

    @Test("completion always emits")
    func completionEmits() {
        var throttle = DownloadLogThrottle()
        _ = throttle.shouldEmit(fraction: 0.99, now: start)
        let finished = throttle.shouldEmit(fraction: 1.0, now: start.advanced(by: .seconds(1)))
        #expect(finished)
    }
}
