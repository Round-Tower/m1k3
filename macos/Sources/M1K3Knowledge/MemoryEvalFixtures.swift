//
//  MemoryEvalFixtures.swift
//  M1K3Knowledge
//
//  Fixture pairs for the memory-recall threshold eval (M1K3_SELFTEST_MEMEVAL).
//  GroundingGate.memoryThreshold gates query→short-fact cosine, a different
//  distribution from the query→chunk pairs chunkThreshold was tuned on — so
//  the bar is set from THIS data, embedded with the real BGE embedder on
//  device, not guessed. Positives are "user told me X / later asks Y" pairs
//  that MUST recall; negatives are short facts that must NOT surface for an
//  unrelated query.
//
//  The fixtures and report formatter are pure (unit-tested); only the
//  embedding pass is verify-by-launch.
//
//  Signed: Kev + claude-fable-5, 2026-06-12, Confidence 0.85 (pair set is
//  hand-curated — extend it as real recall misses surface). Prior: Unknown

import Foundation

public enum MemoryEvalFixtures {
    public struct Pair: Sendable {
        public let memory: String
        public let query: String

        public init(memory: String, query: String) {
            self.memory = memory
            self.query = query
        }
    }

    /// Facts the user gave M1K3, with a question a later session would ask.
    /// Every one of these failing the gate is a user-visible broken promise.
    public static let positives: [Pair] = [
        .init(memory: "Kev's sister is called Aoife.", query: "what's my sister's name?"),
        .init(memory: "The user prefers metric units.", query: "how tall is that in my preferred units?"),
        .init(memory: "Kev lives in Cork, Ireland.", query: "where do I live?"),
        .init(memory: "The user's dog is a collie named Bran.", query: "what breed is my dog?"),
        .init(memory: "Kev is dyslexic and prefers audio summaries.", query: "remind me why I like things read aloud"),
        .init(memory: "The user decided to use GRDB over Core Data for persistence.", query: "which database library did we pick?"),
        .init(memory: "Kev's company is called Round Tower.", query: "what's the name of my company?"),
        .init(memory: "The user's favourite editor is Xcode with Vim bindings.", query: "what editor setup do I use?"),
        .init(memory: "Kev drinks his coffee black, no sugar.", query: "how do I take my coffee?"),
        .init(memory: "The user was born in 1987.", query: "what year was I born?"),
        .init(memory: "Kev's partner is called Niamh.", query: "what's my partner's name?"),
        .init(memory: "The user decided the app ships on macOS first, iOS later.", query: "which platform are we shipping first?"),
        .init(memory: "Kev plays five-a-side football on Thursdays.", query: "what day do I play football?"),
        .init(memory: "The user is allergic to penicillin.", query: "what am I allergic to?"),
        .init(memory: "Kev's mum's birthday is the 14th of March.", query: "when is my mum's birthday?"),
        .init(memory: "The user prefers en-GB spellings in all writing.", query: "which English spelling style do I want?"),
        .init(memory: "Kev's car is a blue Skoda Octavia.", query: "what car do I drive?"),
        .init(memory: "The user keeps project notes in a folder called Development.", query: "where do I keep my project notes?"),
        .init(memory: "Kev studied computer science at UCC.", query: "where did I go to college?"),
        .init(memory: "The user's main project is a local AI assistant called M1K3.", query: "what's my main project?"),
        .init(memory: "Kev wants commit messages without emoji.", query: "how do I like my commit messages?"),
        .init(memory: "The user's daughter starts school in September.", query: "when does my daughter start school?"),
    ]

    /// Unrelated fact/query pairs — the gate must keep these OUT. Same short,
    /// personal register as the positives so the test is honest.
    public static let negatives: [Pair] = [
        .init(memory: "Kev's sister is called Aoife.", query: "what's the capital of France?"),
        .init(memory: "The user prefers metric units.", query: "who wrote Ulysses?"),
        .init(memory: "Kev drinks his coffee black, no sugar.", query: "how do transformers handle attention?"),
        .init(memory: "The user's dog is a collie named Bran.", query: "what's a good pasta recipe?"),
        .init(memory: "Kev plays five-a-side football on Thursdays.", query: "explain quantum entanglement"),
        .init(memory: "The user was born in 1987.", query: "what's the weather like in Tokyo?"),
        .init(memory: "Kev's car is a blue Skoda Octavia.", query: "summarise the plot of Hamlet"),
        .init(memory: "The user is allergic to penicillin.", query: "how do I centre a div?"),
        .init(memory: "Kev studied computer science at UCC.", query: "what time is high tide?"),
        .init(memory: "The user keeps project notes in a folder called Development.", query: "translate hello to Japanese"),
        .init(memory: "Kev's company is called Round Tower.", query: "best sourdough starter ratio?"),
    ]
}

/// Pure formatter for the MEMEVAL report — given the measured cosine scores,
/// render the distributions and a suggested threshold.
public enum MemoryEvalReport {
    public static func render(positives: [Float], negatives: [Float]) -> String {
        var lines: [String] = []
        lines.append("memeval positives: \(summary(positives))")
        lines.append("memeval negatives: \(summary(negatives))")
        if let posMin = positives.min(), let negMax = negatives.max() {
            if posMin > negMax {
                let suggested = (posMin + negMax) / 2
                lines.append(String(
                    format: "memeval suggested threshold: %.3f (clean separation: neg max %.3f < pos min %.3f)",
                    suggested, negMax, posMin
                ))
            } else {
                let overlap = positives.filter { $0 <= negMax }.count
                lines.append(String(
                    format: "memeval OVERLAP: %d positive(s) at or below neg max %.3f — pick the floor that loses fewest recalls",
                    overlap, negMax
                ))
            }
        }
        return lines.joined(separator: "\n")
    }

    static func summary(_ scores: [Float]) -> String {
        guard !scores.isEmpty else { return "no scores" }
        let sorted = scores.sorted()
        let median = sorted[sorted.count / 2]
        return String(
            format: "min %.3f / median %.3f / max %.3f (n=%d)",
            sorted.first ?? 0, median, sorted.last ?? 0, scores.count
        )
    }
}
