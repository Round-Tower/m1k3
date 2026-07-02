#!/usr/bin/env python3
"""
Final M1K3 Voice Test: Lessac vs Daniel
Both at 1.4x speed with Radio Chat effects
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

print("🤖 M1K3 Final Voice Comparison @ 1.4x Speed")
print("=" * 70)

# M1K3's introduction
m1k3_intro = (
    "Greetings Human, I'm m-1-k-3 - an offline edge mechanic & assistant, "
    "designed to save the planet by using the compute you have in your own very pocket. "
    "Eco, offline, forever!!!"
)

# Create output directory
os.makedirs("audio_samples/m1k3_final_1.4x", exist_ok=True)

print(f"\n📝 M1K3 Introduction:")
print(f'   "{m1k3_intro}"')
print(f"\n⚙️  Settings:")
print(f"   Speed: 1.4x (40% slower - perfect pace)")
print(f"   Effects: Radio Chat (Intercom + Compression + Normalization)")
print("\n" + "=" * 70)

# Radio chat effects
effects = [
    IntercomEffect(config={"low_freq": 300, "high_freq": 3400}),
    CompressionEffect(config={"threshold": 0.6, "ratio": 0.3}),
    NormalizationEffect(config={"level": 0.8})
]

# Test 1: Piper Lessac
print("\n🎤 Voice 1: Piper Lessac")
print("   Model: en_US-lessac-low (60MB)")
print("   Character: American Male, professional, clear")

try:
    if piper_manager.load_model(voice_name="en_US-lessac-low"):
        piper_manager.set_speed(1.4)

        start_gen = time.time()
        audio = piper_manager.generate(m1k3_intro, voice="en_US-lessac-low")
        gen_time = time.time() - start_gen

        if audio is not None:
            sample_rate = piper_manager.sample_rate
            duration = len(audio) / sample_rate
            rtf = gen_time / duration if duration > 0 else 0
            print(f"   ✅ Generated {duration:.2f}s in {gen_time:.2f}s (RTF: {rtf:.2f}x)")

            # Apply effects
            start_fx = time.time()
            processed = audio.copy()
            for effect in effects:
                processed = effect.apply(processed, sample_rate)
            fx_time = time.time() - start_fx
            print(f"   ✅ Effects applied in {fx_time:.3f}s")

            # Save
            filename = "audio_samples/m1k3_final_1.4x/m1k3_lessac_1.4x.wav"
            sf.write(filename, processed, sample_rate)
            print(f"   💾 Saved: {filename}")
        else:
            print("   ❌ Generation failed")
    else:
        print("   ❌ Failed to load model")
except Exception as e:
    print(f"   ❌ Error: {e}")

# Test 2: Kokoro Daniel
print("\n🎤 Voice 2: Kokoro Daniel")
print("   Model: Kokoro-82M bm_daniel (310MB)")
print("   Character: British Male, professional & precise (#1 TTS Arena)")

try:
    if kokoro_manager.load_model():
        # Kokoro speed: 1.0/1.4 = 0.714 for 1.4x slower
        start_gen = time.time()
        audio = kokoro_manager.generate(
            m1k3_intro,
            voice="bm_daniel",
            speed=0.714  # 1.0/1.4 for 1.4x slower
        )
        gen_time = time.time() - start_gen

        if audio is not None:
            sample_rate = kokoro_manager.sample_rate
            duration = len(audio) / sample_rate
            rtf = gen_time / duration if duration > 0 else 0
            print(f"   ✅ Generated {duration:.2f}s in {gen_time:.2f}s (RTF: {rtf:.2f}x)")

            # Apply effects
            start_fx = time.time()
            processed = audio.copy()
            for effect in effects:
                processed = effect.apply(processed, sample_rate)
            fx_time = time.time() - start_fx
            print(f"   ✅ Effects applied in {fx_time:.3f}s")

            # Save
            filename = "audio_samples/m1k3_final_1.4x/m1k3_daniel_1.4x.wav"
            sf.write(filename, processed, sample_rate)
            print(f"   💾 Saved: {filename}")
        else:
            print("   ❌ Generation failed")
    else:
        print("   ❌ Failed to load model")
except Exception as e:
    print(f"   ❌ Error: {e}")

print("\n" + "=" * 70)
print("\n✅ Final Voice Comparison Complete!")
print(f"\n📂 Audio samples: audio_samples/m1k3_final_1.4x/")
print(f"\n🎧 Listen and choose M1K3's voice:")
print(f"\n   Option A: Piper Lessac")
print(f"   - American accent")
print(f"   - 60MB model (5.2x smaller)")
print(f"   - Ultra-fast generation")
print(f"   - Professional, clear")
print(f"\n   Option B: Kokoro Daniel")
print(f"   - British accent")
print(f"   - 310MB model (#1 TTS Arena)")
print(f"   - SOTA quality")
print(f"   - Professional, precise")
print(f"\nBoth at 1.4x speed with radio chat effects!")

# Open directory
print(f"\n📂 Opening directory...")
os.system('open "audio_samples/m1k3_final_1.4x"')
