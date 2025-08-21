#!/usr/bin/env python3
"""
Unified Voice Engine for M1K3
A modern, pipeline-based voice engine using modular components for
text processing, TTS synthesis, and audio effects.
"""

import time
import threading
import numpy as np
from typing import List, Dict, Any, Optional

from kittentts_manager import KittenManager
from text_processors import smart_text_chunking
from audio_effects import AudioEffect
from simple_voice_engine import SimpleVoiceEngine

class UnifiedVoiceEngine:
    """
    A flexible, pipeline-based voice engine.
    
    This engine works by processing text and audio through a series of
    modular components:
    1. Text Processors: Clean and chunk text for the TTS.
    2. TTS Manager: Synthesizes raw audio from text.
    3. Audio Effects Pipeline: Applies a series of post-processing effects.
    """

    def __init__(self):
        self.kitten_manager = KittenManager
        self.fallback_engine = SimpleVoiceEngine()
        
        # State
        self.voice_enabled = False
        self.is_loaded = False
        
        # Pipeline components
        self.text_chunker = smart_text_chunking
        self.effects_pipeline: List[AudioEffect] = []

        # Configurable settings
        self.sample_rate = 24000  # KittenTTS native sample rate
        self.chunk_size = 300
        self.inter_chunk_silence = 0.03  # Reduced from 0.1 for speed
        self.current_voice = None  # Will be set by voice profile

    def load_model(self) -> bool:
        """Loads the necessary models for the pipeline."""
        if self.is_loaded:
            return True
            
        # Try to load the core KittenTTS model
        if self.kitten_manager.load_model():
            self.is_loaded = True
            self.voice_enabled = True
            return True
        
        # Fallback to simple system TTS
        print("🔄 KittenTTS not available, falling back to system TTS.")
        if self.fallback_engine.load_model():
            self.is_loaded = True
            self.voice_enabled = True
            return True
            
        print("❌ All voice engines failed to load.")
        self.is_loaded = False
        self.voice_enabled = False
        return False

    def set_pipeline(self, effects: List[AudioEffect]):
        """Configure the audio effects pipeline."""
        self.effects_pipeline = effects
        print(f"🎤 Voice pipeline configured with: {[e.get_name() for e in effects]}")
    
    def set_voice(self, voice: str) -> bool:
        """Set the KittenTTS voice for synthesis."""
        if self.kitten_manager.set_voice(voice):
            self.current_voice = voice
            return True
        return False

    def synthesize_and_play(self, text: str, background: bool = True):
        """Synthesizes and plays audio in a background thread."""
        if not self.voice_enabled:
            return

        if background:
            thread = threading.Thread(target=self._synthesis_worker, args=(text,), daemon=True)
            thread.start()
        else:
            self._synthesis_worker(text)

    def _synthesis_worker(self, text: str):
        """The core synthesis and playback logic."""
        # Fallback if KittenTTS is not available
        if not self.kitten_manager.is_available():
            self.fallback_engine.synthesize_and_play(text, background=False)
            return

        # 1. Text Processing
        chunks = self.text_chunker(text, chunk_size=self.chunk_size)
        
        # 2. TTS Synthesis with voice selection
        audio_chunks = []
        for chunk in chunks:
            raw_audio = self.kitten_manager.generate(chunk, voice=self.current_voice)
            if raw_audio is not None:
                audio_chunks.append(raw_audio)
        
        if not audio_chunks:
            print("⚠️  TTS generation failed for all chunks.")
            return
            
        # Combine chunks with minimal silence for faster speech
        silence = np.zeros(int(self.inter_chunk_silence * self.sample_rate), dtype=np.float32)
        full_audio = np.concatenate([np.concatenate([chunk, silence]) for chunk in audio_chunks[:-1]] + [audio_chunks[-1]])
        
        # Add initial padding for effects protection
        pre_padding = np.zeros(int(0.1 * self.sample_rate), dtype=np.float32)  # 100ms pre-padding
        full_audio = np.concatenate([pre_padding, full_audio])
        
        # Only apply fade-out if audio doesn't end naturally
        fade_length = int(0.1 * self.sample_rate)  # 100ms fade
        if len(full_audio) > fade_length:
            # Check if audio already fades naturally (low amplitude at end)
            tail_samples = full_audio[-1000:]  # Check last ~40ms
            avg_tail_amplitude = np.mean(np.abs(tail_samples))
            
            # Only fade if the ending is still loud (above threshold)
            if avg_tail_amplitude > 0.05:  # Significant audio at end
                # Find where actual speech content ends
                speech_end_idx = len(full_audio)
                for i in range(len(full_audio)-1, max(0, len(full_audio)-fade_length*3), -1):
                    if abs(full_audio[i]) > 0.02:  # Found last significant speech
                        speech_end_idx = i + 1
                        break
                
                # Apply fade only after speech content ends
                fade_start = min(speech_end_idx, len(full_audio) - fade_length)
                if fade_start < len(full_audio):
                    fade_samples = len(full_audio) - fade_start
                    fade_curve = np.linspace(1.0, 0.0, fade_samples)
                    full_audio[fade_start:] *= fade_curve
        
        # Add generous padding at the end to prevent cutoff
        end_padding = np.zeros(int(0.5 * self.sample_rate), dtype=np.float32)  # 500ms padding (increased)
        full_audio = np.concatenate([full_audio, end_padding])

        # 3. Audio Effects Pipeline
        processed_audio = full_audio
        for effect in self.effects_pipeline:
            processed_audio = effect.apply(processed_audio, self.sample_rate)
        
        # 4. Post-Effects Padding (double protection)
        # Add final padding after all effects to ensure nothing gets truncated
        final_padding = np.zeros(int(0.2 * self.sample_rate), dtype=np.float32)  # 200ms final padding
        processed_audio = np.concatenate([processed_audio, final_padding])
            
        # 5. Hardware-Aware Playback
        try:
            import sounddevice as sd
            
            # Get hardware buffer info for timing calculations
            try:
                device_info = sd.query_devices(sd.default.device['output'])
                latency = device_info.get('default_high_output_latency', 0.1)  # Default 100ms
            except:
                latency = 0.15  # Conservative fallback
            
            # Calculate total playback time + hardware latency
            audio_duration = len(processed_audio) / self.sample_rate
            total_wait_time = audio_duration + latency + 0.1  # Extra 100ms safety margin
            
            # Start playback
            sd.play(processed_audio, samplerate=self.sample_rate)
            
            # Robust waiting - combine sd.wait() with manual timing
            try:
                sd.wait()  # Wait for buffer to empty
            except:
                pass  # Continue even if sd.wait() fails
            
            # Additional hardware completion delay
            import time
            time.sleep(latency + 0.05)  # Wait for hardware to finish + 50ms margin
            
        except Exception as e:
            print(f"❌ Audio playback failed: {e}")

