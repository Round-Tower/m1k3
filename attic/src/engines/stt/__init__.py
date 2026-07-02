#!/usr/bin/env python3
"""
Speech-to-Text (STT) Engine Package for M1K3
Provides conversational AI capabilities through speech recognition
Optimized for minimal footprint with on-device recognition
"""

from .stt_manager import STTManager, STTEngine

# Import engines in order of preference (best UX first)
try:
    from .macos_stt_engine import MacOSSTTEngine
except ImportError:
    MacOSSTTEngine = None

try:
    from .vosk_stt_engine import VoskSTTEngine
except ImportError:
    VoskSTTEngine = None

try:
    from .web_speech_engine import WebSpeechEngine
except ImportError:
    WebSpeechEngine = None

try:
    from .whisper_stt_engine import WhisperSTTEngine
except ImportError:
    WhisperSTTEngine = None

__all__ = [
    'STTManager',
    'STTEngine', 
    'MacOSSTTEngine',
    'VoskSTTEngine',
    'WebSpeechEngine',
    'WhisperSTTEngine'
]