//
//  SpeechChunker.swift
//  M1K3Kokoro
//
//  Splits long text into Kokoro-sized pieces BEFORE G2P, so nothing ever hits
//  the model's 510-phoneme-token context cap (which used to truncate long
//  answers silently). Splits prefer sentence enders, then commas/clause marks,
//  then word boundaries; pieces pack greedily up to the budget. Ranges are
//  UTF-16 offsets that tile the input losslessly — the synthesizer maps each
//  chunk's timeline back into the full string by range offset.
//
//  The token counter is injected: production passes the G2P assembly count,
//  tests pass something deterministic. The +1 junction cost per merged piece
//  is exactly the inter-word space token our G2P emits at a join.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.9 (pure, test-pinned;
//  mirrors the Python realtime_processor sentence-split precedent).
//  Prior: Unknown.
//

import Foundation

public enum SpeechChunker {
    /// Separator tiers, most natural first. A piece that exceeds the budget is
    /// re-split at the next tier.
    private static let separatorTiers: [Set<Character>] = [
        [".", "!", "?", "…"],
        [",", ";", ":", "—"],
        [" ", "\n", "\t", "\r"],
    ]

    /// UTF-16 ranges of `text`, each guaranteed ≤ `maxTokens` by `tokenCount`,
    /// tiling the input losslessly. Empty for whitespace-only input.
    public static func chunkRanges(
        _ text: String,
        tokenCount: @escaping (Substring) -> Int,
        maxTokens: Int
    ) -> [Range<Int>] {
        let chars = Array(text)
        guard chars.contains(where: { !$0.isWhitespace }) else { return [] }

        // Prefix sums: utf16Position[i] = UTF-16 offset of chars[i].
        var utf16Position = [Int](repeating: 0, count: chars.count + 1)
        for (index, char) in chars.enumerated() {
            utf16Position[index + 1] = utf16Position[index] + char.utf16.count
        }

        var packer = Packer(chars: chars, tokenCount: tokenCount, maxTokens: maxTokens)
        for piece in split(0 ..< chars.count, chars: chars, tier: 0) {
            packer.add(piece: piece, nextTier: 1)
        }
        packer.finish()
        return packer.chunks.map { utf16Position[$0.lowerBound] ..< utf16Position[$0.upperBound] }
    }

    /// Pieces of `range` ending after a separator run plus trailing whitespace.
    private static func split(_ range: Range<Int>, chars: [Character], tier: Int) -> [Range<Int>] {
        let separators = separatorTiers[tier]
        var pieces: [Range<Int>] = []
        var pieceStart = range.lowerBound
        var index = range.lowerBound
        while index < range.upperBound {
            if separators.contains(chars[index]) {
                while index < range.upperBound, separators.contains(chars[index]) {
                    index += 1
                }
                while index < range.upperBound, chars[index].isWhitespace {
                    index += 1
                }
                pieces.append(pieceStart ..< index)
                pieceStart = index
            } else {
                index += 1
            }
        }
        if pieceStart < range.upperBound {
            pieces.append(pieceStart ..< range.upperBound)
        }
        return pieces
    }

    /// Greedy accumulator over character-index pieces. A piece that cannot fit
    /// even alone is re-split at `nextTier`; past the last tier it ships as its
    /// own oversized chunk (a single >510-phoneme word — unreachable English).
    private struct Packer {
        let chars: [Character]
        let tokenCount: (Substring) -> Int
        let maxTokens: Int
        var chunks: [Range<Int>] = []
        private var current: Range<Int>?
        private var currentCost = 0

        init(chars: [Character], tokenCount: @escaping (Substring) -> Int, maxTokens: Int) {
            self.chars = chars
            self.tokenCount = tokenCount
            self.maxTokens = maxTokens
        }

        mutating func add(piece: Range<Int>, nextTier: Int) {
            let cost = tokenCount(String(chars[piece])[...])
            // +1 is an UPPER bound on the join cost: a word-to-word join emits
            // exactly one space token; a punctuation-led piece joins for free.
            // Conservative — a packed chunk can undershoot but never exceed.
            let junction = current == nil ? 0 : 1
            if currentCost + junction + cost <= maxTokens {
                current = (current?.lowerBound ?? piece.lowerBound) ..< piece.upperBound
                currentCost += junction + cost
            } else if cost <= maxTokens {
                finish()
                current = piece
                currentCost = cost
            } else if nextTier < SpeechChunker.separatorTiers.count {
                let subPieces = SpeechChunker.split(piece, chars: chars, tier: nextTier)
                if subPieces.count > 1 {
                    for subPiece in subPieces {
                        add(piece: subPiece, nextTier: nextTier + 1)
                    }
                } else {
                    add(piece: piece, nextTier: nextTier + 1)
                }
            } else {
                finish()
                chunks.append(piece)
            }
        }

        mutating func finish() {
            if let chunk = current {
                chunks.append(chunk)
                current = nil
                currentCost = 0
            }
        }
    }
}
