//
//  KokoroG2P+Bundled.swift
//  M1K3Kokoro
//
//  Loads the bundled espeak-en-gb pronunciation dictionary into a KokoroG2P. The
//  resource `g2p-en-gb.deflate` is `[UInt32 LE uncompressedSize][raw DEFLATE bytes]`
//  (~2 MB compressed from ~11 MB of `word<TAB>id,id,…` lines), inflated at load via
//  Apple's Compression framework — no third-party dependency.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-09, Confidence 0.7, Prior: Unknown
//

import Compression
import Foundation

public extension KokoroG2P {
    enum LoadError: Error {
        case resourceMissing(String)
        case inflateFailed
    }

    /// Resource basename of the bundled dictionary (without extension).
    static let bundledResource = "g2p-en-gb"

    /// Build a KokoroG2P from the bundled, compressed dictionary resource.
    static func bundled() throws -> KokoroG2P {
        guard let url = Bundle.module.url(forResource: bundledResource, withExtension: "deflate") else {
            throw LoadError.resourceMissing("\(bundledResource).deflate")
        }
        let text = try inflate(Data(contentsOf: url))
        return KokoroG2P(dictionary: parse(text))
    }

    /// Inflate `[UInt32 LE uncompressedSize][raw DEFLATE]` to its UTF-8 text.
    internal static func inflate(_ data: Data) throws -> String {
        guard data.count > 4 else { throw LoadError.inflateFailed }
        let size = Int(data[0]) | Int(data[1]) << 8 | Int(data[2]) << 16 | Int(data[3]) << 24
        let deflate = data.subdata(in: 4 ..< data.count)
        var destination = Data(count: size)
        let written = destination.withUnsafeMutableBytes { dst -> Int in
            deflate.withUnsafeBytes { src -> Int in
                compression_decode_buffer(
                    dst.bindMemory(to: UInt8.self).baseAddress!, size,
                    src.bindMemory(to: UInt8.self).baseAddress!, deflate.count,
                    nil, COMPRESSION_ZLIB
                )
            }
        }
        guard written == size, let text = String(bytes: destination, encoding: .utf8) else {
            throw LoadError.inflateFailed
        }
        return text
    }

    /// Parse the `word<TAB>id,id,…` dictionary text into a lookup table. Lines without
    /// a tab or with no parseable ids are skipped.
    internal static func parse(_ text: String) -> [String: [Int]] {
        var dict = [String: [Int]](minimumCapacity: 240_000)
        for line in text.split(separator: "\n", omittingEmptySubsequences: true) {
            guard let tab = line.firstIndex(of: "\t") else { continue }
            let word = String(line[..<tab])
            let ids = line[line.index(after: tab)...].split(separator: ",").compactMap { Int($0) }
            if !word.isEmpty, !ids.isEmpty { dict[word] = ids }
        }
        return dict
    }
}
