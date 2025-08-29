#!/usr/bin/env python3
"""
Streaming TTS Engine - Real-time text-to-speech synthesis for M1K3
Processes AI response tokens as they arrive for natural conversation flow
"""

import time
import threading
import queue
from typing import Optional, Callable, Generator, List, Dict, Any
from dataclasses import dataclass
from enum import Enum
import re

# Add src to path for imports
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '../../..'))

from streaming_response_filter import create_streaming_filter


class StreamingState(Enum):
    """Streaming TTS states"""
    IDLE = "idle"
    BUFFERING = "buffering" 
    SYNTHESIZING = "synthesizing"
    SPEAKING = "speaking"
    PAUSED = "paused"
    ERROR = "error"


@dataclass
class SpeechChunk:
    """Individual speech synthesis chunk"""
    text: str
    audio_data: Optional[bytes] = None
    chunk_id: int = 0
    is_complete: bool = False
    timestamp: float = 0.0
    voice_profile: Optional[str] = None


class StreamingTTSEngine:
    """
    Real-time streaming TTS engine that synthesizes speech as tokens arrive
    Provides natural conversation flow with minimal latency
    """
    
    def __init__(self, voice_engine=None, chunk_size: int = 20, 
                 chunk_timeout: float = 0.5, buffer_size: int = 100):
        """
        Initialize streaming TTS engine
        
        Args:
            voice_engine: Underlying voice engine (UnifiedVoiceEngine, etc.)
            chunk_size: Number of words/tokens to buffer before synthesis
            chunk_timeout: Max time to wait before synthesizing partial chunk
            buffer_size: Maximum chunks to keep in synthesis queue
        """
        self.voice_engine = voice_engine
        self.chunk_size = chunk_size
        self.chunk_timeout = chunk_timeout
        self.buffer_size = buffer_size
        
        # VibeVoice-specific settings
        self.vibevoice_mode = False  # Enable for long-form content
        self.long_form_threshold = 1000  # Characters threshold for long-form mode
        self.continuous_mode = False  # For 90-minute continuous synthesis
        
        # Streaming state
        self.state = StreamingState.IDLE
        self.is_streaming = False
        self.chunk_counter = 0
        
        # Token and text processing
        self.token_buffer = []
        self.sentence_buffer = ""
        self.last_chunk_time = 0.0
        
        # Threading and queues
        self.synthesis_queue = queue.Queue(maxsize=buffer_size)
        self.audio_queue = queue.Queue(maxsize=buffer_size * 2)
        self.synthesis_thread: Optional[threading.Thread] = None
        self.playback_thread: Optional[threading.Thread] = None
        self.stop_event = threading.Event()
        
        # Callbacks
        self.on_chunk_ready: Optional[Callable[[SpeechChunk], None]] = None
        self.on_streaming_start: Optional[Callable] = None
        self.on_streaming_complete: Optional[Callable] = None
        self.on_error: Optional[Callable[[str], None]] = None
        
        # Sentence detection patterns
        self.sentence_endings = re.compile(r'[.!?]+\s*')
        self.clause_boundaries = re.compile(r'[,;:\-—]+\s*')
        
        # Statistics
        self.stats = {
            'tokens_processed': 0,
            'chunks_synthesized': 0,
            'average_latency': 0.0,
            'total_synthesis_time': 0.0
        }
        
        # Initialize response filter for clean token processing
        self.response_filter = create_streaming_filter(simple_mode=True)
    
    def start_streaming(self) -> bool:
        """Start streaming TTS mode"""
        if self.is_streaming:
            return True
            
        if not self.voice_engine:
            print("❌ No voice engine available for streaming TTS")
            return False
        
        try:
            print("🎤 Starting streaming TTS engine...")
            
            # Reset state
            self.state = StreamingState.IDLE
            self.is_streaming = True
            self.stop_event.clear()
            self.chunk_counter = 0
            
            # Clear buffers
            self.token_buffer = []
            self.sentence_buffer = ""
            self.last_chunk_time = time.time()
            
            # Clear queues
            while not self.synthesis_queue.empty():
                try:
                    self.synthesis_queue.get_nowait()
                except queue.Empty:
                    break
            
            while not self.audio_queue.empty():
                try:
                    self.audio_queue.get_nowait()
                except queue.Empty:
                    break
            
            # Start worker threads
            self.synthesis_thread = threading.Thread(
                target=self._synthesis_worker,
                name="StreamingTTS-Synthesis",
                daemon=True
            )
            self.synthesis_thread.start()
            
            self.playback_thread = threading.Thread(
                target=self._playback_worker,
                name="StreamingTTS-Playback",
                daemon=True
            )
            self.playback_thread.start()
            
            print("✅ Streaming TTS engine started")
            if self.on_streaming_start:
                self.on_streaming_start()
                
            return True
            
        except Exception as e:
            print(f"❌ Failed to start streaming TTS: {e}")
            if self.on_error:
                self.on_error(f"Streaming start failed: {e}")
            return False
    
    def stop_streaming(self) -> bool:
        """Stop streaming TTS mode"""
        if not self.is_streaming:
            return True
        
        try:
            print("🛑 Stopping streaming TTS engine...")
            
            # Signal stop
            self.stop_event.set()
            self.is_streaming = False
            self.state = StreamingState.IDLE
            
            # Wait for threads to complete (with timeout)
            if self.synthesis_thread and self.synthesis_thread.is_alive():
                self.synthesis_thread.join(timeout=2.0)
            
            if self.playback_thread and self.playback_thread.is_alive():
                self.playback_thread.join(timeout=2.0)
            
            # Flush any remaining content
            self._flush_remaining_content()
            
            print("✅ Streaming TTS engine stopped")
            if self.on_streaming_complete:
                self.on_streaming_complete()
                
            return True
            
        except Exception as e:
            print(f"❌ Error stopping streaming TTS: {e}")
            return False
    
    def process_token_stream(self, tokens: Generator[str, None, None]) -> Generator[SpeechChunk, None, None]:
        """
        Process streaming tokens and yield speech chunks as they become ready
        
        Args:
            tokens: Generator of text tokens from AI response
            
        Yields:
            SpeechChunk: Audio chunks ready for playback
        """
        if not self.is_streaming:
            if not self.start_streaming():
                return
        
        self.state = StreamingState.BUFFERING
        
        try:
            # Filter tokens through response filter first
            filtered_tokens = self.response_filter.filter_tokens(tokens)
            
            for token in filtered_tokens:
                if self.stop_event.is_set():
                    break
                
                # Process the token
                yield from self._process_token(token)
                
                # Check if we should flush based on timing
                current_time = time.time()
                if (current_time - self.last_chunk_time) > self.chunk_timeout:
                    yield from self._flush_current_chunk()
            
            # Final flush when stream ends
            yield from self._flush_remaining_content()
            
        except Exception as e:
            print(f"❌ Error processing token stream: {e}")
            if self.on_error:
                self.on_error(f"Token processing error: {e}")
        finally:
            self.stop_streaming()
    
    def add_text_chunk(self, text: str) -> None:
        """
        Add a text chunk directly for synthesis (non-streaming mode)
        
        Args:
            text: Text to synthesize
        """
        if not self.is_streaming:
            if not self.start_streaming():
                return
        
        try:
            # Process as if it came from token stream
            for token in text.split():
                list(self._process_token(token))
            
            # Flush immediately for direct text input
            list(self._flush_current_chunk())
            
        except Exception as e:
            print(f"❌ Error adding text chunk: {e}")
            if self.on_error:
                self.on_error(f"Text chunk error: {e}")
    
    def _process_token(self, token: str) -> Generator[SpeechChunk, None, None]:
        """Process a single token and potentially yield speech chunks"""
        self.stats['tokens_processed'] += 1
        self.token_buffer.append(token)
        self.sentence_buffer += token + " "
        
        # Check for sentence completion
        if self._is_sentence_complete(self.sentence_buffer):
            yield from self._flush_current_chunk()
        # Check for clause boundaries (for more natural pauses)
        elif self._has_clause_boundary(token) and len(self.token_buffer) >= self.chunk_size // 2:
            yield from self._flush_current_chunk()
        # Check if buffer is full
        elif len(self.token_buffer) >= self.chunk_size:
            yield from self._flush_current_chunk()
    
    def _is_sentence_complete(self, text: str) -> bool:
        """Check if text contains a complete sentence"""
        return bool(self.sentence_endings.search(text.strip()))
    
    def _has_clause_boundary(self, token: str) -> bool:
        """Check if token contains a clause boundary"""
        return bool(self.clause_boundaries.search(token))
    
    def _flush_current_chunk(self) -> Generator[SpeechChunk, None, None]:
        """Flush current buffer as a speech chunk"""
        if not self.token_buffer:
            return
        
        try:
            # Create chunk text
            chunk_text = " ".join(self.token_buffer).strip()
            if not chunk_text:
                return
            
            # Create speech chunk
            chunk = SpeechChunk(
                text=chunk_text,
                chunk_id=self.chunk_counter,
                timestamp=time.time(),
                voice_profile=getattr(self.voice_engine, 'current_profile', None)
            )
            
            self.chunk_counter += 1
            
            # Add to synthesis queue (non-blocking)
            try:
                self.synthesis_queue.put_nowait(chunk)
                print(f"📝 Queued chunk #{chunk.chunk_id}: '{chunk.text[:30]}...'")
            except queue.Full:
                print("⚠️ Synthesis queue full, dropping chunk")
            
            # Clear buffers
            self.token_buffer = []
            self.sentence_buffer = ""
            self.last_chunk_time = time.time()
            
            # Yield the chunk (it will be synthesized asynchronously)
            yield chunk
            
        except Exception as e:
            print(f"❌ Error flushing chunk: {e}")
    
    def _flush_remaining_content(self) -> Generator[SpeechChunk, None, None]:
        """Flush any remaining content in buffers"""
        if self.token_buffer:
            yield from self._flush_current_chunk()
        
        # Mark final chunk as complete
        if self.chunk_counter > 0:
            try:
                final_marker = SpeechChunk(
                    text="",
                    chunk_id=self.chunk_counter,
                    is_complete=True,
                    timestamp=time.time()
                )
                self.synthesis_queue.put_nowait(final_marker)
                yield final_marker
            except queue.Full:
                pass
    
    def _synthesis_worker(self):
        """Background thread for synthesizing speech chunks"""
        print("🔄 Synthesis worker started")
        
        while not self.stop_event.is_set() or not self.synthesis_queue.empty():
            try:
                # Get chunk from queue with timeout
                try:
                    chunk = self.synthesis_queue.get(timeout=0.1)
                except queue.Empty:
                    continue
                
                if chunk.is_complete:
                    print("🏁 Final chunk marker received")
                    break
                
                # Synthesize audio
                synthesis_start = time.time()
                self.state = StreamingState.SYNTHESIZING
                
                print(f"🎵 Synthesizing chunk #{chunk.chunk_id}: '{chunk.text[:50]}...'")
                
                if self.voice_engine:
                    try:
                        # Use voice engine to synthesize
                        audio_data = self.voice_engine.synthesize_speech(chunk.text)
                        chunk.audio_data = audio_data
                        
                        synthesis_time = time.time() - synthesis_start
                        self.stats['total_synthesis_time'] += synthesis_time
                        self.stats['chunks_synthesized'] += 1
                        
                        # Calculate average latency
                        self.stats['average_latency'] = (
                            self.stats['total_synthesis_time'] / 
                            max(1, self.stats['chunks_synthesized'])
                        )
                        
                        print(f"✅ Synthesized chunk #{chunk.chunk_id} in {synthesis_time:.2f}s")
                        
                        # Add to playback queue
                        try:
                            self.audio_queue.put_nowait(chunk)
                        except queue.Full:
                            print("⚠️ Audio queue full, dropping synthesized chunk")
                        
                        # Callback notification
                        if self.on_chunk_ready:
                            self.on_chunk_ready(chunk)
                            
                    except Exception as e:
                        print(f"❌ Synthesis failed for chunk #{chunk.chunk_id}: {e}")
                        
                else:
                    print(f"⚠️ No voice engine available for chunk #{chunk.chunk_id}")
                
            except Exception as e:
                print(f"❌ Synthesis worker error: {e}")
                if self.on_error:
                    self.on_error(f"Synthesis error: {e}")
        
        print("🔄 Synthesis worker stopped")
    
    def _playback_worker(self):
        """Background thread for playing back synthesized audio"""
        print("🔊 Playback worker started")
        
        while not self.stop_event.is_set() or not self.audio_queue.empty():
            try:
                # Get audio chunk from queue with timeout
                try:
                    chunk = self.audio_queue.get(timeout=0.1)
                except queue.Empty:
                    continue
                
                if not chunk.audio_data:
                    continue
                
                # Play the audio
                self.state = StreamingState.SPEAKING
                
                print(f"🔊 Playing chunk #{chunk.chunk_id}")
                
                if self.voice_engine and hasattr(self.voice_engine, 'play_audio'):
                    try:
                        self.voice_engine.play_audio(chunk.audio_data)
                        print(f"✅ Played chunk #{chunk.chunk_id}")
                    except Exception as e:
                        print(f"❌ Playback failed for chunk #{chunk.chunk_id}: {e}")
                else:
                    # Simulate playback time
                    estimated_duration = len(chunk.text) * 0.1  # ~100ms per character
                    time.sleep(estimated_duration)
                    print(f"🔊 Simulated playback of chunk #{chunk.chunk_id}")
                
            except Exception as e:
                print(f"❌ Playback worker error: {e}")
                if self.on_error:
                    self.on_error(f"Playback error: {e}")
        
        self.state = StreamingState.IDLE
        print("🔊 Playback worker stopped")
    
    def pause_streaming(self) -> bool:
        """Pause streaming TTS (useful for interruptions)"""
        if self.state == StreamingState.SPEAKING:
            self.state = StreamingState.PAUSED
            print("⏸️ Streaming TTS paused")
            return True
        return False
    
    def resume_streaming(self) -> bool:
        """Resume paused streaming TTS"""
        if self.state == StreamingState.PAUSED:
            self.state = StreamingState.SPEAKING
            print("▶️ Streaming TTS resumed")
            return True
        return False
    
    def get_stats(self) -> Dict[str, Any]:
        """Get streaming TTS statistics"""
        return {
            'state': self.state.value,
            'is_streaming': self.is_streaming,
            'tokens_processed': self.stats['tokens_processed'],
            'chunks_synthesized': self.stats['chunks_synthesized'],
            'average_latency': self.stats['average_latency'],
            'total_synthesis_time': self.stats['total_synthesis_time'],
            'synthesis_queue_size': self.synthesis_queue.qsize(),
            'audio_queue_size': self.audio_queue.qsize(),
            'chunk_size': self.chunk_size,
            'chunk_timeout': self.chunk_timeout
        }
    
    def set_voice_profile(self, profile: str) -> bool:
        """Set voice profile for streaming synthesis"""
        if self.voice_engine and hasattr(self.voice_engine, 'set_voice_profile'):
            return self.voice_engine.set_voice_profile(profile)
        return False
    
    def cleanup(self):
        """Clean up streaming TTS resources"""
        self.stop_streaming()
        
        # Clear queues
        while not self.synthesis_queue.empty():
            try:
                self.synthesis_queue.get_nowait()
            except queue.Empty:
                break
        
        while not self.audio_queue.empty():
            try:
                self.audio_queue.get_nowait()
            except queue.Empty:
                break
        
        print("🧹 Streaming TTS cleaned up")
    
    def enable_vibevoice_mode(self, continuous: bool = False):
        """Enable VibeVoice mode for long-form synthesis"""
        self.vibevoice_mode = True
        self.continuous_mode = continuous
        
        if continuous:
            # Adjust settings for 90-minute continuous synthesis
            self.chunk_size = 100  # Larger chunks for efficiency
            self.chunk_timeout = 2.0  # Longer timeout for quality
            self.buffer_size = 50  # Smaller buffer to manage memory
            print("🎬 VibeVoice continuous mode enabled (up to 90 minutes)")
        else:
            # Standard long-form mode
            self.chunk_size = 50
            self.chunk_timeout = 1.0
            print("📚 VibeVoice long-form mode enabled")
    
    def disable_vibevoice_mode(self):
        """Disable VibeVoice mode and return to standard streaming"""
        self.vibevoice_mode = False
        self.continuous_mode = False
        
        # Reset to standard streaming settings
        self.chunk_size = 20
        self.chunk_timeout = 0.5
        self.buffer_size = 100
        print("🔄 Returned to standard streaming mode")
    
    def process_long_form_content(self, content: str, speakers: Optional[List[str]] = None) -> bool:
        """Process long-form content using VibeVoice capabilities"""
        if not self.vibevoice_mode:
            print("⚠️ VibeVoice mode not enabled for long-form content")
            return False
            
        if not self.voice_engine:
            print("⚠️ No voice engine configured")
            return False
            
        try:
            # Check if voice engine supports VibeVoice
            if hasattr(self.voice_engine, 'vibevoice_manager'):
                vibevoice = self.voice_engine.vibevoice_manager
                
                if len(content) > self.long_form_threshold or self.continuous_mode:
                    print(f"🎬 Processing long-form content ({len(content)} chars)")
                    
                    # Use VibeVoice long-form generation
                    audio_chunks = vibevoice.generate_long_form(content, speakers=speakers)
                    
                    if audio_chunks:
                        # Create speech chunks for the streaming pipeline
                        for i, audio in enumerate(audio_chunks):
                            chunk = SpeechChunk(
                                text=f"Long-form chunk {i+1}",
                                audio_data=audio,
                                chunk_id=self.chunk_counter + i,
                                is_complete=(i == len(audio_chunks) - 1),
                                timestamp=time.time()
                            )
                            
                            if self.on_chunk_ready:
                                self.on_chunk_ready(chunk)
                        
                        self.chunk_counter += len(audio_chunks)
                        return True
                    else:
                        print("⚠️ VibeVoice long-form generation failed")
                        return False
                else:
                    # Use standard VibeVoice generation
                    audio = vibevoice.generate(content, speakers=speakers)
                    if audio is not None:
                        chunk = SpeechChunk(
                            text=content[:100] + "..." if len(content) > 100 else content,
                            audio_data=audio,
                            chunk_id=self.chunk_counter,
                            is_complete=True,
                            timestamp=time.time()
                        )
                        
                        if self.on_chunk_ready:
                            self.on_chunk_ready(chunk)
                        
                        self.chunk_counter += 1
                        return True
            else:
                print("⚠️ Voice engine does not support VibeVoice")
                return False
                
        except Exception as e:
            print(f"❌ Long-form content processing failed: {e}")
            return False
    
    def get_vibevoice_status(self) -> Dict[str, Any]:
        """Get VibeVoice streaming status"""
        return {
            "vibevoice_mode": self.vibevoice_mode,
            "continuous_mode": self.continuous_mode,
            "long_form_threshold": self.long_form_threshold,
            "chunk_size": self.chunk_size,
            "chunk_timeout": self.chunk_timeout,
            "supports_90min": self.continuous_mode,
            "supports_multi_speaker": self.vibevoice_mode
        }


