//
//  MemoryTitleCompositionTests.swift
//  M1K3MemoryTests
//
//  B5 layer 3, ported to the memory GRAPH lane. The corpus lane embeds chunks
//  title-prefixed (EmbeddingText.forChunk) — which is why search_knowledge now
//  catches keyword queries — but graph facts embedded BARE long text, so
//  recall_memory still missed keywords against long facts (the 2026-07-09
//  live residual). Facts now carry an optional title, compose their embedding
//  text through the SAME forChunk rules (nil / leads-with-title → bare), and
//  reindexEmbeddings re-embeds COMPOSED — so the one-time reindex the app
//  wiring triggers gives existing titled facts their discriminating context.
//
//  Cosines are the deterministic hashing embedder (1-of-N shared → 1/√N), so
//  the keyword-rescue case is engineered exactly, not hoped for.
//
//  Signed: Kev + claude-fable-5, 2026-07-09, Confidence 0.85, Prior:
//  Kev + claude-fable-5 (EmbeddingText.swift B5 layer 3, 2026-07-08).
//

import Foundation
import GRDB
@testable import M1K3Knowledge
@testable import M1K3Memory
import Testing

private struct Fixture {
    let store: MemoryStore
    let embedder = HashingEmbeddingService()

    init() throws {
        store = try MemoryStore()
    }
}

struct MemoryTitleCompositionTests {
    @Test("a title round-trips through remember → recall")
    func titleRoundTrips() async throws {
        let f = try Fixture()
        let m = Memory(kind: .episode, text: "alpha beta gamma", title: "Launch day", source: "test")
        try f.store.remember(m, embedding: await f.embedder.embed(m.embeddingText))
        let got = try f.store.memory(id: m.id)
        #expect(got?.title == "Launch day")
    }

    @Test("a title-less memory stays title-less — the pre-v2 shape is unchanged")
    func nilTitlePreserved() async throws {
        let f = try Fixture()
        let m = Memory(kind: .note, text: "alpha beta gamma", source: "test")
        try f.store.remember(m, embedding: await f.embedder.embed(m.embeddingText))
        #expect(try f.store.memory(id: m.id)?.title == nil)
    }

