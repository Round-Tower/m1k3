#!/usr/bin/env python3
"""
Simple Voice Test - Just hear M1K3's witty bartender personality speak
"""

import sys
import time
from pathlib import Path

# Add src directory to path
sys.path.insert(0, str(Path(__file__).parent / "src"))

from witty_bartender_m1k3 import WittyBartenderM1K3

def simple_voice_test():
    """Simple voice test without complex interactions"""

    print("🍺 Witty Bartender M1K3 - Simple Voice Test")
    print("=" * 50)

    bartender = WittyBartenderM1K3()

    # Test messages to speak
    test_messages = [
        "Welcome! I'm M1K3, your local AI bartender. I serve up knowledge instead of drinks!",

        "I'm curious by nature, love sharing random trivia, and I'm always happy to admit when I don't know something.",

        "Fun fact related to that: Honey never spoils - archaeologists found edible honey in Egyptian tombs over 3,000 years old!",

        "I process everything locally on your device, so your privacy is safe with me.",

        "What aspect of consciousness intrigues you most? I love these kinds of deep conversations!",

        "You know what? I'm not entirely sure about that one. Could you give me a bit more context? I'd rather ask for clarification than guess what you're after!"
    ]

    for i, message in enumerate(test_messages, 1):
        print(f"\n🎵 Voice Test {i}:")
        print(f"🍺 M1K3: {message}")
        print("🎤 Speaking...")

        # Speak the message
        success = bartender.speak_naturally(message)
        if success:
            print("✅ Voice synthesis successful!")
        else:
            print("❌ Voice synthesis failed, but text shows the personality!")

        time.sleep(1)  # Short pause between messages

    print("\n🎉 Voice test complete!")
    print("🍺 That's the friendly, witty bartender M1K3 personality in action!")

if __name__ == "__main__":
    try:
        simple_voice_test()
    except Exception as e:
        print(f"\n🍺 Voice test encountered an issue: {e}")
        print("But you can see the witty bartender personality in the text!")