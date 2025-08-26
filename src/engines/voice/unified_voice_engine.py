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

from src.tts.controllers.kittentts_manager import KittenManager
from src.utils.text_processors import smart_text_chunking
from src.tts.effects.audio_effects import AudioEffect
from src.engines.voice.simple_voice_engine import SimpleVoiceEngine
from src.tts.effects.audio_completion_engine import AudioCompletionEngine

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
        self.kitten_manager = KittenManager  # KittenManager is already a singleton instance
        self.fallback_engine = SimpleVoiceEngine()
        
        # State
        self.voice_enabled = False
        self.is_loaded = False
        
        # Shutdown signal (can be set by CLI core for force termination)
        self.force_shutdown = None
        
        # Configurable settings
        self.sample_rate = 24000  # KittenTTS native sample rate
        
        # Pipeline components
        self.text_chunker = smart_text_chunking
        self.effects_pipeline: List[AudioEffect] = []
        self.completion_engine = AudioCompletionEngine(sample_rate=self.sample_rate)
        self.chunk_size = 300
        self.inter_chunk_silence = 0.02  # Optimized for speed
        self.current_voice = None  # Will be set by voice profile
        self.current_profile = "natural"
        
        # Voice profiles with audio effect configurations
        # Standard effects: compression, normalization, formant_correction for quality
        # Intercom effects: the main differentiator between profiles
        self.profiles = {
            "natural": {
                "description": "Default conversational voice with light intercom enhancement",
                "effects": ["light_intercom", "formant_correction", "compression", "normalization"]  # Now with intercom
            },
            "assistant": {
                "description": "Professional AI assistant tone with medium intercom", 
                "effects": ["medium_intercom", "formant_correction", "compression", "normalization"]  # Enhanced intercom
            },
            "broadcast": {
                "description": "Clear, announcer-style voice with strong intercom effect",
                "effects": ["heavy_intercom", "formant_correction", "compression", "normalization"]  # Radio/PA style
            },
            "terminal": {
                "description": "Technical, system-style voice with medium intercom",
                "effects": ["medium_intercom", "formant_correction", "compression", "normalization"]  # System announcements
            },
            "debug": {
                "description": "Fast, minimal voice for debugging - no effects for speed",
                "effects": []  # No processing for maximum speed
            },
            "minimal": {
                "description": "Basic synthesis with minimal processing - no effects",
                "effects": []  # No effects for compatibility/fallback
            }
        }

    def load_model(self) -> bool:
        """Loads the necessary models for the pipeline."""
        if self.is_loaded:
            return True
            
        # Try to load the core KittenTTS model
        if self.kitten_manager.load_model():
            self.is_loaded = True
            self.voice_enabled = True
            # Initialize effects pipeline with default profile
            self._configure_effects_pipeline()
            return True
        
        # Fallback to simple system TTS
        print("🔄 KittenTTS not available, falling back to system TTS.")
        if self.fallback_engine.load_model():
            self.is_loaded = True
            self.voice_enabled = True
            # Initialize effects pipeline with default profile
            self._configure_effects_pipeline()
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

    def _synthesis_fast_path(self, text: str):
        """Fast-path synthesis for short text with minimal processing"""
        try:
            import sounddevice as sd
            
            # Generate audio directly without chunking
            raw_audio = self.kitten_manager.generate(text, voice=self.current_voice)
            if raw_audio is None:
                print("⚠️ Fast-path TTS generation failed, falling back to simple engine")
                return self.fallback_engine.synthesize_and_play(text, background=False)
            
            # Minimal padding - just enough to prevent cutoff
            pre_padding = np.zeros(int(0.02 * self.sample_rate), dtype=np.float32)  # 20ms
            end_padding = np.zeros(int(0.08 * self.sample_rate), dtype=np.float32)  # 80ms
            processed_audio = np.concatenate([pre_padding, raw_audio, end_padding])
            
            # Apply effects pipeline if configured
            try:
                for effect in self.effects_pipeline:
                    processed_audio = effect.apply(processed_audio, self.sample_rate)
            except Exception as e:
                print(f"⚠️ Audio effects failed, using raw audio: {e}")
            
            # Play immediately
            sd.play(processed_audio, samplerate=self.sample_rate)
            sd.wait()
            
            return True
            
        except Exception as e:
            print(f"❌ Fast-path synthesis failed: {e}")
            # Fallback to simple engine
            return self.fallback_engine.synthesize_and_play(text, background=False)

    def synthesize_and_play(self, text: str, background: bool = True):
        """Synthesizes and plays audio in a background thread."""
        if not self.voice_enabled:
            print("⚠️ Voice synthesis disabled")
            return False
            
        if not self.is_loaded:
            print("⚠️ Voice engine not loaded")
            return False
            
        if not text or not text.strip():
            print("⚠️ Empty text provided for synthesis")
            return False

        try:
            if background:
                thread = threading.Thread(target=self._synthesis_worker, args=(text,), daemon=True)
                thread.start()
                return True
            else:
                return self._synthesis_worker(text)
        except Exception as e:
            print(f"❌ Failed to start synthesis: {e}")
            return False

    def _synthesis_worker(self, text: str):
        """The core synthesis and playback logic."""
        # Fallback if KittenTTS is not available
        if not self.kitten_manager.is_available():
            return self.fallback_engine.synthesize_and_play(text, background=False)

        # Fast-path for short text (under 100 chars) - skip chunking and minimal processing
        if len(text) < 100:
            return self._synthesis_fast_path(text)

        # 1. Text Processing with anti-truncation padding
        # Add padding to help prevent model-level truncation
        padded_text = self._add_truncation_padding(text)
        chunks = self.text_chunker(padded_text, chunk_size=self.chunk_size)
        
        # 2. TTS Synthesis with voice selection and smart truncation fixing
        audio_chunks = []
        failed_chunks = 0
        
        for i, chunk in enumerate(chunks):
            try:
                raw_audio = self.kitten_manager.generate(chunk, voice=self.current_voice)
                if raw_audio is not None:
                    # Smart completion engine usage for speed optimization
                    if i == len(chunks) - 1 and len(chunk) > 50:
                        # Only apply to final chunk of longer text to prevent cutoff
                        try:
                            fixed_audio, fix_info = self.completion_engine.fix_audio(raw_audio, f"final_chunk")
                            audio_chunks.append(fixed_audio)
                        except Exception as e:
                            print(f"⚠️ Audio completion failed, using raw audio: {e}")
                            audio_chunks.append(raw_audio)
                    else:
                        # Skip completion engine for intermediate chunks and very short text
                        audio_chunks.append(raw_audio)
                else:
                    failed_chunks += 1
                    print(f"⚠️ Chunk {i+1} generation failed")
                    
            except Exception as e:
                failed_chunks += 1
                print(f"⚠️ Error generating chunk {i+1}: {e}")
        
        if not audio_chunks:
            print("❌ TTS generation failed for all chunks, falling back to simple engine")
            return self.fallback_engine.synthesize_and_play(text, background=False)
        elif failed_chunks > 0:
            print(f"⚠️ {failed_chunks}/{len(chunks)} chunks failed, continuing with partial audio")
            
        # Combine chunks with minimal silence for faster speech
        silence = np.zeros(int(self.inter_chunk_silence * self.sample_rate), dtype=np.float32)
        full_audio = np.concatenate([np.concatenate([chunk, silence]) for chunk in audio_chunks[:-1]] + [audio_chunks[-1]])
        
        # Add optimized padding for clean playback (reduced for speed)
        pre_padding = np.zeros(int(0.03 * self.sample_rate), dtype=np.float32)  # 30ms pre-padding  
        end_padding = np.zeros(int(0.15 * self.sample_rate), dtype=np.float32)  # Reduced to 150ms
        full_audio = np.concatenate([pre_padding, full_audio, end_padding])

        # 3. Audio Effects Pipeline
        processed_audio = full_audio
        for effect in self.effects_pipeline:
            processed_audio = effect.apply(processed_audio, self.sample_rate)
        
        # 4. Add minimal final padding for clean completion
        final_padding = np.zeros(int(0.05 * self.sample_rate), dtype=np.float32)  # 50ms final padding
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
            
            # Enhanced wait with timeout and retry - interruptible
            try:
                # Check for force shutdown before blocking wait
                if self.force_shutdown and self.force_shutdown.is_set():
                    print("🛑 Audio playback interrupted by shutdown signal")
                    sd.stop()
                    return True
                
                # Use manual timing instead of sd.wait() for interruptibility  
                elapsed = 0
                check_interval = 0.1  # Check shutdown signal every 100ms
                
                while elapsed < audio_duration + 0.5:
                    if self.force_shutdown and self.force_shutdown.is_set():
                        print("🛑 Audio playback interrupted by shutdown signal")
                        sd.stop()
                        return True
                    
                    import time
                    time.sleep(min(check_interval, audio_duration + 0.5 - elapsed))
                    elapsed += check_interval
                    
            except Exception as e:
                print(f"🔊 Wait exception: {e}")
                # Fallback: manual timing
                import time
                time.sleep(audio_duration + 0.5)
            
            # Minimal completion verification delay (optimized for speed)
            import time
            time.sleep(0.1)  # Reduced from 0.3s to 0.1s for better performance
            
            return True
            
        except Exception as e:
            print(f"❌ Audio playback failed: {e}")
            return False

    def set_profile(self, profile_name: str) -> bool:
        """Set the voice profile and configure audio effects pipeline"""
        if profile_name in self.profiles:
            self.current_profile = profile_name
            
            # Configure effects pipeline based on profile
            self._configure_effects_pipeline()
            
            print(f"🎭 UnifiedVoiceEngine profile set to: {profile_name}")
            print(f"🎵 Effects pipeline: {[e.get_name() for e in self.effects_pipeline]}")
            return True
        return False
    
    def _configure_effects_pipeline(self):
        """Configure the audio effects pipeline based on current profile"""
        try:
            from src.tts.effects.audio_effects import IntercomEffect, CompressionEffect, NormalizationEffect, FormantCorrectionEffect
            effects_available = True
        except ImportError as e:
            print(f"⚠️ Audio effects not available: {e}")
            effects_available = False
        
        # Clear existing pipeline
        self.effects_pipeline = []
        
        # Skip effects configuration if not available
        if not effects_available:
            print("🔇 Running without audio effects")
            return
        
        # Get effects list for current profile
        profile_effects = self.profiles[self.current_profile].get("effects", [])
        
        # Create effect instances based on profile configuration
        for effect_name in profile_effects:
            try:
                if effect_name == "light_intercom":
                    # Subtle intercom for assistant branding
                    effect = IntercomEffect({"low_freq": 400, "high_freq": 3000})
                    self.effects_pipeline.append(effect)
                    
                elif effect_name == "medium_intercom":
                    # Standard intercom for terminal/system voice
                    effect = IntercomEffect({"low_freq": 350, "high_freq": 3200})
                    self.effects_pipeline.append(effect)
                    
                elif effect_name == "heavy_intercom":
                    # Strong intercom for broadcast announcements
                    effect = IntercomEffect({"low_freq": 300, "high_freq": 3400})
                    self.effects_pipeline.append(effect)
                    
                elif effect_name == "compression":
                    # Audio compression for consistency
                    effect = CompressionEffect({"threshold": 0.7, "ratio": 0.4})
                    self.effects_pipeline.append(effect)
                    
                elif effect_name == "normalization":
                    # Volume normalization
                    effect = NormalizationEffect({"level": 0.85})
                    self.effects_pipeline.append(effect)
                    
                elif effect_name == "formant_correction":
                    # Formant correction for clarity
                    effect = FormantCorrectionEffect({"shift_factor": 0.97})
                    self.effects_pipeline.append(effect)
                    
                else:
                    print(f"⚠️ Unknown effect: {effect_name}")
                    
            except Exception as e:
                print(f"⚠️ Failed to create effect {effect_name}: {e}")

    def get_current_profile(self) -> dict:
        """Get current voice profile settings"""
        return self.profiles.get(self.current_profile, self.profiles["natural"])
        
    def set_voice_enabled(self, enabled: bool):
        """Enable or disable voice output"""
        self.voice_enabled = enabled
    
    def get_status(self) -> dict:
        """Get voice engine status"""
        return {
            "available": self.voice_enabled,
            "loaded": self.is_loaded,
            "enabled": self.voice_enabled,
            "loading": False,
            "model": "UnifiedVoiceEngine",
            "current_profile": self.current_profile,
            "available_profiles": list(self.profiles.keys())
        }

if __name__ == "__main__":
    print("Testing Unified Voice Engine...")
    
    # Import effects for testing
    from src.tts.effects.audio_effects import IntercomEffect, CompressionEffect, NormalizationEffect

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
