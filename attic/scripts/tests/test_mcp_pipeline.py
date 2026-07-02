#!/usr/bin/env python3
"""Simple MCP pipeline test - sends speak request to MCP server"""
import asyncio
import json
import sys

async def test_mcp_pipeline():
    """Test the MCP TTS pipeline with proper JSON-RPC protocol"""

    # Start the MCP server
    proc = await asyncio.create_subprocess_exec(
        'venv/bin/python', 'mcp_tts_server.py',
        stdin=asyncio.subprocess.PIPE,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE
    )

    print("🚀 Starting MCP server...")

    # Read stderr to see server initialization
    async def read_stderr():
        while True:
            line = await proc.stderr.readline()
            if not line:
                break
            print(f"[SERVER] {line.decode().strip()}", file=sys.stderr)

    stderr_task = asyncio.create_task(read_stderr())

    # Wait for server to initialize
    await asyncio.sleep(2)

    print("\n📤 Sending initialize request...")

    # Initialize
    init_msg = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {"name": "test-client", "version": "1.0.0"}
        }
    }

    proc.stdin.write((json.dumps(init_msg) + '\n').encode())
    await proc.stdin.drain()

    # Read response
    try:
        response_line = await asyncio.wait_for(proc.stdout.readline(), timeout=5.0)
        response = json.loads(response_line.decode())
        print(f"✅ Initialized: {response.get('result', {}).get('serverInfo', {}).get('name')}")
    except asyncio.TimeoutError:
        print("❌ Initialize timeout")
        proc.terminate()
        return

    print("\n📤 Sending speak request...")

    # Call speak tool
    speak_msg = {
        "jsonrpc": "2.0",
        "id": 2,
        "method": "tools/call",
        "params": {
            "name": "speak",
            "arguments": {
                "text": "Testing the M1K3 MCP voice pipeline! This is going through the full Model Context Protocol integration.",
                "voice": "bm_daniel",
                "effect": "none"
            }
        }
    }

    proc.stdin.write((json.dumps(speak_msg) + '\n').encode())
    await proc.stdin.drain()

    # Read response with longer timeout for audio generation/playback
    try:
        response_line = await asyncio.wait_for(proc.stdout.readline(), timeout=30.0)
        response = json.loads(response_line.decode())

        if 'result' in response:
            content = response['result'].get('content', [])
            if content:
                print(f"\n✅ {content[0].get('text', 'Success!')}")
        else:
            print(f"❌ Error: {response.get('error', 'Unknown error')}")
    except asyncio.TimeoutError:
        print("❌ Speak timeout (audio might still be playing)")

    # Give a moment for audio to finish
    await asyncio.sleep(1)

    # Clean up
    print("\n🛑 Shutting down...")
    proc.terminate()
    stderr_task.cancel()
    try:
        await asyncio.wait_for(proc.wait(), timeout=2.0)
    except asyncio.TimeoutError:
        proc.kill()

    print("✅ Pipeline test complete!")

if __name__ == "__main__":
    try:
        asyncio.run(test_mcp_pipeline())
    except KeyboardInterrupt:
        print("\n⚠️  Interrupted")
    except Exception as e:
        print(f"❌ Test failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
