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
import MLXEmbedders

/// Resolves self-test configuration from the environment FIRST, then a JSON
/// override file in the app's container — so a run can be driven WITHOUT
/// `open --env`, which silently fails to inject on repeat LaunchServices
/// launches of the shared `app.m1k3` bundle id (it works once, then boots the
/// app idle). Drop `~/Library/Containers/app.m1k3/Data/.m1k3-selftest.json` — a
/// flat `{"M1K3_SELFTEST": "1", …}` map keyed by env-var name — launch the app
/// normally, and the selftest reads its config off disk. Env always wins when
/// both are set, so existing `open --env` / Xcode-scheme runs are unchanged.
///
/// Signed: Kev + claude-opus-4-8, 2026-06-15, Confidence 0.85 (file fallback
/// sidesteps the open --env flake end-to-end; env precedence preserves every
/// existing run path). Prior: Unknown
enum SelfTestEnv {
    /// The on-disk override map, loaded once. Keys are env-var names; values are
    /// coerced to strings (JSON bools → "1"/"0", numbers → their text) so the
    /// config reads the same as the environment it shadows.
    static let fileOverrides: [String: String] = {
        let path = (NSHomeDirectory() as NSString).appendingPathComponent(".m1k3-selftest.json")
        guard let data = FileManager.default.contents(atPath: path) else { return [:] }
        // One-shot: consume the trigger the moment it's read, so an ordinary
        // relaunch (or a crash mid-run) never leaves the app selftest-booting.
        // Drop the file again to run again. Read happens once (cached static),
        // so deleting now doesn't affect the in-memory config below.
        try? FileManager.default.removeItem(atPath: path)
        guard let object = try? JSONSerialization.jsonObject(with: data),
              let dictionary = object as? [String: Any]
        else { return [:] }
        var resolved: [String: String] = [:]
        for (key, raw) in dictionary {
            switch raw {
            case let bool as Bool: resolved[key] = bool ? "1" : "0"
            case let string as String: resolved[key] = string
            default: resolved[key] = "\(raw)"
            }
        }
        return resolved
    }()

    /// The environment value if present, else the on-disk override, else nil.
    static func value(_ key: String) -> String? {
        ProcessInfo.processInfo.environment[key] ?? fileOverrides[key]
    }
}

enum SelfTest {
    static var isRequested: Bool {
        SelfTestEnv.value("M1K3_SELFTEST") == "1"
    }

    /// Where the streamed report goes. A bundled GUI .app sends stdio to the
    /// unified log, not an inherited fd — so we write to a file we can read back.
    private static var outputPath: String {
        SelfTestEnv.value("M1K3_SELFTEST_OUT") ?? "/tmp/m1k3_selftest.log"
    }

    private static func truncateOutput() {
        try? Data().write(to: URL(fileURLWithPath: outputPath))
    }

