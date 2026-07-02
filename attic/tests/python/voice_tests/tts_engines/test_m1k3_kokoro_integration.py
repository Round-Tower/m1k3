#!/usr/bin/env python3
"""
Test M1K3 with Kokoro TTS - Full Effects Pipeline
Tests the complete integration: Kokoro → Effects → Audio Output
"""

import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../../.."))

from src.engines.voice.unified_voice_engine import UnifiedVoiceEngine
import soundfile as sf
import time

print("🤖 M1K3 + Kokoro-82M Integration Test")
print("=" * 70)

# M1K3's introduction message
m1k3_intro = (
    "Hi! I'm M1K3 - my friends call me Mike. "
    "I'm an offline mechanic that aims to save the planet by using the "
    "compute you have in your pocket. Eco, offline, fun!"
)

# Test different Kokoro voice profiles
test_profiles = [
    ("kokoro", "Default Kokoro (Sky - American Female)"),
    ("kokoro_male", "Kokoro Male (Adam)"),
    ("kokoro_british", "Kokoro British (Alice)"),
    ("kokoro_fast", "Kokoro Fast (Sky at 1.2x)"),
]

# Create output directory
os.makedirs("audio_samples/m1k3_kokoro_integration", exist_ok=True)

print(f"\n📝 M1K3 Introduction:")
print(f'   "{m1k3_intro}"')
print("\n" + "=" * 70)

for profile_name, description in test_profiles:
    print(f"\n🎤 Testing: {description}")
    print(f"   Profile: {profile_name}")

    try:
        # Initialize voice engine with profile
        voice_engine = UnifiedVoiceEngine(profile_name=profile_name)

        # Load the model
        print(f"   Loading model...")
        start_load = time.time()
        if not voice_engine.load_model():
            print(f"   ❌ Failed to load model")
            continue
        load_time = time.time() - start_load
        print(f"   ✅ Loaded in {load_time:.2f}s")

        # Generate audio with effects pipeline
        print(f"   Generating audio with effects pipeline...")
        start_gen = time.time()
        audio = voice_engine.synthesize(m1k3_intro)
        gen_time = time.time() - start_gen

        if audio is not None:
            duration = len(audio) / voice_engine.sample_rate
            rtf = gen_time / duration if duration > 0 else 0

            # Get applied effects
            profile = voice_engine.voice_profiles.get(profile_name, {})
            effects = profile.get("effects", [])

            print(f"   ✅ Generated {duration:.2f}s in {gen_time:.2f}s (RTF: {rtf:.2f}x)")
            print(f"   🎛️  Effects applied: {', '.join(effects) if effects else 'none'}")

            # Save to file
            filename = f"audio_samples/m1k3_kokoro_integration/{profile_name}.wav"
            sf.write(filename, audio, voice_engine.sample_rate)
            print(f"   💾 Saved: {filename}")

            # Cleanup
            voice_engine.cleanup()
        else:
            print(f"   ❌ Generation failed")

    except Exception as e:
        print(f"   ❌ Error: {e}")
        import traceback
        traceback.print_exc()

print("\n" + "=" * 70)
print("\n✅ Integration Test Complete!")
print(f"\n📂 Audio samples saved to: audio_samples/m1k3_kokoro_integration/")
print(f"\n🎧 Listen to M1K3 introducing itself with different voices and effects!")

# Open the directory
print(f"\n📂 Opening directory...")
os.system(f'open "audio_samples/m1k3_kokoro_integration"')
