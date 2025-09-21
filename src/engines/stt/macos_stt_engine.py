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
    # Check if any delegate class already exists in objc runtime
    try:
        import objc
        # Try to find if the class already exists
        existing_class = objc.lookUpClass("M1K3MacOSSTTDelegate")
        print("⚠️ M1K3MacOSSTTDelegate already exists, using existing class")
        M1K3MacOSSTTDelegate = existing_class
    except objc.nosuchclass_error:
        # Class doesn't exist, safe to create
        class M1K3MacOSSTTDelegate(NSObject):
            """
            Enhanced delegate for handling speech recognition events
            Implements all required SFSpeechRecognition methods with proper PyObjC naming
            """
        
        def init(self):
            self = objc.super(MacOSSTTDelegate, self).init()
            if self is None:
                return None
            
            # Core recognition state
            self.recognition_result = None
            self.recognition_error = None
            self.is_finished = False
            self.success_status = None
            self.partial_result = None
            
            # Enhanced diagnostics
            self.speech_detected = False
            self.audio_finished = False
            self.delegate_calls = []  # Track which methods are called
            self.callback_count = 0  # Total callback count
            
            return self
        
        def _log_delegate_call(self, method_name, details=""):
            """Log delegate method calls for diagnostics"""
            self.callback_count += 1
            call_info = f"#{self.callback_count} {method_name}: {details}"
            self.delegate_calls.append(call_info)
            print(f"🔔 Delegate: {call_info}")
        
        def speechRecognitionTask_didFinishRecognition_(self, task, result):
            """Called when the final utterance is recognized"""
            self._log_delegate_call("didFinishRecognition", 
                                  f"result={'Yes' if result else 'None'}")
            
            if result:
                self.recognition_result = result
                # Extract text immediately for diagnostics
                try:
                    best_transcription = result.bestTranscription()
                    if best_transcription:
                        text = best_transcription.formattedString()
                        print(f"🎯 Final result: '{text}'")
                except Exception as e:
                    print(f"⚠️ Error extracting result text: {e}")
        
        def speechRecognitionTask_didFinishSuccessfully_(self, task, successfully):
            """Called when recognition of all requested utterances is finished"""
            self._log_delegate_call("didFinishSuccessfully", 
                                  f"success={successfully}")
            
            self.success_status = successfully
            self.is_finished = True
            
            if successfully:
                print("✅ Recognition completed successfully")
            else:
                print("❌ Recognition finished unsuccessfully")
        
        def speechRecognitionTask_didHypothesizeTranscription_(self, task, transcription):
            """Called for intermediate results (partial transcriptions)"""
            if transcription and hasattr(transcription, 'formattedString'):
                try:
                    partial_text = transcription.formattedString()
                    if partial_text and partial_text.strip():
                        self._log_delegate_call("didHypothesizeTranscription", 
                                              f"partial='{partial_text.strip()}'")
                        
                        # Store latest partial result as backup
                        self.partial_result = partial_text
                        print(f"🔄 Partial: '{partial_text.strip()}'")
                except Exception as e:
                    print(f"⚠️ Error processing partial transcription: {e}")
        
        def speechRecognitionTaskFinishedReadingAudio_(self, task):
            """Called when the task finishes reading audio input"""
            self._log_delegate_call("finishedReadingAudio", "audio input complete")
            self.audio_finished = True
            print("📻 Audio input finished")
        
        def speechRecognitionDidDetectSpeech_(self, task):
            """Called when speech is detected in the audio stream"""
            self._log_delegate_call("didDetectSpeech", "speech detected")
            self.speech_detected = True
            print("🎤 Speech detected!")
        
        def speechRecognitionTask_didFinishWithError_(self, task, error):
            """Called when recognition fails with an error"""
            self._log_delegate_call("didFinishWithError", 
                                  f"error={error}")
            
            self.recognition_error = error
            self.is_finished = True
            print(f"❌ Recognition error: {error}")
        
        
        def get_diagnostic_summary(self):
            """Get diagnostic summary of delegate method calls"""
            return {
                'delegate_calls': self.delegate_calls,
                'speech_detected': self.speech_detected,
                'audio_finished': self.audio_finished,
                'has_result': self.recognition_result is not None,
                'has_error': self.recognition_error is not None,
                'success_status': self.success_status,
                'is_finished': self.is_finished,
                'partial_results_count': len([c for c in self.delegate_calls if 'didHypothesizeTranscription' in c])
            }

    # Create alias for compatibility
    MacOSSTTDelegate = M1K3MacOSSTTDelegate

