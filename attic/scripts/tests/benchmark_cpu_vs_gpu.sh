#!/bin/bash
# Benchmark CPU vs GPU performance for Kokoro TTS

TEST_TEXT="This is a performance benchmark test. We're comparing CPU versus GPU acceleration with CoreML on Apple Silicon. The GPU should provide significantly faster inference times."

echo "🔬 Kokoro TTS Performance Benchmark"
echo "===================================="
echo ""

# Test 1: CPU-only
echo "📊 Test 1: CPU-only inference"
echo "------------------------------"
{
  echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"bench","version":"1.0"}}}'
  echo "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"speak\",\"arguments\":{\"text\":\"$TEST_TEXT\",\"voice\":\"bm_daniel\",\"effect\":\"none\"}}}"
} | ONNX_PROVIDER=CPUExecutionProvider venv/bin/python mcp_tts_server.py 2>&1 | grep -E "(🎯|CPU|GPU)"

echo ""
echo "📊 Test 2: GPU (CoreML) acceleration"
echo "-------------------------------------"
{
  echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"bench","version":"1.0"}}}'
  echo "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"speak\",\"arguments\":{\"text\":\"$TEST_TEXT\",\"voice\":\"bm_daniel\",\"effect\":\"none\"}}}"
} | venv/bin/python mcp_tts_server.py 2>&1 | grep -E "(🎯|CPU|GPU)"

echo ""
echo "✅ Benchmark complete!"
echo "Lower RTF (Real-Time Factor) = faster performance"
echo "RTF < 1.0 = faster than real-time"
