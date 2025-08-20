#!/usr/bin/env python3
"""
Zen Voice Engine for M1K3
Refined KittenML TTS with "Retro Zen Oracle" voice profile
Crystal-clear quality with subtle digital warmth and anime mentor characteristics
"""

import io
import json
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

class ZenVoiceEngine:
    """Retro Zen Oracle voice synthesis with anime mentor characteristics"""
    
    def __init__(self, model_name: str = "KittenML/kitten-tts-nano-0.1", config_path: str = "voice_config.json"):
        self.model_name = model_name
        self.tts_model: Optional[KittenTTS] = None
        self.voice_enabled = False
        self.loading = False
        self.config = self._load_config(config_path)
        
        # Load personas from config or use defaults
        self.personas = self.config.get("personas", self._default_personas())
        self.current_persona = self.config.get("default_persona", "assistant")
        
    def _load_config(self, config_path: str) -> Dict[str, Any]:
        """Load voice configuration from JSON file"""
        try:
            config_file = Path(config_path)
            if config_file.exists():
                with open(config_file, 'r') as f:
                    return json.load(f)
            else:
                print(f"⚠️  Config file {config_path} not found, using defaults")
                return {}
        except Exception as e:
            print(f"⚠️  Error loading config: {e}, using defaults")
            return {}
            
    def _default_personas(self) -> Dict[str, Dict[str, Any]]:
        """Default persona configurations"""
        return {
            "natural": {
                "name": "Natural",
                "description": "Pure, unprocessed voice with maximum clarity",
                "voice": {
                    "intercom_filter": False,
                    "compression": False,
                    "gate": False,
                },
            },
            "assistant": {
                "name": "Assistant",
                "description": "Clean, clear voice with light processing (default)",
                "voice": {
                    "intercom_filter": False,
                    "compression": True,
                    "gate": False,
                },
            },
            "pa_system": {
                "name": "PA System",
                "description": "Public address system voice with gentle filtering",
                "voice": {
                    "intercom_filter": True,
                    "compression": True,
                    "gate": False,
                },
            },
            "broadcast": {
                "name": "Broadcast",
                "description": "Radio-quality voice with professional sound",
                "voice": {
                    "intercom_filter": True,
                    "compression": True,
                    "gate": False,
                },
            },
            "terminal": {
                "name": "Terminal",
                "description": "Retro computer terminal voice",
                "voice": {
                    "intercom_filter": True,
                    "compression": False,
                    "gate": False,
                },
            }
        }
        
    def is_available(self) -> bool:
        """Check if KittenML TTS is available"""
        return VOICE_AVAILABLE
        
    def load_model(self) -> bool:
        """Load the KittenTTS model with zen configuration"""
        if not self.is_available():
            return False
            
        if self.tts_model is not None:
            return True
            
        print("🧘 Loading KittenML TTS for Retro Zen Oracle voice...")
        start_time = time.time()
        self.loading = True
        
        try:
            # Initialize KittenTTS
            self.tts_model = KittenTTS(self.model_name)
            
            load_time = time.time() - start_time
            print(f"🧘 Zen voice model loaded in {load_time:.2f} seconds")
            print("✨ Retro Zen Oracle voice configured - crystal clear with digital warmth")
            
            self.voice_enabled = True
            self.loading = False
            return True
            
        except Exception as e:
            print(f"❌ Failed to load zen voice model: {e}")
            self.loading = False
            return False
            
    def _apply_intercom_effects(self, audio_data: np.ndarray, sample_rate: int = 22050) -> np.ndarray:
        """Apply intercom-style audio processing for broadcast quality"""
        try:
            # Ensure audio is numpy array
            if hasattr(audio_data, 'numpy'):
                audio = audio_data.numpy()
            else:
                audio = np.array(audio_data)
                
            # Flatten if multi-channel
            if len(audio.shape) > 1:
                audio = audio[:, 0] if audio.shape[1] > 0 else audio.flatten()
                
            persona_voice = self.personas[self.current_persona]["voice"]
            
            # Apply intercom band-pass filter (configurable frequencies)
            if persona_voice.get("intercom_filter", True):
                filter_settings = self.config.get("filter_settings", {}).get("bandpass", {})
                low_freq = filter_settings.get("low_freq", 300)
                high_freq = filter_settings.get("high_freq", 3400)
                audio = self._apply_bandpass_filter(audio, sample_rate, low_freq, high_freq)
                
            # Apply broadcast-quality compression (configurable)
            if persona_voice.get("compression", True):
                comp_settings = self.config.get("compression_settings", {})
                threshold = comp_settings.get("threshold", 0.6)
                ratio = comp_settings.get("ratio", 0.3)
                audio = self._apply_simple_compression(audio, threshold=threshold, ratio=ratio)
                
            # Apply subtle gate effect for that "click on/off" intercom feel
            if persona_voice.get("gate", False):
                gate_settings = self.config.get("gate_settings", {})
                threshold = gate_settings.get("threshold", 0.02)
                audio = self._apply_simple_gate(audio, threshold=threshold)
                
            # High-quality normalization for consistent volume
            max_val = np.max(np.abs(audio))
            if max_val > 0:
                # Professional normalization (configurable level)
                norm_level = self.config.get("audio_settings", {}).get("normalization_level", 0.7)
                audio = audio / max_val * norm_level
                
            return audio.astype(np.float32)
            
        except Exception as e:
            print(f"🔊 Error applying intercom effects: {e}")
            # Return original audio if effects fail
            if hasattr(audio_data, 'numpy'):
                return audio_data.numpy().astype(np.float32)
            return np.array(audio_data, dtype=np.float32)
            
    def _apply_bandpass_filter(self, audio: np.ndarray, sample_rate: int, low_freq: float, high_freq: float) -> np.ndarray:
        """Apply simple bandpass filter for intercom character"""
        try:
            from scipy import signal
            # Design a Butterworth bandpass filter
            nyquist = sample_rate / 2
            low = low_freq / nyquist
            high = high_freq / nyquist
            b, a = signal.butter(4, [low, high], btype='band')
            return signal.filtfilt(b, a, audio)
        except ImportError:
            # Fallback: simple frequency domain filtering
            fft = np.fft.fft(audio)
            freqs = np.fft.fftfreq(len(audio), 1/sample_rate)
            
            # Create mask for bandpass
            mask = (np.abs(freqs) >= low_freq) & (np.abs(freqs) <= high_freq)
            fft[~mask] *= 0.1  # Attenuate outside band
            
            return np.real(np.fft.ifft(fft))
            
    def _apply_simple_compression(self, audio: np.ndarray, threshold: float = 0.6, ratio: float = 0.3) -> np.ndarray:
        """Apply simple audio compression for broadcast quality"""
        # Soft knee compression
        mask = np.abs(audio) > threshold
        compressed = audio.copy()
        
        # Apply compression to peaks
        over_threshold = np.abs(audio[mask]) - threshold
        compressed[mask] = np.sign(audio[mask]) * (threshold + over_threshold * ratio)
        
        return compressed
        
    def _apply_simple_gate(self, audio: np.ndarray, threshold: float = 0.02) -> np.ndarray:
        """Apply simple noise gate for clean on/off"""
        # Fade in/out at start and end for gate effect
        fade_samples = int(len(audio) * 0.01)  # 1% fade
        
        # Start fade in
        audio[:fade_samples] *= np.linspace(0, 1, fade_samples)
        
        # End fade out
        audio[-fade_samples:] *= np.linspace(1, 0, fade_samples)
        
        return audio
        
    def _apply_speed_factor(self, audio: np.ndarray, speed_factor: float) -> np.ndarray:
        """Apply speed factor using time-stretching technique"""
        if speed_factor == 1.0:
            return audio
            
        try:
            # Simple time-stretching by resampling
            original_length = len(audio)
            new_length = int(original_length / speed_factor)
            
            # Use linear interpolation for speed adjustment
            indices = np.linspace(0, original_length - 1, new_length)
            stretched_audio = np.interp(indices, np.arange(original_length), audio)
            
            return stretched_audio.astype(np.float32)
        except Exception as e:
            print(f"🔊 Speed factor error: {e}")
            return audio
            
    def synthesize_and_play(self, text: str, background: bool = True) -> bool:
        """Synthesize text with zen characteristics and natural pacing"""
        if not self.voice_enabled or not self.tts_model:
            return False
            
        # Enhance text with persona characteristics
        enhanced_text = self._enhance_text_for_persona(text)
            
        if background:
            # Play in background thread
            thread = threading.Thread(target=self._synthesize_and_play_sync, args=(enhanced_text,))
            thread.daemon = True
            thread.start()
            return True
        else:
            return self._synthesize_and_play_sync(enhanced_text)
            
    def _enhance_text_for_persona(self, text: str) -> str:
        """Add persona-specific enhancements to text"""
        # Apply persona-based text modifications
        enhanced = text
        
        # Apply persona-specific text enhancements (minimal for quality)
        if self.current_persona == "natural":
            enhanced = self._apply_natural_style(enhanced)
        elif self.current_persona == "assistant":
            enhanced = self._apply_assistant_style(enhanced)
        elif self.current_persona == "pa_system":
            enhanced = self._apply_pa_system_style(enhanced)
        elif self.current_persona == "broadcast":
            enhanced = self._apply_broadcast_style(enhanced)
        elif self.current_persona == "terminal":
            enhanced = self._apply_terminal_style(enhanced)
            
        return enhanced
        
    def _apply_persona_style(self, text: str, persona_name: str) -> str:
        """Apply persona-specific text replacements from config"""
        persona_config = self.personas.get(persona_name, {})
        replacements = persona_config.get("text_replacements", {})
        
        enhanced = text
        for old, new in replacements.items():
            if old in enhanced:
                enhanced = enhanced.replace(old, new)
        return enhanced
        
    def _apply_natural_style(self, text: str) -> str:
        """Apply natural voice style - no modifications"""
        return self._apply_persona_style(text, "natural")
        
    def _apply_assistant_style(self, text: str) -> str:
        """Apply neutral assistant style"""
        return self._apply_persona_style(text, "assistant")
        
    def _apply_pa_system_style(self, text: str) -> str:
        """Apply PA system style"""
        return self._apply_persona_style(text, "pa_system")
        
    def _apply_broadcast_style(self, text: str) -> str:
        """Apply radio broadcast style"""
        return self._apply_persona_style(text, "broadcast")
        
    def _apply_terminal_style(self, text: str) -> str:
        """Apply retro computer terminal style"""
        return self._apply_persona_style(text, "terminal")
            
    def _synthesize_and_play_sync(self, text: str) -> bool:
        """Synchronous synthesis with intercom-quality audio processing"""
        try:
            # Generate base audio
            audio_data = self.tts_model.generate(text)
            
            # Apply intercom effects for broadcast quality
            intercom_audio = self._apply_intercom_effects(audio_data)
            
            # Apply speed factor for faster playback
            speed_factor = self.config.get("audio_settings", {}).get("speed_factor", 1.0)
            if speed_factor != 1.0:
                intercom_audio = self._apply_speed_factor(intercom_audio, speed_factor)
            
            # Play with optimized characteristics
            sample_rate = self.config.get("audio_settings", {}).get("sample_rate", 22050)
            
            # Play audio using sounddevice
            sd.play(intercom_audio, samplerate=sample_rate)
            
            # Wait for playback to complete
            sd.wait()
            return True
            
        except Exception as e:
            print(f"🔊 Intercom voice synthesis error: {e}")
            return False
            
    def set_persona(self, persona_name: str) -> bool:
        """Switch to a different persona"""
        if persona_name in self.personas:
            self.current_persona = persona_name
            persona = self.personas[persona_name]
            print(f"🧘 Persona changed to: {persona['name']}")
            print(f"✨ {persona['description']}")
            return True
        else:
            print(f"❌ Unknown persona: {persona_name}")
            return False
            
    def get_personas(self) -> Dict[str, Dict[str, str]]:
        """Get available personas"""
        return {
            name: {
                "name": info["name"],
                "description": info["description"]
            }
            for name, info in self.personas.items()
        }
        
    def get_current_persona(self) -> Dict[str, Any]:
        """Get current persona information"""
        return self.personas[self.current_persona]
        
    def set_voice_enabled(self, enabled: bool):
        """Enable or disable voice output"""
        self.voice_enabled = enabled and self.tts_model is not None
        
    def get_status(self) -> dict:
        """Get voice engine status"""
        current_persona = self.personas[self.current_persona]
        return {
            "available": self.is_available(),
            "loaded": self.tts_model is not None,
            "enabled": self.voice_enabled,
            "loading": self.loading,
            "model": self.model_name,
            "persona": self.current_persona,
            "persona_name": current_persona["name"],
            "voice_config": current_persona["voice"].copy()
        }

