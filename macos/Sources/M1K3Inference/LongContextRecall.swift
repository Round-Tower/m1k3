//
//  LongContextRecall.swift
//  M1K3Inference
//
//  Needle-in-a-haystack check for the per-model eval harness. Every other
//  eval prompt is short, so none of them exercise a LONG KV cache — exactly
//  where quantized-KV (allow-listed families) or cache-rotation (everything
//  else) quality loss would hide. The needle lands EARLY in the prompt: the
//  oldest cache region is the first to degrade under either mechanism.
//
//  Pure and deterministic; the generation side lives in SelfTest.evalModel
//  like every other check.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.8, Prior: Unknown
//

import Foundation

public enum LongContextRecall {
    /// The fact the model must carry across the whole context.
    public static let needleCode = "KESTREL-47"

    /// Deterministic prompt: needle early, varied filler log entries pushing
    /// the context to a few thousand tokens, question last.
    public static func prompt(fillerSentences: Int = 150) -> String {
        let header = """
        You are reading a maintenance log. One question follows at the end.

        Maintenance note: the access code for the auxiliary pump is \(needleCode). \
        Remember it — it is asked about later.

        """
        let filler = (1 ... max(fillerSentences, 1)).map { index in
            "Log entry \(index): conveyor \(index % 9 + 1) ran at \(70 + index % 25) percent load; "
                + "bearing temperature held steady through shift \(index % 3 + 1); "
                + "lubrication interval \(index % 14 + 1) of 14; no faults recorded."
        }.joined(separator: "\n")
        let question = """


        Question: What is the access code for the auxiliary pump? \
        Reply with the code only.
        """
        return header + filler + question
    }

    /// True when the answer carries the code in any format models actually
    /// echo (case, hyphen/space/backtick variations).
    public static func passes(_ answer: String) -> Bool {
        let flattened = answer.lowercased().filter(\.isAlphanumeric)
        let needle = needleCode.lowercased().filter(\.isAlphanumeric)
        return flattened.contains(needle)
    }
}

private extension Character {
    var isAlphanumeric: Bool {
        isLetter || isNumber
    }
}
