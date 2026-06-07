//
//  StereoInterleaver.swift
//  M1K3Calls
//
//  Mux two mono channels into one interleaved stereo stream. For stereo call
//  capture: left = near-end (mic, "Speaker 1"), right = far-end (system audio,
//  "Speaker 2"). The StereoChannelDiarizer then reads channel == speaker.
//
//  Pure + deterministic — the testable core of an otherwise verify-by-launch
//  capture path. The shorter channel is padded with silence so the two sides
//  stay time-aligned (both are written from the same start instant).
//
//  Signed: Kev + claude-opus-4-8, 2026-06-07, Confidence 0.9, Prior: Unknown

import Foundation

public enum StereoInterleaver {
    /// Interleave two mono sample buffers into LRLR… stereo, padding the shorter
    /// side with silence. Both buffers are assumed to share a sample rate (the
    /// capture adapter configures mic + system audio to a common format).
    public static func interleave(left: [Float], right: [Float]) -> [Float] {
        let frames = max(left.count, right.count)
        guard frames > 0 else { return [] }
        var out = [Float](repeating: 0, count: frames * 2)
        for frame in 0 ..< frames {
            if frame < left.count { out[frame * 2] = left[frame] }
            if frame < right.count { out[frame * 2 + 1] = right[frame] }
        }
        return out
    }
}
