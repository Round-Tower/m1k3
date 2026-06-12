//
//  MemoryDistiller.swift
//  M1K3Chat
//
//  The distillation core: turn a finished conversation slice into 0-N durable
//  facts about the user. Prompt + parser + normalizer are pure (TDD'd); the
//  ProviderMemoryDistiller orchestrates AFM-first with an MLX fallback (the
//  SummarizationPipeline two-tier shape, sequential because one output is
//  consumed, not two).
//
//  Output contract is line-based — "FACT: <one sentence>" or exactly "NONE" —
//  because small local models fumble JSON (CallSummaryParser precedent) and
//  FACT: lines parse with zero ambiguity. The parser is the defence layer:
//  whatever persona-flavoured chatter a model emits, only FACT: lines survive.
//
//  Signed: Kev + claude-fable-5, 2026-06-12, Confidence 0.85 (pure cores
//  tested; real-model fact quality is verify-at-⌘R). Prior: Unknown

import Foundation
import M1K3Inference

// MARK: - Protocol

/// Extracts durable facts from a transcript slice. Throws ONLY on total
/// generation failure — the caller withholds the distillation watermark on
/// throw so the same slice is retried at the next trigger.
public protocol MemoryDistilling: Sendable {
    func distill(turns: [ChatTurn]) async throws -> [String]
}

// MARK: - Prompt

public enum MemoryDistillationPrompt {
    /// Neutral note-taker instructions — deliberately NOT M1K3Persona (a
    /// judge shouldn't be the defendant's twin; the distiller reads M1K3's
    /// own conversations).
    public static let instructions = """
    You are a neutral note-taker. You read conversations and extract only \
    durable facts about the user. You never chat, never add commentary, and \
    reply only in the exact format requested.
    """

    static let maxCharsPerTurn = 400
    static let maxTotalChars = 6000

    /// The transcript rendered newest-last with role labels, capped per turn
    /// and in total (newest turns survive the total cap — they're the ones
    /// the watermark says are new).
    public static func build(turns: [ChatTurn]) -> String {
        var lines: [String] = []
        var total = 0
        for turn in turns.reversed() {
            let label = turn.role == .user ? "USER" : "ASSISTANT"
            let text = String(turn.text.prefix(maxCharsPerTurn))
            let line = "\(label): \(text)"
            if total + line.count > maxTotalChars { break }
            lines.append(line)
            total += line.count
        }
        let transcript = lines.reversed().joined(separator: "\n")
        return """
        Read the conversation below and extract facts about the user worth \
        remembering long-term: stable personal facts, preferences, decisions, \
        relationships, ongoing projects.

        Do NOT extract: small talk, transient states ("user is tired today"), \
        questions the user merely asked about, or anything the assistant said \
        about itself.

        Reply with one line per fact, each starting with exactly "FACT: ". \
        Each fact must be one short standalone sentence about the user. \
        If there is nothing durable, reply with exactly "NONE".

        CONVERSATION:
        \(transcript)
        """
    }
}

// MARK: - Parser

public enum MemoryFactParser {
    static let maxFacts = 8
    static let maxFactLength = 200
    static let minFactLength = 10

    /// Keep only well-formed FACT: lines. Garbage in → [] out, never a throw:
    /// the parser is the defence against off-format model output.
    public static func parse(_ raw: String) -> [String] {
        // Reasoning models think first — the facts live in the answer half.
        let answer = ReasoningSplit.split(raw).answer
        var seen = Set<String>()
        var facts: [String] = []
        for line in answer.split(separator: "\n") {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            guard trimmed.hasPrefix("FACT:") else { continue }
            let fact = trimmed.dropFirst("FACT:".count)
                .trimmingCharacters(in: .whitespaces)
            // Overlong = the model rambled mid-line; drop rather than cut a
            // fact mid-thought. Tiny = no information ("FACT: yes").
            guard fact.count >= minFactLength, fact.count <= maxFactLength else { continue }
            guard seen.insert(MemoryFactNormalizer.normalize(fact)).inserted else { continue }
            facts.append(fact)
            if facts.count == maxFacts { break }
        }
        return facts
    }

    /// True when the model explicitly said there is nothing durable.
    static func isExplicitNone(_ raw: String) -> Bool {
        ReasoningSplit.split(raw).answer
            .trimmingCharacters(in: .whitespacesAndNewlines) == "NONE"
    }
}

// MARK: - Normalizer

/// Canonical form for the dedupe hash: the same fact re-distilled with
/// different capitalisation/spacing/punctuation must collapse to one row.
public enum MemoryFactNormalizer {
    public static func normalize(_ fact: String) -> String {
        var text = fact.lowercased()
            .components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
        while let last = text.last, ".,;:!?".contains(last) {
            text = String(text.dropLast())
        }
        return text
    }
}

// MARK: - Provider orchestration

/// AFM-first, fallback second. A clean "NONE" from the primary is a valid
/// empty result (no fall-through); garbage (no FACT: lines, not NONE) tries
/// the fallback. Both producing garbage returns [] — retrying garbage forever
/// would re-distill the same slice at every conversation exit. Only a throw
/// from the final attempted provider propagates (the watermark-retry signal).
public struct ProviderMemoryDistiller: MemoryDistilling {
    private let primary: any InferenceProvider
    private let fallback: any InferenceProvider

    public init(primary: any InferenceProvider, fallback: any InferenceProvider) {
        self.primary = primary
        self.fallback = fallback
    }

    public func distill(turns: [ChatTurn]) async throws -> [String] {
        let prompt = MemoryDistillationPrompt.build(turns: turns)
        if primary.isAvailable {
            do {
                let raw = try await primary.generate(prompt: prompt)
                let facts = MemoryFactParser.parse(raw)
                if !facts.isEmpty || MemoryFactParser.isExplicitNone(raw) {
                    return facts
                }
                // Off-format output: give the fallback a chance.
            } catch {
                // Primary generation failed: the fallback is the retry.
            }
        }
        let raw = try await fallback.generate(prompt: prompt)
        return MemoryFactParser.parse(raw)
    }
}
