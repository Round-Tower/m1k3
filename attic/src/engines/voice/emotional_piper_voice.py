#!/usr/bin/env python3
"""
Emotional Piper Voice Engine for M1K3
Combines Piper TTS with emotional prosody and Film80sEffect for branded character
"""

import numpy as np
from typing import Optional, Tuple
import time

from src.tts.controllers.piper_tts_manager import piper_manager
from src.tts.effects.prosody_engine import ProsodyEngine, Emotion
from src.tts.effects.audio_effects import Film80sEffect


class EmotionalPiperVoice:
    """
    M1K3's branded emotional voice character

    Combines:
    1. Piper neural TTS (high-quality base synthesis)
    2. Prosody Engine (emotional characteristics)
    3. Film80sEffect (warm analog character)

    Creates a warm, empathetic AI companion voice with subtle retro character
    """

    def __init__(self):
        self.piper = piper_manager
        self.prosody = ProsodyEngine(sample_rate=22050)  # Piper outputs 22050Hz
        self.film_effect = None  # Lazy load

        self.sample_rate = 22050
        self.is_loaded = False

        # M1K3 voice character profiles
        self.voice_profiles = {
            "m1k3_friendly": {
                "description": "M1K3's default friendly companion voice",
                "base_emotion": Emotion.FRIENDLY,
                "intensity": 0.8,
                "film_preset": "theatrical",
                "piper_voice": "en_US-amy-medium"  # Clear female voice
            },
            "m1k3_professional": {
                "description": "Professional AI assistant mode",
                "base_emotion": Emotion.PROFESSIONAL,
                "intensity": 0.6,
                "film_preset": "theatrical",
                "piper_voice": "en_US-amy-medium"
            },
            "m1k3_playful": {
                "description": "Playful and expressive character",
                "base_emotion": Emotion.PLAYFUL,
                "intensity": 1.0,
                "film_preset": "vhs_hifi",
                "piper_voice": "en_US-amy-medium"
            },
            "m1k3_empathetic": {
                "description": "Gentle, empathetic support mode",
                "base_emotion": Emotion.EMPATHETIC,
                "intensity": 0.9,
                "film_preset": "theatrical",
                "piper_voice": "en_US-amy-medium"
            },
            "m1k3_male": {
                "description": "M1K3 with male voice character",
                "base_emotion": Emotion.FRIENDLY,
                "intensity": 0.7,
                "film_preset": "theatrical",
                "piper_voice": "en_US-ryan-high"  # Male voice
            }
        }

        self.current_profile = "m1k3_friendly"
        self.auto_emotion = True  # Automatically detect emotion from text

    def load_model(self) -> bool:
        """Load Piper TTS model"""
        if self.is_loaded:
            return True

        print("🎙️ Loading M1K3 Emotional Voice Engine...")
        print("   • Base: Piper neural TTS")
        print("   • Enhancement: Emotional prosody + Film80s character")

        if not self.piper.is_available():
            print("❌ Piper TTS not available")
            return False

        if not self.piper.load_model():
            print("❌ Failed to load Piper TTS")
            return False

        # Set default voice
        profile = self.voice_profiles[self.current_profile]
        self.piper.set_voice(profile["piper_voice"])

        self.is_loaded = True
        print(f"✅ M1K3 Emotional Voice ready (profile: {self.current_profile})")
        return True

    def is_available(self) -> bool:
        """Check if engine is available"""
        return self.piper.is_available()

    def generate(
        self,
        text: str,
        emotion: Optional[Emotion] = None,
        intensity: float = 1.0,
        apply_film_effect: bool = True
    ) -> Optional[np.ndarray]:
        """
        Generate emotional speech with M1K3's branded character

        Args:
            text: Text to synthesize
            emotion: Override emotion (None = auto-detect)
            intensity: Emotion intensity (0.0-1.0)
            apply_film_effect: Apply Film80sEffect for analog character

        Returns:
            Audio array (float32, mono, 22050Hz)
        """
        if not self.is_loaded:
            if not self.load_model():
                return None

        try:
            start_time = time.time()

            # 1. Auto-detect emotion if enabled
            if emotion is None and self.auto_emotion:
                detected_emotion, confidence = self.prosody.detect_emotion_from_text(text)
                emotion = detected_emotion
                intensity = intensity * confidence
                print(f"🎭 Detected emotion: {emotion.value} (intensity: {intensity:.2f})")
            elif emotion is None:
                # Use profile default
                profile = self.voice_profiles[self.current_profile]
                emotion = profile["base_emotion"]
                intensity = profile["intensity"]

            # 2. Generate base audio with Piper
            print(f"🎤 Synthesizing with Piper TTS...")
            base_audio = self.piper.generate(text)

            if base_audio is None or len(base_audio) == 0:
                print("❌ Piper synthesis failed")
                return None

            synthesis_time = time.time() - start_time
            print(f"✅ Base synthesis: {synthesis_time:.2f}s ({len(base_audio)/self.sample_rate:.2f}s audio)")

            # 3. Apply emotional prosody
            if emotion != Emotion.NEUTRAL:
                print(f"🎭 Applying {emotion.value} prosody (intensity: {intensity:.2f})...")
                prosody_start = time.time()
                emotional_audio = self.prosody.apply_emotion(base_audio, emotion, intensity)
                prosody_time = time.time() - prosody_start
                print(f"✅ Prosody applied: {prosody_time:.2f}s")
            else:
                emotional_audio = base_audio

            # 4. Apply Film80sEffect for M1K3 character
            if apply_film_effect:
                profile = self.voice_profiles[self.current_profile]
                film_preset = profile["film_preset"]

                print(f"🎚️  Applying Film80s character ({film_preset})...")
                effect_start = time.time()

                # Lazy load film effect
                if self.film_effect is None:
                    self.film_effect = Film80sEffect({"preset": film_preset})

                final_audio = self.film_effect.apply(emotional_audio, self.sample_rate)
                effect_time = time.time() - effect_start
                print(f"✅ Film effect applied: {effect_time:.2f}s")
            else:
                final_audio = emotional_audio

            total_time = time.time() - start_time
            print(f"🎉 Total generation: {total_time:.2f}s")

            return final_audio.astype(np.float32)

        except Exception as e:
            print(f"❌ Emotional voice generation failed: {e}")
            import traceback
            traceback.print_exc()
            return None

    def set_profile(self, profile_name: str) -> bool:
        """Set M1K3 voice profile"""
        if profile_name not in self.voice_profiles:
            print(f"❌ Unknown profile: {profile_name}")
            print(f"   Available: {', '.join(self.voice_profiles.keys())}")
            return False

        self.current_profile = profile_name

        # Update Piper voice
        profile = self.voice_profiles[profile_name]
        if self.is_loaded:
            self.piper.set_voice(profile["piper_voice"])

        # Update film effect
        self.film_effect = Film80sEffect({"preset": profile["film_preset"]})

        print(f"✅ M1K3 voice profile: {profile_name}")
        print(f"   {profile['description']}")
        return True

    def set_auto_emotion(self, enabled: bool):
        """Enable/disable automatic emotion detection"""
        self.auto_emotion = enabled
        print(f"🎭 Auto emotion detection: {'enabled' if enabled else 'disabled'}")

    def get_available_profiles(self) -> list:
        """Get list of available M1K3 voice profiles"""
        return list(self.voice_profiles.keys())

    def get_status(self) -> dict:
        """Get engine status"""
        return {
            "available": self.is_available(),
            "loaded": self.is_loaded,
            "current_profile": self.current_profile,
            "auto_emotion": self.auto_emotion,
            "available_profiles": self.get_available_profiles(),
            "sample_rate": self.sample_rate,
            "piper_status": self.piper.get_status()
        }


