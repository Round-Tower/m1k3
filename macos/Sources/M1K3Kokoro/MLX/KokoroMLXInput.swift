//
//  KokoroMLXInput.swift
//  M1K3Kokoro
//
//  The one pure seam in the MLX synthesis path: wrapping KokoroG2P's phoneme
//  tokens in the model's [pad] … [pad] boundary. Kept as a standalone pure
//  function (not inlined into KokoroSynthesizer.produceChunks) so the
//  assembly can be red-first unit-tested without the metallib wall — no
//  MLXArray involved, just Int arithmetic.
//
//  Signed: Kev + claude-fable-5, 2026-07-18, Confidence 0.9, Prior: none —
//  new file, extracted while porting KokoroSynthesizer.swift's Loaded/infer
//  from the ONNX Runtime backend to MLX (see that file's 2026-07-18 review
//  note). The framing itself ([0] + tokens + [0]) is unchanged from the
//  prior ORT path; only the element type moved from Int64 (ORT's tensor
//  element type) to Int32 (MLX's — matches the vendored KokoroModel's own
//  `tokenize` framing, verified against Blaizzy/mlx-audio-swift's
//  KokoroModel.generate()).
//

import Foundation

enum KokoroMLXInput {
    /// `[pad] + phonemeTokens + [pad]` as Int32 — the exact model-input
    /// framing KokoroModel.generate() uses upstream, and byte-identical to
    /// what the prior ORT path sent (same pad id, same boundary shape).
    static func modelTokens(_ phonemeTokens: [Int]) -> [Int32] {
        [Int32(KokoroG2P.pad)] + phonemeTokens.map(Int32.init) + [Int32(KokoroG2P.pad)]
    }
}
