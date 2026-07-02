#!/usr/bin/env python3
"""Test Kokoro integration in MCP server"""
import asyncio
import json
import sys

async def test_kokoro_mcp():
    """Test MCP server with Kokoro voice"""
    # Start the server process
    proc = await asyncio.create_subprocess_exec(
        'venv/bin/python', 'mcp_tts_server.py',
        stdin=asyncio.subprocess.PIPE,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE
    )

    # Wait for initialization
    await asyncio.sleep(3)

    print("✅ Server started")

    # Send initialize request
    init_request = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "test-client", "version": "1.0.0"}
        }
    }

    proc.stdin.write((json.dumps(init_request) + '\n').encode())
    await proc.stdin.drain()

    response_line = await asyncio.wait_for(proc.stdout.readline(), timeout=5.0)
    response = json.loads(response_line.decode())
    print(f"✅ Initialize: {response.get('result', {}).get('serverInfo', {}).get('name')}")

    # List tools
    tools_request = {
        "jsonrpc": "2.0",
        "id": 2,
        "method": "tools/list",
        "params": {}
    }

    proc.stdin.write((json.dumps(tools_request) + '\n').encode())
    await proc.stdin.drain()

    response_line = await asyncio.wait_for(proc.stdout.readline(), timeout=5.0)
    response = json.loads(response_line.decode())
    tools = response.get('result', {}).get('tools', [])
    print(f"✅ Tools: {[t['name'] for t in tools]}")

    # Test speak with Kokoro (bm_daniel)
    speak_request = {
        "jsonrpc": "2.0",
        "id": 3,
        "method": "tools/call",
        "params": {
            "name": "speak",
            "arguments": {
                "text": "Testing Kokoro Daniel voice in MCP server!",
                "voice": "bm_daniel"
            }
        }
    }

    print("\n🎤 Testing Kokoro voice (bm_daniel)...")
    proc.stdin.write((json.dumps(speak_request) + '\n').encode())
    await proc.stdin.drain()

    response_line = await asyncio.wait_for(proc.stdout.readline(), timeout=30.0)
    response = json.loads(response_line.decode())
    result = response.get('result', {})
    if 'content' in result:
        print(f"✅ {result['content'][0]['text']}")
    else:
        print(f"❌ Unexpected response: {response}")

    # Test voice detection helper
    print("\n🔍 Testing voice detection...")
    from mcp_unified_server import detect_engine_from_voice

    test_voices = {
        "bm_daniel": "kokoro",
        "bf_emma": "kokoro",
        "am_michael": "kokoro",
        "af_sky": "kokoro",
        "en_US-ryan-high": "piper",
        "en_US-amy-medium": "piper"
    }

    all_passed = True
    for voice, expected_engine in test_voices.items():
        detected = detect_engine_from_voice(voice)
        if detected == expected_engine:
            print(f"  ✅ {voice} → {detected}")
        else:
            print(f"  ❌ {voice} → {detected} (expected {expected_engine})")
            all_passed = False

    if all_passed:
        print("✅ All voice detection tests passed!")

    # Clean up
    proc.terminate()
    await proc.wait()

    print("\n✅ All tests complete!")

if __name__ == "__main__":
    try:
        asyncio.run(test_kokoro_mcp())
    except Exception as e:
        print(f"❌ Test failed: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)
