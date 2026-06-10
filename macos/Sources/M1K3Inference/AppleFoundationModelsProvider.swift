//
//  AppleFoundationModelsProvider.swift
//  M1K3Inference
//
//  InferenceProvider backed by Apple's on-device Foundation Models. M1K3's
//  cheap/fast tier — short turns, the Tier-1 call summary, anything that
//  doesn't need Gemma 4's depth. Thin OS adapter: the testable routing logic
//  lives in ProviderRouter, so this file is verified by compiling against the
//  macOS 26 SDK + a name check, not by invoking the model (which needs Apple
//  Intelligence hardware).
//
//  Mirrors the internal call-pipeline project's AppleFoundationModelsProvider.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.75,
//  Prior: the internal call-pipeline project AppleFoundationModelsProvider (Kev)

import Foundation
import FoundationModels

public struct AppleFoundationModelsProvider: InferenceProvider {
    public let name = "apple-foundation-models"

    public init() {}

    public var isAvailable: Bool {
        switch SystemLanguageModel.default.availability {
        case .available:
            return true
        default:
            return false
        }
    }

    public func generate(prompt: String) async throws -> String {
        let session = LanguageModelSession(instructions: M1K3Persona.systemPrompt)
        let response = try await session.respond(to: prompt)
        return response.content
    }

    public func generateStreaming(prompt: String) -> AsyncStream<String> {
        AsyncStream { continuation in
            let task = Task {
                do {
                    let session = LanguageModelSession(instructions: M1K3Persona.systemPrompt)
                    let stream = session.streamResponse(to: prompt)
                    for try await snapshot in stream {
                        continuation.yield(snapshot.content)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish()
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}
