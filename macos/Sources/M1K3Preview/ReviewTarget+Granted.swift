//
//  ReviewTarget+Granted.swift
//  M1K3Preview
//
//  Routing for a URL we ALREADY hold access to — a link clicked in chat, a dropped
//  file, an open-panel pick, or a URL handed in by a tool. File URLs are trusted
//  straight to `.file` (the caller owns the sandbox grant); web URLs still pass
//  through the resolver so a non-file URL is validated the same way typed input is.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-19, Confidence 0.85, Prior: Unknown

import Foundation

public extension ReviewTarget {
    /// Route a URL the caller already has access to into a review target.
    static func forGranted(_ url: URL) -> ReviewTarget {
        if url.isFileURL {
            return .file(url)
        }
        return ReviewTargetResolver.resolve(url.absoluteString)
    }
}
