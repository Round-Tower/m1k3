//
//  EffectfulSpeechProvider.swift
//  M1K3Voice
//
//  Gives M1K3 its own voice TODAY by running Apple's synthesized speech through the
//  pure VoiceEffectChain before playback: AVSpeechSynthesizer.write → [Float] PCM →
//  effect chain → AVAudioEngine. So "M1K3 Voice" sounds distinct from the plain
//  Built-in tier even before the Kokoro neural model lands — and when it does, its
//  PCM flows through the exact same chain + playback path (this provider becomes
//  Kokoro's downstream renderer).
//
//  The DSP is unit-tested in VoiceEffectTests; THIS file is the thin AVFoundation
//  adapter — verify-by-launch. Any failure in the effect path falls back to plain
//  Apple speech, so M1K3 is never left silent.
//
//  Signed: Kev + claude-sonnet-4-6, 2026-06-08, Confidence 0.55, Prior: Unknown

import AVFoundation
import Foundation

public final class EffectfulSpeechProvider: NSObject, SpeechProviderWithLifecycle, @unchecked Sendable {
    public let name = "m1k3-effect-voice"

    private let chain: VoiceEffectChain
    private let synthesizer = AVSpeechSynthesizer()
    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    /// Used only when the effect/render path fails — M1K3 still speaks, just dry.
    private let plainFallback: AVSpeechProvider

    public var onSpeakingStarted: (@Sendable () -> Void)?
    public var onSpeakingEnded: (@Sendable () -> Void)?

    @MainActor private var engineConfigured = false
    @MainActor private var configuredSampleRate: Double = 0
    /// The in-flight playback wait, so `stop()` can resume it (the .dataPlayedBack
    /// completion is NOT delivered after player.stop(), which would otherwise hang).
    @MainActor private var playbackContinuation: CheckedContinuation<Void, Never>?

    public init(chain: VoiceEffectChain = .m1k3Character, fallback: AVSpeechProvider = AVSpeechProvider()) {
        self.chain = chain
        plainFallback = fallback
        super.init()
    }

    public var isAvailable: Bool {
        true
    }

    public func speak(_ utterance: SpeechUtterance) async {
        do {
            let (samples, sampleRate) = try await synthesizeToFloats(utterance)
            guard !samples.isEmpty, sampleRate > 0 else {
                await plainSpeak(utterance)
                return
            }
            let processed = chain.process(samples, sampleRate: sampleRate)
            try await play(processed, sampleRate: sampleRate)
        } catch {
            await plainSpeak(utterance)
        }
    }

    public func stop() async {
        let wasActive = await isSpeaking()
        await MainActor.run {
            player.stop()
            _ = synthesizer.stopSpeaking(at: .immediate)
            // Resume any playback wait — .dataPlayedBack won't fire after stop().
            finishPlayback()
        }
        await plainFallback.stop()
        if wasActive { await MainActor.run { onSpeakingEnded?() } }
    }

    public func isSpeaking() async -> Bool {
        await MainActor.run { player.isPlaying || synthesizer.isSpeaking }
    }

    // MARK: - Synthesis → PCM

    // @MainActor: AVSpeechSynthesizer.write must be invoked on the main thread
    // (same contract as .speak), and building `spoken` here keeps it main-isolated.
    /// Mono PCM samples + their sample rate.
    private typealias PCM = ([Float], Double)

