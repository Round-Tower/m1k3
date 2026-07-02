#!/usr/bin/env python3
"""
Content-Specific Audio Effects for M1K3
Specialized audio effects for different types of model output content
"""

import numpy as np
from abc import ABC, abstractmethod
from typing import Dict, Any, Optional, List
from dataclasses import dataclass
import warnings

try:
    from src.utils.model_output_parser import ContentType, ContentTypeModulation
    DEPENDENCIES_AVAILABLE = True
except ImportError:
    DEPENDENCIES_AVAILABLE = False

# Suppress numpy warnings for cleaner output
warnings.filterwarnings("ignore", category=RuntimeWarning)

class ContentAwareEffect(ABC):
    """Base class for content-aware audio effects"""
    
    def __init__(self, content_type: ContentType = None, config: Dict[str, Any] = None):
        self.content_type = content_type
        self.config = config or {}
        self._setup_default_config()
    
    @abstractmethod
    def _setup_default_config(self):
        """Setup default configuration for the effect"""
        pass
    
    @abstractmethod 
    def process(self, audio: np.ndarray, sample_rate: int = 22050, **kwargs) -> np.ndarray:
        """Process audio with the content-specific effect"""
        pass
    
    def _validate_audio_format(self, audio: np.ndarray) -> bool:
        """Validate audio format and range"""
        if not isinstance(audio, np.ndarray):
            return False
        
        if len(audio.shape) != 1:  # Should be mono
            return False
        
        if len(audio) == 0:
            return False
        
        # Check for clipping (values outside [-1, 1])
        if np.max(np.abs(audio)) > 1.0:
            return False
        
        return True
    
    def _apply_volume_adjustment(self, audio: np.ndarray, multiplier: float) -> np.ndarray:
        """Apply volume adjustment to audio"""
        adjusted = audio * multiplier
        
        # Prevent clipping
        max_val = np.max(np.abs(adjusted))
        if max_val > 1.0:
            adjusted = adjusted / max_val * 0.95
        
        return adjusted
    
    def _apply_simple_reverb(self, audio: np.ndarray, reverb_amount: float, 
                           sample_rate: int = 22050) -> np.ndarray:
        """Apply simple reverb effect"""
        if reverb_amount <= 0:
            return audio
        
        # Simple reverb using delayed reflections
        delay_samples = int(0.05 * sample_rate)  # 50ms delay
        reverb_decay = 0.3 * reverb_amount
        
        # Create reverb buffer
        reverb_audio = np.zeros(len(audio) + delay_samples)
        reverb_audio[:len(audio)] = audio
        
        # Add delayed reflections
        reverb_audio[delay_samples:] += audio * reverb_decay
        
        # Add second reflection
        delay2_samples = int(0.08 * sample_rate)  # 80ms delay
        if delay2_samples < len(reverb_audio):
            reverb_audio[delay2_samples:] += audio[:len(reverb_audio)-delay2_samples] * reverb_decay * 0.5
        
        # Mix with original
        mixed = reverb_audio[:len(audio)] * (1 + reverb_amount)
        
        # Normalize
        max_val = np.max(np.abs(mixed))
        if max_val > 1.0:
            mixed = mixed / max_val * 0.95
        
        return mixed
    
    def _apply_lowpass_filter(self, audio: np.ndarray, sample_rate: int,
                             cutoff_factor: float = 0.3) -> np.ndarray:
        """Apply simple low-pass filter"""
        # Simple moving average filter for low-pass effect
        window_size = max(3, int(sample_rate * 0.0001 * (1 + cutoff_factor)))  # Adaptive window
        
        if window_size >= len(audio):
            return audio
        
        # Apply moving average
        kernel = np.ones(window_size) / window_size
        filtered = np.convolve(audio, kernel, mode='same')
        
        # Blend with original based on cutoff_factor
        blended = audio * (1 - cutoff_factor) + filtered * cutoff_factor
        
        return blended
    
    def _apply_highpass_filter(self, audio: np.ndarray, sample_rate: int) -> np.ndarray:
        """Apply simple high-pass filter"""
        # Simple high-pass using difference filter
        filtered = np.diff(audio, prepend=audio[0])
        return filtered * 0.3 + audio * 0.7  # Blend with original

