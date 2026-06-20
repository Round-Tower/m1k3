//
//  ReviewTargetResolver.swift
//  M1K3Preview
//
//  Routes a raw string (pasted link, dropped file:// URL, typed path) to a
//  ReviewTarget. Pure: file existence and the home directory are injected, so the
//  whole thing tests off-device with no filesystem.
//
//  The contract is shaped by the App Sandbox: the app can only read files the user
//  explicitly granted (drop / open panel), which arrive as file:// URLs. A typed
//  bare path to an un-granted file reads as non-existent anyway, so a typed dotted
//  token (e.g. "example.com", "README.md") is treated as a domain and coerced to
//  https. Real local files come in as file:// URLs and resolve to `.file`.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-19, Confidence 0.85, Prior: Unknown

import Foundation

public enum ReviewTargetResolver {
    /// Resolve `rawInput` into a `ReviewTarget`.
    ///
    /// - Parameters:
    ///   - rawInput: the user's text (a link, a `file://` URL, or a path).
    ///   - homePath: home directory used to expand a leading `~`. Injected for tests.
    ///   - fileExists: existence probe for a filesystem path. Injected for tests.
    public static func resolve(
        _ rawInput: String,
        homePath: String = NSHomeDirectory(),
        fileExists: (String) -> Bool = { FileManager.default.fileExists(atPath: $0) }
    ) -> ReviewTarget {
        let input = rawInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !input.isEmpty else { return .empty }

        // 1. file:// URL — resolve against the filesystem.
        if input.lowercased().hasPrefix("file://") {
            if let url = URL(string: input), fileExists(url.path) {
                return .file(URL(fileURLWithPath: url.path))
            }
            return .invalid(rawInput)
        }

        // 2. http(s) link — load as-is.
        let lower = input.lowercased()
        if lower.hasPrefix("http://") || lower.hasPrefix("https://") {
            if let url = URL(string: input), url.host != nil {
                return .web(url)
            }
            return .invalid(rawInput)
        }

        // 3. Any other explicit scheme (ftp:, javascript:, …) is out of scope.
        if hasUnsupportedScheme(input) {
            return .invalid(rawInput)
        }

        // 4. An explicit local path (absolute, tilde, or dot-relative).
        if let path = explicitPath(input, homePath: homePath) {
            return fileExists(path) ? .file(URL(fileURLWithPath: path)) : .invalid(rawInput)
        }

        // 5. A bare domain → coerce to https.
        if looksLikeDomain(input), let url = URL(string: "https://\(input)"), url.host != nil {
            return .web(url)
        }

        // 6. Nothing fit.
        return .invalid(rawInput)
    }

    // MARK: - Helpers

    /// True when `input` carries a `scheme:` we don't handle. http/https/file are
    /// stripped before this is reached, so any remaining `scheme://` or `scheme:`
    /// prefix is unsupported.
    private static func hasUnsupportedScheme(_ input: String) -> Bool {
        guard let colon = input.firstIndex(of: ":") else { return false }
        let scheme = input[input.startIndex ..< colon]
        // A scheme is letters/digits/+/-/. and must not be a Windows-style drive
        // or a port-bearing host fragment — keep it simple: alphanum, length ≥ 2.
        guard scheme.count >= 2, scheme.allSatisfy({ $0.isLetter || $0.isNumber }) else { return false }
        return true
    }

    /// Returns the resolved filesystem path for an explicit path input, or nil if
    /// `input` doesn't look like a path (so it can fall through to domain coercion).
    private static func explicitPath(_ input: String, homePath: String) -> String? {
        if input.hasPrefix("/") { return input }
        if input.hasPrefix("~") {
            let tail = String(input.dropFirst())
            return homePath + tail
        }
        if input.hasPrefix("./") || input.hasPrefix("../") { return input }
        return nil
    }

    /// A loose hostname check: dotted, no whitespace, with a 2+ letter final label
    /// (the TLD). The path/query after the first `/` is ignored for the test.
    private static func looksLikeDomain(_ input: String) -> Bool {
        let host = input.split(separator: "/", maxSplits: 1).first.map(String.init) ?? input
        guard !host.isEmpty, !host.contains(" ") else { return false }
        let labels = host.split(separator: ".", omittingEmptySubsequences: false).map(String.init)
        guard labels.count >= 2 else { return false }
        guard labels.allSatisfy({ isValidLabel($0) }) else { return false }
        // Final label (TLD) must be ≥ 2 chars and all letters.
        guard let tld = labels.last, tld.count >= 2, tld.allSatisfy(\.isLetter) else { return false }
        return true
    }

    private static func isValidLabel(_ label: String) -> Bool {
        guard !label.isEmpty else { return false }
        guard let first = label.first, let last = label.last,
              first != "-", last != "-" else { return false }
        return label.allSatisfy { $0.isLetter || $0.isNumber || $0 == "-" }
    }
}