    /// Whole milliseconds in a Duration, for the TTFT probe lines.
    private static func milliseconds(_ duration: Duration) -> Int {
        let parts = duration.components
        return Int(parts.seconds * 1000) + Int(parts.attoseconds / 1_000_000_000_000_000)
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

        // 2. MLX Qwen3-Embedding (Metal).
        emit("• loading MLX Qwen3-Embedding (downloads on first use)…")
        do {
            let mlx = MLXEmbeddingService()
            let v = try await mlx.embed("hydraulic seal conveyor")
            let norm = v.reduce(Float(0)) { $0 + $1 * $1 }.squareRoot()
            emit("✓ MLX Qwen3-Embedding embed: dim=\(v.count) ‖v‖=\(String(format: "%.3f", norm))")
        } catch {
            emit("✗ MLX embed stage: \(error)")
        }

        // 3. MLX generation (Metal). Uses a (likely cached) small model so the
        //    path is proven without a slow QAT download.
        let modelID = SelfTestEnv.value("M1K3_SELFTEST_MODEL")
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
            if SelfTestEnv.value("M1K3_SELFTEST_DEBUG") == "1" {
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
            if let loops = SelfTestEnv.value("M1K3_SELFTEST_MEMLOOP")
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

            // 3c. Optional TTFT probe (M1K3_SELFTEST_TTFT=1): wall-clock time
            // to first streamed token for a short prompt and a grounded-size
            // prompt — the A/B harness for prefill-cost changes. (Per-stage
            // prefill/decode metrics also land in the unified log, category
            // "ttft".)
            if SelfTestEnv.value("M1K3_SELFTEST_TTFT") == "1" {
                let grounded = String(
                    repeating: "KNOWLEDGE: conveyor belts run on rollers; hydraulic seals retain fluid under pressure; "
                        + "maintenance intervals follow load cycles. ",
                    count: 40
                ) + "\nIn one short sentence, what is a hydraulic seal?"
                let probes: [(String, String)] = [
                    ("short", "In one short sentence, what is a hydraulic seal?"),
                    ("grounded", grounded),
                ]
                for (label, prompt) in probes {
                    let clock = ContinuousClock()
                    let start = clock.now
                    var firstToken: Duration?
                    var characters = 0
                    for await chunk in llm.generateStreaming(prompt: prompt) {
                        // The synthetic <think> opener is emitted before any
                        // real generation — don't let it fake the first token.
                        if firstToken == nil, !chunk.isEmpty, chunk != "<think>" {
                            firstToken = clock.now - start
                        }
                        characters += chunk.count
                    }
                    let total = clock.now - start
                    let firstMS = firstToken.map { milliseconds($0) } ?? -1
                    emit("ttft \(label): first=\(firstMS)ms total=\(milliseconds(total))ms chars=\(characters)")
                }
            }

            await runKVPersistProbeIfRequested(llm: llm)

            // 3e. Optional persona-prefix invariant (M1K3_SELFTEST_PREFIX=1):
            // assert [cached system block] + [delta] == [full render] for this
            // model, and report the prefill tokens the cache saves. The safety
            // net for the Qwen two-probe boundary slice — and gemma's prefix too.
            if SelfTestEnv.value("M1K3_SELFTEST_PREFIX") == "1" {
                emit(await llm.personaPrefixInvariantProbe())
            }
        } catch {
            emit("✗ MLX generate stage: \(error)")
        }

        await runEvalSuiteIfRequested()

        // 5. Optional memory-threshold eval (M1K3_SELFTEST_MEMEVAL=1): embed
        //    the fixture pairs with the REAL BGE embedder and report the
        //    query→short-fact cosine distributions — the data that sets
        //    GroundingGate.memoryThreshold (the chunk bar was tuned on a
        //    different distribution; short facts sit lower in the cone).
        if SelfTestEnv.value("M1K3_SELFTEST_MEMEVAL") == "1" {
            await runMemoryThresholdEval()
        }

        // 6. Optional A/B separation eval (M1K3_SELFTEST_ABSEP=1): embed the
        //    in/off-domain fixtures with BOTH the old bge-small and the new
        //    qwen3-embed-512 and report which separates the classes wider.
        //    This is what justifies the embedder swap — measured, not trusted —
        //    and feeds the GroundingGate threshold re-tune.
        if SelfTestEnv.value("M1K3_SELFTEST_ABSEP") == "1" {
            await runSeparationEval()
        }

        // 7. Optional chat-quality eval (M1K3_SELFTEST_CHATEVAL=1): run the
        //    task-kind fixtures against each brain through the REAL providers
        //    and score them with the pure M1K3Eval scorer — the cross-brain
        //    matrix that turns "AFM feels weaker at chat" into a number and
        //    becomes the EscalationLadder policy's evidence.
        if ChatEvalStage.isRequested {
            await ChatEvalStage.run(emit: emit)
        }

        emit("=== END SELF-TEST ===")
    }

    /// 4. Optional per-model eval suite (M1K3_SELFTEST_EVAL=1): the same
    /// behavioral checklist against every brain — or any ad-hoc list via
    /// M1K3_SELFTEST_EVAL_MODELS=id,id. Promotion gate for new models.
    private static func runEvalSuiteIfRequested() async {
        guard SelfTestEnv.value("M1K3_SELFTEST_EVAL") == "1" else { return }
        let models = SelfTestEnv.value("M1K3_SELFTEST_EVAL_MODELS")
            .map { $0.split(separator: ",").map { String($0).trimmingCharacters(in: .whitespaces) } }
            ?? BrainTier.allCases.compactMap(\.mlxModelID)
        for modelID in models {
            emit("• eval \(modelID)…")
            let report = await evalModel(modelID)
            emit(report.rendered)
        }
    }

