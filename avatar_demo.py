#!/usr/bin/env python3
"""
M1K3 Avatar Integration Demo
Demonstrates the avatar system working with M1K3
"""

import time
import threading
from avatar_server import start_avatar_server, stop_avatar_server, send_avatar_emotion, send_avatar_state, get_avatar_server_status
from avatar_controller import AvatarController, AvatarEmotion

def demo_avatar_integration():
    print("🎭 M1K3 Avatar Integration Demo")
    print("=" * 50)
    
    # Initialize controller
    controller = AvatarController()
    
    print("1. 🚀 Starting Avatar Server...")
    if start_avatar_server():
        status = get_avatar_server_status()
        print(f"   ✅ Server running at: {status['http_url']}")
        print(f"   🔌 WebSocket: ws://localhost:{status['ws_port']}")
        print(f"   📱 Open the URL above in your browser!")
        
        print("\n2. 🧪 Testing Emotion Analysis...")
        
        # Demo conversation with emotion detection
        conversation = [
            ("Hello! How can I help you today?", "greeting"),
            ("I'm thinking about your question...", "processing"),
            ("That's absolutely fantastic! I love helping you!", "response"),
            ("Hmm, let me consider this carefully...", "thinking"),
            ("Wow! That's surprising and amazing!", "response"),
            ("I'm sorry, but I can't help with that right now.", "error"),
            ("I'm feeling a bit tired after all that work.", "idle"),
            ("Thanks for chatting! Have a wonderful day!", "farewell")
        ]
        
        for i, (text, context) in enumerate(conversation, 1):
            print(f"\n   Step {i}: Processing: '{text}'")
            
            # Analyze emotion
            result = controller.update_emotion(text, context)
            print(f"           Detected emotion: {result['emotion']} ({result['intensity']}%)")
            
            # Send to avatar
            send_avatar_emotion(result['emotion'], result['intensity'], text)
            
            # Send appropriate state
            if context == "processing":
                send_avatar_state("thinking")
            elif context == "thinking":
                send_avatar_state("thinking")
            elif context == "error":
                send_avatar_state("error")
            elif context == "idle":
                send_avatar_state("idle")
            else:
                send_avatar_state("idle")
            
            time.sleep(3)  # Wait to see the emotion change
        
        print("\n3. 🎨 Testing All Avatar Styles...")
        styles = ["robot", "organic", "crystal", "ghost", "energy", "cute"]
        colors = ["#E25303", "#0F4C81", "#92B48B", "#8E354A", "#00FFFF", "#FF0099"]
        
        for style, color in zip(styles, colors):
            print(f"   Switching to: {style} ({color})")
            from avatar_server import get_avatar_server
            server = get_avatar_server()
            server.send_style_update(style, color)
            send_avatar_emotion("happy", 60, f"Now I'm a {style}!")
            time.sleep(2)
        
        print("\n4. 🎭 Testing All Emotions...")
        emotions = ["happy", "excited", "love", "surprised", "thinking", "sleepy", "sad", "angry"]
        
        for emotion in emotions:
            print(f"   Emotion: {emotion}")
            send_avatar_emotion(emotion, 75, f"Feeling {emotion}!")
            time.sleep(2)
        
        print("\n5. 🎪 Animation Demo...")
        # Reset to default
        send_avatar_emotion("happy", 50, "Ready for animation demo!")
        time.sleep(1)
        
        # Demo different states with emotions
        states_demo = [
            ("thinking", "thinking", "Let me think about this..."),
            ("generating", "excited", "Generating an amazing response!"),
            ("speaking", "happy", "Speaking with enthusiasm!"),
            ("idle", "sleepy", "Taking a little rest...")
        ]
        
        for state, emotion, message in states_demo:
            print(f"   State: {state} + Emotion: {emotion}")
            send_avatar_state(state)
            send_avatar_emotion(emotion, 65, message)
            time.sleep(3)
        
        print("\n6. ✅ Demo Complete!")
        print("   🔄 Stopping server...")
        stop_avatar_server()
        print("   🛑 Server stopped")
        
    else:
        print("   ❌ Failed to start server")
        return
    
    print(f"\n🎉 Avatar Integration Demo Complete!")
    print("\n📖 How to use with M1K3:")
    print("1. Run: python cli.py --no-voice")
    print("2. Type: avatar start")
    print("3. Open: http://localhost:8080 in your browser")
    print("4. Chat with M1K3 and watch your avatar react!")
    print("\n🎮 Avatar Commands:")
    print("   avatar start          - Start avatar server")
    print("   avatar stop           - Stop avatar server")
    print("   avatar status         - Check server status")
    print("   avatar emotion happy  - Set emotion manually")
    print("   avatar style robot    - Change avatar style")
    print("   avatar test           - Test all emotions")

if __name__ == "__main__":
    demo_avatar_integration()