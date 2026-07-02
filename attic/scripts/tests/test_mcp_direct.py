#!/usr/bin/env python3
"""Direct test of MCP server initialization"""
import subprocess
import time
import sys

print("🚀 Starting MCP server and monitoring stderr...")

proc = subprocess.Popen(
    ['venv/bin/python', 'mcp_tts_server.py'],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True,
    bufsize=1
)

# Give it time to initialize and print to stderr
time.sleep(3)

# Check if process is still running
if proc.poll() is not None:
    print(f"❌ Server exited with code: {proc.returncode}")
    stderr = proc.stderr.read()
    print(f"STDERR:\n{stderr}")
else:
    print("✅ Server is running")
    # Try to read any stderr output
    # Note: This might block if there's no output, but we gave it 3 seconds

print("\n🛑 Terminating server...")
proc.terminate()
proc.wait(timeout=2)
print("✅ Done")
