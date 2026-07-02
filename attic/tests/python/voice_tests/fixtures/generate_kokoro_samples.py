#!/usr/bin/env python3
"""Generate Kokoro audio samples to files"""

import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../../.."))

from src.tts.controllers.kokoro_tts_manager import kokoro_manager
import soundfile as sf
import time

print("🎤 Kokoro-82M Sample Generator\n")

# Create output directory
os.makedirs("audio_samples", exist_ok=True)

# Load model
print("Loading Kokoro-82M...")
if not kokoro_manager.load_model():
    print("❌ Failed to load model")
    sys.exit(1)

# Test voices with different samples
samples = [
    {
        "voices": [
            ("af_sky", "Sky - American Female"),
            ("af_nova", "Nova - American Female"),
            ("am_adam", "Adam - American Male"),
            ("bf_alice", "Alice - British Female"),
            ("bm_george", "George - British Male"),
        ],
        "text": "Hello! I'm Kokoro, an eighty-two million parameter text-to-speech model. I achieved number one ranking on the TTS Arena.",
        "prefix": "greeting"
    },
    {
        "voices": [("af_sky", "Sky"), ("am_adam", "Adam")],
        "text": "This is amazing! The quality is incredible, and I'm super lightweight too. Want to hear something cool? I can speak in real-time with minimal latency.",
        "prefix": "expressive"
    },
]

all_files = []

for sample_set in samples:
    for voice_id, voice_name in sample_set["voices"]:
        text = sample_set["text"]
        prefix = sample_set["prefix"]

        print(f"\n{'='*60}")
        print(f"🎤 Voice: {voice_name} ({voice_id})")
        print(f"   Prefix: {prefix}")
        print(f"   Text: \"{text[:50]}...\"")

        # Generate audio
        start_time = time.time()
        audio = kokoro_manager.generate(text, voice=voice_id, speed=1.0)
        gen_time = time.time() - start_time

        if audio is not None:
            duration = len(audio) / kokoro_manager.sample_rate
            rtf = gen_time / duration if duration > 0 else 0

            # Save to file
            filename = f"kokoro_{prefix}_{voice_id}.wav"
            filepath = f"audio_samples/{filename}"
            sf.write(filepath, audio, kokoro_manager.sample_rate)

            print(f"   ✅ Generated {duration:.2f}s in {gen_time:.2f}s (RTF: {rtf:.2f}x)")
            print(f"   💾 Saved: {filepath}")

            all_files.append(filepath)
        else:
            print(f"   ❌ Generation failed")

print("\n" + "="*60)
print(f"✅ Generated {len(all_files)} audio samples!\n")
print("📂 Files saved to: audio_samples/")
for f in all_files:
    print(f"   - {f}")

print("\n🎧 Open these files in your audio player to hear Kokoro!")
