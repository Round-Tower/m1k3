#!/usr/bin/env python3
"""Quick test of M1K3 TTS MCP server"""
import asyncio
import json
import sys

async def test_mcp_server():
    """Test MCP server via stdio"""
    # Start the server process
    proc = await asyncio.create_subprocess_exec(
        'venv/bin/python', 'mcp_tts_server.py',
        stdin=asyncio.subprocess.PIPE,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE
    )

    # Wait for initialization
    await asyncio.sleep(2)

    # Send initialize request
    init_request = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
            "protocolVersion": "2024-11-05",
            "capabilities": {},
            "clientInfo": {
                "name": "test-client",
                "version": "1.0.0"
            }
        }
    }

    proc.stdin.write((json.dumps(init_request) + '\n').encode())
    await proc.stdin.drain()

    # Read response
    response_line = await asyncio.wait_for(proc.stdout.readline(), timeout=5.0)
    response = json.loads(response_line.decode())
    print(f"✅ Initialize response: {response.get('result', {}).get('serverInfo', {}).get('name')}")

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
    print(f"✅ Available tools: {[t['name'] for t in tools]}")

    # Clean up
    proc.terminate()
    await proc.wait()

    print("✅ MCP server test complete!")

if __name__ == "__main__":
    try:
        asyncio.run(test_mcp_server())
    except Exception as e:
        print(f"❌ Test failed: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)
