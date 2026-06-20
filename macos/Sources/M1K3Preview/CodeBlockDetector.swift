import Foundation

public enum CodeBlockDetector {
    public static func detect(in text: String) -> [CodeArtifact] {
        var artifacts: [CodeArtifact] = []

        let fenced = extractFencedBlocks(from: text)
        for block in fenced {
            let language = resolveLanguage(tag: block.tag, source: block.source)
            guard let language else { continue }
            artifacts.append(CodeArtifact(source: block.source, language: language))
        }

        if artifacts.isEmpty, looksLikeFullDocument(text) {
            artifacts.append(
                CodeArtifact(source: text.trimmingCharacters(in: .whitespacesAndNewlines), language: .html)
            )
        }

        return artifacts
    }

    private struct FencedBlock {
        let tag: String
        let source: String
    }

    private static func extractFencedBlocks(from text: String) -> [FencedBlock] {
        var blocks: [FencedBlock] = []
        let lines = text.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        var idx = 0

        while idx < lines.count {
            let trimmed = lines[idx].trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix("```") {
                let tag = String(trimmed.dropFirst(3)).trimmingCharacters(in: .whitespaces).lowercased()
                idx += 1
                var body: [String] = []
                while idx < lines.count {
                    let line = lines[idx].trimmingCharacters(in: .whitespaces)
                    if line.hasPrefix("```") {
                        break
                    }
                    body.append(lines[idx])
                    idx += 1
                }
                let source = body.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
                if !source.isEmpty {
                    blocks.append(FencedBlock(tag: tag, source: source))
                }
            }
            idx += 1
        }

        return blocks
    }

    private static func resolveLanguage(tag: String, source: String) -> CodeArtifact.Language? {
        if !tag.isEmpty {
            switch tag {
            case "html", "htm": return .html
            case "css": return .css
            case "js", "javascript": return .js
            default: return nil
            }
        }
        return CodeArtifact.Language.detect(from: source)
    }

    private static func looksLikeFullDocument(_ text: String) -> Bool {
        let lines = text.lowercased()
            .split(separator: "\n")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        // A real document opens its <!doctype>/<html> at the START of a line, not
        // mid-sentence — so prose like "wrap it in <html> tags" doesn't fire the
        // panel. (The <head>/<body> arm below was already line-anchored.)
        if lines.contains(where: { $0.hasPrefix("<!doctype html") || $0.hasPrefix("<html") }) {
            return true
        }
        let hasHeadLine = lines.contains { $0.hasPrefix("<head") }
        let hasBodyLine = lines.contains { $0.hasPrefix("<body") }
        return hasHeadLine && hasBodyLine
    }
}