class ThinkingEffect(ContentAwareEffect):
    """Audio effect for thinking content - softer, more contemplative"""
    
    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(content_type=ContentType.THINKING, config=config)
    
    def _setup_default_config(self):
        """Setup default thinking effect configuration"""
        defaults = {
            "volume_reduction": 0.2,     # Reduce volume by 20%
            "speed_reduction": 0.15,     # Reduce speed by 15%
            "reverb_amount": 0.15,       # Add subtle reverb
            "pitch_adjustment": 0.0,     # Fixed: never go below 0 for pitch
            "softness_factor": 0.3       # Add softness/warmth
        }
        
        # Apply config overrides
        for key, default_value in defaults.items():
            setattr(self, key, self.config.get(key, default_value))
    
    def process(self, audio: np.ndarray, sample_rate: int = 22050, **kwargs) -> np.ndarray:
        """Process audio with thinking-specific effects"""
        if not self._validate_audio_format(audio):
            return audio
        
        processed = audio.copy()
        
        # Apply volume reduction for contemplative feel
        volume_multiplier = 1.0 - self.volume_reduction
        processed = self._apply_volume_adjustment(processed, volume_multiplier)
        
        # Apply reverb for introspective quality
        processed = self._apply_reverb(processed, 
                                     reverb_amount=self.reverb_amount, 
                                     sample_rate=sample_rate)
        
        # Apply pitch adjustment for deeper, more thoughtful tone
        processed = self._apply_pitch_shift(processed, 
                                          shift_semitones=self.pitch_adjustment * 12,
                                          sample_rate=sample_rate)
        
        # Apply softness filter
        processed = self._apply_softness(processed, 
                                       softness=self.softness_factor,
                                       sample_rate=sample_rate)
        
        return processed
    
    def _apply_reverb(self, audio: np.ndarray, reverb_amount: float, 
                     sample_rate: int) -> np.ndarray:
        """Apply reverb with thinking-specific characteristics"""
        return self._apply_simple_reverb(audio, reverb_amount, sample_rate)
    
    def _apply_pitch_shift(self, audio: np.ndarray, shift_semitones: float,
                          sample_rate: int) -> np.ndarray:
        """Apply pitch shift (pitch-up only to avoid distortion)"""
        if shift_semitones <= 0.1:  # Only apply pitch increases
            return audio
        
        # For thinking content, we want slightly higher perceived pitch
        # Use high-frequency emphasis for pitch-up effect without artifacts
        if len(audio) > 1:
            # Create high-frequency emphasis through differentiation
            high_freq_emphasis = np.diff(audio, prepend=audio[0])
            # Apply emphasis based on semitone shift, capped to avoid artifacts
            emphasis_strength = min(abs(shift_semitones) * 0.02, 0.1)  # Cap at 10%
            processed = audio + high_freq_emphasis * emphasis_strength
        else:
            processed = audio
        
        return processed
    
    def _apply_softness(self, audio: np.ndarray, softness: float, 
                       sample_rate: int) -> np.ndarray:
        """Apply softness filter for gentler sound"""
        if softness <= 0:
            return audio
        
        # Apply gentle low-pass filtering for softness
        return self._apply_lowpass_filter(audio, sample_rate, cutoff_factor=softness)
    
    def _apply_lowpass_filter(self, audio: np.ndarray, sample_rate: int,
                             cutoff_factor: float = 0.3) -> np.ndarray:
        """Apply simple low-pass filter"""
        # Simple moving average filter for low-pass effect
        window_size = max(3, int(sample_rate * 0.0001 * (1 + cutoff_factor)))  # Adaptive window
        
        if window_size >= len(audio):
            return audio
        
        # Apply moving average
        kernel = np.ones(window_size) / window_size
        filtered = np.convolve(audio, kernel, mode='same')
        
        # Blend with original based on cutoff_factor
        blended = audio * (1 - cutoff_factor) + filtered * cutoff_factor
        
        return blended
    
    def _apply_highpass_filter(self, audio: np.ndarray, sample_rate: int) -> np.ndarray:
        """Apply simple high-pass filter"""
        # Simple high-pass using difference filter
        filtered = np.diff(audio, prepend=audio[0])
        return filtered * 0.3 + audio * 0.7  # Blend with original