def create_streaming_tts_engine(voice_engine=None, **kwargs) -> StreamingTTSEngine:
    """Factory function to create streaming TTS engine"""
    return StreamingTTSEngine(voice_engine=voice_engine, **kwargs)


# Example usage and testing
if __name__ == "__main__":
    import sys
    
    print("🧪 Testing Streaming TTS Engine")
    print("=" * 50)
    
    # Create streaming TTS engine (without actual voice engine for testing)
    streaming_tts = StreamingTTSEngine(chunk_size=10, chunk_timeout=1.0)
    
    # Set up callbacks for testing
    def on_chunk_ready(chunk):
        print(f"📢 Chunk ready: #{chunk.chunk_id} - '{chunk.text}'")
    
    def on_streaming_start():
        print("🎬 Streaming started!")
    
    def on_streaming_complete():
        print("🏁 Streaming completed!")
    
    streaming_tts.on_chunk_ready = on_chunk_ready
    streaming_tts.on_streaming_start = on_streaming_start
    streaming_tts.on_streaming_complete = on_streaming_complete
    
    # Test with mock AI response
    def mock_ai_response():
        """Simulate AI response tokens"""
        response = "Hello! This is a test of the streaming TTS system. It should break this long response into natural chunks. Each chunk will be synthesized and played as soon as it's ready, creating a more natural conversation flow."
        
        for word in response.split():
            yield word
            time.sleep(0.1)  # Simulate AI generation delay
    
    print("🎤 Starting streaming TTS test...")
    
    try:
        # Process the mock response
        chunks = list(streaming_tts.process_token_stream(mock_ai_response()))
        
        print(f"\n📊 Test Results:")
        print(f"   Chunks generated: {len(chunks)}")
        
        stats = streaming_tts.get_stats()
        for key, value in stats.items():
            print(f"   {key}: {value}")
        
    except KeyboardInterrupt:
        print("\n⏹️ Test interrupted")
    finally:
        streaming_tts.cleanup()
        print("🧪 Test complete")