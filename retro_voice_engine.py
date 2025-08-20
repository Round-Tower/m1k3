#!/usr/bin/env python3
"""
Retro Voice Engine for M1K3
KittenML TTS with PlayStation 1 style voice customization
"""

import io
import time
import threading
import numpy as np
from pathlib import Path
from typing import Optional, Dict, Any
import warnings

# Suppress warnings for cleaner output
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)

try:
    import sounddevice as sd
    import soundfile as sf
    from kittentts import KittenTTS
    VOICE_AVAILABLE = True
except ImportError as e:
    print(f"KittenML TTS not available: {e}")
    VOICE_AVAILABLE = False

class RetroVoiceEngine:
    """PlayStation 1 style voice synthesis using KittenML TTS"""
    
    def __init__(self, model_name: str = "KittenML/kitten-tts-nano-0.1"):
        self.model_name = model_name
        self.tts_model: Optional[KittenTTS] = None
        self.voice_enabled = False
        self.loading = False
        
        # PlayStation 1 style voice parameters
        self.voice_config = {
            "speed": 1.1,          # Slightly faster for retro feel
            "pitch_shift": -0.15,   # Lower pitch for male voice
            "bitrate_reduction": True,  # Simulate lower quality audio
            "reverb": 0.2,         # Small reverb for depth
            "compression": True,    # Audio compression for that digital feel
        }
        
    def is_available(self) -> bool:
        """Check if KittenML TTS is available"""
        return VOICE_AVAILABLE
        
    def load_model(self) -> bool:
        """Load the KittenTTS model with retro configuration"""
        if not self.is_available():
            return False
            
        if self.tts_model is not None:
            return True
            
        print("🎮 Loading KittenML TTS for retro gaming voice...")
        start_time = time.time()
        self.loading = True
        
        try:
            # Initialize KittenTTS
            self.tts_model = KittenTTS(self.model_name)
            
            load_time = time.time() - start_time
            print(f"🎮 Retro voice model loaded in {load_time:.2f} seconds")
            print("🔊 M1K3 voice configured for PlayStation 1 style")
            
            self.voice_enabled = True
            self.loading = False
            return True
            
        except Exception as e:
            print(f"❌ Failed to load retro voice model: {e}")
            self.loading = False
            return False
            
    def _apply_retro_effects(self, audio_data: np.ndarray, sample_rate: int = 22050) -> np.ndarray:
        """Apply PlayStation 1 style audio effects"""
        try:
            # Ensure audio is numpy array
            if hasattr(audio_data, 'numpy'):
                audio = audio_data.numpy()
            else:
                audio = np.array(audio_data)
                
            # Flatten if multi-channel
            if len(audio.shape) > 1:
                audio = audio[:, 0] if audio.shape[1] > 0 else audio.flatten()
                
            # Apply speed adjustment (slightly faster)
            if self.voice_config["speed"] != 1.0:
                # Simple speed change by resampling
                speed_factor = self.voice_config["speed"]
                new_length = int(len(audio) / speed_factor)
                indices = np.linspace(0, len(audio) - 1, new_length)
                audio = np.interp(indices, np.arange(len(audio)), audio)
                
            # Apply pitch shift (lower for male voice)
            # This is a simple implementation - real pitch shifting is more complex
            if self.voice_config["pitch_shift"] != 0:
                # Simulate lower pitch by slight frequency adjustment
                audio = audio * (1 + self.voice_config["pitch_shift"] * 0.1)
                
            # Apply bitrate reduction for retro feel
            if self.voice_config["bitrate_reduction"]:
                # Quantize to simulate lower bit depth
                quantized = np.round(audio * 32) / 32
                audio = quantized
                
            # Apply simple reverb
            if self.voice_config["reverb"] > 0:
                # Simple delay-based reverb
                reverb_strength = self.voice_config["reverb"]
                delay_samples = int(0.05 * sample_rate)  # 50ms delay
                
                if len(audio) > delay_samples:
                    reverb = np.zeros_like(audio)
                    reverb[delay_samples:] = audio[:-delay_samples] * reverb_strength
                    audio = audio + reverb
                    
            # Apply compression for that digital feel
            if self.voice_config["compression"]:
                # Simple compression (reduce dynamic range)
                threshold = 0.5
                ratio = 0.3
                
                # Soft compression
                mask = np.abs(audio) > threshold
                compressed = audio.copy()
                compressed[mask] = np.sign(audio[mask]) * (
                    threshold + (np.abs(audio[mask]) - threshold) * ratio
                )
                audio = compressed
                
            # Normalize to prevent clipping
            max_val = np.max(np.abs(audio))
            if max_val > 0:
                audio = audio / max_val * 0.8
                
            return audio.astype(np.float32)
            
        except Exception as e:
            print(f"🎮 Error applying retro effects: {e}")
            # Return original audio if effects fail
            if hasattr(audio_data, 'numpy'):
                return audio_data.numpy().astype(np.float32)
            return np.array(audio_data, dtype=np.float32)
            
    def synthesize_and_play(self, text: str, background: bool = True) -> bool:
        """Synthesize text with retro effects and play audio"""
        if not self.voice_enabled or not self.tts_model:
            return False
            
        # Add some gaming flavor to the text
        enhanced_text = self._enhance_text_for_gaming(text)
            
        if background:
            # Play in background thread
            thread = threading.Thread(target=self._synthesize_and_play_sync, args=(enhanced_text,))
            thread.daemon = True
            thread.start()
            return True
        else:
            return self._synthesize_and_play_sync(enhanced_text)
            
    def _enhance_text_for_gaming(self, text: str) -> str:
        """Add subtle gaming-style enhancements to text"""
        # Convert some phrases for gaming feel
        replacements = {
            "Error": "System error",
            "Failed": "Operation failed", 
            "Ready": "System ready",
            "Loading": "Initializing",
            "Hello": "Greetings, user",
        }
        
        enhanced = text
        for old, new in replacements.items():
            if old in enhanced and not enhanced.startswith("System"):
                enhanced = enhanced.replace(old, new)
                
        return enhanced
            
    def _synthesize_and_play_sync(self, text: str) -> bool:
        """Synchronous synthesis and playback with retro effects"""
        try:
            # Generate base audio
            audio_data = self.tts_model.generate(text)
            
            # Apply PlayStation 1 style effects
            retro_audio = self._apply_retro_effects(audio_data)
            
            # Play with retro audio settings
            sample_rate = 22050  # Standard rate for retro feel
            
            # Play audio using sounddevice
            sd.play(retro_audio, samplerate=sample_rate)
            
            # Wait for playback to complete
            sd.wait()
            return True
            
        except Exception as e:
            print(f"🎮 Retro voice synthesis error: {e}")
            return False
            
    def set_voice_config(self, config: Dict[str, Any]):
        """Update voice configuration parameters"""
        self.voice_config.update(config)
        print(f"🎮 Voice config updated: {config}")
        
    def get_voice_presets(self) -> Dict[str, Dict[str, Any]]:
        """Get predefined voice presets"""
        return {
            "ps1_hero": {
                "speed": 1.1,
                "pitch_shift": -0.15,
                "bitrate_reduction": True,
                "reverb": 0.2,
                "compression": True,
            },
            "ps1_villain": {
                "speed": 0.9,
                "pitch_shift": -0.3,
                "bitrate_reduction": True,
                "reverb": 0.4,
                "compression": True,
            },
            "ps1_narrator": {
                "speed": 1.0,
                "pitch_shift": -0.1,
                "bitrate_reduction": True,
                "reverb": 0.1,
                "compression": True,
            },
            "classic": {
                "speed": 1.0,
                "pitch_shift": 0,
                "bitrate_reduction": False,
                "reverb": 0,
                "compression": False,
            }
        }
        
    def set_voice_preset(self, preset_name: str) -> bool:
        """Apply a voice preset"""
        presets = self.get_voice_presets()
        if preset_name in presets:
            self.voice_config = presets[preset_name].copy()
            print(f"🎮 Applied voice preset: {preset_name}")
            return True
        else:
            print(f"❌ Unknown preset: {preset_name}")
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
            "model": self.model_name,
            "preset": "ps1_hero",
            "config": self.voice_config.copy()
        }

