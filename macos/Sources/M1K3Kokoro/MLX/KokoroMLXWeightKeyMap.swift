//
//  KokoroMLXWeightKeyMap.swift
//  M1K3Kokoro
//
//  The pure half of the vendored KokoroModel's weight-key sanitize: the
//  PyTorch checkpoint → MLX module-tree string remap, lifted out of
//  MLX/Vendored/Kokoro/KokoroModel.swift's `sanitize(weights:)` so it can be
//  red-first unit-tested (no MLXArray involved — the metallib wall in
//  `../../CLAUDE.md` blocks anything that touches one under `swift test`).
//  `sanitize(weights:)` calls this directly; it is the single source of
//  truth for the remap, not a parallel reimplementation liable to drift.
//
//  Signed: Kev + claude-fable-5, 2026-07-18, Confidence 0.9, Prior: none —
//  new file, extracted verbatim (same rules, same order) from Blaizzy/
//  mlx-audio-swift's KokoroModel.sanitize while porting KokoroSynthesizer
//  off ONNX Runtime.
//

import Foundation

enum KokoroMLXWeightKeyMap {
    /// Remaps one PyTorch-checkpoint key to its MLX module-tree key, or
    /// `nil` if the model doesn't need it at all (Albert's `position_ids`
    /// buffer — MLX computes position ids itself, see AlbertEmbeddings).
    ///
    /// Order matters: the four `_reverse` rules MUST run before their
    /// non-reverse counterparts — `.weight_ih_l0_reverse` contains
    /// `.weight_ih_l0` as a substring, so remapping forward first would
    /// leave `_reverse` dangling on an already-renamed backward key.
    static func remap(_ key: String) -> String? {
        if key.contains("position_ids") { return nil }

        var nk = key

        // LSTM: remap reverse FIRST to avoid partial match with forward keys.
        nk = nk.replacingOccurrences(of: ".weight_ih_l0_reverse", with: ".Wx_backward")
        nk = nk.replacingOccurrences(of: ".weight_hh_l0_reverse", with: ".Wh_backward")
        nk = nk.replacingOccurrences(of: ".bias_ih_l0_reverse", with: ".bias_ih_backward")
        nk = nk.replacingOccurrences(of: ".bias_hh_l0_reverse", with: ".bias_hh_backward")
        nk = nk.replacingOccurrences(of: ".weight_ih_l0", with: ".Wx_forward")
        nk = nk.replacingOccurrences(of: ".weight_hh_l0", with: ".Wh_forward")
        nk = nk.replacingOccurrences(of: ".bias_ih_l0", with: ".bias_ih_forward")
        nk = nk.replacingOccurrences(of: ".bias_hh_l0", with: ".bias_hh_forward")

        nk = nk.replacingOccurrences(of: ".gamma", with: ".weight")
        nk = nk.replacingOccurrences(of: ".beta", with: ".bias")
        nk = nk.replacingOccurrences(of: ".alpha1.", with: ".alpha1_")
        nk = nk.replacingOccurrences(of: ".alpha2.", with: ".alpha2_")

        return nk
    }
}
