//
//  SelfTest.swift
//  M1K3App
//
//  Headless on-device proof. `swift test` can't run MLX (the metallib only
//  resolves inside an .app bundle), so this runs the real pipeline FROM WITHIN
//  the built app, gated on M1K3_SELFTEST=1, and STREAMS each stage to stderr as
//  it completes (so a slow/hung stage still shows the earlier results), then
//  exits:
//
//      M1K3_SELFTEST=1 M1K3.app/Contents/MacOS/M1K3 2>report.log
//
//  The generation stage uses a model id from M1K3_SELFTEST_MODEL (default a small
//  cached model) so it proves the MLXLLM path without a fresh download — the
//  product default (gemma-3-1b QAT) can be slow to fetch on first run.
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

    /// Where the streamed report goes. A bundled GUI .app sends stdio to the
    /// unified log, not an inherited fd — so we write to a file we can read back.
    private static var outputPath: String {
        ProcessInfo.processInfo.environment["M1K3_SELFTEST_OUT"] ?? "/tmp/m1k3_selftest.log"
    }

    private static func truncateOutput() {
        try? Data().write(to: URL(fileURLWithPath: outputPath))
    }

    /// Append a line to the report file immediately so progress survives an
    /// interrupted run.
    private static func emit(_ line: String) {
        let data = Data((line + "\n").utf8)
        if let handle = FileHandle(forWritingAtPath: outputPath) {
            handle.seekToEndOfFile()
            handle.write(data)
            try? handle.close()
        } else {
            try? data.write(to: URL(fileURLWithPath: outputPath))
        }
    }

    /// Run every backend end-to-end, emitting each result as it lands. Each stage
    /// is independently guarded so one failure (or stall) still reports the rest.
    static func run() async {
        truncateOutput()
        emit("=== M1K3 SELF-TEST (inside .app bundle) ===")

        // 1. Store + ingest + Hashing embed + AFM RAG.
        do {
            let store = try KnowledgeStore() // in-memory
            let hashing = HashingEmbeddingService()
            let ingester = DocumentIngester(store: store, embedder: hashing)
            try await ingester.ingest(
                title: "Plant Notes",
                text: "3.2 Seals\nThe hydraulic seal on the conveyor failed under load."
            )
            try emit("✓ store+ingest: \(store.chunkCount()) chunk, \(store.embeddingCount()) embedding(s)")

            let afm = AppleFoundationModelsProvider()
            emit("• AFM available: \(afm.isAvailable)")
            if afm.isAvailable {
                let rag = RAGResponder(store: store, embedder: hashing, provider: afm)
                let r = try await rag.answer("What failed on the conveyor?")
                emit("✓ AFM RAG answer: \(r.answer.trimmingCharacters(in: .whitespacesAndNewlines).prefix(180))")
                emit("  grounded in \(r.sources.count) source(s)")
            }
        } catch {
            emit("✗ store/AFM stage: \(error)")
        }

        // 2. MLX bge_small embedding (Metal).
        emit("• loading MLX bge_small (downloads on first use)…")
        do {
            let mlx = MLXEmbeddingService()
            let v = try await mlx.embed("hydraulic seal conveyor")
            let norm = v.reduce(Float(0)) { $0 + $1 * $1 }.squareRoot()
            emit("✓ MLX bge_small embed: dim=\(v.count) ‖v‖=\(String(format: "%.3f", norm))")
        } catch {
            emit("✗ MLX embed stage: \(error)")
        }

        // 3. MLX generation (Metal). Uses a (likely cached) small model so the
        //    path is proven without a slow QAT download.
        let modelID = ProcessInfo.processInfo.environment["M1K3_SELFTEST_MODEL"]
            ?? "mlx-community/Llama-3.2-1B-Instruct-4bit"
        emit("• loading MLX generation model \(modelID)…")
        do {
            let llm = MLXGemmaProvider(modelID: modelID, maxTokens: 48)
            let answer = try await llm.generate(prompt: "In one short sentence, what is a hydraulic seal?")
            emit("✓ MLX generate: \(answer.trimmingCharacters(in: .whitespacesAndNewlines).prefix(180))")

            // Raw-output diagnostics (M1K3_SELFTEST_DEBUG=1): when an answer
            // comes back empty, show what the model ACTUALLY returned and how
            // the chat template rendered — distinguishes "model emitted EOS
            // immediately" from "detokenizer produced nothing".
            if ProcessInfo.processInfo.environment["M1K3_SELFTEST_DEBUG"] == "1" {
                emit("debug raw answer: count=\(answer.count) [\(answer.prefix(300))]")
                let debug = await llm.templateDebugDescription(
                    prompt: "In one short sentence, what is a hydraulic seal?"
                )
                emit("debug template: \(debug)")
            }

            // 3b. Optional memory loop (M1K3_SELFTEST_MEMLOOP=N): N more
            // generations with a footprint snapshot after each. An agent turn
            // runs generations back-to-back, so a growing footprint here is
            // exactly the unbounded-Metal-cache pathology; a flat one means the
            // budget holds.
            if let loops = ProcessInfo.processInfo.environment["M1K3_SELFTEST_MEMLOOP"]
                .flatMap(Int.init), loops > 0
            {
                emit(MLXMemoryBudget.snapshotDescription(label: "memloop start"))
                for index in 1 ... loops {
                    _ = try await llm.generate(
                        prompt: "In two sentences, explain fact #\(index) about industrial conveyor maintenance."
                    )
                    emit(MLXMemoryBudget.snapshotDescription(label: "memloop gen \(index)"))
                }
            }
        } catch {
            emit("✗ MLX generate stage: \(error)")
        }

        emit("=== END SELF-TEST ===")
    }
}
