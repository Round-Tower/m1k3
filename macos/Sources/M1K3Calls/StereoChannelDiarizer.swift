//
//  StereoChannelDiarizer.swift
//  M1K3Calls
//
//  Diarization without a model. When a call is captured with each party on its
//  own channel (near-end mic vs far-end system audio), channel == speaker, so
//  attribution is exact — no CoreML, no DER, no new dependency. It's the
//  architecturally-correct PRIMARY diarizer for separated-channel recordings;
//  FluidAudio (mono ML, ~17% DER) is the fallback for single-source captures,
//  dropped in later behind this same DiarizationProvider seam.
//
//  Split as everywhere: reading the file's per-channel energy is a thin
//  verify-by-launch adapter; the segmentation — per-frame channel activity →
//  coalesced speaker turns, silence-gapped, blip-filtered — is pure + unit-tested.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-07, Confidence 0.8, Prior: Unknown

import AVFoundation
import Foundation

public struct StereoChannelDiarizer: DiarizationProvider {
    public let name = "Stereo channels"
    /// Pure + dependency-free, so it can always run; `diarize` simply returns no
    /// turns for a mono file (leaving the transcript unattributed, per the pipeline).
    public var isAvailable: Bool {
        true
    }

    private let frameDuration: TimeInterval
    private let threshold: Float
    private let minSegmentDuration: TimeInterval
    private let leftSpeaker: String
    private let rightSpeaker: String

    /// - Parameters:
    ///   - frameDuration: analysis window (s). 100ms balances resolution vs noise.
    ///   - threshold: RMS energy below which a channel is treated as silent.
    ///   - minSegmentDuration: turns shorter than this are dropped as blips.
    public init(
        frameDuration: TimeInterval = 0.1,
        threshold: Float = 0.01,
        minSegmentDuration: TimeInterval = 0.25,
        leftSpeaker: String = "Speaker 1",
        rightSpeaker: String = "Speaker 2"
    ) {
        self.frameDuration = frameDuration
        self.threshold = threshold
        self.minSegmentDuration = minSegmentDuration
        self.leftSpeaker = leftSpeaker
        self.rightSpeaker = rightSpeaker
    }

    public func diarize(fileURL: URL) async throws -> [SpeakerSegment] {
        let (left, right) = try Self.frameEnergies(of: fileURL, frameDuration: frameDuration)
        guard !left.isEmpty else { return [] } // mono / unreadable → unattributed
        return Self.segments(
            left: left, right: right,
            frameDuration: frameDuration, threshold: threshold,
            minSegmentDuration: minSegmentDuration,
            leftSpeaker: leftSpeaker, rightSpeaker: rightSpeaker
        )
    }

    // MARK: - Pure segmentation (unit-tested)

    /// Turn per-frame channel energies into coalesced speaker turns: each frame is
    /// attributed to the louder active channel (or silence), runs of the same
    /// speaker merge into one turn, and turns under `minSegmentDuration` are dropped.
    /// Confidence = mean per-frame channel separation (dominant / (left + right)).
    public static func segments(
        left: [Float], right: [Float],
        frameDuration: TimeInterval,
        threshold: Float,
        minSegmentDuration: TimeInterval = 0,
        leftSpeaker: String = "Speaker 1",
        rightSpeaker: String = "Speaker 2"
    ) -> [SpeakerSegment] {
        let count = min(left.count, right.count)
        var result: [SpeakerSegment] = []

        var runSpeaker: String?
        var runStart = 0
        var runConfidences: [Float] = []

        func flush(end: Int) {
            guard let speaker = runSpeaker, !runConfidences.isEmpty else { return }
            let start = TimeInterval(runStart) * frameDuration
            let stop = TimeInterval(end) * frameDuration
            guard stop - start >= minSegmentDuration else { return }
            let confidence = runConfidences.reduce(0, +) / Float(runConfidences.count)
            result.append(SpeakerSegment(
                speakerId: speaker,
                speakerLabel: speaker,
                startTime: start,
                endTime: stop,
                confidence: confidence
            ))
        }

        for frame in 0 ..< count {
            let leftLevel = left[frame], rightLevel = right[frame]
            let speaker: String?
            let confidence: Float
            if max(leftLevel, rightLevel) < threshold {
                speaker = nil // silence
                confidence = 0
            } else {
                speaker = leftLevel >= rightLevel ? leftSpeaker : rightSpeaker
                let total = leftLevel + rightLevel
                confidence = total > 0 ? max(leftLevel, rightLevel) / total : 1
            }

            if speaker != runSpeaker {
                flush(end: frame)
                runSpeaker = speaker
                runStart = frame
                runConfidences = []
            }
            if speaker != nil { runConfidences.append(confidence) }
        }
        flush(end: count)
        return result
    }

    // MARK: - File adapter (verify-by-launch)

    /// Mean per-frame RMS energy for the left and right channels. Returns empty
    /// arrays for a mono (or unreadable) file — the signal to skip channel diarization.
    static func frameEnergies(
        of fileURL: URL,
        frameDuration: TimeInterval
    ) throws -> (left: [Float], right: [Float]) {
        let file = try AVAudioFile(forReading: fileURL)
        let format = file.processingFormat
        guard format.channelCount >= 2 else { return ([], []) }

        let frameSize = max(1, AVAudioFrameCount(Double(format.sampleRate) * frameDuration))
        var left: [Float] = []
        var right: [Float] = []

        while file.framePosition < file.length {
            guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frameSize) else { break }
            try file.read(into: buffer, frameCount: frameSize)
            guard buffer.frameLength > 0, let channels = buffer.floatChannelData else { break }
            left.append(rms(channels[0], count: Int(buffer.frameLength)))
            right.append(rms(channels[1], count: Int(buffer.frameLength)))
        }
        return (left, right)
    }

    private static func rms(_ samples: UnsafePointer<Float>, count: Int) -> Float {
        guard count > 0 else { return 0 }
        var sum: Float = 0
        for idx in 0 ..< count {
            sum += samples[idx] * samples[idx]
        }
        return (sum / Float(count)).squareRoot()
    }
}
