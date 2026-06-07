//
//  StereoCallRecorder.swift
//  M1K3Calls
//
//  Capture a call as TWO channels — near-end mic (left) + far-end system audio
//  (right, via ScreenCaptureKit) — muxed into one stereo file. The
//  StereoChannelDiarizer then reads channel == speaker, so a live recording is
//  speaker-attributed with no ML. If system-audio capture is unavailable (no
//  screen-recording permission, unsupported), it degrades GRACEFULLY to a mono
//  mic recording (diarizer returns no turns → unattributed transcript, never a
//  failure).
//
//  Verify-by-launch: SCStream + the mic engine + the screen-recording TCC prompt
//  need a real device and a live call — none of it runs headless. The one part
//  that's deterministic, the channel interleave, is StereoInterleaver (unit-tested).
//  This adapter is the OS glue, kept defensive (mono fallback) so a capture fault
//  can't lose a recording.
//
//  Async by necessity: SCStream start/stopCapture are async, so this carries its
//  own async start()/stop() rather than the sync AudioRecorder seam.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-07, Confidence 0.55, Prior: Unknown
//  Context: lowest confidence in the session — entirely verify-by-launch OS
//  capture; first ⌘R on a real call is where this gets shaken out.

@preconcurrency import AVFoundation
import Foundation
import ScreenCaptureKit

/// Records a stereo call (mic + system audio). `@unchecked Sendable`: mutable
/// capture state is guarded by `lock`; the SCStream/engine callbacks append under it.
public final class StereoCallRecorder: NSObject, @unchecked Sendable {
    /// Common capture format both sources are normalised to before interleaving.
    private static let sampleRate: Double = 48000

    private let lock = NSLock()
    private let engine = AVAudioEngine()
    private var stream: SCStream?
    private var nearSamples: [Float] = []
    private var farSamples: [Float] = []
    private var recording = false
    private var micConverter: AVAudioConverter?
    private let captureQueue = DispatchQueue(label: "dev.murphysig.m1k3.stereocapture")

    public var isRecording: Bool {
        lock.withLock { recording }
    }

    /// Start both captures. The mic is required; system audio is best-effort —
    /// if it can't start, we still record mono (so the call is never lost).
    /// - Returns: whether the far-end (system audio) channel is being captured.
    @discardableResult
    public func start() async throws -> Bool {
        _ = await stop() // reset any prior session

        try startMic()
        lock.withLock { recording = true }

        do {
            try await startSystemAudio()
            return true
        } catch {
            // No screen-recording permission / unsupported → mono mic only.
            return false
        }
    }

    /// Stop both captures and write the interleaved stereo (or mono) file.
    public func stop() async -> URL? {
        let wasRecording = lock.withLock { recording }
        guard wasRecording else { return nil }

        if let stream {
            try? await stream.stopCapture()
        }
        engine.stop()
        engine.inputNode.removeTap(onBus: 0)

        let (near, far) = lock.withLock {
            let result = (nearSamples, farSamples)
            stream = nil
            nearSamples = []
            farSamples = []
            recording = false
            micConverter = nil
            return result
        }
        guard !near.isEmpty || !far.isEmpty else { return nil }
        return try? Self.writeFile(near: near, far: far, sampleRate: Self.sampleRate)
    }

    // MARK: - Mic (near-end, left)

    private func startMic() throws {
        let input = engine.inputNode
        let inFormat = input.outputFormat(forBus: 0)
        guard let target = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: Self.sampleRate, channels: 1, interleaved: false
        ) else { throw RecorderError.formatUnavailable }
        let converter = AVAudioConverter(from: inFormat, to: target)
        lock.withLock { micConverter = converter }