class NarrationEffect(ContentAwareEffect):
    """Audio effect for narration content - warm and expressive"""
    
    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(content_type=ContentType.NARRATION, config=config)
    
    def _setup_default_config(self):
        """Setup default narration effect configuration"""
        defaults = {
            "warmth_enhancement": 0.25,   # Add warmth
            "expressiveness_factor": 0.2,  # Increase dynamic range
            "speed_adjustment": 0.1,       # Slightly faster for storytelling
            "presence_boost": 0.15,        # Boost presence frequencies
            "dynamic_range_expansion": 0.1 # Make more expressive
        }
        
        for key, default_value in defaults.items():
            setattr(self, key, self.config.get(key, default_value))
    
    def process(self, audio: np.ndarray, sample_rate: int = 22050, **kwargs) -> np.ndarray:
        """Process audio with narration-specific effects"""
        if not self._validate_audio_format(audio):
            return audio
        
        processed = audio.copy()
        
        # Apply warmth enhancement
        processed = self._apply_warmth(processed, 
                                     warmth_amount=self.warmth_enhancement,
                                     sample_rate=sample_rate)
        
        # Apply expressiveness enhancement
        processed = self._apply_expressiveness(processed, 
                                             factor=self.expressiveness_factor)
        
        # Apply presence boost for clarity
        processed = self._apply_presence_boost(processed, 
                                             boost_amount=self.presence_boost,
                                             sample_rate=sample_rate)
        
        # Apply dynamic range expansion
        processed = self._apply_dynamic_expansion(processed, 
                                                expansion=self.dynamic_range_expansion)
        
        return processed
    
    def _apply_warmth(self, audio: np.ndarray, warmth_amount: float,
                     sample_rate: int, **kwargs) -> np.ndarray:
        """Apply warmth enhancement"""
        if warmth_amount <= 0:
            return audio
        
        # Warmth through gentle low-mid boost and soft compression
        # Apply gentle compression for warmth
        compressed = self._apply_gentle_compression(audio, ratio=1.5)
        
        # Boost low-mid frequencies
        warm_boosted = self._apply_lowmid_boost(compressed, sample_rate)
        
        # Blend with original
        warmed = audio * (1 - warmth_amount) + warm_boosted * warmth_amount
        
        return warmed
    
    def _apply_expressiveness(self, audio: np.ndarray, factor: float) -> np.ndarray:
        """Apply expressiveness enhancement through dynamic processing"""
        if factor <= 0:
            return audio
        
        # Enhance dynamics by subtle expansion
        enhanced = self._apply_dynamic_expansion(audio, factor)
        return enhanced
    
    def _apply_presence_boost(self, audio: np.ndarray, boost_amount: float,
                            sample_rate: int) -> np.ndarray:
        """Apply presence boost for clarity"""
        if boost_amount <= 0:
            return audio
        
        # Simple presence boost using high-frequency emphasis
        # This is a simplified implementation
        emphasized = self._apply_highpass_filter(audio, sample_rate)
        
        # Blend with original
        boosted = audio + emphasized * boost_amount
        
        # Normalize
        max_val = np.max(np.abs(boosted))
        if max_val > 1.0:
            boosted = boosted / max_val * 0.95
        
        return boosted
    
    def _apply_gentle_compression(self, audio: np.ndarray, ratio: float = 2.0) -> np.ndarray:
        """Apply gentle compression for warmth"""
        # Simple soft compression
        threshold = 0.3
        
        # Find samples above threshold
        above_threshold = np.abs(audio) > threshold
        
        # Apply compression to loud parts
        compressed = audio.copy()
        compressed[above_threshold] = (
            np.sign(audio[above_threshold]) * 
            (threshold + (np.abs(audio[above_threshold]) - threshold) / ratio)
        )
        
        return compressed
    
    def _apply_lowmid_boost(self, audio: np.ndarray, sample_rate: int) -> np.ndarray:
        """Apply low-mid frequency boost for warmth"""
        # Simple low-mid boost using gentle filtering
        boosted = self._apply_lowpass_filter(audio, sample_rate, cutoff_factor=0.2)
        return audio * 0.8 + boosted * 0.2
    
    def _apply_dynamic_expansion(self, audio: np.ndarray, expansion: float) -> np.ndarray:
        """Apply dynamic range expansion for expressiveness"""
        if expansion <= 0:
            return audio
        
        # Simple expansion - make loud parts louder, quiet parts quieter
        rms = np.sqrt(np.mean(audio**2))
        threshold = rms * 0.5
        
        expanded = audio.copy()
        
        # Expand dynamics
        above_threshold = np.abs(audio) > threshold
        expanded[above_threshold] *= (1 + expansion)
        
        below_threshold = np.abs(audio) < threshold
        expanded[below_threshold] *= (1 - expansion * 0.5)
        
        # Prevent clipping
        max_val = np.max(np.abs(expanded))
        if max_val > 1.0:
            expanded = expanded / max_val * 0.95
        
        return expanded

