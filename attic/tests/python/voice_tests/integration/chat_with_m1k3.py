#!/usr/bin/env python3
"""
Interactive chat with M1K3 using the new Kokoro Daniel voice
Type messages and hear M1K3 respond!
"""

import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../../.."))

from src.tts.controllers.kokoro_tts_manager import kokoro_manager
from src.tts.effects.audio_effects import (
    IntercomEffect, CompressionEffect, NormalizationEffect
)
import sounddevice as sd

print("🤖 Chat with M1K3")
print("=" * 70)
print("\n🎤 Voice: Kokoro Daniel (British Male)")
print("⚙️  Effects: Radio Chat")
print("\nType 'quit' or 'exit' to stop")
print("\n" + "=" * 70)

# Radio chat effects
effects = [
    IntercomEffect(config={"low_freq": 300, "high_freq": 3400}),
    CompressionEffect(config={"threshold": 0.6, "ratio": 0.3}),
    NormalizationEffect(config={"level": 0.8})
]

# Load Kokoro Daniel
print("\n🎤 Loading Kokoro-82M (Daniel)...")
if not kokoro_manager.load_model():
    print("❌ Failed to load Kokoro model")
    sys.exit(1)

sample_rate = kokoro_manager.sample_rate
print("✅ Ready!\n")

# M1K3's introduction
intro = "Hello! I'm M1K3, your offline edge mechanic. How can I help you today?"
print(f"M1K3: {intro}")

# Generate and play intro
audio = kokoro_manager.generate(intro, voice="bm_daniel", speed=1.0)
if audio is not None:
    processed = audio.copy()
    for effect in effects:
        processed = effect.apply(processed, sample_rate)
    sd.play(processed, samplerate=sample_rate)
    sd.wait()

print("\n" + "=" * 70)

# Chat loop
try:
    while True:
        # Get user input
        user_input = input("\nYou: ").strip()

        if user_input.lower() in ['quit', 'exit', 'q']:
            response = "Goodbye! Stay eco-friendly, Human!"
            print(f"M1K3: {response}")

            # Generate and play goodbye
            audio = kokoro_manager.generate(response, voice="bm_daniel", speed=1.0)
            if audio is not None:
                processed = audio.copy()
                for effect in effects:
                    processed = effect.apply(processed, sample_rate)
                sd.play(processed, samplerate=sample_rate)
                sd.wait()
            break

        if not user_input:
            continue

        # Simple responses (in real M1K3, this would use the AI model)
        responses = {
            "hello": "Hello again! How can I assist you?",
            "hi": "Hi there! What would you like to know?",
            "help": "I'm an offline edge mechanic designed to save the planet by using local compute. Ask me anything!",
            "who are you": "I'm M1K3 - my friends call me Mike. I'm an offline edge mechanic and assistant.",
            "what can you do": "I can help with various tasks using only the compute in your pocket. Eco-friendly and offline!",
        }

        # Find response
        response = None
        for key, value in responses.items():
            if key in user_input.lower():
                response = value
                break

        if response is None:
            response = f"Interesting! You said: '{user_input}'. I'm still learning, but I'm here to help with offline, eco-friendly computing!"

        print(f"M1K3: {response}")

        # Generate and play response
        print("   (Generating audio...)", end=" ")
        audio = kokoro_manager.generate(response, voice="bm_daniel", speed=1.0)

        if audio is not None:
            # Apply effects
            processed = audio.copy()
            for effect in effects:
                processed = effect.apply(processed, sample_rate)

            print("✅")
            sd.play(processed, samplerate=sample_rate)
            sd.wait()
        else:
            print("❌ Failed to generate audio")

except KeyboardInterrupt:
    print("\n\n👋 Goodbye!")
    sd.stop()
except Exception as e:
    print(f"\n❌ Error: {e}")
    import traceback
    traceback.print_exc()

print("\n" + "=" * 70)
print("Thanks for chatting with M1K3! 🤖")