        input.installTap(onBus: 0, bufferSize: 4096, format: inFormat) { [weak self] buffer, _ in
            guard let self else { return }
            let samples = Self.convert(buffer, to: target, using: self.converter())
            guard !samples.isEmpty else { return }
            self.lock.withLock { self.nearSamples.append(contentsOf: samples) }
        }
        engine.prepare()
        try engine.start()
    }

    private func converter() -> AVAudioConverter? {
        lock.withLock { micConverter }
    }

    // MARK: - System audio (far-end, right)

    private func startSystemAudio() async throws {
        let content = try await SCShareableContent.current
        guard let display = content.displays.first else { throw RecorderError.noDisplay }

        let filter = SCContentFilter(display: display, excludingApplications: [], exceptingWindows: [])
        let config = SCStreamConfiguration()
        config.capturesAudio = true
        config.excludesCurrentProcessAudio = true // don't record our own TTS/output
        config.sampleRate = Int(Self.sampleRate)
        config.channelCount = 1
        // Minimise the (unused) video path — audio is all we consume.
        config.width = 2
        config.height = 2

        let stream = SCStream(filter: filter, configuration: config, delegate: nil)
        try stream.addStreamOutput(self, type: .audio, sampleHandlerQueue: captureQueue)
        lock.withLock { self.stream = stream }
        try await stream.startCapture()
    }

    // MARK: - File output

    /// Interleave the two channels and write a Float32 stereo `.caf`. With one
    /// channel empty this is effectively a (silence-paired) mono recording.
    private static func writeFile(near: [Float], far: [Float], sampleRate: Double) throws -> URL {
        let interleaved = StereoInterleaver.interleave(left: near, right: far)
        guard !interleaved.isEmpty,
              let format = AVAudioFormat(
                  commonFormat: .pcmFormatFloat32,
                  sampleRate: sampleRate, channels: 2, interleaved: true
              ),
              let buffer = AVAudioPCMBuffer(
                  pcmFormat: format,
                  frameCapacity: AVAudioFrameCount(interleaved.count / 2)
              )
        else { throw RecorderError.formatUnavailable }

        buffer.frameLength = buffer.frameCapacity
        interleaved.withUnsafeBufferPointer { src in
            buffer.floatChannelData![0].update(from: src.baseAddress!, count: interleaved.count)
        }

        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("m1k3-call-\(UUID().uuidString).caf")
        let file = try AVAudioFile(forWriting: url, settings: format.settings)
        try file.write(from: buffer)
        return url
    }

    /// Convert a captured PCM buffer to mono Float32 at the common rate → samples.
    private static func convert(
        _ buffer: AVAudioPCMBuffer,
        to format: AVAudioFormat,
        using converter: AVAudioConverter?
    ) -> [Float] {
        guard let converter else { return [] }
        let ratio = format.sampleRate / buffer.format.sampleRate
        let capacity = AVAudioFrameCount(Double(buffer.frameLength) * ratio) + 1
        guard let out = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: capacity) else { return [] }

        // Hand the converter the buffer exactly once. A reference box dodges the
        // Swift 6 "captured var in concurrent code" warning (the block runs
        // synchronously inside convert(), but its type is treated as Sendable).
        let pending = InputBox(buffer)
        var error: NSError?
        converter.convert(to: out, error: &error) { _, status in
            guard let next = pending.take() else { status.pointee = .noDataNow; return nil }
            status.pointee = .haveData
            return next
        }
        guard error == nil, let channel = out.floatChannelData?[0] else { return [] }
        return Array(UnsafeBufferPointer(start: channel, count: Int(out.frameLength)))
    }

    public enum RecorderError: Error, Sendable {
        case formatUnavailable
        case noDisplay
    }

    /// One-shot holder for the converter's input buffer (Sendable so the input
    /// block can capture it without a strict-concurrency warning).
    private final class InputBox: @unchecked Sendable {
        private var buffer: AVAudioPCMBuffer?
        init(_ buffer: AVAudioPCMBuffer) {
            self.buffer = buffer
        }

        func take() -> AVAudioPCMBuffer? {
            defer { buffer = nil }
            return buffer
        }
    }
}

// MARK: - SCStreamOutput

extension StereoCallRecorder: SCStreamOutput {
    public func stream(_: SCStream, didOutputSampleBuffer sampleBuffer: CMSampleBuffer, of type: SCStreamOutputType) {
        guard type == .audio, sampleBuffer.isValid else { return }
        let samples = Self.samples(from: sampleBuffer)
        guard !samples.isEmpty else { return }
        lock.withLock { farSamples.append(contentsOf: samples) }
    }

    /// Extract mono Float32 samples from an SCStream audio CMSampleBuffer.
    private static func samples(from sampleBuffer: CMSampleBuffer) -> [Float] {
        var samples: [Float] = []
        try? sampleBuffer.withAudioBufferList { audioBufferList, _ in
            for buffer in audioBufferList {
                guard let data = buffer.mData else { continue }
                let count = Int(buffer.mDataByteSize) / MemoryLayout<Float>.size
                let pointer = data.assumingMemoryBound(to: Float.self)
                samples.append(contentsOf: UnsafeBufferPointer(start: pointer, count: count))
                break // mono → first buffer only
            }
        }
        return samples
    }
}
