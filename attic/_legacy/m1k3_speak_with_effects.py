#!/usr/bin/env python3
"""
M1K3 Speak with Effects
Wraps any TTS (including macOS 'say') with M1K3's nostalgic effect pipeline
"""

import sys
import subprocess
import tempfile
import os
from pathlib import Path

# Add src to path
sys.path.insert(0, str(Path(__file__).parent))

def speak_with_effects(text, effect_preset="lofi_balanced", voice="Samantha", rate=180):
    """
    Speak text with M1K3's nostalgic effect applied

    Args:
        text: Text to speak
        effect_preset: lofi_gentle, lofi_balanced, or lofi_aggressive
        voice: macOS voice name
        rate: Speech rate (words per minute)
    """
    try:
        from src.tts.effects.audio_effects import MultibandLoFiEffect, Film80sEffect
        import numpy as np
        import sounddevice as sd
        from scipy.io import wavfile

        # Create temporary file for audio
        with tempfile.NamedTemporaryFile(suffix='.aiff', delete=False) as tmp:
            tmp_path = tmp.name

        try:
            # Generate audio with macOS say command
            print(f"🎤 Generating speech: '{text[:50]}...'")
            subprocess.run([
                "say", "-v", voice, "-r", str(rate),
                "-o", tmp_path,
                text
            ], check=True)

            # Read the AIFF audio file using pydub
            print("📁 Loading audio...")
            from pydub import AudioSegment

            audio = AudioSegment.from_file(tmp_path, format="aiff")
            sample_rate = audio.frame_rate

            # Convert to numpy array
            audio_data = np.array(audio.get_array_of_samples(), dtype=np.float32)

            # Normalize to -1.0 to 1.0 range
            if audio.sample_width == 2:  # 16-bit
                audio_data = audio_data / 32768.0
            elif audio.sample_width == 4:  # 32-bit
                audio_data = audio_data / 2147483648.0

            # Ensure mono
            if audio.channels == 2:
                audio_data = audio_data.reshape((-1, 2)).mean(axis=1)

            print(f"✅ Loaded {len(audio_data)} samples @ {sample_rate}Hz")

            # Apply effect (Film80s or old LoFi)
            if effect_preset in ["theatrical", "vhs_hifi", "vhs_linear"]:
                print(f"🎚️  Applying 80s film effect ({effect_preset})...")
                effect = Film80sEffect({"preset": effect_preset})
            else:
                print(f"🎚️  Applying lofi effect ({effect_preset})...")
                effect = MultibandLoFiEffect({"preset": effect_preset})

            audio_data = effect.apply(audio_data, sample_rate)
            print("✅ Effect applied")

            # Play the processed audio
            print("🔊 Playing with nostalgic effect...")
            sd.play(audio_data, sample_rate)
            sd.wait()
            print("✅ Playback complete")

        finally:
            # Clean up temp file
            if os.path.exists(tmp_path):
                os.unlink(tmp_path)

    except ImportError as e:
        print(f"❌ Missing dependency: {e}")
        print("   Install with: pip install scipy sounddevice numpy")
        sys.exit(1)
    except Exception as e:
        print(f"❌ Error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="M1K3 Speak with 80s Film Effects")
    parser.add_argument("text", nargs="+", help="Text to speak")
    parser.add_argument("--preset", choices=["theatrical", "vhs_hifi", "vhs_linear", "lofi_gentle", "lofi_balanced", "lofi_aggressive"],
                       default="theatrical", help="Effect preset (default: theatrical - authentic 80s film sound)")
    parser.add_argument("--voice", default="Samantha", help="macOS voice (default: Samantha)")
    parser.add_argument("--rate", type=int, default=180, help="Speech rate WPM (default: 180)")

    args = parser.parse_args()
    text = " ".join(args.text)

    print("🎙️ M1K3 Speak with Effects")
    print("=" * 80)
    print()

    speak_with_effects(text, args.preset, args.voice, args.rate)

    print()
    print("=" * 80)
    print("✨ M1K3 80s film voice synthesis complete!")
