#!/usr/bin/env python3
"""
Enhanced Voice Engine for M1K3
Intelligent voice engine with Retro Zen Oracle as primary and easy persona switching
"""

import time
import threading
from typing import Optional, Dict, Any

# Try to import voice engines in order of preference
try:
    from turbo_voice_engine import TurboVoiceEngine
    TURBO_AVAILABLE = True
except ImportError:
    TURBO_AVAILABLE = False

try:
    from optimized_voice_engine import OptimizedVoiceEngine
    OPTIMIZED_AVAILABLE = True
except ImportError:
    OPTIMIZED_AVAILABLE = False

try:
    from zen_voice_engine import ZenVoiceEngine
    ZEN_AVAILABLE = True
except ImportError:
    ZEN_AVAILABLE = False

try:
    from sound_manager import SoundManager
    SOUNDS_AVAILABLE = True
except ImportError:
    SOUNDS_AVAILABLE = False

from simple_voice_engine import SimpleVoiceEngine

class EnhancedVoiceEngine:
    """Intelligent voice engine with optimized performance and graceful fallbacks"""
    
    def __init__(self):
        self.primary_engine = None
        self.secondary_engine = None
        self.fallback_engine = SimpleVoiceEngine()
        self.current_engine = None
        self.voice_enabled = False
        self.loading = False
        self.engine_mode = "fallback"  # "turbo", "optimized", "zen", or "fallback"
        
        # Sound effects integration
        self.sound_manager = None
        if SOUNDS_AVAILABLE:
            try:
                self.sound_manager = SoundManager()
            except:
                pass
        
    def is_available(self) -> bool:
        """Check if any voice engine is available"""
        return TURBO_AVAILABLE or OPTIMIZED_AVAILABLE or ZEN_AVAILABLE or self.fallback_engine.is_available()
        
    def load_model(self) -> bool:
        """Load the best available voice engine with priority optimization"""
        self.loading = True
        
        # Play startup sound if available (non-blocking)
        if self.sound_manager:
            try:
                self.sound_manager.play_startup_sequence("random")
            except:
                pass  # Don't let sound errors block voice loading
        
        # Try turbo engine first (fastest)
        if TURBO_AVAILABLE:
            try:
                print("⚡ Attempting to load Turbo Voice Engine...")
                self.primary_engine = TurboVoiceEngine()
                
                if self.primary_engine.load_model():
                    self.current_engine = self.primary_engine
                    self.engine_mode = "turbo"
                    print("🚀 Turbo voice engine loaded - Ultra-fast synthesis!")
                    self.voice_enabled = True
                    self.loading = False
                    return True
                else:
                    print("⚠️  Turbo voice failed to load, trying optimized...")
                    
            except Exception as e:
                print(f"⚠️  Turbo voice error: {e}")
                print("🔄 Falling back to optimized voice...")
        
        # Try optimized engine (best performance)
        if OPTIMIZED_AVAILABLE:
            try:
                print("🚀 Attempting to load Optimized Voice Engine...")
                self.primary_engine = OptimizedVoiceEngine()
                
                if self.primary_engine.load_model():
                    self.current_engine = self.primary_engine
                    self.engine_mode = "optimized"
                    print("⚡ Optimized voice engine loaded - Enhanced performance & reliability!")
                    self.voice_enabled = True
                    self.loading = False
                    return True
                else:
                    print("⚠️  Optimized voice failed to load, trying Zen voice...")
                    
            except Exception as e:
                print(f"⚠️  Optimized voice error: {e}")
                print("🔄 Falling back to Zen voice...")
        
        # Try zen voice engine (high quality)
        if ZEN_AVAILABLE:
            try:
                print("🧘 Attempting to load Retro Zen Oracle voice...")
                self.secondary_engine = ZenVoiceEngine()
                
                if self.secondary_engine.load_model():
                    self.current_engine = self.secondary_engine
                    self.engine_mode = "zen"
                    print("✨ Retro Zen Oracle loaded - Crystal clear with digital warmth!")
                    self.voice_enabled = True
                    self.loading = False
                    return True
                else:
                    print("⚠️  Zen voice failed to load, falling back to system TTS...")
                    
            except Exception as e:
                print(f"⚠️  Zen voice error: {e}")
                print("🔄 Falling back to system TTS...")
        
        # Final fallback to system TTS
        if self.fallback_engine.load_model():
            self.current_engine = self.fallback_engine
            self.engine_mode = "fallback"
            print("🔊 System TTS loaded - Standard voice mode active")
            self.voice_enabled = True
            self.loading = False
            return True
        else:
            print("❌ No voice synthesis available")
            self.loading = False
            return False
    
    def play_startup_sound(self, style: str = "random") -> bool:
        """Play startup sound effect"""
        if self.sound_manager:
            try:
                return self.sound_manager.play_startup_sequence(style)
            except:
                pass
        return False
    
    def play_sound_effect(self, effect_name: str, background: bool = True) -> bool:
        """Play a sound effect"""
        if self.sound_manager:
            try:
                return self.sound_manager.play_sound(effect_name, background=background)
            except:
                pass
        return False
            
    def synthesize_and_play(self, text: str, background: bool = True) -> bool:
        """Synthesize and play text using the active engine"""
        if not self.voice_enabled or not self.current_engine:
            return False
            
        try:
            result = self.current_engine.synthesize_and_play(text, background)
            
            # If neural voice fails, try fallback engines
            if not result and self.engine_mode != "fallback":
                error_msg = f"{self.engine_mode.title()} voice failed"
                print(f"🔇 {error_msg}, trying fallback...")
                
                # Try the next available engine in priority order
                if self.engine_mode == "optimized" and self.secondary_engine:
                    # Try zen voice as fallback
                    self.current_engine = self.secondary_engine
                    self.engine_mode = "zen"
                    print("🧘 Switched to Zen voice")
                    result = self.secondary_engine.synthesize_and_play(text, background)
                    
                    if result:
                        return True
                
                # Final fallback to system TTS
                if self.fallback_engine.load_model():
                    self.current_engine = self.fallback_engine
                    self.engine_mode = "fallback"
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
        """Get comprehensive voice engine status with optimization info"""
        base_status = {
            "available": self.is_available(),
            "loaded": self.current_engine is not None,
            "enabled": self.voice_enabled,
            "loading": self.loading,
            "engine_mode": self.engine_mode,
            "engine": self.engine_mode
        }
        
        if self.current_engine:
            if hasattr(self.current_engine, 'get_status'):
                engine_status = self.current_engine.get_status()
                base_status.update(engine_status)
            
            # Add optimization info based on engine mode
            if self.engine_mode == "optimized":
                base_status.update({
                    "performance": "Optimized",
                    "features": ["Smart chunking", "Audio caching", "Error recovery"],
                })
            elif self.engine_mode == "zen":
                base_status.update({
                    "performance": "High Quality", 
                    "features": ["Persona system", "Audio effects", "Digital warmth"],
                })
            else:
                base_status.update({
                    "performance": "Basic",
                    "features": ["System TTS", "Reliable fallback"],
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