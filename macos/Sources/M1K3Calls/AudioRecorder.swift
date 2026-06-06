//
//  AudioRecorder.swift
//  M1K3Calls
//
//  The mic-capture seam: start recording, stop and get a file. A protocol so the
//  recording lifecycle is testable with a fake, while the real AVAudioEngine
//  capture lives in one thin adapter (verify-by-launch — needs a mic + the
//  audio-input entitlement). The recorded file feeds the same CallIntelligencePipeline
//  the transcript-import path already exercises.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.7, Prior: Unknown

import AVFoundation
import Foundation

public protocol AudioRecorder: Sendable {
    var isRecording: Bool { get }
    /// Begin capturing microphone audio to a fresh temp file.
    func start() throws
    /// Stop capturing; returns the recorded file URL (nil if nothing was recorded).
    func stop() -> URL?
}

/// AVAudioEngine mic capture to a `.caf` file. `@unchecked Sendable`: the file +
/// state are lock-guarded; the tap closure only appends under the lock.
public final class MicAudioRecorder: AudioRecorder, @unchecked Sendable {
    private let engine = AVAudioEngine()
    private let lock = NSLock()
    private var file: AVAudioFile?
    private var url: URL?
    private var recording = false

    public init() {}

    public var isRecording: Bool {
        lock.withLock { recording }
    }

    public func start() throws {
        _ = stop() // reset any prior session

        let input = engine.inputNode
        let format = input.outputFormat(forBus: 0)
        let outURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("m1k3-call-\(UUID().uuidString).caf")
        let audioFile = try AVAudioFile(forWriting: outURL, settings: format.settings)
        lock.withLock {
            self.file = audioFile
            self.url = outURL
            self.recording = true
        }

        input.installTap(onBus: 0, bufferSize: 4096, format: format) { [weak self] buffer, _ in
            self?.lock.withLock { try? self?.file?.write(from: buffer) }
        }
        engine.prepare()
        try engine.start()
    }

    public func stop() -> URL? {
        // Stop + drain the tap OUTSIDE the lock (engine.stop blocks on in-flight tap
        // callbacks, which take the lock — holding it here would deadlock). Same
        // lesson as AppleSpeechTranscriber.
        engine.stop()
        engine.inputNode.removeTap(onBus: 0)
        return lock.withLock {
            let recorded = self.url
            self.file = nil
            self.url = nil
            self.recording = false
            return recorded
        }
    }
}
