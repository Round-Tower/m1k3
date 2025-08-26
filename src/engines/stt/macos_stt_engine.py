#!/usr/bin/env python3
"""
macOS Native STT Engine - Zero-footprint Speech-to-Text for M1K3
Uses macOS SFSpeechRecognizer for on-device speech recognition
"""

import time
import threading
import platform
from typing import Optional, Callable, List

from .stt_manager import STTEngine, STTResult, STTStatus

# Check if running on macOS and required version
MACOS_AVAILABLE = platform.system() == "Darwin"
MACOS_VERSION_OK = False

if MACOS_AVAILABLE:
    try:
        import platform
        version = platform.mac_ver()[0]
        major, minor = map(int, version.split('.')[:2])
        MACOS_VERSION_OK = (major > 10) or (major == 10 and minor >= 15)  # macOS 10.15+
    except:
        MACOS_VERSION_OK = False

PYOBJC_AVAILABLE = False
if MACOS_AVAILABLE and MACOS_VERSION_OK:
    try:
        import objc
        from Foundation import NSObject, NSRunLoop, NSURL
        from Speech import (
            SFSpeechRecognizer, SFSpeechAudioBufferRecognitionRequest,
            SFSpeechRecognitionTask, SFSpeechRecognitionResult
        )
        from AVFoundation import (
            AVAudioEngine, AVAudioInputNode, AVAudioSession, 
            AVAudioSessionCategoryRecord, AVAudioFormat
        )
        PYOBJC_AVAILABLE = True
        print("✅ PyObjC Speech framework available")
    except ImportError as e:
        PYOBJC_AVAILABLE = False
        # Don't print error during import, only when actually trying to use
        pass


if PYOBJC_AVAILABLE:
    class MacOSSTTDelegate(NSObject):
        """Delegate for handling speech recognition events"""
        
        def init(self):
            self = objc.super(MacOSSTTDelegate, self).init()
            if self is None:
                return None
            
            self.recognition_result = None
            self.recognition_error = None
            self.is_finished = False
            self.partial_result = None
            return self
        
        def speechRecognitionTask_didFinishRecognition_(self, task, result):
            """Called when speech recognition completes"""
            if result:
                self.recognition_result = result
            self.is_finished = True
        
        def speechRecognitionTask_didFinishSuccessfully_(self, task, successfully):
            """Called when recognition task finishes"""
            self.is_finished = True
        
        def speechRecognitionTask_didHypothesizeTranscription_(self, task, transcription):
            """Called for intermediate results (partial transcriptions)"""
            if transcription and hasattr(transcription, 'formattedString'):
                partial_text = transcription.formattedString()
                if partial_text.strip():
                    print(f"🔄 Partial: '{partial_text.strip()}'")
                    # Store latest partial result as backup
                    self.partial_result = partial_text
        
        # Note: speechRecognitionTask_wasCancelled_ has signature issues with PyObjC
        # We'll handle cancellation through other delegate methods
        
        def speechRecognitionTask_didFinishWithError_(self, task, error):
            """Called when recognition fails"""
            self.recognition_error = error
            self.is_finished = True
else:
    # Dummy delegate class when PyObjC not available
    class MacOSSTTDelegate:
        def __init__(self):
            pass


