//
//  MemoryConstellationTests.swift
//  M1K3MemoryTests
//
//  Pins the pure constellation layout: determinism (no jump on redraw), edge
//  attraction (linked motes cluster), kind→hue + degree→radius mapping, the
//  growth timeline, and dangling-edge pruning. The RealityKit view on top is
//  verify-by-run; everything that can be a value-in/value-out contract is here.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.85. Prior: this file.

import Foundation
@testable import M1K3Memory
import simd
import Testing

private func mem(_ text: String, kind: MemoryKind = .note, at t: TimeInterval) -> Memory {
    Memory(kind: kind, text: text, source: "test", createdAt: Date(timeIntervalSince1970: t))
}

struct MemoryConstellationTests {
    @Test("an empty store yields an empty constellation")
    func empty() {
        let model = ConstellationLayout.build(memories: [], edges: [])
        #expect(model.isEmpty)
        #expect(model.growthOrder.isEmpty)
    }

    @Test("layout is deterministic — same input, identical output")
    func deterministic() {
        let a = mem("alpha", at: 1)
        let b = mem("beta", at: 2)
        let edges = [MemoryEdge(fromID: a.id, toID: b.id, relation: "x")]
        let first = ConstellationLayout.build(memories: [a, b], edges: edges)
        let second = ConstellationLayout.build(memories: [a, b], edges: edges)
        #expect(first == second)
    }

    @Test("linked motes settle closer than unlinked ones")
    func edgeAttraction() throws {
        let a = mem("a", at: 1)
        let b = mem("b", at: 2)
        let c = mem("c", at: 3) // isolated, no edges
        let model = ConstellationLayout.build(
            memories: [a, b, c],
            edges: [MemoryEdge(fromID: a.id, toID: b.id, relation: "peer")]
        )
        let pa = try #require(model.node(a.id)?.position)
        let pb = try #require(model.node(b.id)?.position)
        let pc = try #require(model.node(c.id)?.position)
        let linked = simd_distance(pa, pb)
        #expect(linked < simd_distance(pa, pc))
        #expect(linked < simd_distance(pb, pc))
    }

    @Test("a hub mote is larger than a leaf (radius grows with degree)")
    func radiusFromDegree() throws {
        let hub = mem("hub", at: 1)
        let l1 = mem("leaf1", at: 2)
        let l2 = mem("leaf2", at: 3)
        let leaf = mem("lonely", at: 4)
        let model = ConstellationLayout.build(
            memories: [hub, l1, l2, leaf],
            edges: [
                MemoryEdge(fromID: hub.id, toID: l1.id, relation: "r"),
                MemoryEdge(fromID: hub.id, toID: l2.id, relation: "r"),
            ]
        )
        #expect(try #require(model.node(hub.id)?.radius) > model.node(leaf.id)!.radius)
        #expect(model.node(hub.id)?.degree == 2)
        #expect(model.node(leaf.id)?.degree == 0)
    }

    @Test("kind maps to a stable hue; custom kinds get a consistent hashed hue")
    func hueMapping() {
        #expect(ConstellationLayout.hue(for: .profile) == 0.08)
        #expect(ConstellationLayout.hue(for: .decision) == 0.6)
        // An open/custom kind is stable run-to-run (deterministic hash, not Hasher).
        let custom = MemoryKind(rawValue: "relationship")
        #expect(ConstellationLayout.hue(for: custom) == ConstellationLayout.hue(for: custom))
        #expect(ConstellationLayout.hue(for: custom) != ConstellationLayout.hue(for: .profile))
    }

    @Test("plain notes are near-white starlight; categorised kinds carry colour")
    func saturationByKind() {
        // Notes (what knowledge-base seeds map to) must NOT render as a saturated
        // colour — the all-red-dots bug. They're near-white; kinds are accents.
        #expect(ConstellationLayout.saturation(for: .note) < 0.2)
        #expect(ConstellationLayout.saturation(for: .profile) > 0.5)
        #expect(ConstellationLayout.saturation(for: .decision) > 0.5)
    }

    @Test("growth order is oldest-first — the accretion timeline")
    func growthOrder() {
        let oldest = mem("oldest", at: 100)
        let newest = mem("newest", at: 300)
        let middle = mem("middle", at: 200)
        let model = ConstellationLayout.build(memories: [newest, oldest, middle], edges: [])
        #expect(model.growthOrder == [oldest.id, middle.id, newest.id])
    }

