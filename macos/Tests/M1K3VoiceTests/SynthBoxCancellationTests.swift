//
//  SynthBoxCancellationTests.swift
//  M1K3VoiceTests
//
//  Pins the exactly-once resume contract of EffectfulSpeechProvider's SynthBox —
//  the offline-render continuation backstop (audit finding 9). Before this,
//  SynthBox resumed ONLY on `write`'s zero-frame sentinel, so a stop/barge-in
//  mid-render (`stopSpeaking(at:.immediate)`) never delivered it and the
//  continuation leaked — every caller awaiting `speak` hung forever (the MCP
//  speak HTTP call, the voice loop). These are pure delegate-callback tests: no
//  audio device, so they run on CI (unlike the streaming integration smoke).
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
        let box = SynthBox()
        var results: [Result<([Float], Double, [WordOnset]), Error>] = []
        box.onDone = { results.append($0) }

        box.speechSynthesizer(AVSpeechSynthesizer(), didCancel: AVSpeechUtterance(string: "stop me"))

        #expect(results.count == 1)
        guard case let .failure(error)? = results.first else {
            Issue.record("expected a failure result")
            return
        }
        #expect(error is CancellationError)
    }

    @Test("didFinish is a standalone success backstop when the sentinel never arrives")
    func didFinishBacktopsSuccess() {
        let box = SynthBox()
        var results: [Result<([Float], Double, [WordOnset]), Error>] = []
        box.onDone = { results.append($0) }

        box.speechSynthesizer(AVSpeechSynthesizer(), didFinish: AVSpeechUtterance(string: "hi"))

        #expect(results.count == 1)
        guard case .success? = results.first else {
            Issue.record("expected a success result")
            return
        }
    }

    @Test("exactly-once: the completion sentinel wins, later didFinish/didCancel are no-ops")
    func sentinelWinsAndLaterCallbacksAreNoOps() {
        let box = SynthBox()
        var results: [Result<([Float], Double, [WordOnset]), Error>] = []
        box.onDone = { results.append($0) }

        box.ingest(zeroFrameBuffer()) // the normal render-complete path → success
        box.speechSynthesizer(AVSpeechSynthesizer(), didFinish: AVSpeechUtterance(string: "hi"))
        box.speechSynthesizer(AVSpeechSynthesizer(), didCancel: AVSpeechUtterance(string: "hi"))

        #expect(results.count == 1)
        guard case .success? = results.first else {
            Issue.record("expected the sentinel's success to win")
            return
        }
    }

    @Test("exactly-once: a cancel wins over a LATE sentinel (barge-in then trailing buffer)")
    func cancelWinsOverLateSentinel() {
        let box = SynthBox()
        var results: [Result<([Float], Double, [WordOnset]), Error>] = []
        box.onDone = { results.append($0) }

        box.speechSynthesizer(AVSpeechSynthesizer(), didCancel: AVSpeechUtterance(string: "hi"))
        box.ingest(zeroFrameBuffer()) // a trailing sentinel after cancel must not double-resume

        #expect(results.count == 1)
        guard case .failure? = results.first else {
            Issue.record("expected the cancellation to win")
            return
        }
    }
}
