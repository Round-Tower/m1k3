#!/usr/bin/env python3
"""
STT Manager - Speech-to-Text Engine Manager for M1K3
Handles multiple STT backends and provides unified interface
"""

import time
import threading
from abc import ABC, abstractmethod
from typing import Optional, Callable, Dict, Any, List
from enum import Enum
from dataclasses import dataclass


class STTStatus(Enum):
    """STT Engine Status"""
    IDLE = "idle"
    LISTENING = "listening"
    PROCESSING = "processing"
    ERROR = "error"
    DISABLED = "disabled"


@dataclass
class STTResult:
    """Speech recognition result"""
    text: str
    confidence: float
    language: str
    duration: float
    engine: str
    raw_data: Optional[Dict[str, Any]] = None


class STTEngine(ABC):
    """Abstract base class for STT engines"""
    
    @abstractmethod
    def initialize(self) -> bool:
        """Initialize the STT engine"""
        pass
    
    @abstractmethod
    def is_available(self) -> bool:
        """Check if engine is available"""
        pass
    
    @abstractmethod
    def listen_once(self, timeout: float = 5.0, phrase_timeout: float = 1.0) -> Optional[STTResult]:
        """Listen for a single phrase"""
        pass
    
    @abstractmethod
    def start_continuous_listening(self, callback: Callable[[STTResult], None]) -> bool:
        """Start continuous listening mode"""
        pass
    
    @abstractmethod
    def stop_continuous_listening(self) -> bool:
        """Stop continuous listening mode"""
        pass
    
    @abstractmethod
    def get_status(self) -> STTStatus:
        """Get current engine status"""
        pass
    
    @abstractmethod
    def get_supported_languages(self) -> List[str]:
        """Get list of supported languages"""
        pass


