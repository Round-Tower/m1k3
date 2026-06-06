//
//  KnowledgeGraphTests.swift
//  M1K3KnowledgeTests
//
//  Contract tests for the pure graph builder: item/entity nodes, mentions
//  edges, co-occurrence pairs, cross-item entity sharing (the bit that makes it
//  a graph), case-folded dedupe, and determinism.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown

import Foundation
@testable import M1K3Knowledge
import Testing

struct KnowledgeGraphTests {
    private func input(
        _ title: String,
        kind: KnowledgeKind = .call,
        _ entities: [(String, String)]
    ) -> KnowledgeGraphInput {
        KnowledgeGraphInput(
            itemID: UUID(),
            title: title,
            kind: kind,
            entities: entities.map { GraphEntity(type: $0.0, name: $0.1) }
        )
    }

    @Test("an item with no entities yields a single item node and no edges")
    func loneItem() {
        let snap = KnowledgeGraphBuilder.build(from: [input("Empty call", [])])
        #expect(snap.nodes.count == 1)
        #expect(snap.nodes.first?.kind == .item)
        #expect(snap.nodes.first?.metadata["kind"] == "call")
        #expect(snap.edges.isEmpty)
    }

    @Test("each entity becomes a node with a mentions edge from the item")
    func mentions() {
        let snap = KnowledgeGraphBuilder.build(from: [
            input("Acme call", [("org", "Acme"), ("person", "Dana")]),
        ])
        #expect(snap.nodes.filter { $0.kind == .item }.count == 1)
        #expect(snap.nodes.filter { $0.kind == .entity }.count == 2)
        let mentions = snap.edges.filter { $0.kind == .mentions }
        #expect(mentions.count == 2)
        #expect(mentions.allSatisfy { $0.sourceID.hasPrefix("item:") })
        #expect(mentions.allSatisfy { $0.targetID.hasPrefix("entity:") })
    }

    @Test("n entities in one item produce nC2 co-occurrence edges")
    func coOccurrencePairs() {
        let snap = KnowledgeGraphBuilder.build(from: [
            input("Three", [("org", "A"), ("org", "B"), ("topic", "C")]),
        ])
        // 3 choose 2 = 3
        #expect(snap.edges.filter { $0.kind == .coOccurs }.count == 3)
    }

    @Test("co-occurrence edges are undirected-canonical (lo,hi ordered)")
    func coOccurrenceCanonical() throws {
        let snap = KnowledgeGraphBuilder.build(from: [
            input("Pair", [("z", "Zebra"), ("a", "Ant")]),
        ])
        let edge = try #require(snap.edges.first { $0.kind == .coOccurs })
        #expect(edge.sourceID < edge.targetID)
    }

    @Test("the same entity across two items is ONE shared node — this is the graph")
    func entitySharedAcrossItems() {
        let snap = KnowledgeGraphBuilder.build(from: [
            input("Call 1", [("org", "Acme")]),
            input("Call 2", [("org", "Acme")]),
        ])
        let entityNodes = snap.nodes.filter { $0.kind == .entity }
        #expect(entityNodes.count == 1) // shared, not duplicated
        // both items mention it
        #expect(snap.edges.filter { $0.kind == .mentions }.count == 2)
        #expect(snap.nodes.filter { $0.kind == .item }.count == 2)
    }

    @Test("entity dedupe is case-folded within an item")
    func caseFoldedDedupe() {
        let snap = KnowledgeGraphBuilder.build(from: [
            input("Dupes", [("org", "Acme"), ("org", "ACME"), ("org", "acme")]),
        ])
        #expect(snap.nodes.filter { $0.kind == .entity }.count == 1)
        #expect(snap.edges.filter { $0.kind == .mentions }.count == 1)
        #expect(snap.edges.filter { $0.kind == .coOccurs }.isEmpty) // only one unique entity
    }

    @Test("same name under different types does NOT collide")
    func typeNamespacing() {
        let snap = KnowledgeGraphBuilder.build(from: [
            input("Apple", [("org", "Apple"), ("topic", "Apple")]),
        ])
        #expect(snap.nodes.filter { $0.kind == .entity }.count == 2)
    }

    @Test("build is deterministic — same input, identical snapshot")
    func deterministic() {
        let id = UUID()
        let mk = {
            KnowledgeGraphBuilder.build(from: [
                KnowledgeGraphInput(
                    itemID: id, title: "T", kind: .document,
                    entities: [GraphEntity(type: "org", name: "Acme"),
                               GraphEntity(type: "person", name: "Dana")]
                ),
            ])
        }
        let a = mk()
        let b = mk()
        #expect(Set(a.edges.map(\.id)) == Set(b.edges.map(\.id)))
        #expect(Set(a.nodes.map(\.id)) == Set(b.nodes.map(\.id)))
    }
}
