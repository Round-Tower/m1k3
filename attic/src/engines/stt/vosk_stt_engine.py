#!/usr/bin/env python3
"""
Vosk STT Engine - Lightweight offline Speech-to-Text for M1K3
High-quality offline speech recognition using Vosk (40MB model, 300MB runtime)
"""

import os
import json
import time
import threading
import numpy as np
import tempfile
import urllib.request
import zipfile
from typing import Optional, Callable, List
from pathlib import Path

from .stt_manager import STTEngine, STTResult, STTStatus

try:
    import vosk
    import sounddevice as sd
    import soundfile as sf
    VOSK_AVAILABLE = True
except ImportError as e:
    VOSK_AVAILABLE = False
    print(f"Vosk dependencies not available: {e}")


class VoskSTTEngine(STTEngine):
    """Vosk-based STT engine for lightweight offline recognition"""
    
    def __init__(self, model_name: str = "vosk-model-small-en-us-0.15"):
        self.model_name = model_name
        self.model = None
        self.recognizer = None
        self.status = STTStatus.DISABLED
        self.sample_rate = 16000  # Vosk works well with 16kHz audio
        
        # Audio recording settings
        self.channels = 1
        self.dtype = np.float32
        
        # Continuous listening state
        self.continuous_listening = False
        self.continuous_thread: Optional[threading.Thread] = None
        self.continuous_callback: Optional[Callable[[STTResult], None]] = None
        self.stop_continuous = threading.Event()
        
        # Shutdown signal (can be set by CLI core for force termination)
        self.force_shutdown = None
        
        # Voice activity detection settings (more sensitive thresholds)
        self.silence_threshold = 0.003  # Lowered from 0.01 to detect quieter speech
        self.min_speech_duration = 0.3  # Minimum seconds of speech (reduced)
        self.max_silence_duration = 2.0  # Max seconds of silence before stopping
        
        # Model settings
        self.model_dir = Path.home() / ".m1k3" / "vosk_models"
        self.model_path = self.model_dir / self.model_name
        
        # Supported languages (Vosk supports 20+ languages)
        self.supported_languages = [
            "en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh", 
            "ar", "hi", "tr", "pl", "nl", "sv", "da", "no", "fi", "cs"
        ]
        
        # Default model URLs (small models only)
        self.model_urls = {
            "vosk-model-small-en-us-0.15": "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            "vosk-model-en-us-0.22": "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22.zip",  # Larger, more accurate
            "vosk-model-small-en-in-0.4": "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",  # Indian English
        }
    
    def _ensure_model_downloaded(self) -> bool:
        """Download Vosk model if not already present"""
        if self.model_path.exists():
            print(f"✅ Vosk model already available: {self.model_path}")
            return True
        
        if self.model_name not in self.model_urls:
            print(f"❌ Unknown Vosk model: {self.model_name}")
            print(f"Available models: {list(self.model_urls.keys())}")
            return False
        
        model_url = self.model_urls[self.model_name]
        
        try:
            print(f"📥 Downloading Vosk model: {self.model_name} (~40MB)")
            print(f"🔗 URL: {model_url}")
            
            # Create models directory
            self.model_dir.mkdir(parents=True, exist_ok=True)
            
            # Download model to temporary file
            with tempfile.NamedTemporaryFile(suffix='.zip', delete=False) as temp_file:
                temp_path = temp_file.name
                
                def progress_hook(block_num, block_size, total_size):
                    if total_size > 0:
                        progress = (block_num * block_size / total_size) * 100
                        if progress <= 100:  # Avoid >100% due to chunking
                            print(f"\r📊 Download progress: {progress:.1f}%", end="", flush=True)
                
                urllib.request.urlretrieve(model_url, temp_path, progress_hook)
                print("\n✅ Download complete")
            
            # Extract model
            print(f"📦 Extracting model to {self.model_dir}")
            with zipfile.ZipFile(temp_path, 'r') as zip_ref:
                zip_ref.extractall(self.model_dir)
            
            # Clean up temporary file
            os.unlink(temp_path)
            
            # Verify extraction
            if self.model_path.exists():
                print(f"✅ Model extracted successfully: {self.model_path}")
                return True
            else:
                print(f"❌ Model extraction failed: {self.model_path} not found")
                return False
                
        except Exception as e:
            print(f"❌ Failed to download Vosk model: {e}")
            return False
    
    def initialize(self) -> bool:
        """Initialize Vosk model and recognizer"""
        if not VOSK_AVAILABLE:
            print("❌ Vosk dependencies not available")
            return False
        
        try:
            # Ensure model is downloaded
            if not self._ensure_model_downloaded():
                return False
            
            print(f"🔄 Loading Vosk model: {self.model_name}")
            
            # Set Vosk log level (0 = no logs, 3 = verbose)
            vosk.SetLogLevel(-1)  # Disable Vosk logs for clean output
            
            # Load model
            self.model = vosk.Model(str(self.model_path))
            
            # Create recognizer for streaming
            self.recognizer = vosk.KaldiRecognizer(self.model, self.sample_rate)
            
            # Enable words with timestamps (optional)
            self.recognizer.SetWords(True)
            
            self.status = STTStatus.IDLE
            print(f"✅ Vosk model '{self.model_name}' loaded successfully")
            return True
            
        except Exception as e:
            print(f"❌ Failed to load Vosk model: {e}")
            self.status = STTStatus.ERROR
            return False
    
    def is_available(self) -> bool:
        """Check if Vosk engine is available"""
        return VOSK_AVAILABLE and self.model is not None and self.recognizer is not None
    
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
                import time as time_module
                current_time = time_module.time()
                
                if volume_level > self.silence_threshold:
                    last_speech_time = current_time
                    if not recording_started:
                        recording_started = True
                        print("🔊 Speech detected, recording...")
                    
                    audio_buffer.append(indata.copy())
                elif recording_started:
                    # Add silence but check if we should stop
                    audio_buffer.append(indata.copy())
                    
                    if (last_speech_time and 
                        current_time - last_speech_time > phrase_timeout):
                        raise sd.CallbackStop()
                
                # Show audio level periodically
                if len(audio_buffer) % 50 == 0 and len(audio_buffer) > 0:  # Every ~0.5 seconds
                    print(f"🎵 Audio level: {volume_level:.4f} (threshold: {self.silence_threshold})")
            
            # Record audio with explicit device selection
            try:
                # Try to get the default input device
                device_info = sd.query_devices(kind='input')
                print(f"🎤 Using audio device: {device_info['name']}")
            except:
                print("🎤 Using default audio device")
            
            with sd.InputStream(
                samplerate=self.sample_rate,
                channels=self.channels,
                dtype=self.dtype,
                callback=audio_callback,
                device=None  # Use default input device
            ):
                start_time = time.time()
                while time.time() - start_time < timeout:
                    # Check for force shutdown signal
                    if self.force_shutdown and self.force_shutdown.is_set():
                        print("🛑 Audio recording interrupted by shutdown signal")
                        return None
                    
                    if not audio_buffer:
                        # Use time.sleep instead of sd.sleep for interruptibility
                        import time
                        time.sleep(0.1)  # Wait 100ms
                        continue
                    
                    # Check if we have enough audio and silence to stop
                    if (recording_started and last_speech_time and 
                        time.time() - last_speech_time > phrase_timeout):
                        break
                    
                    # Use time.sleep instead of sd.sleep for interruptibility
                    import time
                    time.sleep(0.1)  # 100ms
            
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
        """Transcribe audio using Vosk"""
        if not self.is_available() or audio_data is None:
            return None
        
        try:
            start_time = time.time()
            self.status = STTStatus.PROCESSING
            
            print("🧠 Transcribing audio...")
            print(f"   📊 Audio data: {len(audio_data)} samples, dtype: {audio_data.dtype}")
            print(f"   📊 Audio range: {audio_data.min():.6f} to {audio_data.max():.6f}")
            
            # Convert to int16 for Vosk (it expects 16-bit audio)
            if audio_data.dtype != np.int16:
                # Normalize and convert to int16 with extra amplification
                max_val = np.max(np.abs(audio_data))
                if max_val > 0:
                    # Normalize to use full range, apply gain boost, then convert
                    normalized = audio_data / max_val
                    # Apply 3x gain boost for quiet speech (but prevent clipping)
                    gained = np.clip(normalized * 3.0, -1.0, 1.0)
                    audio_data = (gained * 32767).astype(np.int16)
                    print(f"   🔧 Normalized with 3x gain and converted to int16 (max was: {max_val:.6f})")
                else:
                    print("   ⚠️ Audio data is silent, converting to int16 anyway")
                    audio_data = (audio_data * 32767).astype(np.int16)
            
            print(f"   📊 Final audio: {len(audio_data)} samples, range: {audio_data.min()} to {audio_data.max()}")
            
            # Process audio in chunks for streaming-like behavior
            chunk_size = int(self.sample_rate * 0.1)  # 100ms chunks
            final_result = None
            
            for i in range(0, len(audio_data), chunk_size):
                chunk = audio_data[i:i + chunk_size].tobytes()
                
                if self.recognizer.AcceptWaveform(chunk):
                    # Partial result available
                    result = json.loads(self.recognizer.Result())
                    if result.get('text'):
                        final_result = result
                else:
                    # Continue processing
                    pass
            
            # Get final result
            final_json = self.recognizer.FinalResult()
            print(f"   📋 Vosk final result JSON: {final_json}")
            final_result = json.loads(final_json)
            print(f"   📋 Parsed final result: {final_result}")
            
            transcription_time = time.time() - start_time
            
            text = final_result.get('text', '').strip()
            print(f"   📝 Extracted text: '{text}'")
            
            # Calculate confidence from word-level confidence if available
            confidence = 0.8  # Default confidence
            if 'result' in final_result and final_result['result']:
                # Get average confidence from words
                word_confidences = [word.get('conf', 0.8) for word in final_result['result']]
                if word_confidences:
                    confidence = sum(word_confidences) / len(word_confidences)
            
            duration = len(audio_data) / self.sample_rate
            
            if text:
                print(f"✅ Transcribed: '{text}' (confidence: {confidence:.2f})")
                
                stt_result = STTResult(
                    text=text,
                    confidence=confidence,
                    language="en-US",  # Default language
                    duration=duration,
                    engine="vosk",
                    raw_data={
                        "vosk_result": final_result,
                        "transcription_time": transcription_time,
                        "model": self.model_name,
                        "words": final_result.get('result', [])
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
        
        print("🎤 Continuous listening started (Vosk)")
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
        
        print("🎤 Continuous listening stopped (Vosk)")
        return True
    
    def get_status(self) -> STTStatus:
        """Get current engine status"""
        return self.status
    
    def get_supported_languages(self) -> List[str]:
        """Get list of supported languages"""
        return self.supported_languages.copy()

    def transcribe_file(self, file_path: str) -> Optional[STTResult]:
        """Transcribe an audio file"""
        if not self.is_available():
            return None
        
        try:
            audio_data, _ = sf.read(file_path, dtype=self.dtype)
            return self._transcribe_audio(audio_data)
        except Exception as e:
            print(f"❌ Failed to read or transcribe audio file: {e}")
            return None

    
    def set_model(self, model_name: str) -> bool:
        """Switch to a different Vosk model"""
        if model_name == self.model_name and self.model:
            return True
        
        # Stop continuous listening if active
        if self.continuous_listening:
            self.stop_continuous_listening()
        
        self.model_name = model_name
        self.model_path = self.model_dir / self.model_name
        return self.initialize()
    
    def set_vocabulary(self, words: List[str]) -> bool:
        """Set custom vocabulary for better accuracy"""
        if not self.is_available():
            return False
        
        try:
            # Create a new recognizer with custom vocabulary
            vocab_json = json.dumps(words)
            self.recognizer = vosk.KaldiRecognizer(self.model, self.sample_rate, vocab_json)
            self.recognizer.SetWords(True)
            
            print(f"✅ Custom vocabulary set with {len(words)} words")
            return True
        except Exception as e:
            print(f"❌ Failed to set vocabulary: {e}")
            return False
    
    def cleanup(self):
        """Clean up resources"""
        if self.continuous_listening:
            self.stop_continuous_listening()
        
        self.model = None
        self.recognizer = None
        self.status = STTStatus.DISABLED


if __name__ == "__main__":
    # Test Vosk STT Engine
    print("🧪 Testing Vosk STT Engine")
    
    engine = VoskSTTEngine("vosk-model-small-en-us-0.15")
    
    if engine.initialize():
        print("✅ Vosk engine initialized")
        
        # Test custom vocabulary for CLI commands
        cli_vocab = [
            "help", "quit", "exit", "clear", "stats", "status", 
            "voice", "avatar", "model", "listen", "stop", "start"
        ]
        engine.set_vocabulary(cli_vocab)
        
        # Test single recognition
        print("\n🎤 Say a CLI command...")
        result = engine.listen_once(timeout=10.0)
        
        if result:
            print(f"✅ Recognition result:")
            print(f"   Text: {result.text}")
            print(f"   Confidence: {result.confidence:.2f}")
            print(f"   Language: {result.language}")
            print(f"   Duration: {result.duration:.2f}s")
            print(f"   Engine: {result.engine}")
        else:
            print("⚠️ No speech recognized")
    else:
        print("❌ Failed to initialize Vosk engine")
    
    engine.cleanup()
    print("🧪 Test complete")