class STTManager:
    """Manages multiple STT engines and provides unified interface"""
    
    def __init__(self):
        self.engines: Dict[str, STTEngine] = {}
        self.current_engine: Optional[STTEngine] = None
        self.current_engine_name: Optional[str] = None
        self.status = STTStatus.IDLE
        
        # Configuration
        self.language = "en-US"
        self.confidence_threshold = 0.5
        self.timeout = 5.0
        self.phrase_timeout = 1.0
        
        # Callbacks
        self.on_listening_start: Optional[Callable] = None
        self.on_listening_stop: Optional[Callable] = None
        self.on_speech_detected: Optional[Callable[[STTResult], None]] = None
        self.on_error: Optional[Callable[[str], None]] = None
        
        # Continuous listening state
        self.continuous_listening = False
        self.listening_thread: Optional[threading.Thread] = None
        
        self._initialize_engines()
    
    def _initialize_engines(self):
        """Initialize all available STT engines in priority order (reliability first)"""
        print("🎤 Initializing STT engines...")
        
        # Priority 1: Vosk (lightweight offline, reliable)
        # NOTE: Temporarily prioritized over macOS Native due to delegate callback issues
        try:
            from .vosk_stt_engine import VoskSTTEngine
            vosk_engine = VoskSTTEngine()
            if vosk_engine.initialize():
                self.engines["vosk"] = vosk_engine
                print("✅ Vosk STT engine loaded (54MB footprint) - PRIMARY")
                if not self.current_engine or self._engine_explicitly_requested("vosk"):
                    self.current_engine = vosk_engine
                    self.current_engine_name = "vosk"
        except ImportError as e:
            print(f"⚠️ Vosk STT not available: {e}")
        except Exception as e:
            print(f"⚠️ Vosk STT initialization failed: {e}")
        
        # Priority 2: macOS Native (temporarily disabled due to Objective-C class conflicts)
        print("⚠️ macOS Native STT temporarily disabled (Objective-C class conflict - see BUGS.md)")
        
        # Priority 3: Web Speech (cloud-based fallback)
        try:
            from .web_speech_engine import WebSpeechEngine
            web_engine = WebSpeechEngine()
            if web_engine.initialize():
                self.engines["web_speech"] = web_engine
                print("✅ Web Speech STT engine loaded (0MB footprint)")
                if not self.current_engine or self._engine_explicitly_requested("web") or self._engine_explicitly_requested("web_speech"):
                    self.current_engine = web_engine
                    self.current_engine_name = "web_speech"
        except ImportError as e:
            print(f"⚠️ Web Speech STT not available: {e}")
        except Exception as e:
            print(f"⚠️ Web Speech STT initialization failed: {e}")
        
        # Priority 4: Whisper (optional, heavy but excellent quality)
        # Only initialize if explicitly requested or no other engines available
        if not self.engines or self._whisper_explicitly_requested():
            try:
                from .whisper_stt_engine import WhisperSTTEngine
                # Default to tiny model for smaller footprint
                whisper_engine = WhisperSTTEngine("tiny")
                if whisper_engine.initialize():
                    self.engines["whisper"] = whisper_engine
                    print("✅ Whisper STT engine loaded (500MB footprint)")
                    if not self.current_engine:
                        self.current_engine = whisper_engine
                        self.current_engine_name = "whisper"
            except ImportError as e:
                print(f"⚠️ Whisper STT not available: {e}")
            except Exception as e:
                print(f"⚠️ Whisper STT initialization failed: {e}")
        
        if not self.engines:
            print("❌ No STT engines available")
            print()
            print("🔧 Troubleshooting:")
            print("   • For Web Speech: Check microphone permissions")
            print("   • For Vosk: pip install vosk sounddevice")
            print("   • For macOS Native: Enable Siri in System Preferences")
            print("   • Run: stt test (to diagnose issues)")
            self.status = STTStatus.DISABLED
        else:
            print(f"🎤 STT Manager initialized with {len(self.engines)} engines")
            print(f"🎯 Active engine: {self.current_engine_name}")
            
            # Show footprint summary
            footprint_info = {
                "macos_native": "0MB (system)",
                "vosk": "54MB (14MB lib + 40MB model)",
                "web_speech": "0MB (cloud-based)",
                "whisper": "500MB+ (model dependent)"
            }
            if self.current_engine_name in footprint_info:
                print(f"📊 Current footprint: {footprint_info[self.current_engine_name]}")
            
            # Show helpful usage tips
            if len(self.engines) == 1:
                print("💡 Quick start: Type or press ENTER for voice input")
    
    def _whisper_explicitly_requested(self) -> bool:
        """Check if Whisper was explicitly requested via environment or config"""
        import os
        return (
            os.environ.get('M1K3_STT_ENGINE', '').lower() == 'whisper' or
            os.environ.get('M1K3_USE_WHISPER', '').lower() in ('1', 'true', 'yes')
        )
    
    def _engine_explicitly_requested(self, engine_name: str) -> bool:
        """Check if a specific engine was explicitly requested"""
        import os
        requested_engine = os.environ.get('M1K3_STT_ENGINE', '').lower()
        return requested_engine == engine_name.lower()
    
    def is_available(self) -> bool:
        """Check if STT is available"""
        return len(self.engines) > 0 and self.current_engine is not None
    
    def get_available_engines(self) -> List[str]:
        """Get list of available engine names"""
        return list(self.engines.keys())
    
    def switch_engine(self, engine_name: str) -> bool:
        """Switch to a different STT engine"""
        if engine_name not in self.engines:
            return False
        
        # Stop current continuous listening if active
        if self.continuous_listening:
            self.stop_continuous_listening()
        
        self.current_engine = self.engines[engine_name]
        self.current_engine_name = engine_name
        print(f"🔄 Switched to STT engine: {engine_name}")
        return True
    
    def listen_once(self, timeout: Optional[float] = None, phrase_timeout: Optional[float] = None) -> Optional[STTResult]:
        """Listen for a single phrase with automatic fallback"""
        if not self.current_engine:
            return None
        
        timeout = timeout or self.timeout
        phrase_timeout = phrase_timeout or self.phrase_timeout
        
        try:
            self.status = STTStatus.LISTENING
            if self.on_listening_start:
                self.on_listening_start()
            
            # Try current engine first
            result = self.current_engine.listen_once(timeout, phrase_timeout)
            
            # If no result and we have fallback engines available, try them
            if not result and len(self.engines) > 1:
                print(f"⚠️ {self.current_engine_name} failed, trying fallback engines...")
                
                # Get list of engines excluding current one
                fallback_engines = [(name, engine) for name, engine in self.engines.items() 
                                  if name != self.current_engine_name]
                
                for engine_name, engine in fallback_engines:
                    print(f"🔄 Trying fallback engine: {engine_name}")
                    try:
                        # Ensure clean state before trying fallback
                        if hasattr(engine, '_cleanup_audio_resources'):
                            engine._cleanup_audio_resources()
                        
                        # Small delay to allow system cleanup
                        import time
                        time.sleep(0.5)
                        
                        result = engine.listen_once(timeout * 0.8, phrase_timeout)  # Shorter timeout for fallbacks
                        if result:
                            print(f"✅ Fallback success with {engine_name}")
                            # Temporarily switch to working engine
                            self.current_engine = engine
                            self.current_engine_name = engine_name
                            print(f"🔄 Switched to working engine: {engine_name}")
                            break
                    except Exception as e:
                        print(f"❌ Fallback engine {engine_name} failed: {e}")
                        # Add recovery delay before trying next engine
                        import time
                        time.sleep(0.2)
                        continue
            
            if result and result.confidence >= self.confidence_threshold:
                self.status = STTStatus.IDLE
                if self.on_speech_detected:
                    self.on_speech_detected(result)
                return result
            else:
                self.status = STTStatus.IDLE
                return None
                
        except Exception as e:
            self.status = STTStatus.ERROR
            if self.on_error:
                self.on_error(f"STT Error: {str(e)}")
            return None
        finally:
            if self.on_listening_stop:
                self.on_listening_stop()
    
    def start_continuous_listening(self) -> bool:
        """Start continuous listening mode"""
        if not self.current_engine or self.continuous_listening:
            return False
        
        def continuous_callback(result: STTResult):
            """Callback for continuous listening results"""
            if result.confidence >= self.confidence_threshold:
                if self.on_speech_detected:
                    self.on_speech_detected(result)
        
        try:
            success = self.current_engine.start_continuous_listening(continuous_callback)
            if success:
                self.continuous_listening = True
                self.status = STTStatus.LISTENING
                print("🎤 Continuous listening started")
                if self.on_listening_start:
                    self.on_listening_start()
            return success
        except Exception as e:
            if self.on_error:
                self.on_error(f"Failed to start continuous listening: {str(e)}")
            return False
    
    def stop_continuous_listening(self) -> bool:
        """Stop continuous listening mode"""
        if not self.continuous_listening or not self.current_engine:
            return False
        
        try:
            success = self.current_engine.stop_continuous_listening()
            if success:
                self.continuous_listening = False
                self.status = STTStatus.IDLE
                print("🎤 Continuous listening stopped")
                if self.on_listening_stop:
                    self.on_listening_stop()
            return success
        except Exception as e:
            if self.on_error:
                self.on_error(f"Failed to stop continuous listening: {str(e)}")
            return False
    
    def toggle_continuous_listening(self) -> bool:
        """Toggle continuous listening on/off"""
        if self.continuous_listening:
            return self.stop_continuous_listening()
        else:
            return self.start_continuous_listening()
    
    def get_status(self) -> STTStatus:
        """Get current STT status"""
        return self.status
    
    def get_engine_info(self) -> Dict[str, Any]:
        """Get current engine information"""
        if not self.current_engine:
            return {"available": False}
        
        return {
            "available": True,
            "current_engine": self.current_engine_name,
            "available_engines": list(self.engines.keys()),
            "status": self.status.value,
            "language": self.language,
            "continuous_listening": self.continuous_listening,
            "confidence_threshold": self.confidence_threshold
        }
    
    def set_language(self, language: str) -> bool:
        """Set recognition language"""
        self.language = language
        # Note: Individual engines may need to reinitialize for language changes
        return True
    
    def set_confidence_threshold(self, threshold: float) -> bool:
        """Set confidence threshold for accepting results"""
        if 0.0 <= threshold <= 1.0:
            self.confidence_threshold = threshold
            return True
        return False
    
    def cleanup(self):
        """Clean up resources"""
        if self.continuous_listening:
            self.stop_continuous_listening()
        
        # Clean up engines
        for engine in self.engines.values():
            if hasattr(engine, 'cleanup'):
                engine.cleanup()
        
        self.engines.clear()
        self.current_engine = None
        self.status = STTStatus.DISABLED


def create_stt_manager() -> STTManager:
    """Factory function to create STT manager"""
    return STTManager()


if __name__ == "__main__":
    # Test STT Manager
    print("🧪 Testing STT Manager")
    
    manager = STTManager()
    
    if manager.is_available():
        print(f"✅ STT available with engines: {manager.get_available_engines()}")
        print(f"📊 Engine info: {manager.get_engine_info()}")
        
        # Test single listen
        print("\n🎤 Say something (5 second timeout)...")
        result = manager.listen_once()
        
        if result:
            print(f"✅ Heard: '{result.text}' (confidence: {result.confidence:.2f})")
        else:
            print("⚠️ Nothing heard or low confidence")
    else:
        print("❌ STT not available")
    
    manager.cleanup()
    print("🧪 STT Manager test complete")