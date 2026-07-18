//
//  Utilities.swift
//  M1K3Kokoro/MLX/Vendored
//
//  Vendored from Blaizzy/mlx-audio-swift (MIT), commit
//  542fffacb3be8de47024b3b54888f71d72d46d30, 2026-07-18. Unmodified from
//  upstream. See README.md in this directory for the vendoring rationale.
//

import Foundation
import MLX

// MARK: - 1D Linear Interpolation

public func interpolate1d(_ input: MLXArray, size: Int) -> MLXArray {
    let inWidth = input.shape[2]
    if inWidth == size { return input }
    if inWidth < 1 || size < 1 { return input }

    let scale = Float(inWidth) / Float(size)
    var xCoords = MLXArray(Array((0 ..< size).map { Float($0) * scale + 0.5 * scale - 0.5 }))
    xCoords = MLX.clip(xCoords, min: 0, max: Float(inWidth - 1))

    let xLow = MLX.floor(xCoords).asType(.int32)
    let xHigh = MLX.minimum(xLow + 1, MLXArray(Int32(inWidth - 1)))
    let xFrac = xCoords - xLow.asType(.float32)

    let yLow = input[0..., 0..., xLow]
    let yHigh = input[0..., 0..., xHigh]
    let fracExpanded = xFrac.reshaped([1, 1, size])
    return yLow * (1 - fracExpanded) + yHigh * fracExpanded
}
