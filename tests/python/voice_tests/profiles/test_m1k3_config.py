#!/usr/bin/env python3
"""
Test M1K3's new voice configuration
Verifies both CLI (Kokoro) and Mobile (Piper) profiles work correctly
"""

import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../../.."))

from src.tts.controllers.kokoro_tts_manager import kokoro_manager
from src.tts.controllers.piper_tts_manager import piper_manager
import soundfile as sf

print("🤖 M1K3 Voice Configuration Test")
print("=" * 70)

# M1K3's greeting
m1k3_greeting = "Greetings Human, I'm M1K3 - your offline edge mechanic!"

# Create output directory
os.makedirs("audio_samples/m1k3_config_test", exist_ok=True)

print(f"\n📝 Test Message: '{m1k3_greeting}'")
print("\n" + "=" * 70)

# Test 1: CLI Default (Kokoro Daniel)
print("\n🎤 Test 1: CLI Default Voice")
print("   Profile: kokoro")
print("   Engine: Kokoro-82M")
print("   Voice: Daniel (bm_daniel)")
print("   Speed: 1.0x (natural)")

try:
    # Check default voice
    print(f"\n   Current default voice: {kokoro_manager.current_voice}")

    if kokoro_manager.current_voice == "bm_daniel":
        print("   ✅ Default voice correctly set to Daniel!")
    else:
        print(f"   ⚠️  Default voice is {kokoro_manager.current_voice}, expected bm_daniel")

    # Load and generate
    if kokoro_manager.load_model():
        audio = kokoro_manager.generate(m1k3_greeting)
        if audio is not None:
            filename = "audio_samples/m1k3_config_test/cli_default.wav"
            sf.write(filename, audio, kokoro_manager.sample_rate)
            print(f"   ✅ Generated: {filename}")
        else:
            print("   ❌ Generation failed")
    else:
        print("   ❌ Failed to load model")

except Exception as e:
    print(f"   ❌ Error: {e}")

# Test 2: Mobile Default (Piper Lessac)
print("\n🎤 Test 2: Mobile Default Voice")
print("   Profile: mobile")
print("   Engine: Piper TTS")
print("   Voice: Lessac Low (en_US-lessac-low)")
print("   Speed: 1.4x")

try:
    # Load Piper with Lessac voice at 1.4x
    if piper_manager.load_model(voice_name="en_US-lessac-low"):
        piper_manager.set_speed(1.4)

        audio = piper_manager.generate(m1k3_greeting)
        if audio is not None:
            filename = "audio_samples/m1k3_config_test/mobile_default.wav"
            sf.write(filename, audio, piper_manager.sample_rate)
            print(f"   ✅ Generated: {filename}")
        else:
            print("   ❌ Generation failed")
    else:
        print("   ❌ Failed to load model")

except Exception as e:
    print(f"   ❌ Error: {e}")

print("\n" + "=" * 70)
print("\n✅ Configuration Test Complete!")
print(f"\n📂 Audio samples: audio_samples/m1k3_config_test/")
print(f"\n📊 Configuration Summary:")
print(f"   - CLI: Kokoro Daniel @ 1.0x (310MB, SOTA quality)")
print(f"   - Mobile: Piper Lessac @ 1.4x (60MB, ultra-fast)")
print(f"   - Both use Radio Chat effects pipeline")
print(f"\n📖 See docs/M1K3_VOICE_CONFIG.md for full details")

# Open directory
print(f"\n📂 Opening directory...")
os.system('open "audio_samples/m1k3_config_test"')