    @MainActor
    private func synthesizeToFloats(_ utterance: SpeechUtterance) async throws -> PCM {
        let spoken = AVSpeechUtterance(string: utterance.text)
        spoken.rate = utterance.rate
        spoken.pitchMultiplier = utterance.pitch
        if let id = utterance.voiceIdentifier,
           let voice = AVSpeechSynthesisVoice(identifier: id)
        {
            spoken.voice = voice
        }

        let box = SynthBox()
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<PCM, Error>) in
            box.onDone = { continuation.resume(with: $0) }
            synthesizer.write(spoken) { buffer in
                box.ingest(buffer)
            }
        }
    }

    // MARK: - Playback

    @MainActor
    private func play(_ samples: [Float], sampleRate: Double) async throws {
        guard let format = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: sampleRate,
            channels: 1,
            interleaved: false
        ),
            let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(samples.count))
        else {
            throw EffectfulSpeechError.bufferAllocationFailed
        }

        buffer.frameLength = AVAudioFrameCount(samples.count)
        if let channel = buffer.floatChannelData {
            samples.withUnsafeBufferPointer { source in
                if let base = source.baseAddress {
                    channel[0].update(from: base, count: samples.count)
                }
            }
        }

        try configureEngineIfNeeded(format: format)

        onSpeakingStarted?()
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            playbackContinuation = continuation
            player.scheduleBuffer(
                buffer, at: nil, options: [], completionCallbackType: .dataPlayedBack
            ) { [weak self] _ in
                Task { @MainActor in self?.finishPlayback() }
            }
            player.play()
        }
        onSpeakingEnded?()
    }

    /// Resume the playback wait exactly once — from either the .dataPlayedBack
    /// completion (normal end) or stop() (cancelled).
    @MainActor
    private func finishPlayback() {
        playbackContinuation?.resume()
        playbackContinuation = nil
    }

    @MainActor
    private func configureEngineIfNeeded(format: AVAudioFormat) throws {
        // Reconnect when the sample rate changes (e.g. a different system voice) —
        // scheduling a buffer whose format mismatches the connection asserts.
        if engineConfigured, configuredSampleRate == format.sampleRate { return }
        if engineConfigured {
            player.stop()
            engine.disconnectNodeOutput(player)
        } else {
            engine.attach(player)
        }
        engine.connect(player, to: engine.mainMixerNode, format: format)
        if !engine.isRunning { try engine.start() }
        engineConfigured = true
        configuredSampleRate = format.sampleRate
    }

    // MARK: - Fallback

    private func plainSpeak(_ utterance: SpeechUtterance) async {
        await MainActor.run { onSpeakingStarted?() }
        await plainFallback.speak(utterance)
        await MainActor.run { onSpeakingEnded?() }
    }
}

enum EffectfulSpeechError: Error {
    case bufferAllocationFailed
}

/// Accumulates the synthesizer's PCM chunks (float / int16 / int32) into one mono
/// Float buffer and resumes exactly once when the final (empty) buffer arrives.
private final class SynthBox: @unchecked Sendable {
    private let lock = NSLock()
    private var samples: [Float] = []
    private var sampleRate: Double = 0
    private var done = false
    var onDone: ((Result<([Float], Double), Error>) -> Void)?

    func ingest(_ buffer: AVAudioBuffer) {
        guard let pcm = buffer as? AVAudioPCMBuffer else { return }
        let frames = Int(pcm.frameLength)
        if frames == 0 {
            finish()
            return
        }
        var chunk = [Float](repeating: 0, count: frames)
        if let floatData = pcm.floatChannelData {
            for index in 0 ..< frames {
                chunk[index] = floatData[0][index]
            }
        } else if let int16Data = pcm.int16ChannelData {
            for index in 0 ..< frames {
                chunk[index] = Float(int16Data[0][index]) / 32768
            }
        } else if let int32Data = pcm.int32ChannelData {
            for index in 0 ..< frames {
                chunk[index] = Float(int32Data[0][index]) / Float(Int32.max)
            }
        }
        lock.withLock {
            sampleRate = pcm.format.sampleRate
            samples.append(contentsOf: chunk)
        }
    }

    private func finish() {
        let payload: ([Float], Double)? = lock.withLock {
            guard !done else { return nil }
            done = true
            return (samples, sampleRate)
        }
        guard let payload else { return }
        onDone?(.success(payload))
    }
}
