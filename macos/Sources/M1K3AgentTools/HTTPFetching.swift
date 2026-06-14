//
//  HTTPFetching.swift
//  M1K3AgentTools
//
//  The network seam behind WebSearchTool — tools test against a scripted
//  fake; only this adapter touches URLSession. Tight timeout so a stalled
//  search can't hang the agent loop.
//
//  Signed: Kev + claude-fable-5, 2026-06-09, Confidence 0.85, Prior: Unknown

import Foundation

public protocol HTTPFetching: Sendable {
    func fetch(_ request: URLRequest) async throws -> (data: Data, response: HTTPURLResponse)
}

public struct URLSessionHTTPFetcher: HTTPFetching {
    private let timeout: TimeInterval

    public init(timeout: TimeInterval = 12) {
        self.timeout = timeout
    }

    public func fetch(_ request: URLRequest) async throws -> (data: Data, response: HTTPURLResponse) {
        var timed = request
        timed.timeoutInterval = timeout
        let (data, response) = try await URLSession.shared.data(for: timed)
        guard let http = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        return (data, http)
    }
}
