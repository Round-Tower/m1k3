//
//  ToolSourceCollector.swift
//  M1K3Knowledge
//
//  The rendezvous between a knowledge-search TOOL and the chat turn's source
//  list: when the model retrieves via search_knowledge mid-turn, the hits it
//  saw are recorded here, then drained after the stream completes and merged
//  into the message's sources — so the citation validator's allow-list covers
//  what the model ASKED for, not just what was injected up front.
//
//  Signed: Kev + claude-fable-5, 2026-06-10, Confidence 0.85, Prior: Unknown
//

import Foundation
import Synchronization

public final class ToolSourceCollector: Sendable {
    private let hits = Mutex<[ChunkHit]>([])

    public init() {}

    public func record(_ newHits: [ChunkHit]) {
        hits.withLock { $0.append(contentsOf: newHits) }
    }

    /// Return everything recorded and reset — call once per turn.
    public func drain() -> [ChunkHit] {
        hits.withLock { current in
            let drained = current
            current = []
            return drained
        }
    }
}
