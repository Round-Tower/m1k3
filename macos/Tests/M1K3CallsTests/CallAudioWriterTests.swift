//
//  CallAudioWriterTests.swift
//  M1K3CallsTests
//
//  The stereo-capture file write was verify-by-launch and left an orphaned
//  zero-frame .caf on disk when a write failed or the app was killed mid-write
//  (a recording that "happened" but produced no audio). CallAudioWriter makes the
//  write atomic + validated — these pin the contract: no samples → a clear throw and
//  NO file; real samples → a file whose frames match the capture; never a 0-frame
//  orphan returned as success.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-21, Confidence 0.85, Prior: Unknown
//

import AVFoundation
import Foundation
@testable import M1K3Calls
import Testing

struct CallAudioWriterTests {
    /// A unique scratch directory per test so file assertions are isolated.
    private func scratch() throws -> URL {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("callwriter-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private func cafCount(in dir: URL) -> Int {
        let items = (try? FileManager.default.contentsOfDirectory(atPath: dir.path)) ?? []
        return items.filter { $0.hasSuffix(".caf") }.count
    }

    private func partialCount(in dir: URL) -> Int {
        let items = (try? FileManager.default.contentsOfDirectory(atPath: dir.path)) ?? []
        return items.filter { $0.hasSuffix(".partial") }.count
    }

    @Test("empty capture throws and writes NO file")
    func emptyThrowsNoFile() throws {
        let dir = try scratch()
        #expect(throws: CallAudioWriterError.emptyCapture) {
            try CallAudioWriter.write(near: [], far: [], sampleRate: 48000, in: dir)
        }
        #expect(cafCount(in: dir) == 0)
        #expect(partialCount(in: dir) == 0) // no orphaned partial left behind
    }

    @Test("real samples write a validated stereo file with matching frames")
    func realSamplesWriteValidFile() throws {
        let dir = try scratch()
        let near = (0 ..< 2400).map { Float(sin(Double($0) * 0.05)) } // 50ms @ 48k
        let url = try CallAudioWriter.write(near: near, far: [], sampleRate: 48000, in: dir)

        #expect(url.pathExtension == "caf")
        #expect(url.deletingLastPathComponent().path == dir.path) // lands in the given dir
        #expect(cafCount(in: dir) == 1)
        #expect(partialCount(in: dir) == 0)

        let file = try AVAudioFile(forReading: url)
        #expect(file.length == Int64(near.count)) // frames == captured samples
        #expect(file.fileFormat.channelCount == 2) // stereo (silence-paired)
        #expect(file.fileFormat.sampleRate == 48000)
    }

    @Test("frame count follows the longer channel (time-aligned)")
    func framesFollowLongerChannel() throws {
        let dir = try scratch()
        let near = [Float](repeating: 0.2, count: 1000)
        let far = [Float](repeating: 0.1, count: 1600)
        let url = try CallAudioWriter.write(near: near, far: far, sampleRate: 48000, in: dir)
        let file = try AVAudioFile(forReading: url)
        #expect(file.length == 1600)
    }

    @Test("near lands on channel 0, far on channel 1 — the diarizer's speaker contract")
    func channelsPreserveSpeakerOrder() throws {
        let dir = try scratch()
        let near = [Float](repeating: 0.7, count: 512) // "Speaker 1"
        let far = [Float](repeating: -0.4, count: 512) // "Speaker 2"
        let url = try CallAudioWriter.write(near: near, far: far, sampleRate: 48000, in: dir)

        let file = try AVAudioFile(forReading: url)
        #expect(file.processingFormat.channelCount == 2)
        let buffer = try #require(AVAudioPCMBuffer(
            pcmFormat: file.processingFormat,
            frameCapacity: AVAudioFrameCount(file.length)
        ))
        try file.read(into: buffer)
        let channels = try #require(buffer.floatChannelData)
        // Channel ordering is the contract StereoChannelDiarizer relies on; a
        // regression that swapped/merged channels would mis-attribute speakers.
        #expect(abs(channels[0][100] - 0.7) < 0.001)
        #expect(abs(channels[1][100] - -0.4) < 0.001)
    }
}
