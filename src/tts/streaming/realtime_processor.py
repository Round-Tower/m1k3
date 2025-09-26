#!/usr/bin/env python3
"""
Real-time TTS Processing System for M1K3
Optimized for conversational chatbot synthesis with streaming and interruption support.
"""

import time
import threading
import queue
from typing import Optional, List, Callable, Dict, Any
import numpy as np

class StreamingTextProcessor:
    """
    Processes text in real-time for TTS synthesis with conversation flow optimization.
    """

    def __init__(self, tts_engine, buffer_size: int = 3):
        self.tts_engine = tts_engine
        self.buffer_size = buffer_size

        # Threading and queues
        self.text_queue = queue.Queue()
        self.audio_queue = queue.Queue()
        self.stop_event = threading.Event()
        self.processing_thread = None

        # State tracking
        self.is_processing = False
        self.current_sentence = ""
        self.sentence_buffer = []

        # Callback for audio output
        self.audio_callback: Optional[Callable[[np.ndarray], None]] = None

        # Performance metrics
        self.metrics = {
            "sentences_processed": 0,
            "average_latency": 0.0,
            "cache_hits": 0,
            "processing_times": []
        }

    def set_audio_callback(self, callback: Callable[[np.ndarray], None]):
        """Set callback function for audio output."""
        self.audio_callback = callback

    def start_processing(self):
        """Start the background processing thread."""
        if self.processing_thread and self.processing_thread.is_alive():
            return

        self.stop_event.clear()
        self.processing_thread = threading.Thread(target=self._processing_loop)
        self.processing_thread.daemon = True
        self.processing_thread.start()
        self.is_processing = True
        print("🔄 Started real-time TTS processing")

    def stop_processing(self):
        """Stop the background processing thread."""
        if not self.processing_thread:
            return

        self.stop_event.set()
        self.processing_thread.join(timeout=2)
        self.is_processing = False

        # Clear queues
        self._clear_queue(self.text_queue)
        self._clear_queue(self.audio_queue)
        print("⏹️  Stopped real-time TTS processing")

    def add_text(self, text: str, priority: bool = False):
        """
        Add text for processing. Can be partial sentences.

        Args:
            text: Text to synthesize
            priority: If True, processes immediately (for interruptions)
        """
        if priority:
            # Clear current buffer and process immediately
            self.interrupt_current()

        # Add to sentence buffer or queue directly
        sentences = self._split_into_sentences(text)

        for sentence in sentences:
            if sentence.strip():
                if priority:
                    self.text_queue.put(("priority", sentence.strip()))
                else:
                    self.text_queue.put(("normal", sentence.strip()))

    def interrupt_current(self):
        """Interrupt current synthesis for immediate response."""
        # Clear the text queue
        self._clear_queue(self.text_queue)

        # Signal interruption (can be expanded for audio interruption)
        print("⚡ Interrupting current synthesis for priority message")

    def _processing_loop(self):
        """Main processing loop running in background thread."""
        while not self.stop_event.is_set():
            try:
                # Wait for text with timeout
                try:
                    priority, text = self.text_queue.get(timeout=0.1)
                except queue.Empty:
                    continue

                if text:
                    start_time = time.time()

                    # Generate audio
                    audio = self.tts_engine.generate(text)

                    if audio is not None:
                        # Calculate latency
                        processing_time = time.time() - start_time
                        self._update_metrics(processing_time)

                        # Send to audio callback or queue
                        if self.audio_callback:
                            self.audio_callback(audio)
                        else:
                            self.audio_queue.put(audio)

                        print(f"🎤 Synthesized: '{text[:50]}...' ({processing_time:.2f}s)")

                    self.text_queue.task_done()

            except Exception as e:
                print(f"❌ Processing error: {e}")

    def _split_into_sentences(self, text: str) -> List[str]:
        """Split text into sentences for better real-time processing."""
        import re

        # Simple sentence splitting (can be enhanced)
        sentences = re.split(r'[.!?]+', text)

        # Filter out empty sentences and clean whitespace
        return [s.strip() for s in sentences if s.strip()]

    def _clear_queue(self, q: queue.Queue):
        """Clear all items from a queue."""
        while not q.empty():
            try:
                q.get_nowait()
            except queue.Empty:
                break

    def _update_metrics(self, processing_time: float):
        """Update performance metrics."""
        self.metrics["sentences_processed"] += 1
        self.metrics["processing_times"].append(processing_time)

        # Keep only last 100 processing times
        if len(self.metrics["processing_times"]) > 100:
            self.metrics["processing_times"] = self.metrics["processing_times"][-100:]

        # Calculate average latency
        if self.metrics["processing_times"]:
            self.metrics["average_latency"] = sum(self.metrics["processing_times"]) / len(self.metrics["processing_times"])

    def get_metrics(self) -> Dict[str, Any]:
        """Get current performance metrics."""
        return {
            **self.metrics,
            "is_processing": self.is_processing,
            "queue_size": self.text_queue.qsize(),
            "audio_queue_size": self.audio_queue.qsize()
        }

    def get_audio(self, timeout: float = 1.0) -> Optional[np.ndarray]:
        """Get next available audio from the queue."""
        try:
            return self.audio_queue.get(timeout=timeout)
        except queue.Empty:
            return None

