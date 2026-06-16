//
//  MemoryConstellation.swift
//  M1K3Memory
//
//  The PURE layout + growth core for the 3D memory constellation — the flair
//  that makes the temporal graph felt: memories are motes, edges are threads,
//  and the whole thing accretes as the store grows over time.
//
//  No RealityKit here, by design. This file is `simd`-only value-in/value-out so
//  it's fully unit-testable without a renderer; the RealityKit view (separate
//  target) is a thin adapter that just places entities at these positions and
//  plays the growth order. Same split as KnowledgeGraph (pure) vs the avatar
//  views (RealityKit).
//
//  Layout is a deterministic force-directed relaxation: positions seed from a
//  hash of each memory's UUID (stable across runs), then a fixed number of
//  spring/repulsion iterations pull linked memories together and push the rest
//  apart. Deterministic in, deterministic out — no RNG, no clock — so it tests
//  cleanly and a redraw never makes the constellation jump.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-16, Confidence 0.82 (layout math +
//  growth timeline + kind→hue all TDD'd and deterministic; the visual tuning of
//  the RealityKit adapter on top is verify-by-run, Kev's eye). Prior: Unknown.

import Foundation
import simd

/// One memory as a placed mote. Visual attributes (`radius`, `hue`) are derived
/// from the graph, not stored — bigger = more connected, hue = kind.
public struct ConstellationNode: Identifiable, Equatable, Sendable {
    public let id: UUID
    public let kind: MemoryKind
    /// A short label for the mote (the memory text, trimmed for display).
    public let label: String
    public let createdAt: Date
    /// Number of edges touching this node — drives `radius`.
    public let degree: Int
    /// Resting position in constellation space (unit-ish sphere, ~[-1.5, 1.5]).
    public var position: SIMD3<Float>
    /// Visual radius: a base size that grows (sublinearly) with degree, so a
    /// hub memory looms larger than a leaf without swamping it.
    public var radius: Float
    /// Hue in 0..<1 (HSB), mapped from `kind`.
    public var hue: Float
}

/// An edge between two placed nodes (dangling edges are dropped at build time).
public struct ConstellationEdge: Equatable, Sendable {
    public let from: UUID
    public let to: UUID
    public let relation: String

    public init(from: UUID, to: UUID, relation: String) {
        self.from = from
        self.to = to
        self.relation = relation
    }
}

/// The renderable model: placed nodes, surviving edges, and the order nodes
/// entered the store (the accretion timeline the view animates).
public struct ConstellationModel: Sendable, Equatable {
    public let nodes: [ConstellationNode]
    public let edges: [ConstellationEdge]
    /// Node ids oldest-first (by `createdAt`) — replay this to grow the
    /// constellation mote by mote.
    public let growthOrder: [UUID]

    public var isEmpty: Bool {
        nodes.isEmpty
    }

    /// Look a node up by id (the view needs this to wire edge endpoints).
    public func node(_ id: UUID) -> ConstellationNode? {
        nodes.first { $0.id == id }
    }
}

public enum ConstellationLayout {
    /// Build a renderable constellation from memories + their edges. Pure and
    /// deterministic: same inputs → byte-identical output.
    ///
    /// `iterations` is the relaxation budget — more = tighter clusters, slower.
    /// The default is tuned for a few hundred motes; the math is O(n²) per
    /// iteration, matching the store's own "fine to thousands" stance.
    public static func build(
        memories: [Memory],
        edges: [MemoryEdge],
        iterations: Int = 60
    ) -> ConstellationModel {
        guard !memories.isEmpty else {
            return ConstellationModel(nodes: [], edges: [], growthOrder: [])
        }

        let ids = Set(memories.map(\.id))
        // Keep only edges whose BOTH ends are present (drop dangling).
        let liveEdges = edges.filter { ids.contains($0.fromID) && ids.contains($0.toID) }

        // Degree per node (undirected).
        var degree: [UUID: Int] = [:]
        for edge in liveEdges {
            degree[edge.fromID, default: 0] += 1
            degree[edge.toID, default: 0] += 1
        }

        // Seed positions deterministically from the UUID hash.
        var positions: [UUID: SIMD3<Float>] = [:]
        for memory in memories {
            positions[memory.id] = seedPosition(for: memory.id)
        }

        // Adjacency for the spring pass.
        var neighbours: [UUID: [UUID]] = [:]
        for edge in liveEdges {
            neighbours[edge.fromID, default: []].append(edge.toID)
            neighbours[edge.toID, default: []].append(edge.fromID)
        }

        positions = relax(
            ids: memories.map(\.id),
            positions: positions,
            neighbours: neighbours,
            iterations: iterations
        )

        let nodes = memories.map { memory -> ConstellationNode in
            let deg = degree[memory.id] ?? 0
            return ConstellationNode(
                id: memory.id,
                kind: memory.kind,
                label: displayLabel(memory.text),
                createdAt: memory.createdAt,
                degree: deg,
                position: positions[memory.id] ?? .zero,
                radius: radius(forDegree: deg),
                hue: hue(for: memory.kind)
            )
        }

        let renderEdges = liveEdges.map {
            ConstellationEdge(from: $0.fromID, to: $0.toID, relation: $0.relation)
        }
        let growthOrder = memories
            .sorted { $0.createdAt < $1.createdAt }
            .map(\.id)

        return ConstellationModel(nodes: nodes, edges: renderEdges, growthOrder: growthOrder)
    }