class MacOSSTTEngine(STTEngine):
    """macOS native STT engine using SFSpeechRecognizer"""
    
    def __init__(self, locale: str = "en-US"):
        self.locale = locale
        self.speech_recognizer = None
        self.audio_engine = None
        self.recognition_request = None
        self.recognition_task = None
        self.delegate = None
        self.status = STTStatus.DISABLED
        
        # Continuous listening state
        self.continuous_listening = False
        self.continuous_thread: Optional[threading.Thread] = None
        self.continuous_callback: Optional[Callable[[STTResult], None]] = None
        self.stop_continuous = threading.Event()
        
        # Audio settings
        self.sample_rate = 16000
        
        # Supported languages (SFSpeechRecognizer supports 50+ languages)
        self.supported_languages = [
            "en-US", "en-GB", "en-AU", "en-CA", "en-IN", "en-NZ", "en-ZA",
            "es-ES", "es-MX", "fr-FR", "fr-CA", "de-DE", "it-IT", "pt-BR",
            "ru-RU", "ja-JP", "ko-KR", "zh-CN", "zh-TW", "ar-SA", "hi-IN",
            "tr-TR", "pl-PL", "nl-NL", "sv-SE", "da-DK", "no-NO", "fi-FI"
        ]
    
    def initialize(self) -> bool:
        """Initialize macOS Speech Recognition"""
        if not MACOS_AVAILABLE:
            print("❌ Not running on macOS")
            return False
        
        if not MACOS_VERSION_OK:
            print("❌ Requires macOS 10.15 (Catalina) or later")
            return False
        
        if not PYOBJC_AVAILABLE:
            print("❌ PyObjC Speech framework not available")
            print("💡 Install with: pip install pyobjc-framework-Speech pyobjc-framework-AVFoundation")
            return False
        
        try:
            print(f"🔄 Initializing macOS Speech Recognition (locale: {self.locale})")
            
            # Create speech recognizer for the locale
            locale_obj = objc.lookUpClass('NSLocale').localeWithLocaleIdentifier_(self.locale)
            self.speech_recognizer = SFSpeechRecognizer.alloc().initWithLocale_(locale_obj)
            
            if not self.speech_recognizer:
                print(f"❌ Speech recognizer not available for locale: {self.locale}")
                return False
            
            if not self.speech_recognizer.isAvailable():
                print("❌ Speech recognition not available on this device")
                print("💡 Make sure Siri is enabled in System Preferences")
                return False
            
            # Check authorization status first
            try:
                # Use class method instead of instance method
                auth_status = SFSpeechRecognizer.authorizationStatus()
                print(f"🔐 Speech recognition authorization status: {auth_status}")
                
                # Authorization status constants:
                # 0 = NotDetermined, 1 = Denied, 2 = Restricted, 3 = Authorized
                if auth_status == 1:  # Denied
                    print("❌ Speech recognition access denied")
                    print("💡 Enable speech recognition in System Preferences > Privacy & Security > Speech Recognition")
                    return False
                elif auth_status == 2:  # Restricted
                    print("❌ Speech recognition access restricted")
                    return False
                elif auth_status == 0:  # NotDetermined
                    print("⚠️ Speech recognition authorization not determined")
                    print("🔑 Requesting speech recognition permission...")
                    # Request authorization explicitly
                    if not self._request_speech_authorization():
                        print("❌ Speech recognition authorization denied or failed")
                        return False
                elif auth_status == 3:  # Authorized
                    print("✅ Speech recognition authorized")
                else:
                    print(f"⚠️ Unknown authorization status: {auth_status}")
            except Exception as e:
                print(f"⚠️ Could not check authorization status: {e}")
                print("💡 Continuing with initialization...")
            
            # Create audio engine
            self.audio_engine = AVAudioEngine.alloc().init()
            
            # Create delegate
            self.delegate = MacOSSTTDelegate.alloc().init()
            
            self.status = STTStatus.IDLE
            print(f"✅ macOS Speech Recognition initialized")
            return True
            
        except Exception as e:
            print(f"❌ Failed to initialize macOS Speech Recognition: {e}")
            self.status = STTStatus.ERROR
            return False
    
    def _request_speech_authorization(self) -> bool:
        """Request speech recognition authorization and wait for response"""
        if not PYOBJC_AVAILABLE:
            return False
        
        try:
            import threading
            import time
            
            # Create a semaphore to wait for the authorization response
            authorization_complete = threading.Event()
            authorization_granted = [False]  # Use list to modify from closure
            
            def authorization_handler(status):
                """Handle the authorization response"""
                print(f"🔐 Authorization response received: {status}")
                # Status: 0=NotDetermined, 1=Denied, 2=Restricted, 3=Authorized
                authorization_granted[0] = (status == 3)  # Authorized
                authorization_complete.set()
            
            # Request authorization
            print("📱 Requesting speech recognition authorization...")
            print("💡 A system dialog should appear - please allow access")
            SFSpeechRecognizer.requestAuthorization_(authorization_handler)
            
            # Wait for response (timeout after 30 seconds)
            if authorization_complete.wait(timeout=30.0):
                if authorization_granted[0]:
                    print("✅ Speech recognition authorization granted!")
                    return True
                else:
                    print("❌ Speech recognition authorization denied")
                    return False
            else:
                print("⏰ Authorization request timed out")
                print("💡 Please check System Preferences > Privacy & Security > Speech Recognition")
                return False
                
        except Exception as e:
            print(f"❌ Error requesting speech authorization: {e}")
            return False
    
    def is_available(self) -> bool:
        """Check if macOS Speech Recognition is available"""
        return (MACOS_AVAILABLE and MACOS_VERSION_OK and PYOBJC_AVAILABLE and 
                self.speech_recognizer is not None and 
                self.speech_recognizer.isAvailable())
    
    def _cleanup_audio_resources(self):
        """Clean up audio resources from previous attempts"""
        try:
            # Stop and cleanup audio engine if running
            if self.audio_engine and self.audio_engine.isRunning():
                print("🧹 Stopping previous audio engine...")
                self.audio_engine.stop()
            
            # Remove any existing taps
            if self.audio_engine:
                input_node = self.audio_engine.inputNode()
                try:
                    input_node.removeTapOnBus_(0)
                except:
                    pass  # Tap might not exist
            
            # Cancel any existing recognition task
            if self.recognition_task:
                print("🧹 Cancelling previous recognition task...")
                self.recognition_task.cancel()
                self.recognition_task = None
            
            # Clean up recognition request
            if self.recognition_request:
                try:
                    self.recognition_request.endAudio()
                except:
                    pass  # Might already be ended
                self.recognition_request = None
            
            # Reset delegate state
            if self.delegate:
                self.delegate.recognition_result = None
                self.delegate.recognition_error = None
                self.delegate.is_finished = False
                if hasattr(self.delegate, 'partial_result'):
                    self.delegate.partial_result = None
            
            print("🧹 Audio resources cleaned up")
            
        except Exception as e:
            print(f"⚠️ Error during cleanup: {e}")
    
    def _recognize_speech_from_microphone(self, timeout: float = 5.0) -> Optional[STTResult]:
        """Recognize speech from microphone using SFSpeechRecognizer"""
        if not self.is_available():
            return None
        
        # Ensure clean state before starting
        self._cleanup_audio_resources()
        
        try:
            start_time = time.time()
            self.status = STTStatus.PROCESSING
            
            print("🧠 Starting speech recognition...")
            
            # Create recognition request
            self.recognition_request = SFSpeechAudioBufferRecognitionRequest.alloc().init()
            
            # Enable on-device recognition if available (iOS 13+, macOS 10.15+)
            if hasattr(self.recognition_request, 'setRequiresOnDeviceRecognition_'):
                self.recognition_request.setRequiresOnDeviceRecognition_(True)
                print("🔒 Using on-device recognition (private)")
            
            # Configure for better speech detection
            if hasattr(self.recognition_request, 'setShouldReportPartialResults_'):
                self.recognition_request.setShouldReportPartialResults_(True)
                print("🎯 Partial results enabled for real-time detection")
            
            # Set task hint for better recognition
            if hasattr(self.recognition_request, 'setTaskHint_'):
                # SFSpeechRecognitionTaskHint.dictation = 0
                self.recognition_request.setTaskHint_(0)  # Dictation mode
                print("📝 Task hint set to dictation mode")
            
            # Configure detection sensitivity
            if hasattr(self.recognition_request, 'setDetectionLimit_'):
                self.recognition_request.setDetectionLimit_(1)  # Detect first utterance
                print("🎚️ Detection limit set for single utterance")
            
            # Set end silence duration (shorter for faster response)
            if hasattr(self.recognition_request, 'setEndpointing_'):
                self.recognition_request.setEndpointing_(True)
                print("⏱️ Automatic endpointing enabled")
            
            # Reset delegate state
            self.delegate.recognition_result = None
            self.delegate.recognition_error = None
            self.delegate.is_finished = False
            
            # Start recognition task
            self.recognition_task = self.speech_recognizer.recognitionTaskWithRequest_delegate_(
                self.recognition_request, self.delegate
            )
            
            # Get input node and configure optimal audio format
            input_node = self.audio_engine.inputNode()
            input_format = input_node.outputFormatForBus_(0)
            
            print(f"🎤 Input format: {input_format.sampleRate()}Hz, {input_format.channelCount()} channels")
            
            # Create optimal format for speech recognition (16kHz mono is often best)
            # But we'll use the device's native format to avoid conversion issues
            recording_format = input_format
            
            # Install tap on input node with audio monitoring
            buffer_size = 1024
            buffer_count = [0]  # Use list to allow modification in closure
            
            def audio_tap_block(buffer, time):
                """Process audio buffer and monitor levels"""
                # Append to recognition request
                self.recognition_request.appendAudioPCMBuffer_(buffer)
                
                # Monitor audio levels
                buffer_count[0] += 1
                if buffer_count[0] % 50 == 0:  # Every ~0.5 seconds at 44kHz
                    try:
                        # Get audio level from buffer 
                        channel_count = buffer.format().channelCount()
                        frame_length = buffer.frameLength()
                        if frame_length > 0:
                            print(f"🎵 Audio: {buffer_count[0]} buffers, {channel_count} channels, {frame_length} frames")
                    except:
                        print(f"🎵 Audio: {buffer_count[0]} buffers received")
            
            input_node.installTapOnBus_bufferSize_format_block_(
                0, buffer_size, input_format, audio_tap_block
            )
            
            # Start audio engine
            self.audio_engine.prepare()
            self.audio_engine.startAndReturnError_(None)
            
            # Wait for recognition to complete or timeout
            start_wait = time.time()
            print(f"⏱️ Waiting for recognition (timeout: {timeout}s)...")
            check_count = 0
            while not self.delegate.is_finished and (time.time() - start_wait) < timeout:
                NSRunLoop.currentRunLoop().runUntilDate_(
                    objc.lookUpClass('NSDate').dateWithTimeIntervalSinceNow_(0.1)
                )
                check_count += 1
                if check_count % 10 == 0:  # Every second
                    elapsed = time.time() - start_wait
                    print(f"🔄 Recognition in progress... {elapsed:.1f}s elapsed")
            
            elapsed_total = time.time() - start_wait
            print(f"⏱️ Recognition wait completed after {elapsed_total:.1f}s")
            print(f"🔍 Delegate finished: {self.delegate.is_finished}")
            print(f"🔍 Recognition task state: {self.recognition_task.state() if self.recognition_task else 'None'}")
            
            # Stop audio engine
            self.audio_engine.stop()
            input_node.removeTapOnBus_(0)
            
            # Finish the request
            self.recognition_request.endAudio()
            
            recognition_time = time.time() - start_time
            
            if self.delegate.recognition_error:
                print(f"❌ Speech recognition error: {self.delegate.recognition_error}")
                self.status = STTStatus.ERROR
                return None
            
            if not self.delegate.recognition_result:
                # Check if we have partial results as fallback
                if hasattr(self.delegate, 'partial_result') and self.delegate.partial_result:
                    print(f"💡 Using partial result: '{self.delegate.partial_result.strip()}'")
                    
                    stt_result = STTResult(
                        text=self.delegate.partial_result.strip(),
                        confidence=0.7,  # Lower confidence for partial results
                        language=self.locale,
                        duration=recognition_time,
                        engine="macos_native",
                        raw_data={
                            "partial_result": True,
                            "recognition_time": recognition_time,
                            "locale": self.locale,
                            "on_device": True
                        }
                    )
                    
                    self.status = STTStatus.IDLE
                    return stt_result
                else:
                    print("⚠️ No recognition result received")
                    print(f"🔍 Task completed: {self.delegate.is_finished}")
                    print(f"🔍 Audio engine was running: {elapsed_total > 0}")
                    self.status = STTStatus.IDLE
                    return None
            
            # Get the best transcription
            result = self.delegate.recognition_result
            best_transcription = result.bestTranscription()
            text = best_transcription.formattedString()
            
            # Get confidence (if available)
            confidence = 0.9  # Default high confidence for macOS native
            if hasattr(best_transcription, 'averageConfidence'):
                confidence = float(best_transcription.averageConfidence())
            
            if text:
                print(f"✅ Transcribed: '{text}' (confidence: {confidence:.2f})")
                
                stt_result = STTResult(
                    text=text.strip(),
                    confidence=confidence,
                    language=self.locale,
                    duration=recognition_time,
                    engine="macos_native",
                    raw_data={
                        "sf_result": str(result),
                        "recognition_time": recognition_time,
                        "locale": self.locale,
                        "on_device": hasattr(self.recognition_request, 'requiresOnDeviceRecognition') and 
                                   self.recognition_request.requiresOnDeviceRecognition()
                    }
                )
                
                self.status = STTStatus.IDLE
                return stt_result
            else:
                print("⚠️ Empty transcription result")
                self.status = STTStatus.IDLE
                return None
                
        except Exception as e:
            print(f"❌ Speech recognition failed: {e}")
            self.status = STTStatus.ERROR
            return None
        finally:
            # Ensure complete cleanup
            try:
                if self.audio_engine and self.audio_engine.isRunning():
                    self.audio_engine.stop()
                if self.audio_engine:
                    input_node = self.audio_engine.inputNode()
                    try:
                        input_node.removeTapOnBus_(0)
                    except:
                        pass
                if self.recognition_request:
                    try:
                        self.recognition_request.endAudio()
                    except:
                        pass
                self.recognition_request = None
                self.recognition_task = None
            except Exception as e:
                print(f"⚠️ Cleanup error: {e}")
    
    def listen_once(self, timeout: float = 5.0, phrase_timeout: float = 1.0) -> Optional[STTResult]:
        """Listen for a single phrase and transcribe it"""
        if not self.is_available():
            return None
        
        print(f"🎤 Listening for {timeout}s...")
        return self._recognize_speech_from_microphone(timeout)
    
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
        
        print("🎤 Continuous listening started (macOS Native)")
        return True
    
    def _continuous_listening_worker(self):
        """Worker thread for continuous listening"""
        print("🔄 Starting continuous listening worker...")
        
        while self.continuous_listening and not self.stop_continuous.is_set():
            try:
                # Listen for a phrase
                result = self.listen_once(timeout=8.0, phrase_timeout=2.0)
                
                if result and self.continuous_callback:
                    self.continuous_callback(result)
                
                # Small delay between listening cycles
                if not self.stop_continuous.wait(0.5):
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
        
        print("🎤 Continuous listening stopped (macOS Native)")
        return True
    
    def get_status(self) -> STTStatus:
        """Get current engine status"""
        return self.status
    
    def get_supported_languages(self) -> List[str]:
        """Get list of supported languages"""
        return self.supported_languages.copy()
    
    def set_locale(self, locale: str) -> bool:
        """Switch to a different locale"""
        if locale == self.locale and self.speech_recognizer:
            return True
        
        # Stop continuous listening if active
        if self.continuous_listening:
            self.stop_continuous_listening()
        
        self.locale = locale
        return self.initialize()
    
    def cleanup(self):
        """Clean up resources"""
        if self.continuous_listening:
            self.stop_continuous_listening()
        
        if self.audio_engine and self.audio_engine.isRunning():
            self.audio_engine.stop()
        
        if self.recognition_task:
            self.recognition_task.cancel()
        
        self.speech_recognizer = None
        self.audio_engine = None
        self.recognition_request = None
        self.recognition_task = None
        self.delegate = None
        self.status = STTStatus.DISABLED


