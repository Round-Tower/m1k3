#!/usr/bin/env python3
"""
M1K3 Voice Engine
Handles text-to-speech using KittenML TTS and audio playback
"""

import io
import time
import threading
import numpy as np
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
        self.current_profile = "natural"
        
        # Voice profiles with different characteristics
        self.profiles = {
            "natural": {
                "description": "Default conversational voice",
                "speed_multiplier": 1.0,
                "pitch_adjustment": 0.0,
                "volume_multiplier": 1.0,
                "pause_duration": 0.1
            },
            "assistant": {
                "description": "Professional AI assistant tone", 
                "speed_multiplier": 0.95,
                "pitch_adjustment": 0.05,
                "volume_multiplier": 0.9,
                "pause_duration": 0.15
            },
            "broadcast": {
                "description": "Clear, announcer-style voice",
                "speed_multiplier": 0.85,
                "pitch_adjustment": 0.1,
                "volume_multiplier": 1.1,
                "pause_duration": 0.2
            },
            "terminal": {
                "description": "Technical, system-style voice",
                "speed_multiplier": 1.1,
                "pitch_adjustment": -0.05,
                "volume_multiplier": 0.85,
                "pause_duration": 0.05
            },
            "debug": {
                "description": "Fast, minimal voice for debugging",
                "speed_multiplier": 1.3,
                "pitch_adjustment": -0.1,
                "volume_multiplier": 0.8,
                "pause_duration": 0.02
            },
            "minimal": {
                "description": "Basic synthesis with minimal processing",
                "speed_multiplier": 1.0,
                "pitch_adjustment": 0.0,
                "volume_multiplier": 0.9,
                "pause_duration": 0.05
            }
        }
        
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
    
    def _apply_profile_effects(self, audio_data, sample_rate: int = 22050):
        """Apply current profile effects to audio data"""
        try:
            # Convert to numpy array
            if hasattr(audio_data, 'numpy'):
                audio = audio_data.numpy()
            else:
                audio = np.array(audio_data)
                
            # Flatten if multi-channel
            if len(audio.shape) > 1:
                audio = audio[:, 0] if audio.shape[1] > 0 else audio.flatten()
            
            # Get current profile settings
            profile = self.get_current_profile()
            
            # Apply speed modification (resampling)
            speed_mult = profile.get("speed_multiplier", 1.0)
            if speed_mult != 1.0:
                new_length = int(len(audio) / speed_mult)
                if new_length > 0:
                    indices = np.linspace(0, len(audio) - 1, new_length)
                    audio = np.interp(indices, np.arange(len(audio)), audio)
            
            # Apply pitch adjustment (simple frequency domain)
            pitch_adj = profile.get("pitch_adjustment", 0.0)
            if pitch_adj != 0.0:
                # Simple pitch shift by slight amplitude scaling
                audio = audio * (1 + pitch_adj * 0.2)
            
            # Apply volume control
            volume_mult = profile.get("volume_multiplier", 1.0)
            audio = audio * volume_mult
            
            # Apply profile-specific character
            if self.current_profile == "assistant":
                # Professional clarity: slight compression
                audio = self._apply_soft_compression(audio)
            elif self.current_profile == "broadcast":
                # Announcer style: enhanced clarity and presence
                audio = self._apply_clarity_boost(audio)
            elif self.current_profile == "terminal":
                # Technical/digital feel: light quantization
                audio = self._apply_digital_character(audio)
            elif self.current_profile == "debug":
                # Minimal processing: light compression only
                audio = self._apply_minimal_processing(audio)
            
            # Normalize to prevent clipping
            max_val = np.max(np.abs(audio))
            if max_val > 0:
                audio = audio / max_val * 0.85
                
            return audio.astype(np.float32)
            
        except Exception as e:
            print(f"⚠️ Voice profile effects error: {e}")
            # Return original audio if effects fail
            if hasattr(audio_data, 'numpy'):
                return audio_data.numpy().astype(np.float32)
            return np.array(audio_data, dtype=np.float32)
    
    def _apply_soft_compression(self, audio):
        """Apply soft compression for professional sound"""
        threshold = 0.6
        ratio = 0.4
        mask = np.abs(audio) > threshold
        compressed = audio.copy()
        over_threshold = np.abs(audio[mask]) - threshold
        compressed[mask] = np.sign(audio[mask]) * (threshold + over_threshold * ratio)
        return compressed
    
    def _apply_clarity_boost(self, audio):
        """Apply clarity enhancement for broadcast sound"""
        # Simple high-frequency emphasis for clarity
        # This is a basic implementation - more advanced would use FFT
        enhanced = audio.copy()
        if len(enhanced) > 1:
            # Simple high-pass like effect by emphasizing changes
            diff = np.diff(enhanced)
            enhanced[1:] += diff * 0.15
        return enhanced
    
    def _apply_digital_character(self, audio):
        """Apply digital/technical character"""
        # Light quantization for digital feel
        quantized = np.round(audio * 16) / 16
        return quantized * 0.95  # Slightly reduce volume for technical feel
    
    def _apply_minimal_processing(self, audio):
        """Apply minimal processing for debug profile"""
        # Just very light compression
        return self._apply_soft_compression(audio) * 0.9
            
    def _synthesize_and_play_sync(self, text: str) -> bool:
        """Synchronous synthesis and playback with profile effects"""
        try:
            # Generate base audio
            audio_data = self.tts_model.generate(text)
            
            # Apply profile-specific effects
            processed_audio = self._apply_profile_effects(audio_data)
            
            # Play the processed audio
            sample_rate = 22050
            sd.play(processed_audio, samplerate=sample_rate)
            
            # Wait for playback to complete
            sd.wait()
            return True
            
        except Exception as e:
            print(f"❌ Voice synthesis error: {e}")
            return False
            
    def set_voice_enabled(self, enabled: bool):
        """Enable or disable voice output"""
        self.voice_enabled = enabled and self.tts_model is not None
        
    def set_profile(self, profile_name: str) -> bool:
        """Set the voice profile for synthesis"""
        if profile_name in self.profiles:
            self.current_profile = profile_name
            return True
        return False
    
    def get_current_profile(self) -> dict:
        """Get current voice profile settings"""
        return self.profiles.get(self.current_profile, self.profiles["natural"])
        
    def get_status(self) -> dict:
        """Get voice engine status"""
        return {
            "available": self.is_available(),
            "loaded": self.tts_model is not None,
            "enabled": self.voice_enabled,
            "loading": self.loading,
            "model": self.model_name,
            "current_profile": self.current_profile,
            "available_profiles": list(self.profiles.keys())
        }

