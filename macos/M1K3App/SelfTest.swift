//
//  SelfTest.swift
//  M1K3App
//
//  Headless on-device proof. `swift test` can't run MLX (the metallib only
//  resolves inside an .app bundle), so this runs the real pipeline FROM WITHIN
//  the built app, gated on M1K3_SELFTEST=1, prints a report to stderr, and exits.
//  It's how we verify generation + embeddings actually execute on Metal without a
//  human driving the UI:
//
//      M1K3_SELFTEST=1 M1K3.app/Contents/MacOS/M1K3 2>report.log
//
//  Uses an in-memory store so it never touches the real container.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import Foundation
import M1K3Chat
import M1K3Inference
import M1K3Knowledge
import M1K3MLX

enum SelfTest {
    static var isRequested: Bool {
        ProcessInfo.processInfo.environment["M1K3_SELFTEST"] == "1"
    }

    /// Run every backend end-to-end and return a human-readable report. Each
    /// stage is independently guarded so one failure still reports the rest.
    static func run() async -> String {
        var out = ["=== M1K3 SELF-TEST (inside .app bundle) ==="]

        // 1. Store + ingest + Hashing embed + AFM RAG.
        do {
            let store = try KnowledgeStore() // in-memory
            let hashing = HashingEmbeddingService()
            let ingester = DocumentIngester(store: store, embedder: hashing)
            try await ingester.ingest(
                title: "Plant Notes",
                text: "3.2 Seals — the hydraulic seal on the conveyor failed under load."
            )
            try out.append("✓ store+ingest: \(store.chunkCount()) chunk, \(store.embeddingCount()) embedding(s)")

            let afm = AppleFoundationModelsProvider()
            out.append("• AFM available: \(afm.isAvailable)")
            if afm.isAvailable {
                let rag = RAGResponder(store: store, embedder: hashing, provider: afm)
                let r = try await rag.answer("What failed on the conveyor?")
                out.append("✓ AFM RAG answer: \(r.answer.prefix(180))")
                out.append("  grounded in \(r.sources.count) source(s)")
            }
        } catch {
            out.append("✗ store/AFM stage: \(error)")
        }

        // 2. MLX bge_small embedding (Metal).
        do {
            let mlx = MLXEmbeddingService()
            let v = try await mlx.embed("hydraulic seal conveyor")
            let norm = v.reduce(Float(0)) { $0 + $1 * $1 }.squareRoot()
            out.append("✓ MLX bge_small embed: dim=\(v.count) ‖v‖=\(String(format: "%.3f", norm))")
        } catch {
            out.append("✗ MLX embed stage: \(error)")
        }

        // 3. MLX Gemma generation (Metal).
        do {
            let gemma = MLXGemmaProvider(maxTokens: 48)
            let answer = try await gemma.generate(prompt: "In one short sentence, what is a hydraulic seal?")
            out.append("✓ MLX Gemma generate: \(answer.trimmingCharacters(in: .whitespacesAndNewlines).prefix(180))")
        } catch {
            out.append("✗ MLX Gemma stage: \(error)")
        }

        out.append("=== END SELF-TEST ===")
        return out.joined(separator: "\n")
    }
}
