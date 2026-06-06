//
//  RuntimeInferenceProvider.swift
//  M1K3App
//
//  A façade that forwards to whichever backend the runtime picker has selected,
//  so flipping AFM ↔ MLX Gemma in Settings changes who answers the *next* turn
//  without rebuilding the RAGResponder or ChatSession (the chat transcript
//  survives the swap). The selection lives in a lock-protected box so this
//  Sendable provider can read it off the main actor at generate-time while the
//  @Observable UI mutates it on the main actor.
//
//  Generation only — the embedder is deliberately NOT switched here: it defines
//  the stored vector space, so swapping it would require a re-index.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import Foundation
import M1K3Inference

/// Thread-safe holder for the picker's current selection. The @Observable
/// AppEnvironment writes it on the main actor; the façade reads it anywhere.
final class RuntimeSelectionBox: @unchecked Sendable {
    private let lock = NSLock()
    private var stored: RuntimeOption

    init(_ initial: RuntimeOption) {
        stored = initial
    }

    var value: RuntimeOption {
        get { lock.withLock { stored } }
        set { lock.withLock { stored = newValue } }
    }
}

/// Routes each generation to the selected backend. Unknown / not-yet-wired
/// selections fall back to Apple Foundation Models.
final class RuntimeInferenceProvider: InferenceProvider, @unchecked Sendable {
    let name = "runtime"

    private let selection: RuntimeSelectionBox
    private let backends: [RuntimeOption: any InferenceProvider]
    private let fallback: any InferenceProvider

    init(
        selection: RuntimeSelectionBox,
        backends: [RuntimeOption: any InferenceProvider],
        fallback: any InferenceProvider
    ) {
        self.selection = selection
        self.backends = backends
        self.fallback = fallback
    }

    private var active: any InferenceProvider {
        backends[selection.value] ?? fallback
    }

    var isAvailable: Bool {
        active.isAvailable
    }

    func generate(prompt: String) async throws -> String {
        try await active.generate(prompt: prompt)
    }

    func generateStreaming(prompt: String) -> AsyncStream<String> {
        active.generateStreaming(prompt: prompt)
    }
}
