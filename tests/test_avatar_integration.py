#!/usr/bin/env python3
"""
Test M1K3 Avatar Integration
"""

import time
import threading
from src.avatar.avatar_server import start_avatar_server, stop_avatar_server, send_avatar_emotion, send_avatar_state
from src.avatar.avatar_controller import AvatarController

def test_avatar_integration():
    print("🧪 Testing M1K3 Avatar Integration")
    print("=" * 50)
    
    # Test 1: Avatar Controller
    print("\n1. Testing Avatar Controller:")
    controller = AvatarController()
    
    # Test emotion analysis
    test_cases = [
        ("Hello! How are you today?", "greeting"),
        ("I'm thinking about your question...", "processing"),
        ("That's amazing! I love it!", "response"),
        ("I'm sorry, I can't help with that.", "error"),
    ]
    
    for text, context in test_cases:
        result = controller.update_emotion(text, context)
        print(f"   Text: '{text}'")
        print(f"   Emotion: {result['emotion']} (intensity: {result['intensity']})")
        print(f"   Changed: {result['changed']}")
        print()
    
    # Test 2: Avatar Server
    print("2. Testing Avatar Server:")
    print("   Starting server...")
    
    if start_avatar_server():
        print("   ✅ Server started successfully")
        
        # Test emotion updates
        print("   Testing emotion updates...")
        emotions = ['happy', 'thinking', 'excited', 'sleepy']
        
        for emotion in emotions:
            print(f"   Sending: {emotion}")
            send_avatar_emotion(emotion, 70, f"Testing {emotion}")
            time.sleep(1)
        
        print("   Testing state updates...")
        states = ['thinking', 'generating', 'speaking', 'idle']
        
        for state in states:
            print(f"   Sending: {state}")
            send_avatar_state(state)
            time.sleep(1)
        
        print("   Stopping server...")
        stop_avatar_server()
        print("   ✅ Server stopped successfully")
        
    else:
        print("   ❌ Failed to start server")
    
    print("\n✅ Avatar integration test complete!")
    print("\nTo test the full system:")
    print("1. Run: python cli.py --no-voice")
    print("2. Type: avatar start")
    print("3. Open: http://localhost:8080")
    print("4. Type: avatar test")
    print("5. Chat with M1K3 and watch avatar emotions!")

if __name__ == "__main__":
    test_avatar_integration()