#!/usr/bin/env python3
"""
Test Piper Lessac through M1K3's full voice pipeline
Tests with effects: compression, normalization, etc.
"""

import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../../.."))

from src.tts.controllers.piper_tts_manager import piper_manager
from src.tts.effects.audio_effects import (
    CompressionEffect, NormalizationEffect, IntercomEffect
)
import soundfile as sf
import time
import numpy as np

print("🤖 M1K3 Pipeline Test: Piper Lessac with Effects")
print("=" * 70)

# M1K3's introduction
m1k3_intro = (
    "Greetings Human, I'm m-1-k-3 - an offline edge mechanic & assistant, "
    "designed to save the planet by using the compute you have in your own very pocket. "
    "Eco, offline, forever!!!"
)

# Create output directory
os.makedirs("audio_samples/m1k3_pipeline_test", exist_ok=True)

print(f"\n📝 M1K3 Introduction:")
print(f'   "{m1k3_intro}"')
print("\n" + "=" * 70)

# Load Piper model once
print("\n🎤 Loading Piper TTS (en_US-lessac-low)...")
if not piper_manager.load_model(voice_name="en_US-lessac-low"):
    print("❌ Failed to load Piper model")
    sys.exit(1)

sample_rate = piper_manager.sample_rate
print(f"✅ Model loaded (sample rate: {sample_rate}Hz)")

# Test different effect configurations
test_configs = [
    {
        "name": "Raw (No Effects)",
        "effects": [],
        "description": "Baseline - no processing"
    },
    {
        "name": "Clean (Realtime)",
        "effects": [
            CompressionEffect(config={"threshold": 0.6, "ratio": 0.3}),
            NormalizationEffect(config={"level": 0.8})
        ],
        "description": "Compression + Normalization"
    },
    {
        "name": "Radio (Chat)",
        "effects": [
            IntercomEffect(config={"low_freq": 300, "high_freq": 3400}),  # Light intercom
            CompressionEffect(config={"threshold": 0.6, "ratio": 0.3}),
            NormalizationEffect(config={"level": 0.8})
        ],
        "description": "Light Intercom + Compression + Normalization"
    },
]

for config in test_configs:
    name = config["name"]
    effects = config["effects"]
    description = config["description"]

    print(f"\n🎤 Test: {name}")
    print(f"   Effects: {description}")

    try:
        # Generate raw audio
        print(f"   Generating audio...")
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
        if effects:
            print(f"   Applying {len(effects)} effects...")
            start_fx = time.time()
            processed_audio = audio.copy()

            for effect in effects:
                processed_audio = effect.apply(processed_audio, sample_rate)

            fx_time = time.time() - start_fx
            print(f"   ✅ Effects applied in {fx_time:.3f}s")
        else:
            processed_audio = audio

        # Save to file
        filename_safe = name.lower().replace(" ", "_").replace("(", "").replace(")", "")
        filename = f"audio_samples/m1k3_pipeline_test/lessac_{filename_safe}.wav"
        sf.write(filename, processed_audio, sample_rate)
        print(f"   💾 Saved: {filename}")

    except Exception as e:
        print(f"   ❌ Error: {e}")
        import traceback
        traceback.print_exc()

print("\n" + "=" * 70)
print("\n✅ Pipeline Test Complete!")
print(f"\n📂 Audio samples saved to: audio_samples/m1k3_pipeline_test/")
print(f"\n🎧 Compare raw TTS vs. processed audio with effects!")
print(f"\n📊 Summary:")
print(f"   - Raw: No processing (baseline)")
print(f"   - Realtime: Clean, balanced")
print(f"   - Chat: Intercom-style, conversational")

# Open directory
print(f"\n📂 Opening directory...")
os.system('open "audio_samples/m1k3_pipeline_test"')
