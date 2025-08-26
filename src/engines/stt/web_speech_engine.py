#!/usr/bin/env python3
"""
Web Speech STT Engine - SpeechRecognition-based Speech-to-Text for M1K3
Fallback STT engine using SpeechRecognition library with multiple backends
"""

import time
import threading
from typing import Optional, Callable, List

from .stt_manager import STTEngine, STTResult, STTStatus

try:
    import speech_recognition as sr
    SPEECH_RECOGNITION_AVAILABLE = True
except ImportError as e:
    SPEECH_RECOGNITION_AVAILABLE = False
    print(f"SpeechRecognition dependencies not available: {e}")


class WebSpeechEngine(STTEngine):
    """SpeechRecognition-based STT engine with multiple backend options"""
    
    def __init__(self, backend: str = "google"):
        self.backend = backend
        self.recognizer = None
        self.microphone = None
        self.status = STTStatus.DISABLED
        
        # Continuous listening state
        self.continuous_listening = False
        self.continuous_thread: Optional[threading.Thread] = None
        self.continuous_callback: Optional[Callable[[STTResult], None]] = None
        self.stop_continuous = threading.Event()
        
        # Recognition settings
        self.energy_threshold = 300
        self.dynamic_energy_threshold = True
        self.pause_threshold = 0.8
        self.operation_timeout = None
        self.phrase_timeout = 5.0
        self.non_speaking_duration = 0.5
        
        # Supported languages (varies by backend)
        self.supported_languages = [
            "en-US", "en-GB", "es-ES", "fr-FR", "de-DE", "it-IT", 
            "pt-BR", "ru-RU", "ja-JP", "ko-KR", "zh-CN", "ar-SA"
        ]
        
        # Backend configurations
        self.backend_configs = {
            "google": {
                "language": "en-US",
                "show_all": False
            },
            "google_cloud": {
                "language": "en-US",
                "preferred_phrases": None
            },
            "wit": {
                "key": None  # Requires API key
            },
            "bing": {
                "key": None,  # Requires API key
                "language": "en-US"
            },
            "sphinx": {
                "language": "en-US",
                "keyword_entries": None
            }
        }
    
    def initialize(self) -> bool:
        """Initialize SpeechRecognition engine"""
        if not SPEECH_RECOGNITION_AVAILABLE:
            print("❌ SpeechRecognition dependencies not available")
            return False
        
        try:
            print(f"🔄 Initializing Web Speech engine (backend: {self.backend})")
            
            # Initialize recognizer and microphone
            self.recognizer = sr.Recognizer()
            self.microphone = sr.Microphone()
            
            # Configure recognizer settings
            self.recognizer.energy_threshold = self.energy_threshold
            self.recognizer.dynamic_energy_threshold = self.dynamic_energy_threshold
            self.recognizer.pause_threshold = self.pause_threshold
            self.recognizer.operation_timeout = self.operation_timeout
            self.recognizer.phrase_timeout = self.phrase_timeout
            self.recognizer.non_speaking_duration = self.non_speaking_duration
            
            # Test microphone access first
            if not self.test_microphone_access():
                print("❌ Web Speech engine initialization failed - no microphone access")
                self.status = STTStatus.ERROR
                return False
            
            self.status = STTStatus.IDLE
            print(f"✅ Web Speech engine initialized (energy threshold: {self.recognizer.energy_threshold})")
            return True
            
        except Exception as e:
            print(f"❌ Failed to initialize Web Speech engine: {e}")
            self.status = STTStatus.ERROR
            return False
    
    def is_available(self) -> bool:
        """Check if Web Speech engine is available"""
        return SPEECH_RECOGNITION_AVAILABLE and self.recognizer is not None and self.microphone is not None
    
    def _recognize_speech(self, audio_data) -> Optional[STTResult]:
        """Recognize speech using configured backend"""
        if not self.is_available():
            return None
        
        try:
            start_time = time.time()
            self.status = STTStatus.PROCESSING
            
            print(f"🧠 Recognizing speech using {self.backend}...")
            
            # Select recognition backend
            config = self.backend_configs.get(self.backend, {})
            text = None
            confidence = 0.8  # Default confidence for most backends
            
            if self.backend == "google":
                try:
                    result = self.recognizer.recognize_google(
                        audio_data, 
                        language=config.get("language", "en-US"),
                        show_all=config.get("show_all", False)
                    )
                    if isinstance(result, list) and result:
                        # If show_all=True, get best result
                        best_result = max(result, key=lambda x: x.get('confidence', 0))
                        text = best_result['transcript']
                        confidence = best_result.get('confidence', 0.8)
                    else:
                        text = result
                except sr.UnknownValueError:
                    print("⚠️ Google Speech Recognition could not understand audio")
                    return None
                except sr.RequestError as e:
                    print(f"❌ Google Speech Recognition error: {e}")
                    return None
            
            elif self.backend == "sphinx":
                try:
                    text = self.recognizer.recognize_sphinx(
                        audio_data,
                        language=config.get("language", "en-US"),
                        keyword_entries=config.get("keyword_entries")
                    )
                    confidence = 0.6  # Sphinx typically has lower confidence
                except sr.UnknownValueError:
                    print("⚠️ Sphinx could not understand audio")
                    return None
                except sr.RequestError as e:
                    print(f"❌ Sphinx error: {e}")
                    return None
            
            elif self.backend == "wit":
                wit_key = config.get("key")
                if not wit_key:
                    print("❌ Wit.ai requires API key")
                    return None
                try:
                    text = self.recognizer.recognize_wit(audio_data, key=wit_key)
                except sr.UnknownValueError:
                    print("⚠️ Wit.ai could not understand audio")
                    return None
                except sr.RequestError as e:
                    print(f"❌ Wit.ai error: {e}")
                    return None
            
            elif self.backend == "bing":
                bing_key = config.get("key")
                if not bing_key:
                    print("❌ Bing Speech requires API key")
                    return None
                try:
                    text = self.recognizer.recognize_bing(
                        audio_data, 
                        key=bing_key,
                        language=config.get("language", "en-US")
                    )
                except sr.UnknownValueError:
                    print("⚠️ Bing Speech could not understand audio")
                    return None
                except sr.RequestError as e:
                    print(f"❌ Bing Speech error: {e}")
                    return None
            
            else:
                print(f"❌ Unsupported backend: {self.backend}")
                return None
            
            recognition_time = time.time() - start_time
            
            if text:
                text = text.strip()
                print(f"✅ Recognized: '{text}' (confidence: {confidence:.2f})")
                
                stt_result = STTResult(
                    text=text,
                    confidence=confidence,
                    language=config.get("language", "en-US"),
                    duration=recognition_time,
                    engine=f"web_speech_{self.backend}",
                    raw_data={
                        "backend": self.backend,
                        "recognition_time": recognition_time,
                        "energy_threshold": self.recognizer.energy_threshold
                    }
                )
                
                self.status = STTStatus.IDLE
                return stt_result
            else:
                print("⚠️ No recognition result")
                self.status = STTStatus.IDLE
                return None
                
        except Exception as e:
            print(f"❌ Speech recognition failed: {e}")
            self.status = STTStatus.ERROR
            return None
    
    def listen_once(self, timeout: float = 5.0, phrase_timeout: float = 1.0) -> Optional[STTResult]:
        """Listen for a single phrase and recognize it"""
        if not self.is_available():
            return None
        
        # Reinitialize microphone and recognizer for fresh state
        try:
            self.recognizer = sr.Recognizer()
            self.microphone = sr.Microphone()
            
            # Restore configuration
            self.recognizer.energy_threshold = self.energy_threshold
            self.recognizer.dynamic_energy_threshold = self.dynamic_energy_threshold
            self.recognizer.pause_threshold = self.pause_threshold
            self.recognizer.operation_timeout = self.operation_timeout
            self.recognizer.phrase_timeout = self.phrase_timeout
            self.recognizer.non_speaking_duration = self.non_speaking_duration
            
            print("🔄 Recognizer and microphone reinitialized")
        except Exception as e:
            print(f"⚠️ Reinit failed: {e}")
        
        try:
            print(f"🎤 Listening for {timeout}s (phrase timeout: {phrase_timeout}s)...")
            print(f"🔧 Energy threshold: {self.recognizer.energy_threshold:.1f}")
            
            self.status = STTStatus.LISTENING
            
            # Update timeout settings
            original_phrase_timeout = self.recognizer.phrase_timeout
            self.recognizer.phrase_timeout = phrase_timeout
            
            # Listen for audio
            with self.microphone as source:
                try:
                    # Quick ambient noise adjustment with lower sensitivity
                    print("🎯 Calibrating microphone for current environment...")
                    original_threshold = self.recognizer.energy_threshold
                    self.recognizer.adjust_for_ambient_noise(source, duration=0.3)
                    new_threshold = self.recognizer.energy_threshold
                    print(f"📊 Threshold adjusted: {original_threshold:.1f} → {new_threshold:.1f}")
                    
                    # Make threshold even more sensitive for better detection
                    if new_threshold > 50:
                        self.recognizer.energy_threshold = max(30, new_threshold * 0.7)
                        print(f"🎚️ Further sensitivity boost: {new_threshold:.1f} → {self.recognizer.energy_threshold:.1f}")
                    
                    print("📢 Speak now - listening for your voice...")
                    audio = self.recognizer.listen(
                        source, 
                        timeout=timeout, 
                        phrase_time_limit=phrase_timeout
                    )
                    print("📼 Audio captured, recognizing...")
                    
                    # Recognize the audio
                    result = self._recognize_speech(audio)
                    return result
                    
                except sr.WaitTimeoutError:
                    print("⚠️ Listening timeout - no speech detected")
                    self.status = STTStatus.IDLE
                    return None
                finally:
                    # Restore original settings
                    self.recognizer.phrase_timeout = original_phrase_timeout
            
        except Exception as e:
            print(f"❌ Listen once failed: {e}")
            self.status = STTStatus.ERROR
            return None
    
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
        
        print(f"🎤 Continuous listening started (Web Speech - {self.backend})")
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
        
        print(f"🎤 Continuous listening stopped (Web Speech - {self.backend})")
        return True
    
    def get_status(self) -> STTStatus:
        """Get current engine status"""
        return self.status
    
    def get_supported_languages(self) -> List[str]:
        """Get list of supported languages"""
        return self.supported_languages.copy()
    
    def set_backend(self, backend: str) -> bool:
        """Switch to a different recognition backend"""
        if backend not in self.backend_configs:
            print(f"❌ Unsupported backend: {backend}")
            return False
        
        # Stop continuous listening if active
        if self.continuous_listening:
            self.stop_continuous_listening()
        
        self.backend = backend
        print(f"🔄 Switched to {backend} backend")
        return True
    
    def set_api_key(self, backend: str, key: str) -> bool:
        """Set API key for backends that require it"""
        if backend in self.backend_configs:
            self.backend_configs[backend]["key"] = key
            print(f"🔑 API key set for {backend}")
            return True
        return False
    
    def test_microphone_access(self) -> bool:
        """Test if microphone is accessible"""
        if not self.is_available():
            return False
        
        try:
            print("🎤 Testing microphone access...")
            
            # Quick test to see if we can access microphone
            with self.microphone as source:
                # Very short test - just check if we can open the microphone
                self.recognizer.adjust_for_ambient_noise(source, duration=0.5)
            
            print("✅ Microphone access test passed")
            return True
        except OSError as e:
            if "device unavailable" in str(e).lower() or "no such device" in str(e).lower():
                print("❌ Microphone not available - check microphone connection")
            elif "permission" in str(e).lower() or "access" in str(e).lower():
                print("❌ Microphone permission denied")
                print("💡 Grant microphone access in System Preferences > Privacy & Security > Microphone")
            else:
                print(f"❌ Microphone access failed: {e}")
            return False
        except Exception as e:
            print(f"❌ Microphone access test failed: {e}")
            return False
    
    def calibrate_microphone(self, duration: float = 2.0) -> bool:
        """Recalibrate microphone for ambient noise"""
        if not self.is_available():
            return False
        
        # Test microphone access first
        if not self.test_microphone_access():
            return False
        
        try:
            print(f"🔄 Calibrating microphone for {duration}s...")
            with self.microphone as source:
                self.recognizer.adjust_for_ambient_noise(source, duration=duration)
            
            print(f"✅ Microphone calibrated (energy threshold: {self.recognizer.energy_threshold})")
            return True
        except Exception as e:
            print(f"❌ Microphone calibration failed: {e}")
            return False
    
    def cleanup(self):
        """Clean up resources"""
        if self.continuous_listening:
            self.stop_continuous_listening()
        
        self.recognizer = None
        self.microphone = None
        self.status = STTStatus.DISABLED


if __name__ == "__main__":
    # Test Web Speech Engine
    print("🧪 Testing Web Speech Engine")
    
    # Test different backends
    backends = ["google", "sphinx"]
    
    for backend in backends:
        print(f"\n--- Testing {backend} backend ---")
        
        engine = WebSpeechEngine(backend)
        
        if engine.initialize():
            print(f"✅ {backend} engine initialized")
            
            # Test single recognition
            print(f"🎤 Say something (using {backend})...")
            result = engine.listen_once(timeout=5.0)
            
            if result:
                print(f"✅ Recognition result:")
                print(f"   Text: {result.text}")
                print(f"   Confidence: {result.confidence:.2f}")
                print(f"   Language: {result.language}")
                print(f"   Engine: {result.engine}")
            else:
                print("⚠️ No speech recognized")
        else:
            print(f"❌ Failed to initialize {backend} engine")
        
        engine.cleanup()
    
    print("🧪 Test complete")