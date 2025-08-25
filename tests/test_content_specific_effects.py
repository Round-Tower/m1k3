#!/usr/bin/env python3
"""
Test suite for Content-Specific Audio Effects - TDD approach
Tests the specialized audio effects for different content types
"""

import pytest
import sys
import os
import numpy as np
from unittest.mock import Mock, patch

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Import will fail initially - that's expected in TDD!
try:
    from src.tts.effects.content_specific_effects import (
        ThinkingEffect,
        NarrationEffect, 
        ClarificationEffect,
        ContentAwareEffect,
        AnswerEffect,
        ContentEffectsManager
    )
    from src.tts.controllers.intelligent_tts_controller import ContentTypeModulation
    from src.utils.model_output_parser import ContentType
    MODULES_AVAILABLE = True
except ImportError:
    MODULES_AVAILABLE = False
    pytest.skip("content_specific_effects.py not yet implemented", allow_module_level=True)

class TestThinkingEffect:
    """Test the thinking-specific audio effect"""
    
    @pytest.fixture
    def thinking_effect(self):
        """Create thinking effect with default settings"""
        return ThinkingEffect()
    
    @pytest.fixture
    def sample_audio(self):
        """Create sample audio data for testing"""
        # Generate a simple sine wave for testing
        sample_rate = 22050
        duration = 1.0  # 1 second
        frequency = 440  # A4 note
        t = np.linspace(0, duration, int(sample_rate * duration), False)
        audio = np.sin(2 * np.pi * frequency * t)
        return audio
    
    def test_thinking_effect_initialization(self):
        """Test thinking effect initialization with default settings"""
        effect = ThinkingEffect()
        
        assert effect.volume_reduction > 0  # Should reduce volume
        assert effect.speed_reduction > 0   # Should reduce speed
        assert effect.reverb_amount > 0     # Should add reverb
        assert effect.pitch_adjustment < 0  # Should lower pitch
    
    def test_thinking_effect_custom_settings(self):
        """Test thinking effect with custom configuration"""
        config = {
            "volume_reduction": 0.3,
            "speed_reduction": 0.2, 
            "reverb_amount": 0.15,
            "pitch_adjustment": -0.05
        }
        
        effect = ThinkingEffect(config=config)
        
        assert effect.volume_reduction == 0.3
        assert effect.speed_reduction == 0.2
        assert effect.reverb_amount == 0.15
        assert effect.pitch_adjustment == -0.05
    
    def test_thinking_effect_audio_processing(self, thinking_effect, sample_audio):
        """Test that thinking effect processes audio correctly"""
        original_audio = sample_audio.copy()
        
        # Process the audio
        processed_audio = thinking_effect.process(original_audio, sample_rate=22050)
        
        # Should return processed audio
        assert processed_audio is not None
        assert isinstance(processed_audio, np.ndarray)
        
        # Audio should be modified (different from original)
        assert not np.array_equal(original_audio, processed_audio)
        
        # Volume should be reduced (RMS should be lower)
        original_rms = np.sqrt(np.mean(original_audio**2))
        processed_rms = np.sqrt(np.mean(processed_audio**2))
        assert processed_rms < original_rms  # Should be quieter
    
    def test_thinking_effect_reverb_application(self, thinking_effect, sample_audio):
        """Test that reverb is applied to thinking content"""
        # Mock the reverb processing to verify it's called
        with patch.object(thinking_effect, '_apply_reverb') as mock_reverb:
            mock_reverb.return_value = sample_audio  # Return unmodified for test
            
            thinking_effect.process(sample_audio, sample_rate=22050)
            
            # Reverb should have been applied
            mock_reverb.assert_called_once()
            
            # Should have been called with correct parameters
            call_args = mock_reverb.call_args[1]
            assert 'reverb_amount' in call_args
            assert call_args['reverb_amount'] == thinking_effect.reverb_amount
    
    def test_thinking_effect_parameter_bounds(self):
        """Test that thinking effect parameters are within reasonable bounds"""
        effect = ThinkingEffect()
        
        # Volume reduction should be reasonable (not too extreme)
        assert 0.1 <= effect.volume_reduction <= 0.5
        
        # Speed reduction should be subtle
        assert 0.1 <= effect.speed_reduction <= 0.3
        
        # Reverb should be subtle
        assert 0.05 <= effect.reverb_amount <= 0.4
        
        # Pitch adjustment should be subtle
        assert -0.2 <= effect.pitch_adjustment <= 0.0

