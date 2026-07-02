#!/usr/bin/env python3
"""
Whisper STT Engine - OpenAI Whisper-based Speech-to-Text for M1K3
High-quality local speech recognition using OpenAI Whisper
"""

import time
import threading
import numpy as np
import tempfile
import os
from typing import Optional, Callable, List
from pathlib import Path

from .stt_manager import STTEngine, STTResult, STTStatus

try:
    import whisper
    import sounddevice as sd
    import soundfile as sf
    WHISPER_AVAILABLE = True
except ImportError as e:
    WHISPER_AVAILABLE = False
    print(f"Whisper dependencies not available: {e}")


class WhisperSTTEngine(STTEngine):
    """OpenAI Whisper-based STT engine for high-quality local recognition"""
    
    def __init__(self, model_name: str = "base"):
        self.model_name = model_name
        self.model = None
        self.status = STTStatus.DISABLED
        self.sample_rate = 16000  # Whisper expects 16kHz audio
        
        # Audio recording settings
        self.channels = 1
        self.dtype = np.float32
        
        # Continuous listening state
        self.continuous_listening = False
        self.continuous_thread: Optional[threading.Thread] = None
        self.continuous_callback: Optional[Callable[[STTResult], None]] = None
        self.stop_continuous = threading.Event()
        
        # Voice activity detection settings
        self.silence_threshold = 0.01
        self.min_speech_duration = 0.5  # Minimum seconds of speech
        self.max_silence_duration = 2.0  # Max seconds of silence before stopping
        
        # Supported languages (Whisper supports 100+ languages)
        self.supported_languages = [
            "en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh", 
            "ar", "hi", "tr", "pl", "nl", "sv", "da", "no", "fi"
        ]
    
    def initialize(self) -> bool:
        """Initialize Whisper model"""
        if not WHISPER_AVAILABLE:
            print("❌ Whisper dependencies not available")
            return False
        
        try:
            print(f"🔄 Loading Whisper model: {self.model_name}")
            self.model = whisper.load_model(self.model_name)
            self.status = STTStatus.IDLE
            print(f"✅ Whisper model '{self.model_name}' loaded successfully")
            return True
            
        except Exception as e:
            print(f"❌ Failed to load Whisper model: {e}")
            self.status = STTStatus.ERROR
            return False
    
    def is_available(self) -> bool:
        """Check if Whisper engine is available"""
        return WHISPER_AVAILABLE and self.model is not None
    
    def _record_audio(self, timeout: float = 5.0, phrase_timeout: float = 1.0) -> Optional[np.ndarray]:
        """Record audio from microphone with voice activity detection"""
        if not self.is_available():
            return None
        
        print(f"🎤 Listening for {timeout}s (phrase timeout: {phrase_timeout}s)...")
        
        try:
            audio_buffer = []
            last_speech_time = None
            recording_started = False
            
            def audio_callback(indata, frames, time, status):
                """Audio input callback"""
                nonlocal last_speech_time, recording_started
                
                if status:
                    print(f"⚠️ Audio input status: {status}")
                
                # Calculate RMS (volume level)
                volume_level = np.sqrt(np.mean(indata ** 2))
                
                # Voice activity detection
                if volume_level > self.silence_threshold:
                    last_speech_time = time.inputBufferAdcTime
                    if not recording_started:
                        recording_started = True
                        print("🔊 Speech detected, recording...")
                    
                    audio_buffer.append(indata.copy())
                elif recording_started:
                    # Add silence but check if we should stop
                    audio_buffer.append(indata.copy())
                    
                    if (last_speech_time and 
                        time.inputBufferAdcTime - last_speech_time > phrase_timeout):
                        raise sd.CallbackStop()
            
            # Record audio
            with sd.InputStream(
                samplerate=self.sample_rate,
                channels=self.channels,
                dtype=self.dtype,
                callback=audio_callback
            ):
                start_time = time.time()
                while time.time() - start_time < timeout:
                    if not audio_buffer:
                        sd.sleep(100)  # Wait 100ms
                        continue
                    
                    # Check if we have enough audio and silence to stop
                    if (recording_started and last_speech_time and 
                        time.time() - last_speech_time > phrase_timeout):
                        break
                    
                    sd.sleep(100)
            
            if not audio_buffer:
                print("⚠️ No audio recorded")
                return None
            
            # Concatenate audio data
            audio_data = np.concatenate(audio_buffer, axis=0)
            
            # Check minimum duration
            duration = len(audio_data) / self.sample_rate
            if duration < self.min_speech_duration:
                print(f"⚠️ Recording too short: {duration:.2f}s")
                return None
            
            print(f"📼 Recorded {duration:.2f}s of audio")
            return audio_data.flatten()
            
        except Exception as e:
            print(f"❌ Recording failed: {e}")
            return None
    
    def _transcribe_audio(self, audio_data: np.ndarray) -> Optional[STTResult]:
        """Transcribe audio using Whisper"""
        if not self.is_available() or audio_data is None:
            return None
        
        try:
            start_time = time.time()
            self.status = STTStatus.PROCESSING
            
            print("🧠 Transcribing audio...")
            
            # Whisper expects audio normalized to [-1, 1]
            if audio_data.dtype != np.float32:
                audio_data = audio_data.astype(np.float32)
            
            # Normalize audio
            if np.max(np.abs(audio_data)) > 0:
                audio_data = audio_data / np.max(np.abs(audio_data))
            
            # Transcribe with Whisper
            result = self.model.transcribe(
                audio_data,
                language=None,  # Auto-detect language
                task="transcribe"
            )
            
            transcription_time = time.time() - start_time
            
            text = result["text"].strip()
            language = result.get("language", "unknown")
            
            # Calculate confidence (Whisper doesn't provide confidence directly)
            # We'll use a heuristic based on the number of segments and their avg_logprob
            confidence = 0.8  # Default confidence for Whisper
            if "segments" in result and result["segments"]:
                avg_logprobs = [seg.get("avg_logprob", -1.0) for seg in result["segments"]]
                # Convert log prob to confidence (rough approximation)
                confidence = min(1.0, max(0.1, np.mean(avg_logprobs) + 1.0))
            
            duration = len(audio_data) / self.sample_rate
            
            if text:
                print(f"✅ Transcribed: '{text}' (lang: {language}, confidence: {confidence:.2f})")
                
                stt_result = STTResult(
                    text=text,
                    confidence=confidence,
                    language=language,
                    duration=duration,
                    engine="whisper",
                    raw_data={
                        "whisper_result": result,
                        "transcription_time": transcription_time,
                        "model": self.model_name
                    }
                )
                
                self.status = STTStatus.IDLE
                return stt_result
            else:
                print("⚠️ No transcription result")
                self.status = STTStatus.IDLE
                return None
                
        except Exception as e:
            print(f"❌ Transcription failed: {e}")
            self.status = STTStatus.ERROR
            return None
    
    def listen_once(self, timeout: float = 5.0, phrase_timeout: float = 1.0) -> Optional[STTResult]:
        """Listen for a single phrase and transcribe it"""
        if not self.is_available():
            return None
        
        # Record audio
        audio_data = self._record_audio(timeout, phrase_timeout)
        if audio_data is None:
            return None
        
        # Transcribe audio
        return self._transcribe_audio(audio_data)
    
    def start_continuous_listening(self, callback: Callable[[STTResult], None]) -> bool:
        """Start continuous listening mode"""
        if not self.is_available() or self.continuous_listening:
            return False
        
        self.continuous_callback = callback
        self.continuous_listening = True
        self.stop_continuous.clear()
        
        # Start continuous listening thread
        self.continuous_thread = threading.Thread(
            target=self._continuous_listening_worker,
            daemon=True
        )
        self.continuous_thread.start()
        
        print("🎤 Continuous listening started (Whisper)")
        return True
    
    def _continuous_listening_worker(self):
        """Worker thread for continuous listening"""
        print("🔄 Starting continuous listening worker...")
        
        while self.continuous_listening and not self.stop_continuous.is_set():
            try:
                # Listen for a phrase
                result = self.listen_once(timeout=10.0, phrase_timeout=2.0)
                
                if result and self.continuous_callback:
                    self.continuous_callback(result)
                
                # Small delay between listening cycles
                if not self.stop_continuous.wait(0.1):
                    continue
                else:
                    break
                    
            except Exception as e:
                print(f"❌ Continuous listening error: {e}")
                time.sleep(1)  # Wait before retrying
        
        print("🔄 Continuous listening worker stopped")
    
    def stop_continuous_listening(self) -> bool:
        """Stop continuous listening mode"""
        if not self.continuous_listening:
            return False
        
        self.continuous_listening = False
        self.stop_continuous.set()
        
        # Wait for thread to finish
        if self.continuous_thread and self.continuous_thread.is_alive():
            self.continuous_thread.join(timeout=2.0)
        
        self.continuous_thread = None
        self.continuous_callback = None
        
        print("🎤 Continuous listening stopped (Whisper)")
        return True
    
    def get_status(self) -> STTStatus:
        """Get current engine status"""
        return self.status
    
    def get_supported_languages(self) -> List[str]:
        """Get list of supported languages"""
        return self.supported_languages.copy()
    
    def set_model(self, model_name: str) -> bool:
        """Switch to a different Whisper model"""
        if model_name == self.model_name and self.model:
            return True
        
        # Stop continuous listening if active
        if self.continuous_listening:
            self.stop_continuous_listening()
        
        self.model_name = model_name
        return self.initialize()
    
    def cleanup(self):
        """Clean up resources"""
        if self.continuous_listening:
            self.stop_continuous_listening()
        
        self.model = None
        self.status = STTStatus.DISABLED


if __name__ == "__main__":
    # Test Whisper STT Engine
    print("🧪 Testing Whisper STT Engine")
    
    engine = WhisperSTTEngine("base")
    
    if engine.initialize():
        print("✅ Whisper engine initialized")
        
        # Test single recognition
        print("\n🎤 Say something...")
        result = engine.listen_once(timeout=10.0)
        
        if result:
            print(f"✅ Recognition result:")
            print(f"   Text: {result.text}")
            print(f"   Confidence: {result.confidence:.2f}")
            print(f"   Language: {result.language}")
            print(f"   Duration: {result.duration:.2f}s")
        else:
            print("⚠️ No speech recognized")
    else:
        print("❌ Failed to initialize Whisper engine")
    
    engine.cleanup()
    print("🧪 Test complete")