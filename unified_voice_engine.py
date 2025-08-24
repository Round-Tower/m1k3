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
from audio_completion_engine import AudioCompletionEngine

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
        
        # Configurable settings
        self.sample_rate = 24000  # KittenTTS native sample rate
        
        # Pipeline components
        self.text_chunker = smart_text_chunking
        self.effects_pipeline: List[AudioEffect] = []
        self.completion_engine = AudioCompletionEngine(sample_rate=self.sample_rate)
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
    
    def _add_truncation_padding(self, text: str) -> str:
        """Add padding to text to help prevent model-level truncation"""
        # Don't pad if text is already very long
        if len(text) > 500:
            return text
            
        padded = text.strip()
        
        # Ensure text ends with proper punctuation
        if not padded.endswith(('.', '!', '?')):
            padded += '.'
        
        # Add ellipsis padding that can be truncated without loss
        # The model can cut off the padding but the main content survives
        padded += ' ...'
        
        return padded

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

        # 1. Text Processing with anti-truncation padding
        # Add padding to help prevent model-level truncation
        padded_text = self._add_truncation_padding(text)
        chunks = self.text_chunker(padded_text, chunk_size=self.chunk_size)
        
        # 2. TTS Synthesis with voice selection and truncation fixing
        audio_chunks = []
        for i, chunk in enumerate(chunks):
            raw_audio = self.kitten_manager.generate(chunk, voice=self.current_voice)
            if raw_audio is not None:
                # Fix truncation in each chunk with debug label
                fixed_audio, fix_info = self.completion_engine.fix_audio(raw_audio, f"chunk_{i+1}")
                audio_chunks.append(fixed_audio)
        
        if not audio_chunks:
            print("⚠️  TTS generation failed for all chunks.")
            return
            
        # Combine chunks with minimal silence for faster speech
        silence = np.zeros(int(self.inter_chunk_silence * self.sample_rate), dtype=np.float32)
        full_audio = np.concatenate([np.concatenate([chunk, silence]) for chunk in audio_chunks[:-1]] + [audio_chunks[-1]])
        
        # MULTI-LEVEL FIX: Check combined audio for truncation
        final_audio_fixed, final_fix_info = self.completion_engine.fix_audio(full_audio, "combined_audio")
        full_audio = final_audio_fixed
        
        # Add enhanced padding for clean playback
        pre_padding = np.zeros(int(0.05 * self.sample_rate), dtype=np.float32)  # 50ms pre-padding
        end_padding = np.zeros(int(0.3 * self.sample_rate), dtype=np.float32)  # Increased to 300ms
        full_audio = np.concatenate([pre_padding, full_audio, end_padding])

        # 3. Audio Effects Pipeline
        processed_audio = full_audio
        for effect in self.effects_pipeline:
            processed_audio = effect.apply(processed_audio, self.sample_rate)
        
        # 4. POST-EFFECTS TRUNCATION CHECK: Final safety check
        post_effects_fixed, post_fix_info = self.completion_engine.fix_audio(processed_audio, "post_effects")
        processed_audio = post_effects_fixed
        
        # Enhanced final padding based on truncation analysis
        base_final_padding = 0.1  # 100ms base
        if post_fix_info.get('applied_fix', False):
            # If we had to fix post-effects, add extra padding
            base_final_padding = 0.2  # 200ms for problematic audio
        
        final_padding = np.zeros(int(base_final_padding * self.sample_rate), dtype=np.float32)
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
            
            # Start playback with enhanced completion verification
            sd.play(processed_audio, samplerate=self.sample_rate)
            
            # Calculate expected playback duration
            audio_duration = len(processed_audio) / self.sample_rate
            
            # Enhanced wait with timeout and retry
            try:
                sd.wait()  # Remove timeout parameter for compatibility
            except Exception as e:
                print(f"🔊 Wait exception: {e}")
                # Fallback: manual timing
                import time
                time.sleep(audio_duration + 0.5)
            
            # Additional completion verification delay
            import time
            time.sleep(0.3)  # Increased from 0.1s to 0.3s for better completion
            
            # Optional: Verify no audio is still playing
            try:
                if sd.query_devices(sd.default.device['output']):
                    time.sleep(0.1)  # Brief additional wait if device is still active
            except:
                pass
            
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
