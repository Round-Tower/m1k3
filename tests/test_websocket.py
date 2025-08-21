#!/usr/bin/env python3
"""
Test WebSocket connectivity to avatar server
"""

import asyncio
import websockets
import json
import time

async def test_websocket():
    """Test WebSocket connection and message flow"""
    uri = "ws://localhost:8081"
    
    try:
        print(f"🔌 Connecting to {uri}...")
        async with websockets.connect(uri) as websocket:
            print("✅ Connected to WebSocket server!")
            
            # Wait for welcome message
            welcome = await websocket.recv()
            print(f"📨 Received: {welcome}")
            
            # Send a ping
            ping_message = json.dumps({"type": "ping"})
            await websocket.send(ping_message)
            print(f"📤 Sent: {ping_message}")
            
            # Wait for pong
            pong = await websocket.recv()
            print(f"📨 Received: {pong}")
            
            # Now test if server can send messages
            print("\n🧪 Waiting for server messages (open browser to http://localhost:8080)...")
            print("   The dashboard should now connect and receive messages!")
            
            # Keep connection open to receive messages
            timeout = 10
            start_time = time.time()
            
            while time.time() - start_time < timeout:
                try:
                    message = await asyncio.wait_for(websocket.recv(), timeout=1.0)
                    data = json.loads(message)
                    print(f"📨 Received: {data.get('type', 'unknown')} - {message[:100]}")
                except asyncio.TimeoutError:
                    # No message received, continue waiting
                    pass
                except Exception as e:
                    print(f"Error receiving message: {e}")
                    break
            
            print(f"\n✅ WebSocket test complete!")
            
    except Exception as e:
        print(f"❌ WebSocket connection failed: {e}")
        print("\nTroubleshooting:")
        print("1. Check if avatar server is running on port 8081")
        print("2. Try: python avatar_server.py --verbose")
        return False
    
    return True

async def send_test_emotions():
    """Send test emotions after connection is established"""
    # Wait a bit for connection
    await asyncio.sleep(2)
    
    # Use the avatar server API to send messages
    from avatar_server import get_avatar_server
    server = get_avatar_server()
    
    # Check if we have connected clients
    if server.websocket_server and hasattr(server.websocket_server, 'clients'):
        print(f"\n📊 Connected clients: {len(server.websocket_server.clients)}")
        
        # Send test emotions
        emotions = ['happy', 'excited', 'thinking', 'love']
        for emotion in emotions:
            server.send_emotion_update(emotion, 80, f"Testing {emotion}")
            print(f"📤 Sent emotion: {emotion}")
            await asyncio.sleep(1)

if __name__ == "__main__":
    print("🧪 M1K3 Avatar WebSocket Test")
    print("=" * 40)
    
    # Run the test
    loop = asyncio.get_event_loop()
    success = loop.run_until_complete(test_websocket())
    
    if success:
        print("\n✅ WebSocket is working correctly!")
        print("📱 Open http://localhost:8080 in your browser")
        print("   You should see the avatar dashboard with live updates!")
    else:
        print("\n❌ WebSocket connection issues detected")