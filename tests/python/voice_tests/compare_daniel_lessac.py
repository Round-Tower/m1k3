#!/usr/bin/env python3
"""
Compare Kokoro Daniel vs Piper Lessac for M1K3
Testing both voices with M1K3's introduction
"""

import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../../.."))

from src.tts.controllers.kokoro_tts_manager import kokoro_manager
from src.tts.controllers.piper_tts_manager import piper_manager
import soundfile as sf
import time

print("🤖 M1K3 Voice Comparison: Daniel vs Lessac")
print("=" * 70)

# M1K3's introduction
m1k3_intro = (
    "Greetings Human, I'm m-1-k-3 - an offline edge mechanic & assistant, "
    "designed to save the planet by using the compute you have in your own very pocket. "
    "Eco, offline, forever!!!"
)

# Create output directory
os.makedirs("audio_samples/m1k3_voice_comparison", exist_ok=True)

print(f"\n📝 M1K3 Introduction:")
print(f'   "{m1k3_intro}"')
print("\n" + "=" * 70)

# Test 1: Kokoro Daniel (British Male - Professional, precise)
print("\n🎤 Test 1: Kokoro Daniel (British Male)")
print("   Character: Professional, precise")
print("   Loading Kokoro-82M...")

if kokoro_manager.load_model():
    try:
        start = time.time()
        audio = kokoro_manager.generate(m1k3_intro, voice="bm_daniel", speed=1.0)
        gen_time = time.time() - start

        if audio is not None:
            duration = len(audio) / kokoro_manager.sample_rate
            rtf = gen_time / duration if duration > 0 else 0

            filename = "audio_samples/m1k3_voice_comparison/kokoro_daniel.wav"
            sf.write(filename, audio, kokoro_manager.sample_rate)

            print(f"   ✅ Generated {duration:.2f}s in {gen_time:.2f}s (RTF: {rtf:.2f}x)")
            print(f"   💾 Saved: {filename}")
        else:
            print(f"   ❌ Generation failed")

    except Exception as e:
        print(f"   ❌ Error: {e}")
        import traceback
        traceback.print_exc()
else:
    print("   ❌ Failed to load Kokoro model")

# Test 2: Piper Lessac (American Male - Low quality, smallest model)
print("\n🎤 Test 2: Piper Lessac (American Male)")
print("   Voice: en_US-lessac-low (60M model - smallest available)")
print("   Character: Professional, clear, fast")
print("   Loading Piper...")

if piper_manager.load_model(voice_name="en_US-lessac-low"):
    try:
        start = time.time()
        audio = piper_manager.generate(m1k3_intro, voice="en_US-lessac-low")
        gen_time = time.time() - start

        if audio is not None:
            duration = len(audio) / piper_manager.sample_rate
            rtf = gen_time / duration if duration > 0 else 0

            filename = "audio_samples/m1k3_voice_comparison/piper_lessac.wav"
            sf.write(filename, audio, piper_manager.sample_rate)

            print(f"   ✅ Generated {duration:.2f}s in {gen_time:.2f}s (RTF: {rtf:.2f}x)")
            print(f"   💾 Saved: {filename}")
        else:
            print(f"   ❌ Generation failed")

    except Exception as e:
        print(f"   ❌ Error: {e}")
        import traceback
        traceback.print_exc()
else:
    print("   ❌ Failed to load Piper model")
    print("   Note: Piper may need model download first")

print("\n" + "=" * 70)
print("\n📊 Voice Comparison Summary:")
print("\n   Kokoro Daniel (bm_daniel):")
print("   - British Male, professional and precise")
print("   - 82M parameters, #1 TTS Arena")
print("   - Model file: 310MB (ONNX)")
print("\n   Piper Lessac (en_US-lessac-low):")
print("   - American Male, professional and clear")
print("   - Low quality (fastest, smallest)")
print("   - Model file: 60MB (ONNX) - 5.2x smaller than Kokoro!")

print(f"\n📂 Audio samples saved to: audio_samples/m1k3_voice_comparison/")
print(f"\n🎧 Opening directory for comparison...")

os.system('open "audio_samples/m1k3_voice_comparison"')
