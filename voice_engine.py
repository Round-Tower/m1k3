#!/usr/bin/env python3
"""
M1K3 Voice Engine
Handles text-to-speech using KittenML TTS and audio playback
"""

import io
import time
import threading
from pathlib import Path
from typing import Optional

try:
    import sounddevice as sd
    import soundfile as sf
    from kittentts import KittenTTS
    VOICE_AVAILABLE = True
except ImportError as e:
    print(f"Voice dependencies not available: {e}")
    VOICE_AVAILABLE = False

class VoiceEngine:
    """KittenML TTS integration for M1K3"""
    
    def __init__(self, model_name: str = "KittenML/kitten-tts-nano-0.1"):
        self.model_name = model_name
        self.tts_model: Optional[KittenTTS] = None
        self.voice_enabled = False
        self.loading = False
        
    def is_available(self) -> bool:
        """Check if voice synthesis is available"""
        return VOICE_AVAILABLE
        
    def load_model(self) -> bool:
        """Load the KittenTTS model"""
        if not self.is_available():
            return False
            
        if self.tts_model is not None:
            return True
            
        print("🔊 Loading KittenTTS model...")
        start_time = time.time()
        self.loading = True
        
        try:
            self.tts_model = KittenTTS(self.model_name)
            load_time = time.time() - start_time
            print(f"🔊 Voice model loaded in {load_time:.2f} seconds")
            self.voice_enabled = True
            self.loading = False
            return True
            
        except Exception as e:
            print(f"❌ Failed to load voice model: {e}")
            self.loading = False
            return False
            
    def synthesize_and_play(self, text: str, background: bool = True) -> bool:
        """Synthesize text and play audio"""
        if not self.voice_enabled or not self.tts_model:
            return False
            
        if background:
            # Play in background thread
            thread = threading.Thread(target=self._synthesize_and_play_sync, args=(text,))
            thread.daemon = True
            thread.start()
            return True
        else:
            return self._synthesize_and_play_sync(text)
            
    def _synthesize_and_play_sync(self, text: str) -> bool:
        """Synchronous synthesis and playback"""
        try:
            # Generate audio
            audio_data = self.tts_model.generate(text)
            
            # Play audio using sounddevice
            if hasattr(audio_data, 'numpy'):
                # Convert to numpy if needed
                audio_array = audio_data.numpy()
            else:
                audio_array = audio_data
                
            # Ensure proper format for playback
            if len(audio_array.shape) == 1:
                # Mono audio
                sd.play(audio_array, samplerate=22050)
            else:
                # Multi-channel, take first channel
                sd.play(audio_array[:, 0], samplerate=22050)
                
            # Wait for playback to complete
            sd.wait()
            return True
            
        except Exception as e:
            print(f"❌ Voice synthesis error: {e}")
            return False
            
    def set_voice_enabled(self, enabled: bool):
        """Enable or disable voice output"""
        self.voice_enabled = enabled and self.tts_model is not None
        
    def get_status(self) -> dict:
        """Get voice engine status"""
        return {
            "available": self.is_available(),
            "loaded": self.tts_model is not None,
            "enabled": self.voice_enabled,
            "loading": self.loading,
            "model": self.model_name
        }

class MockVoiceEngine:
    """Mock voice engine for testing when KittenTTS is not available"""
    
    def __init__(self, *args, **kwargs):
        self.voice_enabled = False
        
    def is_available(self) -> bool:
        return False
        
    def load_model(self) -> bool:
        print("🔇 Voice synthesis not available (mock mode)")
        return False
        
    def synthesize_and_play(self, text: str, background: bool = True) -> bool:
        return False
        
    def set_voice_enabled(self, enabled: bool):
        pass
        
    def get_status(self) -> dict:
        return {
            "available": False,
            "loaded": False,
            "enabled": False,
            "loading": False,
            "model": "mock"
        }

def create_voice_engine() -> VoiceEngine:
    """Factory function to create appropriate voice engine"""
    if VOICE_AVAILABLE:
        return VoiceEngine()
    else:
        return MockVoiceEngine()

if __name__ == "__main__":
    # Test voice engine
    engine = create_voice_engine()
    
    if engine.is_available():
        if engine.load_model():
            test_text = "Hello! I'm M1K3, your local AI assistant. Voice synthesis is working!"
            print(f"Testing voice with: '{test_text}'")
            success = engine.synthesize_and_play(test_text, background=False)
            if success:
                print("✅ Voice synthesis test successful!")
            else:
                print("❌ Voice synthesis test failed")
        else:
            print("❌ Failed to load voice model")
    else:
        print("❌ Voice synthesis not available")