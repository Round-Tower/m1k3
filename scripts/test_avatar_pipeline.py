#!/usr/bin/env python3
"""
Test the MCP → avatar_server → web-avatar pipeline
Run this while web-avatar dev server is running at http://localhost:5174
"""

import sys
import os
import time

# Add project root to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from src.avatar.avatar_server import (
    start_avatar_server,
    send_avatar_emotion,
    send_avatar_state,
    get_avatar_server,
)

def main():
    print("=" * 60)
    print("  M1K3 Avatar Pipeline Test")
    print("=" * 60)
    print()
    print("Make sure web-avatar is running: http://localhost:5174")
    print()

    # Start avatar server
    print("Starting avatar server (WebSocket on port 8081)...")
    success = start_avatar_server(port=8080, ws_port=8081, open_browser=False, verbose=True)

    if not success:
        print("Failed to start avatar server!")
        return 1

    print()
    print("Waiting for web-avatar to connect...")
    print("(Refresh http://localhost:5174 if needed)")
    print()

    # Wait for connection
    server = get_avatar_server()
    for i in range(30):  # Wait up to 30 seconds
        if len(server.clients) > 0:
            print(f"Connected! {len(server.clients)} client(s)")
            break
        time.sleep(1)
        if i % 5 == 0 and i > 0:
            print(f"  Still waiting... ({i}s)")

    if len(server.clients) == 0:
        print("No clients connected after 30s. Make sure to:")
        print("  1. Run: cd src/web-avatar && npm run dev")
        print("  2. Open: http://localhost:5174")
        return 1

    print()
    print("=" * 60)
    print("  Running animation test sequence...")
    print("=" * 60)
    print()

    # Test sequence
    test_sequence = [
        ("happy", 80, "Feeling great!", 2),
        ("excited", 95, "WOW!", 2),
        ("thinking", 60, "Hmm...", 3),
        ("surprised", 85, "Oh!", 2),
        ("sad", 40, "Aww...", 2),
        ("love", 90, "Love it!", 2),
        ("sleepy", 30, "Zzz...", 2),
        ("happy", 70, "Back to happy!", 2),
    ]

    for emotion, intensity, message, delay in test_sequence:
        print(f"  → {emotion.upper()} (intensity: {intensity}) - '{message}'")
        send_avatar_emotion(emotion, intensity, message)
        time.sleep(delay)

    print()
    print("Testing activity states...")
    print()

    state_sequence = [
        ("thinking", 2),
        ("speaking", 2),
        ("listening", 2),  # Maps to 'loading' internally
        ("idle", 2),
    ]

    for state, delay in state_sequence:
        print(f"  → State: {state.upper()}")
        send_avatar_state(state)
        time.sleep(delay)

    print()
    print("=" * 60)
    print("  Test complete!")
    print("=" * 60)
    print()
    print("The avatar should have cycled through emotions and states.")
    print("Press Ctrl+C to stop the server.")
    print()

    # Keep running
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nShutting down...")
        return 0

if __name__ == "__main__":
    sys.exit(main())
