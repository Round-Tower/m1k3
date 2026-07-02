#!/usr/bin/env python3
"""
Final M1K3 Voice Test: Lessac vs Daniel with Radio Chat Effects
Both at 1.6x speed with intercom + compression + normalization
"""

import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../../.."))

from src.tts.controllers.piper_tts_manager import piper_manager
from src.tts.controllers.kokoro_tts_manager import kokoro_manager
from src.tts.effects.audio_effects import (
    IntercomEffect, CompressionEffect, NormalizationEffect
)
import soundfile as sf
import time

print("🤖 M1K3 Final Voice Test: Radio Chat Style")
print("=" * 70)

# M1K3's introduction
m1k3_intro = (
    "Greetings Human, I'm m-1-k-3 - an offline edge mechanic & assistant, "
    "designed to save the planet by using the compute you have in your own very pocket. "
    "Eco, offline, forever!!!"
)

# Create output directory
os.makedirs("audio_samples/m1k3_final_test", exist_ok=True)

print(f"\n📝 M1K3 Introduction:")
print(f'   "{m1k3_intro}"')
print("\n" + "=" * 70)

# Radio chat effects pipeline
print("\n🎛️  Radio Chat Effects Pipeline:")
print("   1. Intercom (300-3400Hz bandpass)")
print("   2. Compression (threshold: 0.6, ratio: 0.3)")
print("   3. Normalization (level: 0.8)")

effects = [
    IntercomEffect(config={"low_freq": 300, "high_freq": 3400}),
    CompressionEffect(config={"threshold": 0.6, "ratio": 0.3}),
    NormalizationEffect(config={"level": 0.8})
]

# Test 1: Piper Lessac at 1.6x speed
print("\n" + "=" * 70)
print("🎤 Test 1: Piper Lessac (60MB model)")
print("   Speed: 1.6x (Deliberate pace)")
print("   Character: American Male, professional")

try:
    if piper_manager.load_model(voice_name="en_US-lessac-low"):
        # Set speed to 1.6x
        piper_manager.set_speed(1.6)

        # Generate audio
        print("   Generating...")
        start_gen = time.time()
        audio = piper_manager.generate(m1k3_intro, voice="en_US-lessac-low")
        gen_time = time.time() - start_gen

        if audio is not None:
            sample_rate = piper_manager.sample_rate
            duration = len(audio) / sample_rate
            rtf = gen_time / duration if duration > 0 else 0
            print(f"   ✅ Generated {duration:.2f}s in {gen_time:.2f}s (RTF: {rtf:.2f}x)")

            # Apply radio chat effects
            print("   Applying radio chat effects...")
            start_fx = time.time()
            processed = audio.copy()
            for effect in effects:
                processed = effect.apply(processed, sample_rate)
            fx_time = time.time() - start_fx
            print(f"   ✅ Effects applied in {fx_time:.3f}s")

            # Save
            filename = "audio_samples/m1k3_final_test/m1k3_lessac_radio_1.6x.wav"
            sf.write(filename, processed, sample_rate)
            print(f"   💾 Saved: {filename}")
        else:
            print("   ❌ Generation failed")
    else:
        print("   ❌ Failed to load Piper model")
except Exception as e:
    print(f"   ❌ Error: {e}")
    import traceback
    traceback.print_exc()

# Test 2: Kokoro Daniel at 1.6x speed
print("\n" + "=" * 70)
print("🎤 Test 2: Kokoro Daniel (310MB model)")
print("   Speed: 1.6x (Deliberate pace)")
print("   Character: British Male, professional & precise")

try:
    if kokoro_manager.load_model():
        # Generate audio with Daniel voice at 1.6x speed
        # Note: Kokoro uses inverse speed (0.625 = 1.6x slower)
        # speed parameter: 1.0 = normal, <1.0 = slower, >1.0 = faster
        # So for 1.6x slower, we use 1.0/1.6 = 0.625
        print("   Generating...")
        start_gen = time.time()
        audio = kokoro_manager.generate(
            m1k3_intro,
            voice="bm_daniel",
            speed=0.625  # 1.0/1.6 = 0.625 for 1.6x slower
        )
        gen_time = time.time() - start_gen

        if audio is not None:
            sample_rate = kokoro_manager.sample_rate
            duration = len(audio) / sample_rate
            rtf = gen_time / duration if duration > 0 else 0
            print(f"   ✅ Generated {duration:.2f}s in {gen_time:.2f}s (RTF: {rtf:.2f}x)")

            # Apply radio chat effects
            print("   Applying radio chat effects...")
            start_fx = time.time()
            processed = audio.copy()
            for effect in effects:
                processed = effect.apply(processed, sample_rate)
            fx_time = time.time() - start_fx
            print(f"   ✅ Effects applied in {fx_time:.3f}s")

            # Save
            filename = "audio_samples/m1k3_final_test/m1k3_daniel_radio_1.6x.wav"
            sf.write(filename, processed, sample_rate)
            print(f"   💾 Saved: {filename}")
        else:
            print("   ❌ Generation failed")
    else:
        print("   ❌ Failed to load Kokoro model")
except Exception as e:
    print(f"   ❌ Error: {e}")
    import traceback
    traceback.print_exc()

print("\n" + "=" * 70)
print("\n✅ Final Voice Test Complete!")
print(f"\n📂 Audio samples saved to: audio_samples/m1k3_final_test/")
print(f"\n🎧 Compare M1K3's final voice candidates:")
print(f"   - Piper Lessac: American, 60MB, ultra-fast")
print(f"   - Kokoro Daniel: British, 310MB, SOTA quality")
print(f"\nBoth with:")
print(f"   - 1.6x deliberate pace")
print(f"   - Radio chat effects (intercom style)")

# Open directory
print(f"\n📂 Opening directory...")
os.system('open "audio_samples/m1k3_final_test"')
