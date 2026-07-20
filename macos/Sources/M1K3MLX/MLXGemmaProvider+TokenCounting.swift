//
//  MLXGemmaProvider+TokenCounting.swift
//  M1K3MLX
//
//  Conforms MLXGemmaProvider to M1K3Inference's `TokenCounting` seam so
//  `GroundingBudget` (M1K3Knowledge, wired from M1K3Chat's AgentRAGResponder)
//  can measure the REAL rendered grounding cost before it's injected into a
//  prompt — without M1K3Chat linking M1K3MLX (M1K3Chat → M1K3Inference is
//  already a dependency edge; M1K3MLX → M1K3Chat would be a new, wrong-
//  direction one). Apple Foundation Models / Mini simply don't conform:
//  `provider as? TokenCounting` is nil for them, which the cap treats as
//  "this tier self-manages its own context window, nothing to measure".
//
//  ⚠️ Overlap note (read before touching MLXGemmaProvider.swift): PR #65
//  (feat/prompt-size-instrument, open, unmerged as of 2026-07-20)
//  independently adds an IDENTICAL `tokenCount(_ text:) async -> Int?` method
//  directly on MLXGemmaProvider (no protocol) for its own prompt-size report.
//  When both land this is a duplicate-declaration collision to resolve by
//  hand: keep PR #65's method body (same logic, arrived at independently),
//  delete this file's `tokenCount` implementation, and instead declare
//  `extension MLXGemmaProvider: TokenCounting {}` — the method PR #65 adds
//  already satisfies this protocol's signature exactly.
//
//  Signed: Kev + claude-fable-5, 2026-07-20, Confidence 0.75, Prior: Unknown
//  Context: grounding-cap PR. The tokenizer path mirrors
//  `templateDebugDescription`'s `ensureLoaded()`/`container.perform()` shape
//  one-for-one (both use `context.tokenizer` off a loaded `ModelContainer`),
//  so mechanically it should be correct — but it is UNVERIFIED live: MLX
//  generation/tokenizer paths need the app-bundle metallib and can't run
//  under `swift test` (same limit as every other MLX call in this module).
//  Verify-owed: an on-device re-measure of grounded-Q, confirming it now
//  lands under the 3000-token reserve with margin.
//

import Foundation
import M1K3Inference

extension MLXGemmaProvider: TokenCounting {
    /// REAL token count for `text` through this model's own tokenizer, RAW
    /// (no chat template) — the grounding cap measures rendered section
    /// text, not a wrapped chat turn. `nil` on any failure to load (never a
    /// confident zero).
    public func tokenCount(_ text: String) async -> Int? {
        do {
            let container = try await ensureLoaded()
            return await container.perform { context in
                context.tokenizer.encode(text: text).count
            }
        } catch {
            return nil
        }
    }
}
