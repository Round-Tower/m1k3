//
//  main.swift
//  M1K3MCP
//
//  Thin entry: run the M1K3 MCP stdio server. All logic lives in M1K3MCPKit so
//  it can be unit-tested. Claude Desktop/Code spawns this binary; it talks MCP
//  over stdin/stdout.
//
//  Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.8, Prior: Unknown

import M1K3MCPKit

try await runM1K3MCPServer()