    @Test("embeddingText composes through the forChunk rules: nil → bare, title → prefixed, own-head → bare")
    func embeddingTextComposition() {
        #expect(Memory(kind: .note, text: "just a fact", source: "test").embeddingText == "just a fact")
        #expect(
            Memory(kind: .episode, text: "The build went green.", title: "Golden Gate", source: "test").embeddingText
                == "Golden Gate\nThe build went green."
        )
        // Facts that are their own titles (the distilled-fact shape) embed bare.
        #expect(
            Memory(kind: .profile, text: "Kev lives in Cork.", title: "Kev lives in Cork.", source: "test").embeddingText
                == "Kev lives in Cork."
        )
    }

    @Test("THE GRAPH-LANE RESCUE: a keyword query recalls a long fact only through its title")
    func keywordRecallsThroughTitle() async throws {
        let f = try Fixture()
        // Long fact, ZERO token overlap with the query → bare cosine 0.0.
        let text = "gamma delta epsilon zeta eta theta iota kappa lambda"
        // Composed with the title, the query shares 2 of 11 tokens → 2/√22 ≈ 0.426,
        // above memoryThreshold (0.35). The title carries the discriminating
        // context — the miniature of the live Golden Gate miss.
        let titled = Memory(kind: .episode, text: text, title: "alpha beta", source: "test")
        try f.store.remember(titled, embedding: await f.embedder.embed(titled.embeddingText))

        let hits = try f.store.recall(query: "alpha beta", queryVector: await f.embedder.embed("alpha beta"))
        #expect(hits.contains { $0.memory.id == titled.id })
    }

    @Test("control: the same long fact WITHOUT a title stays unrecallable by the keyword")
    func bareLongFactStillMisses() async throws {
        let f = try Fixture()
        let bare = Memory(kind: .episode, text: "gamma delta epsilon zeta eta theta iota kappa lambda", source: "test")
        try f.store.remember(bare, embedding: await f.embedder.embed(bare.embeddingText))
        let hits = try f.store.recall(query: "alpha beta", queryVector: await f.embedder.embed("alpha beta"))
        #expect(!hits.contains { $0.memory.id == bare.id })
    }

    @Test("reindexEmbeddings re-embeds the COMPOSED text, not the bare row text")
    func reindexEmbedsComposed() async throws {
        let f = try Fixture()
        let titled = Memory(kind: .episode, text: "The build went green.", title: "Golden Gate", source: "test")
        // Written with a deliberately WRONG embedding (bare text) — the reindex
        // must repair it to the composed vector.
        try f.store.remember(titled, embedding: await f.embedder.embed(titled.text))

        let recorder = RecordingMemoryEmbedder()
        _ = try await f.store.reindexEmbeddings(using: recorder, fingerprint: "test/v2")
        #expect(recorder.embedded.contains("Golden Gate\nThe build went green."))
        #expect(!recorder.embedded.contains("The build went green."))
    }

    @Test("setTitle backfills a title; memoriesWithoutTitle lists only the untitled")
    func titleBackfillAPIs() async throws {
        let f = try Fixture()
        let untitled = Memory(kind: .episode, text: "alpha beta gamma", source: "test")
        let titled = Memory(kind: .episode, text: "delta epsilon zeta", title: "Named", source: "test")
        try f.store.remember(untitled, embedding: await f.embedder.embed(untitled.embeddingText))
        try f.store.remember(titled, embedding: await f.embedder.embed(titled.embeddingText))

        let needing = try f.store.memoriesWithoutTitle()
        #expect(needing.map(\.id) == [untitled.id])

        try f.store.setTitle("Backfilled", for: untitled.id)
        #expect(try f.store.memory(id: untitled.id)?.title == "Backfilled")
        #expect(try f.store.memoriesWithoutTitle().isEmpty)
    }

    @Test("a REAL v1-shaped store on disk migrates: rows survive, title reads back nil")
    func v1StoreOnDiskMigrates() throws {
        // Build a genuine v1 database (no title column) via a v1-only migrator,
        // seed a row, close — then open through the real MemoryStore and prove
        // v2-title lands without touching the data. This is the actual upgrade
        // path every production memory.sqlite takes on first launch.
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("m1k3-tests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        let path = dir.appendingPathComponent("memory.sqlite").path

        let id = UUID()
        do {
            let dbQueue = try DatabaseQueue(path: path)
            var migrator = DatabaseMigrator()
            // Verbatim v1 DDL (MemoryStore.migrate's "v1" block) so the real
            // migrator sees "v1" applied and runs ONLY v2-title.
            migrator.registerMigration("v1") { db in
                try db.create(table: "memories") { t in
                    t.column("id", .text).primaryKey()
                    t.column("kind", .text).notNull().indexed()
                    t.column("text", .text).notNull()
                    t.column("source", .text).notNull()
                    t.column("created_at", .double).notNull()
                    t.column("superseded_by", .text)
                }
                try db.execute(sql: "CREATE VIRTUAL TABLE memory_fts USING fts5(id UNINDEXED, text)")
                try db.create(table: "memory_embeddings") { t in
                    t.column("memory_id", .text).primaryKey()
                    t.column("embedding", .blob).notNull()
                }
                try db.create(table: "memory_edges") { t in
                    t.column("from_id", .text).notNull().indexed()
                    t.column("to_id", .text).notNull().indexed()
                    t.column("relation", .text).notNull()
                    t.column("created_at", .double).notNull()
                    t.primaryKey(["from_id", "to_id", "relation"])
                }
                try db.create(table: "memory_meta") { t in
                    t.column("key", .text).primaryKey()
                    t.column("value", .text).notNull()
                }
            }
            try migrator.migrate(dbQueue)
            try dbQueue.write { db in
                try db.execute(
                    sql: """
                    INSERT INTO memories (id, kind, text, source, created_at, superseded_by)
                    VALUES (?, 'profile', 'Kev lives in Cork.', 'test', 1000000, NULL)
                    """,
                    arguments: [id.uuidString]
                )
            }
            try dbQueue.close()
        }

        let store = try MemoryStore(path: path)
        let migrated = try store.memory(id: id)
        #expect(migrated?.text == "Kev lives in Cork.")
        #expect(migrated?.title == nil)
        #expect(try store.memoriesWithoutTitle().map(\.id) == [id])
        // And the new column is writable on the migrated row.
        try store.setTitle("Home", for: id)
        #expect(try store.memory(id: id)?.title == "Home")
    }

    @Test("reindex on an EMPTY store still adopts the fingerprint marker — fresh graphs must not stay markerless")
    func emptyStoreAdoptsMarker() async throws {
        let f = try Fixture()
        _ = try await f.store.reindexEmbeddings(using: f.embedder, fingerprint: "hashing/v1+title-v1")
        #expect(try f.store.meta(key: MemoryStore.embedderFingerprintKey) == "hashing/v1+title-v1")
    }

    @Test("setMeta/deleteMeta round-trip — the crash-safe backfill flag's storage")
    func metaWriteSurface() throws {
        let f = try Fixture()
        try f.store.setMeta(key: MemoryStore.titleBackfillPendingKey, value: "1")
        #expect(try f.store.meta(key: MemoryStore.titleBackfillPendingKey) == "1")
        try f.store.setMeta(key: MemoryStore.titleBackfillPendingKey, value: "2")
        #expect(try f.store.meta(key: MemoryStore.titleBackfillPendingKey) == "2")
        try f.store.deleteMeta(key: MemoryStore.titleBackfillPendingKey)
        #expect(try f.store.meta(key: MemoryStore.titleBackfillPendingKey) == nil)
    }

    @Test("the meta surface the app wiring needs: fingerprint read + embedding count")
    func metaAndCountSurface() async throws {
        let f = try Fixture()
        #expect(try f.store.meta(key: MemoryStore.embedderFingerprintKey) == nil)
        #expect(try f.store.embeddingCount() == 0)

        let m = Memory(kind: .note, text: "alpha beta gamma", source: "test")
        try f.store.remember(m, embedding: await f.embedder.embed(m.embeddingText))
        #expect(try f.store.embeddingCount() == 1)

        _ = try await f.store.reindexEmbeddings(using: f.embedder, fingerprint: "hashing/v1+title-v1")
        #expect(try f.store.meta(key: MemoryStore.embedderFingerprintKey) == "hashing/v1+title-v1")
    }
}

/// Records every string handed to embed/embedBatch (the DocumentIngesterTests
/// RecordingEmbedder pattern, local to this target).
private final class RecordingMemoryEmbedder: EmbeddingService, @unchecked Sendable {
    private let inner = HashingEmbeddingService()
    private let lock = NSLock()
    /// @unchecked Sendable: appends are lock-guarded; tests read after awaiting.
    private(set) var embedded: [String] = []

    var fingerprint: String {
        inner.fingerprint
    }

    var dimension: Int {
        inner.dimension
    }

    func isAvailable() async -> Bool {
        true
    }

    func embed(_ text: String) async throws -> [Float] {
        lock.withLock { embedded.append(text) }
        return try await inner.embed(text)
    }

    func embedBatch(_ texts: [String]) async throws -> [[Float]] {
        lock.withLock { embedded.append(contentsOf: texts) }
        return try await inner.embedBatch(texts)
    }
}
