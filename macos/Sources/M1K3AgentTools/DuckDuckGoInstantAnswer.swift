//
//  DuckDuckGoInstantAnswer.swift
//  M1K3AgentTools
//
//  Pure decoder for api.duckduckgo.com's Instant Answer JSON. The endpoint
//  answers fact-box queries (definitions, conversions, encyclopedic topics)
//  and returns all-empty fields for everything else — an empty parse result
//  is the signal to fall back to the HTML endpoint.
//
//  RelatedTopics is heterogeneous: plain topics {Text, FirstURL} mixed with
//  named groups {Name, Topics: [...]}; the custom decode flattens both.
//
//  Signed: Kev + claude-fable-5, 2026-06-09, Confidence 0.85, Prior: Unknown

import Foundation

struct DuckDuckGoInstantAnswer: Decodable {
    let answer: String
    let abstractText: String
    let abstractURL: String
    let heading: String
    let relatedTopics: [InstantAnswerRelatedTopic]

    enum CodingKeys: String, CodingKey {
        case answer = "Answer"
        case abstractText = "AbstractText"
        case abstractURL = "AbstractURL"
        case heading = "Heading"
        case relatedTopics = "RelatedTopics"
    }
}

/// A topic, or a named group of topics — flattened by `flatTopics`. Top-level
/// (not nested in DuckDuckGoInstantAnswer) to satisfy the 1-deep nesting rule.
struct InstantAnswerRelatedTopic: Decodable {
    let text: String?
    let firstURL: String?
    let topics: [InstantAnswerRelatedTopic]?

    enum CodingKeys: String, CodingKey {
        case text = "Text"
        case firstURL = "FirstURL"
        case topics = "Topics"
    }
}

enum DuckDuckGoInstantAnswerParser {
    /// Empty array ⇒ the caller falls back to the HTML endpoint.
    static func parse(_ data: Data) -> [WebSearchResult] {
        guard let answer = try? JSONDecoder().decode(DuckDuckGoInstantAnswer.self, from: data) else {
            return []
        }
        var results: [WebSearchResult] = []

        if !answer.answer.isEmpty {
            results.append(WebSearchResult(
                title: answer.heading.isEmpty ? "Answer" : answer.heading,
                url: answer.abstractURL,
                snippet: answer.answer
            ))
        }
        if !answer.abstractText.isEmpty {
            results.append(WebSearchResult(
                title: answer.heading,
                url: answer.abstractURL,
                snippet: answer.abstractText
            ))
        }
        results.append(contentsOf: flatTopics(answer.relatedTopics))
        return results
    }

    private static func flatTopics(
        _ topics: [InstantAnswerRelatedTopic]
    ) -> [WebSearchResult] {
        topics.flatMap { topic -> [WebSearchResult] in
            if let nested = topic.topics {
                return flatTopics(nested)
            }
            guard let text = topic.text, let url = topic.firstURL, !text.isEmpty else {
                return []
            }
            // "Title - description" → title up to the first " - ".
            let title = text.components(separatedBy: " - ").first ?? text
            return [WebSearchResult(title: title, url: url, snippet: text)]
        }
    }
}
