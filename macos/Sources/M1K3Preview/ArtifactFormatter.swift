import Foundation

public enum ArtifactFormatter {
    public static func format(_ artifact: CodeArtifact) -> CodeArtifact {
        var result = artifact
        switch artifact.language {
        case .html:
            result.source = formatHTML(artifact.source)
        case .css, .js:
            result.source = trimLines(artifact.source)
        }
        return result
    }

    // MARK: - HTML

    public static func formatHTML(_ source: String) -> String {
        var html = closeUnclosedTags(source)
        html = ensureHTMLStructure(html)
        html = normalizeIndentation(html)
        return html
    }

    public static func ensureHTMLStructure(_ source: String) -> String {
        let trimmed = source.trimmingCharacters(in: .whitespacesAndNewlines)
        let lower = trimmed.lowercased()

        let hasDoctype = lower.hasPrefix("<!doctype")
        let hasHTML = lower.contains("<html")
        let hasHead = lower.contains("<head")
        let hasBody = lower.contains("<body")

        let structured: String
        if hasDoctype && hasHTML {
            structured = trimmed
        } else if hasDoctype && !hasHTML {
            // Strips only the bare HTML5 doctype; legacy PUBLIC/SYSTEM doctypes are
            // left in place (browsers tolerate a non-leading doctype inside <html>).
            let body = trimmed.replacingOccurrences(
                of: "<!doctype html>", with: "", options: [.caseInsensitive]
            ).trimmingCharacters(in: .whitespacesAndNewlines)
            structured = "<!DOCTYPE html>\n<html lang=\"en\">\n\(body)\n</html>"
        } else if hasHTML && !hasDoctype {
            structured = "<!DOCTYPE html>\n" + trimmed
        } else if hasHead && hasBody {
            structured = "<!DOCTYPE html>\n<html lang=\"en\">\n\(trimmed)\n</html>"
        } else if hasBody {
            structured = "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n</head>\n\(trimmed)\n</html>"
        } else {
            structured = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
            </head>
            <body>
            \(trimmed)
            </body>
            </html>
            """
        }

        // Defense in depth: seal the WebSocket/fetch/XHR egress the WKContentRuleList
        // can't reach (see ArtifactSandboxPolicy). The artifact preview also disables
        // JavaScript, so this is belt-and-braces — but a CSP costs nothing and survives
        // even if the preview surface ever re-enables scripting.
        return injectCSPMeta(structured)
    }

    /// Insert the sandbox CSP as the first child of `<head>`. Idempotent (skips if the
    /// CSP is already present). If the document has no `<head>` it is returned unchanged
    /// rather than guessing a location — every synthesized path above provides one.
    static func injectCSPMeta(_ html: String) -> String {
        guard !html.contains(ArtifactSandboxPolicy.contentSecurityPolicy) else { return html }
        guard let headOpen = html.range(of: "<head", options: [.caseInsensitive]),
              let tagClose = html.range(of: ">", range: headOpen.upperBound ..< html.endIndex)
        else { return html }
        var result = html
        result.insert(contentsOf: "\n  " + ArtifactSandboxPolicy.cspMetaTag, at: tagClose.upperBound)
        return result
    }

    // MARK: - Unclosed tags

    private static let voidElements: Set<String> = [
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr",
    ]

    /// Best-effort for small-model output; does not handle `<` inside attribute values or raw-text elements.
    public static func closeUnclosedTags(_ source: String) -> String {
        var stack: [String] = []
        var scanner = source[source.startIndex...]

        while let openBracket = scanner.firstIndex(of: "<") {
            let afterBracket = source.index(after: openBracket)
            guard afterBracket < source.endIndex else { break }

            if source[afterBracket] == "/" {
                if let closeBracket = source[afterBracket...].firstIndex(of: ">") {
                    let tagNameStart = source.index(after: afterBracket)
                    let tagContent = source[tagNameStart ..< closeBracket]
                    let tagName = extractTagName(String(tagContent))
                    if let idx = stack.lastIndex(of: tagName) {
                        stack.remove(at: idx)
                    }
                    scanner = source[source.index(after: closeBracket)...]
                } else {
                    break
                }
            } else if source[afterBracket] == "!" || source[afterBracket] == "?" {
                if let closeBracket = source[afterBracket...].firstIndex(of: ">") {
                    scanner = source[source.index(after: closeBracket)...]
                } else {
                    break
                }
            } else {
                if let closeBracket = source[openBracket...].firstIndex(of: ">") {
                    let tagContent = source[afterBracket ..< closeBracket]
                    let contentStr = String(tagContent)

                    if contentStr.hasSuffix("/") {
                        scanner = source[source.index(after: closeBracket)...]
                        continue
                    }

                    let tagName = extractTagName(contentStr).lowercased()
                    if !tagName.isEmpty && !voidElements.contains(tagName) {
                        stack.append(tagName)
                    }
                    scanner = source[source.index(after: closeBracket)...]
                } else {
                    break
                }
            }
        }

        if stack.isEmpty { return source }

        var result = source
        for tag in stack.reversed() {
            result += "</\(tag)>"
        }
        return result
    }

    private static func extractTagName(_ content: String) -> String {
        let trimmed = content.trimmingCharacters(in: .whitespaces)
        let end = trimmed.firstIndex(where: { $0 == " " || $0 == "\t" || $0 == "\n" || $0 == "/" }) ?? trimmed.endIndex
        return String(trimmed[trimmed.startIndex ..< end]).lowercased()
    }

    // MARK: - Indentation

    public static func normalizeIndentation(_ source: String) -> String {
        let tokens = tokenize(source)
        var result = ""
        var depth = 0

        for token in tokens {
            let trimmed = token.trimmingCharacters(in: .whitespaces)
            if trimmed.isEmpty { continue }

            let isClose = trimmed.hasPrefix("</")
            let isOpen = trimmed.hasPrefix("<") && !isClose && !trimmed.hasPrefix("<!") && !trimmed.hasPrefix("<?")
            let tagName = isOpen || isClose ? extractTagName(String(trimmed.dropFirst(isClose ? 2 : 1))) : ""
            let isVoid = voidElements.contains(tagName)
            let isSelfClosing = trimmed.hasSuffix("/>")

            if isClose {
                depth = max(0, depth - 1)
            }

            let indent = String(repeating: "  ", count: depth)
            if !result.isEmpty { result += "\n" }
            result += indent + trimmed

            if isOpen && !isVoid && !isSelfClosing {
                let hasInlineClose = trimmed.contains("</\(tagName)>")
                if !hasInlineClose {
                    depth += 1
                }
            }
        }

        return result
    }

    /// Does not handle `<` inside <script>/<style> content; a raw-text element parser is the long-term fix.
    private static func tokenize(_ source: String) -> [String] {
        var tokens: [String] = []
        var current = ""
        var inTag = false

        for char in source {
            if char == "<" {
                let text = current.trimmingCharacters(in: .whitespacesAndNewlines)
                if !text.isEmpty {
                    tokens.append(text)
                }
                current = "<"
                inTag = true
            } else if char == ">", inTag {
                current += ">"
                tokens.append(current)
                current = ""
                inTag = false
            } else {
                current += String(char)
            }
        }

        let remaining = current.trimmingCharacters(in: .whitespacesAndNewlines)
        if !remaining.isEmpty {
            tokens.append(remaining)
        }

        return tokens
    }

    // MARK: - CSS / JS

    private static func trimLines(_ source: String) -> String {
        source
            .split(separator: "\n", omittingEmptySubsequences: false)
            .map { $0.trimmingCharacters(in: .init(charactersIn: " ")) == "" ? "" : String($0) }
            .joined(separator: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
