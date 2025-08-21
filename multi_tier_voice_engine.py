#!/usr/bin/env python3
"""
Multi-Tier Voice Engine for M1K3
Intelligent voice engine selection system with quality tiers
"""

import time
import threading
import numpy as np
from typing import Optional, Dict, Any, List
from enum import Enum

class VoiceQuality(Enum):
    """Voice quality tiers"""
    PREMIUM = "premium"      # Coqui TTS - highest quality
    BALANCED = "balanced"    # KittenTTS optimized - good quality, fast
    FAST = "fast"           # System TTS - fastest
    FALLBACK = "fallback"   # Mock - always works

class MultiTierVoiceEngine:
    """Intelligent voice engine with automatic quality selection"""
    
    def __init__(self, preferred_quality: VoiceQuality = VoiceQuality.BALANCED):
        self.preferred_quality = preferred_quality
        self.current_engine = None
        self.current_quality = None
        
        # Initialize available engines
        self.engines = {}
        self._initialize_engines()
        
        # Voice settings
        self.voice_enabled = False
        self.is_loaded = False
        
    def _initialize_engines(self):
        """Initialize all available voice engines"""
        print("🔧 Initializing multi-tier voice system...")
        
        # Try to import and initialize each engine
        
        # Premium Tier: Coqui TTS
        try:
            from coqui_tts_manager import CoquiTTSManager
            coqui = CoquiTTSManager()
            if coqui.is_available():
                self.engines[VoiceQuality.PREMIUM] = coqui
                print("✅ Premium tier: Coqui TTS available")
            else:
                print("⚠️  Premium tier: Coqui TTS not available")
        except ImportError:
            print("⚠️  Premium tier: Coqui TTS not installed")
            
        # Balanced Tier: KittenTTS
        try:
            from kittentts_manager import KittenTTSManager
            kitten = KittenTTSManager()
            if kitten.is_available():
                self.engines[VoiceQuality.BALANCED] = kitten
                print("✅ Balanced tier: KittenTTS available")
            else:
                print("⚠️  Balanced tier: KittenTTS not available")
        except ImportError:
            print("⚠️  Balanced tier: KittenTTS not installed")
            
        # Fast Tier: System TTS
        try:
            from simple_voice_engine import SimpleVoiceEngine
            system = SimpleVoiceEngine()
            if system.is_available():
                self.engines[VoiceQuality.FAST] = system
                print("✅ Fast tier: System TTS available")
            else:
                print("⚠️  Fast tier: System TTS not available")
        except ImportError:
            print("⚠️  Fast tier: System TTS not available")
            
        # Fallback Tier: Mock
        try:
            from simple_voice_engine import MockVoiceEngine
            mock = MockVoiceEngine()
            self.engines[VoiceQuality.FALLBACK] = mock
            print("✅ Fallback tier: Mock engine available")
        except ImportError:
            print("⚠️  Fallback tier: Mock engine not available")
            
        print(f"🎤 Voice tiers available: {list(self.engines.keys())}")
        
    def load_model(self) -> bool:
        """Load the best available voice engine"""
        if self.is_loaded:
            return True
            
        # Try engines in preference order
        quality_order = [
            self.preferred_quality,
            VoiceQuality.BALANCED,
            VoiceQuality.FAST,
            VoiceQuality.FALLBACK
        ]
        
        for quality in quality_order:
            if quality in self.engines:
                engine = self.engines[quality]
                print(f"🎤 Attempting to load {quality.value} tier engine...")
                
                if engine.load_model():
                    self.current_engine = engine
                    self.current_quality = quality
                    self.is_loaded = True
                    self.voice_enabled = True
                    
                    print(f"✅ Voice engine loaded: {quality.value} tier")
                    print(f"📊 Engine: {type(engine).__name__}")
                    
                    return True
                else:
                    print(f"❌ Failed to load {quality.value} tier")
                    
        print("❌ No voice engines could be loaded")
        return False
        
    def synthesize_and_play(self, text: str, background: bool = True) -> bool:
        """Synthesize and play text with current engine"""
        if not self.voice_enabled or not self.current_engine:
            return False
            
        # Check text length and select appropriate engine if needed
        optimal_engine = self._select_engine_for_text(text)
        
        if optimal_engine != self.current_engine:
            print(f"🔄 Switching to {self._get_engine_quality(optimal_engine).value} tier for this text")
            
        try:
            return optimal_engine.synthesize_and_play(text, background)
        except Exception as e:
            print(f"🔇 Voice synthesis error: {e}")
            # Try fallback engine
            if self.current_quality != VoiceQuality.FALLBACK:
                print("🔄 Attempting fallback engine...")
                fallback = self.engines.get(VoiceQuality.FALLBACK)
                if fallback:
                    return fallback.synthesize_and_play(text, background)
            return False
            
    def _select_engine_for_text(self, text: str):
        """Select optimal engine based on text characteristics"""
        text_length = len(text)
        
        # For very short text, use fast engines
        if text_length < 50:
            for quality in [VoiceQuality.FAST, VoiceQuality.BALANCED]:
                if quality in self.engines:
                    return self.engines[quality]
                    
        # For medium text, use balanced
        elif text_length < 200:
            for quality in [VoiceQuality.BALANCED, VoiceQuality.PREMIUM]:
                if quality in self.engines:
                    return self.engines[quality]
                    
        # For long text, use premium quality
        else:
            for quality in [VoiceQuality.PREMIUM, VoiceQuality.BALANCED]:
                if quality in self.engines:
                    return self.engines[quality]
                    
        # Fallback to current engine
        return self.current_engine
        
    def _get_engine_quality(self, engine) -> VoiceQuality:
        """Get the quality tier of an engine"""
        for quality, eng in self.engines.items():
            if eng == engine:
                return quality
        return VoiceQuality.FALLBACK
        
    def set_preferred_quality(self, quality: VoiceQuality) -> bool:
        """Change preferred quality tier"""
        if quality in self.engines:
            self.preferred_quality = quality
            print(f"🎤 Preferred quality set to: {quality.value}")
            
            # Reload with new preference
            if self.is_loaded:
                self.is_loaded = False
                return self.load_model()
            return True
        else:
            available = [q.value for q in self.engines.keys()]
            print(f"❌ Quality {quality.value} not available. Available: {available}")
            return False
            
    def get_available_qualities(self) -> List[VoiceQuality]:
        """Get list of available quality tiers"""
        return list(self.engines.keys())
        
    def get_status(self) -> Dict[str, Any]:
        """Get comprehensive status of voice system"""
        status = {
            "loaded": self.is_loaded,
            "enabled": self.voice_enabled,
            "current_quality": self.current_quality.value if self.current_quality else None,
            "preferred_quality": self.preferred_quality.value,
            "available_qualities": [q.value for q in self.engines.keys()],
            "engines": {}
        }
        
        # Get status from each engine
        for quality, engine in self.engines.items():
            if hasattr(engine, 'get_status'):
                status["engines"][quality.value] = engine.get_status()
            else:
                status["engines"][quality.value] = {"available": True}
                
        return status
        
    def force_engine(self, quality: VoiceQuality) -> bool:
        """Force use of specific engine quality"""
        if quality in self.engines:
            engine = self.engines[quality]
            if engine.load_model() if hasattr(engine, 'load_model') else True:
                self.current_engine = engine
                self.current_quality = quality
                print(f"🔄 Forced engine switch to: {quality.value}")
                return True
        return False

