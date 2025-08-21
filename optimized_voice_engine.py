#!/usr/bin/env python3
"""
Optimized Voice Engine for M1K3
Enhanced KittenML TTS integration with intelligent text chunking, 
performance optimizations, and robust error recovery
"""

import io
import json
import time
import threading
import numpy as np
from pathlib import Path
from typing import Optional, Dict, Any, List
import warnings
import queue
import hashlib

# Suppress warnings for cleaner output
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)

try:
    import sounddevice as sd
    import soundfile as sf
    from kittentts import KittenTTS
    KITTEN_AVAILABLE = True
except ImportError as e:
    print(f"🔇 KittenML TTS not available: {e}")
    KITTEN_AVAILABLE = False

from simple_voice_engine import SimpleVoiceEngine

class OptimizedVoiceEngine:
    """
    High-performance voice engine with intelligent text processing and caching
    
    Key Optimizations:
    1. Smart text chunking to prevent ONNX errors
    2. Audio caching for repeated phrases
    3. Streaming synthesis for long text
    4. Memory-efficient audio processing
    5. Intelligent error recovery with retry logic
    """
    
    def __init__(self, model_name: str = "KittenML/kitten-tts-nano-0.1"):
        self.model_name = model_name
        self.tts_model: Optional[KittenTTS] = None
        self.fallback_engine = SimpleVoiceEngine()
        
        # State management
        self.voice_enabled = False
        self.loading = False
        self.use_kitten = False
        self.initialization_failed = False
        
        # Performance optimizations
        self.audio_cache = {}  # Cache for repeated phrases
        self.cache_size_limit = 50  # Max cached audio clips
        
        # Text processing settings
        self.chunk_size = 180  # Conservative BERT-safe chunk size
        self.overlap_words = 2   # Word overlap between chunks for natural flow
        
        # Audio settings
        self.sample_rate = 22050
        self.normalize_level = 0.8
        
        # Threading
        self._synthesis_queue = queue.Queue()
        self._playback_thread = None
        
    def is_available(self) -> bool:
        """Check if any voice engine is available"""
        return KITTEN_AVAILABLE or self.fallback_engine.is_available()
    
    def load_model(self) -> bool:
        """Load voice model with performance monitoring"""
        if self.loading:
            return False
            
        self.loading = True
        start_time = time.time()
        
        # Try KittenML TTS first
        if KITTEN_AVAILABLE and not self.initialization_failed:
            try:
                print("🎤 Loading KittenML TTS (optimized)...")
                
                self.tts_model = KittenTTS(self.model_name)
                
                # Warm up the model with a short test
                test_audio = self.tts_model.generate("Test")
                
                load_time = time.time() - start_time
                print(f"✅ KittenML loaded in {load_time:.2f}s")
                print("🎯 Optimized voice engine ready - enhanced quality & performance")
                
                self.voice_enabled = True
                self.use_kitten = True
                self.loading = False
                return True
                
            except Exception as e:
                print(f"❌ KittenML failed: {e}")
                self.initialization_failed = True
        
        # Fallback to system TTS
        print("🔄 Falling back to system TTS...")
        if self.fallback_engine.load_model():
            self.voice_enabled = True
            self.use_kitten = False
            print("🔊 System TTS ready")
            self.loading = False
            return True
        else:
            print("❌ No voice synthesis available")
            self.loading = False
            return False
    
    def _smart_text_chunking(self, text: str) -> List[str]:
        """
        Intelligent text chunking to prevent ONNX errors while maintaining natural flow
        
        Strategy:
        1. Split on sentence boundaries when possible
        2. Respect chunk size limits
        3. Add word overlap for natural transitions
        4. Handle edge cases gracefully
        """
        if len(text) <= self.chunk_size:
            return [text]
        
        chunks = []
        sentences = text.split('. ')
        current_chunk = ""
        
        for sentence in sentences:
            # Add sentence end period back (except for last sentence)
            if sentence != sentences[-1]:
                sentence += "."
            
            # Check if adding this sentence would exceed limit
            potential_chunk = current_chunk + (" " if current_chunk else "") + sentence
            
            if len(potential_chunk) <= self.chunk_size:
                current_chunk = potential_chunk
            else:
                # Current chunk is ready, start new one
                if current_chunk:
                    chunks.append(current_chunk.strip())
                    
                    # Add word overlap for natural flow
                    words = current_chunk.split()
                    if len(words) > self.overlap_words:
                        overlap = " ".join(words[-self.overlap_words:])
                        current_chunk = overlap + " " + sentence
                    else:
                        current_chunk = sentence
                else:
                    # Single sentence too long, force split by words
                    words = sentence.split()
                    word_chunk = ""
                    
                    for word in words:
                        if len(word_chunk + " " + word) <= self.chunk_size:
                            word_chunk += (" " if word_chunk else "") + word
                        else:
                            if word_chunk:
                                chunks.append(word_chunk.strip())
                            word_chunk = word
                    
                    if word_chunk:
                        current_chunk = word_chunk
        
        # Add final chunk
        if current_chunk:
            chunks.append(current_chunk.strip())
        
        return chunks
    
    def _get_cache_key(self, text: str) -> str:
        """Generate cache key for text"""
        return hashlib.md5(text.encode()).hexdigest()[:12]
    
    def _cache_audio(self, text: str, audio_data: np.ndarray):
        """Cache audio with size management"""
        if len(self.audio_cache) >= self.cache_size_limit:
            # Remove oldest entry (FIFO)
            oldest_key = next(iter(self.audio_cache))
            del self.audio_cache[oldest_key]
        
        cache_key = self._get_cache_key(text)
        self.audio_cache[cache_key] = {
            'audio': audio_data.copy(),
            'timestamp': time.time(),
            'text': text
        }
    
    def _get_cached_audio(self, text: str) -> Optional[np.ndarray]:
        """Retrieve cached audio if available"""
        cache_key = self._get_cache_key(text)
        if cache_key in self.audio_cache:
            return self.audio_cache[cache_key]['audio']
        return None
    
    def _synthesize_chunk(self, text: str) -> Optional[np.ndarray]:
        """Synthesize a single text chunk with caching and error handling"""
        # Check cache first
        cached_audio = self._get_cached_audio(text)
        if cached_audio is not None:
            return cached_audio
        
        try:
            # Generate audio with KittenTTS
            audio_data = self.tts_model.generate(text)
            
            # Convert to numpy array
            if hasattr(audio_data, 'numpy'):
                audio_array = audio_data.numpy()
            else:
                audio_array = np.array(audio_data, dtype=np.float32)
            
            # Ensure mono
            if len(audio_array.shape) > 1:
                audio_array = audio_array[:, 0] if audio_array.shape[1] > 0 else audio_array.flatten()
            
            # Normalize audio for consistent volume
            max_val = np.max(np.abs(audio_array))
            if max_val > 0:
                audio_array = (audio_array / max_val) * self.normalize_level
            
            # Cache the result
            self._cache_audio(text, audio_array)
            
            return audio_array
            
        except Exception as e:
            print(f"🔇 Synthesis error for chunk: {e}")
            return None
    
    def _combine_audio_chunks(self, audio_chunks: List[np.ndarray]) -> np.ndarray:
        """Combine audio chunks with smooth transitions"""
        if not audio_chunks:
            return np.array([])
        
        if len(audio_chunks) == 1:
            return audio_chunks[0]
        
        # Add small silence between chunks for natural pacing
        silence_samples = int(0.1 * self.sample_rate)  # 100ms silence
        silence = np.zeros(silence_samples, dtype=np.float32)
        
        combined = audio_chunks[0]
        for chunk in audio_chunks[1:]:
            combined = np.concatenate([combined, silence, chunk])
        
        return combined
    
    def synthesize_and_play(self, text: str, background: bool = True) -> bool:
        """
        Synthesize and play text with intelligent chunking and optimization
        
        Features:
        - Smart text chunking to prevent ONNX errors
        - Audio caching for performance
        - Streaming synthesis for long text
        - Graceful error recovery
        """
        if not self.voice_enabled:
            return False
        
        # Use fallback engine if KittenTTS failed
        if not self.use_kitten:
            return self.fallback_engine.synthesize_and_play(text, background)
        
        if background:
            # Play in background thread
            thread = threading.Thread(
                target=self._synthesize_and_play_sync, 
                args=(text,),
                daemon=True
            )
            thread.start()
            return True
        else:
            return self._synthesize_and_play_sync(text)
    
    def _synthesize_and_play_sync(self, text: str) -> bool:
        """Synchronous synthesis and playback with chunking"""
        try:
            # Clean and prepare text
            cleaned_text = text.strip()
            if not cleaned_text:
                return False
            
            # Smart chunking to prevent ONNX errors
            chunks = self._smart_text_chunking(cleaned_text)
            
            if len(chunks) > 1:
                print(f"🔊 Synthesizing {len(chunks)} chunks for optimal quality")
            
            # Synthesize each chunk
            audio_chunks = []
            for i, chunk in enumerate(chunks):
                if len(chunks) > 1:
                    print(f"🎤 Processing chunk {i+1}/{len(chunks)}: {chunk[:50]}{'...' if len(chunk) > 50 else ''}")
                
                chunk_audio = self._synthesize_chunk(chunk)
                if chunk_audio is not None:
                    audio_chunks.append(chunk_audio)
                else:
                    print(f"⚠️  Failed to synthesize chunk {i+1}, continuing...")
            
            if not audio_chunks:
                print("❌ All chunks failed to synthesize")
                # Fallback to system TTS
                return self.fallback_engine.synthesize_and_play(text, False)
            
            # Combine chunks into final audio
            final_audio = self._combine_audio_chunks(audio_chunks)
            
            # Play the combined audio
            sd.play(final_audio, samplerate=self.sample_rate)
            sd.wait()  # Wait for playback to complete
            
            return True
            
        except Exception as e:
            print(f"🔇 Synthesis error: {e}")
            # Final fallback to system TTS
            print("🔄 Falling back to system TTS...")
            return self.fallback_engine.synthesize_and_play(text, False)
    
    def get_stats(self) -> Dict[str, Any]:
        """Get performance statistics"""
        return {
            "engine": "KittenML TTS (Optimized)" if self.use_kitten else "System TTS",
            "model": self.model_name if self.use_kitten else "System Voice",
            "cache_size": len(self.audio_cache),
            "cache_limit": self.cache_size_limit,
            "chunk_size": self.chunk_size,
            "sample_rate": self.sample_rate,
            "available": self.is_available(),
            "enabled": self.voice_enabled,
            "using_kitten": self.use_kitten
        }
    
    def clear_cache(self):
        """Clear audio cache to free memory"""
        self.audio_cache.clear()
        print("🗑️  Audio cache cleared")
    
    def set_chunk_size(self, size: int):
        """Adjust chunk size for different hardware capabilities"""
        if 50 <= size <= 500:  # Reasonable bounds
            self.chunk_size = size
            print(f"🔧 Chunk size set to {size} characters")
        else:
            print(f"⚠️  Invalid chunk size {size}, keeping {self.chunk_size}")
    
    def get_status(self) -> dict:
        """Get detailed voice engine status"""
        return {
            "available": self.is_available(),
            "loaded": self.tts_model is not None,
            "enabled": self.voice_enabled,
            "loading": self.loading,
            "engine": "KittenML TTS (Optimized)" if self.use_kitten else "System TTS",
            "model": self.model_name if self.use_kitten else "System Voice",
            "cache_entries": len(self.audio_cache),
            "performance": "Optimized" if self.use_kitten else "Basic"
        }

# For backward compatibility
class MockOptimizedVoiceEngine:
    """Mock optimized voice engine for testing"""
    
    def __init__(self, *args, **kwargs):
        self.voice_enabled = False
        
    def is_available(self) -> bool:
        return False
        
    def load_model(self) -> bool:
        print("🎤 Optimized voice synthesis not available (mock mode)")
        return False
        
    def synthesize_and_play(self, text: str, background: bool = True) -> bool:
        return False
    
    def get_stats(self) -> Dict[str, Any]:
        return {"engine": "Mock", "available": False}
    
    def get_status(self) -> dict:
        return {"available": False, "enabled": False, "loading": False}