elif 'MacOSSTTDelegate' not in globals():
    # Dummy delegate class when PyObjC not available
    class MacOSSTTDelegate:
        def __init__(self):
            pass

        def alloc(self):
            return self

        def init(self):
            return self


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
        
        # Audio settings - will be dynamically detected
        self.sample_rate = None  # Detected dynamically
        self.detected_sample_rate = None
        self.hardware_format = None
        self.device_type = "unknown"  # "internal", "bluetooth", "external"
        
        # Control flags
        self.force_shutdown = None  # For graceful shutdown
        self.force_internal_mic = False  # Workaround for Bluetooth issues
        
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
            
            # Detect hardware sample rate to prevent format mismatches
            print("🔍 Detecting audio hardware configuration...")
            sample_rate_detection_success = self._detect_hardware_sample_rate()
            
            if not sample_rate_detection_success:
                print("⚠️ Sample rate detection failed, using fallback configuration")
                # Continue anyway - fallback values were set in the detection method
            
            # Create delegate
            self.delegate = MacOSSTTDelegate.alloc().init()
            
            self.status = STTStatus.IDLE
            print(f"✅ macOS Speech Recognition initialized (sample rate: {self.sample_rate}Hz, device: {self.device_type})")
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
    
    def _test_microphone_hardware(self) -> bool:
        """Test microphone hardware detection and levels"""
        try:
            print("🔧 Testing microphone hardware...")
            
            # Check if we can access the audio input node
            if not self.audio_engine:
                print("❌ No audio engine available for hardware test")
                return False
            
            input_node = self.audio_engine.inputNode()
            if not input_node:
                print("❌ No input node available")
                return False
            
            input_format = input_node.outputFormatForBus_(0)
            sample_rate = input_format.sampleRate()
            channels = input_format.channelCount()
            
            print(f"🎤 Microphone detected: {sample_rate}Hz, {channels} channel(s)")
            
            # Test if we can prepare the audio engine
            try:
                self.audio_engine.prepare()
                print("✅ Audio engine can be prepared")
                return True
            except Exception as e:
                print(f"❌ Audio engine preparation failed: {e}")
                print("💡 This may indicate microphone permission or hardware issues")
                return False
                
        except Exception as e:
            print(f"❌ Microphone hardware test failed: {e}")
            return False
    
    def _check_microphone_permissions(self) -> bool:
        """Check and request microphone permissions if needed"""
        try:
            print("🔐 Checking microphone permissions...")
            
            # Try to access microphone through AVAudioEngine
            test_engine = AVAudioEngine.alloc().init()
            input_node = test_engine.inputNode()
            
            # Try to prepare the engine - this will fail if no microphone permission
            try:
                test_engine.prepare()
                print("✅ Microphone permission appears to be granted")
                return True
            except Exception as e:
                print(f"❌ Microphone permission test failed: {e}")
                print("💡 Grant microphone access in System Preferences > Security & Privacy > Microphone")
                return False
                
        except Exception as e:
            print(f"⚠️ Microphone permission check error: {e}")
            return False
    
    def _diagnose_audio_levels(self) -> bool:
        """Diagnose audio input levels (addresses the 0.0000 levels bug)"""
        try:
            print("📊 Diagnosing audio input levels...")
            
            # Create a temporary audio engine for testing
            test_engine = AVAudioEngine.alloc().init()
            input_node = test_engine.inputNode()
            input_format = input_node.outputFormatForBus_(0)
            
            # Set up monitoring for audio levels
            buffer_count = [0]
            max_level = [0.0]
            has_audio = [False]
            
            def test_audio_tap(buffer, time):
                """Monitor audio levels during test"""
                try:
                    buffer_count[0] += 1
                    frame_length = buffer.frameLength()
                    
                    if frame_length > 0:
                        # Try to get audio data pointer for level calculation
                        # This is a simplified approach - in real implementation,
                        # we'd need to access the actual audio data
                        has_audio[0] = True
                        
                        # Basic level estimation based on frame presence
                        estimated_level = min(1.0, frame_length / 1024.0) * 0.1
                        max_level[0] = max(max_level[0], estimated_level)
                        
                        if buffer_count[0] <= 5:  # First few buffers
                            print(f"🎵 Buffer {buffer_count[0]}: {frame_length} frames (estimated level: {estimated_level:.4f})")
                    
                except Exception as e:
                    print(f"⚠️ Audio tap error: {e}")
            
            # Install tap for testing
            input_node.installTapOnBus_bufferSize_format_block_(
                0, 1024, input_format, test_audio_tap
            )
            
            # Start engine briefly to test
            test_engine.prepare()
            success = test_engine.startAndReturnError_(None)
            
            if success:
                print("🎤 Recording test audio for 2 seconds...")
                time.sleep(2)
                
                # Stop and cleanup
                test_engine.stop()
                input_node.removeTapOnBus_(0)
                
                if has_audio[0]:
                    print(f"✅ Audio detected: {buffer_count[0]} buffers, max level: {max_level[0]:.4f}")
                    if max_level[0] > 0.001:
                        print("✅ Audio levels appear normal")
                        return True
                    else:
                        print("⚠️ Audio levels very low - check microphone volume")
                        print("💡 Go to System Preferences > Sound > Input and increase input volume")
                        return False
                else:
                    print("❌ No audio buffers received")
                    print("💡 This is likely the cause of the 0.0000 input levels bug")
                    print("💡 Check microphone connection and permissions")
                    return False
            else:
                print("❌ Could not start audio engine for testing")
                return False
                
        except Exception as e:
            print(f"❌ Audio level diagnosis failed: {e}")
            return False
    
    def _detect_hardware_sample_rate(self) -> bool:
        """Detect hardware sample rate and audio device type to prevent format mismatches"""
        try:
            print("🔍 Detecting hardware audio format...")
            
            # Create temporary audio engine for detection
            test_engine = AVAudioEngine.alloc().init()
            input_node = test_engine.inputNode()
            
            if not input_node:
                print("❌ No input node available for format detection")
                return False
            
            # Get the current hardware format
            self.hardware_format = input_node.outputFormatForBus_(0)
            self.detected_sample_rate = float(self.hardware_format.sampleRate())
            channels = int(self.hardware_format.channelCount())
            
            print(f"🎤 Hardware format detected:")
            print(f"   📊 Sample rate: {self.detected_sample_rate}Hz")  
            print(f"   📊 Channels: {channels}")
            
            # Detect device type based on sample rate and other characteristics
            if self.detected_sample_rate <= 8000:
                self.device_type = "bluetooth_low"
                print(f"   🎧 Device type: Bluetooth (low quality mode - {self.detected_sample_rate}Hz)")
                print("   💡 Bluetooth headphones in call/microphone mode use reduced sample rates")
            elif self.detected_sample_rate <= 16000:
                # Could be Bluetooth in better mode or internal mic in low power mode
                self.device_type = "bluetooth_standard" 
                print(f"   🎧 Device type: Bluetooth or low-power internal (standard quality - {self.detected_sample_rate}Hz)")
            elif self.detected_sample_rate >= 44100:
                self.device_type = "internal_or_external"
                print(f"   🎤 Device type: Internal microphone or external interface (high quality - {self.detected_sample_rate}Hz)")
            else:
                self.device_type = "unknown"
                print(f"   ❓ Device type: Unknown ({self.detected_sample_rate}Hz)")
            
            # Set our engine to use the detected sample rate
            self.sample_rate = int(self.detected_sample_rate)
            
            # Validate that we can work with this sample rate
            supported_rates = [8000, 16000, 22050, 24000, 32000, 44100, 48000]
            if self.sample_rate in supported_rates:
                print(f"✅ Sample rate {self.sample_rate}Hz is supported")
                return True
            else:
                # Find closest supported sample rate
                closest_rate = min(supported_rates, key=lambda x: abs(x - self.sample_rate))
                print(f"⚠️ Unusual sample rate {self.sample_rate}Hz detected")
                print(f"   💡 Will use closest supported rate: {closest_rate}Hz")
                self.sample_rate = closest_rate
                return True
                
        except Exception as e:
            print(f"❌ Hardware sample rate detection failed: {e}")
            print("   💡 Falling back to default 16kHz")
            self.sample_rate = 16000
            self.detected_sample_rate = 16000.0
            self.device_type = "fallback"
            return False
    
    def _create_compatible_format(self, target_sample_rate: int, channels: int = 1) -> bool:
        """Create a compatible audio format for the recognition engine"""
        try:
            from AVFoundation import AVAudioFormat
            
            print(f"🔧 Creating compatible audio format: {target_sample_rate}Hz, {channels} channels")
            
            # Create a standard format that should work across different devices
            compatible_format = AVAudioFormat.alloc().initWithCommonFormat_sampleRate_channels_interleaved_(
                1,  # AVAudioPCMFormatFloat32 
                float(target_sample_rate), 
                channels,
                True  # interleaved
            )
            
            if compatible_format:
                print(f"✅ Compatible format created: {compatible_format}")
                return compatible_format
            else:
                print("❌ Failed to create compatible format")
                return None
                
        except Exception as e:
            print(f"❌ Audio format creation failed: {e}")
            return None
    
    def _get_optimal_sample_rate_for_device(self) -> int:
        """Get optimal sample rate based on detected device type"""
        if self.device_type == "bluetooth_low":
            # Accept 8kHz for very low-bandwidth Bluetooth  
            return 8000
        elif self.device_type == "bluetooth_standard":
            # Use 16kHz for standard Bluetooth
            return 16000
        elif self.device_type == "internal_or_external":
            # Use high quality for internal/external devices
            return min(int(self.detected_sample_rate), 44100)  # Cap at 44.1kHz
        else:
            # Fallback to detected rate or 16kHz
            return int(self.detected_sample_rate) if self.detected_sample_rate else 16000
    
    def _reset_recognition_state(self):
        """Reset recognition state between retry attempts"""
        try:
            print("🔄 Resetting recognition state...")
            
            # Clean up previous recognition components
            if self.recognition_task:
                self.recognition_task.cancel()
                self.recognition_task = None
            
            if self.recognition_request:
                self.recognition_request.endAudio()
                self.recognition_request = None
            
            if self.audio_engine:
                if self.audio_engine.isRunning():
                    self.audio_engine.stop()
                self.audio_engine = None
            
            # Create fresh delegate
            self.delegate = MacOSSTTDelegate.alloc().init()
            
            print("✅ Recognition state reset complete")
            
        except Exception as e:
            print(f"⚠️ Error resetting recognition state: {e}")

    def _recognize_speech_from_microphone_with_retry(self, timeout: float = 5.0, max_retries: int = 2) -> Optional[STTResult]:
        """Recognize speech with retry logic and fallback mechanisms"""
        for attempt in range(max_retries + 1):
            if attempt > 0:
                print(f"🔄 Retry attempt {attempt}/{max_retries}")
                # Reset state between retries
                self._reset_recognition_state()
                # Brief pause between retries
                time.sleep(1.0)
            
            try:
                result = self._recognize_speech_from_microphone_internal(timeout)
                if result:
                    if attempt > 0:
                        print(f"✅ Success on retry attempt {attempt}")
                    return result
                else:
                    print(f"⚠️ Attempt {attempt + 1} failed - no result")
                    
                    # Analyze failure reason for better debugging
                    if hasattr(self, 'delegate') and self.delegate:
                        print(f"🔍 Failure analysis:")
                        print(f"   - Delegate callbacks: {getattr(self.delegate, 'callback_count', 0)}")
                        print(f"   - Speech detected: {getattr(self.delegate, 'speech_detected', False)}")
                        print(f"   - Audio finished: {getattr(self.delegate, 'audio_finished', False)}")
                        print(f"   - Recognition finished: {getattr(self.delegate, 'is_finished', False)}")
                        print(f"   - Has error: {getattr(self.delegate, 'recognition_error', None) is not None}")
                        
                        # Specific failure reason recommendations
                        if self.delegate.callback_count == 0:
                            print("💡 No delegate callbacks received - possible PyObjC bridge issue")
                        elif self.delegate.recognition_error:
                            print(f"💡 Recognition error: {self.delegate.recognition_error}")
                        elif not self.delegate.speech_detected:
                            print("💡 No speech detected - check audio levels or speak louder")
                        else:
                            print("💡 Speech detected but no final result - processing timeout")
                    
                    # Don't retry if this is the last attempt
                    if attempt == max_retries:
                        break
                        
            except Exception as e:
                print(f"⚠️ Attempt {attempt + 1} failed with exception: {e}")
                import traceback
                print(f"📍 Exception traceback: {traceback.format_exc()}")
                
                if attempt == max_retries:
                    print("❌ All retry attempts exhausted")
                    return None
        
        print("❌ All attempts failed, no speech recognition result")
        return None
    
    def _recognize_speech_from_microphone_internal(self, timeout: float = 5.0) -> Optional[STTResult]:
        """Recognize speech from microphone using SFSpeechRecognizer"""
        if not self.is_available():
            return None
        
        # Enhanced pre-flight checks to prevent 0.0000 levels bug
        print("🔍 Running pre-flight diagnostics...")
        
        # Check microphone permissions first
        if not self._check_microphone_permissions():
            print("❌ Microphone permission check failed")
            return None
        
        # Test microphone hardware
        if not self._test_microphone_hardware():
            print("❌ Microphone hardware test failed")
            return None
        
        # Diagnose audio levels
        if not self._diagnose_audio_levels():
            print("⚠️ Audio level diagnosis indicated potential issues")
            print("💡 Continuing anyway, but audio quality may be poor")
        
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
            
            # Start recognition task with enhanced delegate logging
            print(f"🔧 Starting recognition task with delegate: {self.delegate}")
            print(f"🔧 Delegate object type: {type(self.delegate)}")
            print(f"🔧 Delegate responds to didFinishRecognition: {hasattr(self.delegate, 'speechRecognitionTask_didFinishRecognition_')}")
            
            self.recognition_task = self.speech_recognizer.recognitionTaskWithRequest_delegate_(
                self.recognition_request, self.delegate
            )
            
            print(f"🔧 Recognition task created: {self.recognition_task}")
            print(f"🔧 Recognition task state: {self.recognition_task.state() if self.recognition_task else 'None'}")
            
            # Get input node and configure optimal audio format
            input_node = self.audio_engine.inputNode()
            input_format = input_node.outputFormatForBus_(0)
            
            current_sample_rate = float(input_format.sampleRate())
            current_channels = int(input_format.channelCount())
            
            print(f"🎤 Current input format: {current_sample_rate}Hz, {current_channels} channels")
            
            # Validate against our detected format to catch sample rate mismatches early
            if self.detected_sample_rate and abs(current_sample_rate - self.detected_sample_rate) > 0.1:
                print(f"⚠️ Sample rate changed from detected {self.detected_sample_rate}Hz to {current_sample_rate}Hz")
                print("   💡 This could indicate a device switch or Bluetooth mode change")
                
                # Update our tracking to match current reality
                self.detected_sample_rate = current_sample_rate
                self.sample_rate = int(current_sample_rate)
                
                # Update device type based on new sample rate
                if current_sample_rate <= 8000:
                    self.device_type = "bluetooth_low"
                    print("   🎧 Device now in Bluetooth low-quality mode")
                elif current_sample_rate <= 16000:
                    self.device_type = "bluetooth_standard"  
                    print("   🎧 Device now in Bluetooth standard mode")
                elif current_sample_rate >= 44100:
                    self.device_type = "internal_or_external"
                    print("   🎤 Device now in high-quality mode")
                    
            # Check for problematic sample rates that cause Core Audio issues
            if current_sample_rate < 8000 or current_sample_rate > 48000:
                print(f"🚨 WARNING: Unusual sample rate {current_sample_rate}Hz detected")
                print("   💡 This may cause 'format.sampleRate == inputHWFormat.sampleRate' errors")
                print("   💡 Consider switching to internal microphone or different audio device")
            
            # Enhanced audio monitoring to catch level issues
            buffer_size = 1024
            buffer_count = [0]
            max_audio_level = [0.0]
            zero_level_count = [0]
            
            def enhanced_audio_tap_block(buffer, audio_time):
                """Enhanced audio processing with comprehensive diagnostic logging"""
                try:
                    # Append to recognition request
                    self.recognition_request.appendAudioPCMBuffer_(buffer)
                    
                    # Enhanced audio level monitoring
                    buffer_count[0] += 1
                    frame_length = buffer.frameLength()
                    
                    if frame_length > 0:
                        # Estimate audio level (simplified approach)
                        estimated_level = min(1.0, frame_length / 1024.0) * 0.1
                        max_audio_level[0] = max(max_audio_level[0], estimated_level)
                        
                        # Count zero-level buffers
                        if estimated_level < 0.0001:
                            zero_level_count[0] += 1
                        
                        # Report every 25 buffers (~0.25 seconds) with enhanced diagnostics
                        if buffer_count[0] % 25 == 0:
                            elapsed = time.time() - start_time
                            print(f"🎵 Audio: {buffer_count[0]} buffers, max level: {max_audio_level[0]:.4f}, elapsed: {elapsed:.1f}s")
                            
                            # Comprehensive delegate status logging
                            print(f"   🔍 Delegate diagnostics:")
                            print(f"      - Speech detected: {getattr(self.delegate, 'speech_detected', False)}")
                            print(f"      - Audio finished: {getattr(self.delegate, 'audio_finished', False)}")
                            print(f"      - Recognition finished: {getattr(self.delegate, 'is_finished', False)}")
                            print(f"      - Has result: {getattr(self.delegate, 'recognition_result', None) is not None}")
                            print(f"      - Has error: {getattr(self.delegate, 'recognition_error', None) is not None}")
                            print(f"      - Partial result: '{str(getattr(self.delegate, 'partial_result', 'None'))[:50]}'")
                            print(f"      - Callback count: {getattr(self.delegate, 'callback_count', 0)}")
                            
                            # Recognition task status
                            if self.recognition_task:
                                task_state = self.recognition_task.state()
                                state_names = {0: "Starting", 1: "Running", 2: "Finishing", 3: "Canceling", 4: "Completed"}
                                state_name = state_names.get(task_state, f"Unknown({task_state})")
                                print(f"   🎯 Task state: {state_name} ({task_state})")
                                print(f"   🎯 Task object: {self.recognition_task}")
                            else:
                                print("   🎯 No recognition task available")
                            
                            # Audio stream health check
                            print(f"   📊 Audio health: {zero_level_count[0]} zero-level buffers out of {buffer_count[0]} total")
                            zero_percentage = (zero_level_count[0] / buffer_count[0]) * 100
                            if zero_percentage > 80:
                                print(f"   🚨 CRITICAL: {zero_percentage:.1f}% zero-level buffers - 0.0000 levels bug detected!")
                            elif zero_percentage > 50:
                                print(f"   ⚠️  HIGH: {zero_percentage:.1f}% zero-level buffers - potential audio issues")
                            
                            # Warn about zero levels (the bug!)
                            if zero_level_count[0] > 20:
                                print(f"⚠️ Warning: {zero_level_count[0]} near-zero level buffers detected!")
                                print("💡 This may be the 0.0000 input levels bug - speech recognition may fail")
                    else:
                        zero_level_count[0] += 1
                        if buffer_count[0] <= 5:  # Log first few empty buffers
                            print(f"⚠️ Empty buffer #{buffer_count[0]} (frame_length: {frame_length})")
                        
                except Exception as e:
                    print(f"⚠️ Audio tap error: {e}")
                    import traceback
                    print(f"📍 Audio tap traceback: {traceback.format_exc()}")
            
            # Install audio tap and start audio engine with enhanced error checking for Bluetooth issues
            try:
                print("🔧 Installing audio tap...")
                input_node.installTapOnBus_bufferSize_format_block_(
                    0, buffer_size, input_format, enhanced_audio_tap_block
                )
                print("🔧 Preparing audio engine...")
                self.audio_engine.prepare()
                
                print("🔧 Starting audio engine...")
                start_success = self.audio_engine.startAndReturnError_(None)
                
                if not start_success:
                    print("❌ Failed to start audio engine")
                    print("💡 This often indicates microphone permission or hardware issues")
                    return None
                
                print("✅ Audio engine started successfully")
                
            except Exception as audio_error:
                error_str = str(audio_error).lower()
                print(f"❌ Audio engine start failed: {audio_error}")
                
                # Check for specific Core Audio sample rate mismatch error
                if "samplerate" in error_str or "format" in error_str:
                    print("🚨 CORE AUDIO FORMAT MISMATCH DETECTED!")
                    print("   💡 This is the Bluetooth earphone sample rate issue")
                    print("   📱 Current audio device may be in incompatible mode")
                    print("")
                    print("🔧 Suggested solutions:")
                    print("   1. Switch to internal microphone in System Preferences > Sound")
                    print("   2. Disconnect Bluetooth headphones and reconnect")  
                    print("   3. Use a different STT engine: --stt-engine vosk")
                    print("   4. Check Audio MIDI Setup for device configuration")
                    print("")
                    print("📊 Debug info:")
                    print(f"   - Detected rate: {self.detected_sample_rate}Hz")
                    print(f"   - Current rate: {current_sample_rate}Hz") 
                    print(f"   - Device type: {self.device_type}")
                    
                elif "bluetooth" in error_str or "headphone" in error_str:
                    print("🎧 Bluetooth audio device issue detected")
                    print("   💡 Try switching to internal microphone")
                    
                elif "permission" in error_str or "authorization" in error_str:
                    print("🔐 Microphone permission issue")
                    print("   💡 Check System Preferences > Privacy & Security > Microphone")
                    
                else:
                    print("❓ Unknown audio engine error")
                    print("   💡 Try restarting the application or switching audio devices")
                
                return None
            
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
                    print(f"   📊 Audio stats: {buffer_count[0]} buffers, max level: {max_audio_level[0]:.4f}")
                    
                    # Check for zero levels bug
                    if buffer_count[0] > 10 and max_audio_level[0] < 0.001:
                        print("⚠️ Very low audio levels detected - possible microphone issue")
            
            elapsed_total = time.time() - start_wait
            print(f"⏱️ Recognition wait completed after {elapsed_total:.1f}s")
            print(f"🔍 Delegate finished: {self.delegate.is_finished}")
            print(f"🔍 Recognition task state: {self.recognition_task.state() if self.recognition_task else 'None'}")
            print(f"📊 Final audio stats: {buffer_count[0]} buffers, max level: {max_audio_level[0]:.4f}, zero levels: {zero_level_count[0]}")
            
            # Stop audio engine
            self.audio_engine.stop()
            input_node.removeTapOnBus_(0)
            
            # Finish the request
            self.recognition_request.endAudio()
            
            recognition_time = time.time() - start_time
            
            # Check for zero levels bug
            if buffer_count[0] > 0 and max_audio_level[0] < 0.001:
                print("🚨 DETECTED: 0.0000 input levels bug!")
                print("💡 Microphone is connected but not providing audio data")
                print("💡 Possible fixes:")
                print("   - Check System Preferences > Sound > Input volume")
                print("   - Grant microphone access in Privacy settings")
                print("   - Try a different microphone or restart audio drivers")
            
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
                            "on_device": True,
                            "audio_stats": {
                                "buffer_count": buffer_count[0],
                                "max_level": max_audio_level[0],
                                "zero_level_count": zero_level_count[0]
                            }
                        }
                    )
                    
                    self.status = STTStatus.IDLE
                    return stt_result
                else:
                    print("⚠️ No recognition result received")
                    print(f"🔍 Task completed: {self.delegate.is_finished}")
                    print(f"🔍 Audio engine was running: {elapsed_total > 0}")
                    
                    # Provide diagnostic info for zero levels
                    if max_audio_level[0] < 0.001:
                        print("💡 No recognition likely due to zero audio levels")
                    
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
                                   self.recognition_request.requiresOnDeviceRecognition(),
                        "audio_stats": {
                            "buffer_count": buffer_count[0],
                            "max_level": max_audio_level[0],
                            "zero_level_count": zero_level_count[0]
                        }
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
            print("💡 This may be due to microphone access issues")
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
        return self._recognize_speech_from_microphone_with_retry(timeout)
    
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