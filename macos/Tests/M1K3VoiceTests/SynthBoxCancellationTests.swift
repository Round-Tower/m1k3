//
//  SynthBoxCancellationTests.swift
//  M1K3VoiceTests
//
//  Pins the exactly-once resume contract of EffectfulSpeechProvider's SynthBox —
//  the offline-render continuation backstop (audit finding 9). Before this,
//  SynthBox resumed ONLY on `write`'s zero-frame sentinel, so a stop/barge-in
//  mid-render (`stopSpeaking(at:.immediate)`) never delivered it and the
//  continuation leaked — every caller awaiting `speak` hung forever (the MCP
//  speak HTTP call, the voice loop). Also pins the owner-identity guard: the
//  delegate is a single shared property, so a stale event for a superseded
//  utterance must NOT resume a successor box. These are pure delegate-callback
//  tests: no audio device, so they run on CI (unlike the streaming smoke).
//

import AVFoundation
@testable import M1K3Voice
import Testing

@MainActor
struct SynthBoxCancellationTests {
    private func zeroFrameBuffer() -> AVAudioPCMBuffer {
        let format = AVAudioFormat(
            commonFormat: .pcmFormatFloat32, sampleRate: 22050, channels: 1, interleaved: false
        )!
        let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: 1)!
        buffer.frameLength = 0 // the render-complete sentinel `ingest` recognises
        return buffer
    }

    @Test("didCancel resumes exactly once with CancellationError (the leaked-continuation fix)")
    func didCancelResumesWithCancellation() {
        let utterance = AVSpeechUtterance(string: "stop me")
        let box = SynthBox(owner: utterance)
        var results: [Result<([Float], Double, [WordOnset]), Error>] = []
        box.onDone = { results.append($0) }

        box.speechSynthesizer(AVSpeechSynthesizer(), didCancel: utterance)

        #expect(results.count == 1)
        guard case let .failure(error)? = results.first else {
            Issue.record("expected a failure result")
            return
        }
        #expect(error is CancellationError)
    }

    @Test("didFinish is a standalone success backstop when the sentinel never arrives")
    func didFinishBacktopsSuccess() {
        let utterance = AVSpeechUtterance(string: "hi")
        let box = SynthBox(owner: utterance)
        var results: [Result<([Float], Double, [WordOnset]), Error>] = []
        box.onDone = { results.append($0) }

        box.speechSynthesizer(AVSpeechSynthesizer(), didFinish: utterance)

        #expect(results.count == 1)
        guard case .success? = results.first else {
            Issue.record("expected a success result")
            return
        }
    }

    @Test("exactly-once: the completion sentinel wins, later didFinish/didCancel are no-ops")
    func sentinelWinsAndLaterCallbacksAreNoOps() {
        let utterance = AVSpeechUtterance(string: "hi")
        let box = SynthBox(owner: utterance)
        var results: [Result<([Float], Double, [WordOnset]), Error>] = []
        box.onDone = { results.append($0) }

        box.ingest(zeroFrameBuffer()) // the normal render-complete path → success
        box.speechSynthesizer(AVSpeechSynthesizer(), didFinish: utterance)
        box.speechSynthesizer(AVSpeechSynthesizer(), didCancel: utterance)

        #expect(results.count == 1)
        guard case .success? = results.first else {
            Issue.record("expected the sentinel's success to win")
            return
        }
    }

    @Test("exactly-once: a cancel wins over a LATE sentinel (barge-in then trailing buffer)")
    func cancelWinsOverLateSentinel() {
        let utterance = AVSpeechUtterance(string: "hi")
        let box = SynthBox(owner: utterance)
        var results: [Result<([Float], Double, [WordOnset]), Error>] = []
        box.onDone = { results.append($0) }

        box.speechSynthesizer(AVSpeechSynthesizer(), didCancel: utterance)
        box.ingest(zeroFrameBuffer()) // a trailing sentinel after cancel must not double-resume

        #expect(results.count == 1)
        guard case .failure? = results.first else {
            Issue.record("expected the cancellation to win")
            return
        }
    }

    @Test("a delegate event for a FOREIGN utterance is ignored (the cross-utterance barge-in guard)")
    func ignoresForeignUtteranceEvents() {
        let owned = AVSpeechUtterance(string: "mine")
        let foreign = AVSpeechUtterance(string: "a superseded utterance")
        let box = SynthBox(owner: owned)
        var results: [Result<([Float], Double, [WordOnset]), Error>] = []
        box.onDone = { results.append($0) }

        // A straggling cancel/finish from the PREVIOUS utterance, delivered after
        // this box became the delegate — must not touch this box's continuation.
        box.speechSynthesizer(AVSpeechSynthesizer(), didCancel: foreign)
        box.speechSynthesizer(AVSpeechSynthesizer(), didFinish: foreign)
        #expect(results.isEmpty)

        // This box's OWN cancel still resumes it — exactly once.
        box.speechSynthesizer(AVSpeechSynthesizer(), didCancel: owned)
        #expect(results.count == 1)
        guard case .failure? = results.first else {
            Issue.record("expected the owner's cancellation to resume")
            return
        }
    }
}
