//
//  LinkDetector.swift
//  M1K3Preview
//
//  Finds http(s) links in free text (a chat turn) so they can be surfaced as
//  one-click "review this" affordances. Pure: NSDataDetector over a string, no
//  UI. Order-preserving and de-duplicated, so a link mentioned twice yields one
//  chip and the chips read in the order they appear.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-19, Confidence 0.85, Prior: Unknown

import Foundation

public enum LinkDetector {
    /// Extract the http(s) URLs from `text`, in first-seen order, de-duplicated by
    /// absolute string. Non-web links (mailto:, file:, tel:) are ignored — the
    /// review panel reviews web pages and explicitly-granted files, not arbitrary
    /// schemes pulled out of model output.
    public static func detect(in text: String) -> [URL] {
        guard !text.isEmpty,
              let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue)
        else { return [] }

        let range = NSRange(text.startIndex ..< text.endIndex, in: text)
        var seen = Set<String>()
        var urls: [URL] = []
        detector.enumerateMatches(in: text, range: range) { match, _, _ in
            guard let url = match?.url,
                  let scheme = url.scheme?.lowercased(),
                  scheme == "http" || scheme == "https"
            else { return }
            if seen.insert(url.absoluteString).inserted {
                urls.append(url)
            }
        }
        return urls
    }
}
