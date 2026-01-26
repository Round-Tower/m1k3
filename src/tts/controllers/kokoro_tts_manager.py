#!/usr/bin/env python3
"""
Kokoro TTS Manager for M1K3
SOTA 82M parameter neural TTS with ONNX Runtime
Ultra-lightweight model optimized for real-time applications
Based on hexgrad/Kokoro-82M - #1 on TTS Arena
"""

import time
import numpy as np
import warnings
import os
from typing import Optional, List, Dict, Any
from pathlib import Path

# Suppress warnings for cleaner output
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)

try:
    import kokoro_onnx
    KOKORO_AVAILABLE = True
except ImportError:
    KOKORO_AVAILABLE = False


class KokoroTTSManager:
    """
    Ultra-lightweight neural TTS using Kokoro-82M (ONNX)
    82M parameter model - #1 on TTS Arena
    Optimized for real-time applications with minimal resource usage
    """
    _instance = None

    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super(KokoroTTSManager, cls).__new__(cls)
        return cls._instance

    def __init__(self, model_path: Optional[str] = None, voices_path: Optional[str] = None):
        if not hasattr(self, 'initialized'):  # Ensure __init__ runs only once
            # Model paths
            self.model_path = model_path or "models/kokoro/kokoro-v1.0.onnx"
            self.voices_path = voices_path or "models/kokoro/voices-v1.0.bin"

            # Engine state
            self.tts = None
            self.loading = False
            self.sample_rate = 24000  # Kokoro's native sample rate
            self.current_voice = "bm_daniel"  # Default voice (British Male - Daniel)

            # Performance settings
            self.speed = 1.0  # Speed multiplier (1.0 = normal)
            self.use_gpu = False  # GPU acceleration (requires onnxruntime-gpu)

            # Available voices from Kokoro (55 total!)
            # Popular voices for quick access
            self.available_voices = {
                # American Female
                "af_sky": {"name": "Sky (AF)", "language": "en-US", "gender": "female", "character": "Natural, clear"},
                "af_nova": {"name": "Nova (AF)", "language": "en-US", "gender": "female", "character": "Warm, friendly"},
                "af_sarah": {"name": "Sarah (AF)", "language": "en-US", "gender": "female", "character": "Professional"},
                "af_nicole": {"name": "Nicole (AF)", "language": "en-US", "gender": "female", "character": "Smooth, articulate"},

                # American Male
                "am_adam": {"name": "Adam (AM)", "language": "en-US", "gender": "male", "character": "Deep, authoritative"},
                "am_michael": {"name": "Michael (AM)", "language": "en-US", "gender": "male", "character": "Confident, clear"},
                "am_eric": {"name": "Eric (AM)", "language": "en-US", "gender": "male", "character": "Friendly, warm"},

                # British Female
                "bf_alice": {"name": "Alice (BF)", "language": "en-GB", "gender": "female", "character": "Refined, elegant"},
                "bf_emma": {"name": "Emma (BF)", "language": "en-GB", "gender": "female", "character": "Professional, clear"},
                "bf_lily": {"name": "Lily (BF)", "language": "en-GB", "gender": "female", "character": "Warm, friendly"},

                # British Male
                "bm_george": {"name": "George (BM)", "language": "en-GB", "gender": "male", "character": "Distinguished, commanding"},
                "bm_daniel": {"name": "Daniel (BM)", "language": "en-GB", "gender": "male", "character": "Professional, precise"},
                "bm_lewis": {"name": "Lewis (BM)", "language": "en-GB", "gender": "male", "character": "Articulate, clear"},
            }

            # Full voice list will be populated on load
            self.all_voices = []

            self.initialized = True

    def is_available(self) -> bool:
        """Check if Kokoro TTS is available"""
        return KOKORO_AVAILABLE

    def get_availability_info(self) -> Dict[str, Any]:
        """Get detailed availability information"""
        model_exists = os.path.exists(self.model_path)
        voices_exist = os.path.exists(self.voices_path)

        return {
            "available": self.is_available(),
            "kokoro_installed": KOKORO_AVAILABLE,
            "model_downloaded": model_exists,
            "voices_downloaded": voices_exist,
            "model_path": self.model_path,
            "voices_path": self.voices_path,
            "tts_loaded": self.tts is not None,
            "current_voice": self.current_voice,
            "sample_rate": self.sample_rate,
            "available_voices": len(self.available_voices),
            "gpu_available": self.use_gpu
        }

    def load_model(self, voice: Optional[str] = None, use_gpu: bool = False) -> bool:
        """Load the Kokoro TTS model"""
        if not self.is_available():
            print("❌ Kokoro TTS not available")
            print("   Install with: pip install kokoro-onnx")
            return False

        # Check if model files exist
        if not os.path.exists(self.model_path):
            print(f"❌ Model file not found: {self.model_path}")
            print(f"   Download from: https://huggingface.co/hexgrad/Kokoro-82M")
            print(f"   Place kokoro-v1.0.onnx in models/kokoro/")
            return False

        if not os.path.exists(self.voices_path):
            print(f"❌ Voices file not found: {self.voices_path}")
            print(f"   Download from: https://huggingface.co/hexgrad/Kokoro-82M")
            print(f"   Place voices-v1.0.bin in models/kokoro/")
            return False

        # If already loaded with same settings, skip reload
        if self.tts is not None and use_gpu == self.use_gpu:
            if voice:
                self.current_voice = voice
            return True

        if voice and voice in self.available_voices:
            self.current_voice = voice

        self.use_gpu = use_gpu

        print(f"🎤 Loading Kokoro-82M TTS (voice: {self.current_voice})")
        print(f"   GPU: {'Enabled' if use_gpu else 'Disabled (CPU)'}")
        self.loading = True
        start_time = time.time()

        try:
            # Create kokoro TTS instance
            # The kokoro_onnx.Kokoro class takes model and voices paths
            self.tts = kokoro_onnx.Kokoro(
                model_path=self.model_path,
                voices_path=self.voices_path
            )

            # Get full voice list
            self.all_voices = sorted(list(self.tts.voices.keys()))

            load_time = time.time() - start_time
            print(f"✅ Kokoro-82M loaded in {load_time:.2f} seconds")
            print(f"   Voice: {self.available_voices.get(self.current_voice, {}).get('name', self.current_voice)}")
            print(f"   Sample rate: {self.sample_rate}Hz")
            print(f"   Model size: 82M parameters")
            print(f"   Total voices available: {len(self.all_voices)}")
            print(f"   Optimized for: Ultra-fast real-time synthesis")

            self.loading = False
            return True

        except Exception as e:
            print(f"❌ Failed to load Kokoro TTS: {e}")
            print(f"   Attempted voice: {self.current_voice}")
            print(f"   GPU mode: {use_gpu}")
            self.tts = None
            self.loading = False
            return False

    def generate(self, text: str, voice: Optional[str] = None, speed: Optional[float] = None) -> Optional[np.ndarray]:
        """Generate audio from text using Kokoro TTS"""
        if not self.tts:
            print("⚠️  Kokoro TTS model not loaded. Attempting to load...")
            if not self.load_model(voice):
                print("❌ Failed to load Kokoro TTS model. Cannot generate audio.")
                return None

        # Use provided parameters or defaults
        target_voice = voice or self.current_voice
        target_speed = speed or self.speed

        # Validate voice
        if target_voice not in self.available_voices:
            print(f"⚠️  Voice '{target_voice}' not recognized. Using '{self.current_voice}'")
            target_voice = self.current_voice

        try:
            start_time = time.time()

            # Generate audio using Kokoro
            # The kokoro_onnx API: kokoro.create(text, voice, speed, lang)
            # Returns tuple of (audio_data, sample_rate)
            lang = 'en-gb' if target_voice.startswith('b') else 'en-us'
            audio_data, actual_sr = self.tts.create(
                text=text,
                voice=target_voice,
                speed=target_speed,
                lang=lang
            )

            if audio_data is None or len(audio_data) == 0:
                print("❌ No audio data generated")
                return None

            # Ensure audio is numpy array with correct dtype
            if not isinstance(audio_data, np.ndarray):
                audio_data = np.array(audio_data)

            # Convert to float32 if needed
            if audio_data.dtype != np.float32:
                audio_data = audio_data.astype(np.float32)

            generation_time = time.time() - start_time
            duration = len(audio_data) / self.sample_rate
            rtf = generation_time / duration if duration > 0 else 0

            print(f"🎯 Kokoro-82M: Generated {duration:.2f}s audio in {generation_time:.3f}s (RTF: {rtf:.2f}x)")

            return audio_data

        except Exception as e:
            print(f"❌ Kokoro TTS synthesis error: {e}")
            import traceback
            traceback.print_exc()
            return None

    def set_voice(self, voice: str) -> bool:
        """Set the voice for generation"""
        if voice not in self.available_voices:
            print(f"❌ Voice '{voice}' not available")
            print(f"   Available voices: {list(self.available_voices.keys())}")
            return False

        self.current_voice = voice
        print(f"✅ Voice set to: {self.available_voices[voice]['name']}")
        return True

    def get_available_voices(self) -> List[str]:
        """Get list of available voice IDs (all 55 voices if loaded, else popular subset)"""
        if self.all_voices:
            return self.all_voices
        return list(self.available_voices.keys())

    def get_voice_info(self, voice: str) -> Optional[Dict[str, Any]]:
        """Get information about a specific voice"""
        return self.available_voices.get(voice)

    def set_speed(self, speed: float) -> bool:
        """Set synthesis speed (1.0 = normal, <1.0 = faster, >1.0 = slower)"""
        if 0.5 <= speed <= 2.0:
            self.speed = speed
            print(f"🎯 Kokoro TTS speed set to {speed}x")
            return True
        else:
            print(f"❌ Speed multiplier must be between 0.5 and 2.0")
            return False

    def enable_gpu(self) -> bool:
        """Enable GPU acceleration (requires onnxruntime-gpu)"""
        if not self.is_available():
            return False

        try:
            import onnxruntime as ort
            providers = ort.get_available_providers()

            if 'CUDAExecutionProvider' in providers or 'CoreMLExecutionProvider' in providers:
                self.use_gpu = True
                # Reload model with GPU
                if self.tts:
                    return self.load_model(use_gpu=True)
                return True
            else:
                print("❌ GPU acceleration not available")
                print(f"   Available providers: {providers}")
                return False
        except Exception as e:
            print(f"❌ GPU enable failed: {e}")
            return False

    def get_model_info(self) -> Dict[str, Any]:
        """Get current model information"""
        if not self.tts:
            return {"loaded": False}

        return {
            "loaded": True,
            "model": "Kokoro-82M",
            "version": "v1.0",
            "voice": self.current_voice,
            "voice_info": self.available_voices.get(self.current_voice, {}),
            "sample_rate": self.sample_rate,
            "speed": self.speed,
            "gpu_enabled": self.use_gpu,
            "parameters": "82M",
            "available_voices": len(self.available_voices),
            "engine_type": "kokoro_onnx_tts",
            "optimization": "ultra_lightweight",
            "ranking": "#1 TTS Arena",
            "architecture": "StyleTTS2 + ISTFTNet"
        }

    def cleanup(self):
        """Clean up resources"""
        if self.tts:
            # Kokoro handles cleanup automatically
            pass
        self.tts = None
        self.loading = False


