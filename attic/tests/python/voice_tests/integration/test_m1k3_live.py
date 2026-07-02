#!/usr/bin/env python3
"""
Test M1K3 CLI with new Kokoro Daniel voice
Plays audio through speakers so you can hear it!
"""

import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../../.."))

from src.tts.controllers.kokoro_tts_manager import kokoro_manager
from src.tts.effects.audio_effects import (
    IntercomEffect, CompressionEffect, NormalizationEffect
)
import sounddevice as sd
import time

print("🤖 M1K3 Live Voice Test")
print("=" * 70)
print("\n🎤 Voice: Kokoro Daniel (British Male)")
print("⚙️  Speed: 1.0x (Natural pace)")
print("🎛️  Effects: Radio Chat (Intercom + Compression + Normalization)")
print("\n" + "=" * 70)

# M1K3's introduction
m1k3_intro = (
    "Greetings Human, I'm m-1-k-3 - an offline edge mechanic & assistant, "
    "designed to save the planet by using the compute you have in your own very pocket. "
    "Eco, offline, forever!!!"
)

print(f"\n📝 M1K3 will say:")
print(f'   "{m1k3_intro}"')
print("\n" + "=" * 70)

# Radio chat effects
effects = [
    IntercomEffect(config={"low_freq": 300, "high_freq": 3400}),
    CompressionEffect(config={"threshold": 0.6, "ratio": 0.3}),
    NormalizationEffect(config={"level": 0.8})
]

try:
    # Load Kokoro Daniel
    print("\n🎤 Loading Kokoro-82M (Daniel)...")
    if not kokoro_manager.load_model():
        print("❌ Failed to load Kokoro model")
        sys.exit(1)

    # Generate audio
    print("\n🎯 Generating speech...")
    start_gen = time.time()
    audio = kokoro_manager.generate(m1k3_intro, voice="bm_daniel", speed=1.0)
    gen_time = time.time() - start_gen

    if audio is None:
        print("❌ Generation failed")
        sys.exit(1)

    sample_rate = kokoro_manager.sample_rate
    duration = len(audio) / sample_rate
    rtf = gen_time / duration if duration > 0 else 0
    print(f"   ✅ Generated {duration:.2f}s in {gen_time:.2f}s (RTF: {rtf:.2f}x)")

    # Apply radio chat effects
    print("\n🎛️  Applying Radio Chat effects...")
    start_fx = time.time()
    processed = audio.copy()

    for i, effect in enumerate(effects, 1):
        effect_name = effect.get_name()
        print(f"   {i}. {effect_name}...", end=" ")
        processed = effect.apply(processed, sample_rate)
        print("✅")

    fx_time = time.time() - start_fx
    print(f"   ✅ All effects applied in {fx_time:.3f}s")

    # Play audio
    total_time = gen_time + fx_time
    print(f"\n📊 Total processing: {total_time:.2f}s for {duration:.2f}s audio")
    print("\n🔊 Playing audio through speakers...")
    print("   (Turn up your volume!)")
    print("\n" + "=" * 70)

    # Play and wait
    sd.play(processed, samplerate=sample_rate)
    sd.wait()

    print("\n" + "=" * 70)
    print("\n✅ Playback complete!")
    print("\nThis is M1K3's new voice!")
    print("   - Kokoro Daniel (British Male)")
    print("   - Natural conversational pace")
    print("   - Radio Chat effects for character")

except KeyboardInterrupt:
    print("\n\n⚠️  Interrupted by user")
    sd.stop()
except Exception as e:
    print(f"\n❌ Error: {e}")
    import traceback
    traceback.print_exc()
