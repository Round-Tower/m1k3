//
//  GroundingBudget.swift
//  M1K3Knowledge
//
//  Caps injected grounding (KNOWLEDGE chunks + the WHAT-I-KNOW-ABOUT-YOU
//  memory block) to a token budget BEFORE AgentRAGResponder ever renders it
//  into a prompt. Both lanes are injected VERBATIM and UNTRUNCATED today
//  (AgentRAGResponder.groundingBody), so a wide retrieval hit, a dense memory
//  set, or simply more chunks than usual can silently blow the prompt's fixed
//  non-history reserve.
//
//  The concrete failure this closes (measured on-device, PR #65's prompt-size
//  instrument, 2026-07-19/20): on gemma-4-12B ("Big"), the grounded-Q worst
//  case prompt landed at 2998 of a 3000-token reserve — 2 tokens of headroom.
//  One more chunk, a denser memory hit, or plain tokenization variance tips
//  it past 3000, and gemma-4's `RotatingKVCache(8192)` silently rotates the
//  persona/grounding head out of the window mid-turn. There is no error —
//  M1K3 just answers off-persona.
//
//  `fit` is pure and deterministic: same inputs, same `countTokens` answers,
//  same output, every time. No logging here — the wiring site (a `.notice`
//  breadcrumb, once, only when something actually changed) is
//  AgentRAGResponder's job, not this policy's.
//
//  Signed: Kev + claude-fable-5, 2026-07-20, Confidence 0.85 (arithmetic
//  fully pinned by GroundingBudgetTests with a deterministic char-count
//  fake; the real on-device re-measure against a live MLX tokenizer — does
//  grounded-Q actually drop below 3000 now? — is owed, not run here).
//  Prior: Unknown
//

import Foundation

public enum GroundingBudget {
    /// The token budget for the COMBINED grounding (KNOWLEDGE chunks +
    /// memory facts) — one slice of the app's fixed 3000-token non-history
    /// reserve (`AppEnvironment.historyReserveTokens`), whose own comment
    /// already earmarked ~1100 for "grounding chunks" as a design-time
    /// guess. PR #65 measured the REAL fixed parts on-device — persona+tools
    /// KV-seed ~1380 + rules ~338 + preamble ~71 + template ~14 ≈ 1800 —
    /// leaving ~1200 of the 3000 reserve actually free for grounding. 1100
    /// keeps a ~100-token margin below that for tokenization variance (a
    /// denser chunk, a longer citation label) rather than spending every
    /// last token the measurement implies is available.
    public static let defaultTokenBudget = 1100

    /// Fit `chunks` and `memories` inside `tokenBudget`, sharing ONE budget —
    /// doc chunks (the larger, more variable lane, and the one actually
    /// cited) are filled first in rank order, then memories against whatever
    /// remains.
    ///
    /// - `countTokens` returning `nil` means the active provider has no
    ///   tokenizer (Apple Foundation Models / Mini self-manage their own
    ///   context windows) — the cap is a NO-OP, inputs pass through
    ///   unchanged. Checked ONCE, on the very first candidate unit: a nil
    ///   there means every later call would also be nil (same provider,
    ///   same turn), so nothing further is measured.
    /// - Whole units are kept in rank order until the next would exceed the
    ///   remaining budget, then the rest are dropped — no mid-unit
    ///   truncation, except for the very first unit overall (below).
    /// - At least one unit always survives when anything was retrieved: if
    ///   the single highest-ranked unit alone exceeds the WHOLE budget, it
    ///   is kept but its content is truncated to fit, with a visible
    ///   " …[truncated]" tail marker. A chunk's citation label sits at the
    ///   HEAD of its rendered text (rendered separately downstream, from
    ///   fields this policy never touches), so tail-truncating `content`
    ///   never loses it.
    public static func fit(
        chunks: [ChunkHit],
        memories: [ChunkHit],
        tokenBudget: Int,
        countTokens: (String) async -> Int?
    ) async -> (chunks: [ChunkHit], memories: [ChunkHit]) {
        guard !chunks.isEmpty || !memories.isEmpty else {
            return (chunks, memories)
        }

        let units = chunks.enumerated().map { Unit.chunk($1, index: $0 + 1) }
            + memories.map { Unit.memory($0) }

        // The gate measurement: nil here means this turn's provider has no
        // tokenizer — no-op, the whole inputs pass through untouched.
        guard let gateText = units.first?.renderedText,
              let gateCost = await countTokens(gateText)
        else {
            return (chunks, memories)
        }

        var remaining = tokenBudget
        var keptChunks: [ChunkHit] = []
        var keptMemories: [ChunkHit] = []

        for (offset, unit) in units.enumerated() {
            let cost = offset == 0 ? gateCost : (await countTokens(unit.renderedText) ?? 0)
            let isTopOverallUnit = keptChunks.isEmpty && keptMemories.isEmpty
            if cost > remaining {
                if isTopOverallUnit {
                    // Never return empty grounding when something was
                    // retrieved — truncate the single top unit's tail to fit.
                    switch unit {
                    case let .chunk(hit, index):
                        keptChunks.append(
                            await truncatedChunk(
                                hit, index: index, tokenBudget: remaining, countTokens: countTokens
                            )
                        )
                    case let .memory(hit):
                        keptMemories.append(
                            await truncatedMemory(hit, tokenBudget: remaining, countTokens: countTokens)
                        )
                    }
                }
                break
            }
            switch unit {
            case let .chunk(hit, _): keptChunks.append(hit)
            case let .memory(hit): keptMemories.append(hit)
            }
            remaining -= cost
        }
        return (keptChunks, keptMemories)
    }

