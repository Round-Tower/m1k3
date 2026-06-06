//
//  KnowledgeGraph.swift
//  M1K3Knowledge
//
//  Pure logic: knowledge items + their extracted entities → graph nodes + edges.
//  No I/O, no database — value types in, value types out. The caller persists or
//  serves the snapshot (e.g. the MCP `query_graph` tool).
//
//  Generalised from the prior knowledge-server project's KnowledgeGraphBuilder (which was session/manager/tag/
//  category specific). M1K3's domain-neutral shape: an *item* node per piece of
//  knowledge, an *entity* node per distinct extracted entity, `mentions` edges
//  item→entity, and `co_occurs` edges between entities sharing an item. So a
//  logged call and an ingested document both light up the same graph, and
//  "which calls mention Acme?" becomes a graph query.
//
//  Entity *extraction* is intentionally NOT here — the builder takes entities it
//  is given, exactly as the prior knowledge-server project's builder took a fully-formed SessionDataPackage.
//  That keeps it pure and fully testable without mocks.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8,
//  Prior: the prior knowledge-server project KnowledgeGraphBuilder (Kev, HIGH confidence)

import Foundation

// MARK: - Graph types

public enum GraphNodeKind: String, Codable, Sendable {
    case item
    case entity
}

public enum GraphEdgeKind: String, Codable, Sendable {
    /// item → entity: this item references this entity.
    case mentions
    /// entity ↔ entity: both entities appear in the same item.
    case coOccurs = "co_occurs"
}

public struct GraphNode: Codable, Sendable, Equatable {
    public let id: String
    public let kind: GraphNodeKind
    public let label: String
    public let metadata: [String: String]

    public init(id: String, kind: GraphNodeKind, label: String, metadata: [String: String] = [:]) {
        self.id = id
        self.kind = kind
        self.label = label
        self.metadata = metadata
    }
}

public struct GraphEdge: Codable, Sendable, Equatable {
    public let id: String
    public let sourceID: String
    public let targetID: String
    public let kind: GraphEdgeKind
    public let weight: Double

    public init(id: String, sourceID: String, targetID: String, kind: GraphEdgeKind, weight: Double = 1.0) {
        self.id = id
        self.sourceID = sourceID
        self.targetID = targetID
        self.kind = kind
        self.weight = weight
    }
}

public struct GraphSnapshot: Sendable, Equatable {
    public let nodes: [GraphNode]
    public let edges: [GraphEdge]

    public init(nodes: [GraphNode], edges: [GraphEdge]) {
        self.nodes = nodes
        self.edges = edges
    }
}

/// A named entity extracted from an item (a person, org, place, topic…). The
/// `type` namespaces the entity so "Apple" the company and "apple" the topic
/// don't collide.
public struct GraphEntity: Hashable, Sendable {
    public let type: String
    public let name: String

    public init(type: String, name: String) {
        self.type = type
        self.name = name
    }

    /// Stable node identity. Case-folded name so "Acme"/"acme" map to one node.
    var nodeID: String {
        "entity:\(type):\(name.lowercased())"
    }

    var label: String {
        name
    }
}

/// Builder input: one knowledge item plus the entities extracted from it.
public struct KnowledgeGraphInput: Sendable {
    public let itemID: UUID
    public let title: String
    public let kind: KnowledgeKind
    public let entities: [GraphEntity]

    public init(itemID: UUID, title: String, kind: KnowledgeKind, entities: [GraphEntity]) {
        self.itemID = itemID
        self.title = title
        self.kind = kind
        self.entities = entities
    }

    /// Convenience: derive an input from a stored item + extracted entities.
    public init(item: KnowledgeItem, entities: [GraphEntity]) {
        self.init(itemID: item.id, title: item.title, kind: item.kind, entities: entities)
    }
}

// MARK: - Builder

public enum KnowledgeGraphBuilder {
    /// Build a graph snapshot across many items. Pure — no I/O, no side effects.
    /// Nodes are deduped by id; entity nodes are shared across items, which is
    /// what turns isolated items into a connected graph. Co-occurrence edges are
    /// emitted once per unordered entity pair per item.
    public static func build(from inputs: [KnowledgeGraphInput]) -> GraphSnapshot {
        var nodes: [String: GraphNode] = [:]
        var edges: [GraphEdge] = []

        for input in inputs {
            let itemNodeID = "item:\(input.itemID.uuidString)"
            nodes[itemNodeID] = GraphNode(
                id: itemNodeID,
                kind: .item,
                label: input.title,
                metadata: ["kind": input.kind.rawValue]
            )

            // De-dupe entities within this item (case-folded via nodeID) while
            // preserving first-seen order for deterministic co-occurrence pairs.
            var seen: Set<String> = []
            var uniqueEntities: [GraphEntity] = []
            for entity in input.entities where seen.insert(entity.nodeID).inserted {
                uniqueEntities.append(entity)
            }

            for entity in uniqueEntities {
                if nodes[entity.nodeID] == nil {
                    nodes[entity.nodeID] = GraphNode(
                        id: entity.nodeID,
                        kind: .entity,
                        label: entity.label,
                        metadata: ["type": entity.type]
                    )
                }
                edges.append(GraphEdge(
                    id: "edge:mentions:\(input.itemID.uuidString):\(entity.nodeID)",
                    sourceID: itemNodeID,
                    targetID: entity.nodeID,
                    kind: .mentions
                ))
            }

            // Co-occurrence between every unordered pair of entities in this item.
            for i in 0 ..< uniqueEntities.count {
                for j in (i + 1) ..< uniqueEntities.count {
                    let a = uniqueEntities[i].nodeID
                    let b = uniqueEntities[j].nodeID
                    let (lo, hi) = a < b ? (a, b) : (b, a)
                    edges.append(GraphEdge(
                        id: "edge:co_occurs:\(input.itemID.uuidString):\(lo):\(hi)",
                        sourceID: lo,
                        targetID: hi,
                        kind: .coOccurs
                    ))
                }
            }
        }

        return GraphSnapshot(nodes: Array(nodes.values), edges: edges)
    }
}
