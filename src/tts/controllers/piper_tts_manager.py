#!/usr/bin/env python3
"""
Piper TTS Manager for M1K3
Ultra-fast neural TTS engine optimized for real-time applications
Piper is 10x faster than traditional neural TTS while maintaining human-like quality
"""

import time
import numpy as np
import warnings
import os
import tempfile
from typing import Optional, List, Dict, Any
from pathlib import Path

# Suppress warnings for cleaner output
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)

try:
    from piper import PiperVoice
    PIPER_AVAILABLE = True
except ImportError:
    PIPER_AVAILABLE = False

try:
    import soundfile as sf
    SOUNDFILE_AVAILABLE = True
except ImportError:
    SOUNDFILE_AVAILABLE = False


class PiperTTSManager:
    """
    Ultra-fast neural TTS using Piper engine
    Optimized for real-time applications with sub-50ms latency
    """
    _instance = None

    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super(PiperTTSManager, cls).__new__(cls)
        return cls._instance

    def __init__(self, model_path: Optional[str] = None):
        if not hasattr(self, 'initialized'):  # Ensure __init__ runs only once
            self.model_path = model_path
            self.voice: Optional[PiperVoice] = None
            self.loading = False
            self.sample_rate = 22050  # Piper's default sample rate
            self.current_voice = "en_US-lessac-medium"  # Default voice

            # Performance settings
            self.length_scale = 1.0  # Speed multiplier (1.0 = normal, <1.0 = faster)
            self.noise_scale = 0.667  # Noise for synthesis quality
            self.noise_w = 0.8  # Noise weight

            # Available voice models (will be populated on initialization)
            self.available_voices = {
                "en_US-lessac-medium": {
                    "name": "Lessac (Medium Quality)",
                    "language": "en-US",
                    "quality": "medium",
                    "speed": "fast"
                },
                "en_US-lessac-low": {
                    "name": "Lessac (Low Quality - Fastest)",
                    "language": "en-US",
                    "quality": "low",
                    "speed": "fastest"
                },
                "en_US-amy-medium": {
                    "name": "Amy (Medium Quality)",
                    "language": "en-US",
                    "quality": "medium",
                    "speed": "fast"
                },
                "en_US-ryan-medium": {
                    "name": "Ryan (Medium Quality)",
                    "language": "en-US",
                    "quality": "medium",
                    "speed": "fast"
                }
            }

            self.initialized = True

    def is_available(self) -> bool:
        """Check if Piper TTS is available"""
        return PIPER_AVAILABLE and SOUNDFILE_AVAILABLE

    def get_availability_info(self) -> Dict[str, Any]:
        """Get detailed availability information"""
        return {
            "available": self.is_available(),
            "piper_installed": PIPER_AVAILABLE,
            "soundfile_installed": SOUNDFILE_AVAILABLE,
            "voice_loaded": self.voice is not None,
            "current_voice": self.current_voice,
            "sample_rate": self.sample_rate,
            "available_voices": len(self.available_voices)
        }

    def load_model(self, voice_name: Optional[str] = None) -> bool:
        """Load the Piper TTS model"""
        if not self.is_available():
            print("❌ Piper TTS dependencies not available")
            print("   Install with: pip install piper-tts soundfile")
            return False

        if self.voice is not None and voice_name == self.current_voice:
            return True  # Already loaded with correct voice

        if voice_name and voice_name in self.available_voices:
            self.current_voice = voice_name

        print(f"🎤 Loading Piper TTS voice: {self.current_voice}")
        self.loading = True
        start_time = time.time()

        try:
            # Download voice model if not present (Piper handles this automatically)
            self.voice = PiperVoice.load(self.current_voice, use_cuda=False)

            # Set performance parameters for real-time usage
            self.voice.config.length_scale = self.length_scale
            self.voice.config.noise_scale = self.noise_scale
            self.voice.config.noise_w = self.noise_w

            load_time = time.time() - start_time
            print(f"✅ Piper TTS loaded in {load_time:.2f} seconds")
            print(f"   Voice: {self.available_voices[self.current_voice]['name']}")
            print(f"   Sample rate: {self.sample_rate}Hz")
            print(f"   Optimized for: Real-time synthesis (sub-50ms)")

            self.loading = False
            return True

        except Exception as e:
            print(f"❌ Failed to load Piper TTS: {e}")
            print(f"   Attempted voice: {self.current_voice}")
            self.voice = None
            self.loading = False
            return False

    def generate(self, text: str, voice: Optional[str] = None) -> Optional[np.ndarray]:
        """Generate audio from text using Piper TTS"""
        if not self.voice:
            print("⚠️  Piper TTS model not loaded. Attempting to load...")
            if not self.load_model(voice):
                print("❌ Failed to load Piper TTS model. Cannot generate audio.")
                return None

        # Switch voice if requested
        if voice and voice != self.current_voice and voice in self.available_voices:
            if not self.load_model(voice):
                print(f"⚠️  Failed to switch to voice '{voice}', using current voice")

        try:
            start_time = time.time()

            # Generate audio using Piper
            with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as temp_file:
                temp_path = temp_file.name

            # Piper generates to file (most efficient method)
            self.voice.synthesize(text, temp_path)

            # Read the generated audio file
            audio_data, sample_rate = sf.read(temp_path, dtype=np.float32)

            # Clean up temporary file
            os.unlink(temp_path)

            # Ensure correct sample rate
            if sample_rate != self.sample_rate:
                print(f"⚠️  Sample rate mismatch: expected {self.sample_rate}, got {sample_rate}")

            # Ensure mono audio
            if len(audio_data.shape) > 1:
                audio_data = audio_data[:, 0] if audio_data.shape[1] > 0 else audio_data.flatten()

            generation_time = time.time() - start_time
            duration = len(audio_data) / sample_rate
            rtf = generation_time / duration if duration > 0 else 0

            print(f"🎯 Piper TTS: Generated {duration:.2f}s audio in {generation_time:.3f}s (RTF: {rtf:.2f}x)")

            return audio_data

        except Exception as e:
            print(f"❌ Piper TTS synthesis error: {e}")
            return None

    def set_voice(self, voice_name: str) -> bool:
        """Set the voice for generation"""
        if voice_name not in self.available_voices:
            print(f"❌ Voice '{voice_name}' not available")
            print(f"   Available voices: {list(self.available_voices.keys())}")
            return False

        if voice_name != self.current_voice:
            return self.load_model(voice_name)
        return True

    def get_available_voices(self) -> List[str]:
        """Get list of available voice names"""
        return list(self.available_voices.keys())

    def get_voice_info(self, voice_name: str) -> Optional[Dict[str, Any]]:
        """Get information about a specific voice"""
        return self.available_voices.get(voice_name)

    def set_speed(self, speed_multiplier: float) -> bool:
        """Set synthesis speed (1.0 = normal, <1.0 = faster, >1.0 = slower)"""
        if 0.1 <= speed_multiplier <= 3.0:
            self.length_scale = speed_multiplier
            if self.voice:
                self.voice.config.length_scale = speed_multiplier
            print(f"🎯 Piper TTS speed set to {speed_multiplier}x")
            return True
        else:
            print(f"❌ Speed multiplier must be between 0.1 and 3.0")
            return False

    def set_quality(self, noise_scale: float = 0.667, noise_w: float = 0.8) -> bool:
        """Set synthesis quality parameters"""
        if 0.0 <= noise_scale <= 1.0 and 0.0 <= noise_w <= 1.0:
            self.noise_scale = noise_scale
            self.noise_w = noise_w
            if self.voice:
                self.voice.config.noise_scale = noise_scale
                self.voice.config.noise_w = noise_w
            print(f"🎯 Piper TTS quality set: noise_scale={noise_scale}, noise_w={noise_w}")
            return True
        else:
            print(f"❌ Quality parameters must be between 0.0 and 1.0")
            return False

    def get_model_info(self) -> Dict[str, Any]:
        """Get current model information"""
        if not self.voice:
            return {"loaded": False}

        return {
            "loaded": True,
            "voice": self.current_voice,
            "voice_info": self.available_voices.get(self.current_voice, {}),
            "sample_rate": self.sample_rate,
            "length_scale": self.length_scale,
            "noise_scale": self.noise_scale,
            "noise_w": self.noise_w,
            "available_voices": len(self.available_voices),
            "engine_type": "piper_neural_tts",
            "optimization": "real_time"
        }

    def cleanup(self):
        """Clean up resources"""
        if self.voice:
            # Piper handles cleanup automatically
            pass
        self.voice = None
        self.loading = False


# Create singleton instance
piper_manager = PiperTTSManager()


if __name__ == "__main__":
    # Test Piper TTS Manager
    print("🧪 Testing Piper TTS Manager")

    manager = PiperTTSManager()

    if manager.is_available():
        print("✅ Piper TTS available")
        print(f"📊 Availability info: {manager.get_availability_info()}")

        if manager.load_model():
            print("✅ Model loaded successfully")

            # Test synthesis
            test_text = "Hello! This is a test of the ultra-fast Piper TTS engine optimized for real-time chat."
            print(f"\n🎤 Generating speech: '{test_text}'")

            audio = manager.generate(test_text)

            if audio is not None:
                duration = len(audio) / manager.sample_rate
                print(f"✅ Generated {duration:.2f}s of audio")
                print(f"📊 Model info: {manager.get_model_info()}")
            else:
                print("❌ Failed to generate audio")
        else:
            print("❌ Failed to load model")
    else:
        print("❌ Piper TTS not available")
        print(f"📊 Availability info: {manager.get_availability_info()}")

    manager.cleanup()
    print("🧪 Test complete")