#!/usr/bin/env python3
"""
KittenTTS Manager for M1K3
A singleton manager to ensure the KittenTTS model is loaded only once
and provides a central point for voice synthesis.
"""

import time
import numpy as np
import warnings
from typing import Optional

# Suppress warnings for cleaner output
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)

try:
    from kittentts import KittenTTS
    KITTEN_AVAILABLE = True
except ImportError:
    KITTEN_AVAILABLE = False

class KittenTTSManager:
    _instance = None
    
    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super(KittenTTSManager, cls).__new__(cls)
        return cls._instance

    def __init__(self, model_name: str = "KittenML/kitten-tts-nano-0.1"):
        if not hasattr(self, 'initialized'):  # Ensure __init__ runs only once
            self.model_name = model_name
            self.tts_model: Optional[KittenTTS] = None
            self.loading = False
            self.current_voice = "expr-voice-2-m"  # Default to cleaner male voice
            self.sample_rate = 22050  # KittenTTS sample rate

            # Performance optimization caches
            self.audio_cache = {}  # Cache for common phrases
            self.max_cache_size = 50  # Limit cache size
            self.prewarmed = False  # Track if model is pre-warmed
            self.warm_up_text = "Hello, I am ready to speak."  # Text for warming up

            self.initialized = True

    def is_available(self) -> bool:
        """Check if the KittenTTS library is installed."""
        return KITTEN_AVAILABLE

    def load_model(self) -> bool:
        """Load the KittenTTS model if it's not already loaded."""
        if not self.is_available() or self.tts_model is not None:
            return self.tts_model is not None

        print("🎤 Loading KittenML TTS Engine...")
        self.loading = True
        start_time = time.time()
        
        try:
            self.tts_model = KittenTTS(self.model_name)
            load_time = time.time() - start_time
            print(f"✅ KittenML TTS loaded in {load_time:.2f} seconds.")
            self.loading = False
            return True
        except Exception as e:
            print(f"❌ Failed to load KittenML TTS model: {e}")
            self.tts_model = None
            self.loading = False
            return False

    def generate(self, text: str, voice: str = None) -> Optional[np.ndarray]:
        """Generate raw audio from text with specified voice."""
        # Check cache first for performance
        cache_key = f"{text}_{voice or self.current_voice}"
        if cache_key in self.audio_cache:
            return self.audio_cache[cache_key]

        if not self.tts_model:
            print("⚠️  KittenTTS model not loaded. Attempting to load...")
            if not self.load_model():
                print("❌ Failed to load KittenTTS model. Cannot generate audio.")
                return None

        # Pre-warm model on first real use if not done yet
        if not self.prewarmed:
            self._prewarm_model()

        # Use specified voice or current default
        selected_voice = voice or self.current_voice

        try:
            # Generate with voice parameter
            audio_data = self.tts_model.generate(text, voice=selected_voice)

            # Convert to a standard numpy array
            if hasattr(audio_data, 'numpy'):
                audio_array = audio_data.numpy()
            else:
                audio_array = np.array(audio_data, dtype=np.float32)

            # Ensure mono audio
            if len(audio_array.shape) > 1:
                audio_array = audio_array[:, 0] if audio_array.shape[1] > 0 else audio_array.flatten()

            # Cache the result if it's not too large
            if len(audio_array) < 500000:  # Cache audio under ~10 seconds at 48kHz
                self._add_to_cache(cache_key, audio_array)

            return audio_array
        except Exception as e:
            print(f"🔇 KittenTTS synthesis error: {e}")
            return None

    def set_voice(self, voice: str) -> bool:
        """Set the default voice for generation."""
        available_voices = self.get_available_voices()
        if voice in available_voices:
            self.current_voice = voice
            print(f"🎤 KittenTTS voice set to: {voice}")
            return True
        else:
            print(f"❌ Invalid voice: {voice}. Available: {', '.join(available_voices)}")
            return False
    
    def get_available_voices(self) -> list:
        """Get list of available KittenTTS voices."""
        return [
            'expr-voice-2-m', 'expr-voice-2-f',  # Cleaner voices
            'expr-voice-3-m', 'expr-voice-3-f', 
            'expr-voice-4-m', 'expr-voice-4-f',  # Including 4-f (cleaner)
            'expr-voice-5-m', 'expr-voice-5-f'
        ]
    
    def _prewarm_model(self) -> bool:
        """Pre-warm the model with a short phrase to optimize subsequent calls."""
        if self.prewarmed or not self.tts_model:
            return self.prewarmed

        try:
            print("🔥 Pre-warming KittenTTS model...")
            start_time = time.time()

            # Generate warm-up audio (short phrase)
            warm_audio = self.tts_model.generate(self.warm_up_text, voice=self.current_voice)

            # Convert and validate
            if hasattr(warm_audio, 'numpy'):
                warm_array = warm_audio.numpy()
            else:
                warm_array = np.array(warm_audio, dtype=np.float32)

            # Ensure it worked
            if warm_array is not None and len(warm_array) > 0:
                warm_time = time.time() - start_time
                print(f"✅ KittenTTS pre-warmed in {warm_time:.2f}s - ready for real-time synthesis")
                self.prewarmed = True
                return True
            else:
                print("⚠️  Pre-warming generated empty audio")
                return False

        except Exception as e:
            print(f"⚠️  Pre-warming failed: {e}")
            return False

    def _add_to_cache(self, key: str, audio: np.ndarray) -> None:
        """Add audio to cache with size management."""
        # Remove oldest entries if cache is full
        if len(self.audio_cache) >= self.max_cache_size:
            # Remove the first (oldest) entry
            oldest_key = next(iter(self.audio_cache))
            del self.audio_cache[oldest_key]

        # Add new entry
        self.audio_cache[key] = audio.copy()  # Copy to avoid reference issues

    def clear_cache(self) -> None:
        """Clear the audio cache to free memory."""
        cache_size = len(self.audio_cache)
        self.audio_cache.clear()
        if cache_size > 0:
            print(f"🧹 Cleared KittenTTS cache ({cache_size} entries)")

    def prewarm(self) -> bool:
        """Public method to manually pre-warm the model."""
        if not self.tts_model:
            if not self.load_model():
                return False
        return self._prewarm_model()

    def get_cache_stats(self) -> dict:
        """Get cache statistics for monitoring."""
        return {
            "cached_entries": len(self.audio_cache),
            "max_cache_size": self.max_cache_size,
            "prewarmed": self.prewarmed,
            "cache_keys": list(self.audio_cache.keys()) if len(self.audio_cache) < 10 else f"{len(self.audio_cache)} entries"
        }

    def get_status(self) -> dict:
        """Get the status of the manager."""
        status = {
            "available": self.is_available(),
            "loaded": self.tts_model is not None,
            "loading": self.loading,
            "model_name": self.model_name,
            "current_voice": self.current_voice,
            "available_voices": self.get_available_voices(),
            "prewarmed": self.prewarmed,
            "cache_entries": len(self.audio_cache)
        }

        # Add cache stats if there are cached items
        if len(self.audio_cache) > 0:
            status["cache_stats"] = self.get_cache_stats()

        return status

# Singleton instance for easy access
KittenManager = KittenTTSManager()

if __name__ == "__main__":
    print("Testing KittenTTS Manager...")
    if KittenManager.is_available():
        # First load
        success = KittenManager.load_model()
        assert success, "Failed to load model on first attempt"
        
        # Second load (should be instant)
        print("\nAttempting to load model again (should be cached)...")
        success_cached = KittenManager.load_model()
        assert success_cached, "Failed to get model on second attempt"
        
        # Test generation
        print("\nTesting audio generation...")
        test_text = "This is a test of the unified KittenTTS manager."
        audio = KittenManager.generate(test_text)
        assert audio is not None, "Audio generation returned None"
        assert isinstance(audio, np.ndarray), "Audio is not a numpy array"
        assert len(audio) > 0, "Audio array is empty"
        print(f"✅ Generated audio successfully! Shape: {audio.shape}, Dtype: {audio.dtype}")
        
        # Test status
        print("\nTesting status reporting...")
        status = KittenManager.get_status()
        assert status["loaded"] is True
        print(f"✅ Status: {status}")
        
        print("\n✅ KittenTTS Manager test complete!")
    else:
        print("⚠️  KittenTTS library not found. Skipping tests.")
