// swift-tools-version: 6.2
//
// M1K3 — Mac-native MVP.
// Multi-module Swift package consumed by the SwiftUI app shell (added at the UI
// phase). Business logic lives here so `swift test` drives the TDD loop without
// the Xcode/simulator storm — same split as the prior knowledge-server project's the internal knowledge-server core.
//
// Target platform is macOS 26 (Tahoe): SwiftUI Liquid Glass + on-device
// Foundation Models. Heavy deps (MLX, GRDB, WhisperKit, swift-sdk) are added
// per-phase as the modules that need them land, to keep early builds quick.
//
// Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown
// Context: First Mac-native surface for M1K3. Scaffold begins with the pure,
// dependency-free knowledge primitives (VectorMath, RRFFusion) ported from
// the prior knowledge-server project so the foundation builds in seconds before MLX/GRDB enter the graph.

import PackageDescription

let package = Package(
    name: "M1K3",
    platforms: [.macOS(.v26)],
    products: [
        .library(name: "M1K3Knowledge", targets: ["M1K3Knowledge"]),
        .library(name: "M1K3Inference", targets: ["M1K3Inference"]),
        .library(name: "M1K3Agent", targets: ["M1K3Agent"]),
        .library(name: "M1K3KnowledgeTools", targets: ["M1K3KnowledgeTools"]),
        .library(name: "M1K3Chat", targets: ["M1K3Chat"]),
    ],
    dependencies: [
        .package(url: "https://github.com/groue/GRDB.swift.git", from: "7.0.0"),
    ],
    targets: [
        .target(
            name: "M1K3Knowledge",
            dependencies: [
                .product(name: "GRDB", package: "GRDB.swift"),
            ],
            path: "Sources/M1K3Knowledge"
        ),
        .testTarget(
            name: "M1K3KnowledgeTests",
            dependencies: ["M1K3Knowledge"],
            path: "Tests/M1K3KnowledgeTests"
        ),
        // Pluggable LLM runtime. The InferenceProvider protocol + router are
        // pure/testable; backends (Apple Foundation Models now, MLX/LiteRT
        // Gemma later) are thin adapters behind it. No external deps —
        // FoundationModels is a system framework on macOS 26.
        .target(
            name: "M1K3Inference",
            path: "Sources/M1K3Inference"
        ),
        .testTarget(
            name: "M1K3InferenceTests",
            dependencies: ["M1K3Inference"],
            path: "Tests/M1K3InferenceTests"
        ),
        // Local agent: ReAct loop + tool protocol. Pure logic over the
        // InferenceProvider seam — tools are injected, so it tests against
        // fakes with no model. Knowledge-backed tools wire in at the app layer.
        .target(
            name: "M1K3Agent",
            dependencies: ["M1K3Inference"],
            path: "Sources/M1K3Agent"
        ),
        .testTarget(
            name: "M1K3AgentTests",
            dependencies: ["M1K3Agent"],
            path: "Tests/M1K3AgentTests"
        ),
        // Agent tools backed by the knowledge layer — the bridge that makes the
        // LocalAgent able to actually search M1K3's memory. Depends on both the
        // agent (AgentTool) and knowledge (KnowledgeStore) modules.
        .target(
            name: "M1K3KnowledgeTools",
            dependencies: ["M1K3Agent", "M1K3Knowledge"],
            path: "Sources/M1K3KnowledgeTools"
        ),
        .testTarget(
            name: "M1K3KnowledgeToolsTests",
            dependencies: ["M1K3KnowledgeTools", "M1K3Inference"],
            path: "Tests/M1K3KnowledgeToolsTests"
        ),
        // RAG chat: embed → hybrid search → documents-first prompt → generate.
        // The grounded-answer brain. Depends on knowledge + inference.
        .target(
            name: "M1K3Chat",
            dependencies: ["M1K3Knowledge", "M1K3Inference"],
            path: "Sources/M1K3Chat"
        ),
        .testTarget(
            name: "M1K3ChatTests",
            dependencies: ["M1K3Chat"],
            path: "Tests/M1K3ChatTests"
        ),
    ]
)
