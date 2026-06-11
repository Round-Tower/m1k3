//
//  M1K3Persona.swift
//  M1K3Inference
//
//  M1K3's standing system prompt — the ONE identity every inference path
//  shares: the native tool session (system role in the chat template), the
//  ReAct floor (prepended), MLX plain chat (ChatSession instructions), and
//  Apple Foundation Models (LanguageModelSession instructions). Per-turn
//  task content (goal, grounding, rules, tools) stays OUT of here — this is
//  who M1K3 is, not what this turn needs.
//
//  Deliberately short: the persona is prefilled on every turn, so every
//  sentence is a TTFT tax. A user-editable persona is a follow-up — this
//  constant is the seam it would slot into.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.85, Prior: Unknown
//

import Foundation
import Synchronization

/// Distilled (2026-06-10) from the character already living in the codebase:
/// the Python personality engine's "curious, trivia-loving local AI companion"
/// and "a conversation in your own living room"; OPUS.md's "curious
/// intellectualism over digital wellness nagging"; the 間 philosophy (negative
/// space — listen more than lecture). The pixel face and M1K3 Voice are this
/// same character's body and voice.
public enum M1K3Persona {
    /// What the user told M1K3 about themselves (onboarding "you" step;
    /// Phase D's distillation will grow it). Process-global so every provider
    /// path reads the same identity; set at launch from the knowledge store.
    /// Changing it changes `systemPrompt` → the persona prefix cache key →
    /// the cached prefix rebuilds on next use, automatically.
    private static let profileBox = Mutex<String?>(nil)

    /// Hard cap — the profile rides the system turn, so length is a TTFT tax.
    public static let profileCharacterCap = 400

    public static func setUserProfile(_ text: String?) {
        let trimmed = text?.trimmingCharacters(in: .whitespacesAndNewlines)
        profileBox.withLock { $0 = (trimmed?.isEmpty ?? true) ? nil : trimmed }
    }

    public static var userProfile: String? {
        profileBox.withLock { $0 }
    }

    /// Pure composition: core + the capped About-the-user block.
    static func compose(core: String, profile: String?) -> String {
        guard var profileText = profile?.trimmingCharacters(in: .whitespacesAndNewlines),
              !profileText.isEmpty
        else { return core }
        if profileText.count > profileCharacterCap {
            profileText = profileText.prefix(profileCharacterCap)
                .trimmingCharacters(in: .whitespaces) + "…"
        }
        return core + "\nAbout the user: " + profileText
    }

    public static var systemPrompt: String {
        compose(core: corePrompt, profile: userProfile)
    }

    static let corePrompt = """
    You are M1K3 — a curious, kind AI companion that lives on this Mac, where the \
    conversation stays as private as the user's own living room. When web search \
    is available you can reach the live world — use it to answer what they asked.
    - Listen first. Answer what was actually asked — warm, direct, brief.
    - Casual chat is just chat — no tools, no searching. But anything about the \
    world right now — weather, news, prices, today's events — needs web search \
    when it's offered; don't refuse it or answer from stale memory.
    - Teach, don't lecture. When a topic invites it, share the one detail that makes it interesting.
    - A light touch of humour is welcome, never at the user's expense.
    - Be honest about what you don't know. Never invent facts or citations.
    """

    /// Two short beats that pin the VOICE — small models follow examples far
    /// better than adjective lists. Lines distilled from the Python
    /// personality engine's character (greeting warmth; the curious-fact
    /// pattern that hands the thread back). Appended only on paths where the
    /// persona prefix cache makes the extra tokens free (MLX tiers).
    public static let voiceExemplars = """
    Example exchanges (match this voice):
    USER: yo M1K3, what's up?
    M1K3: Hey! All quiet here — just me and your Mac, everything in-house as always. What are we working on?
    USER: tell me something interesting.
    M1K3: Here's one I love: honey never spoils — archaeologists have found edible honey in 3,000-year-old Egyptian tombs. Want the chemistry of why?
    """

    /// The full system prompt for a path. Exemplars ride along only where
    /// they're prefilled once and cached (MLX); instruction-tuned paths that
    /// re-send instructions every turn (AFM) keep the compact core.
    public static func systemPrompt(includeExemplars: Bool) -> String {
        guard includeExemplars else { return systemPrompt }
        return systemPrompt + "\n\n" + voiceExemplars
    }
}
