//
//  VectorMath.swift
//  M1K3Knowledge
//
//  Pure math utilities for vector operations — cosine similarity,
//  serialization to/from BLOB. No dependencies beyond Foundation.
//
//  MurphySig: Semantic data layer — vector math.
//  Confidence: HIGH. Pure functions, no state, Accelerate-ready.
//  https://murphysig.dev
//
//  ── Review ───────────────────────────────────────────────────────────────
//  Ported verbatim into M1K3Knowledge from the prior knowledge-server project's the internal knowledge-server core/VectorMath.swift
//  to back the Mac-native semantic store. Logic unchanged; only the module
//  comment differs.
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.95,
//  Prior: the prior knowledge-server project the internal knowledge-server core/VectorMath.swift (Kev, HIGH confidence)

import Foundation

public enum VectorMath {
    /// Cosine similarity between two Float vectors. Returns 0.0 for zero-length vectors.
    public static func cosineSimilarity(_ a: [Float], _ b: [Float]) -> Float {
        guard a.count == b.count, !a.isEmpty else { return 0.0 }

        var dotProduct: Float = 0
        var normA: Float = 0
        var normB: Float = 0

        for i in 0 ..< a.count {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        let denominator = sqrt(normA) * sqrt(normB)
        guard denominator > 0 else { return 0.0 }
        return dotProduct / denominator
    }

    /// Return `vector` scaled to unit L2 length, preserving direction. A
    /// zero-length (or empty) vector is returned unchanged — never NaN.
    public static func l2Normalized(_ vector: [Float]) -> [Float] {
        var sumSquares: Float = 0
        for value in vector {
            sumSquares += value * value
        }
        let norm = sqrt(sumSquares)
        guard norm > 0 else { return vector }
        return vector.map { $0 / norm }
    }

    /// Serialize a Float vector to Data (little-endian, 4 bytes per element).
    /// 768-dim vector → 3072 bytes.
    public static func serialize(_ vector: [Float]) -> Data {
        vector.withUnsafeBufferPointer { buffer in
            Data(buffer: buffer)
        }
    }

    /// Deserialize Data back to a Float vector.
    public static func deserialize(_ data: Data) -> [Float] {
        let count = data.count / MemoryLayout<Float>.size
        return data.withUnsafeBytes { raw in
            let buffer = raw.bindMemory(to: Float.self)
            return Array(buffer.prefix(count))
        }
    }
}
