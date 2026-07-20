//
//  PromptSizeReport.swift
//  M1K3Eval
//
//  The prompt-size instrument's pure half — what a measurement IS and how it
//  renders. The measuring itself needs a real tokenizer, so it rides SelfTest
//  (PromptSizeStage) like every other on-device arm; this is the part that can
//  be TDD'd in seconds.
//
//  WHY THIS EXISTS. Every context guarantee in the app currently rests on an
//  ESTIMATE that has never been checked against a tokenizer:
//  HistoryBudgetPolicy assumes 3.5 chars/token (its own header names real token
//  counting as a deferred [SPIKE]), DocumentChunker implies 4, and
//  AppEnvironment+ChatHistory reserves the non-history prompt at 3000 tokens
//  with grounding itemised at "~1100" — while AgentRAGResponder.groundingBody
//  interpolates retrieved chunks VERBATIM and untruncated. On Big (gemma-4-12B)
//  the failure mode of getting that wrong is silent: a RotatingKVCache(8192)
//  rotates the persona/grounding head out during prefill and answers
//  off-persona with no error. So the measurement has to come before the fix.
//
//  DESIGN NOTE — nil beats zero. A tier with no exposed tokenizer (AFM/mini)
//  yields nil token counts, and every derived figure stays nil rather than
//  collapsing to 0. A confident "0 tokens" is exactly the sounds-right-is-wrong
//  failure this instrument exists to prevent, and the same class of trap that
//  parked the voice LoRA.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-19, Confidence 0.8 (pure arithmetic +
//  rendering, TDD'd red-first in PromptSizeReportTests incl. the nil-not-zero
//  and inclusive-boundary directions; the on-device numbers it consumes are the
//  named verify-owed). Prior: Unknown
//
//  Review: Kev + claude-fable-5, 2026-07-20, Confidence 0.85 — an on-device run
//  (gemma-4-12B) showed `measuredCharsPerToken` reading ~1.1: the 0-byte
//  `persona+tools (KV-seed)`/`template` components (real tokens, never
//  rendered as text) were folding into the ratio and tanking it, making a
//  ~4.4 real-text ratio look like a scary under-reservation. Fixed red-first
//  (`measuredCharsPerTokenExcludesStructuralComponents` /
//  `measuredCharsPerTokenNilWhenAllStructural` in PromptSizeReportTests):
//  the ratio now sums bytes/tokens over ONLY components with `bytes > 0`.
//  `totalTokens`/`exceedsReserve`/`contextFraction` are UNTOUCHED — the seed
//  is real KV context and must still count toward the reserve/window fit;
//  only the text-calibration ratio excludes structural, textless tokens.
//

import Foundation

/// One labelled slice of an assembled prompt (persona, tools, grounding,
/// history, images…). `tokens` is nil when the tier exposes no tokenizer.
public struct PromptComponentSize: Sendable, Equatable {
    public let name: String
    public let bytes: Int
    public let tokens: Int?

    public init(name: String, bytes: Int, tokens: Int?) {
        self.name = name
        self.bytes = bytes
        self.tokens = tokens
    }
}

/// One assembled prompt, measured. `reserveTokens` is what the app *promised*
/// this prompt would cost (the figure a budget subtracts from the window);
/// `windowTokens` is the tier's real context window.
public struct PromptSizeMeasurement: Sendable, Equatable {
    public let label: String
    public let components: [PromptComponentSize]
    public let reserveTokens: Int?
    public let windowTokens: Int?

    public init(
        label: String,
        components: [PromptComponentSize],
        reserveTokens: Int?,
        windowTokens: Int?
    ) {
        self.label = label
        self.components = components
        self.reserveTokens = reserveTokens
        self.windowTokens = windowTokens
    }

    public var totalBytes: Int {
        components.reduce(0) { $0 + $1.bytes }
    }

    /// nil if ANY component is untokenised — a partial sum would understate the
    /// prompt while looking like a measurement.
    public var totalTokens: Int? {
        var sum = 0
        for component in components {
            guard let tokens = component.tokens else { return nil }
            sum += tokens
        }
        return sum
    }