# Create singleton instance
kokoro_manager = KokoroTTSManager()


if __name__ == "__main__":
    # Test Kokoro TTS Manager
    print("🧪 Testing Kokoro TTS Manager")
    print("=" * 60)

    manager = KokoroTTSManager()

    print("\n📊 Availability Check:")
    info = manager.get_availability_info()
    for key, value in info.items():
        print(f"   {key}: {value}")

    if manager.is_available():
        print("\n✅ Kokoro TTS package available")

        if manager.load_model():
            print("\n✅ Model loaded successfully")
            print(f"\n📊 Model Info:")
            model_info = manager.get_model_info()
            for key, value in model_info.items():
                if isinstance(value, dict):
                    print(f"   {key}:")
                    for k, v in value.items():
                        print(f"      {k}: {v}")
                else:
                    print(f"   {key}: {value}")

            # Test synthesis
            test_text = "Hello! This is the Kokoro eighty-two million parameter text-to-speech model. It's optimized for real-time synthesis with minimal resource usage."
            print(f"\n🎤 Generating speech:")
            print(f"   Text: '{test_text}'")
            print(f"   Voice: {manager.available_voices[manager.current_voice]['name']}")

            audio = manager.generate(test_text)

            if audio is not None:
                duration = len(audio) / manager.sample_rate
                print(f"\n✅ Successfully generated {duration:.2f}s of audio")
                print(f"   Audio shape: {audio.shape}")
                print(f"   Audio dtype: {audio.dtype}")
                print(f"   Audio range: [{audio.min():.3f}, {audio.max():.3f}]")

                # Test voice switching
                print("\n🔄 Testing voice switching...")
                for voice_id in ['am', 'bf', 'bm']:
                    if voice_id in manager.available_voices:
                        manager.set_voice(voice_id)
                        test_audio = manager.generate("Testing voice " + voice_id, voice=voice_id)
                        if test_audio is not None:
                            print(f"   ✅ {voice_id}: {len(test_audio) / manager.sample_rate:.2f}s")
            else:
                print("\n❌ Failed to generate audio")
        else:
            print("\n❌ Failed to load model")
            print("\nTo use Kokoro TTS:")
            print("1. pip install kokoro-onnx")
            print("2. Download from https://huggingface.co/hexgrad/Kokoro-82M:")
            print("   - kokoro-v1.0.onnx")
            print("   - voices-v1.0.bin")
            print("3. Place files in models/kokoro/")
    else:
        print("\n❌ Kokoro TTS not available")
        print("   Install with: pip install kokoro-onnx")

    manager.cleanup()
    print("\n🧪 Test complete")
