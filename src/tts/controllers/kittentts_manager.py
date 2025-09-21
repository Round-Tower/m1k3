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
        if not self.tts_model:
            print("⚠️  KittenTTS model not loaded. Attempting to load...")
            if not self.load_model():
                print("❌ Failed to load KittenTTS model. Cannot generate audio.")
                return None
        
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
    
    def get_status(self) -> dict:
        """Get the status of the manager."""
        return {
            "available": self.is_available(),
            "loaded": self.tts_model is not None,
            "loading": self.loading,
            "model_name": self.model_name,
            "current_voice": self.current_voice,
            "available_voices": self.get_available_voices()
        }

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