class MockRetroVoiceEngine:
    """Mock retro voice engine for testing when KittenTTS is not available"""
    
    def __init__(self, *args, **kwargs):
        self.voice_enabled = False
        self.voice_config = {}
        
    def is_available(self) -> bool:
        return False
        
    def load_model(self) -> bool:
        print("🎮 Retro voice synthesis not available (mock mode)")
        return False
        
    def synthesize_and_play(self, text: str, background: bool = True) -> bool:
        return False
        
    def set_voice_config(self, config: Dict[str, Any]):
        pass
        
    def set_voice_preset(self, preset_name: str) -> bool:
        return False
        
    def get_voice_presets(self) -> Dict[str, Dict[str, Any]]:
        return {}
        
    def set_voice_enabled(self, enabled: bool):
        pass
        
    def get_status(self) -> dict:
        return {
            "available": False,
            "loaded": False,
            "enabled": False,
            "loading": False,
            "model": "mock",
            "preset": "none",
            "config": {}
        }

def create_voice_engine() -> RetroVoiceEngine:
    """Factory function to create appropriate retro voice engine"""
    if VOICE_AVAILABLE:
        return RetroVoiceEngine()
    else:
        return MockRetroVoiceEngine()

if __name__ == "__main__":
    # Test retro voice engine
    engine = create_voice_engine()
    
    if engine.is_available():
        if engine.load_model():
            print("\n🎮 Testing PlayStation 1 style voice presets:")
            
            presets = engine.get_voice_presets()
            test_text = "Greetings, user. M1K3 retro voice system online."
            
            for preset_name in ["ps1_hero", "ps1_narrator", "ps1_villain"]:
                print(f"\n🎵 Testing {preset_name} preset...")
                engine.set_voice_preset(preset_name)
                engine.synthesize_and_play(test_text, background=False)
                time.sleep(1)
                
            print("\n✅ Retro voice testing complete!")
        else:
            print("❌ Failed to load retro voice model")
    else:
        print("❌ KittenML TTS not available")