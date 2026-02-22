#!/bin/bash
# Test clean voice (no effect)

{
  echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}'
  echo '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"speak","arguments":{"text":"This is without the intercom effect, clean voice for comparison.","voice":"bm_daniel","effect":"none"}}}'
} | venv/bin/python mcp_tts_server.py
