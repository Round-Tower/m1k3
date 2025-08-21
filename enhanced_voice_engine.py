#!/usr/bin/env python3
"""
Voice Manager for M1K3
Loads voice profiles and manages the UnifiedVoiceEngine pipeline.
"""

import json
from pathlib import Path
from typing import Dict, Any, List
import time

from unified_voice_engine import UnifiedVoiceEngine
from audio_effects import (AudioEffect, IntercomEffect, CompressionEffect, NormalizationEffect, 
                           FormantCorrectionEffect, ClarityEnhancementEffect, SibilanceReductionEffect, ClippingProtectionEffect, SidechainCompressionEffect)
from simple_voice_engine import SimpleVoiceEngine

class VoiceManager:
    def __init__(self, config_path: str = "voice_config.json"):
        self.config = self._load_config(config_path)
        self.engine = UnifiedVoiceEngine()
        self.fallback_engine = SimpleVoiceEngine()
        
        # Dynamically map effect names to classes
        self.effect_map = {
            "IntercomEffect": IntercomEffect,
            "CompressionEffect": CompressionEffect,
            "NormalizationEffect": NormalizationEffect,
            "FormantCorrectionEffect": FormantCorrectionEffect,
            "ClarityEnhancementEffect": ClarityEnhancementEffect,
            "SibilanceReductionEffect": SibilanceReductionEffect,
            "ClippingProtectionEffect": ClippingProtectionEffect,
            "SidechainCompressionEffect": SidechainCompressionEffect
        }
        
        self.current_profile = None
        self.profiles = self.config.get("voice_profiles", {})
        
        # Apply global settings for performance optimization
        global_settings = self.config.get("global_settings", {})
        if "chunk_size" in global_settings:
            self.engine.chunk_size = global_settings["chunk_size"]
        if "inter_chunk_silence" in global_settings:
            self.engine.inter_chunk_silence = global_settings["inter_chunk_silence"]
        
    def _load_config(self, config_path: str) -> Dict[str, Any]:
        """Loads the voice configuration file."""
        config_file = Path(config_path)
        if config_file.exists():
            with open(config_file, 'r') as f:
                return json.load(f)
        else:
            print(f"⚠️  Voice config not found at {config_path}. Using defaults.")
            return {}

    def load_model(self) -> bool:
        """Loads the engine and sets the default profile."""
        if not self.engine.load_model():
            return False
            
        default_profile = self.config.get("default_profile", "assistant")
        self.set_profile(default_profile)
        return True

    def set_profile(self, profile_name: str) -> bool:
        """Sets a voice profile by building and applying its effects pipeline."""
        if profile_name not in self.profiles:
            print(f"❌ Unknown voice profile: {profile_name}")
            return False

        profile = self.profiles[profile_name]
        pipeline: List[AudioEffect] = []
        
        # Set KittenTTS voice if specified
        kitten_voice = profile.get("kitten_voice")
        if kitten_voice:
            self.engine.set_voice(kitten_voice)
        
        for effect_config in profile.get("effects_pipeline", []):
            module_name = effect_config.get("module")
            if module_name in self.effect_map:
                effect_class = self.effect_map[module_name]
                pipeline.append(effect_class(config=effect_config.get("config")))
            else:
                print(f"⚠️  Unknown audio effect module: {module_name}")

        self.engine.set_pipeline(pipeline)
        self.current_profile = profile
        print(f"✅ Voice profile set to: {profile['name']}")
        return True

    def synthesize_and_play(self, text: str, background: bool = True):
        """Synthesizes text using the current profile's pipeline with sidechain support."""
        # Import here to avoid circular imports
        try:
            from sound_manager import SoundManager
            # Try to get global sound manager instance for sidechain ducking
            if hasattr(self, 'sound_manager') and self.sound_manager:
                self.sound_manager.set_voice_active(True)
        except ImportError:
            pass
        
        try:
            self.engine.synthesize_and_play(text, background)
        finally:
            # Always reset voice state when done
            try:
                if hasattr(self, 'sound_manager') and self.sound_manager:
                    self.sound_manager.set_voice_active(False)
            except:
                pass
    
    def set_sound_manager(self, sound_manager):
        """Set the sound manager for sidechain compression coordination."""
        self.sound_manager = sound_manager

    def get_status(self) -> dict:
        """Gets the status of the voice manager and underlying engine."""
        status = {
            "manager_loaded": True,
            "current_profile": self.current_profile.get('name', 'None') if self.current_profile else 'None'
        }
        status.update(self.engine.kitten_manager.get_status())
        return status

def create_voice_engine() -> VoiceManager:
    """Factory function to create the main VoiceManager."""
    return VoiceManager()

if __name__ == "__main__":
    print("Testing Voice Manager...")
    engine = create_voice_engine()

    if engine.load_model():
        print(f"\n📊 Initial Status: {engine.get_status()}")

        test_phrase = "This is a test of the new voice profile system."

        # Test switching between all available profiles
        for profile_name in engine.profiles.keys():
            print(f"\n--- Testing Profile: {profile_name} ---")
            engine.set_profile(profile_name)
            engine.synthesize_and_play(test_phrase, background=False)
            time.sleep(1)

        print(f"\n📊 Final Status: {engine.get_status()}")
        print("\n✅ Voice Manager testing complete!")
    else:
        print("❌ Failed to load voice engine.")