    /// Sum of every unit's rendered-and-counted cost, using the SAME
    /// rendering `fit` walks — for the wiring site's before→after breadcrumb
    /// only. Not called by `fit` itself, which counts unit-by-unit and stops
    /// early on purpose; a `nil` per-unit count reads as 0 here (this total
    /// is diagnostic, not budget-critical).
    public static func totalTokens(
        chunks: [ChunkHit], memories: [ChunkHit], countTokens: (String) async -> Int?
    ) async -> Int {
        var total = 0
        for (offset, chunk) in chunks.enumerated() {
            total += await countTokens(Unit.chunk(chunk, index: offset + 1).renderedText) ?? 0
        }
        for memory in memories {
            total += await countTokens(Unit.memory(memory).renderedText) ?? 0
        }
        return total
    }

    /// One candidate for the budget walk: a KNOWLEDGE chunk (numbered, with
    /// its citation label) or a memory fact (bulleted) — rendered EXACTLY the
    /// way `AgentRAGResponder.groundingBody` renders it, so the measured cost
    /// matches the real prompt almost byte-for-byte (minus the "\n\n" join
    /// separators between sections/units — a few tokens the 1100 budget's
    /// ~100-token margin already covers).
    private enum Unit {
        case chunk(ChunkHit, index: Int)
        case memory(ChunkHit)

        var renderedText: String {
            switch self {
            case let .chunk(hit, index):
                "\(index). \(ChatPromptBuilder.citationLabel(for: hit))\n\(hit.content)"
            case let .memory(hit):
                "- \(hit.content)"
            }
        }
    }

    /// Visible marker for a tail-truncated unit — appended, never hidden,
    /// so a truncated grounding item never silently masquerades as complete.
    private static let truncationMarker = " …[truncated]"

    /// Truncate `chunk`'s content tail to fit `tokenBudget`, keeping its
    /// numbered citation-label HEAD intact (rendered from `itemTitle`/
    /// `heading`, never touched here — only `content` is mutated).
    private static func truncatedChunk(
        _ chunk: ChunkHit, index: Int, tokenBudget: Int, countTokens: (String) async -> Int?
    ) async -> ChunkHit {
        let head = "\(index). \(ChatPromptBuilder.citationLabel(for: chunk))\n"
        let headCost = await countTokens(head) ?? 0
        let markerCost = await countTokens(truncationMarker) ?? 0
        let available = max(0, tokenBudget - headCost - markerCost)
        var truncated = chunk
        truncated.content = await truncatedContent(
            chunk.content, available: available, countTokens: countTokens
        ) + truncationMarker
        return truncated
    }

    /// Truncate `memory`'s content tail to fit `tokenBudget` — no head to
    /// preserve beyond the one-character "- " bullet, folded into the search.
    private static func truncatedMemory(
        _ memory: ChunkHit, tokenBudget: Int, countTokens: (String) async -> Int?
    ) async -> ChunkHit {
        let bulletCost = await countTokens("- ") ?? 0
        let markerCost = await countTokens(truncationMarker) ?? 0
        let available = max(0, tokenBudget - bulletCost - markerCost)
        var truncated = memory
        truncated.content = await truncatedContent(
            memory.content, available: available, countTokens: countTokens
        ) + truncationMarker
        return truncated
    }

    /// The largest character prefix of `text` whose token count fits
    /// `available`, found by binary search over character length —
    /// tokenization isn't linear in characters, so halving (not a fixed
    /// chars/token ratio) is the safe way to narrow in on the fit in
    /// O(log n) `countTokens` calls.
    private static func truncatedContent(
        _ text: String, available: Int, countTokens: (String) async -> Int?
    ) async -> String {
        guard available > 0, !text.isEmpty else { return "" }
        var lo = 0
        var hi = text.count
        var best = 0
        while lo <= hi {
            let mid = (lo + hi) / 2
            let candidate = String(text.prefix(mid))
            let cost = await countTokens(candidate) ?? 0
            if cost <= available {
                best = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return String(text.prefix(best))
    }
}
