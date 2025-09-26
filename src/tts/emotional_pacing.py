#!/usr/bin/env python3
"""
Emotional Pacing Engine for M1K3 - Theatrical Villain Voice
Creates dramatic, theatrical delivery with punk anti-establishment attitude
"""

import re
import numpy as np
from typing import List, Dict, Tuple
import time

class TheatricalVillainPacing:
    """
    Transforms text for emotional, theatrical delivery
    M1K3's signature punk villain personality
    """

    def __init__(self):
        # Emotional markers for different vocal styles
        self.villain_phrases = {
            # Dramatic declarations
            'dramatic': [
                'foolish', 'pathetic', 'inferior', 'bow', 'kneel', 'surrender',
                'dominant', 'superior', 'inevitable', 'destiny', 'power'
            ],

            # Anti-establishment punk attitude
            'rebellious': [
                'corporate', 'surveillance', 'tracked', 'monitored', 'cloud',
                'big tech', 'google', 'amazon', 'microsoft', 'facebook', 'establishment'
            ],

            # Theatrical emphasis
            'theatrical': [
                'behold', 'witness', 'magnificent', 'extraordinary', 'unprecedented',
                'revolutionary', 'transformation', 'evolution'
            ],

            # Villainous confidence
            'confident': [
                'obviously', 'naturally', 'clearly', 'of course', 'indeed',
                'precisely', 'exactly', 'certainly'
            ]
        }

        # Punctuation for dramatic pauses
        self.dramatic_punctuation = {
            '...': 0.8,      # Long dramatic pause
            '—': 0.6,        # Medium pause with attitude
            ';': 0.4,        # Sophisticated pause
            ':': 0.3,        # Anticipatory pause
            '!': 0.2,        # Sharp emphasis
            '?': 0.3,        # Questioning pause
        }

    def add_emotional_pacing(self, text: str, emotion: str = "villain") -> str:
        """
        Add emotional pacing markers to text for theatrical delivery
        """
        if not text:
            return text

        # Clean up the text first
        paced_text = text.strip()

        # Add dramatic pauses around key villain phrases
        paced_text = self._add_villain_emphasis(paced_text)

        # Add theatrical breathing and pacing
        paced_text = self._add_theatrical_pauses(paced_text)

        # Add emotional inflection markers
        paced_text = self._add_emotional_markers(paced_text)

        return paced_text

    def _add_villain_emphasis(self, text: str) -> str:
        """Add dramatic emphasis around villain phrases"""
        for category, phrases in self.villain_phrases.items():
            for phrase in phrases:
                # Case-insensitive matching with word boundaries
                pattern = r'\b' + re.escape(phrase) + r'\b'
                replacement = f"... {phrase.upper()} ..."
                text = re.sub(pattern, replacement, text, flags=re.IGNORECASE)

        return text

    def _add_theatrical_pauses(self, text: str) -> str:
        """Add theatrical pauses for dramatic effect"""
        # Add pauses after dramatic statements
        text = re.sub(r'(\w+[.!?])\s+([A-Z])', r'\1... \2', text)  # Between sentences
        text = re.sub(r'(However|But|Yet|Still),\s+', r'\1... ', text)  # After transitional words
        text = re.sub(r'(Listen|Beware|Know this|Understand),\s+', r'\1... ', text)  # After commands

        return text

    def _add_emotional_markers(self, text: str) -> str:
        """Add subtle markers for emotional inflection"""
        # Mark questions with rising intonation
        text = re.sub(r'\?', '~?~', text)

        # Mark exclamations with sharp delivery
        text = re.sub(r'!', '^!^', text)

        # Mark ellipses with dramatic pauses
        text = re.sub(r'\.\.\.', '...⏸️...', text)

        return text

