#!/bin/bash
# Test GPU-accelerated Kokoro TTS performance

echo "🎮 Testing GPU-accelerated Kokoro TTS..."

{
  echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}'
  echo '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"speak","arguments":{"text":"Testing GPU acceleration with CoreML on Apple Silicon. This should be significantly faster than CPU-only inference!","voice":"bm_daniel"}}}'
} | venv/bin/python mcp_tts_server.py
