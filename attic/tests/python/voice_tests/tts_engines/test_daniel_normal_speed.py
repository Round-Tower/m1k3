#!/usr/bin/env python3
"""
Test Kokoro Daniel at normal speed (1.0x) with Radio Chat effects
"""

import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../../.."))

from src.tts.controllers.kokoro_tts_manager import kokoro_manager
from src.tts.effects.audio_effects import (
    IntercomEffect, CompressionEffect, NormalizationEffect
)
import soundfile as sf
import time

print("🤖 M1K3 Voice Test: Kokoro Daniel @ Normal Speed")
print("=" * 70)

# M1K3's introduction
m1k3_intro = (
    "Greetings Human, I'm m-1-k-3 - an offline edge mechanic & assistant, "
    "designed to save the planet by using the compute you have in your own very pocket. "
    "Eco, offline, forever!!!"
)

# Create output directory
os.makedirs("audio_samples/m1k3_daniel_normal", exist_ok=True)

print(f"\n📝 M1K3 Introduction:")
print(f'   "{m1k3_intro}"')
print(f"\n⚙️  Settings:")
print(f"   Voice: Kokoro Daniel (bm_daniel)")
print(f"   Speed: 1.0x (Normal - no speed adjustment)")
print(f"   Effects: Radio Chat (Intercom + Compression + Normalization)")
print("\n" + "=" * 70)

# Radio chat effects
effects = [
    IntercomEffect(config={"low_freq": 300, "high_freq": 3400}),
    CompressionEffect(config={"threshold": 0.6, "ratio": 0.3}),
    NormalizationEffect(config={"level": 0.8})
]

print("\n🎤 Generating with Kokoro Daniel...")

try:
    if kokoro_manager.load_model():
        # Generate at normal speed (1.0)
        start_gen = time.time()
        audio = kokoro_manager.generate(
            m1k3_intro,
            voice="bm_daniel",
            speed=1.0  # Normal speed
        )
        gen_time = time.time() - start_gen

        if audio is not None:
            sample_rate = kokoro_manager.sample_rate
            duration = len(audio) / sample_rate
            rtf = gen_time / duration if duration > 0 else 0
            print(f"   ✅ Generated {duration:.2f}s in {gen_time:.2f}s (RTF: {rtf:.2f}x)")

            # Apply radio chat effects
            print(f"   Applying radio chat effects...")
            start_fx = time.time()
            processed = audio.copy()
            for effect in effects:
                processed = effect.apply(processed, sample_rate)
            fx_time = time.time() - start_fx
            print(f"   ✅ Effects applied in {fx_time:.3f}s")

            # Save
            filename = "audio_samples/m1k3_daniel_normal/m1k3_daniel_1.0x_radio.wav"
            sf.write(filename, processed, sample_rate)
            print(f"   💾 Saved: {filename}")

            # Calculate total time
            total_time = gen_time + fx_time
            print(f"\n📊 Performance:")
            print(f"   Audio duration: {duration:.2f}s")
            print(f"   Generation time: {gen_time:.2f}s")
            print(f"   Effects time: {fx_time:.3f}s")
            print(f"   Total time: {total_time:.2f}s")
            print(f"   Real-time factor: {rtf:.2f}x")
        else:
            print("   ❌ Generation failed")
    else:
        print("   ❌ Failed to load Kokoro model")
except Exception as e:
    print(f"   ❌ Error: {e}")
    import traceback
    traceback.print_exc()

print("\n" + "=" * 70)
print("\n✅ Test Complete!")
print(f"\n📂 Audio sample: audio_samples/m1k3_daniel_normal/")
print(f"\n🎧 Kokoro Daniel at natural speed with radio chat effects!")
print(f"   - British Male, professional & precise")
print(f"   - No speed adjustment (natural pace)")
print(f"   - Radio chat effects applied")

# Open directory
print(f"\n📂 Opening directory...")
os.system('open "audio_samples/m1k3_daniel_normal"')
