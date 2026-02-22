#!/usr/bin/env python3
"""Generate samples for ALL 54 Kokoro voices!"""

import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../../.."))

from src.tts.controllers.kokoro_tts_manager import kokoro_manager
import soundfile as sf
import time

print("🎤 Kokoro-82M - ALL VOICES Sample Generator")
print("=" * 70)

# Create output directory
os.makedirs("audio_samples/all_voices", exist_ok=True)

# Load model
print("\nLoading Kokoro-82M...")
if not kokoro_manager.load_model():
    print("❌ Failed to load model")
    sys.exit(1)

# Get all voices
all_voices = kokoro_manager.get_available_voices()
print(f"\n✅ Found {len(all_voices)} voices!")

# Test text
text = "Hello! I'm Kokoro. This is voice testing for the M1K3 AI assistant."

print(f"\n📝 Generating samples with text:")
print(f'   "{text}"')
print("\n" + "=" * 70)

successful = 0
failed = 0
total_gen_time = 0
total_audio_duration = 0

for i, voice_id in enumerate(all_voices, 1):
    # Parse voice info
    gender = "Female" if voice_id[1] == "f" else "Male"
    lang_map = {
        "a": "🇺🇸 American",
        "b": "🇬🇧 British",
        "e": "🇪🇸 Spanish",
        "f": "🇫🇷 French",
        "h": "🇮🇳 Hindi",
        "i": "🇮🇹 Italian",
        "j": "🇯🇵 Japanese",
        "p": "🇵🇹 Portuguese",
        "z": "🇨🇳 Chinese"
    }
    lang = lang_map.get(voice_id[0], "Unknown")

    print(f"\n[{i}/{len(all_voices)}] {voice_id}")
    print(f"    {lang} {gender}")

    try:
        start = time.time()
        audio = kokoro_manager.generate(text, voice=voice_id, speed=1.0)
        gen_time = time.time() - start

        if audio is not None:
            duration = len(audio) / kokoro_manager.sample_rate
            rtf = gen_time / duration if duration > 0 else 0

            # Save to file
            filename = f"audio_samples/all_voices/{voice_id}.wav"
            sf.write(filename, audio, kokoro_manager.sample_rate)

            print(f"    ✅ {duration:.2f}s in {gen_time:.2f}s (RTF: {rtf:.2f}x)")
            successful += 1
            total_gen_time += gen_time
            total_audio_duration += duration
        else:
            print(f"    ❌ Failed")
            failed += 1

    except Exception as e:
        print(f"    ❌ Error: {e}")
        failed += 1

print("\n" + "=" * 70)
print(f"\n📊 Summary:")
print(f"   ✅ Successful: {successful}/{len(all_voices)}")
print(f"   ❌ Failed: {failed}/{len(all_voices)}")
print(f"   ⏱️  Total generation time: {total_gen_time:.1f}s")
print(f"   🎵 Total audio duration: {total_audio_duration:.1f}s")
if total_audio_duration > 0:
    avg_rtf = total_gen_time / total_audio_duration
    print(f"   📊 Average RTF: {avg_rtf:.2f}x")

print(f"\n📂 All samples saved to: audio_samples/all_voices/")
print(f"\n🎧 Opening directory...")
