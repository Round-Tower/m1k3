#!/usr/bin/env python3
"""Quick Kokoro test - generate and play audio"""

import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../../.."))

from src.tts.controllers.kokoro_tts_manager import kokoro_manager
import sounddevice as sd

print("🎤 Kokoro-82M Quick Test\n")

# Load model
print("Loading Kokoro-82M...")
if not kokoro_manager.load_model():
    print("❌ Failed to load model")
    sys.exit(1)

# Test voices
test_voices = [
    ("af_sky", "Sky - American Female"),
    ("af_nova", "Nova - American Female"),
    ("am_adam", "Adam - American Male"),
    ("bf_alice", "Alice - British Female"),
    ("bm_george", "George - British Male"),
]

text = "Hello! I'm Kokoro, an eighty-two million parameter text-to-speech model."

for voice_id, voice_name in test_voices:
    print(f"\n{'='*60}")
    print(f"🎤 Voice: {voice_name}")
    print(f"   Text: \"{text}\"")

    # Generate audio
    audio = kokoro_manager.generate(text, voice=voice_id, speed=1.0)

    if audio is not None:
        duration = len(audio) / kokoro_manager.sample_rate
        print(f"   ✅ Generated {duration:.2f}s audio")

        # Play audio
        print(f"   🔊 Playing...")
        sd.play(audio, samplerate=kokoro_manager.sample_rate)
        sd.wait()
        print(f"   ✅ Done!")
    else:
        print(f"   ❌ Generation failed")

print("\n" + "="*60)
print("✅ Demo complete! Check out the different voices.")
