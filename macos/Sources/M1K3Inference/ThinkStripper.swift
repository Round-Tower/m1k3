import Foundation

/// Strip chain-of-thought `<think>…</think>` blocks from model output.
///
/// Handles matched pairs, Qwen3.5's lone `</think>` close (everything before
/// it is scratchpad), multiple blocks in one response, and plain text
/// passthrough. Shared by M1K3Calls (call summaries) and M1K3Eval (scoring).
public enum ThinkStripper {
    public static func strip(_ text: String) -> String {
        var working = text
        if let close = working.range(of: "</think>") {
            working = String(working[close.upperBound...])
        }
        working = working.replacingOccurrences(
            of: "<think>.*?</think>", with: "", options: .regularExpression
        )
        return working.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