class ClarificationEffect(ContentAwareEffect):
    """Audio effect for clarification content - questioning intonation"""
    
    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(content_type=ContentType.CLARIFICATION, config=config)
    
    def _setup_default_config(self):
        """Setup default clarification effect configuration"""
        defaults = {
            "pitch_rise_amount": 0.15,    # Amount of pitch rise
            "intonation_curve": "rising", # Type of intonation curve
            "question_emphasis": 0.2,     # Emphasis on question words
            "clarity_boost": 0.1          # Boost clarity for questions
        }
        
        for key, default_value in defaults.items():
            setattr(self, key, self.config.get(key, default_value))
    
    def process(self, audio: np.ndarray, sample_rate: int = 22050, 
               text: str = "", **kwargs) -> np.ndarray:
        """Process audio with clarification-specific effects"""
        if not self._validate_audio_format(audio):
            return audio
        
        processed = audio.copy()
        
        # Apply pitch curve for question intonation
        processed = self._apply_pitch_curve(processed, 
                                          curve_type=self.intonation_curve,
                                          rise_amount=self.pitch_rise_amount,
                                          sample_rate=sample_rate)
        
        # Apply question emphasis if text indicates question
        if text and self._is_question_text(text):
            processed = self._apply_question_emphasis(processed, 
                                                    emphasis=self.question_emphasis)
        
        # Apply clarity boost for better comprehension
        processed = self._apply_clarity_boost(processed, 
                                            boost_amount=self.clarity_boost,
                                            sample_rate=sample_rate)
        
        return processed
    
    def _apply_pitch_curve(self, audio: np.ndarray, curve_type: str,
                          rise_amount: float, sample_rate: int, **kwargs) -> np.ndarray:
        """Apply pitch curve for question intonation"""
        if curve_type != "rising" or rise_amount <= 0:
            return audio
        
        # Create rising pitch envelope
        length = len(audio)
        
        if curve_type == "rising":
            # Rising curve - starts normal, ends higher
            curve = np.linspace(0, rise_amount, length)
        else:
            curve = np.zeros(length)
        
        # Apply pitch modulation using simple frequency modulation
        # This is a simplified approach - could be enhanced with better pitch algorithms
        modulated = audio.copy()
        
        # Apply gentle high-frequency boost at the end for rising effect
        end_portion = int(length * 0.3)  # Last 30% of audio
        if end_portion > 0:
            end_start = length - end_portion
            end_audio = audio[end_start:]
            
            # Apply high-frequency emphasis to end portion
            emphasized_end = self._apply_highpass_filter(end_audio, sample_rate)
            
            # Blend emphasized end back in
            blend_factor = rise_amount
            modulated[end_start:] = (
                end_audio * (1 - blend_factor) + 
                emphasized_end * blend_factor
            )
        
        return modulated
    
    def _is_question_text(self, text: str) -> bool:
        """Detect if text is a question"""
        if not text:
            return False
        
        text_lower = text.lower().strip()
        
        # Check for question mark
        if '?' in text:
            return True
        
        # Check for question words at the beginning
        question_starters = [
            'what', 'how', 'when', 'where', 'why', 'which', 'who',
            'could', 'can', 'would', 'will', 'should', 'do', 'does',
            'are', 'is', 'have', 'has'
        ]
        
        words = text_lower.split()
        if words and words[0] in question_starters:
            return True
        
        return False
    
    def _apply_question_emphasis(self, audio: np.ndarray, emphasis: float) -> np.ndarray:
        """Apply emphasis for question content"""
        if emphasis <= 0:
            return audio
        
        # Apply gentle volume boost and presence enhancement
        emphasized = audio * (1 + emphasis * 0.5)
        
        # Prevent clipping
        max_val = np.max(np.abs(emphasized))
        if max_val > 1.0:
            emphasized = emphasized / max_val * 0.95
        
        return emphasized
    
    def _apply_clarity_boost(self, audio: np.ndarray, boost_amount: float,
                           sample_rate: int) -> np.ndarray:
        """Apply clarity boost for better question comprehension"""
        if boost_amount <= 0:
            return audio
        
        # Boost mid-high frequencies for clarity
        clarity_enhanced = self._apply_midrange_boost(audio, sample_rate)
        
        # Blend with original
        boosted = audio * (1 - boost_amount) + clarity_enhanced * boost_amount
        
        return boosted
    
    def _apply_midrange_boost(self, audio: np.ndarray, sample_rate: int) -> np.ndarray:
        """Apply mid-range frequency boost for clarity"""
        # Simple mid-range boost using band-pass effect
        # This is a simplified implementation
        high_passed = self._apply_highpass_filter(audio, sample_rate)
        low_passed = self._apply_lowpass_filter(audio, sample_rate, cutoff_factor=0.3)
        
        # Combine for mid-range emphasis
        mid_boosted = audio + high_passed * 0.1 - low_passed * 0.05
        
        return mid_boosted