def create_multi_tier_voice_engine(preferred_quality: str = "balanced") -> MultiTierVoiceEngine:
    """Factory function to create multi-tier voice engine"""
    quality_map = {
        "premium": VoiceQuality.PREMIUM,
        "balanced": VoiceQuality.BALANCED,
        "fast": VoiceQuality.FAST,
        "fallback": VoiceQuality.FALLBACK
    }
    
    quality = quality_map.get(preferred_quality, VoiceQuality.BALANCED)
    return MultiTierVoiceEngine(quality)

if __name__ == "__main__":
    print("Testing Multi-Tier Voice Engine...")
    
    # Test with balanced preference
    engine = create_multi_tier_voice_engine("balanced")
    
    if engine.load_model():
        print("✅ Multi-tier engine loaded successfully")
        
        status = engine.get_status()
        print(f"\n📊 Status: {status}")
        
        # Test synthesis
        test_texts = [
            ("Short text", "Hello there!"),
            ("Medium text", "This is a medium length sentence to test the voice engine selection."),
            ("Long text", "This is a much longer piece of text that should trigger the premium voice engine for the highest quality synthesis available in the system.")
        ]
        
        for desc, text in test_texts:
            print(f"\n🎤 Testing {desc}: \"{text[:30]}...\"")
            success = engine.synthesize_and_play(text, background=False)
            print(f"Result: {'✅ Success' if success else '❌ Failed'}")
            time.sleep(1)
            
        print("\n🎉 Multi-tier voice engine test complete!")
    else:
        print("❌ Failed to load multi-tier engine")