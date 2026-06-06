//
//  MCPServer.swift
//  M1K3MCPKit
//
//  The stdio MCP server that exposes M1K3's knowledge to Claude Desktop/Code.
//  Registers three tools (search_knowledge / list_documents / get_document) over
//  the same KnowledgeStore the app writes to, and serves them on stdin/stdout.
//
//  Store path (the sandbox wrinkle): the app is App-Sandboxed, so it writes to
//  its CONTAINER, not bare ~/Library/Application Support. This server is a plain
//  CLI (unsandboxed) Claude spawns, so it reads the container path directly.
//  Override with M1K3_STORE_PATH; otherwise it uses the container if present and
//  falls back to an unsandboxed store.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.75, Prior: Unknown

import Foundation
import M1K3Knowledge
import MCP

/// Resolve the knowledge-store path: explicit env override → app sandbox
/// container (the app's real data) → unsandboxed fallback.
public func resolveStorePath(environment: [String: String] = ProcessInfo.processInfo.environment) -> String {
    let fm = FileManager.default
    if let override = environment["M1K3_STORE_PATH"], !override.isEmpty {
        return override
    }
    let home = fm.homeDirectoryForCurrentUser
    let container = home.appendingPathComponent(
        "Library/Containers/dev.murphysig.M1K3/Data/Library/Application Support/M1K3/knowledge.sqlite"
    )
    if fm.fileExists(atPath: container.path) {
        return container.path
    }
    let unsandboxed = home.appendingPathComponent("Library/Application Support/M1K3")
    try? fm.createDirectory(at: unsandboxed, withIntermediateDirectories: true)
    return unsandboxed.appendingPathComponent("knowledge.sqlite").path
}

/// Build + run the M1K3 MCP server over stdio. Blocks until the transport closes.
public func runM1K3MCPServer() async throws {
    let store = try KnowledgeStore(path: resolveStorePath())
    let tools = KnowledgeMCPTools(store: store)

    let server = Server(
        name: "m1k3",
        version: "0.1.0",
        capabilities: .init(tools: .init())
    )

    let toolList = makeToolList()

    await server.withMethodHandler(ListTools.self) { _ in
        ListTools.Result(tools: toolList)
    }

    await server.withMethodHandler(CallTool.self) { params in
        do {
            let text: String
            switch params.name {
            case "search_knowledge":
                let query = stringArg(params.arguments, "query") ?? ""
                let limit = intArg(params.arguments, "limit") ?? 5
                text = try tools.searchKnowledge(query: query, limit: limit)
            case "list_documents":
                text = try tools.listDocuments(limit: intArg(params.arguments, "limit") ?? 100)
            case "get_document":
                text = try tools.getDocument(idString: stringArg(params.arguments, "id") ?? "")
            default:
                return CallTool.Result(content: [.text(text: "Unknown tool: \(params.name)", annotations: nil, _meta: nil)], isError: true)
            }
            return CallTool.Result(content: [.text(text: text, annotations: nil, _meta: nil)])
        } catch {
            return CallTool.Result(content: [.text(text: "Error: \(error)", annotations: nil, _meta: nil)], isError: true)
        }
    }

    try await server.start(transport: StdioTransport())
    await server.waitUntilCompleted()
}

// MARK: - Tool declarations

private func makeToolList() -> [Tool] {
    [
        Tool(
            name: "search_knowledge",
            description: "Full-text search over M1K3's stored knowledge (documents, calls, notes). Returns ranked matching chunks.",
            inputSchema: [
                "type": "object",
                "properties": [
                    "query": ["type": "string", "description": "the text to search for"],
                    "limit": ["type": "integer", "description": "max results (default 5)"],
                ],
                "required": ["query"],
            ]
        ),
        Tool(
            name: "list_documents",
            description: "List the items M1K3 has indexed, with their ids, kinds, and titles.",
            inputSchema: [
                "type": "object",
                "properties": [
                    "limit": ["type": "integer", "description": "max items (default 100)"],
                ],
            ]
        ),
        Tool(
            name: "get_document",
            description: "Fetch the full text of one indexed item by its id (from list_documents).",
            inputSchema: [
                "type": "object",
                "properties": [
                    "id": ["type": "string", "description": "the document id (UUID)"],
                ],
                "required": ["id"],
            ]
        ),
    ]
}

private func stringArg(_ args: [String: Value]?, _ key: String) -> String? {
    if case let .string(value)? = args?[key] { return value }
    return nil
}

private func intArg(_ args: [String: Value]?, _ key: String) -> Int? {
    if case let .int(value)? = args?[key] { return value }
    return nil
}