class TestNarrationEffect:
    """Test the narration-specific audio effect"""
    
    @pytest.fixture
    def narration_effect(self):
        """Create narration effect with default settings"""
        return NarrationEffect()
    
    @pytest.fixture
    def sample_audio(self):
        """Create sample audio data for testing"""
        sample_rate = 22050
        duration = 1.0
        frequency = 440
        t = np.linspace(0, duration, int(sample_rate * duration), False)
        audio = np.sin(2 * np.pi * frequency * t)
        return audio
    
    def test_narration_effect_initialization(self):
        """Test narration effect initialization"""
        effect = NarrationEffect()
        
        assert effect.warmth_enhancement > 0    # Should add warmth
        assert effect.expressiveness_factor > 0 # Should increase expressiveness
        assert effect.speed_adjustment > 0      # Should increase speed slightly
    
    def test_narration_effect_warmth_processing(self, narration_effect, sample_audio):
        """Test that warmth enhancement is applied"""
        with patch.object(narration_effect, '_apply_warmth') as mock_warmth:
            mock_warmth.return_value = sample_audio
            
            narration_effect.process(sample_audio, sample_rate=22050)
            
            # Warmth should have been applied
            mock_warmth.assert_called_once()
            
            # Should enhance warmth
            call_args = mock_warmth.call_args[1]
            assert 'warmth_amount' in call_args
            assert call_args['warmth_amount'] == narration_effect.warmth_enhancement
    
    def test_narration_effect_expressiveness(self, narration_effect, sample_audio):
        """Test that expressiveness enhancement is applied"""
        processed_audio = narration_effect.process(sample_audio, sample_rate=22050)
        
        # Should apply dynamic range expansion for expressiveness
        assert processed_audio is not None
        assert isinstance(processed_audio, np.ndarray)
        
        # Should be different from original (more expressive)
        assert not np.array_equal(sample_audio, processed_audio)
    
    def test_narration_effect_storytelling_pace(self, narration_effect):
        """Test that narration has appropriate pacing adjustments"""
        # Narration should have slightly faster pace for storytelling
        assert narration_effect.speed_adjustment > 0
        assert narration_effect.speed_adjustment <= 0.2  # But not too fast

class TestClarificationEffect:
    """Test the clarification-specific audio effect"""
    
    @pytest.fixture
    def clarification_effect(self):
        """Create clarification effect"""
        return ClarificationEffect()
    
    @pytest.fixture
    def sample_audio(self):
        """Create sample audio data for testing"""
        sample_rate = 22050
        duration = 1.0
        frequency = 440
        t = np.linspace(0, duration, int(sample_rate * duration), False)
        audio = np.sin(2 * np.pi * frequency * t)
        return audio
    
    def test_clarification_effect_initialization(self):
        """Test clarification effect initialization"""
        effect = ClarificationEffect()
        
        assert effect.pitch_rise_amount > 0     # Should raise pitch for questions
        assert effect.intonation_curve is not None  # Should have intonation curve
        assert effect.question_emphasis > 0     # Should emphasize questions
    
    def test_clarification_effect_pitch_curve(self, clarification_effect, sample_audio):
        """Test that pitch curve is applied for question intonation"""
        with patch.object(clarification_effect, '_apply_pitch_curve') as mock_pitch:
            mock_pitch.return_value = sample_audio
            
            clarification_effect.process(sample_audio, sample_rate=22050)
            
            # Pitch curve should have been applied
            mock_pitch.assert_called_once()
            
            # Should apply rising pitch curve
            call_args = mock_pitch.call_args[1] 
            assert 'curve_type' in call_args
            assert call_args['curve_type'] == 'rising'
    
    def test_clarification_effect_question_detection(self, clarification_effect):
        """Test that effect can detect and emphasize questions"""
        # Test text with question markers
        question_texts = [
            "What do you think?",
            "Could you clarify?", 
            "How does this work?",
            "Are you sure about that?"
        ]
        
        for question in question_texts:
            # Should detect question markers
            is_question = clarification_effect._is_question_text(question)
            assert is_question == True
        
        # Test non-question text
        statement_texts = [
            "This is a statement.",
            "Here's what I think.",
            "The answer is clear."
        ]
        
        for statement in statement_texts:
            is_question = clarification_effect._is_question_text(statement)
            assert is_question == False

