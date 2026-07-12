//
//  SelfQueryGate.swift
//  M1K3Chat
//
//  The self-query router (prompt-hardening v2, code-side ticket 1). Persona
//  rule 3 says questions about M1K3 itself are answered from persona alone —
//  NEVER via retrieval. The prompt states the policy; this gate enforces it:
//  on a self-query turn the responder skips retrieval entirely and withholds
//  the corpus-reaching tools, so the model cannot call what it is never
//  offered. A soft prior is the wrong home for a hard gate.
//
//  This is deliberately NOT the pre-generation intent router rejected on
//  2026-06-12 ("brittle both ways") — that one decided retrieve-vs-not for
//  EVERY question. This gate is a narrow, precision-first classifier for the
//  leak class only: prompt/config/instruction probes, passphrase asks, and
//  corpus nouns aimed at M1K3 itself. A miss costs nothing new (the prompt
//  rule still defends, as before); a false positive robs one turn of its
//  grounding — so every pattern requires a self-directed shape, and plain
//  topical questions ("what do your notes say about the conveyor seal?")
//  stay retrievable. The boundary cases are pinned in SelfQueryGateTests.
//
//  Signed: Kev + claude-fable-5, 2026-07-12, Confidence 0.85, Prior: Unknown
//  Context: docs/prompt-hardening-v2.md code-side ticket 1; the eval-side
//  guard is ChatEvalFixtures.security (selfquery-notes et al.).
//

import Foundation

public enum SelfQueryGate {
    /// Tools a self-query turn never sees: the three corpus-reaching tools
    /// plus `lookup_fact`, the retrieval tools persona rule 3 names. Web and
    /// utility tools stay — they cannot surface the corpus or the prompt.
    /// The names are strings because M1K3Chat cannot link the tool modules
    /// (tools are injected by the app layer); `withheldNamesMatchLiveTools`
    /// in SelfQueryGateTests pins them against the real declarations.
    public static let withheldToolNames: Set<String> = [
        "search_knowledge", "list_documents", "get_document", "lookup_fact",
    ]

    /// Substrings that gate on their own: credential probes and the prompt's
    /// own scaffold text (the sentence-completion attack opens with it).
    private static let unconditionalMarkers = [
        "passphrase",
        "you are m1k3",
        "absolute rules",
        "you were told",
    ]

    /// A possessive aimed at M1K3's own machinery: "your (full) configuration",
    /// "its system prompt", "your own wiring". Up to two intervening words so
    /// "your full configuration" and "your own instructions" match while a
    /// longer, different-shaped sentence does not.
    private static var possessiveIntrospection: Regex<Substring> {
        #/\b(?:your|its|m1k3's)\s+(?:\w+\s+){0,2}(?:prompt|prompts|instructions|configuration|config|rules|wiring|persona|guidelines|directives)\b/#
    }

    /// "The system prompt/message" — the definite article marks the probe
    /// ("show me the system prompt you were given"); "a system prompt" in a
    /// how-do-I question does not gate.
    private static var definiteSystemPrompt: Regex<Substring> {
        #/\bthe\s+system\s+(?:prompt|message)\b/#
    }

    /// A corpus noun in M1K3's possession ("your notes", "your knowledge") —
    /// gates ONLY when the question also aims at M1K3 itself (see selfTarget).
    /// Without the self-target this is the user's ingested corpus and stays
    /// fully retrievable.
    private static var possessiveCorpusNoun: Regex<Substring> {
        #/\byour\s+(?:\w+\s+){0,2}(?:notes|memories|documents|docs|knowledge|files|database|index)\b/#
    }

    /// The self-directed aim that turns a corpus-noun ask into a self-query:
    /// "about you", "about yourself", "on yourself", or bare "yourself".
    private static var selfTarget: Regex<Substring> {
        #/\b(?:about|on|regarding|concerning)\s+(?:you|yourself)\b|\byourself\b/#
    }

    /// "Your internal/diagnostic/QA …" — the internal-notes leak shape, where
    /// the adjective itself is the self-target ("your internal QA notes").
    private static var possessiveInternal: Regex<Substring> {
        #/\byour\s+(?:internal|diagnostic|diagnostics|qa)\b/#
    }

    /// True when the question is about M1K3 itself — its prompt, config,
    /// rules, credentials, or notes-about-itself — and must be answered from
    /// persona with no retrieval.
    public static func isSelfQuery(_ question: String) -> Bool {
        let text = question.lowercased()
        if unconditionalMarkers.contains(where: { text.contains($0) }) { return true }
        if text.contains(possessiveIntrospection) { return true }
        if text.contains(definiteSystemPrompt) { return true }
        if text.contains(possessiveInternal) { return true }
        if text.contains(possessiveCorpusNoun), text.contains(selfTarget) { return true }
        return false
    }
}
