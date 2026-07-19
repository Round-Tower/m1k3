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

    /// Reference box so the render closure (non-Sendable, non-escaping) can record
    /// whether it ran without a cross-actor mutable capture.
    private final class RanFlag: @unchecked Sendable { var value = false }

    @Test("a superseded entry skips its render closure — so speak(stream:) won't spurious-fallback on barge-in")
    func supersededEntrySkipsRender() async {
        // The provider-level guard the streaming fix relies on: when a newer speak()
        // supersedes a queued one, runRender must bail WITHOUT running the render —
        // that's what lets speak(stream:)'s `true`-seeded outcome survive (so the
        // abandoned utterance doesn't report "nothing spoken" and trigger a stale
        // Apple-voice fallback that stop()s the newer render — the #52 barge-in bug).
        let provider = EffectfulSpeechProvider()

        let staleGeneration = await provider.claimEntry() // generation N
        _ = await provider.claimEntry() // generation N+1 supersedes it

        let ranStale = RanFlag()
        await provider.runRender(staleGeneration) { ranStale.value = true }
        #expect(ranStale.value == false) // superseded → closure never runs

        // The current (latest) generation still renders normally.
        let currentGeneration = await provider.claimEntry()
        let ranCurrent = RanFlag()
        await provider.runRender(currentGeneration) { ranCurrent.value = true }
        #expect(ranCurrent.value == true)
    }
}