    @Test("maxNodes caps to the newest memories, dropping older ones")
    func maxNodesCapKeepsNewest() {
        let oldest = mem("oldest", at: 100)
        let middle = mem("middle", at: 200)
        let newest = mem("newest", at: 300)
        let model = ConstellationLayout.build(
            memories: [oldest, middle, newest], edges: [], maxNodes: 2
        )
        let ids = Set(model.nodes.map(\.id))
        #expect(model.nodes.count == 2)
        #expect(ids == [middle.id, newest.id]) // the two newest; oldest dropped
    }

    @Test("an edge to a capped-out node is pruned")
    func maxNodesPrunesEdgesToDropped() {
        let oldest = mem("oldest", at: 100)
        let newest = mem("newest", at: 300)
        let model = ConstellationLayout.build(
            memories: [oldest, newest],
            edges: [MemoryEdge(fromID: oldest.id, toID: newest.id, relation: "x")],
            maxNodes: 1
        )
        #expect(model.nodes.count == 1)
        #expect(model.edges.isEmpty) // oldest dropped → its edge is dangling → pruned
    }

    @Test("nil maxNodes keeps everything")
    func maxNodesNilKeepsAll() {
        let memories = (1 ... 5).map { mem("m\($0)", at: TimeInterval($0)) }
        let model = ConstellationLayout.build(memories: memories, edges: [], maxNodes: nil)
        #expect(model.nodes.count == 5)
    }

    @Test("dangling edges (endpoint not in the node set) are dropped")
    func danglingEdgesDropped() {
        let a = mem("a", at: 1)
        let ghost = UUID()
        let model = ConstellationLayout.build(
            memories: [a],
            edges: [MemoryEdge(fromID: a.id, toID: ghost, relation: "x")]
        )
        #expect(model.edges.isEmpty)
        #expect(model.node(a.id)?.degree == 0)
    }

    @Test("seed positions sit on the unit sphere and are id-stable")
    func seedOnUnitSphere() {
        let id = UUID()
        let p = ConstellationLayout.seedPosition(for: id)
        #expect(abs(simd_length(p) - 1.0) < 0.001) // on the sphere
        #expect(ConstellationLayout.seedPosition(for: id) == p) // stable
    }

    @Test("accessibilitySummary names the field for VoiceOver — counts, correctly pluralised")
    func accessibilitySummaryPluralises() {
        let empty = ConstellationLayout.build(memories: [], edges: [])
        #expect(empty.accessibilitySummary == "Memory constellation — 0 memories, 0 connections")

        let one = mem("solo", at: 1)
        let onlyOne = ConstellationLayout.build(memories: [one], edges: [])
        #expect(onlyOne.accessibilitySummary == "Memory constellation — 1 memory, 0 connections")

        let a = mem("a", at: 1)
        let b = mem("b", at: 2)
        let c = mem("c", at: 3)
        let many = ConstellationLayout.build(
            memories: [a, b, c],
            edges: [
                MemoryEdge(fromID: a.id, toID: b.id, relation: "r"),
                MemoryEdge(fromID: b.id, toID: c.id, relation: "r"),
            ]
        )
        #expect(many.accessibilitySummary == "Memory constellation — 3 memories, 2 connections")

        let oneEdge = ConstellationLayout.build(
            memories: [a, b],
            edges: [MemoryEdge(fromID: a.id, toID: b.id, relation: "r")]
        )
        #expect(oneEdge.accessibilitySummary == "Memory constellation — 2 memories, 1 connection")
    }

    @Test("every node and surviving edge is represented")
    func countsPreserved() {
        let a = mem("a", at: 1)
        let b = mem("b", at: 2)
        let c = mem("c", at: 3)
        let model = ConstellationLayout.build(
            memories: [a, b, c],
            edges: [
                MemoryEdge(fromID: a.id, toID: b.id, relation: "r"),
                MemoryEdge(fromID: b.id, toID: c.id, relation: "r"),
            ]
        )
        #expect(model.nodes.count == 3)
        #expect(model.edges.count == 2)
    }
}
