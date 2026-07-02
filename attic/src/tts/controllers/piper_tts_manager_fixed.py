#!/usr/bin/env python3
"""
Piper TTS Manager for M1K3 - Fixed Implementation
Ultra-fast neural TTS engine optimized for real-time applications
Uses the proper piper-tts library with automatic voice model downloading
"""

import time
import numpy as np
import warnings
import os
import tempfile
import subprocess
import json
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


class PiperTTSManagerFixed:
    """
    Ultra-fast neural TTS using Piper engine (Fixed Implementation)
    Properly downloads and uses voice models
    """
    _instance = None

    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super(PiperTTSManagerFixed, cls).__new__(cls)
        return cls._instance

    def __init__(self, model_path: Optional[str] = None):
        if not hasattr(self, 'initialized'):
            self.model_path = model_path
            self.sample_rate = 22050

            # Default voice - use a simple model name that piper-tts can handle
            self.current_voice = "en_US-lessac-medium"

            # Models directory
            self.models_dir = Path.home() / ".local" / "share" / "piper-voices"
            self.models_dir.mkdir(parents=True, exist_ok=True)

            # Available voice models (simpler approach)
            self.available_voices = {
                "en_US-lessac-medium": {
                    "name": "Lessac (Medium Quality)",
                    "language": "en-US",
                    "quality": "medium",
                    "speed": "fast",
                    "url": "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US/lessac/medium/en_US-lessac-medium.onnx",
                    "config_url": "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/en_US/lessac/medium/en_US-lessac-medium.onnx.json"
                }
            }

            self.initialized = True

    def is_available(self) -> bool:
        """Check if Piper TTS is available"""
        try:
            # Check if piper command is available
            result = subprocess.run(['piper', '--version'],
                                  capture_output=True, text=True, timeout=5)
            return result.returncode == 0
        except (subprocess.SubprocessError, FileNotFoundError):
            # Try the piper-tts Python package approach
            try:
                import piper_tts
                return True
            except ImportError:
                return False

    def get_availability_info(self) -> Dict[str, Any]:
        """Get detailed availability information"""
        info = {
            "available": self.is_available(),
            "soundfile_installed": SOUNDFILE_AVAILABLE,
            "current_voice": self.current_voice,
            "sample_rate": self.sample_rate,
            "available_voices": len(self.available_voices),
            "models_dir": str(self.models_dir)
        }

        if not self.is_available():
            info["error"] = "Piper TTS not installed"
            info["install_hint"] = "Install with: pip install piper-tts"

        return info

    def _download_voice_model(self, voice_name: str) -> bool:
        """Download a voice model if it doesn't exist"""
        if voice_name not in self.available_voices:
            print(f"❌ Voice '{voice_name}' not available")
            return False

        voice_info = self.available_voices[voice_name]

        # File paths
        model_file = self.models_dir / f"{voice_name}.onnx"
        config_file = self.models_dir / f"{voice_name}.onnx.json"

        # Check if already downloaded
        if model_file.exists() and config_file.exists():
            return True

        print(f"📥 Downloading Piper voice model: {voice_name}")

        try:
            import requests

            # Download model file
            if not model_file.exists():
                print("   Downloading voice model...")
                response = requests.get(voice_info["url"], stream=True)
                response.raise_for_status()

                with open(model_file, 'wb') as f:
                    for chunk in response.iter_content(chunk_size=8192):
                        f.write(chunk)

            # Download config file
            if not config_file.exists():
                print("   Downloading voice config...")
                response = requests.get(voice_info["config_url"])
                response.raise_for_status()

                with open(config_file, 'w') as f:
                    f.write(response.text)

            print(f"✅ Downloaded {voice_name} model successfully")
            return True

        except ImportError:
            print("❌ 'requests' library not available for downloading models")
            return False
        except Exception as e:
            print(f"❌ Failed to download {voice_name} model: {e}")
            return False

    def load_model(self, voice_name: Optional[str] = None) -> bool:
        """Load the Piper TTS model"""
        if not self.is_available():
            print("❌ Piper TTS not available")
            return False

        if voice_name and voice_name in self.available_voices:
            self.current_voice = voice_name

        print(f"🎤 Loading Piper TTS voice: {self.current_voice}")

        # Try to download the model if needed
        if not self._download_voice_model(self.current_voice):
            print("❌ Failed to download voice model")
            return False

        print("✅ Piper TTS model ready")
        return True

    def generate(self, text: str, voice: Optional[str] = None) -> Optional[np.ndarray]:
        """Generate audio from text using Piper TTS"""
        if not self.is_available():
            print("❌ Piper TTS not available")
            return None

        # Use specified voice or current default
        selected_voice = voice if voice and voice in self.available_voices else self.current_voice

        try:
            start_time = time.time()

            # Create temporary files
            with tempfile.NamedTemporaryFile(mode='w', suffix='.txt', delete=False) as text_file:
                text_file.write(text)
                text_path = text_file.name

            with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as audio_file:
                audio_path = audio_file.name

            # Model paths
            model_file = self.models_dir / f"{selected_voice}.onnx"

            if not model_file.exists():
                print(f"❌ Model file not found: {model_file}")
                return None

            # Run piper command
            cmd = [
                'piper',
                '--model', str(model_file),
                '--output_file', audio_path
            ]

            # Execute piper with text input
            with open(text_path, 'r') as f:
                result = subprocess.run(cmd, stdin=f, capture_output=True, text=True, timeout=30)

            # Clean up text file
            os.unlink(text_path)

            if result.returncode != 0:
                print(f"❌ Piper synthesis failed: {result.stderr}")
                os.unlink(audio_path) if os.path.exists(audio_path) else None
                return None

            # Read the generated audio
            if not SOUNDFILE_AVAILABLE:
                print("❌ soundfile not available - cannot read generated audio")
                os.unlink(audio_path)
                return None

            audio_data, sample_rate = sf.read(audio_path, dtype=np.float32)

            # Clean up audio file
            os.unlink(audio_path)

            # Ensure mono audio
            if len(audio_data.shape) > 1:
                audio_data = audio_data[:, 0] if audio_data.shape[1] > 0 else audio_data.flatten()

            generation_time = time.time() - start_time
            duration = len(audio_data) / sample_rate
            rtf = generation_time / duration if duration > 0 else 0

            print(f"🎯 Piper TTS: Generated {duration:.2f}s audio in {generation_time:.3f}s (RTF: {rtf:.2f}x)")

            return audio_data

        except subprocess.TimeoutExpired:
            print("❌ Piper synthesis timed out")
            return None
        except Exception as e:
            print(f"❌ Piper synthesis error: {e}")
            return None

    def set_voice(self, voice_name: str) -> bool:
        """Set the voice for generation"""
        if voice_name not in self.available_voices:
            print(f"❌ Voice '{voice_name}' not available")
            print(f"   Available voices: {list(self.available_voices.keys())}")
            return False

        self.current_voice = voice_name
        print(f"🎤 Piper voice set to: {voice_name}")
        return True

    def get_available_voices(self) -> List[str]:
        """Get list of available voice names"""
        return list(self.available_voices.keys())

    def get_voice_info(self, voice_name: str) -> Optional[Dict[str, Any]]:
        """Get information about a specific voice"""
        return self.available_voices.get(voice_name)

    def get_model_info(self) -> Dict[str, Any]:
        """Get current model information"""
        return {
            "loaded": self.is_available(),
            "voice": self.current_voice,
            "voice_info": self.available_voices.get(self.current_voice, {}),
            "sample_rate": self.sample_rate,
            "available_voices": len(self.available_voices),
            "engine_type": "piper_neural_tts",
            "optimization": "real_time",
            "models_dir": str(self.models_dir)
        }

    def cleanup(self):
        """Clean up resources"""
        pass


# Create singleton instance
piper_manager_fixed = PiperTTSManagerFixed()


if __name__ == "__main__":
    # Test Fixed Piper TTS Manager
    print("🧪 Testing Fixed Piper TTS Manager")

    manager = PiperTTSManagerFixed()

    print(f"📊 Availability info: {manager.get_availability_info()}")

    if manager.is_available():
        print("✅ Piper TTS available")

        if manager.load_model():
            print("✅ Model loaded successfully")

            # Test synthesis
            test_text = "Hello! This is a test of the improved Piper TTS engine with proper voice models."
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

    manager.cleanup()
    print("🧪 Test complete")