    /// 5. The MEMEVAL pass: one cosine per fixture pair, positives then
    /// negatives, then the distribution summary + suggested threshold. Each
    /// line lands in the OUT file as it's measured (interrupt-safe).
    private static func runMemoryThresholdEval() async {
        emit("• memeval: embedding \(MemoryEvalFixtures.positives.count) positive + "
            + "\(MemoryEvalFixtures.negatives.count) negative pairs…")
        do {
            let embedder = MLXEmbeddingService()
            let positiveScores = try await score(
                pairs: MemoryEvalFixtures.positives, label: "pos", embedder: embedder
            )
            let negativeScores = try await score(
                pairs: MemoryEvalFixtures.negatives, label: "neg", embedder: embedder
            )
            emit(MemoryEvalReport.render(positives: positiveScores, negatives: negativeScores))
        } catch {
            emit("✗ memeval: \(error)")
        }
    }

    /// Cosine per pair, emitted as measured so a crash mid-run loses nothing.
    private static func score(
        pairs: [MemoryEvalFixtures.Pair], label: String, embedder: MLXEmbeddingService
    ) async throws -> [Float] {
        let memoryVectors = try await embedder.embedBatch(pairs.map(\.memory))
        let queryVectors = try await embedder.embedBatch(pairs.map(\.query))
        var scores: [Float] = []
        for (index, pair) in pairs.enumerated() {
            let cosine = VectorMath.cosineSimilarity(queryVectors[index], memoryVectors[index])
            scores.append(cosine)
            let preview = String(pair.memory.prefix(40))
            emit(String(format: "memeval %@: %.3f [%@]", label, cosine, preview))
        }
        return scores
    }

    /// 6. The ABSEP pass: in/off-domain query→document cosines for the OLD
    /// (bge-small-384) and NEW (qwen3-embed-512) embedder, then the
    /// head-to-head margin verdict. The candidate must separate the classes at
    /// least as wide as bge, else the swap isn't justified (the ABSEP gate).
    private static func runSeparationEval() async {
        emit("• absep: \(SeparationEvalFixtures.inDomain.count) in-domain + "
            + "\(SeparationEvalFixtures.offDomain.count) off-domain pairs, bge-small-384 vs qwen3-embed-512…")
        do {
            // Old embedder stood up explicitly beside the new default — the init
            // params survived the default change precisely for this.
            let bge = MLXEmbeddingService(configuration: EmbedderRegistry.bge_small, dimension: 384)
            let candidate = MLXEmbeddingService() // new default = qwen3-embed-512

            let bgeIn = try await scoreSeparation(pairs: SeparationEvalFixtures.inDomain, label: "bge in", embedder: bge)
            let bgeOff = try await scoreSeparation(pairs: SeparationEvalFixtures.offDomain, label: "bge off", embedder: bge)
            let candIn = try await scoreSeparation(pairs: SeparationEvalFixtures.inDomain, label: "qwen3 in", embedder: candidate)
            let candOff = try await scoreSeparation(pairs: SeparationEvalFixtures.offDomain, label: "qwen3 off", embedder: candidate)

            let bgeResult = SeparationEvalReport.Result(label: "bge-small-384", inDomain: bgeIn, offDomain: bgeOff)
            let candidateResult = SeparationEvalReport.Result(label: "qwen3-embed-512", inDomain: candIn, offDomain: candOff)
            // candidate second → the head-to-head verdict describes it vs bge.
            emit(SeparationEvalReport.render([bgeResult, candidateResult]))
        } catch {
            emit("✗ absep: \(error)")
        }
    }

    /// Cosine per (query, document) pair, emitted as measured so a crash
    /// mid-run loses nothing.
    private static func scoreSeparation(
        pairs: [SeparationEvalFixtures.Pair], label: String, embedder: MLXEmbeddingService
    ) async throws -> [Float] {
        let queryVectors = try await embedder.embedBatch(pairs.map(\.query))
        let docVectors = try await embedder.embedBatch(pairs.map(\.document))
        var scores: [Float] = []
        for (index, pair) in pairs.enumerated() {
            let cosine = VectorMath.cosineSimilarity(queryVectors[index], docVectors[index])
            scores.append(cosine)
            emit(String(format: "absep %@: %.3f [%@]", label, cosine, String(pair.query.prefix(40))))
        }
        return scores
    }

    /// 3d. Optional prompt-cache persistence probe (M1K3_SELFTEST_KVPERSIST=1):
    /// persona-prefix KV → disk → reload → generate from the reloaded cache.
    /// The prototype gate for persisting PersonaPrefixCache across launches.
    private static func runKVPersistProbeIfRequested(llm: MLXGemmaProvider) async {
        guard SelfTestEnv.value("M1K3_SELFTEST_KVPERSIST") == "1" else { return }
        emit(await llm.promptCacheRoundTripProbe(
            directory: FileManager.default.temporaryDirectory
        ))
    }