# Singleton instance
emotional_voice = EmotionalPiperVoice()


if __name__ == "__main__":
    print("🎙️ M1K3 Emotional Voice Engine Test")
    print("=" * 80)
    print()

    # Test availability
    if not emotional_voice.is_available():
        print("❌ Piper TTS not available. Install with:")
        print("   pip install piper-tts")
        exit(1)

    # Load model
    if not emotional_voice.load_model():
        print("❌ Failed to load model")
        exit(1)

    print()
    print("📊 Engine Status:")
    status = emotional_voice.get_status()
    for key, value in status.items():
        if key != "piper_status":
            print(f"   {key}: {value}")

    print()
    print("🎭 Testing emotional synthesis...")
    print("-" * 80)

    # Test texts with different emotions
    test_cases = [
        ("Hello! I'm M1K3, your friendly AI companion. I'm here to help you!", Emotion.FRIENDLY),
        ("I'm sorry to hear you're having trouble. Let me help you with that.", Emotion.EMPATHETIC),
        ("Wow! That's absolutely amazing! I'm so excited to hear about your success!", Emotion.EXCITED),
        ("Let's take a moment to review the data calmly and carefully.", Emotion.CALM),
        ("As your professional AI assistant, I can help you with that task.", Emotion.PROFESSIONAL)
    ]

    for i, (text, expected_emotion) in enumerate(test_cases, 1):
        print(f"\n{i}. Testing: {expected_emotion.value}")
        print(f"   Text: '{text[:60]}...'")

        # Generate with auto-detection
        audio = emotional_voice.generate(text, apply_film_effect=True)

        if audio is not None:
            print(f"   ✅ Generated {len(audio)/emotional_voice.sample_rate:.2f}s audio")
            print(f"   RMS: {np.sqrt(np.mean(audio**2)):.4f}")
        else:
            print(f"   ❌ Generation failed")

        print()

    print("=" * 80)
    print("✅ M1K3 Emotional Voice Engine test complete!")
    print()
    print("🎉 M1K3 is ready to speak with emotion and character!")