class AnswerEffect(ContentAwareEffect):
    """Audio effect for answer content - natural, clear voice (no special processing)"""
    
    def __init__(self, config: Dict[str, Any] = None):
        super().__init__(content_type=ContentType.ANSWER, config=config)
    
    def _setup_default_config(self):
        """Setup default answer effect configuration (minimal processing)"""
        defaults = {
            "clarity_boost": 0.05,  # Very subtle clarity boost
            "presence_adjustment": 0.02  # Minimal presence adjustment
        }
        
        for key, default_value in defaults.items():
            setattr(self, key, self.config.get(key, default_value))
    
    def process(self, audio: np.ndarray, sample_rate: int = 22050, **kwargs) -> np.ndarray:
        """Process audio with minimal answer-specific effects (mainly natural voice)"""
        if not self._validate_audio_format(audio):
            return audio
        
        # For answers, we want natural, clear voice with minimal processing
        processed = audio.copy()
        
        # Apply very subtle clarity boost if configured
        if self.clarity_boost > 0:
            processed = self._apply_subtle_clarity(processed, 
                                                 boost_amount=self.clarity_boost,
                                                 sample_rate=sample_rate)
        
        return processed
    
    def _apply_subtle_clarity(self, audio: np.ndarray, boost_amount: float,
                            sample_rate: int) -> np.ndarray:
        """Apply very subtle clarity enhancement"""
        if boost_amount <= 0:
            return audio
        
        # Very gentle high-frequency boost
        emphasized = self._apply_highpass_filter(audio, sample_rate)
        
        # Blend very subtly with original
        clarified = audio * (1 - boost_amount) + emphasized * boost_amount
        
        return clarified
    
    def _apply_highpass_filter(self, audio: np.ndarray, sample_rate: int) -> np.ndarray:
        """Apply simple high-pass filter"""
        # Simple difference filter for high-pass effect
        filtered = np.diff(audio, prepend=audio[0])
        return filtered * 0.3 + audio * 0.7  # Gentle blend

