//
//  KokoroMLXConfigDecodeTests.swift
//  M1K3KokoroTests
//
//  Decodes a REAL config.json fixture (byte-for-byte copy of
//  mlx-community/Kokoro-82M-bf16's config.json, fetched 2026-07-18 — the
//  weights this port stages) into the vendored `KokoroConfig` and pins the
//  fields the model construction/forward-pass/sanitize path depends on. Pure
//  JSONDecoder work — no MLXArray, so no metallib wall.
//
//  Signed: Kev + claude-fable-5, 2026-07-18, Confidence 0.9, Prior: none
//

import Foundation
@testable import M1K3Kokoro
import Testing

struct KokoroMLXConfigDecodeTests {
    @Test("decodes the real Kokoro-82M-bf16 config.json shape")
    func decodesRealConfig() throws {
        let config = try JSONDecoder().decode(KokoroConfig.self, from: Data(Self.fixtureJSON.utf8))

        #expect(config.hiddenDim == 512)
        #expect(config.styleDim == 128)
        #expect(config.nToken == 178)
        #expect(config.nLayer == 3)
        #expect(config.textEncoderKernelSize == 5)
        #expect(config.sampleRate == 24000) // not in this fixture — falls back to the default
        #expect(config.quantization == nil) // this checkpoint is unquantized

        #expect(config.plbert.hiddenSize == 768)
        #expect(config.plbert.numAttentionHeads == 12)
        #expect(config.plbert.numHiddenLayers == 12)

        #expect(config.istftnet.upsampleRates == [10, 6])
        #expect(config.istftnet.genIstftNFft == 20)
        #expect(config.istftnet.genIstftHopSize == 5)
        #expect(config.istftnet.resblockKernelSizes == [3, 7, 11])
    }

    @Test("the vocab map decodes with the exact ids KokoroG2P hardcodes")
    func vocabMatchesG2PIds() throws {
        // The parity check this whole port hinged on: verified once by hand
        // against the live HF config.json before any code was written (see
        // MLX/Vendored/README.md); pinned here so it can never silently drift.
        let config = try JSONDecoder().decode(KokoroConfig.self, from: Data(Self.fixtureJSON.utf8))

        // Punctuation ids KokoroG2P.defaultPunctuation hardcodes.
        let punctuation: [Character: Int] = [
            ";": 1, ":": 2, ",": 3, ".": 4, "!": 5, "?": 6,
            "—": 9, "…": 10, "\"": 11, "(": 12, ")": 13, "“": 14, "”": 15,
        ]
        for (char, expected) in punctuation {
            #expect(config.vocab[String(char)] == expected)
        }
        // The space token KokoroG2P.space hardcodes.
        #expect(config.vocab[" "] == KokoroG2P.space)

        // The inflection-suffix phoneme ids KokoroG2P hardcodes (d/s/t/z and
        // the sibilant/voiceless finals used to pick plural/past allomorphs).
        let phonemes: [Character: Int] = [
            "d": 46, "s": 61, "t": 62, "z": 68,
            "ʤ": 82, "ɪ": 102, "θ": 119, "ʃ": 131, "ʧ": 133, "ʒ": 147,
        ]
        for (char, expected) in phonemes {
            #expect(config.vocab[String(char)] == expected)
        }
    }

    /// Byte-for-byte `mlx-community/Kokoro-82M-bf16`'s `config.json`
    /// (fetched 2026-07-18). Raw string literal (`#"""..."""#`) so the JSON's
    /// own `\uXXXX` escapes pass through untouched instead of being
    /// (invalidly) parsed as Swift escapes.
    private static let fixtureJSON = #"""
    {
      "istftnet": {
        "upsample_kernel_sizes": [20, 12],
        "upsample_rates": [10, 6],
        "gen_istft_hop_size": 5,
        "gen_istft_n_fft": 20,
        "resblock_dilation_sizes": [
          [1, 3, 5],
          [1, 3, 5],
          [1, 3, 5]
        ],
        "resblock_kernel_sizes": [3, 7, 11],
        "upsample_initial_channel": 512
      },
      "dim_in": 64,
      "dropout": 0.2,
      "hidden_dim": 512,
      "max_conv_dim": 512,
      "max_dur": 50,
      "multispeaker": true,
      "n_layer": 3,
      "n_mels": 80,
      "n_token": 178,
      "style_dim": 128,
      "text_encoder_kernel_size": 5,
      "plbert": {
        "hidden_size": 768,
        "num_attention_heads": 12,
        "intermediate_size": 2048,
        "max_position_embeddings": 512,
        "num_hidden_layers": 12,
        "dropout": 0.1
      },
      "vocab": {
        ";": 1,
        ":": 2,
        ",": 3,
        ".": 4,
        "!": 5,
        "?": 6,
        "—": 9,
        "…": 10,
        "\"": 11,
        "(": 12,
        ")": 13,
        "“": 14,
        "”": 15,
        " ": 16,
        "̃": 17,
        "ʣ": 18,
        "ʥ": 19,
        "ʦ": 20,
        "ʨ": 21,
        "ᵝ": 22,
        "ꭧ": 23,
        "A": 24,
        "I": 25,
        "O": 31,
        "Q": 33,
        "S": 35,
        "T": 36,
        "W": 39,
        "Y": 41,
        "ᵊ": 42,
        "a": 43,
        "b": 44,
        "c": 45,
        "d": 46,
        "e": 47,
        "f": 48,
        "h": 50,
        "i": 51,
        "j": 52,
        "k": 53,
        "l": 54,
        "m": 55,
        "n": 56,
        "o": 57,
        "p": 58,
        "q": 59,
        "r": 60,
        "s": 61,
        "t": 62,
        "u": 63,
        "v": 64,
        "w": 65,
        "x": 66,
        "y": 67,
        "z": 68,
        "ɑ": 69,
        "ɐ": 70,
        "ɒ": 71,
        "æ": 72,
        "β": 75,
        "ɔ": 76,
        "ɕ": 77,
        "ç": 78,
        "ɖ": 80,
        "ð": 81,
        "ʤ": 82,
        "ə": 83,
        "ɚ": 85,
        "ɛ": 86,
        "ɜ": 87,
        "ɟ": 90,
        "ɡ": 92,
        "ɥ": 99,
        "ɨ": 101,
        "ɪ": 102,
        "ʝ": 103,
        "ɯ": 110,
        "ɰ": 111,
        "ŋ": 112,
        "ɳ": 113,
        "ɲ": 114,
        "ɴ": 115,
        "ø": 116,
        "ɸ": 118,
        "θ": 119,
        "œ": 120,
        "ɹ": 123,
        "ɾ": 125,
        "ɻ": 126,
        "ʁ": 128,
        "ɽ": 129,
        "ʂ": 130,
        "ʃ": 131,
        "ʈ": 132,
        "ʧ": 133,
        "ʊ": 135,
        "ʋ": 136,
        "ʌ": 138,
        "ɣ": 139,
        "ɤ": 140,
        "χ": 142,
        "ʎ": 143,
        "ʒ": 147,
        "ʔ": 148,
        "ˈ": 156,
        "ˌ": 157,
        "ː": 158,
        "ʰ": 162,
        "ʲ": 164,
        "↓": 169,
        "→": 171,
        "↗": 172,
        "↘": 173,
        "ᵻ": 177
      }
    }
    """#
}
