#!/bin/bash
# M1K3 Introduction - Testing full voice pipeline with longer text

M1K3_INTRO="Attention all units. This is M1K3, your local AI assistant. I run entirely on your device - no cloud, no tracking, just pure privacy-first intelligence. I'm powered by an 82 megabyte neural voice engine that sounds this natural while running faster than real-time on your hardware. My knowledge base spans everything from cooking recipes to quantum physics, all searchable through retrieval augmented generation. I speak through radio frequencies, just like the communications systems of old. Think of me as your personal AI copilot - always available, completely offline, and never sharing your data with anyone. I can help you learn, create, and explore, all while respecting your privacy. Whether you need voice synthesis, intelligent chat, or knowledge retrieval, I'm here, running locally on your machine. M1K3, standing by."

{
  echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"m1k3-test","version":"1.0.0"}}}'
  echo "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"speak\",\"arguments\":{\"text\":\"$M1K3_INTRO\",\"voice\":\"bm_daniel\"}}}"
} | venv/bin/python mcp_tts_server.py
