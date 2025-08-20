#!/usr/bin/env python3
"""
Hybrid Voice Engine for M1K3
Intelligent fallback between KittenML TTS (retro) and system TTS
"""

import time
import subprocess
import platform
import threading
from typing import Optional, Dict, Any

# Try to import KittenML TTS
try:
    from retro_voice_engine import RetroVoiceEngine
    KITTEN_AVAILABLE = True
except ImportError:
    KITTEN_AVAILABLE = False

from simple_voice_engine import SimpleVoiceEngine

class HybridVoiceEngine:
    """Intelligent voice engine that prefers KittenML but falls back to system TTS"""
    
    def __init__(self):
        self.primary_engine = None
        self.fallback_engine = SimpleVoiceEngine()
        self.current_engine = None
        self.voice_enabled = False
        self.loading = False
        self.retro_mode = True
        
        # Voice presets for different character styles
        self.character_presets = {
            "m1k3": {"description": "M1K3's default PlayStation 1 style voice"},
            "hero": {"description": "Confident retro game protagonist"},
            "narrator": {"description": "Classic game narrator voice"},
            "villain": {"description": "Deep retro game antagonist"},
            "system": {"description": "Clean system TTS voice"}
        }
        self.current_preset = "m1k3"
        
    def is_available(self) -> bool:
        """Check if any voice engine is available"""
        return KITTEN_AVAILABLE or self.fallback_engine.is_available()
        
    def load_model(self) -> bool:
        """Load the best available voice engine"""
        self.loading = True
        
        if KITTEN_AVAILABLE:
            try:
                print("🎮 Attempting to load KittenML TTS for retro gaming voice...")
                self.primary_engine = RetroVoiceEngine()
                
                if self.primary_engine.load_model():
                    self.current_engine = self.primary_engine
                    self.retro_mode = True
                    print("🎮 KittenML TTS loaded successfully - Retro mode active!")
                    self.voice_enabled = True
                    self.loading = False
                    return True
                else:
                    print("⚠️  KittenML TTS failed to load, falling back to system TTS...")
                    
            except Exception as e:
                print(f"⚠️  KittenML TTS error: {e}")
                print("🔄 Falling back to system TTS...")
        
        # Fallback to system TTS
        if self.fallback_engine.load_model():
            self.current_engine = self.fallback_engine
            self.retro_mode = False
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
            
        # Enhance text based on current mode
        enhanced_text = self._enhance_text_for_character(text)
        
        try:
            return self.current_engine.synthesize_and_play(enhanced_text, background)
        except Exception as e:
            print(f"🔇 Voice synthesis error: {e}")
            return False
            
    def _enhance_text_for_character(self, text: str) -> str:
        """Enhance text based on current character preset"""
        if not self.retro_mode:
            return text
            
        # Character-specific text enhancements
        enhancements = {
            "m1k3": {
                "Hello": "Greetings, user",
                "Error": "System error detected",
                "Ready": "M1K3 system ready",
                "Loading": "Initializing M1K3 protocols",
            },
            "hero": {
                "Hello": "Greetings, ally",
                "Error": "Mission failure",
                "Ready": "Hero ready for action",
                "Loading": "Preparing for adventure",
            },
            "narrator": {
                "Hello": "Welcome, traveler",
                "Error": "An error has occurred in our tale",
                "Ready": "The story begins",
                "Loading": "Preparing the next chapter",
            },
            "villain": {
                "Hello": "Well, well, well",
                "Error": "Your plans have failed",
                "Ready": "The dark lord awakens",
                "Loading": "Gathering dark powers",
            }
        }
        
        preset_enhancements = enhancements.get(self.current_preset, enhancements["m1k3"])
        enhanced = text
        
        for old, new in preset_enhancements.items():
            if old in enhanced and not enhanced.startswith(("System", "M1K3", "Greetings")):
                enhanced = enhanced.replace(old, new)
                
        return enhanced
        
    def set_character_preset(self, preset_name: str) -> bool:
        """Set character voice preset"""
        if preset_name in self.character_presets:
            self.current_preset = preset_name
            
            if self.retro_mode and hasattr(self.current_engine, 'set_voice_preset'):
                # Map character presets to voice presets
                voice_preset_map = {
                    "m1k3": "ps1_hero",
                    "hero": "ps1_hero", 
                    "narrator": "ps1_narrator",
                    "villain": "ps1_villain",
                    "system": "classic"
                }
                
                voice_preset = voice_preset_map.get(preset_name, "ps1_hero")
                self.current_engine.set_voice_preset(voice_preset)
                
            print(f"🎮 Character preset set to: {preset_name}")
            return True
        else:
            print(f"❌ Unknown character preset: {preset_name}")
            return False
            
    def get_character_presets(self) -> Dict[str, str]:
        """Get available character presets"""
        return {name: info["description"] for name, info in self.character_presets.items()}
        
    def toggle_retro_mode(self) -> bool:
        """Toggle between retro and standard voice modes"""
        if KITTEN_AVAILABLE and self.primary_engine:
            if self.retro_mode:
                # Switch to system TTS
                self.current_engine = self.fallback_engine
                self.retro_mode = False
                print("🔊 Switched to standard voice mode")
            else:
                # Switch back to KittenML
                self.current_engine = self.primary_engine
                self.retro_mode = True
                print("🎮 Switched to retro voice mode")
            return True
        else:
            print("⚠️  Retro mode not available (KittenML TTS not loaded)")
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
            "retro_mode": self.retro_mode,
            "character_preset": self.current_preset,
            "engine": "none"
        }
        
        if self.current_engine:
            engine_status = self.current_engine.get_status()
            base_status.update({
                "engine": "kitten" if self.retro_mode else "system",
                "model": engine_status.get("model", "unknown")
            })
            
        return base_status

def create_voice_engine() -> HybridVoiceEngine:
    """Factory function to create hybrid voice engine"""
    return HybridVoiceEngine()

if __name__ == "__main__":
    # Test hybrid voice engine
    print("🎮 M1K3 Hybrid Voice Engine Test")
    print("=" * 40)
    
    engine = create_voice_engine()
    
    if engine.is_available():
        if engine.load_model():
            print(f"\n📊 Engine Status: {engine.get_status()}")
            
            print("\n🎭 Testing character presets:")
            test_phrases = [
                "Greetings, user. M1K3 system online.",
                "Ready for your commands.",
                "Error: Unable to process request.",
                "Loading mission parameters."
            ]
            
            presets = ["m1k3", "hero", "narrator", "villain"]
            
            for preset in presets:
                print(f"\n🎵 Testing {preset} character preset...")
                engine.set_character_preset(preset)
                
                for phrase in test_phrases[:1]:  # Test first phrase only
                    engine.synthesize_and_play(phrase, background=False)
                    time.sleep(0.5)
                    
            print("\n🔄 Testing mode switching...")
            if engine.toggle_retro_mode():
                engine.synthesize_and_play("Standard voice mode activated.", background=False)
                time.sleep(1)
                engine.toggle_retro_mode()
                engine.synthesize_and_play("Retro voice mode reactivated.", background=False)
                
            print("\n✅ Hybrid voice engine testing complete!")
            print(f"Final status: {engine.get_status()}")
        else:
            print("❌ Failed to load any voice engine")
    else:
        print("❌ No voice synthesis available")