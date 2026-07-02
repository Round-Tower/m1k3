#!/usr/bin/env python3
"""
M1K3 Emotional Voice Demo
Showcases M1K3's emotional voice character with prosody + Film80s effects
"""

import sys
import subprocess
import tempfile
import os
import time
from pathlib import Path

# Add src to path
sys.path.insert(0, str(Path(__file__).parent))

from src.tts.effects.audio_effects import Film80sEffect
from src.tts.effects.prosody_engine import ProsodyEngine, Emotion
from pydub import AudioSegment
import numpy as np
import sounddevice as sd

print("🎙️ M1K3 Emotional Voice Demo")
print("=" * 80)
print()
print("Showcasing M1K3's emotional voice character:")
print("  • Emotional prosody (pitch, speed, energy)")
print("  • Film80s analog warmth")
print("  • Multiple personality modes")
print()
print("=" * 80)
print()

# Initialize engines
prosody = ProsodyEngine(sample_rate=22050)
film_effect = Film80sEffect({"preset": "theatrical"})

def speak_with_emotion(
    text: str,
    emotion: Emotion = Emotion.NEUTRAL,
    intensity: float = 1.0,
    film_preset: str = "theatrical",
    voice: str = "Samantha",
    rate: int = 180
):
    """
    Speak text with emotional prosody and Film80s effect

    Args:
        text: Text to speak
        emotion: Emotion to apply
        intensity: Emotion intensity (0.0-1.0)
        film_preset: Film80s preset (theatrical, vhs_hifi, vhs_linear)
        voice: macOS voice name
        rate: Speech rate (words per minute)
    """
    try:
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
            ], check=True, capture_output=True)

            # Load audio
            print(f"📁 Loading audio...")
            audio = AudioSegment.from_file(tmp_path, format="aiff")
            sample_rate = audio.frame_rate

            # Convert to numpy array
            audio_data = np.array(audio.get_array_of_samples(), dtype=np.float32)

            # Normalize
            if audio.sample_width == 2:  # 16-bit
                audio_data = audio_data / 32768.0
            elif audio.sample_width == 4:  # 32-bit
                audio_data = audio_data / 2147483648.0

            # Ensure mono
            if audio.channels == 2:
                audio_data = audio_data.reshape((-1, 2)).mean(axis=1)

            print(f"✅ Loaded {len(audio_data)} samples @ {sample_rate}Hz")

            # Apply emotional prosody
            if emotion != Emotion.NEUTRAL:
                print(f"🎭 Applying {emotion.value} prosody (intensity: {intensity:.2f})...")
                prosody_engine = ProsodyEngine(sample_rate=sample_rate)
                audio_data = prosody_engine.apply_emotion(audio_data, emotion, intensity)
                print(f"✅ Prosody applied")

            # Apply Film80s effect
            print(f"🎚️  Applying Film80s effect ({film_preset})...")
            effect = Film80sEffect({"preset": film_preset})
            audio_data = effect.apply(audio_data, sample_rate)
            print(f"✅ Effect applied")

            # Play the processed audio
            print(f"🔊 Playing with emotional character...")
            sd.play(audio_data, sample_rate)
            sd.wait()
            print(f"✅ Playback complete")
            print()

        finally:
            # Clean up temp file
            if os.path.exists(tmp_path):
                os.unlink(tmp_path)

    except Exception as e:
        print(f"❌ Error: {e}")
        import traceback
        traceback.print_exc()

# Demo 1: Emotional Range
print("🎭 DEMO 1: Emotional Range")
print("-" * 80)
print()

emotional_tests = [
    {
        "emotion": Emotion.FRIENDLY,
        "text": "Hello! I'm M1K3, your friendly AI companion. I'm here to help you today!",
        "intensity": 0.8,
        "description": "Friendly & Welcoming"
    },
    {
        "emotion": Emotion.EXCITED,
        "text": "Wow! That's absolutely amazing! I'm so excited to hear about your success!",
        "intensity": 1.0,
        "description": "Excited & Energetic"
    },
    {
        "emotion": Emotion.EMPATHETIC,
        "text": "I'm sorry to hear you're having trouble. Let me help you with that gently.",
        "intensity": 0.9,
        "description": "Empathetic & Caring"
    },
    {
        "emotion": Emotion.CALM,
        "text": "Let's take a moment to review the information calmly and carefully together.",
        "intensity": 0.7,
        "description": "Calm & Measured"
    },
    {
        "emotion": Emotion.PROFESSIONAL,
        "text": "As your professional AI assistant, I can help you complete that task efficiently.",
        "intensity": 0.6,
        "description": "Professional & Clear"
    }
]

