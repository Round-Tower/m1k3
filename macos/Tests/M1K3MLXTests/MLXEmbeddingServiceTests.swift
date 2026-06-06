//
//  MLXEmbeddingServiceTests.swift
//  M1K3MLXTests
//
//  Two tiers. The fast tier asserts the pure surface (protocol conformance,
//  declared dimension) with no model load — runs in CI / the normal loop. The
//  integration tier actually downloads + runs the model and is gated behind the
//  M1K3_MLX_INTEGRATION env var, because it needs network + Metal + ~minutes on
//  first run. Same split the prior knowledge-server project uses to keep its fast loop fast.
//
//  KNOWN LIMIT (2026-06-06): the integration tier does NOT run under CLI
//  `swift test` — MLX aborts with "Failed to load the default metallib (library
//  not found)" because mlx-swift resolves its Metal kernels relative to the
//  running binary, and the xctest runner isn't an .app bundle. Real on-device
//  execution is verified by launching M1K3.app (which bundles the metallib) or
//  via an xctest host app. The gate stays env-var'd so normal runs are clean;
//  flipping it from the CLI will abort, by design of MLX, not a code fault.
//
//  Run integration (needs app-bundle context): M1K3_MLX_INTEGRATION=1
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import Foundation
import M1K3Knowledge
@testable import M1K3MLX
import Testing

struct MLXEmbeddingServiceTests {
    @Test("conforms to EmbeddingService and reports its dimension")
    func dimensionAndConformance() {
        let service: any EmbeddingService = MLXEmbeddingService()
        #expect(service.dimension == 384) // bge_small default
    }

    @Test(
        "INTEGRATION: embeds text into a unit-length vector of the right size",
        .enabled(if: ProcessInfo.processInfo.environment["M1K3_MLX_INTEGRATION"] == "1")
    )
    func embedsRealText() async throws {
        let service = MLXEmbeddingService()
        #expect(await service.isAvailable())

        let vector = try await service.embed("The hydraulic seal failed under load.")
        #expect(vector.count == service.dimension)

        // bge_small normalises — magnitude should be ~1.
        let magnitude = (vector.reduce(0) { $0 + $1 * $1 }).squareRoot()
        #expect(abs(magnitude - 1.0) < 0.01)
    }
}
