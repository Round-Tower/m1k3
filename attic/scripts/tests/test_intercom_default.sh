#!/bin/bash
# Test MCP TTS with default intercom effect

{
  echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}'
  echo '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"speak","arguments":{"text":"Testing intercom effect as default. This should sound like a radio or walkie-talkie communication.","voice":"bm_daniel"}}}'
} | venv/bin/python mcp_tts_server.py