class MockVoiceEngine:
    """Mock voice engine for testing when KittenTTS is not available"""
    
    def __init__(self, *args, **kwargs):
        self.voice_enabled = False
        self.current_profile = "natural"
        
        # Same profiles as real engine for consistency
        self.profiles = {
            "natural": {"description": "Default conversational voice"},
            "assistant": {"description": "Professional AI assistant tone"}, 
            "broadcast": {"description": "Clear, announcer-style voice"},
            "terminal": {"description": "Technical, system-style voice"},
            "debug": {"description": "Fast, minimal voice for debugging"},
            "minimal": {"description": "Basic synthesis with minimal processing"}
        }
        
    def is_available(self) -> bool:
        return False
        
    def load_model(self) -> bool:
        print("🔇 Voice synthesis not available (mock mode)")
        return False
        
    def synthesize_and_play(self, text: str, background: bool = True) -> bool:
        return False
        
    def set_voice_enabled(self, enabled: bool):
        pass
    
    def set_profile(self, profile_name: str) -> bool:
        """Set the voice profile (mock implementation)"""
        if profile_name in self.profiles:
            self.current_profile = profile_name
            return True
        return False
    
    def get_current_profile(self) -> dict:
        """Get current voice profile settings (mock implementation)"""
        return self.profiles.get(self.current_profile, self.profiles["natural"])
        
    def get_status(self) -> dict:
        return {
            "available": False,
            "loaded": False,
            "enabled": False,
            "loading": False,
            "model": "mock",
            "current_profile": self.current_profile,
            "available_profiles": list(self.profiles.keys())
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