class ContentEffectsManager:
    """Manager for coordinating all content-specific effects"""
    
    def __init__(self):
        self.effects = self._initialize_effects()
        self.enable_effect_chaining = False
    
    def _initialize_effects(self) -> Dict[ContentType, ContentAwareEffect]:
        """Initialize all content-specific effects"""
        effects = {}
        
        if DEPENDENCIES_AVAILABLE:
            effects[ContentType.THINKING] = ThinkingEffect()
            effects[ContentType.NARRATION] = NarrationEffect()
            effects[ContentType.CLARIFICATION] = ClarificationEffect()
            
            # Answer content uses no special effect (natural voice)
            effects[ContentType.ANSWER] = AnswerEffect()
        
        return effects
    
    def apply_content_effect(self, audio: np.ndarray, content_type: ContentType,
                           sample_rate: int = 22050, 
                           modulation: Optional['ContentTypeModulation'] = None,
                           text: str = "", **kwargs) -> np.ndarray:
        """Apply content-specific effect to audio"""
        if content_type not in self.effects:
            return audio
        
        effect = self.effects[content_type]
        
        # Apply the content-specific effect
        processed = effect.process(audio, sample_rate=sample_rate, text=text, **kwargs)
        
        # Apply modulation if provided
        if modulation:
            processed = self._apply_modulation(processed, modulation, sample_rate)
        
        return processed
    
    def _apply_modulation(self, audio: np.ndarray, 
                         modulation: 'ContentTypeModulation',
                         sample_rate: int) -> np.ndarray:
        """Apply modulation parameters to processed audio"""
        processed = audio.copy()
        
        # Apply volume multiplier
        if modulation.volume_multiplier != 1.0:
            processed = processed * modulation.volume_multiplier
        
        # Apply pitch adjustment (pitch-up only to avoid distortion)
        if modulation.pitch_adjustment > 0.01:  # Only apply pitch increases
            # Higher pitch - use high-frequency emphasis for brighter sound
            if len(processed) > 1:
                high_freq_emphasis = np.diff(processed, prepend=processed[0])
                emphasis_strength = min(modulation.pitch_adjustment * 0.2, 0.1)  # Cap at 10%
                processed = processed + high_freq_emphasis * emphasis_strength
        
        # Prevent clipping
        max_val = np.max(np.abs(processed))
        if max_val > 1.0:
            processed = processed / max_val * 0.95
        
        return processed
    
    def _apply_simple_lowpass(self, audio: np.ndarray, sample_rate: int) -> np.ndarray:
        """Simple low-pass filter"""
        # Moving average filter
        window_size = max(3, int(sample_rate * 0.0002))
        if window_size >= len(audio):
            return audio
        
        kernel = np.ones(window_size) / window_size
        filtered = np.convolve(audio, kernel, mode='same')
        return filtered
    
    def _apply_simple_highpass(self, audio: np.ndarray, sample_rate: int) -> np.ndarray:
        """Simple high-pass filter"""
        # Difference filter
        filtered = np.diff(audio, prepend=audio[0])
        return filtered * 0.5 + audio * 0.5
    
    def apply_chained_effects(self, audio: np.ndarray, 
                            content_types: List[ContentType],
                            sample_rate: int = 22050,
                            transition_smoothing: bool = True,
                            **kwargs) -> np.ndarray:
        """Apply multiple effects in sequence with smooth transitions"""
        if not content_types:
            return audio
        
        processed = audio.copy()
        
        # Apply each effect with reduced intensity for chaining
        blend_factor = 1.0 / len(content_types)
        
        result = np.zeros_like(audio)
        
        for content_type in content_types:
            effect_result = self.apply_content_effect(
                audio, content_type, sample_rate, **kwargs
            )
            result += effect_result * blend_factor
        
        # Apply transition smoothing if requested
        if transition_smoothing:
            result = self._apply_transition_smoothing(result, sample_rate)
        
        return result
    
    def _apply_transition_smoothing(self, audio: np.ndarray, 
                                  sample_rate: int) -> np.ndarray:
        """Apply smoothing for transitions between effects"""
        # Simple smoothing using gentle low-pass filter
        window_size = max(3, int(sample_rate * 0.001))  # 1ms window
        if window_size >= len(audio):
            return audio
        
        kernel = np.ones(window_size) / window_size
        smoothed = np.convolve(audio, kernel, mode='same')
        
        # Blend with original
        return audio * 0.8 + smoothed * 0.2
    
    def get_effect_info(self) -> Dict[str, Any]:
        """Get information about available effects"""
        info = {}
        
        for content_type, effect in self.effects.items():
            info[content_type.value] = {
                "effect_class": effect.__class__.__name__,
                "config": getattr(effect, 'config', {}),
                "available": True
            }
        
        return info

def create_content_effects_manager() -> ContentEffectsManager:
    """Factory function to create content effects manager"""
    return ContentEffectsManager()

if __name__ == "__main__":
    # Test the effects system
    print("🎵 Testing Content-Specific Audio Effects")
    
    if not DEPENDENCIES_AVAILABLE:
        print("❌ Dependencies not available for testing")
        exit(1)
    
    # Create effects manager
    manager = create_content_effects_manager()
    
    print(f"✅ Effects manager created")
    print(f"📊 Available effects: {list(manager.effects.keys())}")
    
    # Create test audio
    sample_rate = 22050
    duration = 1.0
    t = np.linspace(0, duration, int(sample_rate * duration), False)
    test_audio = np.sin(2 * np.pi * 440 * t) * 0.7  # A4 note
    
    print(f"\n🎤 Testing each effect:")
    
    for content_type in [ContentType.THINKING, ContentType.NARRATION, ContentType.CLARIFICATION]:
        print(f"\n🎯 Testing {content_type.value} effect...")
        
        processed = manager.apply_content_effect(
            audio=test_audio,
            content_type=content_type,
            sample_rate=sample_rate,
            text="This is a test of the content-specific audio effects system."
        )
        
        # Check that audio was processed
        if np.array_equal(test_audio, processed):
            print(f"   ⚠️  Audio unchanged - effect may not be working")
        else:
            print(f"   ✅ Audio processed successfully")
            
        # Check audio quality
        rms_original = np.sqrt(np.mean(test_audio**2))
        rms_processed = np.sqrt(np.mean(processed**2))
        
        print(f"   📊 RMS: {rms_original:.3f} → {rms_processed:.3f}")
        print(f"   📊 Max: {np.max(np.abs(processed)):.3f}")
    
    print(f"\n🎉 Content effects system test complete!")
    print(f"🎯 Effects are ready for integration with TTS controller")