for i, test in enumerate(emotional_tests, 1):
    print(f"{i}. {test['description']} (Emotion: {test['emotion'].value})")
    print(f"   Text: \"{test['text'][:60]}...\"")
    print()

    speak_with_emotion(
        text=test['text'],
        emotion=test['emotion'],
        intensity=test['intensity'],
        film_preset="theatrical"
    )

    if i < len(emotional_tests):
        print("⏸️  Pausing between demos...")
        time.sleep(1)
        print()

# Demo 2: Voice Character Profiles
print()
print("=" * 80)
print("🎙️ DEMO 2: M1K3 Voice Character Profiles")
print("-" * 80)
print()

profile_tests = [
    {
        "name": "M1K3 Friendly (Default)",
        "emotion": Emotion.FRIENDLY,
        "film_preset": "theatrical",
        "text": "This is M1K3's default friendly voice. Warm, empathetic, with that classic analog character.",
        "voice": "Samantha",
        "rate": 180
    },
    {
        "name": "M1K3 Professional",
        "emotion": Emotion.PROFESSIONAL,
        "film_preset": "theatrical",
        "text": "Professional mode provides clear, measured communication for business contexts.",
        "voice": "Samantha",
        "rate": 170
    },
    {
        "name": "M1K3 Playful",
        "emotion": Emotion.PLAYFUL,
        "film_preset": "vhs_hifi",
        "text": "Playful mode adds expressive variation and dynamic energy to conversations!",
        "voice": "Samantha",
        "rate": 190
    },
    {
        "name": "M1K3 Male Voice",
        "emotion": Emotion.FRIENDLY,
        "film_preset": "theatrical",
        "text": "M1K3 also works great with a male voice character. Same warmth, different tone.",
        "voice": "Alex",
        "rate": 180
    }
]

for i, profile in enumerate(profile_tests, 1):
    print(f"{i}. {profile['name']}")
    print(f"   Emotion: {profile['emotion'].value}")
    print(f"   Film preset: {profile['film_preset']}")
    print(f"   Text: \"{profile['text'][:60]}...\"")
    print()

    speak_with_emotion(
        text=profile['text'],
        emotion=profile['emotion'],
        intensity=0.8,
        film_preset=profile['film_preset'],
        voice=profile['voice'],
        rate=profile['rate']
    )

    if i < len(profile_tests):
        print("⏸️  Pausing between profiles...")
        time.sleep(1)
        print()

# Demo 3: Film80s Effect Comparison
print()
print("=" * 80)
print("🎚️  DEMO 3: Film80s Effect Presets")
print("-" * 80)
print()

film_tests = [
    {
        "preset": "theatrical",
        "description": "Theatrical Cinema",
        "text": "This is the theatrical preset. Professional cinema sound with warm analog tape saturation."
    },
    {
        "preset": "vhs_hifi",
        "description": "VHS Hi-Fi",
        "text": "V H S Hi Fi preset provides high fidelity home video quality with subtle tape character."
    },
    {
        "preset": "vhs_linear",
        "description": "VHS Linear",
        "text": "V H S Linear brings that nostalgic consumer tape warmth we all remember."
    }
]

for i, preset_test in enumerate(film_tests, 1):
    print(f"{i}. {preset_test['description']} ({preset_test['preset']})")
    print(f"   Text: \"{preset_test['text'][:60]}...\"")
    print()

    speak_with_emotion(
        text=preset_test['text'],
        emotion=Emotion.FRIENDLY,
        intensity=0.7,
        film_preset=preset_test['preset']
    )

    if i < len(film_tests):
        print("⏸️  Pausing between presets...")
        time.sleep(1)
        print()

# Final summary
print()
print("=" * 80)
print("✨ M1K3 Emotional Voice Demo Complete!")
print("=" * 80)
print()
print("📊 Summary:")
print(f"   • Emotional range: {len(emotional_tests)} emotions demonstrated")
print(f"   • Voice profiles: {len(profile_tests)} character modes")
print(f"   • Film effects: {len(film_tests)} analog presets")
print()
print("🎙️ M1K3 Voice Features:")
print("   ✅ Emotional prosody (pitch, speed, energy)")
print("   ✅ Authentic 80s film analog warmth")
print("   ✅ Multiple voice character profiles")
print("   ✅ Auto emotion detection from text")
print("   ✅ Mobile-ready architecture (ONNX export)")
print()
print("🎉 M1K3 is ready to speak with emotion and character!")
print()
print("💡 Next Steps:")
print("   • Download Piper TTS models for better quality")
print("   • Integrate with UnifiedVoiceEngine")
print("   • Export to ONNX for 間 AI mobile app")
print("   • Fine-tune emotional profiles based on user feedback")