class MockZenVoiceEngine:
    """Mock zen voice engine for testing when KittenTTS is not available"""
    
    def __init__(self, *args, **kwargs):
        self.voice_enabled = False
        self.current_persona = "zen_oracle"
        self.personas = {}
        
    def is_available(self) -> bool:
        return False
        
    def load_model(self) -> bool:
        print("🧘 Zen voice synthesis not available (mock mode)")
        return False
        
    def synthesize_and_play(self, text: str, background: bool = True) -> bool:
        return False
        
    def set_persona(self, persona_name: str) -> bool:
        return False
        
    def get_personas(self) -> Dict[str, Dict[str, str]]:
        return {}
        
    def get_current_persona(self) -> Dict[str, Any]:
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
            "persona": "none",
            "persona_name": "Mock",
            "voice_config": {}
        }

def create_voice_engine() -> ZenVoiceEngine:
    """Factory function to create appropriate zen voice engine"""
    if VOICE_AVAILABLE:
        return ZenVoiceEngine()
    else:
        return MockZenVoiceEngine()

if __name__ == "__main__":
    # Test zen voice engine
    engine = create_voice_engine()
    
    if engine.is_available():
        if engine.load_model():
            print("\n🧘 Testing Retro Zen Oracle personas:")
            
            personas = engine.get_personas()
            test_text = "Greetings, fellow traveler. The digital realm holds many mysteries."
            
            for persona_name, persona_info in personas.items():
                print(f"\n✨ Testing {persona_info['name']}:")
                print(f"   {persona_info['description']}")
                
                engine.set_persona(persona_name)
                engine.synthesize_and_play(test_text, background=False)
                time.sleep(1.5)
                
            print("\n✅ Zen voice testing complete!")
        else:
            print("❌ Failed to load zen voice model")
    else:
        print("❌ KittenML TTS not available")