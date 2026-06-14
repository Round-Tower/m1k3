//
//  AgentLog.swift
//  M1K3Agent
//
//  Unified-logging plumbing for the agent stack. Watch a whole turn live:
//
//      log stream --predicate 'subsystem == "app.m1k3"' --level debug
//
//  (or filter the subsystem in Console.app). Content is logged as bounded
//  one-line PREVIEWS, deliberately `.public` so Kev can actually diagnose his
//  own machine — these logs never leave the device, same trust boundary as
//  the knowledge store itself. Drop to `.private` before any telemetry ever
//  exists (it shouldn't).
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.85, Prior: Unknown

import Foundation
import os

/// One subsystem for every M1K3 module, so a single predicate catches all.
public enum M1K3Log {
    public static let subsystem = "app.m1k3"

    /// The ReAct loop: thoughts, actions, observations, conclusion paths.
    public static let agentLoop = Logger(subsystem: subsystem, category: "agent-loop")
}

/// Bounded, single-line content previews for log lines.
public enum LogPreview {
    /// Compiled once — this runs on every agent log line (thoughts,
    /// observations, conclusions); per-call NSRegularExpression compilation
    /// via `.regularExpression` options would be pure overhead.
    /// `nonisolated(unsafe)`: this toolchain still treats `Regex<Substring>`
    /// as non-Sendable (verified: the bare `static let` is a compile error).
    /// A literal with no transform closures is immutable, so unsafe is sound.
    private nonisolated(unsafe) static let whitespaceRun = /\s+/

    /// Collapse all whitespace runs to single spaces and cap the length.
    public static func preview(_ text: String, max cap: Int = 120) -> String {
        let flattened = text
            .replacing(whitespaceRun, with: " ")
            .trimmingCharacters(in: .whitespaces)
        guard flattened.count > cap else { return flattened }
        return flattened.prefix(cap).trimmingCharacters(in: .whitespaces) + "…"
    }
}
