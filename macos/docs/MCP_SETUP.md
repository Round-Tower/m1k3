# Wiring M1K3 into Claude (MCP)

`M1K3MCP` is a stdio MCP server that exposes M1K3's knowledge to Claude
Desktop / Claude Code as three tools: `search_knowledge`, `list_documents`,
`get_document`. Once registered, Claude can pull from whatever M1K3 has indexed.

## 1. Build the release binary

```bash
cd ~/Development/m1k3/macos
swift build -c release --product M1K3MCP
# → .build/release/M1K3MCP
```

## 2. Point it at M1K3's data

The app is App-Sandboxed, so it writes inside its container. The server reads
that path by default, but it's worth setting explicitly:

```
M1K3_STORE_PATH=~/Library/Containers/dev.murphysig.M1K3/Data/Library/Application Support/M1K3/knowledge.sqlite
```

(If you haven't launched the app yet, the store won't exist — the server falls
back to `~/Library/Application Support/M1K3/knowledge.sqlite`. Run the app and
ingest something first so there's knowledge to serve.)

## 3a. Register with Claude Code (CLI)

```bash
claude mcp add m1k3 \
  ~/Development/m1k3/macos/.build/release/M1K3MCP \
  --env M1K3_STORE_PATH="$HOME/Library/Containers/dev.murphysig.M1K3/Data/Library/Application Support/M1K3/knowledge.sqlite"
```

## 3b. Register with Claude Desktop

Add to `~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "m1k3": {
      "command": "/Users/kevinmurphy/Development/m1k3/macos/.build/release/M1K3MCP",
      "env": {
        "M1K3_STORE_PATH": "/Users/kevinmurphy/Library/Containers/dev.murphysig.M1K3/Data/Library/Application Support/M1K3/knowledge.sqlite"
      }
    }
  }
}
```

Restart Claude Desktop. You should see `search_knowledge` / `list_documents` /
`get_document` available, scoped to M1K3's store.

## Verify by hand

The server speaks newline-delimited JSON-RPC over stdio. Smoke test (note: keep
stdin open — the server tears down on EOF before async handlers reply):

```bash
( printf '%s\n' \
  '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"x","version":"1"}}}' \
  '{"jsonrpc":"2.0","method":"notifications/initialized"}' \
  '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'; sleep 3 ) \
  | .build/release/M1K3MCP
```

You should see `serverInfo` + the three tool definitions.

---
*Signed: Kev + claude-opus-4-8, 2026-06-06, Confidence 0.85, Prior: Unknown*
