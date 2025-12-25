#!/usr/bin/env python3
"""
Quick progress announcement using Piper TTS
"""
import sys
import os

# Suppress ALSA warnings
os.environ['PYGAME_HIDE_SUPPORT_PROMPT'] = "1"

# Run the MCP TTS server speak command
text = sys.argv[1] if len(sys.argv) > 1 else "Progress update"
effect = sys.argv[2] if len(sys.argv) > 2 else "nostalgic"
intensity = float(sys.argv[3]) if len(sys.argv) > 3 else 0.6

print(f"🎤 Announcing: {text[:60]}...")

# Use Python to directly invoke TTS
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))

try:
    from src.tts.controllers.piper_tts_manager import PiperTTSManager
    from src.tts.effects.audio_effects import MultibandLoFiEffect
    import numpy as np
    import sounddevice as sd

    # Initialize Piper
    piper = PiperTTSManager()

    # Try to find any available voice
    available = list(piper.available_voices.keys())
    if available:
        piper.set_voice(available[0])

        if piper.load_model():
            # Generate
            audio = piper.generate(text)

            if audio is not None and effect == "nostalgic":
                # Apply nostalgic effect
                preset = "lofi_balanced" if intensity <= 0.7 else "lofi_aggressive"
                effect_obj = MultibandLoFiEffect({"preset": preset})
                audio = effect_obj.apply(audio, piper.sample_rate)

            if audio is not None:
                # Play
                if audio.dtype != np.float32:
                    audio = audio.astype(np.float32)
                sd.play(audio, piper.sample_rate)
                sd.wait()
                print("✅ Announcement complete")
            else:
                print("⚠️  Audio generation failed, printing text only")
                print(f"\n📢 {text}\n")
        else:
            print("⚠️  TTS unavailable, printing text only")
            print(f"\n📢 {text}\n")
    else:
        print("⚠️  No voices available, printing text only")
        print(f"\n📢 {text}\n")

except Exception as e:
    print(f"⚠️  TTS error: {e}")
    print(f"\n📢 {text}\n")
