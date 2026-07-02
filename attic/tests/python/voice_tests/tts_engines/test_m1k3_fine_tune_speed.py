#!/usr/bin/env python3
"""
Fine-tune M1K3 Voice Speed: Testing 1.0x - 1.4x range
With Radio Chat effects on Piper Lessac
"""

import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../../.."))

from src.tts.controllers.piper_tts_manager import piper_manager
from src.tts.effects.audio_effects import (
    IntercomEffect, CompressionEffect, NormalizationEffect
)
import soundfile as sf
import time

print("🤖 M1K3 Fine-Tune Speed Test: 1.0x - 1.4x")
print("=" * 70)

# M1K3's introduction
m1k3_intro = (
    "Greetings Human, I'm m-1-k-3 - an offline edge mechanic & assistant, "
    "designed to save the planet by using the compute you have in your own very pocket. "
    "Eco, offline, forever!!!"
)

# Create output directory
os.makedirs("audio_samples/m1k3_fine_tune", exist_ok=True)

print(f"\n📝 M1K3 Introduction:")
print(f'   "{m1k3_intro}"')
print(f"\n🎛️  Radio Chat Effects: Intercom + Compression + Normalization")
print("\n" + "=" * 70)

# Load Piper model
if not piper_manager.load_model(voice_name="en_US-lessac-low"):
    print("❌ Failed to load Piper model")
    sys.exit(1)

sample_rate = piper_manager.sample_rate

# Radio chat effects
effects = [
    IntercomEffect(config={"low_freq": 300, "high_freq": 3400}),
    CompressionEffect(config={"threshold": 0.6, "ratio": 0.3}),
    NormalizationEffect(config={"level": 0.8})
]

# Test speeds between 1.0x and 1.4x
speed_tests = [
    (1.0, "Normal - Original pace"),
    (1.1, "Slightly slower - 10% slower"),
    (1.2, "Moderate - 20% slower"),
    (1.3, "Deliberate - 30% slower"),
    (1.4, "Patient - 40% slower"),
]

for length_scale, description in speed_tests:
    print(f"\n🎤 Speed: {length_scale}x - {description}")

    try:
        # Set speed
        piper_manager.set_speed(length_scale)

        # Generate
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
        print(f"   Applying radio chat effects...")
        start_fx = time.time()
        processed = audio.copy()
        for effect in effects:
            processed = effect.apply(processed, sample_rate)
        fx_time = time.time() - start_fx
        print(f"   ✅ Effects applied in {fx_time:.3f}s")

        # Save
        filename = f"audio_samples/m1k3_fine_tune/lessac_radio_{length_scale:.1f}x.wav"
        sf.write(filename, processed, sample_rate)
        print(f"   💾 Saved: {filename}")

    except Exception as e:
        print(f"   ❌ Error: {e}")
        import traceback
        traceback.print_exc()

print("\n" + "=" * 70)
print("\n✅ Fine-Tune Test Complete!")
print(f"\n📂 Audio samples saved to: audio_samples/m1k3_fine_tune/")
print(f"\n🎧 Find the sweet spot between too fast and too slow!")
print(f"\n📊 Speed Guide:")
print(f"   - 1.0x: Original (was too fast)")
print(f"   - 1.1-1.2x: Slightly slower, still quick")
print(f"   - 1.3-1.4x: More deliberate, conversational")

# Open directory
print(f"\n📂 Opening directory...")
os.system('open "audio_samples/m1k3_fine_tune"')
