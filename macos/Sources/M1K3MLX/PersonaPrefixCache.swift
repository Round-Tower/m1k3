//
//  PersonaPrefixCache.swift
//  M1K3MLX
//
//  The enabler for richer prompting: M1K3's persona (and the tool specs —
//  Qwen renders TOOLS inside the SYSTEM block) is prefilled into a KV cache
//  ONCE per (model × tools × persona), and every turn starts from a deep COPY
//  of that prefix instead of re-prefilling it. A longer persona stops being a
//  per-turn TTFT tax — it costs once per launch.
//
//  The stored master cache is never handed out: `snapshot` returns
//  `copy()`-deep copies (upstream KVCache.copy() materialises new arrays), so
//  turns mutate their own offsets independently. In-memory only — on-disk
//  persistence (savePromptCache) is a follow-up with model-versioning needs.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.8 (key/store logic
//  tested; the prefill render + trim normalisation is verify-at-⌘R like all
//  MLX generation). Prior: Unknown
//

import Foundation
import MLXLMCommon

/// Identity of one rendered system-block prefix.
struct PersonaCacheKey: Hashable {
    let modelID: String
    let toolsFingerprint: String
    let personaText: String

    init(modelID: String, toolNames: [String], personaText: String) {
        self.modelID = modelID
        toolsFingerprint = toolNames.sorted().joined(separator: ",")
        self.personaText = personaText
    }
}

/// One cached prefix + its token length (for prefill-savings logging).
struct PersonaPrefixSnapshot {
    let cache: [KVCache]
    let tokenCount: Int
}

/// `@unchecked Sendable`: a single NSLock guards the slot; the stored master
/// cache is only ever read to produce copies.
final class PersonaPrefixCache: @unchecked Sendable {
    private let lock = NSLock()
    private var key: PersonaCacheKey?
    private var master: [KVCache]?
    private var tokenCount = 0

    /// A deep, independently-mutable copy of the cached prefix — or nil when
    /// the slot is empty or keyed to a different render.
    func snapshot(for requested: PersonaCacheKey) -> PersonaPrefixSnapshot? {
        lock.lock()
        defer { lock.unlock() }
        guard requested == key, let master else { return nil }
        return PersonaPrefixSnapshot(cache: master.map { $0.copy() }, tokenCount: tokenCount)
    }

    func store(_ cache: [KVCache], tokenCount: Int, for newKey: PersonaCacheKey) {
        lock.lock()
        defer { lock.unlock() }
        key = newKey
        master = cache
        self.tokenCount = tokenCount
    }

    /// Drop the slot (persona text changed — e.g. a future profile update —
    /// or the caller wants the memory back).
    func invalidate() {
        lock.lock()
        defer { lock.unlock() }
        key = nil
        master = nil
        tokenCount = 0
    }
}
