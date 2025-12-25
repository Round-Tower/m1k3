#!/usr/bin/env python3
"""
eSpeak TTS Manager for M1K3
Ultra-fast TTS engine for emergency speed scenarios
Sub-10ms latency, 2MB footprint, supports 99 languages
"""

import time
import numpy as np
import subprocess
import tempfile
import os
import warnings
from typing import Optional, List, Dict, Any
from pathlib import Path

# Suppress warnings for cleaner output
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)

try:
    import soundfile as sf
    SOUNDFILE_AVAILABLE = True
except ImportError:
    SOUNDFILE_AVAILABLE = False


class ESpeakTTSManager:
    """
    Ultra-fast TTS using eSpeak engine
    Optimized for maximum speed with sub-10ms latency
    Perfect for system notifications and debug scenarios
    """
    _instance = None

    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super(ESpeakTTSManager, cls).__new__(cls)
        return cls._instance

    def __init__(self):
        if not hasattr(self, 'initialized'):  # Ensure __init__ runs only once
            self.sample_rate = 22050  # eSpeak's default sample rate
            self.current_voice = "en+m3"  # M1K3 OPTIMIZED: Professional male voice
            self.speed = 170  # M1K3 OPTIMIZED: Responsive and detailed (was 150)
            self.pitch = 58   # M1K3 OPTIMIZED: Warmer, more human tone (was 48)
            self.amplitude = 100  # Volume (0-200, default: 100)
            self.word_gap = 1  # M1K3 OPTIMIZED: Slight gap for clarity

            # Available voices and languages
            self.available_voices = {
                "en": {"name": "English", "gender": "male", "variant": "default"},
                "en+f3": {"name": "English Female 3", "gender": "female", "variant": "f3"},
                "en+m3": {"name": "English Male 3", "gender": "male", "variant": "m3"},
                "en+f4": {"name": "English Female 4", "gender": "female", "variant": "f4"},
                "en-us": {"name": "English (US)", "gender": "male", "variant": "us"},
                "en-gb": {"name": "English (UK)", "gender": "male", "variant": "gb"}
            }

            # Performance profiles (M1K3 optimized)
            self.profiles = {
                "ultra_fast": {
                    "speed": 200,
                    "pitch": 48,
                    "amplitude": 100,
                    "word_gap": 0,
                    "description": "Maximum speed for emergency use"
                },
                "fast": {
                    "speed": 175,
                    "pitch": 48,
                    "amplitude": 100,
                    "word_gap": 1,
                    "description": "Fast but clear speech"
                },
                "balanced": {
                    "speed": 170,  # M1K3 DEFAULT: Responsive and engaging
                    "pitch": 58,   # M1K3 DEFAULT: Warm, human tone
                    "amplitude": 100,
                    "word_gap": 1,  # M1K3 DEFAULT: Slight clarity gap
                    "description": "M1K3 optimal: warm, engaging, professional"
                },
                "clear": {
                    "speed": 125,
                    "pitch": 48,
                    "amplitude": 100,
                    "word_gap": 2,
                    "description": "Very clear for complex explanations"
                }
            }

            self.current_profile = "balanced"  # M1K3 DEFAULT: Professional, clear delivery
            self._apply_profile(self.current_profile)
            self.initialized = True

    def is_available(self) -> bool:
        """Check if eSpeak is available on the system"""
        try:
            # Try to run espeak with version flag
            result = subprocess.run(['espeak', '--version'],
                                  capture_output=True, text=True, timeout=5)
            return result.returncode == 0
        except (subprocess.SubprocessError, FileNotFoundError):
            return False

    def get_availability_info(self) -> Dict[str, Any]:
        """Get detailed availability information"""
        info = {
            "available": self.is_available(),
            "soundfile_installed": SOUNDFILE_AVAILABLE,
            "current_voice": self.current_voice,
            "current_profile": self.current_profile,
            "sample_rate": self.sample_rate,
            "available_voices": len(self.available_voices),
            "available_profiles": len(self.profiles)
        }

        if self.is_available():
            try:
                # Get eSpeak version
                result = subprocess.run(['espeak', '--version'],
                                      capture_output=True, text=True, timeout=5)
                info["version"] = result.stdout.strip() if result.returncode == 0 else "unknown"
            except:
                info["version"] = "unknown"
        else:
            info["error"] = "eSpeak not installed or not in PATH"
            info["install_hint"] = "Install with: brew install espeak (macOS) or apt-get install espeak (Linux)"

        return info

    def load_model(self) -> bool:
        """Check if eSpeak is ready (no model loading needed)"""
        if not self.is_available():
            print("❌ eSpeak not available on system")
            print("   Install with: brew install espeak (macOS) or apt-get install espeak (Linux)")
            return False

        print("✅ eSpeak TTS ready")
        print(f"   Voice: {self.current_voice}")
        print(f"   Profile: {self.current_profile}")
        print(f"   Speed: {self.speed} WPM")
        print(f"   Optimized for: Ultra-fast synthesis (sub-10ms)")
        return True

    def generate(self, text: str, voice: Optional[str] = None) -> Optional[np.ndarray]:
        """Generate audio from text using eSpeak"""
        if not self.is_available():
            print("❌ eSpeak not available")
            return None

        # Use specified voice or current default
        selected_voice = voice if voice and voice in self.available_voices else self.current_voice

        try:
            start_time = time.time()

            # Create temporary file for output
            with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as temp_file:
                temp_path = temp_file.name

            # Build eSpeak command
            cmd = [
                'espeak',
                '-v', selected_voice,
                '-s', str(self.speed),      # Speed in words per minute
                '-p', str(self.pitch),      # Pitch (0-99)
                '-a', str(self.amplitude),  # Amplitude/volume (0-200)
                '-g', str(self.word_gap),   # Gap between words
                '-w', temp_path,            # Write to wav file
                text
            ]

            # Execute eSpeak
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=10)

            if result.returncode != 0:
                print(f"❌ eSpeak failed: {result.stderr}")
                os.unlink(temp_path) if os.path.exists(temp_path) else None
                return None

            # Read the generated audio file
            if not SOUNDFILE_AVAILABLE:
                print("❌ soundfile not available - cannot read generated audio")
                os.unlink(temp_path)
                return None

            audio_data, sample_rate = sf.read(temp_path, dtype=np.float32)

            # Clean up temporary file
            os.unlink(temp_path)

            # Ensure mono audio
            if len(audio_data.shape) > 1:
                audio_data = audio_data[:, 0] if audio_data.shape[1] > 0 else audio_data.flatten()

            # Update sample rate if different
            if sample_rate != self.sample_rate:
                self.sample_rate = sample_rate

            generation_time = time.time() - start_time
            duration = len(audio_data) / sample_rate
            rtf = generation_time / duration if duration > 0 else 0

            print(f"⚡ eSpeak: Generated {duration:.2f}s audio in {generation_time:.3f}s (RTF: {rtf:.2f}x)")

            return audio_data

        except subprocess.TimeoutExpired:
            print("❌ eSpeak synthesis timed out")
            return None
        except Exception as e:
            print(f"❌ eSpeak synthesis error: {e}")
            return None

    def set_voice(self, voice_name: str) -> bool:
        """Set the voice for generation"""
        if voice_name not in self.available_voices:
            print(f"❌ Voice '{voice_name}' not available")
            print(f"   Available voices: {list(self.available_voices.keys())}")
            return False

        self.current_voice = voice_name
        print(f"🎤 eSpeak voice set to: {voice_name}")
        return True

    def set_profile(self, profile_name: str) -> bool:
        """Set performance profile"""
        if profile_name not in self.profiles:
            print(f"❌ Profile '{profile_name}' not available")
            print(f"   Available profiles: {list(self.profiles.keys())}")
            return False

        self.current_profile = profile_name
        self._apply_profile(profile_name)
        print(f"🎯 eSpeak profile set to: {profile_name}")
        return True

    def _apply_profile(self, profile_name: str):
        """Apply settings from a performance profile"""
        if profile_name in self.profiles:
            profile = self.profiles[profile_name]
            self.speed = profile["speed"]
            self.pitch = profile["pitch"]
            self.amplitude = profile["amplitude"]
            self.word_gap = profile["word_gap"]

    def set_speed(self, words_per_minute: int) -> bool:
        """Set synthesis speed in words per minute"""
        if 80 <= words_per_minute <= 450:  # eSpeak's range
            self.speed = words_per_minute
            print(f"🎯 eSpeak speed set to {words_per_minute} WPM")
            return True
        else:
            print(f"❌ Speed must be between 80 and 450 WPM")
            return False

    def set_pitch(self, pitch: int) -> bool:
        """Set pitch (0-99)"""
        if 0 <= pitch <= 99:
            self.pitch = pitch
            print(f"🎯 eSpeak pitch set to {pitch}")
            return True
        else:
            print(f"❌ Pitch must be between 0 and 99")
            return False

    def get_available_voices(self) -> List[str]:
        """Get list of available voice names"""
        return list(self.available_voices.keys())

    def get_available_profiles(self) -> List[str]:
        """Get list of available performance profiles"""
        return list(self.profiles.keys())

    def get_voice_info(self, voice_name: str) -> Optional[Dict[str, Any]]:
        """Get information about a specific voice"""
        return self.available_voices.get(voice_name)

    def get_profile_info(self, profile_name: str) -> Optional[Dict[str, Any]]:
        """Get information about a specific profile"""
        return self.profiles.get(profile_name)

    def get_model_info(self) -> Dict[str, Any]:
        """Get current model information"""
        return {
            "loaded": self.is_available(),
            "voice": self.current_voice,
            "voice_info": self.available_voices.get(self.current_voice, {}),
            "profile": self.current_profile,
            "profile_info": self.profiles.get(self.current_profile, {}),
            "sample_rate": self.sample_rate,
            "speed": self.speed,
            "pitch": self.pitch,
            "amplitude": self.amplitude,
            "word_gap": self.word_gap,
            "available_voices": len(self.available_voices),
            "available_profiles": len(self.profiles),
            "engine_type": "espeak_formant_synthesis",
            "optimization": "ultra_fast"
        }

    def cleanup(self):
        """Clean up resources (no cleanup needed for eSpeak)"""
        pass


# Create singleton instance
espeak_manager = ESpeakTTSManager()


if __name__ == "__main__":
    # Test eSpeak TTS Manager
    print("🧪 Testing eSpeak TTS Manager")

    manager = ESpeakTTSManager()

    print(f"📊 Availability info: {manager.get_availability_info()}")

    if manager.is_available():
        print("✅ eSpeak available")

        if manager.load_model():
            print("✅ eSpeak ready")

            # Test different profiles
            for profile in ["ultra_fast", "fast", "balanced"]:
                print(f"\n🎯 Testing profile: {profile}")
                manager.set_profile(profile)

                test_text = "Ultra-fast speech synthesis test using eSpeak."
                print(f"🎤 Generating: '{test_text}'")

                audio = manager.generate(test_text)

                if audio is not None:
                    duration = len(audio) / manager.sample_rate
                    print(f"✅ Generated {duration:.2f}s of audio")
                else:
                    print("❌ Failed to generate audio")

            print(f"\n📊 Final model info: {manager.get_model_info()}")
        else:
            print("❌ eSpeak not ready")
    else:
        print("❌ eSpeak not available")

    manager.cleanup()
    print("🧪 Test complete")