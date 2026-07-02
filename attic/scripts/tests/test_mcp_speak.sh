#!/bin/bash
# Test MCP TTS pipeline with actual speech

# Send initialize then speak command
{
  echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}'
  echo '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"speak","arguments":{"text":"Testing the M1K3 MCP voice pipeline! This is Daniel speaking through the full Model Context Protocol integration.","voice":"bm_daniel"}}}'
} | venv/bin/python mcp_tts_server.py
