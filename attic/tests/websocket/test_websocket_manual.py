#!/usr/bin/env python3
"""
Manual WebSocket Test - Send Avatar Updates
"""

import asyncio
import websockets
import json
import time

async def test_avatar_websocket():
    """Test sending avatar updates via WebSocket"""
    
    print("🔌 Testing M1K3 Avatar WebSocket Connection")
    print("=" * 50)
    
    try:
        # Connect to avatar WebSocket server
        uri = "ws://localhost:8081"
        print(f"Connecting to {uri}...")
        
        async with websockets.connect(uri) as websocket:
            print("✅ Connected to avatar server!")
            
            # Test 1: Send ping
            ping_msg = {"type": "ping"}
            await websocket.send(json.dumps(ping_msg))
            print("📤 Sent ping message")
            
            # Test 2: Send avatar emotion update
            emotion_msg = {
                "type": "avatar_emotion",
                "data": {
                    "emotion": "happy",
                    "intensity": 80,
                    "message": "Test emotion update",
                    "metadata": {
                        "intent": "mathematical_calculation",
                        "confidence": 0.95,
                        "response_strategy": "deterministic",
                        "reasoning": "Testing avatar updates",
                        "classification_engine": "test"
                    }
                }
            }
            await websocket.send(json.dumps(emotion_msg))
            print("📤 Sent emotion update: happy (80%)")
            
            # Test 3: Send thinking phase update
            thinking_msg = {
                "type": "thinking_phase",
                "data": {
                    "phase": "calculating", 
                    "progress": 50,
                    "message": "Processing test calculation",
                    "confidence": 0.9
                }
            }
            await websocket.send(json.dumps(thinking_msg))
            print("📤 Sent thinking phase update")
            
            # Test 4: Send classification update
            classification_msg = {
                "type": "classification",
                "data": {
                    "intent": "creative_writing",
                    "confidence": 0.87,
                    "response_strategy": "creative",
                    "reasoning": "User wants creative content"
                }
            }
            await websocket.send(json.dumps(classification_msg))
            print("📤 Sent classification update")
            
            # Test 5: Send generation stream
            for i in range(5):
                stream_msg = {
                    "type": "generation_stream",
                    "data": {
                        "token": f"token_{i}",
                        "progress": (i + 1) * 20,
                        "total_tokens": 5,
                        "current_token": i + 1
                    }
                }
                await websocket.send(json.dumps(stream_msg))
                print(f"📤 Sent generation stream {i+1}/5")
                await asyncio.sleep(0.5)
            
            print("\n✅ All test messages sent successfully!")
            print("👀 Check the browser dashboard at http://localhost:8080")
            print("   The avatar should have changed emotions and shown progress")
            
            # Listen for any responses
            print("\n👂 Listening for responses (5 seconds)...")
            try:
                response = await asyncio.wait_for(websocket.recv(), timeout=5.0)
                print(f"📥 Received: {response}")
            except asyncio.TimeoutError:
                print("⏰ No response received (this is normal)")
                
    except Exception as e:
        print(f"❌ WebSocket test failed: {e}")
        return False
        
    return True

if __name__ == "__main__":
    success = asyncio.run(test_avatar_websocket())
    if success:
        print("\n🎉 WebSocket test completed successfully!")
    else:
        print("\n💥 WebSocket test failed!")