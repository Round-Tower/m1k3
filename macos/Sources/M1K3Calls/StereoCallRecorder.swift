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
//  Review: claude-opus-4-8, 2026-06-09 (PR #10) — stop() now atomically claims the
//  stop inside the lock before async teardown, so two concurrent stops can't both
//  reach removeTap(onBus:) and trap; writeFile guards rather than force-unwraps.
//  Context: lowest confidence in the session — entirely verify-by-launch OS
//  capture; first ⌘R on a real call is where this gets shaken out.

@preconcurrency import AVFoundation
import Foundation
import os
import ScreenCaptureKit

/// Records a stereo call (mic + system audio). `@unchecked Sendable`: mutable
/// capture state is guarded by `lock`; the SCStream/engine callbacks append under it.
public final class StereoCallRecorder: NSObject, @unchecked Sendable {
    /// Common capture format both sources are normalised to before interleaving.
    private static let sampleRate: Double = 48000
    /// Diagnostic trail for QA — `log stream --predicate 'subsystem == "dev.murphysig.M1K3"'`.
    /// Metadata only (formats, counts, sizes, errors); never the audio itself.
    private static let log = Logger(subsystem: "dev.murphysig.M1K3", category: "calls")

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
        Self.log.notice("start requested")
        _ = await stop() // reset any prior session

        try await startMic()
        lock.withLock { recording = true }

        do {
            try await startSystemAudio()
            Self.log.notice("capturing stereo (mic + system audio)")
            return true
        } catch {
            // No screen-recording permission / unsupported → mono mic only.
            Self.log.notice("system audio unavailable → mono mic only: \(error, privacy: .public)")
            return false
        }
    }

    /// Stop both captures and write the interleaved stereo (or mono) file.
    public func stop() async -> URL? {
        // Atomically claim the stop before any async teardown. Two concurrent stops
        // (double-tap, or a consent-timeout racing a user tap) must not both reach
        // removeTap(onBus:) — removing a tap from a bus with none installed traps.
        let claimed = lock.withLock { () -> Bool in
            guard recording else { return false }
            recording = false
            return true
        }
        guard claimed else { return nil }

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
            micConverter = nil
            return result
        }
        Self.log.notice("stopped: near=\(near.count, privacy: .public) far=\(far.count, privacy: .public) samples")
        guard !near.isEmpty || !far.isEmpty else {
            Self.log.error("nothing captured — no file written (mic delivered 0 samples)")
            return nil
        }
        let url = try? Self.writeFile(near: near, far: far, sampleRate: Self.sampleRate)
        if let url {
            let bytes = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? Int) ?? nil
            Self.log.notice("wrote \(bytes ?? -1, privacy: .public) bytes → \(url.lastPathComponent, privacy: .public)")
        } else {
            Self.log.error("writeFile failed despite captured samples")
        }
        return url
    }

    // MARK: - Mic (near-end, left)

    private func startMic() async throws {
        // Mic permission must be SETTLED before we touch the inputNode. On the first
        // launch under a new signing identity the TCC grant isn't established yet, and
        // `outputFormat(forBus:)` in that state returns a degenerate 0-Hz format.
        // Handing that to `installTap` invalidates the HAL AudioUnit
        // (kAudioUnitErr_InvalidElement, -10877) and the recording captures nothing —
        // so request first, then read the format.
        guard await Self.micAuthorized() else {
            Self.log.error("mic permission denied — recording aborted")
            throw RecorderError.micPermissionDenied
        }

        let input = engine.inputNode
        let inFormat = input.outputFormat(forBus: 0)
        Self.log.notice("mic input format \(inFormat.sampleRate, privacy: .public)Hz ch=\(inFormat.channelCount, privacy: .public)")
        // Even with permission granted, refuse a degenerate format rather than crash
        // the HAL — installTap with a 0-Hz clock is the direct trigger for -10877.
        guard inFormat.sampleRate > 0 else {
            Self.log.error("degenerate 0-Hz input format — refusing to install tap (would throw -10877)")
            throw RecorderError.formatUnavailable
        }
        guard let target = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: Self.sampleRate, channels: 1, interleaved: false
        ) else { throw RecorderError.formatUnavailable }
        // AVAudioConverter is nil for an invalid format pair; a silently-nil converter
        // would drop every buffer and yield an empty recording.
        guard let converter = AVAudioConverter(from: inFormat, to: target) else {
            throw RecorderError.formatUnavailable
        }
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

    /// Microphone authorization (macOS TCC). Requests on first use; the prompt uses
    /// NSMicrophoneUsageDescription. Returns false if denied/restricted so the caller
    /// surfaces a readable error instead of recording silence.
    private static func micAuthorized() async -> Bool {
        switch AVCaptureDevice.authorizationStatus(for: .audio) {
        case .authorized: return true
        case .notDetermined: return await AVCaptureDevice.requestAccess(for: .audio)
        default: return false
        }
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
        // Audio is all we consume, but SCStream always runs the video pipeline for a
        // display filter — keep it tiny (2×2) and slow (1 fps) so it costs almost
        // nothing.
        config.width = 2
        config.height = 2
        config.minimumFrameInterval = CMTime(value: 1, timescale: 1)

        let stream = SCStream(filter: filter, configuration: config, delegate: nil)
        try stream.addStreamOutput(self, type: .audio, sampleHandlerQueue: captureQueue)
        // Register a sink for the video frames too — the delegate drops them (guards
        // type == .audio), but WITHOUT a registered .screen output SCStream logs
        // "stream output NOT found. Dropping frame" for every frame it can't deliver.
        try stream.addStreamOutput(self, type: .screen, sampleHandlerQueue: captureQueue)
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
            // Float32 format guarantees floatChannelData; guard anyway, matching the
            // defensive idiom used elsewhere in this file rather than force-unwrapping.
            guard let channel = buffer.floatChannelData, let base = src.baseAddress else { return }
            channel[0].update(from: base, count: interleaved.count)
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

    public enum RecorderError: Error, Sendable, LocalizedError {
        case formatUnavailable
        case noDisplay
        case micPermissionDenied

        public var errorDescription: String? {
            switch self {
            case .formatUnavailable:
                String(localized: "The microphone isn’t ready yet — try again in a moment.")
            case .noDisplay:
                String(localized: "No display is available to capture system audio.")
            case .micPermissionDenied:
                String(localized: "Microphone access is off. Enable it in System Settings → Privacy & Security → Microphone.")
            }
        }
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
