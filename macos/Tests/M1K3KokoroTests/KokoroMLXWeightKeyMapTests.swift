//
//  KokoroMLXWeightKeyMapTests.swift
//  M1K3KokoroTests
//
//  Pins the pure PyTorch-checkpoint-key → MLX-module-tree-key remap
//  (KokoroMLXWeightKeyMap.remap) — the one piece of the MLX backend port
//  testable without the metallib wall. No MLXArray anywhere in this file.
//
//  Signed: Kev + claude-fable-5, 2026-07-18, Confidence 0.9, Prior: none
//

import Foundation
@testable import M1K3Kokoro
import Testing

struct KokoroMLXWeightKeyMapTests {
    @Test("position_ids buffers are dropped — MLX computes position ids itself")
    func dropsPositionIds() {
        #expect(KokoroMLXWeightKeyMap.remap("bert.embeddings.position_ids") == nil)
    }

    @Test("LSTM reverse-direction keys remap to the _backward MLX names")
    func remapsLSTMReverseKeys() {
        #expect(KokoroMLXWeightKeyMap.remap("predictor.lstm.weight_ih_l0_reverse") == "predictor.lstm.Wx_backward")
        #expect(KokoroMLXWeightKeyMap.remap("predictor.lstm.weight_hh_l0_reverse") == "predictor.lstm.Wh_backward")
        #expect(KokoroMLXWeightKeyMap.remap("predictor.lstm.bias_ih_l0_reverse") == "predictor.lstm.bias_ih_backward")
        #expect(KokoroMLXWeightKeyMap.remap("predictor.lstm.bias_hh_l0_reverse") == "predictor.lstm.bias_hh_backward")
    }

    @Test("LSTM forward-direction keys remap to the _forward MLX names, and are NOT corrupted by the reverse rule")
    func remapsLSTMForwardKeysWithoutReverseCollision() {
        // The reverse rules run FIRST specifically so this case is safe: a
        // forward-only key never contains "_reverse", so it can't match the
        // reverse patterns, and the forward remap leaves no dangling suffix.
        #expect(KokoroMLXWeightKeyMap.remap("predictor.lstm.weight_ih_l0") == "predictor.lstm.Wx_forward")
        #expect(KokoroMLXWeightKeyMap.remap("predictor.lstm.weight_hh_l0") == "predictor.lstm.Wh_forward")
        #expect(KokoroMLXWeightKeyMap.remap("predictor.lstm.bias_ih_l0") == "predictor.lstm.bias_ih_forward")
        #expect(KokoroMLXWeightKeyMap.remap("predictor.lstm.bias_hh_l0") == "predictor.lstm.bias_hh_forward")
    }

    @Test("a reverse key is never left with a dangling _reverse suffix after remap")
    func reverseKeyNeverDanglesSuffix() {
        // This is the ordering hazard the upstream comment calls out: if the
        // non-reverse rule ran first, "weight_ih_l0_reverse" would become
        // "Wx_forward_reverse" — a key nothing in the module tree matches.
        let remapped = KokoroMLXWeightKeyMap.remap("predictor.lstm.weight_ih_l0_reverse")
        #expect(remapped?.contains("_reverse") == false)
        #expect(remapped?.contains("_forward") == false)
    }

    @Test("gamma/beta (StyleTTS2 LayerNorm naming) remap to weight/bias")
    func remapsGammaBeta() {
        #expect(KokoroMLXWeightKeyMap.remap("decoder.encode.norm1.norm.gamma") == "decoder.encode.norm1.norm.weight")
        #expect(KokoroMLXWeightKeyMap.remap("decoder.encode.norm1.norm.beta") == "decoder.encode.norm1.norm.bias")
    }

    @Test("alpha1/alpha2 dotted indices remap to underscored MLX property names")
    func remapsAlphaIndices() {
        #expect(KokoroMLXWeightKeyMap.remap("decoder.generator.resblocks.0.alpha1.0") == "decoder.generator.resblocks.0.alpha1_0")
        #expect(KokoroMLXWeightKeyMap.remap("decoder.generator.resblocks.0.alpha2.1") == "decoder.generator.resblocks.0.alpha2_1")
    }

    @Test("a key with no matching rule passes through unchanged")
    func passesThroughUnmatchedKeys() {
        #expect(KokoroMLXWeightKeyMap.remap("bert.embeddings.word_embeddings.weight")
            == "bert.embeddings.word_embeddings.weight")
    }
}
