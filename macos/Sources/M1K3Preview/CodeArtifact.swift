import Foundation
import UniformTypeIdentifiers

public struct CodeArtifact: Equatable, Sendable {
    public var source: String
    public var language: Language
    public var title: String?
    public let createdAt: Date

    public init(source: String, language: Language, title: String? = nil, createdAt: Date = Date()) {
        self.source = source
        self.language = language
        self.title = title
        self.createdAt = createdAt
    }

    public var displayTitle: String {
        title ?? "Generated \(language.rawValue.uppercased())"
    }

    public var filename: String {
        "\(title ?? "generated").\(language.fileExtension)"
    }

    public var exportData: Data? {
        source.data(using: .utf8)
    }

    public enum Language: String, Equatable, Sendable {
        case html
        case css
        case js

        public var fileExtension: String {
            rawValue
        }

        public var utType: UTType {
            switch self {
            case .html: .html
            case .css: .sourceCode
            case .js: .javaScript
            }
        }

        public init?(fileExtension ext: String) {
            switch ext.lowercased() {
            case "html", "htm": self = .html
            case "css": self = .css
            case "js": self = .js
            default: return nil
            }
        }

        public static func detect(from source: String) -> Language? {
            let trimmed = source.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            if trimmed.hasPrefix("<!doctype html") || trimmed.hasPrefix("<html") || trimmed.hasPrefix("<div") ||
                trimmed.hasPrefix("<p") || trimmed.hasPrefix("<h1") || trimmed.hasPrefix("<h2") ||
                trimmed.hasPrefix("<span") || trimmed.hasPrefix("<section") || trimmed.hasPrefix("<header") ||
                trimmed.hasPrefix("<nav") || trimmed.hasPrefix("<main") || trimmed.hasPrefix("<footer")
            {
                return .html
            }
            if trimmed.contains("{") && (trimmed.contains("color:") || trimmed.contains("display:") ||
                trimmed.contains("margin:") || trimmed.contains("padding:") || trimmed.contains("font-"))
            {
                return .css
            }
            if trimmed.hasPrefix("function ") || trimmed.hasPrefix("const ") || trimmed.hasPrefix("let ") ||
                trimmed.hasPrefix("var ") || trimmed.hasPrefix("class ") || trimmed.hasPrefix("import ")
            {
                return .js
            }
            return nil
        }
    }
}
