import M1K3Voice
import Testing

/// Pins the degenerate-format gate that keeps `AppleSpeechTranscriber` from
/// installing a dead audio tap. The live STT mic reads
/// `inputNode.outputFormat(forBus:0)` and, when the audio route hasn't settled
/// (a Bluetooth/HFP mic engaging, or TCC not yet granted), that call returns a
/// degenerate 0-Hz / 0-channel format. Handing THAT to `installTap` invalidates
/// the HAL AudioUnit (kAudioUnitErr_InvalidElement, -10877) and the recogniser
/// captures NOTHING — the "mic won't capture over BLE" bug. The gate is the same
/// `sampleRate > 0` guard StereoCallRecorder already carries, made pure + shared
/// so both capture paths refuse a dead format instead of silently going deaf.
struct MicTapFormatGateTests {
    @Test("a normal built-in mic format is usable")
    func builtInIsUsable() {
        #expect(MicTapFormatGate.isUsable(sampleRate: 48000, channelCount: 1))
    }

    @Test("a Bluetooth HFP format (16kHz mono) is usable")
    func bluetoothHFPIsUsable() {
        // The exact format a BLE headset negotiates once HFP engages — it must
        // pass, or dictation over Bluetooth never installs a tap at all.
        #expect(MicTapFormatGate.isUsable(sampleRate: 16000, channelCount: 1))
    }

    @Test("a degenerate 0-Hz format is rejected — the -10877 trigger")
    func zeroHzRejected() {
        #expect(!MicTapFormatGate.isUsable(sampleRate: 0, channelCount: 1))
    }

    @Test("a 0-channel format is rejected")
    func zeroChannelRejected() {
        #expect(!MicTapFormatGate.isUsable(sampleRate: 48000, channelCount: 0))
    }

    @Test("a fully degenerate format (route not ready) is rejected")
    func fullyDegenerateRejected() {
        #expect(!MicTapFormatGate.isUsable(sampleRate: 0, channelCount: 0))
    }
}
