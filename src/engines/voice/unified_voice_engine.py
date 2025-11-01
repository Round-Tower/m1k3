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
from src.tts.controllers.vibevoice_manager import VibeVoiceManager
from src.tts.controllers.piper_tts_manager import piper_manager
from src.tts.controllers.espeak_tts_manager import espeak_manager
from src.utils.text_processors import smart_text_chunking
from src.tts.effects.audio_effects import AudioEffect
from src.engines.voice.simple_voice_engine import SimpleVoiceEngine
from src.tts.effects.audio_completion_engine import AudioCompletionEngine
from src.tts.streaming.realtime_processor import StreamingTextProcessor, ConversationFlowOptimizer
from src.engines.voice.intelligent_tts_engine import intelligent_tts_engine, TTSQuality

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
        # NEW: Intelligent TTS engine (automatically selects best engine)
        self.intelligent_engine = intelligent_tts_engine

        # Legacy engines (for compatibility and manual selection)
        self.kitten_manager = KittenManager  # KittenManager is already a singleton instance
        self.vibevoice_manager = VibeVoiceManager()  # VibeVoice manager instance
        self.piper_manager = piper_manager  # Piper TTS manager (singleton)
        self.espeak_manager = espeak_manager  # eSpeak TTS manager (singleton)
        self.fallback_engine = SimpleVoiceEngine()

        # State
        self.voice_enabled = False
        self.is_loaded = False
        self.preferred_engine = "intelligent"  # NEW: Default to intelligent engine (automatically avoids truncation)
        self.legacy_engine = "kitten"  # Fallback for manual engine selection
        
        # Shutdown signal (can be set by CLI core for force termination)
        self.force_shutdown = None
        
        # Configurable settings
        self.sample_rate = 24000  # KittenTTS native sample rate

        # Real-time processing components
        self.streaming_processor = None
        self.conversation_optimizer = None
        self.realtime_mode = False
        
        # Pipeline components
        self.text_chunker = smart_text_chunking
        self.effects_pipeline: List[AudioEffect] = []
        self.completion_engine = AudioCompletionEngine(sample_rate=self.sample_rate)
        self.chunk_size = 300
        self.inter_chunk_silence = 0.02  # Optimized for speed
        self.current_voice = None  # Will be set by voice profile
        self.current_profile = "natural"  # Uses kitten by default now
        
        # Voice profiles with audio effect configurations
        # Standard effects: compression, normalization, formant_correction for quality
        # Intercom effects: the main differentiator between profiles
        self.profiles = {
            # Real-time optimized profiles (NEW - for ultra-fast chat)
            "realtime": {
                "description": "Ultra-fast real-time chat using Piper TTS (sub-50ms)",
                "effects": ["compression", "normalization"],
                "preferred_engine": "piper",
                "speed": 1.1,  # Slightly faster for chat
                "optimization": "speed"
            },
            "instant": {
                "description": "Instant synthesis using eSpeak (sub-10ms, emergency speed)",
                "effects": [],  # No effects for maximum speed
                "preferred_engine": "espeak",
                "profile": "ultra_fast",
                "optimization": "maximum_speed"
            },
            "chat": {
                "description": "Optimized for conversational AI with Piper neural TTS",
                "effects": ["light_intercom", "compression", "normalization"],
                "preferred_engine": "piper",
                "speed": 1.0,
                "optimization": "balanced"
            },

            # Traditional profiles (updated with new engine priorities)
            "natural": {
                "description": "Default conversational voice with medium intercom enhancement",
                "effects": ["medium_intercom", "formant_correction", "compression", "normalization"],
                "preferred_engine": "kitten"  # KittenTTS for best quality/speed balance
            },
            "assistant": {
                "description": "Professional AI assistant tone with medium intercom",
                "effects": ["medium_intercom", "formant_correction", "compression", "normalization"],
                "preferred_engine": "kitten"  # KittenTTS for quality
            },
            "broadcast": {
                "description": "Clear, announcer-style voice with strong intercom effect",
                "effects": ["heavy_intercom", "formant_correction", "compression", "normalization"],
                "preferred_engine": "kitten"  # KittenTTS for quality
            },
            "terminal": {
                "description": "Technical, system-style voice with medium intercom",
                "effects": ["medium_intercom", "formant_correction", "compression", "normalization"],
                "preferred_engine": "kitten"  # KittenTTS for quality
            },
            "debug": {
                "description": "Fast, minimal voice for debugging - eSpeak for maximum speed",
                "effects": [],
                "preferred_engine": "espeak",  # Updated to use eSpeak for debug
                "profile": "fast"
            },
            "minimal": {
                "description": "Basic synthesis with minimal processing - no effects",
                "effects": [],
                "preferred_engine": "fallback"
            },

            # Legacy KittenTTS profiles (for compatibility)
            "kitten_natural": {
                "description": "KittenTTS with natural voice processing",
                "effects": ["medium_intercom", "formant_correction", "compression", "normalization"],
                "preferred_engine": "kitten"
            },
            "kitten_fast": {
                "description": "KittenTTS optimized for speed",
                "effects": ["compression", "normalization"],
                "preferred_engine": "kitten"
            },

            # VibeVoice profiles (for long-form content)
            "conversational": {
                "description": "Multi-speaker conversation using VibeVoice (up to 4 speakers)",
                "effects": ["compression", "normalization"],
                "preferred_engine": "vibevoice",
                "speakers": ["Alice", "Bob"]
            },
            "narrative": {
                "description": "Long-form storytelling with VibeVoice (up to 90 minutes)",
                "effects": ["compression", "normalization"],
                "preferred_engine": "vibevoice",
                "speakers": ["Alice"]
            },
            "assistant_duo": {
                "description": "AI assistant with user voice simulation using VibeVoice",
                "effects": ["medium_intercom", "compression", "normalization"],
                "preferred_engine": "vibevoice",
                "speakers": ["Alice", "Bob"]
            },

            # New reverb-enhanced profiles for real-time chatbot
            "studio": {
                "description": "Studio-quality voice with professional reverb",
                "effects": ["reverb_studio", "compression", "normalization"],
                "preferred_engine": "kitten",
                "optimization": "quality"
            },
            "hall": {
                "description": "Grand hall reverb for announcements and presentations",
                "effects": ["reverb_hall", "compression", "normalization"],
                "preferred_engine": "kitten",
                "optimization": "quality"
            },
            "intimate": {
                "description": "Intimate conversation with subtle room reverb",
                "effects": ["reverb_intimate", "compression", "normalization"],
                "preferred_engine": "kitten",
                "optimization": "balanced"
            },
            "realtime_chat": {
                "description": "Ultra-optimized for real-time conversation (minimal effects, maximum speed)",
                "effects": ["compression"],
                "preferred_engine": "kitten",
                "optimization": "speed"
            },
            "studio_chat": {
                "description": "High-quality chat with studio reverb for voice assistant character",
                "effects": ["reverb_studio", "compression"],
                "preferred_engine": "kitten",
                "optimization": "balanced"
            },
            "intimate_chat": {
                "description": "Personal AI companion with intimate room reverb",
                "effects": ["reverb_intimate", "compression"],
                "preferred_engine": "kitten",
                "optimization": "balanced"
            }
        }

    def load_model(self, preferred_engine: str = None) -> bool:
        """Loads the necessary models for the pipeline."""
        if self.is_loaded:
            return True

        if preferred_engine:
            self.preferred_engine = preferred_engine

        # NEW: Priority 0: Intelligent Engine - Automatically selects best engine to avoid truncation
        if self.preferred_engine == "intelligent" or (not preferred_engine and self.preferred_engine == "intelligent"):
            if self.intelligent_engine.load_model():
                self.is_loaded = True
                self.voice_enabled = True
                self.preferred_engine = "intelligent"
                print("✅ Intelligent TTS Engine loaded (auto-selects best engine, avoids truncation)")
                print("   Available engines: eSpeak (no truncation), KittenTTS (with completion), others...")
                return True
            else:
                print("🔄 Intelligent engine not available, falling back to legacy engines...")

        # Legacy engine loading (for manual selection)
        # Priority: kitten > piper > espeak > vibevoice > fallback
        # Note: KittenTTS has truncation issues but good quality with completion

        # Priority 1: KittenTTS - Best quality/speed balance (but has truncation issues)
        if self.preferred_engine == "kitten":
            if self.kitten_manager.load_model():
                self.is_loaded = True
                self.voice_enabled = True
                self.preferred_engine = "kitten"
                print("✅ KittenTTS engine loaded successfully (best quality/speed balance)")
                self._configure_effects_pipeline()
                return True
            else:
                print("🔄 KittenTTS not available, trying next engine...")

        # Priority 2: Piper TTS - Ultra-fast neural TTS (sub-50ms)
        if self.preferred_engine == "piper" or not self.is_loaded:
            if self.piper_manager.is_available():
                print("🔄 Attempting Piper TTS loading...")
                start_time = time.time()

                if self.piper_manager.load_model():
                    load_time = time.time() - start_time
                    self.is_loaded = True
                    self.voice_enabled = True
                    self.preferred_engine = "piper"
                    print(f"✅ Piper TTS engine loaded successfully ({load_time:.2f}s)")
                    self._configure_effects_pipeline()
                    return True
                else:
                    print("🔄 Piper TTS loading failed, trying next engine...")
            else:
                print("🔄 Piper TTS not available, trying next engine...")

        # Priority 3: eSpeak - Ultra-fast fallback (sub-10ms) - ONLY if no better options
        if self.preferred_engine == "espeak" or not self.is_loaded:
            if self.espeak_manager.is_available():
                print("🔄 Attempting eSpeak TTS loading... (Note: Poor quality, but fast)")
                start_time = time.time()

                if self.espeak_manager.load_model():
                    load_time = time.time() - start_time
                    self.is_loaded = True
                    self.voice_enabled = True
                    self.preferred_engine = "espeak"
                    print(f"⚠️ eSpeak TTS loaded ({load_time:.2f}s) - Consider better engines for quality")
                    self._configure_effects_pipeline()
                    return True
                else:
                    print("🔄 eSpeak TTS loading failed, trying next engine...")
            else:
                print("🔄 eSpeak TTS not available, trying next engine...")

        # Priority 4: VibeVoice - Long-form TTS (only if explicitly requested)
        if self.preferred_engine == "vibevoice":
            if self.vibevoice_manager.is_available():
                print("🔄 Attempting VibeVoice loading...")
                start_time = time.time()

                if self.vibevoice_manager.load_model():
                    load_time = time.time() - start_time

                    # Skip VibeVoice if it takes too long (>30 seconds)
                    if load_time > 30.0:
                        print(f"⚠️ VibeVoice loading too slow ({load_time:.1f}s), trying faster engines...")
                    else:
                        self.is_loaded = True
                        self.voice_enabled = True
                        self.preferred_engine = "vibevoice"
                        print(f"✅ VibeVoice engine loaded successfully ({load_time:.1f}s)")
                        self._configure_effects_pipeline()
                        return True
                else:
                    print("🔄 VibeVoice loading failed, trying fallback...")
            else:
                print("🔄 VibeVoice not available, trying fallback...")

        # Final fallback: Simple system TTS
        print("🔄 All advanced engines failed, falling back to system TTS.")
        if self.fallback_engine.load_model():
            self.is_loaded = True
            self.voice_enabled = True
            self.preferred_engine = "fallback"
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

            # CRITICAL FIX: Ensure fast-path also sanitizes text
            try:
                from src.utils.text_processors import sanitize_text_for_speech
                clean_text = sanitize_text_for_speech(text)
                if not clean_text:
                    clean_text = text
            except:
                clean_text = text

            # Generate audio directly without chunking
            raw_audio = self.kitten_manager.generate(clean_text, voice=self.current_voice)
            if raw_audio is None:
                print("⚠️ Fast-path TTS generation failed, falling back to simple engine")
                return self.fallback_engine.synthesize_and_play(clean_text, background=False)

            # AUDIO COMPLETION FIX: Apply completion engine to fast path too
            try:
                fixed_audio, fix_info = self.completion_engine.fix_audio(raw_audio, "fast_path")
                print(f"🔧 Fast-path audio completion: {fix_info}")
            except Exception as e:
                print(f"⚠️ Fast-path audio completion failed, using raw audio: {e}")
                fixed_audio = raw_audio

            # Add minimal pre-padding for clean playback
            pre_padding = np.zeros(int(0.02 * self.sample_rate), dtype=np.float32)  # 20ms
            processed_audio = np.concatenate([pre_padding, fixed_audio])
            
            # Apply effects pipeline if configured
            try:
                for effect in self.effects_pipeline:
                    processed_audio = effect.apply(processed_audio, self.sample_rate)
            except Exception as e:
                print(f"⚠️ Audio effects failed, using raw audio: {e}")
            
            # Play immediately using safe method
            self._safe_audio_play(processed_audio, self.sample_rate)
            
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

        # CRITICAL FIX: Always sanitize text before synthesis to prevent asterisk reading
        try:
            from src.utils.text_processors import sanitize_text_for_speech
            cleaned_text = sanitize_text_for_speech(text)

            if not cleaned_text or not cleaned_text.strip():
                print("⚠️ Text sanitization resulted in empty text")
                return False

            # Use cleaned text for synthesis
            synthesis_text = cleaned_text

        except ImportError:
            print("⚠️ Text sanitization not available, using raw text")
            synthesis_text = text
        except Exception as e:
            print(f"⚠️ Text sanitization failed: {e}, using raw text")
            synthesis_text = text

        try:
            if background:
                thread = threading.Thread(target=self._synthesis_worker, args=(synthesis_text,), daemon=True)
                thread.start()
                return True
            else:
                return self._synthesis_worker(synthesis_text)
        except Exception as e:
            print(f"❌ Failed to start synthesis: {e}")
            return False

    def _synthesis_worker(self, text: str):
        """The core synthesis and playback logic."""
        # Route to appropriate engine based on preference and availability
        if self.preferred_engine == "intelligent":
            # NEW: Use intelligent engine (automatically selects best engine and avoids truncation)
            return self.intelligent_engine.synthesize_and_play(text, background=False, quality=TTSQuality.BALANCED)
        elif self.preferred_engine == "piper" and self.piper_manager.is_available():
            return self._synthesis_with_piper(text)
        elif self.preferred_engine == "espeak" and self.espeak_manager.is_available():
            return self._synthesis_with_espeak(text)
        elif self.preferred_engine == "vibevoice" and self.vibevoice_manager.is_available():
            return self._synthesis_with_vibevoice(text)
        elif self.preferred_engine == "kitten" and self.kitten_manager.is_available():
            return self._synthesis_with_kitten(text)
        else:
            # Fallback to intelligent engine first, then simple engine
            try:
                return self.intelligent_engine.synthesize_and_play(text, background=False, quality=TTSQuality.ULTRA_FAST)
            except:
                return self.fallback_engine.synthesize_and_play(text, background=False)
    
    def _synthesis_with_piper(self, text: str):
        """Synthesis using Piper TTS engine - ultra-fast neural TTS"""
        try:
            import sounddevice as sd

            print("🎯 Using Piper TTS for real-time synthesis...")
            start_time = time.time()

            # Get current profile settings for Piper optimization
            profile = self.profiles.get(self.current_profile, {})
            speed = profile.get("speed", 1.0)

            # Set speed if profile specifies it
            if hasattr(self.piper_manager, 'set_speed'):
                self.piper_manager.set_speed(speed)

            # Generate audio using Piper
            raw_audio = self.piper_manager.generate(text)
            if raw_audio is None:
                print("⚠️ Piper TTS generation failed, falling back to next engine...")
                return self._synthesis_with_espeak(text)

            synthesis_time = time.time() - start_time
            duration = len(raw_audio) / self.piper_manager.sample_rate

            # Apply effects pipeline
            processed_audio = self._apply_effects_pipeline(raw_audio)

            # Add minimal padding for clean playback
            pre_padding = np.zeros(int(0.01 * self.piper_manager.sample_rate), dtype=np.float32)  # 10ms
            end_padding = np.zeros(int(0.05 * self.piper_manager.sample_rate), dtype=np.float32)  # 50ms
            processed_audio = np.concatenate([pre_padding, processed_audio, end_padding])

            # Play audio
            if self._safe_audio_play(processed_audio, self.piper_manager.sample_rate):
                print(f"✅ Piper TTS: {duration:.2f}s audio in {synthesis_time:.3f}s (RTF: {synthesis_time/duration:.2f}x)")
                return True
            else:
                print("⚠️ Audio playback failed, but synthesis completed successfully")
                return True

        except Exception as e:
            print(f"❌ Piper TTS synthesis failed: {e}")
            return self._synthesis_with_espeak(text)

    def _synthesis_with_espeak(self, text: str):
        """Synthesis using eSpeak engine - ultra-fast formant synthesis"""
        try:
            import sounddevice as sd

            print("⚡ Using eSpeak for ultra-fast synthesis...")
            start_time = time.time()

            # Get current profile settings for eSpeak optimization
            profile = self.profiles.get(self.current_profile, {})
            espeak_profile = profile.get("profile", "fast")

            # Set eSpeak performance profile
            if hasattr(self.espeak_manager, 'set_profile'):
                self.espeak_manager.set_profile(espeak_profile)

            # Generate audio using eSpeak
            raw_audio = self.espeak_manager.generate(text)
            if raw_audio is None:
                print("⚠️ eSpeak synthesis failed, falling back to KittenTTS...")
                return self._synthesis_with_kitten(text)

            synthesis_time = time.time() - start_time
            duration = len(raw_audio) / self.espeak_manager.sample_rate

            # Apply minimal effects pipeline (eSpeak profiles often skip effects for speed)
            processed_audio = raw_audio
            if self.effects_pipeline:  # Only apply effects if configured
                processed_audio = self._apply_effects_pipeline(raw_audio)

            # Minimal padding for eSpeak (optimized for maximum speed)
            pre_padding = np.zeros(int(0.005 * self.espeak_manager.sample_rate), dtype=np.float32)  # 5ms
            end_padding = np.zeros(int(0.03 * self.espeak_manager.sample_rate), dtype=np.float32)  # 30ms
            processed_audio = np.concatenate([pre_padding, processed_audio, end_padding])

            # Play audio
            if self._safe_audio_play(processed_audio, self.espeak_manager.sample_rate):
                print(f"⚡ eSpeak: {duration:.2f}s audio in {synthesis_time:.3f}s (RTF: {synthesis_time/duration:.2f}x)")
                return True
            else:
                print("⚠️ Audio playback failed, but synthesis completed successfully")
                return True

        except Exception as e:
            print(f"❌ eSpeak synthesis failed: {e}")
            return self._synthesis_with_kitten(text)

    def _synthesis_with_vibevoice(self, text: str):
        """Synthesis using VibeVoice engine"""
        try:
            import sounddevice as sd
            
            # Get current profile settings
            profile = self.profiles.get(self.current_profile, {})
            speakers = profile.get("speakers", ["Alice"])
            
            # Check if this should be long-form generation
            if len(text) > 1000 or self.current_profile == "narrative":
                print("🎬 Using VibeVoice long-form generation...")
                audio_chunks = self.vibevoice_manager.generate_long_form(text, speakers=speakers)
                if not audio_chunks:
                    print("⚠️ VibeVoice long-form generation failed, falling back...")
                    return self._synthesis_with_kitten(text)
                
                # Play chunks in sequence
                for i, chunk in enumerate(audio_chunks):
                    print(f"🗣️  Playing chunk {i+1}/{len(audio_chunks)}")
                    processed_audio = self._apply_effects_pipeline(chunk)
                    
                    # Try to play with error handling
                    if not self._safe_audio_play(processed_audio, self.vibevoice_manager.sample_rate):
                        print(f"⚠️ Audio playback failed for chunk {i+1}, but synthesis completed successfully")
                        return True  # Still consider it successful since audio was generated
                    
                return True
            else:
                # Standard generation
                raw_audio = self.vibevoice_manager.generate(text, speakers=speakers)
                if raw_audio is None:
                    print("⚠️ VibeVoice generation failed, falling back to KittenTTS...")
                    return self._synthesis_with_kitten(text)
                
                # Apply effects and play
                processed_audio = self._apply_effects_pipeline(raw_audio)
                
                # Try to play with error handling
                if self._safe_audio_play(processed_audio, self.vibevoice_manager.sample_rate):
                    return True
                else:
                    print("⚠️ Audio playback failed, but synthesis completed successfully")
                    return True  # Still consider successful since audio was generated
                
        except Exception as e:
            print(f"❌ VibeVoice synthesis failed: {e}")
            return self._synthesis_with_kitten(text)
    
    def _synthesis_with_kitten(self, text: str):
        """Synthesis using KittenTTS engine (original method)"""
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
                    # AUDIO COMPLETION FIX: Apply to ALL chunks to prevent any truncation
                    try:
                        fixed_audio, fix_info = self.completion_engine.fix_audio(raw_audio, f"chunk_{i+1}")
                        print(f"🔧 Chunk {i+1} audio completion: {fix_info}")
                        audio_chunks.append(fixed_audio)
                    except Exception as e:
                        print(f"⚠️ Audio completion failed for chunk {i+1}, using raw audio: {e}")
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
            
            # Start playback with enhanced completion verification using safe method (non-blocking)
            if not self._safe_audio_play(processed_audio, self.sample_rate, wait=False):
                print("⚠️ Audio playback failed, but continuing...")
                return True  # Continue even if playback failed
            
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
            from src.tts.effects.audio_effects import IntercomEffect, CompressionEffect, NormalizationEffect, FormantCorrectionEffect, ReverbEffect
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

                # Reverb effects with different presets
                elif effect_name == "reverb_room":
                    effect = ReverbEffect({"preset": "room"})
                    self.effects_pipeline.append(effect)

                elif effect_name == "reverb_hall":
                    effect = ReverbEffect({"preset": "hall"})
                    self.effects_pipeline.append(effect)

                elif effect_name == "reverb_cathedral":
                    effect = ReverbEffect({"preset": "cathedral"})
                    self.effects_pipeline.append(effect)

                elif effect_name == "reverb_studio":
                    effect = ReverbEffect({"preset": "studio"})
                    self.effects_pipeline.append(effect)

                elif effect_name == "reverb_intimate":
                    effect = ReverbEffect({"preset": "intimate"})
                    self.effects_pipeline.append(effect)

                # Generic reverb (defaults to room)
                elif effect_name == "reverb":
                    effect = ReverbEffect({"preset": "room"})
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
        status = {
            "available": self.voice_enabled,
            "loaded": self.is_loaded,
            "enabled": self.voice_enabled,
            "loading": False,
            "model": "UnifiedVoiceEngine",
            "current_profile": self.current_profile,
            "available_profiles": list(self.profiles.keys()),
            "preferred_engine": self.preferred_engine,
            "available_engines": ["piper", "espeak", "vibevoice", "kitten", "fallback"]
        }

        # Add engine availability status
        status["engines"] = {
            "piper": {
                "available": self.piper_manager.is_available(),
                "description": "Ultra-fast neural TTS (sub-50ms)",
                "info": self.piper_manager.get_availability_info() if hasattr(self.piper_manager, 'get_availability_info') else {}
            },
            "espeak": {
                "available": self.espeak_manager.is_available(),
                "description": "Ultra-fast formant synthesis (sub-10ms)",
                "info": self.espeak_manager.get_availability_info() if hasattr(self.espeak_manager, 'get_availability_info') else {}
            },
            "kitten": {
                "available": self.kitten_manager.is_available(),
                "description": "Lightweight neural TTS"
            },
            "vibevoice": {
                "available": self.vibevoice_manager.is_available(),
                "description": "Long-form multi-speaker TTS",
                "info": self.vibevoice_manager.get_availability_info()
            },
            "fallback": {
                "available": True,
                "description": "System TTS fallback"
            }
        }

        # Add current engine specific details
        if self.preferred_engine == "piper":
            status["current_engine_info"] = self.piper_manager.get_model_info() if hasattr(self.piper_manager, 'get_model_info') else {}
        elif self.preferred_engine == "espeak":
            status["current_engine_info"] = self.espeak_manager.get_model_info() if hasattr(self.espeak_manager, 'get_model_info') else {}
        elif self.preferred_engine == "vibevoice":
            status["current_engine_info"] = self.vibevoice_manager.get_model_info()
        elif self.preferred_engine == "kitten":
            status["current_engine_info"] = {"loaded": self.kitten_manager.is_available()}

        return status
    
    def _safe_audio_play(self, audio_data: np.ndarray, sample_rate: int, wait: bool = True) -> bool:
        """Safely play audio with error handling and fallbacks"""
        import sounddevice as sd
        import numpy as np
        import subprocess
        import platform
        import tempfile
        import wave
        
        try:
            # First, try the standard sounddevice approach
            sd.play(audio_data, samplerate=sample_rate)
            if wait:
                sd.wait()
            return True
            
        except Exception as e:
            print(f"🔧 Primary audio playback failed: {e}")
            print("🔄 Trying alternative audio playback methods...")
            
            # Fallback 1: Try different audio device
            try:
                # Get available audio devices
                devices = sd.query_devices()
                output_devices = [d for d in devices if d['max_output_channels'] > 0]
                
                if output_devices:
                    # Try the default output device explicitly
                    default_device = sd.default.device[1]  # Output device
                    print(f"🔧 Trying default output device: {default_device}")
                    
                    sd.play(audio_data, samplerate=sample_rate, device=default_device)
                    if wait:
                        sd.wait()
                    return True
                    
            except Exception as e2:
                print(f"🔧 Device-specific playback failed: {e2}")
            
            # Fallback 2: Save to file and play with system player
            try:
                print("🔧 Trying system audio player fallback...")
                
                # Create temporary WAV file
                with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as temp_file:
                    temp_path = temp_file.name
                
                # Ensure audio is in correct format
                if audio_data.dtype != np.int16:
                    # Convert to 16-bit PCM
                    if audio_data.dtype == np.float32 or audio_data.dtype == np.float16:
                        # Convert float to int16
                        audio_int16 = (audio_data * 32767).astype(np.int16)
                    else:
                        audio_int16 = audio_data.astype(np.int16)
                else:
                    audio_int16 = audio_data
                
                # Handle multi-dimensional arrays
                if len(audio_int16.shape) > 1:
                    if audio_int16.shape[0] == 1:
                        audio_int16 = audio_int16[0]  # Remove batch dimension
                    elif audio_int16.shape[1] == 1:
                        audio_int16 = audio_int16[:, 0]  # Remove channel dimension
                
                # Save as WAV file
                with wave.open(temp_path, 'w') as wav_file:
                    wav_file.setnchannels(1)  # Mono
                    wav_file.setsampwidth(2)  # 16-bit
                    wav_file.setframerate(sample_rate)
                    wav_file.writeframes(audio_int16.tobytes())
                
                # Play with system player
                system = platform.system()
                if system == "Darwin":  # macOS
                    subprocess.run(["afplay", temp_path], check=True, 
                                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                elif system == "Windows":
                    subprocess.run(["powershell", "-c", f"(New-Object Media.SoundPlayer '{temp_path}').PlaySync()"], 
                                 check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                else:  # Linux
                    players = ["paplay", "aplay", "play"]
                    for player in players:
                        try:
                            subprocess.run([player, temp_path], check=True,
                                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                            break
                        except FileNotFoundError:
                            continue
                    else:
                        raise Exception("No audio player found")
                
                # Clean up temp file
                try:
                    import os
                    os.unlink(temp_path)
                except:
                    pass
                
                print("✅ System audio player successful")
                return True
                
            except Exception as e3:
                print(f"🔧 System player fallback failed: {e3}")
            
            # If all methods fail, report but don't crash
            print("❌ All audio playback methods failed")
            print("💡 Audio was generated successfully but cannot be played")
            print("💡 This might be due to audio device configuration issues")
            return False
            
    def _apply_effects_pipeline(self, audio_data: np.ndarray) -> np.ndarray:
        """Apply the current effects pipeline to audio data"""
        try:
            processed_audio = audio_data.copy()
            for effect in self.effects_pipeline:
                processed_audio = effect.apply(processed_audio, self.sample_rate)
            return processed_audio
        except Exception as e:
            print(f"⚠️ Effects pipeline failed: {e}")
            return audio_data  # Return original audio if effects fail
    
    def set_engine_preference(self, engine: str) -> bool:
        """Set the preferred TTS engine"""
        valid_engines = ["intelligent", "piper", "espeak", "vibevoice", "kitten", "fallback"]
        if engine in valid_engines:
            self.preferred_engine = engine
            print(f"🔄 TTS engine preference set to: {engine}")

            # Update current profile to match engine if needed (except for intelligent engine)
            if engine != "intelligent":
                profile = self.profiles.get(self.current_profile, {})
                if profile.get("preferred_engine") != engine:
                    # Find a suitable profile for this engine
                    for name, prof in self.profiles.items():
                        if prof.get("preferred_engine") == engine:
                            self.set_profile(name)
                            break

            return True
        else:
            print(f"❌ Invalid engine. Valid options: {valid_engines}")
            return False

    # Real-time processing methods for chatbot optimization
    def enable_realtime_mode(self, audio_callback=None):
        """Enable real-time processing mode for conversational chatbots."""
        if self.realtime_mode:
            print("⚡ Real-time mode already enabled")
            return True

        if not self.is_loaded:
            print("⚠️ Cannot enable real-time mode: voice engine not loaded")
            return False

        # Initialize streaming processor
        current_tts = self._get_current_tts_engine()
        if not current_tts:
            print("⚠️ No TTS engine available for real-time mode")
            return False

        # Create streaming processor with optimized TTS wrapper
        self.streaming_processor = StreamingTextProcessor(current_tts, buffer_size=5)
        self.conversation_optimizer = ConversationFlowOptimizer(self.streaming_processor)

        # Set up audio callback
        if audio_callback:
            self.streaming_processor.set_audio_callback(audio_callback)
        else:
            # Default callback that plays audio immediately
            def default_callback(audio_data):
                self._safe_audio_play(audio_data, self.sample_rate, wait=False)
            self.streaming_processor.set_audio_callback(default_callback)

        # Start processing
        self.streaming_processor.start_processing()
        self.realtime_mode = True

        print("⚡ Real-time processing mode enabled for conversational chatbot")
        return True

    def disable_realtime_mode(self):
        """Disable real-time processing mode."""
        if not self.realtime_mode:
            return

        if self.streaming_processor:
            self.streaming_processor.stop_processing()
            self.streaming_processor = None

        if self.conversation_optimizer:
            self.conversation_optimizer = None

        self.realtime_mode = False
        print("⏹️  Real-time processing mode disabled")

    def speak_realtime(self, text: str, priority: bool = False):
        """Speak text using real-time processing (non-blocking)."""
        if not self.realtime_mode or not self.streaming_processor:
            print("⚠️ Real-time mode not enabled. Use enable_realtime_mode() first.")
            return False

        if not text or not text.strip():
            return False

        self.streaming_processor.add_text(text, priority=priority)
        return True

    def start_conversation_response(self, full_response: str, stream_chunks: bool = True):
        """Start a conversational response with flow optimization."""
        if not self.realtime_mode or not self.conversation_optimizer:
            # Fallback to regular synthesis
            return self.synthesize_and_play(full_response)

        self.conversation_optimizer.start_response(full_response, stream_chunks=stream_chunks)
        return True

    def interrupt_conversation(self):
        """Interrupt current conversation for user input."""
        if self.realtime_mode and self.conversation_optimizer:
            self.conversation_optimizer.interrupt_for_user()

    def _get_current_tts_engine(self):
        """Get the current TTS engine instance for real-time processing."""
        if self.preferred_engine == "kitten" and self.kitten_manager.is_available():
            return self.kitten_manager
        elif self.preferred_engine == "piper" and self.piper_manager.is_available():
            return self.piper_manager
        elif self.preferred_engine == "espeak" and self.espeak_manager.is_available():
            return self.espeak_manager
        elif self.preferred_engine == "vibevoice" and self.vibevoice_manager.is_available():
            return self.vibevoice_manager
        else:
            # Try fallback order
            engines = [
                (self.kitten_manager, "kitten"),
                (self.piper_manager, "piper"),
                (self.espeak_manager, "espeak"),
                (self.vibevoice_manager, "vibevoice")
            ]

            for engine, name in engines:
                if hasattr(engine, 'is_available') and engine.is_available():
                    print(f"🔄 Using {name} engine for real-time processing")
                    return engine

            return None

    def get_realtime_status(self):
        """Get real-time processing status and metrics."""
        if not self.realtime_mode:
            return {"realtime_mode": False}

        status = {
            "realtime_mode": True,
            "current_engine": self.preferred_engine
        }

        if self.streaming_processor:
            status["processor_metrics"] = self.streaming_processor.get_metrics()

        if self.conversation_optimizer:
            status["conversation_stats"] = self.conversation_optimizer.get_conversation_stats()

        return status

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
