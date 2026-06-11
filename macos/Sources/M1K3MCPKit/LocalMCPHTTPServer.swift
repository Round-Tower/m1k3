//
//  LocalMCPHTTPServer.swift
//  M1K3MCPKit
//
//  Loopback-only HTTP/1.1 listener that fronts the in-app MCP server. The MCP
//  SDK's StatelessHTTPServerTransport is framework-agnostic (it answers
//  HTTPRequest values; it binds no socket), so this NWListener shell feeds it:
//  accumulate bytes → HTTPWireCodec.parseRequest → transport.handleRequest →
//  HTTPWireCodec.encode → write → close. One request per connection.
//
//  Session rebuild: the SDK Server rejects a second `initialize` for its
//  lifetime, so a fresh client connecting would 400 forever. We sniff
//  initialize POSTs and rebuild the (Server, transport) pair via the injected
//  factory — the previous client's session simply ends (documented v1 limit:
//  one MCP client at a time).
//
//  Bound to 127.0.0.1 explicitly — never an interface address. The OS-level
//  guarantee is the privacy story: no LAN exposure, ever.
//
//  Signed: Kev + claude-fable-5, 2026-06-11, Confidence 0.8 (wire layer is
//  test-pinned; the NWListener shell is verify-by-launch — exercised live via
//  `claude mcp add`). Prior: Unknown.
//

import Foundation
import MCP
import Network

public actor LocalMCPHTTPServer {
    public typealias SessionFactory = @Sendable () async throws -> (Server, StatelessHTTPServerTransport)

    private let port: UInt16
    private let makeSession: SessionFactory
    private var listener: NWListener?
    private var session: (server: Server, transport: StatelessHTTPServerTransport)?

    public private(set) var isRunning = false

    public init(port: UInt16, makeSession: @escaping SessionFactory) {
        self.port = port
        self.makeSession = makeSession
    }

    /// Bind the loopback listener and serve until `stop()`.
    public func start() async throws {
        guard !isRunning else { return }
        let parameters = NWParameters.tcp
        guard let nwPort = NWEndpoint.Port(rawValue: port) else {
            throw MCPVoiceError("invalid port \(port)")
        }
        // Loopback pin: connections from other machines never reach us.
        parameters.requiredLocalEndpoint = NWEndpoint.hostPort(host: .ipv4(.loopback), port: nwPort)
        let listener = try NWListener(using: parameters)
        listener.newConnectionHandler = { [weak self] connection in
            guard let self else {
                connection.cancel()
                return
            }
            Task { await self.handle(connection) }
        }
        listener.start(queue: .global(qos: .userInitiated))
        self.listener = listener
        session = try await makeSession()
        isRunning = true
    }

    public func stop() async {
        listener?.cancel()
        listener = nil
        if let session {
            await session.server.stop()
            await session.transport.disconnect()
        }
        session = nil
        isRunning = false
    }

    // MARK: - Connection handling

    private func handle(_ connection: NWConnection) async {
        connection.start(queue: .global(qos: .userInitiated))
        var buffer = Data()
        while isRunning {
            guard let chunk = await receiveChunk(connection) else { break }
            buffer.append(chunk)
            guard let parsed = HTTPWireCodec.parseRequest(buffer) else {
                if buffer.count > 1_048_576 { break } // oversized head/body → drop
                continue
            }
            let response = await respond(to: parsed.request)
            await send(HTTPWireCodec.encode(response), over: connection)
            break // Connection: close — one request per connection
        }
        connection.cancel()
    }

    private func respond(to request: HTTPRequest) async -> HTTPResponse {
        // A new client's initialize must land on a FRESH SDK server.
        if let body = request.body, HTTPWireCodec.isInitializeRequest(body: body) {
            if let session {
                await session.server.stop()
                await session.transport.disconnect()
            }
            session = nil
            session = try? await makeSession()
        }
        guard let transport = session?.transport else {
            return .error(statusCode: 500, MCPError.internalError("MCP session unavailable"))
        }
        return await transport.handleRequest(request)
    }

    private func receiveChunk(_ connection: NWConnection) async -> Data? {
        await withCheckedContinuation { continuation in
            connection.receive(minimumIncompleteLength: 1, maximumLength: 262_144) { data, _, isComplete, error in
                if let data, !data.isEmpty, error == nil {
                    continuation.resume(returning: data)
                } else {
                    _ = isComplete
                    continuation.resume(returning: nil)
                }
            }
        }
    }

    private func send(_ data: Data, over connection: NWConnection) async {
        await withCheckedContinuation { continuation in
            connection.send(content: data, completion: .contentProcessed { _ in
                continuation.resume()
            })
        }
    }
}