class TestContentAwareEffect:
    """Test the base content-aware effect system"""
    
    def test_content_aware_effect_interface(self):
        """Test that content-aware effect has proper interface"""
        # This tests the base interface that all content effects should implement
        # Use a concrete implementation for testing
        effect = AnswerEffect()
        
        # Should have process method
        assert hasattr(effect, 'process')
        assert callable(getattr(effect, 'process'))
        
        # Should have content type
        assert hasattr(effect, 'content_type')
        
        # Should have configuration
        assert hasattr(effect, 'config')
    
    def test_content_aware_effect_audio_format_validation(self):
        """Test audio format validation"""
        effect = AnswerEffect()
        
        # Should validate audio format
        valid_audio = np.array([0.1, 0.2, 0.3, -0.1, -0.2])
        assert effect._validate_audio_format(valid_audio) == True
        
        # Should reject invalid formats
        invalid_audio = [[1, 2, 3], [4, 5, 6]]  # Wrong shape
        assert effect._validate_audio_format(invalid_audio) == False
        
        # Should reject audio that's too loud (clipping)
        clipping_audio = np.array([1.5, -1.5, 2.0])  # Values > 1.0
        assert effect._validate_audio_format(clipping_audio) == False

class TestContentEffectsManager:
    """Test the manager that coordinates all content effects"""
    
    @pytest.fixture
    def effects_manager(self):
        """Create effects manager"""
        return ContentEffectsManager()
    
    def test_effects_manager_initialization(self, effects_manager):
        """Test effects manager initializes with all content type effects"""
        # Should have effects for all content types
        assert ContentType.THINKING in effects_manager.effects
        assert ContentType.NARRATION in effects_manager.effects
        assert ContentType.CLARIFICATION in effects_manager.effects
        
        # Should have appropriate effect types
        assert isinstance(effects_manager.effects[ContentType.THINKING], ThinkingEffect)
        assert isinstance(effects_manager.effects[ContentType.NARRATION], NarrationEffect)
        assert isinstance(effects_manager.effects[ContentType.CLARIFICATION], ClarificationEffect)
    
    def test_effects_manager_apply_content_effect(self, effects_manager):
        """Test applying effect based on content type"""
        # Create sample audio
        sample_audio = np.sin(2 * np.pi * 440 * np.linspace(0, 1, 22050))
        
        # Apply thinking effect
        thinking_audio = effects_manager.apply_content_effect(
            audio=sample_audio,
            content_type=ContentType.THINKING,
            sample_rate=22050
        )
        
        # Should return processed audio
        assert thinking_audio is not None
        assert isinstance(thinking_audio, np.ndarray)
        assert len(thinking_audio) > 0
        
        # Should be different from original (processed)
        assert not np.array_equal(sample_audio, thinking_audio)
    
    def test_effects_manager_modulation_integration(self, effects_manager):
        """Test integration with modulation settings"""
        # Create modulation settings
        modulation = ContentTypeModulation(
            volume_multiplier=0.8,
            speed_multiplier=0.9,
            pitch_adjustment=-0.1
        )
        
        sample_audio = np.sin(2 * np.pi * 440 * np.linspace(0, 1, 22050))
        
        # Apply with modulation
        processed_audio = effects_manager.apply_content_effect(
            audio=sample_audio,
            content_type=ContentType.THINKING,
            sample_rate=22050,
            modulation=modulation
        )
        
        # Should integrate modulation parameters
        assert processed_audio is not None
        
        # Volume should be affected by modulation
        original_rms = np.sqrt(np.mean(sample_audio**2))
        processed_rms = np.sqrt(np.mean(processed_audio**2))
        
        # Should be quieter due to volume_multiplier=0.8
        assert processed_rms < original_rms
    
    def test_effects_manager_chaining_effects(self, effects_manager):
        """Test chaining multiple effects"""
        sample_audio = np.sin(2 * np.pi * 440 * np.linspace(0, 1, 22050))
        
        # Enable effect chaining for complex content
        effects_manager.enable_effect_chaining = True
        
        # Apply multiple effects (for complex mixed content)
        chained_audio = effects_manager.apply_chained_effects(
            audio=sample_audio,
            content_types=[ContentType.THINKING, ContentType.NARRATION],
            sample_rate=22050,
            transition_smoothing=True
        )
        
        # Should apply and blend multiple effects
        assert chained_audio is not None
        assert isinstance(chained_audio, np.ndarray)

