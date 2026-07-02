#!/usr/bin/env python3
"""
Coqui TTS Manager for M1K3
High-quality neural TTS engine using Coqui TTS library
"""

import time
import numpy as np
import warnings
from typing import Optional, List
import tempfile
import os

# Suppress warnings for cleaner output
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)

try:
    from TTS.api import TTS
    COQUI_AVAILABLE = True
except ImportError:
    COQUI_AVAILABLE = False

try:
    import sounddevice as sd
    SOUNDDEVICE_AVAILABLE = True
except ImportError:
    SOUNDDEVICE_AVAILABLE = False

class CoquiTTSManager:
    """High-quality TTS using Coqui TTS"""
    
    _instance = None
    
    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super(CoquiTTSManager, cls).__new__(cls)
        return cls._instance

    def __init__(self, model_name: str = None):
        if not hasattr(self, 'initialized'):
            # Use fast, good quality model by default
            self.model_name = model_name or "tts_models/en/ljspeech/tacotron2-DDC"
            self.tts_model: Optional[TTS] = None
            self.loading = False
            self.sample_rate = 22050
            self.initialized = True

    def is_available(self) -> bool:
        """Check if Coqui TTS is available."""
        return COQUI_AVAILABLE and SOUNDDEVICE_AVAILABLE

    def get_available_models(self) -> List[str]:
        """Get list of available Coqui TTS models."""
        if not COQUI_AVAILABLE:
            return []
        
        try:
            # Fast, good quality models
            return [
                "tts_models/en/ljspeech/tacotron2-DDC",  # Fast, good quality
                "tts_models/en/ljspeech/glow-tts",       # Very fast
                "tts_models/en/ljspeech/speedy-speech",  # Fastest
                "tts_models/en/ek1/tacotron2",           # Alternative
                "tts_models/en/jenny/jenny"              # High quality (slower)
            ]
        except:
            return ["tts_models/en/ljspeech/tacotron2-DDC"]

    def load_model(self) -> bool:
        """Load the Coqui TTS model."""
        if not self.is_available():
            return False
            
        if self.tts_model is not None:
            return True

        print("🎤 Loading Coqui TTS Engine...")
        self.loading = True
        start_time = time.time()
        
        try:
            # Initialize with specified model
            self.tts_model = TTS(self.model_name)
            
            # Get sample rate from model
            if hasattr(self.tts_model.synthesizer, 'output_sample_rate'):
                self.sample_rate = self.tts_model.synthesizer.output_sample_rate
            elif hasattr(self.tts_model, 'sample_rate'):
                self.sample_rate = self.tts_model.sample_rate
            
            load_time = time.time() - start_time
            print(f"✅ Coqui TTS loaded in {load_time:.2f} seconds.")
            print(f"📊 Model: {self.model_name}")
            print(f"🔊 Sample rate: {self.sample_rate} Hz")
            
            self.loading = False
            return True
            
        except Exception as e:
            print(f"❌ Failed to load Coqui TTS model: {e}")
            print("💡 Try installing: pip install TTS")
            self.tts_model = None
            self.loading = False
            return False

    def generate(self, text: str) -> Optional[np.ndarray]:
        """Generate high-quality audio from text."""
        if not self.tts_model:
            print("⚠️  Coqui TTS model not loaded. Cannot generate audio.")
            return None
        
        try:
            # Generate audio to temporary file
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp_file:
                temp_path = tmp_file.name
            
            # Synthesize to file
            self.tts_model.tts_to_file(text=text, file_path=temp_path)
            
            # Load the audio file
            import soundfile as sf
            audio_data, sample_rate = sf.read(temp_path)
            
            # Clean up temp file
            os.unlink(temp_path)
            
            # Convert to float32 and ensure correct sample rate
            audio_array = np.array(audio_data, dtype=np.float32)
            
            # Ensure mono audio
            if len(audio_array.shape) > 1:
                audio_array = audio_array[:, 0] if audio_array.shape[1] > 0 else audio_array.flatten()
            
            return audio_array
            
        except Exception as e:
            print(f"🔇 Coqui TTS synthesis error: {e}")
            return None

    def play_audio(self, audio: np.ndarray) -> bool:
        """Play audio using sounddevice."""
        if not SOUNDDEVICE_AVAILABLE:
            print("⚠️  sounddevice not available for playback")
            return False
            
        try:
            sd.play(audio, samplerate=self.sample_rate)
            sd.wait()  # Wait for playback to complete
            return True
        except Exception as e:
            print(f"🔇 Audio playback error: {e}")
            return False

    def synthesize_and_play(self, text: str, background: bool = True) -> bool:
        """Generate and play audio."""
        audio = self.generate(text)
        if audio is not None:
            if background:
                import threading
                thread = threading.Thread(target=self.play_audio, args=(audio,))
                thread.daemon = True
                thread.start()
                return True
            else:
                return self.play_audio(audio)
        return False

    def get_status(self) -> dict:
        """Get the status of the Coqui TTS manager."""
        return {
            "available": self.is_available(),
            "loaded": self.tts_model is not None,
            "loading": self.loading,
            "model_name": self.model_name,
            "sample_rate": self.sample_rate,
            "quality": "high"
        }

# Singleton instance
CoquiManager = CoquiTTSManager()

if __name__ == "__main__":
    print("Testing Coqui TTS Manager...")
    
    if CoquiManager.is_available():
        print(f"✅ Coqui TTS available")
        print(f"📋 Available models: {CoquiManager.get_available_models()}")
        
        if CoquiManager.load_model():
            print("✅ Model loaded successfully")
            
            # Test generation
            test_text = "This is a test of the high-quality Coqui TTS engine for M1K3."
            print(f"\n🎤 Testing synthesis...")
            print(f"Text: \"{test_text}\"")
            
            audio = CoquiManager.generate(test_text)
            if audio is not None:
                print(f"✅ Audio generated! Shape: {audio.shape}, Dtype: {audio.dtype}")
                
                # Test playback
                print("🔊 Playing audio...")
                success = CoquiManager.play_audio(audio)
                if success:
                    print("✅ Playback successful!")
                else:
                    print("❌ Playback failed")
            else:
                print("❌ Audio generation failed")
        else:
            print("❌ Failed to load model")
    else:
        print("❌ Coqui TTS not available")
        print("💡 Install with: pip install TTS soundfile")