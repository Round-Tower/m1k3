import AVFoundation
import Foundation
@testable import M1K3Voice
import Testing

/// Integration smoke for the streaming playback path — the real
/// AVSpeechSynthesizer offline render through the real AVAudioEngine, word
/// clock and all. Audio plays briefly on the local machine; skipped on CI
/// (runners may have no output device). This is the closest `swift test` gets
/// to ⌘R for the speech machinery, and it pins the lifecycle regression class
/// from PR #10: exactly one started/ended pair per utterance.
@MainActor
struct EffectfulStreamingIntegrationTests {
    private var onCI: Bool {
        ProcessInfo.processInfo.environment["CI"] != nil
    }

    private final class Recorder: @unchecked Sendable {
        private let lock = NSLock()
        private var _starts = 0
        private var _ends = 0
        private var _timelines: [SpokenWordTimeline] = []
        private var _words: [Range<Int>] = []

        var starts: Int {
            lock.withLock { _starts }
        }

        var ends: Int {
            lock.withLock { _ends }
        }

        var timelines: [SpokenWordTimeline] {
            lock.withLock { _timelines }
        }

        var words: [Range<Int>] {
            lock.withLock { _words }
        }

        func wire(_ provider: EffectfulSpeechProvider) {
            provider.onSpeakingStarted = { [self] in lock.withLock { _starts += 1 } }
            provider.onSpeakingEnded = { [self] in lock.withLock { _ends += 1 } }
            provider.onTimelineReady = { [self] timeline in lock.withLock { _timelines.append(timeline) } }
            provider.onWordSpoken = { [self] range in lock.withLock { _words.append(range) } }
        }
    }

    @Test("a spoken utterance fires one lifecycle pair, a timeline, and advancing words")
    func appleStreamingPath() async throws {
        guard !onCI else { return }
        let provider = EffectfulSpeechProvider()
        let recorder = Recorder()
        recorder.wire(provider)

        let text = "The rain in Spain falls mainly on the plain."
        await provider.speak(SpeechUtterance(text: text))

        #expect(recorder.starts == 1)
        #expect(recorder.ends == 1)
        // The offline render correlates delegate onsets — a full timeline exists.
        let timeline = try #require(recorder.timelines.last)
        #expect(timeline.text == text)
        #expect(timeline.words.count >= 8)
        #expect(timeline.totalDuration > 1)
        // The word clock fired across the utterance with advancing ranges.
        #expect(recorder.words.count >= 4)
        #expect(recorder.words == recorder.words.sorted { $0.lowerBound < $1.lowerBound })
        let isSpeaking = await provider.isSpeaking()
        #expect(!isSpeaking) // a drained player must not read as still speaking

        // A SECOND utterance must get a fresh clock (regression: the stale
        // player position once anchored utterance 2's words seconds late) and
        // must not fire a spurious ended event on entry.
        let wordsBefore = recorder.words.count
        await provider.speak(SpeechUtterance(text: "Second time around."))
        #expect(recorder.starts == 2)
        #expect(recorder.ends == 2)
        #expect(recorder.words.count > wordsBefore)
        if recorder.words.count > wordsBefore {
            #expect(recorder.words[wordsBefore].lowerBound == 0) // fresh utterance, first word
        }
    }

    @Test("stop() mid-utterance ends exactly once and returns promptly")
    func stopMidUtterance() async throws {
        guard !onCI else { return }
        let provider = EffectfulSpeechProvider()
        let recorder = Recorder()
        recorder.wire(provider)

        let speakTask = Task {
            await provider.speak(SpeechUtterance(
                text: "This is a deliberately long sentence that will be interrupted before it can possibly finish speaking."
            ))
        }
        // Let synthesis + playback begin, then cut it off.
        try await Task.sleep(for: .milliseconds(900))
        await provider.stop()
        await speakTask.value

        #expect(recorder.ends == 1) // never zero (hang) and never two (PR #10 regression)
        let isSpeaking = await provider.isSpeaking()
        #expect(!isSpeaking)
    }
}
