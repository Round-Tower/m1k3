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
        .library(name: "M1K3Voice", targets: ["M1K3Voice"]),
        .library(name: "M1K3MLX", targets: ["M1K3MLX"]),
        .library(name: "M1K3WhisperKit", targets: ["M1K3WhisperKit"]),
        .library(name: "M1K3Calls", targets: ["M1K3Calls"]),
        .library(name: "M1K3Avatar", targets: ["M1K3Avatar"]),
        .library(name: "M1K3Kokoro", targets: ["M1K3Kokoro"]),
    ],
    dependencies: [
        .package(url: "https://github.com/groue/GRDB.swift.git", from: "7.0.0"),
        // On-device embeddings + Gemma generation on Apple Silicon (Metal).
        // Same package family the prior knowledge-server project ships MLXEmbedders from; isolated to the
        // M1K3MLX target so the heavy Metal build never touches the core tests.
        .package(url: "https://github.com/ml-explore/mlx-swift-lm.git", from: "2.29.0"),
        // Official MCP Swift SDK — the M1K3MCP stdio server exposes M1K3's
        // knowledge to Claude Desktop/Code as MCP tools.
        .package(url: "https://github.com/modelcontextprotocol/swift-sdk.git", from: "0.7.0"),
        // WhisperKit — high-accuracy on-device transcription (the P6 primary
        // engine). Heavy (CoreML + model download), so it's isolated to the
        // M1K3WhisperKit target; Apple Speech (system framework) is the
        // always-available fallback behind the same TranscriptionProvider seam.
        .package(url: "https://github.com/argmaxinc/WhisperKit.git", from: "0.15.0"),
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
        // Voice. TTS now (AVSpeech behind SpeechProvider; Kokoro later);
        // transcription (WhisperKit) joins this module in the heavy-dep session.
        // AVFoundation is a system framework — no third-party dep.
        .target(
            name: "M1K3Voice",
            path: "Sources/M1K3Voice"
        ),
        .testTarget(
            name: "M1K3VoiceTests",
            dependencies: ["M1K3Voice"],
            path: "Tests/M1K3VoiceTests"
        ),
        // The heavy on-device backends, isolated here so nothing else links
        // MLX/Metal. MLXEmbeddingService conforms to EmbeddingService; the
        // MLXGemmaProvider conforms to InferenceProvider — both swap in behind
        // the same seams the core already tests against fakes.
        .target(
            name: "M1K3MLX",
            dependencies: [
                "M1K3Knowledge",
                "M1K3Inference",
                .product(name: "MLXEmbedders", package: "mlx-swift-lm"),
                .product(name: "MLXLLM", package: "mlx-swift-lm"),
                .product(name: "MLXLMCommon", package: "mlx-swift-lm"),
            ],
            path: "Sources/M1K3MLX"
        ),
        .testTarget(
            name: "M1K3MLXTests",
            dependencies: ["M1K3MLX", "M1K3Knowledge", "M1K3Inference"],
            path: "Tests/M1K3MLXTests"
        ),
        // MCP server library: knowledge-tool handlers (pure, testable) + the
        // stdio server wiring. Split from the executable so the tools can be
        // unit-tested (executable targets are awkward to @testable import).
        .target(
            name: "M1K3MCPKit",
            dependencies: [
                "M1K3Knowledge",
                .product(name: "MCP", package: "swift-sdk"),
            ],
            path: "Sources/M1K3MCPKit"
        ),
        .testTarget(
            name: "M1K3MCPKitTests",
            dependencies: ["M1K3MCPKit", "M1K3Knowledge"],
            path: "Tests/M1K3MCPKitTests"
        ),
        // The thin executable Claude Desktop/Code spawns — just runs the server.
        .executableTarget(
            name: "M1K3MCP",
            dependencies: ["M1K3MCPKit"],
            path: "Sources/M1K3MCP"
        ),
        // Call intelligence — the model-AGNOSTIC seam (this is the reusable IP, per
        // the challenger pass): batch-transcription + diarization + summarization
        // protocols, the pure DiarizationAligner, and a two-stage summary pipeline.
        // Concrete engines (WhisperKit-batch, FluidAudio, Gemma-shadow) plug in
        // behind the protocols; none are linked here. Depends on M1K3Inference for
        // the summary tier and M1K3Knowledge so finished calls become graph nodes.
        .target(
            name: "M1K3Calls",
            dependencies: [
                "M1K3Inference",
                "M1K3Knowledge",
                .product(name: "GRDB", package: "GRDB.swift"),
            ],
            path: "Sources/M1K3Calls"
        ),
        .testTarget(
            name: "M1K3CallsTests",
            dependencies: [
                "M1K3Calls", "M1K3Knowledge", "M1K3Inference",
                .product(name: "GRDB", package: "GRDB.swift"),
            ],
            path: "Tests/M1K3CallsTests"
        ),
        // WhisperKit transcription, isolated like M1K3MLX so only this target
        // (and the app) link the heavy CoreML/model machinery. Conforms to
        // M1K3Voice's TranscriptionProvider.
        .target(
            name: "M1K3WhisperKit",
            dependencies: [
                "M1K3Voice",
                // M1K3Calls owns the BatchTranscriptionProvider seam + CallTranscriptSegment
                // that WhisperKitBatchTranscriber conforms to / produces (light deps only).
                "M1K3Calls",
                // M1K3Inference owns SingleFlightLoader — coalesces concurrent model
                // loads so a preload racing the first transcribe shares ONE download.
                "M1K3Inference",
                .product(name: "WhisperKit", package: "WhisperKit"),
            ],
            path: "Sources/M1K3WhisperKit"
        ),
        .testTarget(
            name: "M1K3WhisperKitTests",
            dependencies: ["M1K3WhisperKit", "M1K3Voice", "M1K3Calls"],
            path: "Tests/M1K3WhisperKitTests"
        ),
        // Avatar companion: pure types (emotion, activity, state, tool→emotion
        // mapping, animation resolver) + RealityKit view + Observable controller.
        // RealityKit/SwiftUI are system frameworks — no third-party dep.
        .target(
            name: "M1K3Avatar",
            path: "Sources/M1K3Avatar"
        ),
        .testTarget(
            name: "M1K3AvatarTests",
            dependencies: ["M1K3Avatar"],
            path: "Tests/M1K3AvatarTests"
        ),
        // M1K3 Voice — the premium neural TTS tier (Kokoro). Isolated like
        // M1K3MLX/M1K3WhisperKit so only this target (and the app) will link the
        // ONNX runtime + G2P phonemizer once the synthesis spike lands. This
        // session it carries the SpeechProvider scaffold + model-download
        // (ModelPreloading) only — NO heavy dep yet, so the build stays fast and
        // the working MLX/WhisperKit stack is untouched. Conforms to M1K3Voice's
        // SpeechProviderWithLifecycle and M1K3Inference's ModelPreloading.
        .target(
            name: "M1K3Kokoro",
            dependencies: ["M1K3Voice", "M1K3Inference"],
            path: "Sources/M1K3Kokoro"
        ),
    ]
)