if __name__ == "__main__":
    # Test macOS Native STT Engine
    print("🧪 Testing macOS Native STT Engine")
    print(f"Platform: {platform.system()} {platform.mac_ver()[0] if MACOS_AVAILABLE else 'N/A'}")
    
    if not MACOS_AVAILABLE:
        print("❌ This engine only works on macOS")
        exit(1)
    
    if not MACOS_VERSION_OK:
        print("❌ Requires macOS 10.15 (Catalina) or later")
        exit(1)
    
    engine = MacOSSTTEngine("en-US")
    
    if engine.initialize():
        print("✅ macOS Native engine initialized")
        
        # Test single recognition
        print("\n🎤 Say something...")
        result = engine.listen_once(timeout=10.0)
        
        if result:
            print(f"✅ Recognition result:")
            print(f"   Text: {result.text}")
            print(f"   Confidence: {result.confidence:.2f}")
            print(f"   Language: {result.language}")
            print(f"   Duration: {result.duration:.2f}s")
            print(f"   Engine: {result.engine}")
            print(f"   On-device: {result.raw_data.get('on_device', 'Unknown')}")
        else:
            print("⚠️ No speech recognized")
    else:
        print("❌ Failed to initialize macOS Native engine")
    
    engine.cleanup()
    print("🧪 Test complete")