    /// Whether the measured prompt costs more than the app reserved for it.
    /// nil when either side is unknown. The boundary is inclusive: spending
    /// exactly the reserve is honouring it, not breaking it.
    public var exceedsReserve: Bool? {
        guard let totalTokens, let reserveTokens else { return nil }
        return totalTokens > reserveTokens
    }

    /// How far over the reserve we actually went (0 when within it).
    public var reserveOverrunTokens: Int? {
        guard let totalTokens, let reserveTokens else { return nil }
        return max(0, totalTokens - reserveTokens)
    }

    /// Share of the tier's window this prompt consumes before a single token is
    /// generated. On a rotating-KV tier this is the number that matters.
    public var contextFraction: Double? {
        guard let totalTokens, let windowTokens, windowTokens > 0 else { return nil }
        return Double(totalTokens) / Double(windowTokens)
    }

    /// The ratio the char≈token estimates should be tuned to for this corpus —
    /// the [SPIKE] figure. nil when unmeasurable.
    ///
    /// A TEXT-calibration figure, deliberately over ONLY the components that
    /// were actually rendered as text: "template" (the chat-template wrapper's
    /// attributed cost) and "persona+tools (KV-seed)" (Big's native persona
    /// prefix, prefilled straight into the KV cache and never a string at
    /// all) both pair real tokens with `bytes: 0` — including them tanks the
    /// ratio to ~1.1 (live: a 469-token/2056-byte prompt read as 1.11 once its
    /// 1380-token 0-byte seed was folded in), which reads like a scary
    /// under-reservation that isn't real. `totalTokens` and the reserve-fit
    /// check are UNCHANGED by this exclusion — the seed is real KV context and
    /// must still count toward "does this fit the window/reserve"; only the
    /// chars/token calibration figure ignores structural, textless tokens.
    public var measuredCharsPerToken: Double? {
        var textBytes = 0
        var textTokens = 0
        for component in components where component.bytes > 0 {
            guard let tokens = component.tokens else { return nil }
            textBytes += component.bytes
            textTokens += tokens
        }
        guard textTokens > 0 else { return nil }
        return Double(textBytes) / Double(textTokens)
    }
}

public enum PromptSizeReport {
    static let missing = "—"

    /// A per-measurement block: the component breakdown, then the totals line
    /// with the reserve verdict. Rendered as text so it can ride SelfTest's
    /// append-only log alongside the CHATEVAL matrix.
    public static func table(_ measurements: [PromptSizeMeasurement]) -> String {
        var lines = ["=== PROMPT SIZE (bytes / real tokens) ==="]
        for measurement in measurements {
            lines.append("")
            lines.append(measurement.label)
            for component in measurement.components {
                lines.append(
                    "  \(pad(component.name, 14))"
                        + "\(pad(String(component.bytes), 8))B  "
                        + "\(pad(tokenText(component.tokens), 8))tok"
                )
            }
            lines.append(totalsLine(for: measurement))
            if let fraction = measurement.contextFraction {
                lines.append(
                    "  window        \(percent(fraction)) of "
                        + "\(measurement.windowTokens ?? 0)tok"
                )
            }
            if let ratio = measurement.measuredCharsPerToken {
                lines.append("  chars/token   \(String(format: "%.2f", ratio))")
            }
        }
        return lines.joined(separator: "\n")
    }

    private static func totalsLine(for measurement: PromptSizeMeasurement) -> String {
        var line =
            "  TOTAL         "
                + "\(pad(String(measurement.totalBytes), 8))B  "
                + "\(pad(tokenText(measurement.totalTokens), 8))tok"
        if let reserve = measurement.reserveTokens {
            line += "  reserved=\(reserve)tok"
            if measurement.exceedsReserve == true, let over = measurement.reserveOverrunTokens {
                line += "  ⚠️ OVER by \(over)tok"
            }
        }
        return line
    }

    /// nil renders as an em dash — never "0", which would read as a measurement.
    private static func tokenText(_ tokens: Int?) -> String {
        guard let tokens else { return missing }
        return String(tokens)
    }

    private static func percent(_ fraction: Double) -> String {
        String(format: "%.0f%%", fraction * 100)
    }

    private static func pad(_ text: String, _ width: Int) -> String {
        text.count >= width ? text : text + String(repeating: " ", count: width - text.count)
    }
}
