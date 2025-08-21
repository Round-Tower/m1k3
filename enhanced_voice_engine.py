#!/usr/bin/env python3
"""
Enhanced Voice Engine for M1K3
Intelligent voice engine with Retro Zen Oracle as primary and easy persona switching
"""

import time
import threading
from typing import Optional, Dict, Any

# Try to import Zen Voice Engine
try:
    from zen_voice_engine import ZenVoiceEngine
    ZEN_AVAILABLE = True
except ImportError:
    ZEN_AVAILABLE = False

from simple_voice_engine import SimpleVoiceEngine

class EnhancedVoiceEngine:
    """Intelligent voice engine that prefers Zen Voice but falls back gracefully"""
    
    def __init__(self):
        self.primary_engine = None
        self.fallback_engine = SimpleVoiceEngine()
        self.current_engine = None
        self.voice_enabled = False
        self.loading = False
        self.zen_mode = True
        
    def is_available(self) -> bool:
        """Check if any voice engine is available"""
        return ZEN_AVAILABLE or self.fallback_engine.is_available()
        
    def load_model(self) -> bool:
        """Load the best available voice engine"""
        self.loading = True
        
        if ZEN_AVAILABLE:
            try:
                print("🧘 Attempting to load Retro Zen Oracle voice...")
                self.primary_engine = ZenVoiceEngine()
                
                if self.primary_engine.load_model():
                    self.current_engine = self.primary_engine
                    self.zen_mode = True
                    print("✨ Retro Zen Oracle loaded successfully - Crystal clear with digital warmth!")
                    self.voice_enabled = True
                    self.loading = False
                    return True
                else:
                    print("⚠️  Zen voice failed to load, falling back to system TTS...")
                    
            except Exception as e:
                print(f"⚠️  Zen voice error: {e}")
                print("🔄 Falling back to system TTS...")
        
        # Fallback to system TTS
        if self.fallback_engine.load_model():
            self.current_engine = self.fallback_engine
            self.zen_mode = False
            print("🔊 System TTS loaded - Standard voice mode active")
            self.voice_enabled = True
            self.loading = False
            return True
        else:
            print("❌ No voice synthesis available")
            self.loading = False
            return False
            
    def synthesize_and_play(self, text: str, background: bool = True) -> bool:
        """Synthesize and play text using the active engine"""
        if not self.voice_enabled or not self.current_engine:
            return False
            
        try:
            result = self.current_engine.synthesize_and_play(text, background)
            
            # If zen voice fails due to ONNX issues, fall back to system TTS
            if not result and self.zen_mode and self.primary_engine:
                error_msg = "ONNX/hardware compatibility issue detected"
                print(f"🔇 {error_msg}, switching to system TTS...")
                
                # Switch to fallback engine
                if self.fallback_engine.load_model():
                    self.current_engine = self.fallback_engine
                    self.zen_mode = False
                    print("🔊 Switched to system TTS successfully")
                    return self.fallback_engine.synthesize_and_play(text, background)
            
            return result
            
        except Exception as e:
            error_msg = str(e).lower()
            if "onnx" in error_msg or "expand" in error_msg or "bert" in error_msg:
                print(f"🔇 ONNX error detected: {e}")
                print("🔄 Switching to system TTS...")
                
                # Fall back to system TTS
                if self.fallback_engine.load_model():
                    self.current_engine = self.fallback_engine
                    self.zen_mode = False
                    return self.fallback_engine.synthesize_and_play(text, background)
            else:
                print(f"🔇 Voice synthesis error: {e}")
            return False
            
    def set_persona(self, persona_name: str) -> bool:
        """Set persona (only works with zen engine)"""
        if self.zen_mode and hasattr(self.current_engine, 'set_persona'):
            return self.current_engine.set_persona(persona_name)
        else:
            print("⚠️  Persona switching only available with Zen voice mode")
            return False
            
    def get_personas(self) -> Dict[str, Dict[str, str]]:
        """Get available personas"""
        if self.zen_mode and hasattr(self.current_engine, 'get_personas'):
            return self.current_engine.get_personas()
        else:
            return {}
            
    def get_current_persona(self) -> Dict[str, Any]:
        """Get current persona information"""
        if self.zen_mode and hasattr(self.current_engine, 'get_current_persona'):
            return self.current_engine.get_current_persona()
        else:
            return {}
            
    def toggle_zen_mode(self) -> bool:
        """Toggle between zen and standard voice modes"""
        if ZEN_AVAILABLE and self.primary_engine:
            if self.zen_mode:
                # Switch to system TTS
                self.current_engine = self.fallback_engine
                self.zen_mode = False
                print("🔊 Switched to standard voice mode")
            else:
                # Switch back to Zen Voice
                self.current_engine = self.primary_engine
                self.zen_mode = True
                print("🧘 Switched to Zen voice mode")
            return True
        else:
            print("⚠️  Zen mode not available (Zen voice not loaded)")
            return False
            
    def quick_persona_switch(self, persona_key: str) -> bool:
        """Quick persona switching with short commands"""
        persona_shortcuts = {
            "clear": "natural",
            "ai": "assistant",
            "pa": "pa_system",
            "radio": "broadcast",
            "computer": "terminal",
            "retro": "terminal"
        }
        
        full_persona = persona_shortcuts.get(persona_key.lower(), persona_key.lower())
        return self.set_persona(full_persona)
        
    def speak_persona_intro(self) -> bool:
        """Have the current persona introduce itself"""
        if self.zen_mode and hasattr(self.current_engine, 'get_current_persona'):
            persona = self.current_engine.get_current_persona()
            if persona and 'name' in persona and 'description' in persona:
                intro_text = f"Voice mode: {persona['name']}. {persona['description']}"
                return self.synthesize_and_play(intro_text, background=False)
        return False
        
    def set_voice_enabled(self, enabled: bool):
        """Enable or disable voice output"""
        self.voice_enabled = enabled and self.current_engine is not None
        if self.current_engine:
            self.current_engine.set_voice_enabled(enabled)
            
    def get_status(self) -> dict:
        """Get comprehensive voice engine status"""
        base_status = {
            "available": self.is_available(),
            "loaded": self.current_engine is not None,
            "enabled": self.voice_enabled,
            "loading": self.loading,
            "zen_mode": self.zen_mode,
            "engine": "none"
        }
        
        if self.current_engine:
            engine_status = self.current_engine.get_status()
            base_status.update({
                "engine": "zen" if self.zen_mode else "system",
                "model": engine_status.get("model", "unknown")
            })
            
            # Add persona info if available
            if self.zen_mode and "persona" in engine_status:
                base_status.update({
                    "persona": engine_status["persona"],
                    "persona_name": engine_status.get("persona_name", "Unknown")
                })
            
        return base_status

