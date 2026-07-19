//
//  PromptContext.swift
//  M1K3Chat
//
//  A compact per-turn "what's true right now" line for the agent grounding.
//
//  Two honest facts small models otherwise get wrong:
//    • the PRECISE date — weekday + day. The cached persona prefix carries only
//      month+year (kept coarse so the persona-prefix KV cache lives a whole month,
//      see M1K3Persona.currentDateLine), so without this the model can't say what
//      DAY it is — it guesses, or pleads "no real-time data".
//    • WHICH brain is answering. mini/lil/big all share one persona, so the
//      model otherwise can't honestly answer "which model are you?".
//
//  It lives in the PER-TURN grounding, NOT the cached persona prefix — so it
//  never busts the persona-prefix KV cache (a TTFT tax) or drifts the persona-LoRA
//  baseline (trained against the current prefix). Pure + tested; the responder
//  prepends it with `Date()` and the live brain name at turn time.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-21, Confidence 0.85 (textbook date
//  formatting; the cache/LoRA-safe placement is the load-bearing call). Prior: Unknown.

import Foundation
import M1K3Inference

public enum PromptContext {
    /// The grounding line for `now`, naming `brainName` if known. English names,
    /// host-locale-independent. An empty/blank brain name drops the brain clause.
    public static func line(now: Date, brainName: String) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX") // stable English names
        formatter.dateFormat = "EEEE, d MMMM yyyy"
        let date = formatter.string(from: now)
        let brain = brainName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !brain.isEmpty else {
            return "Right now (true for this turn): it's \(date)."
        }
        return "Right now (true for this turn): it's \(date), and you're \(brain) — running entirely on \(HostPlatform.thisDevice)."
    }
}