class TestEffectParameterValidation:
    """Test parameter validation for all effects"""
    
    def test_effect_parameter_ranges(self):
        """Test that all effect parameters are within reasonable ranges"""
        effects = [
            ThinkingEffect(),
            NarrationEffect(), 
            ClarificationEffect()
        ]
        
        for effect in effects:
            # All volume adjustments should be reasonable
            if hasattr(effect, 'volume_reduction'):
                assert 0.0 <= effect.volume_reduction <= 0.6
            if hasattr(effect, 'volume_boost'):
                assert 0.0 <= effect.volume_boost <= 0.3
                
            # Speed adjustments should be subtle
            if hasattr(effect, 'speed_reduction'):
                assert 0.0 <= effect.speed_reduction <= 0.4
            if hasattr(effect, 'speed_adjustment'):
                assert -0.3 <= effect.speed_adjustment <= 0.3
                
            # Pitch adjustments should be subtle
            if hasattr(effect, 'pitch_adjustment'):
                assert -0.3 <= effect.pitch_adjustment <= 0.3
            if hasattr(effect, 'pitch_rise_amount'):
                assert 0.0 <= effect.pitch_rise_amount <= 0.4

class TestAudioQualityPreservation:
    """Test that effects preserve audio quality"""
    
    @pytest.fixture
    def high_quality_audio(self):
        """Create high quality test audio"""
        sample_rate = 22050
        duration = 2.0
        # Create complex audio with multiple harmonics
        t = np.linspace(0, duration, int(sample_rate * duration), False)
        fundamental = np.sin(2 * np.pi * 440 * t)
        harmonic2 = 0.5 * np.sin(2 * np.pi * 880 * t)
        harmonic3 = 0.25 * np.sin(2 * np.pi * 1320 * t)
        audio = fundamental + harmonic2 + harmonic3
        return audio * 0.7  # Prevent clipping
    
    def test_effects_preserve_audio_quality(self, high_quality_audio):
        """Test that effects don't degrade audio quality excessively"""
        effects = [
            ThinkingEffect(),
            NarrationEffect(),
            ClarificationEffect()
        ]
        
        for effect in effects:
            processed = effect.process(high_quality_audio, sample_rate=22050)
            
            # Should not introduce clipping
            assert np.max(np.abs(processed)) <= 1.0
            
            # Should not be silent (complete signal loss)
            rms = np.sqrt(np.mean(processed**2))
            assert rms > 0.01  # Some minimum signal level
            
            # Should maintain reasonable dynamic range
            dynamic_range = np.max(processed) - np.min(processed)
            assert dynamic_range > 0.1  # Not completely flattened

if __name__ == "__main__":
    pytest.main([__file__, "-v", "--tb=short"])