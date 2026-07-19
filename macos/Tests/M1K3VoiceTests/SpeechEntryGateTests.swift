//
//  SpeechEntryGateTests.swift
//  M1K3VoiceTests
//
//  Pins the no-overlap contract of EffectfulSpeechProvider's SpeechEntryGate — the
//  serial gate that closed the #52 review residual. Before it, two speak() calls
//  that BOTH saw an idle provider started two overlapping renders on the single
//  AVSpeechSynthesizer, leaking the loser's SynthBox continuation. The gate makes
//  render bodies run strictly one-at-a-time; these are pure structural tests (no
//  audio device, no AVFoundation), so they run on CI. The live barge-in WIRING —
//  that a superseded render's box is still the delegate when its didCancel arrives
//  — stays verify-by-launch, per the provider's rule.
//
//  Signed: Kev + Claude, 2026-07-19, Confidence 0.85, Prior: Unknown
//

@testable import M1K3Voice
import Testing

struct SpeechEntryGateTests {
    /// Tracks how many gate bodies are executing at once and how many ran in total.
    private actor Concurrency {
        private(set) var current = 0
        private(set) var maxObserved = 0
        private(set) var completed = 0

        func enter() {
            current += 1
            maxObserved = max(maxObserved, current)
        }

        func leave() {
            current -= 1
            completed += 1
        }
    }

    @Test("run executes bodies strictly one-at-a-time even when entrants pile in concurrently")
    func neverOverlaps() async {
        let gate = SpeechEntryGate()
        let tracker = Concurrency()

        // Fan a batch of entrants at the gate at once. Each body yields several
        // times mid-flight so that, WITHOUT serialisation, two would interleave and
        // push `current` above 1 — exactly the overlapping-render race.
        await withTaskGroup(of: Void.self) { group in
            for _ in 0 ..< 12 {
                group.addTask {
                    await gate.run {
                        await tracker.enter()
                        for _ in 0 ..< 5 { await Task.yield() }
                        await tracker.leave()
                    }
                }
            }
        }

        #expect(await tracker.maxObserved == 1) // never two bodies at once
        #expect(await tracker.completed == 12) // and every entrant ran
    }

    @Test("the gate is reusable once drained — a later run still executes")
    func reusableAfterDrain() async {
        let gate = SpeechEntryGate()
        let tracker = Concurrency()

        await gate.run { await tracker.enter(); await tracker.leave() }
        // Second, independent use after the first fully completed (tail dropped).
        await gate.run { await tracker.enter(); await tracker.leave() }

        #expect(await tracker.completed == 2)
        #expect(await tracker.maxObserved == 1)
    }
}
