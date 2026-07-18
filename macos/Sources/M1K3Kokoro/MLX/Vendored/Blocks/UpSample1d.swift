//
//  UpSample1d.swift
//  M1K3Kokoro/MLX/Vendored
//
//  Vendored from Blaizzy/mlx-audio-swift (MIT), commit
//  542fffacb3be8de47024b3b54888f71d72d46d30, 2026-07-18. Unmodified from
//  upstream. See README.md in this directory for the vendoring rationale.
//

import Foundation
import MLX
import MLXNN

public class UpSample1d: Module {
    public let layerType: String

    public init(layerType: String = "none") {
        self.layerType = layerType
    }

    public func callAsFunction(_ x: MLXArray) -> MLXArray {
        if layerType == "none" { return x }
        return Upsample(scaleFactor: .float(2), mode: .nearest)(x)
    }
}