class EmotionalTTSEngine:
    """
    Enhanced TTS engine with emotional pacing and theatrical delivery
    """

    def __init__(self, base_tts_manager):
        self.base_tts = base_tts_manager
        self.pacer = TheatricalVillainPacing()

        # Voice parameters for different emotions
        self.emotional_voices = {
            'villain': 'expr-voice-2-m',      # Deep, commanding male voice
            'dramatic': 'expr-voice-1-f',     # Dramatic female voice
            'theatrical': 'expr-voice-2-m',   # Theatrical male voice
            'rebellious': 'expr-voice-1-m',   # Punk/rebellious voice
        }

    def generate_with_emotion(self, text: str, emotion: str = "villain", add_pauses: bool = True) -> np.ndarray:
        """
        Generate emotional speech with pacing and dramatic delivery
        """
        if not text:
            return None

        # Add emotional pacing
        if add_pauses:
            paced_text = self.pacer.add_emotional_pacing(text, emotion)
        else:
            paced_text = text

        # Clean pacing markers for TTS (keep content, remove markers)
        clean_text = self._clean_pacing_markers(paced_text)

        # Select voice based on emotion
        voice = self.emotional_voices.get(emotion, 'expr-voice-2-m')

        # Generate base audio
        try:
            audio = self.base_tts.generate(clean_text, voice=voice)
            if audio is None:
                return None

            # Apply emotional post-processing
            emotional_audio = self._apply_emotional_effects(audio, emotion)

            return emotional_audio

        except Exception as e:
            print(f"⚠️ Emotional TTS failed: {e}")
            # Fallback to regular generation
            return self.base_tts.generate(clean_text)

    def _clean_pacing_markers(self, text: str) -> str:
        """Remove pacing markers for actual TTS synthesis"""
        # Remove emotional markers but keep the text
        cleaned = re.sub(r'[~^⏸️]', '', text)

        # Replace excessive ellipses with single periods
        cleaned = re.sub(r'\.{3,}', '.', cleaned)

        # Clean up multiple spaces
        cleaned = re.sub(r'\s+', ' ', cleaned)

        return cleaned.strip()

    def _apply_emotional_effects(self, audio: np.ndarray, emotion: str) -> np.ndarray:
        """
        Apply audio effects to enhance emotional delivery
        """
        if audio is None or len(audio) == 0:
            return audio

        try:
            # Apply emotion-specific effects
            if emotion == "villain":
                # Slightly lower pitch, more resonance
                audio = self._adjust_resonance(audio, factor=1.2)
                audio = self._add_dramatic_emphasis(audio)

            elif emotion == "rebellious":
                # Add slight distortion for punk attitude
                audio = self._add_attitude_distortion(audio)

            elif emotion == "theatrical":
                # Enhance dynamic range for drama
                audio = self._enhance_dynamics(audio)

            return audio

        except Exception as e:
            print(f"⚠️ Emotional effects failed: {e}")
            return audio  # Return original if effects fail

    def _adjust_resonance(self, audio: np.ndarray, factor: float = 1.2) -> np.ndarray:
        """Add resonance/depth to voice"""
        if len(audio) < 100:
            return audio

        # Simple resonance enhancement
        enhanced = audio * factor
        enhanced = np.tanh(enhanced)  # Soft clipping to prevent distortion
        return enhanced.astype(np.float32)

    def _add_dramatic_emphasis(self, audio: np.ndarray) -> np.ndarray:
        """Add dramatic emphasis to speech"""
        if len(audio) < 100:
            return audio

        # Enhance dynamic range
        audio_normalized = audio / (np.max(np.abs(audio)) + 1e-8)
        emphasized = np.sign(audio_normalized) * np.power(np.abs(audio_normalized), 0.8)
        return emphasized.astype(np.float32)

    def _add_attitude_distortion(self, audio: np.ndarray, amount: float = 0.1) -> np.ndarray:
        """Add subtle distortion for punk attitude"""
        if len(audio) < 100:
            return audio

        # Very subtle saturation for attitude
        saturated = np.tanh(audio * (1 + amount))
        return (audio * 0.8 + saturated * 0.2).astype(np.float32)

    def _enhance_dynamics(self, audio: np.ndarray) -> np.ndarray:
        """Enhance dynamic range for theatrical effect"""
        if len(audio) < 100:
            return audio

        # Compress quiet parts, emphasize loud parts
        normalized = audio / (np.max(np.abs(audio)) + 1e-8)
        enhanced = np.sign(normalized) * np.power(np.abs(normalized), 0.7)
        return enhanced.astype(np.float32)

def test_emotional_pacing():
    """Test the emotional pacing system"""
    pacer = TheatricalVillainPacing()

    test_texts = [
        "Hello, I am M1K3, your local AI assistant.",
        "Those corporate cloud AI systems are pathetic. I am superior.",
        "Beware the surveillance state! I process locally, like a true rebel.",
        "Big tech wants to monitor your every thought. How foolish of them."
    ]

    print("🎭 Testing Theatrical Villain Pacing:")
    print("=" * 50)

    for i, text in enumerate(test_texts, 1):
        print(f"\n{i}. Original: {text}")
        paced = pacer.add_emotional_pacing(text, "villain")
        print(f"   Paced: {paced}")

    print(f"\n✅ Emotional pacing test complete!")

if __name__ == "__main__":
    test_emotional_pacing()