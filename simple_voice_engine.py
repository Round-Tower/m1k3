#!/usr/bin/env python3
"""
Simple Voice Engine for M1K3
Uses macOS built-in 'say' command as fallback TTS
"""

import subprocess
import platform
import threading
from typing import Optional

class SimpleVoiceEngine:
    """Simple TTS using system commands"""
    
    def __init__(self):
        self.platform = platform.system().lower()
        self.voice_enabled = False
        self.loading = False
        
    def is_available(self) -> bool:
        """Check if system TTS is available"""
        if self.platform == "darwin":  # macOS
            try:
                subprocess.run(["say", "--version"], capture_output=True, timeout=2)
                return True
            except:
                return False
        elif self.platform == "linux":
            try:
                subprocess.run(["espeak", "--version"], capture_output=True, timeout=2)
                return True
            except:
                return False
        return False
        
    def load_model(self) -> bool:
        """Initialize the voice engine"""
        if not self.is_available():
            return False
            
        print("🔊 Voice synthesis ready (system TTS)")
        self.voice_enabled = True
        return True
        
    def synthesize_and_play(self, text: str, background: bool = True) -> bool:
        """Synthesize and play text using system TTS"""
        if not self.voice_enabled:
            return False
            
        if background:
            thread = threading.Thread(target=self._synthesize_and_play_sync, args=(text,))
            thread.daemon = True
            thread.start()
            return True
        else:
            return self._synthesize_and_play_sync(text)
            
    def _synthesize_and_play_sync(self, text: str) -> bool:
        """Synchronous TTS playback"""
        try:
            if self.platform == "darwin":  # macOS
                # Use faster voice and rate for better experience
                subprocess.run([
                    "say", "-v", "Alex", "-r", "200", text
                ], timeout=30)
                return True
            elif self.platform == "linux":
                subprocess.run([
                    "espeak", "-s", "150", "-a", "50", text
                ], timeout=30)
                return True
        except Exception as e:
            print(f"🔇 Voice synthesis error: {e}")
            return False
        return False
        
    def set_voice_enabled(self, enabled: bool):
        """Enable or disable voice output"""
        self.voice_enabled = enabled and self.is_available()
        
    def get_status(self) -> dict:
        """Get voice engine status"""
        return {
            "available": self.is_available(),
            "loaded": self.voice_enabled,
            "enabled": self.voice_enabled,
            "loading": False,
            "model": "system-tts"
        }

class MockVoiceEngine:
    """Mock voice engine when TTS is not available"""
    
    def __init__(self):
        self.voice_enabled = False
        
    def is_available(self) -> bool:
        return False
        
    def load_model(self) -> bool:
        return False
        
    def synthesize_and_play(self, text: str, background: bool = True) -> bool:
        return False
        
    def set_voice_enabled(self, enabled: bool):
        pass
        
    def get_status(self) -> dict:
        return {
            "available": False,
            "loaded": False,
            "enabled": False,
            "loading": False,
            "model": "mock"
        }

def create_voice_engine():
    """Create appropriate voice engine based on availability"""
    engine = SimpleVoiceEngine()
    if engine.is_available():
        return engine
    else:
        return MockVoiceEngine()

if __name__ == "__main__":
    # Test simple voice engine
    engine = create_voice_engine()
    
    print(f"Voice available: {engine.is_available()}")
    
    if engine.is_available():
        if engine.load_model():
            print("Testing voice synthesis...")
            test_text = "Hello! I'm M1K3 with system voice synthesis. This sounds great!"
            success = engine.synthesize_and_play(test_text, background=False)
            if success:
                print("✅ Voice synthesis test successful!")
            else:
                print("❌ Voice synthesis failed")
        else:
            print("❌ Failed to load voice")
    else:
        print("❌ Voice synthesis not available on this system")