    // MARK: - Visual mapping

    /// Base radius + a sublinear bump per edge, so hubs read as bigger without
    /// a single mega-node dwarfing the field.
    static func radius(forDegree degree: Int) -> Float {
        0.05 + 0.03 * sqrt(Float(degree))
    }

    /// Kind → hue (HSB, 0..<1). Known kinds get a hand-picked hue; anything else
    /// (the open MemoryKind) hashes its rawValue to a stable hue so custom kinds
    /// still get a consistent colour.
    static func hue(for kind: MemoryKind) -> Float {
        switch kind {
        case .profile: 0.08 // warm amber — facts about the person
        case .preference: 0.52 // teal
        case .decision: 0.6 // blue
        case .episode: 0.33 // green — things that happened
        case .note: 0.0 // neutral (low-saturation grey at render)
        default: stableUnit(kind.rawValue) // custom kind → stable hashed hue
        }
    }

    static func displayLabel(_ text: String, max: Int = 48) -> String {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count > max else { return trimmed }
        return String(trimmed.prefix(max - 1)) + "…"
    }

    // MARK: - Layout internals

    /// A stable point on the unit sphere derived from the id — the deterministic
    /// seed so the constellation never reshuffles between redraws.
    static func seedPosition(for id: UUID) -> SIMD3<Float> {
        let bytes = withUnsafeBytes(of: id.uuid) { Array($0) }
        // Two independent hashes → spherical angles (Fibonacci-ish spread).
        let a = stableUnit(bytes, salt: 0x9E37) // azimuth fraction
        let b = stableUnit(bytes, salt: 0x85EB) // height fraction
        let theta = a * 2 * Float.pi
        let z = b * 2 - 1 // -1..1
        let r = sqrt(max(0, 1 - z * z))
        return SIMD3<Float>(r * cos(theta), r * sin(theta), z)
    }

    /// Force-directed relaxation: linked nodes spring together, all nodes repel.
    /// Fixed iterations + constants → deterministic.
    private static func relax(
        ids: [UUID],
        positions seed: [UUID: SIMD3<Float>],
        neighbours: [UUID: [UUID]],
        iterations: Int
    ) -> [UUID: SIMD3<Float>] {
        var positions = seed
        guard ids.count > 1 else { return positions }

        let repulsion: Float = 0.02
        let spring: Float = 0.08
        let restLength: Float = 0.5
        let damping: Float = 0.85

        for _ in 0 ..< iterations {
            var displacement: [UUID: SIMD3<Float>] = [:]

            // Repulsion: every ordered pair pushes apart (inverse-square, softened).
            for i in 0 ..< ids.count {
                for j in (i + 1) ..< ids.count {
                    let a = ids[i], b = ids[j]
                    let delta = positions[a]! - positions[b]!
                    let distSq = max(simd_length_squared(delta), 0.0001)
                    let force = delta / distSq * repulsion
                    displacement[a, default: .zero] += force
                    displacement[b, default: .zero] -= force
                }
            }

            // Springs: linked pairs pull toward restLength.
            for a in ids {
                for b in neighbours[a] ?? [] where a.uuidString < b.uuidString {
                    let delta = positions[a]! - positions[b]!
                    let dist = max(simd_length(delta), 0.0001)
                    let force = (delta / dist) * (dist - restLength) * spring
                    displacement[a, default: .zero] -= force
                    displacement[b, default: .zero] += force
                }
            }

            for id in ids {
                positions[id]! += (displacement[id] ?? .zero) * damping
            }
        }
        return positions
    }

    // MARK: - Deterministic hashing (no Hasher — that's randomized per run)

    /// FNV-1a over the string's bytes → a stable 0..<1 float.
    static func stableUnit(_ string: String) -> Float {
        stableUnit(Array(string.utf8), salt: 0)
    }

    static func stableUnit(_ bytes: [UInt8], salt: UInt64) -> Float {
        var hash: UInt64 = 1_469_598_103_934_665_603 &+ salt
        for byte in bytes {
            hash ^= UInt64(byte)
            hash = hash &* 1_099_511_628_211
        }
        // Top 24 bits → [0,1) for a clean fraction.
        return Float(hash >> 40) / Float(1 << 24)
    }
}
