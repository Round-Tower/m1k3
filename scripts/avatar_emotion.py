#!/usr/bin/env python3
"""Quick script to send emotions to the avatar WebSocket server."""
import sys
import json

try:
    from websocket import create_connection
except ImportError:
    print("Installing websocket-client...")
    import subprocess
    subprocess.run([sys.executable, "-m", "pip", "install", "websocket-client", "-q"])
    from websocket import create_connection

def send_emotion(emotion: str, intensity: int = 80):
    """Send emotion to avatar server."""
    try:
        ws = create_connection("ws://localhost:8081", timeout=2)
        ws.send(json.dumps({
            "type": "emotion",
            "emotion": emotion.upper(),
            "intensity": intensity
        }))
        ws.close()
        print(f"🎭 Avatar → {emotion.upper()} ({intensity}%)")
    except Exception as e:
        print(f"❌ Avatar offline: {e}")

def send_state(state: str):
    """Send activity state to avatar server."""
    try:
        ws = create_connection("ws://localhost:8081", timeout=2)
        ws.send(json.dumps({
            "type": "state",
            "state": state.lower()
        }))
        ws.close()
        print(f"⚡ Avatar → {state}")
    except Exception as e:
        print(f"❌ Avatar offline: {e}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: avatar_emotion.py <emotion> [intensity]")
        print("       avatar_emotion.py --state <state>")
        print("\nEmotions: happy, sad, angry, surprised, love, thinking, sleepy, excited, neutral")
        print("States: idle, thinking, speaking, listening, generating, error")
        sys.exit(1)

    if sys.argv[1] == "--state":
        send_state(sys.argv[2] if len(sys.argv) > 2 else "idle")
    else:
        emotion = sys.argv[1]
        intensity = int(sys.argv[2]) if len(sys.argv) > 2 else 80
        send_emotion(emotion, intensity)
