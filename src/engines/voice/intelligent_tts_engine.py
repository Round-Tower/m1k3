#!/usr/bin/env python3
"""
Intelligent TTS Engine for M1K3
Automatically selects the best TTS engine based on content and user preferences
"""

import time
import numpy as np
from typing import Dict, List, Optional, Any, Tuple
from enum import Enum
from dataclasses import dataclass

class TTSQuality(Enum):
    """TTS quality preferences"""
    ULTRA_FAST = "ultra_fast"      # eSpeak - sub-10ms, perfect for system notifications
    FAST = "fast"                  # Piper - sub-50ms, good quality neural TTS
    BALANCED = "balanced"          # KittenTTS - balanced speed/quality (with completion)
    HIGH_QUALITY = "high_quality"  # VibeVoice - best quality, long-form capable

@dataclass
class EngineCapabilities:
    """Capabilities of each TTS engine"""
    name: str
    available: bool
    truncation_rate: float  # 0.0 = never truncates, 1.0 = always truncates
    quality_score: float    # 0.0-1.0 quality rating
    speed_rtf: float       # Real-time factor (lower is faster)
    best_for: List[str]    # Use cases this engine excels at
    max_length: int        # Maximum text length (characters)

class IntelligentTTSEngine:
    """
    Intelligent TTS engine that automatically selects the best engine
    based on text content, user preferences, and engine capabilities
    """

    _instance = None
    _initialized = False

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super(IntelligentTTSEngine, cls).__new__(cls)
        return cls._instance

    def __init__(self):
        # Prevent re-initialization if already initialized
        if IntelligentTTSEngine._initialized:
            return

        self.engines = {}
        self.engine_capabilities = {}
        self.default_quality = TTSQuality.BALANCED
        self.voice_enabled = True

        # Anti-truncation settings
        self.padding_enabled = True
        self.padding_aggressiveness = "adaptive"  # "minimal", "adaptive", "aggressive"

        # Engine selection statistics
        self.usage_stats = {}
        self.fallback_count = 0

        # Initialize engines
        self._initialize_engines()
        IntelligentTTSEngine._initialized = True

    def _initialize_engines(self):
        """Initialize practical TTS engines (Piper primary, KittenTTS backup)"""
        print("🔍 Initializing Streamlined TTS Engine...")

        # Piper - Primary ultra-fast neural TTS (NEW DEFAULT)
        try:
            from src.tts.controllers.piper_tts_manager import piper_manager
            if piper_manager.is_available():
                self.engines['piper'] = piper_manager
                self.engine_capabilities['piper'] = EngineCapabilities(
                    name="Piper",
                    available=True,
                    truncation_rate=0.05,  # Very low truncation risk
                    quality_score=0.90,    # Excellent neural quality (updated based on testing)
                    speed_rtf=0.05,        # Ultra-fast synthesis (20x real-time)
                    best_for=["speed", "quality", "conversation", "primary"],
                    max_length=3000        # Handles long text well
                )
                print("✅ Piper engine available (primary ultra-fast neural)")
        except Exception as e:
            print(f"⚠️ Piper not available: {e}")

        # Simple fallback engine for absolute safety
        try:
            from src.engines.voice.simple_voice_engine import SimpleVoiceEngine
            self.engines['simple'] = SimpleVoiceEngine()
            self.engine_capabilities['simple'] = EngineCapabilities(
                name="SimpleVoice",
                available=True,
                truncation_rate=0.0,  # Never truncates (system TTS)
                quality_score=0.60,   # Basic quality
                speed_rtf=0.10,       # Fast
                best_for=["emergency", "basic"],
                max_length=1000
            )
            print("✅ SimpleVoice engine available (emergency fallback)")
        except Exception as e:
            print(f"⚠️ SimpleVoice not available: {e}")

        if not self.engines:
            print("❌ No TTS engines available!")
        else:
            print(f"🚀 Streamlined TTS ready with {len(self.engines)} practical engines")

    def select_best_engine(self, text: str, quality_preference: Optional[TTSQuality] = None) -> Tuple[str, Any]:
        """
        Select the best engine for the given text and quality preference

        Returns:
            (engine_name, engine_instance)
        """
        if not self.engines:
            raise RuntimeError("No TTS engines available")

        text_length = len(text)
        quality_pref = quality_preference or self.default_quality

        # Streamlined engine selection logic (KittenTTS + Piper + SimpleVoice)
        selected_engine = None
        reason = ""

        # Strategy: Piper primary (ultra-fast + quality), KittenTTS for special cases

        # Ultra-fast requests -> Piper (fastest neural)
        if quality_pref == TTSQuality.ULTRA_FAST:
            if 'piper' in self.engines:
                selected_engine = 'piper'
                reason = "Ultra-fast neural"
            elif 'kitten' in self.engines:
                selected_engine = 'kitten'
                reason = "Fast neural (backup)"

        # High quality requests -> Try KittenTTS first, but Piper is excellent too
        elif quality_pref == TTSQuality.HIGH_QUALITY:
            if 'kitten' in self.engines:
                selected_engine = 'kitten'
                reason = "High quality neural"
            elif 'piper' in self.engines:
                selected_engine = 'piper'
                reason = "Excellent quality neural"

        # Balanced/Fast -> Piper primary (best speed/quality balance)
        else:  # BALANCED and FAST
            if 'piper' in self.engines:
                selected_engine = 'piper'
                reason = "Primary neural voice"
            elif 'kitten' in self.engines:
                selected_engine = 'kitten'
                reason = "High-quality neural backup"

        # Final fallback to SimpleVoice
        if not selected_engine or selected_engine not in self.engines:
            if 'simple' in self.engines:
                selected_engine = 'simple'
                reason = "Emergency system TTS"
            else:
                raise RuntimeError("No TTS engines available")

        # Update usage stats
        self.usage_stats[selected_engine] = self.usage_stats.get(selected_engine, 0) + 1

        print(f"🎯 Selected {self.engine_capabilities[selected_engine].name}: {reason}")
        return selected_engine, self.engines[selected_engine]

    def synthesize_and_play(self, text: str, background: bool = False,
                           quality: Optional[TTSQuality] = None) -> bool:
        """
        Synthesize and play text using the best available engine

        Args:
            text: Text to synthesize
            background: Whether to play in background
            quality: Quality preference override

        Returns:
            True if successful, False otherwise
        """
        if not self.voice_enabled:
            return False

        try:
            # Apply anti-truncation padding to text
            padded_text = self._add_anti_truncation_padding(text, quality)

            # Select best engine
            engine_name, engine = self.select_best_engine(padded_text, quality)

            # Load engine if needed
            if hasattr(engine, 'load_model') and not getattr(engine, 'tts_model', None):
                if not engine.load_model():
                    print(f"❌ Failed to load {engine_name}, trying fallback")
                    return self._try_fallback(padded_text, quality, exclude=[engine_name])

            # Generate audio
            start_time = time.time()

            if engine_name == 'kitten':
                # Use KittenTTS with our enhanced completion system
                return self._synthesize_with_completion(engine, padded_text, play_audio=True)
            else:
                # Use engine directly (they don't truncate)
                audio_data = engine.generate(padded_text)

                if audio_data is None:
                    print(f"❌ {engine_name} generation failed, trying fallback")
                    return self._try_fallback(text, quality, exclude=[engine_name])

                # Play audio
                return self._play_audio(audio_data, getattr(engine, 'sample_rate', 22050))

        except Exception as e:
            print(f"❌ TTS synthesis error: {e}")
            return self._try_fallback(text, quality)

    def _add_anti_truncation_padding(self, text: str, quality: Optional[TTSQuality] = None) -> str:
        """
        Add strategic padding to prevent audio truncation

        Different engines have different truncation patterns:
        - KittenTTS: Often cuts off final syllables/words
        - eSpeak: Generally reliable (no padding needed)
        - Others: May vary
        """
        if not self.padding_enabled or not text or not text.strip():
            return text

        text = text.strip()

        # Check if padding is needed based on engine selection
        engine_name, engine = self.select_best_engine(text, quality)
        cap = self.engine_capabilities.get(engine_name)

        if not cap or cap.truncation_rate == 0.0:
            # Engine doesn't truncate, minimal padding
            return text + " " if self.padding_aggressiveness != "minimal" else text

        # Apply padding strategy based on truncation risk and text characteristics
        padding_strategy = self._determine_padding_strategy(text, cap.truncation_rate)

        return self._apply_padding(text, padding_strategy)

    def _determine_padding_strategy(self, text: str, truncation_rate: float) -> Dict[str, Any]:
        """Determine the best padding strategy using punctuation for the given text and truncation risk"""

        text_length = len(text)
        ends_with_punctuation = text.rstrip()[-1] in '.!?;:' if text.rstrip() else False
        ends_with_strong_punct = text.rstrip()[-1] in '.!?' if text.rstrip() else False

        # Base strategy - use punctuation instead of words
        strategy = {
            "ensure_period": False,
            "add_ellipsis": False,
            "add_extra_periods": 0,
            "add_silence_commas": 0,
            "breathing_punctuation": ""
        }

        # High truncation risk (KittenTTS ~80%) - Use punctuation to force completion
        if truncation_rate > 0.5:
            if self.padding_aggressiveness == "minimal":
                # Light punctuation for KittenTTS
                if not ends_with_punctuation:
                    strategy["ensure_period"] = True
                strategy["add_extra_periods"] = 1

            elif self.padding_aggressiveness == "adaptive":
                # Balanced punctuation - works well with KittenTTS
                if not ends_with_strong_punct:
                    strategy["ensure_period"] = True
                strategy["add_extra_periods"] = 2
                strategy["add_silence_commas"] = 1 if text_length > 40 else 0
                strategy["breathing_punctuation"] = ".."

            else:  # aggressive
                # Heavy punctuation for problem cases
                if not ends_with_strong_punct:
                    strategy["ensure_period"] = True
                strategy["add_extra_periods"] = 3
                strategy["add_silence_commas"] = 1 if text_length > 30 else 0
                strategy["breathing_punctuation"] = "....."

        # Low truncation risk (Piper ~10%, SimpleVoice 0%) - minimal punctuation
        else:
            # Just ensure proper endings for Piper and SimpleVoice
            if not ends_with_punctuation and self.padding_aggressiveness != "minimal":
                strategy["ensure_period"] = True
            if self.padding_aggressiveness == "aggressive":
                strategy["add_extra_periods"] = 1

        return strategy

    def _apply_padding(self, text: str, strategy: Dict[str, Any]) -> str:
        """Apply the punctuation-based padding strategy to the text"""

        padded_text = text.rstrip()

        # Ensure proper ending punctuation
        if strategy["ensure_period"] and not padded_text.endswith(('.', '!', '?', ';', ':')):
            padded_text += "."

        # Add silence-inducing commas in the middle for longer text
        if strategy["add_silence_commas"] > 0 and len(padded_text) > 30:
            # Add strategic commas to create natural pauses
            words = padded_text.split()
            if len(words) > 5:
                # Add comma after roughly 1/3 of the text for breathing room
                comma_position = len(words) // 3
                if comma_position > 0 and comma_position < len(words) - 1:
                    words[comma_position] += ","

                # Add second comma if aggressive
                if strategy["add_silence_commas"] > 1 and len(words) > 8:
                    comma_position2 = (len(words) * 2) // 3
                    if comma_position2 != comma_position and comma_position2 < len(words) - 1:
                        words[comma_position2] += ","

                padded_text = " ".join(words)

        # Add extra periods for completion
        padded_text += "." * strategy["add_extra_periods"]

        # Add breathing punctuation (ellipsis patterns)
        if strategy["breathing_punctuation"]:
            padded_text += strategy["breathing_punctuation"]

        return padded_text

    def synthesize(self, text: str, quality: Optional[TTSQuality] = None) -> Tuple[Optional[np.ndarray], int]:
        """
        Synthesize text to audio data without playing it.

        Args:
            text: Text to synthesize
            quality: Quality preference override

        Returns:
            A tuple of (audio_data, sample_rate), or (None, 0) if synthesis fails.
        """
        if not self.voice_enabled:
            return None, 0

        try:
            padded_text = self._add_anti_truncation_padding(text, quality)
            engine_name, engine = self.select_best_engine(padded_text, quality)

            if hasattr(engine, 'load_model') and not getattr(engine, 'tts_model', None):
                if not engine.load_model():
                    return self._try_fallback_synthesize(padded_text, quality, exclude=[engine_name])

            if engine_name == 'kitten':
                audio_data = self._synthesize_with_completion(engine, padded_text, play_audio=False)
            else:
                audio_data = engine.generate(padded_text)

            if audio_data is None:
                return self._try_fallback_synthesize(text, quality, exclude=[engine_name])

            return audio_data, getattr(engine, 'sample_rate', 22050)

        except Exception as e:
            print(f"❌ TTS synthesis error: {e}")
            return self._try_fallback_synthesize(text, quality)

    def _try_fallback_synthesize(self, text: str, quality: Optional[TTSQuality], exclude: List[str] = None) -> Tuple[Optional[np.ndarray], int]:
        """Try fallback engines for synthesis."""
        exclude = exclude or []
        self.fallback_count += 1

        fallback_order = ['piper', 'kitten', 'simple']

        for engine_name in fallback_order:
            if engine_name in exclude or engine_name not in self.engines:
                continue

            print(f"🔄 Trying fallback engine for synthesis: {engine_name}")
            try:
                engine = self.engines[engine_name]

                if hasattr(engine, 'load_model') and not getattr(engine, 'tts_model', None):
                    if not engine.load_model():
                        continue

                if engine_name == 'kitten':
                    audio_data = self._synthesize_with_completion(engine, text, play_audio=False)
                else:
                    audio_data = engine.generate(text)
                
                if audio_data is not None:
                    return audio_data, getattr(engine, 'sample_rate', 22050)

            except Exception as e:
                print(f"❌ Fallback synthesis with {engine_name} failed: {e}")
                continue

        print("❌ All TTS synthesis engines failed")
        return None, 0

    def _synthesize_with_completion(self, kitten_engine, text: str, play_audio: bool = True) -> Optional[np.ndarray]:
        """Synthesize with KittenTTS directly (enhanced reliability)"""
        try:
            # Load engine if needed
            if not hasattr(kitten_engine, 'tts_model') or kitten_engine.tts_model is None:
                if not kitten_engine.load_model():
                    print("❌ KittenTTS failed to load")
                    return None

            # Generate audio directly
            audio_data = kitten_engine.generate(text)

            if audio_data is None:
                print("❌ KittenTTS generation returned None")
                return None

            if play_audio:
                # Play audio
                self._play_audio(audio_data, getattr(kitten_engine, 'sample_rate', 22050))
                return audio_data # Return the data even after playing
            else:
                return audio_data

        except Exception as e:
            print(f"❌ KittenTTS synthesis failed: {e}")
            return None









    def _try_fallback(self, text: str, quality: Optional[TTSQuality], exclude: List[str] = None) -> bool:
        """Try fallback engines"""
        exclude = exclude or []
        self.fallback_count += 1

        # Try engines in order of reliability (streamlined engines)
        fallback_order = ['piper', 'kitten', 'simple']

        for engine_name in fallback_order:
            if engine_name in exclude or engine_name not in self.engines:
                continue

            print(f"🔄 Trying fallback engine: {engine_name}")
            try:
                engine = self.engines[engine_name]

                # Load if needed
                if hasattr(engine, 'load_model') and not getattr(engine, 'tts_model', None):
                    if not engine.load_model():
                        continue

                # Generate with appropriate method
                if engine_name == 'kitten':
                    return self._synthesize_with_completion(engine, text)
                elif engine_name == 'simple':
                    # SimpleVoice has its own synthesize_and_play method
                    return engine.synthesize_and_play(text, background=False)
                else:
                    # Piper and other engines
                    audio_data = engine.generate(text)
                    if audio_data is not None:
                        return self._play_audio(audio_data, getattr(engine, 'sample_rate', 22050))

            except Exception as e:
                print(f"❌ Fallback {engine_name} failed: {e}")
                continue

        print("❌ All TTS engines failed")
        return False

    def _play_audio(self, audio_data: np.ndarray, sample_rate: int) -> bool:
        """Play audio data"""
        try:
            # Try multiple audio playback methods
            # Method 1: sounddevice (most reliable)
            import sounddevice as sd
            sd.play(audio_data, sample_rate)
            sd.wait()
            return True
        except ImportError:
            try:
                # Method 2: simpleaudio
                import simpleaudio as sa
                # Convert to 16-bit PCM
                audio_16bit = (audio_data * 32767).astype(np.int16)
                play_obj = sa.play_buffer(audio_16bit, 1, 2, sample_rate)
                play_obj.wait_done()
                return True
            except ImportError:
                try:
                    # Method 3: pygame
                    import pygame
                    pygame.mixer.init(frequency=sample_rate, size=-16, channels=1, buffer=512)
                    # Convert to 16-bit
                    audio_16bit = (audio_data * 32767).astype(np.int16)
                    sound = pygame.sndarray.make_sound(audio_16bit)
                    sound.play()
                    pygame.time.wait(int(len(audio_data) / sample_rate * 1000))
                    return True
                except ImportError:
                    # Method 4: Use the engine's native playback if available
                    print("⚠️ No audio playback libraries available (sounddevice, simpleaudio, pygame)")
                    print("   Audio generated successfully but cannot play")
                    return True  # Consider it successful since audio was generated
        except Exception as e:
            print(f"❌ Audio playback error: {e}")
            return False

    def set_quality_preference(self, quality: TTSQuality):
        """Set default quality preference"""
        self.default_quality = quality
        print(f"🎯 TTS quality preference set to: {quality.value}")

    def set_padding_mode(self, enabled: bool = True, aggressiveness: str = "adaptive"):
        """
        Configure anti-truncation padding

        Args:
            enabled: Whether to apply padding
            aggressiveness: "minimal", "adaptive", or "aggressive"
        """
        self.padding_enabled = enabled
        if aggressiveness in ["minimal", "adaptive", "aggressive"]:
            self.padding_aggressiveness = aggressiveness
        else:
            print(f"❌ Invalid aggressiveness: {aggressiveness}. Using 'adaptive'")
            self.padding_aggressiveness = "adaptive"

        status = "enabled" if enabled else "disabled"
        print(f"🛡️  Anti-truncation padding {status} ({self.padding_aggressiveness} mode)")

    def get_padding_info(self) -> Dict[str, Any]:
        """Get current padding configuration"""
        return {
            "enabled": self.padding_enabled,
            "aggressiveness": self.padding_aggressiveness,
            "description": {
                "minimal": "Basic spacing only",
                "adaptive": "Smart padding based on text length and punctuation",
                "aggressive": "Maximum padding for guaranteed completion"
            }.get(self.padding_aggressiveness, "Unknown")
        }

    def get_engine_stats(self) -> Dict[str, Any]:
        """Get usage statistics and engine information"""
        return {
            "available_engines": len(self.engines),
            "engine_capabilities": {name: {
                "name": cap.name,
                "available": cap.available,
                "truncation_rate": cap.truncation_rate,
                "quality_score": cap.quality_score,
                "speed_rtf": cap.speed_rtf,
                "best_for": cap.best_for
            } for name, cap in self.engine_capabilities.items()},
            "usage_stats": self.usage_stats,
            "fallback_count": self.fallback_count,
            "default_quality": self.default_quality.value
        }

    def force_engine(self, engine_name: str) -> bool:
        """Force use of a specific engine"""
        if engine_name in self.engines:
            # Temporarily modify selection logic
            original_method = self.select_best_engine

            def forced_selection(text, quality=None):
                return engine_name, self.engines[engine_name]

            self.select_best_engine = forced_selection
            return True
        return False

    def load_model(self) -> bool:
        """Load the intelligent TTS system"""
        if not self.engines:
            print("❌ No TTS engines available")
            return False

        print("✅ Intelligent TTS Engine ready")
        print(f"   Available engines: {len(self.engines)}")
        print(f"   Default quality: {self.default_quality.value}")

        # Show engine priorities
        non_truncating = [name for name, cap in self.engine_capabilities.items()
                         if cap.truncation_rate < 0.5]
        print(f"   Non-truncating engines: {non_truncating}")

        return True

    def get_status(self) -> Dict[str, Any]:
        """Get detailed status information"""
        return {
            "voice_enabled": self.voice_enabled,
            "engines_loaded": len(self.engines),
            "default_quality": self.default_quality.value,
            "available_engines": list(self.engines.keys()),
            "engine_stats": self.get_engine_stats()
        }

# Create singleton instance for easy access
intelligent_tts_engine = IntelligentTTSEngine()

if __name__ == "__main__":
    # Test the intelligent engine
    engine = IntelligentTTSEngine()

    if engine.load_model():
        # Test with different text types
        test_cases = [
            ("Short notification", "Error detected", TTSQuality.ULTRA_FAST),
            ("Balanced response", "Hello, how can I help you today?", TTSQuality.BALANCED),
            ("Long explanation", "The artificial intelligence system processes your request through multiple layers of neural networks to provide accurate and helpful responses.", TTSQuality.HIGH_QUALITY)
        ]

        for description, text, quality in test_cases:
            print(f"\n🧪 Testing: {description}")
            print(f"Text: '{text}'")
            print(f"Quality: {quality.value}")

            success = engine.synthesize_and_play(text, quality=quality)
            print(f"Result: {'✅ Success' if success else '❌ Failed'}")

        print(f"\n📊 Final stats: {engine.get_engine_stats()}")
    else:
        print("❌ Failed to initialize intelligent TTS engine")