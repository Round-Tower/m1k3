//
//  HistoryWindow.swift
//  M1K3Chat
//
//  Minimal chat memory: until now every turn was independent — the model never
//  saw prior turns, so follow-up questions fell flat. This renders a TIGHTLY
//  capped replay of recent turns into the grounding prompt. The caps are the
//  point: replay grows prefill (which fights the TTFT work), so the window
//  stays small — last few turns, each truncated, answer text only (never
//  reasoning, never sources).
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.8 (caps are a
//  starting point; the ttft logs say what replay actually costs). Prior: Unknown
//

import Foundation

/// One prior exchange turn, flattened for replay.
public struct ChatTurn: Sendable, Equatable {
    public enum Role: Sendable, Equatable {
        case user
        case assistant
    }

    public let role: Role
    public let text: String

    public init(role: Role, text: String) {
        self.role = role
        self.text = text
    }
}

enum HistoryWindow {
    /// Most recent turns kept (≈3 user/assistant exchanges).
    static let maxTurns = 6
    /// Per-turn character cap — enough to carry the thread, not a transcript.
    static let maxCharsPerTurn = 400

    /// Render the replay block for the grounding prompt, or nil when there is
    /// no usable history.
    static func render(_ turns: [ChatTurn]) -> String? {
        let usable = turns
            .map { ChatTurn(role: $0.role, text: $0.text.trimmingCharacters(in: .whitespacesAndNewlines)) }
            .filter { !$0.text.isEmpty }
            .suffix(maxTurns)
        guard !usable.isEmpty else { return nil }

        let lines = usable.map { turn -> String in
            let label = turn.role == .user ? "USER" : "M1K3"
            return "\(label): \(truncate(turn.text))"
        }
        return "CONVERSATION SO FAR (context for the new question):\n"
            + lines.joined(separator: "\n")
    }

    private static func truncate(_ text: String) -> String {
        guard text.count > maxCharsPerTurn else { return text }
        return text.prefix(maxCharsPerTurn).trimmingCharacters(in: .whitespaces) + "…"
    }
}
