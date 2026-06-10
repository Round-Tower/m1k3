//
//  ResponderActivity.swift
//  M1K3Chat
//
//  What the responder is doing while no tokens are streaming — the cover for
//  the agent loop's silence. The labeler doubles as the privacy surface: a
//  web search always shows its query, so nothing leaves the device invisibly.
//
//  Signed: Kev + claude-fable-5, 2026-06-09, Confidence 0.85, Prior: Unknown

import Foundation

/// A progress signal from the responder, shown on the in-flight message.
public enum ResponderActivity: Sendable, Equatable {
    case retrieving
    case thinking(iteration: Int)
    case usingTool(name: String, argument: String)
}

/// Pure activity → user-facing copy.
public enum ActivityLabeler {
    private static let queryCap = 40

    public static func label(for activity: ResponderActivity) -> String {
        switch activity {
        case .retrieving:
            "Looking through your knowledge…"
        case .thinking:
            "Thinking…"
        case let .usingTool(name, argument):
            toolLabel(name: name, argument: argument)
        }
    }

    private static func toolLabel(name: String, argument: String) -> String {
        switch name {
        case "web_search":
            "Searching the web for “\(truncate(argument))”…"
        case "fetch_page":
            "Reading \(URL(string: argument)?.host() ?? "a web page")…"
        case "search_knowledge":
            "Searching your knowledge…"
        case "datetime":
            "Checking the date & time…"
        case "system_status":
            "Checking system status…"
        default:
            "Using \(name)…"
        }
    }

    private static func truncate(_ query: String) -> String {
        guard query.count > queryCap else { return query }
        return query.prefix(queryCap).trimmingCharacters(in: .whitespaces) + "…"
    }
}
