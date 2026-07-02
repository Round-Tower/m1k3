#!/usr/bin/env python3
"""
Test Piper Lessac at different speeds to find the right pace for M1K3
"""

import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../../.."))

from src.tts.controllers.piper_tts_manager import piper_manager
from src.tts.effects.audio_effects import CompressionEffect, NormalizationEffect
import soundfile as sf
import time

print("🤖 M1K3 Speed Test: Finding the Right Pace")
print("=" * 70)

# M1K3's introduction
m1k3_intro = (
    "Greetings Human, I'm m-1-k-3 - an offline edge mechanic & assistant, "
    "designed to save the planet by using the compute you have in your own very pocket. "
    "Eco, offline, forever!!!"
)

# Create output directory
os.makedirs("audio_samples/m1k3_speed_test", exist_ok=True)

print(f"\n📝 M1K3 Introduction:")
print(f'   "{m1k3_intro}"')
print("\n" + "=" * 70)

# Load Piper model
print("\n🎤 Loading Piper TTS (en_US-lessac-low)...")
if not piper_manager.load_model(voice_name="en_US-lessac-low"):
    print("❌ Failed to load Piper model")
    sys.exit(1)

sample_rate = piper_manager.sample_rate
print(f"✅ Model loaded (sample rate: {sample_rate}Hz)")

# Effects pipeline (clean)
effects = [
    CompressionEffect(config={"threshold": 0.6, "ratio": 0.3}),
    NormalizationEffect(config={"level": 0.8})
]

# Test different speed settings
# length_scale: 1.0 = normal, >1.0 = slower, <1.0 = faster
speed_tests = [
    (1.0, "Normal (Default)"),
    (1.2, "Slightly Slower"),
    (1.4, "Moderate Pace"),
    (1.6, "Deliberate"),
    (1.8, "Relaxed"),
    (2.0, "Slow & Clear"),
]

for length_scale, description in speed_tests:
    print(f"\n🎤 Test: Speed {length_scale}x - {description}")

    try:
        # Set speed
        piper_manager.set_speed(length_scale)

        # Generate audio
        start_gen = time.time()
        audio = piper_manager.generate(m1k3_intro, voice="en_US-lessac-low")
        gen_time = time.time() - start_gen

        if audio is None:
            print(f"   ❌ Generation failed")
            continue

        duration = len(audio) / sample_rate
        rtf = gen_time / duration if duration > 0 else 0
        print(f"   ✅ Generated {duration:.2f}s in {gen_time:.2f}s (RTF: {rtf:.2f}x)")

        # Apply effects
        processed_audio = audio.copy()
        for effect in effects:
            processed_audio = effect.apply(processed_audio, sample_rate)

        # Save to file
        filename = f"audio_samples/m1k3_speed_test/lessac_speed_{length_scale:.1f}x.wav"
        sf.write(filename, processed_audio, sample_rate)
        print(f"   💾 Saved: {filename}")

    except Exception as e:
        print(f"   ❌ Error: {e}")
        import traceback
        traceback.print_exc()

print("\n" + "=" * 70)
print("\n✅ Speed Test Complete!")
print(f"\n📂 Audio samples saved to: audio_samples/m1k3_speed_test/")
print(f"\n🎧 Listen to find the perfect pace for M1K3!")
print(f"\n📊 Recommendations:")
print(f"   - 1.0-1.2x: Fast, efficient")
print(f"   - 1.4-1.6x: Conversational")
print(f"   - 1.8-2.0x: Patient, deliberate")

# Open directory
print(f"\n📂 Opening directory...")
os.system('open "audio_samples/m1k3_speed_test"')
