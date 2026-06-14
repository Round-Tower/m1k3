//
//  MicTapFormatGate.swift
//  M1K3Voice
//
//  The one decision that keeps a live mic tap from going silently deaf: is the
//  input format the audio engine handed us actually usable? `AVAudioEngine`'s
//  `inputNode.outputFormat(forBus:0)` returns a DEGENERATE 0-Hz / 0-channel
//  format when the route hasn't settled — a Bluetooth (HFP) mic still engaging,
//  or mic TCC not yet granted. Installing a tap with that format invalidates the
//  HAL AudioUnit (kAudioUnitErr_InvalidElement, -10877) and the recogniser
//  captures nothing. StereoCallRecorder already refuses it inline; this lifts the
//  same guard to a pure, testable predicate the live STT path can share.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-14, Confidence 0.85, Prior: Unknown
//  (mirrors the StereoCallRecorder.startMic 0-Hz guard, 2026-06-12).

import Foundation

/// Pure guard: a tap-able input format needs a real clock and at least one
/// channel. Anything else is a route that hasn't come up — refuse it rather than
/// install a dead tap that yields no audio (the BLE/-10877 silent-capture bug).
public enum MicTapFormatGate {
    public static func isUsable(sampleRate: Double, channelCount: UInt32) -> Bool {
        sampleRate > 0 && channelCount > 0
    }
}
