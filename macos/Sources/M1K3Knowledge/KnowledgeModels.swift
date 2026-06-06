//
//  KnowledgeModels.swift
//  M1K3Knowledge
//
//  Domain-neutral knowledge model. A KnowledgeItem is anything M1K3 remembers —
//  a document, a logged call, a note — and owns ordered KnowledgeChunks that are
//  embedded and searched. Documents (Phase 4) and calls (Phase 7) both land here
//  so a single hybrid search spans everything the assistant knows.
//
//  Generalised from the prior knowledge-server project's IndexedDocument/IndexedDocumentChunk (which were
//  SOP/PDF-specific). GRDB record types; persistence lives in KnowledgeStore.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import Foundation
import GRDB

/// What a knowledge item represents. Open-ended string-backed enum so new
/// sources (email, calendar, transcript) don't require a schema change.
public struct KnowledgeKind: RawRepresentable, Hashable, Sendable, Codable {
    public let rawValue: String
    public init(rawValue: String) {
        self.rawValue = rawValue
    }

    public static let document = KnowledgeKind(rawValue: "document")
    public static let call = KnowledgeKind(rawValue: "call")
    public static let note = KnowledgeKind(rawValue: "note")
}

/// A unit of knowledge the assistant can retrieve over.
public struct KnowledgeItem: Identifiable, Equatable, Sendable, Codable {
    public var id: UUID
    public var kind: KnowledgeKind
    public var title: String
    /// Optional stable external identity (file sha256, call UUID, URL) used for
    /// dedupe. Two ingests of the same source collapse on this.
    public var sourceRef: String?
    public var createdAt: Date

    public init(
        id: UUID = UUID(),
        kind: KnowledgeKind,
        title: String,
        sourceRef: String? = nil,
        createdAt: Date = Date()
    ) {
        self.id = id
        self.kind = kind
        self.title = title
        self.sourceRef = sourceRef
        self.createdAt = createdAt
    }
}

/// An ordered, embeddable slice of a KnowledgeItem's content.
public struct KnowledgeChunk: Identifiable, Equatable, Sendable, Codable {
    public var id: UUID
    public var itemID: UUID
    public var ordinal: Int
    public var heading: String?
    public var content: String

    public init(
        id: UUID = UUID(),
        itemID: UUID,
        ordinal: Int,
        heading: String? = nil,
        content: String
    ) {
        self.id = id
        self.itemID = itemID
        self.ordinal = ordinal
        self.heading = heading
        self.content = content
    }
}

/// A search result: a chunk plus where it came from and how it scored.
/// `similarity` is set on vector hits; `rrfScore` on fused hybrid hits.
public struct ChunkHit: Identifiable, Equatable, Sendable {
    public var id: UUID {
        chunkID
    }

    public var chunkID: UUID
    public var itemID: UUID
    public var itemTitle: String
    public var kind: KnowledgeKind
    public var heading: String?
    public var content: String
    public var similarity: Float?
    public var rrfScore: Double?

    public init(
        chunkID: UUID,
        itemID: UUID,
        itemTitle: String,
        kind: KnowledgeKind,
        heading: String?,
        content: String,
        similarity: Float? = nil,
        rrfScore: Double? = nil
    ) {
        self.chunkID = chunkID
        self.itemID = itemID
        self.itemTitle = itemTitle
        self.kind = kind
        self.heading = heading
        self.content = content
        self.similarity = similarity
        self.rrfScore = rrfScore
    }
}

// MARK: - GRDB persistence records

/// Row shape for `knowledge_items`.
struct ItemRecord: Codable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "knowledge_items"
    var id: String
    var kind: String
    var title: String
    var source_ref: String?
    var created_at: Double

    init(_ item: KnowledgeItem) {
        id = item.id.uuidString
        kind = item.kind.rawValue
        title = item.title
        source_ref = item.sourceRef
        created_at = item.createdAt.timeIntervalSince1970
    }
}

/// Row shape for `knowledge_chunks`.
struct ChunkRecord: Codable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "knowledge_chunks"
    var id: String
    var item_id: String
    var ordinal: Int
    var heading: String?
    var content: String

    init(_ chunk: KnowledgeChunk) {
        id = chunk.id.uuidString
        item_id = chunk.itemID.uuidString
        ordinal = chunk.ordinal
        heading = chunk.heading
        content = chunk.content
    }
}
