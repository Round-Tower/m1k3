//
//  FollowUpSplit.swift
//  M1K3Inference
//
//  Lives alongside ThinkStripper (not M1K3Chat): pure, dependency-free, and
//  needed by M1K3Eval for cross-brain scoring, which depends on M1K3Inference
//  but not M1K3Chat. The live streaming counterpart, StreamingFollowUpSplitter,
//  stays in M1K3Chat — it's chat-specific rendering machinery, same split as
//  ThinkStripper (here) vs StreamingReasoningSplitter (M1K3Chat).
//
//  M1K3 offers up to 3 tap-to-send follow-up questions after an answer — the
//  accessibility win being fewer keystrokes, not a novel mechanism: same
//  "small models fumble multi-part output, a sentinel line doesn't" lesson as
//  MemoryDistiller's `FACT(<kind>):` trailer, but the payload here genuinely
//  needs structure (a short list of strings), so it's one line of JSON after
//  a sentinel rather than a repeated line-per-item format.
//
//  Output contract: "FOLLOWUPS: [\"question one?\", \"question two?\"]" as the
//  LAST thing in the answer (after any reasoning block). Never required —
//  absence means no chips. Whatever is detected after the sentinel is ALWAYS
//  stripped from the visible answer, whether or not it parses: leaving a raw
//  "FOLLOWUPS: [" fragment in a chat bubble is worse than silently dropping a
//  malformed suggestion list. A model that emits a malformed shape (wrong
//  types, unclosed JSON) gets zero follow-ups, not partial credit.
//
//  This is the post-stream authority (mirrors ReasoningSplit); the live
//  counterpart is StreamingFollowUpSplitter.
//
//  Signed: Kev + claude-sonnet-5, 2026-07-14, Confidence 0.85, Prior: Unknown

import Foundation

public enum FollowUpSplit {
    /// Public: StreamingFollowUpSplitter (M1K3Chat) watches for this same
    /// literal so the live and post-stream splitters can't drift apart.
    public static let sentinel = "FOLLOWUPS:"
    static let maxFollowUps = 3
    static let maxQuestionLength = 150

    /// Separate the trailing follow-up trailer from the answer. Both parts are
    /// whitespace-trimmed. No sentinel present → the whole text is the answer,
    /// no follow-ups.
    public static func split(_ text: String) -> (answer: String, followUps: [String]) {
        guard let range = text.range(of: sentinel) else {
            return (text.trimmingCharacters(in: .whitespacesAndNewlines), [])
        }
        let answer = String(text[..<range.lowerBound])
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let payload = String(text[range.upperBound...])
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return (answer, parseFollowUps(payload))
    }

    /// Garbage in → [] out, never a throw — this is the defence against
    /// off-format model output, same posture as MemoryFactParser. Public: also
    /// called directly by StreamingFollowUpSplitter (M1K3Chat, on the raw
    /// trailer payload) and M1K3Eval (on already-answer-isolated text).
    public static func parseFollowUps(_ payload: String) -> [String] {
        guard let data = payload.data(using: .utf8),
              let raw = try? JSONSerialization.jsonObject(with: data) as? [Any]
        else { return [] }
        // A non-string element degrades the WHOLE trailer, not just that
        // item — a model that emits the wrong shape shouldn't get partial
        // credit for the entries it happened to get right.
        guard raw.allSatisfy({ $0 is String }) else { return [] }

        var seen = Set<String>()
        var questions: [String] = []
        for case let item as String in raw {
            let trimmed = item.trimmingCharacters(in: .whitespacesAndNewlines)
            // Blank = nothing to show; overlong = the model rambled instead
            // of giving a short question — drop rather than truncate mid-thought.
            guard !trimmed.isEmpty, trimmed.count <= maxQuestionLength else { continue }
            guard seen.insert(trimmed.lowercased()).inserted else { continue }
            questions.append(trimmed)
            if questions.count == maxFollowUps { break }
        }
        return questions
    }
}
