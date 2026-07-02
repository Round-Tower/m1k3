#!/bin/bash
# Test MCP TTS with nostalgic lofi effect

{
  echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}'
  echo '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"speak","arguments":{"text":"Now testing with the nostalgic lofi effect! This gives it that vintage warmth while keeping the voice clear.","voice":"bm_daniel","effect":"nostalgic","effect_intensity":0.6}}}'
} | venv/bin/python mcp_tts_server.py
