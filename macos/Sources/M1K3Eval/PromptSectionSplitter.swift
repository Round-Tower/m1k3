//
//  PromptSectionSplitter.swift
//  M1K3Eval
//
//  Splits a REAL assembled prompt into named sections so the prompt-size
//  instrument can say WHERE the tokens went, not just how many there were.
//
//  WHY SPLIT AN INTERCEPTED PROMPT rather than re-assemble one from the
//  responder's parts: a reconstruction is a second implementation of prompt
//  assembly, and the moment it drifts from AgentRAGResponder the instrument
//  starts confidently measuring something the model never saw. Intercepting
//  what actually reaches the provider cannot drift. The cost is that the
//  breakdown keys on section headers — so `PromptMarker.live` is pinned
//  against the real prompt in a test, and a reworded header fails loudly.
//
//  The partition is exact by construction (each section runs to the start of
//  the next), so the component bytes always re-sum to the whole prompt — the
//  invariant that makes PromptSizeMeasurement's totals trustworthy.
//
//  Signed: Kev + claude-opus-4-8, 2026-07-19, Confidence 0.8 (TDD'd red-first
//  incl. the exact-partition, absent-is-absent, and first-occurrence
//  directions). Prior: Unknown
//

import Foundation

/// A named section header to split on.
public struct PromptMarker: Sendable, Equatable {
    public let name: String
    public let marker: String

    public init(name: String, marker: String) {
        self.name = name
        self.marker = marker
    }
}

/// One slice of an assembled prompt. `text` includes the header line itself,
/// so the slices partition the prompt exactly.
public struct PromptSection: Sendable, Equatable {
    public let name: String
    public let text: String

    public init(name: String, text: String) {
        self.name = name
        self.text = text
    }
}

public enum PromptSectionSplitter {
    /// Everything ahead of the first recognised header — persona, tool spec,
    /// the goal/context line.
    public static let preambleName = "preamble"

    /// Split `prompt` into sections. A marker that isn't present yields NO
    /// section, so the report can distinguish "absent" from "present and
    /// empty".
    ///
    /// Two rules keep the user's own text from hijacking the split — which
    /// matters because the KNOWLEDGE block carries retrieved document content
    /// VERBATIM, and a chunk that captures a later header would hand that
    /// block's bytes to the wrong component, under-reporting precisely the
    /// thing this instrument exists to watch:
    ///
    /// 1. **Line-anchored.** A marker only matches at the start of a line, so
    ///    prose like "the RULES of chess" inside a chunk is not a header.
    /// 2. **Sequential.** Each marker is searched only after the previous
    ///    match. The cost is a contract: `markers` MUST be declared in render
    ///    order (see `PromptMarker.live`) — a marker is invisible once a later
    ///    one has matched.
    ///
    /// Honest limit: a retrieved chunk containing a line that genuinely begins
    /// with a header word can still fool this. Marker-based splitting cannot
    /// fully separate structure from quoted content; the totals stay exact
    /// regardless (the partition is by construction), only the attribution
    /// between components could shift.
    public static func split(_ prompt: String, markers: [PromptMarker]) -> [PromptSection] {
        guard !prompt.isEmpty else { return [] }

        var found: [(name: String, start: String.Index)] = []
        var searchFrom = prompt.startIndex
        for marker in markers {
            guard
                let start = firstLineAnchoredMatch(
                    of: marker.marker, in: prompt, from: searchFrom
                )
            else { continue }
            found.append((marker.name, start))
            searchFrom = prompt.index(start, offsetBy: marker.marker.count)
        }

        guard let first = found.first else {
            return [PromptSection(name: preambleName, text: prompt)]
        }

        var sections: [PromptSection] = []
        // No empty preamble when the prompt opens directly on a marker.
        if first.start > prompt.startIndex {
            sections.append(
                PromptSection(
                    name: preambleName,
                    text: String(prompt[prompt.startIndex ..< first.start])
                )
            )
        }
        for (offset, entry) in found.enumerated() {
            let end = offset + 1 < found.count ? found[offset + 1].start : prompt.endIndex
            sections.append(
                PromptSection(name: entry.name, text: String(prompt[entry.start ..< end]))
            )
        }
        return sections
    }

    /// First occurrence of `needle` at or after `from` that begins a LINE —
    /// i.e. sits at the very start of the prompt or immediately after a
    /// newline. Prose mentions inside a retrieved chunk are skipped.
    ///
    /// Worst case is O(n·m) (a false-positive-heavy haystack re-scans from
    /// each near-miss) — acceptable at real prompt sizes (single-digit
    /// KB, a handful of markers); this is not a hot loop and no algorithmic
    /// change is warranted for the inputs this instrument actually sees.
    private static func firstLineAnchoredMatch(
        of needle: String, in haystack: String, from: String.Index
    ) -> String.Index? {
        var searchFrom = from
        while
            let range = haystack.range(of: needle, range: searchFrom ..< haystack.endIndex)
        {
            if range.lowerBound == haystack.startIndex
                || haystack[haystack.index(before: range.lowerBound)] == "\n"
            {
                return range.lowerBound
            }
            // Advance past this false positive; a needle can't overlap itself
            // meaningfully here, so stepping one character is enough and keeps
            // the loop obviously terminating.
            searchFrom = haystack.index(after: range.lowerBound)
        }
        return nil
    }
}

public extension PromptMarker {
    /// The live prompt's section headers, in the order AgentRAGResponder
    /// renders them (history replay, then the grounding body's KNOWLEDGE →
    /// WHAT I KNOW ABOUT YOU → RULES).
    ///
    /// Deliberately matched on a PREFIX of each header, not the full sentence:
    /// the tails carry interpolated device nouns (HostPlatform) and parenthetical
    /// copy that shifts with wording passes. These strings are duplicated from
    /// their source — M1K3Eval must not depend on M1K3Chat — and pinned against
    /// the real rendered prompt in PromptMarkerLiveTests so the duplication
    /// can't rot silently.
    static let live: [PromptMarker] = [
        PromptMarker(name: "history", marker: "CONVERSATION SO FAR"),
        PromptMarker(name: "knowledge", marker: "KNOWLEDGE ("),
        PromptMarker(name: "memories", marker: "WHAT I KNOW ABOUT YOU"),
        PromptMarker(name: "rules", marker: "RULES"),
    ]
}
