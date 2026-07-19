//
//  LinearNorm.swift
//  M1K3Kokoro/MLX/Vendored
//
//  Vendored from Blaizzy/mlx-audio-swift (MIT), commit
//  542fffacb3be8de47024b3b54888f71d72d46d30, 2026-07-18. Unmodified from
//  upstream. See README.md in this directory for the vendoring rationale.
//

import Foundation
import MLX
import MLXNN

public class LinearNorm: Module {
    @ModuleInfo(key: "linear_layer") public var linearLayer: Linear

    public init(inDim: Int, outDim: Int) {
        _linearLayer = ModuleInfo(wrappedValue: Linear(inDim, outDim), key: "linear_layer")
    }

    public func callAsFunction(_ x: MLXArray) -> MLXArray {
        linearLayer(x)
    }
}