if __name__ == "__main__":
    print("Testing Unified Voice Engine...")
    
    # Import effects for testing
    from audio_effects import IntercomEffect, CompressionEffect, NormalizationEffect

    engine = UnifiedVoiceEngine()
    
    if engine.load_model():
        print("\n✅ Engine loaded successfully.")
        
        # Test 1: A simple pipeline (Assistant Voice)
        print("\n--- Testing Assistant Profile ---")
        assistant_pipeline = [
            CompressionEffect(config={"threshold": 0.5, "ratio": 0.5}),
            NormalizationEffect(config={"level": 0.8})
        ]
        engine.set_pipeline(assistant_pipeline)
        engine.synthesize_and_play("This is a test of the standard assistant voice.", background=False)
        
        time.sleep(1)

        # Test 2: A more complex pipeline (Broadcast Voice)
        print("\n--- Testing Broadcast Profile ---")
        broadcast_pipeline = [
            IntercomEffect(config={"low_freq": 400, "high_freq": 4000}),
            CompressionEffect(config={"threshold": 0.4, "ratio": 0.2}),
            NormalizationEffect(config={"level": 0.9})
        ]
        engine.set_pipeline(broadcast_pipeline)
        engine.synthesize_and_play("Broadcasting live from the M1K3 Unified Voice Engine.", background=False)
        
        print("\n✅ Unified Voice Engine test complete!")
    else:
        print("❌ Engine failed to load.")