def create_voice_engine() -> EnhancedVoiceEngine:
    """Factory function to create enhanced voice engine"""
    return EnhancedVoiceEngine()

if __name__ == "__main__":
    # Test enhanced voice engine
    print("🧘 M1K3 Enhanced Voice Engine Test")
    print("=" * 40)
    
    engine = create_voice_engine()
    
    if engine.is_available():
        if engine.load_model():
            print(f"\n📊 Engine Status: {engine.get_status()}")
            
            if engine.zen_mode:
                print("\n🎭 Testing persona switching:")
                
                personas_to_test = ["zen", "sage", "professor", "monk"]
                test_phrase = "Welcome to the digital realm, fellow traveler."
                
                for persona in personas_to_test:
                    print(f"\n✨ Switching to {persona}...")
                    if engine.quick_persona_switch(persona):
                        engine.synthesize_and_play(test_phrase, background=False)
                        time.sleep(1.5)
                        
                print("\n🔄 Testing persona introductions:")
                engine.quick_persona_switch("zen")
                engine.speak_persona_intro()
                
            print(f"\n📊 Final Status: {engine.get_status()}")
            print("\n✅ Enhanced voice engine testing complete!")
        else:
            print("❌ Failed to load any voice engine")
    else:
        print("❌ No voice synthesis available")