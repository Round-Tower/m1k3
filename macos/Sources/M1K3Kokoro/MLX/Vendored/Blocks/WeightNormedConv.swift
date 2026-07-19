//
//  WeightNormedConv.swift
//  M1K3Kokoro/MLX/Vendored
//
//  Vendored from Blaizzy/mlx-audio-swift (MIT), commit
//  542fffacb3be8de47024b3b54888f71d72d46d30, 2026-07-18. Unmodified from
//  upstream. See README.md in this directory for the vendoring rationale.
//

import Foundation
import MLX
import MLXNN

public func weightNorm(_ v: MLXArray, _ g: MLXArray, dim: Int = 0) -> MLXArray {
    let rank = v.ndim
    var axes = Array(0 ..< rank)
    let d = dim < 0 ? dim + rank : dim
    if d >= 0 && d < rank {
        axes.remove(at: d)
    }
    let normV = MLX.sqrt(MLX.sum(v * v, axes: axes, keepDims: true))
    return (v / (normV + 1e-7)) * g
}

public class WeightNormedConv: Module {
    public let stride: Int
    public let padding: Int
    public let dilation: Int
    public let groups: Int
    public let encode: Bool
    public var weight_g: MLXArray
    public var weight_v: MLXArray
    public var bias: MLXArray?

    public enum ConvOp {
        case conv1d
        case convTranspose1d
    }

    public init(
        inChannels: Int,
        outChannels: Int,
        kernelSize: Int,
        stride: Int = 1,
        padding: Int = 1,
        dilation: Int = 1,
        groups: Int = 1,
        bias: Bool = true,
        encode: Bool = false
    ) {
        self.stride = stride
        self.padding = padding
        self.dilation = dilation
        self.groups = groups
        self.encode = encode
        weight_g = MLXArray.ones([outChannels, 1, 1])
        weight_v = MLXArray.ones([outChannels, kernelSize, inChannels])
        self.bias = bias ? MLXArray.zeros([encode ? inChannels : outChannels]) : nil
    }

    public func callAsFunction(_ x: MLXArray, op: ConvOp = .conv1d) -> MLXArray {
        let weight = weightNorm(weight_v, weight_g, dim: 0)
        let biasVal = bias?.reshaped([1, 1, -1])

        func applyConv(_ input: MLXArray, _ w: MLXArray) -> MLXArray {
            let result: MLXArray
            switch op {
            case .conv1d:
                result = MLX.conv1d(
                    input, w,
                    stride: stride, padding: padding,
                    dilation: dilation, groups: groups
                )
            case .convTranspose1d:
                result = MLX.convTransposed1d(
                    input, w,
                    stride: stride, padding: padding,
                    dilation: dilation, groups: groups
                )
            }
            if let b = biasVal {
                return result + b
            }
            return result
        }

        if x.shape[x.ndim - 1] == weight.shape[weight.ndim - 1] || groups > 1 {
            return applyConv(x, weight)
        } else {
            return applyConv(x, weight.transposed())
        }
    }
}