class ConversationFlowOptimizer:
    """
    Optimizes conversation flow for real-time chatbot interactions.
    """

    def __init__(self, tts_processor: StreamingTextProcessor):
        self.processor = tts_processor

        # Conversation state
        self.conversation_active = False
        self.last_response_time = 0
        self.response_buffer = []

        # Optimization settings
        self.max_response_delay = 0.5  # Maximum delay before starting synthesis
        self.chunk_size = 100  # Characters per chunk for streaming

    def start_response(self, full_response: str, stream_chunks: bool = True):
        """
        Start generating audio for a chatbot response with flow optimization.

        Args:
            full_response: Complete response text
            stream_chunks: Whether to process in chunks for streaming
        """
        self.conversation_active = True
        self.last_response_time = time.time()

        if stream_chunks:
            self._stream_response_chunks(full_response)
        else:
            self.processor.add_text(full_response)

    def interrupt_for_user(self):
        """Interrupt current response when user wants to speak."""
        if self.conversation_active:
            self.processor.interrupt_current()
            self.conversation_active = False
            print("👤 User interruption - stopping current response")

    def _stream_response_chunks(self, response: str):
        """Stream response in optimal chunks for real-time feel."""
        # Split response into chunks at natural boundaries
        chunks = self._split_response_smartly(response)

        for i, chunk in enumerate(chunks):
            # Add slight delay between chunks for natural flow
            if i > 0:
                time.sleep(0.1)
            self.processor.add_text(chunk)

    def _split_response_smartly(self, response: str) -> List[str]:
        """Split response into chunks at natural language boundaries."""
        # Split by sentences first
        sentences = response.split('. ')
        chunks = []
        current_chunk = ""

        for sentence in sentences:
            if len(current_chunk) + len(sentence) < self.chunk_size:
                current_chunk += sentence + ". "
            else:
                if current_chunk:
                    chunks.append(current_chunk.strip())
                current_chunk = sentence + ". "

        if current_chunk:
            chunks.append(current_chunk.strip())

        return chunks

    def get_conversation_stats(self) -> Dict[str, Any]:
        """Get conversation flow statistics."""
        return {
            "conversation_active": self.conversation_active,
            "last_response_time": self.last_response_time,
            "processor_metrics": self.processor.get_metrics()
        }

# Example usage
if __name__ == "__main__":
    print("Testing Real-time TTS Processing System...")

    # Mock TTS engine for testing
    class MockTTSEngine:
        def generate(self, text):
            import time
            time.sleep(0.1)  # Simulate synthesis time
            return np.random.random(8000).astype(np.float32)  # Mock audio

    mock_engine = MockTTSEngine()
    processor = StreamingTextProcessor(mock_engine)

    # Test callback
    def audio_callback(audio):
        print(f"🔊 Audio ready: {len(audio)} samples")

    processor.set_audio_callback(audio_callback)
    processor.start_processing()

    # Test streaming
    test_text = "This is a test of the real-time processing system. It should handle multiple sentences efficiently. Each sentence should be processed as soon as possible for optimal conversation flow."

    processor.add_text(test_text)

    # Wait for processing
    time.sleep(2)

    # Test interruption
    processor.add_text("This is an urgent interruption message!", priority=True)

    time.sleep(1)

    # Get metrics
    metrics = processor.get_metrics()
    print(f"📊 Metrics: {metrics}")

    processor.stop_processing()
    print("✅ Real-time processing test complete!")