    /// One model through the behavioral checklist. Self-contained on purpose:
    /// the reasoning-family detection reads the output itself (the provider's
    /// synthetic <think> opener IS the contract being checked), so a new model
    /// needs no eval-side configuration at all.
    private static func evalModel(_ modelID: String) async -> ModelEvalReport {
        var records: [ModelEvalRecord] = []
        // 2048, not the chat default: a reasoning model can spend 200–800
        // tokens inside <think> before the one-word answer the think-contract
        // check wants — 1024 risked false truncation FAILs on verbose models.
        let provider = MLXGemmaProvider(modelID: modelID, maxTokens: 2048)

        // Check 1+2: generates a usable answer; reasoning families emit a
        // well-formed think pair (the Qwen3.5 lone-close bug class).
        do {
            let raw = try await provider.generate(
                prompt: "Reply with exactly the word OK and nothing else."
            )
            let answer = ModelEvalReport.strippingThink(raw)
            let generated = answer.localizedCaseInsensitiveContains("ok")
            records.append(ModelEvalRecord(
                check: "generates",
                outcome: generated ? .pass : .fail,
                detail: generated ? "\(raw.count) chars" : "answer: \(answer.prefix(60))"
            ))
            if raw.hasPrefix("<think>") {
                let verdict = ThinkContract.verdict(raw: raw)
                records.append(ModelEvalRecord(
                    check: "think contract",
                    outcome: verdict.outcome,
                    detail: verdict.detail
                ))
            } else {
                records.append(ModelEvalRecord(
                    check: "think contract", outcome: .skip, detail: "not a reasoning family"
                ))
            }
        } catch {
            records.append(ModelEvalRecord(
                check: "generates", outcome: .fail, detail: String(describing: error).prefix(80) + ""
            ))
            return ModelEvalReport(modelID: modelID, records: records)
        }

        // Check 3: the native tool-call dialect actually round-trips — the
        // model SEES the tool (template renders it) and the library PARSES the
        // call (the Gemma-3n silent-drop + missing-parser bug class).
        if provider.supportsToolCalls {
            do {
                let datetime = ToolDefinition(
                    name: "datetime",
                    description: "Get the current date and time on this Mac.",
                    parameters: []
                )
                let turn = try await provider.continueToolTurn(
                    messages: [
                        .system(M1K3Persona.systemPrompt),
                        .user("What time is it right now? Use the datetime tool to find out."),
                    ],
                    tools: [datetime]
                )
                switch turn {
                case let .toolCalls(calls) where calls.contains(where: { $0.name == "datetime" }):
                    records.append(ModelEvalRecord(
                        check: "native tool call", outcome: .pass, detail: "datetime called"
                    ))
                case let .toolCalls(calls):
                    records.append(ModelEvalRecord(
                        check: "native tool call", outcome: .fail,
                        detail: "called \(calls.map(\.name).joined(separator: ",")) instead"
                    ))
                case let .text(text):
                    records.append(ModelEvalRecord(
                        check: "native tool call", outcome: .fail,
                        detail: "no call — answered: \(ModelEvalReport.strippingThink(text).prefix(60))"
                    ))
                }
            } catch {
                records.append(ModelEvalRecord(
                    check: "native tool call", outcome: .fail,
                    detail: String(describing: error).prefix(80) + ""
                ))
            }
        } else {
            records.append(ModelEvalRecord(
                check: "native tool call", outcome: .skip,
                detail: "ReAct floor — no native dialect at this pin"
            ))
        }

        // Check 4: needle recall across a few-thousand-token prompt — the only
        // check that exercises a LONG KV cache (8-bit quantized on allow-listed
        // families, rotation backstop elsewhere). Short prompts cannot catch
        // cache-quality loss.
        records.append(await evalLongContextRecall(provider: provider))

        return ModelEvalReport(modelID: modelID, records: records)
    }

    private static func evalLongContextRecall(provider: MLXGemmaProvider) async -> ModelEvalRecord {
        do {
            let raw = try await provider.generate(prompt: LongContextRecall.prompt())
            let answer = ModelEvalReport.strippingThink(raw)
            let recalled = LongContextRecall.passes(answer)
            return ModelEvalRecord(
                check: "long-context recall",
                outcome: recalled ? .pass : .fail,
                detail: recalled ? "needle recalled" : "answer: \(answer.prefix(60))"
            )
        } catch {
            return ModelEvalRecord(
                check: "long-context recall", outcome: .fail,
                detail: String(describing: error).prefix(80) + ""
            )
        }
    }
}
