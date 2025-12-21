#!/usr/bin/env python3
"""
Integration Tests for Voice Narration Handling
Tests the complete pipeline from input processing to TTS synthesis
"""

import unittest
import sys
import logging
from pathlib import Path

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent))

from voice_input_processor import VoiceInputProcessor, ProcessingConfig, NarrationMode, InputFormat
from src.tts.controllers.intelligent_tts_controller import IntelligentTTSController
from src.utils.model_output_parser import ContentType
from enhanced_voice_engine import VoiceManager

# Set up logging for tests
logging.basicConfig(level=logging.WARNING)  # Reduce noise during tests

class MockVoiceEngine:
    """Mock voice engine for testing without actual audio synthesis"""
    
    def __init__(self):
        self.synthesis_calls = []
        self.voice_enabled = True
        
    def synthesize_and_play(self, text: str, background: bool = False) -> bool:
        self.synthesis_calls.append({
            'text': text,
            'background': background
        })
        print(f"MOCK SYNTHESIS: '{text}'")  # For debugging
        return True

class TestVoiceNarrationIntegration(unittest.TestCase):
    """Test complete narration handling pipeline"""
    
    def setUp(self):
        self.processor = VoiceInputProcessor()
        self.mock_engine = MockVoiceEngine()
        self.tts_controller = IntelligentTTSController(voice_engine=self.mock_engine)
        
    def test_skip_narration_mode_integration(self):
        """Test that narration is properly skipped in the full pipeline"""
        input_text = "*The assistant pauses* Hello there! *gestures* Welcome to our demo."
        
        # Configure for skipping narration
        config = ProcessingConfig(narration_mode=NarrationMode.SKIP)
        
        # Process input
        result = self.processor.process(input_text, config)
        
        # Check processing result
        self.assertTrue(result.success)
        self.assertEqual(len(result.segments), 4)  # 2 narration (skipped) + 2 speech
        
        # Check segment modes
        narration_segments = [s for s in result.segments if s.content_type == ContentType.NARRATION]
        speech_segments = [s for s in result.segments if s.content_type != ContentType.NARRATION]
        
        self.assertEqual(len(narration_segments), 2)
        self.assertEqual(len(speech_segments), 2)
        
        # All narration should be marked for skipping
        for segment in narration_segments:
            self.assertEqual(segment.synthesis_mode, "SKIP")
            
        # Speech should be marked normal
        for segment in speech_segments:
            self.assertEqual(segment.synthesis_mode, "NORMAL")
        
        # Run through TTS controller
        self.mock_engine.synthesis_calls.clear()
        
        for segment in result.segments:
            job = self._create_mock_job(segment)
            success = self.tts_controller.process_job(job)
            self.assertTrue(success)
        
        # Only speech segments should have been synthesized (2 speech segments)
        self.assertEqual(len(self.mock_engine.synthesis_calls), 2)
        
        # Check both speech segments were synthesized
        synthesized_texts = [call['text'] for call in self.mock_engine.synthesis_calls]
        self.assertIn("Hello there!", synthesized_texts)
        self.assertIn("Welcome to our demo.", synthesized_texts)
        
    def test_pause_narration_mode_integration(self):
        """Test that narration is converted to pauses"""
        input_text = "*The assistant pauses* Hello there!"
        
        config = ProcessingConfig(narration_mode=NarrationMode.PAUSE)
        result = self.processor.process(input_text, config)
        
        self.assertTrue(result.success)
        
        # Find pause segments
        pause_segments = [s for s in result.segments if s.synthesis_mode == "PAUSE"]
        self.assertGreater(len(pause_segments), 0)
        
        # Run through TTS controller (should insert pauses)
        for segment in result.segments:
            job = self._create_mock_job(segment)
            success = self.tts_controller.process_job(job)
            self.assertTrue(success)
            
    def test_ssml_integration(self):
        """Test SSML markup processing through full pipeline"""
        input_text = "Welcome! <break time='500ms'/> This is <emphasis level='strong'>important</emphasis>."
        
        config = ProcessingConfig(
            input_format=InputFormat.SSML,
            preserve_ssml_timing=True
        )
        
        result = self.processor.process(input_text, config)
        
        self.assertTrue(result.success)
        self.assertEqual(result.format_detected, InputFormat.SSML)
        
        # Check that SSML was processed
        processed_text = " ".join(s.text for s in result.segments if s.synthesis_mode == "NORMAL")
        self.assertIn("IMPORTANT", processed_text)  # Emphasis should be converted to caps
        self.assertNotIn("<break", processed_text)  # SSML tags should be removed
        
    def test_mixed_content_integration(self):
        """Test mixed narration and SSML content"""
        input_text = "*assistant starts* Hello! <break time='300ms'/> <emphasis>Welcome</emphasis> *waves* to our demo."
        
        config = ProcessingConfig(
            input_format=InputFormat.MIXED,
            narration_mode=NarrationMode.SKIP
        )
        
        result = self.processor.process(input_text, config)
        
        self.assertTrue(result.success)
        self.assertEqual(result.format_detected, InputFormat.MIXED)
        
        # Should have both narration (skipped) and speech segments
        narration_segments = [s for s in result.segments if s.content_type == ContentType.NARRATION]
        speech_segments = [s for s in result.segments if s.content_type != ContentType.NARRATION]
        
        self.assertGreater(len(narration_segments), 0)
        self.assertGreater(len(speech_segments), 0)
        
        # Narration should be skipped
        for segment in narration_segments:
            self.assertEqual(segment.synthesis_mode, "SKIP")
    
    def test_error_handling_integration(self):
        """Test error handling in the integration pipeline"""
        # Test with malformed input
        input_text = "Hello <malformed tag without closing"
        
        result = self.processor.process(input_text)
        
        # Should still succeed with warnings
        self.assertTrue(result.success)
        self.assertGreater(len(result.segments), 0)
        
    def test_empty_input_handling(self):
        """Test handling of empty or whitespace-only input"""
        test_cases = ["", "   ", "\n\t", "*empty narration*"]
        
        for test_input in test_cases:
            with self.subTest(input_text=test_input):
                result = self.processor.process(test_input)
                # Should handle gracefully without crashing
                self.assertIsNotNone(result)
                
    def test_long_text_handling(self):
        """Test handling of long text with multiple content types"""
        long_text = (
            "*The narrator begins* This is a very long piece of text that contains "
            "multiple <emphasis>important</emphasis> sections and several "
            "*dramatic pauses* throughout the content. <break time='1s'/> "
            "It should be properly processed and segmented according to the "
            "*gestures broadly* configuration settings we have established. "
            "<prosody rate='slow'>This part should be spoken slowly</prosody> "
            "while the rest continues at normal pace. *concludes presentation*"
        )
        
        config = ProcessingConfig(
            narration_mode=NarrationMode.SKIP,
            max_text_length=500  # Allow longer text for this test
        )
        
        result = self.processor.process(long_text, config)
        
        self.assertTrue(result.success)
        self.assertGreater(len(result.segments), 3)  # Should be segmented
        
        # Check that processing completed without errors
        error_count = sum(1 for log in result.processing_log if 'error' in log.lower())
        self.assertEqual(error_count, 0)
    
    def _create_mock_job(self, segment):
        """Helper to create a mock TTS job for testing"""
        from src.tts.controllers.intelligent_tts_controller import TTSJob
        return TTSJob(segment=segment, priority=segment.content_type.priority)

class TestConfigurationOptions(unittest.TestCase):
    """Test various configuration options"""
    
    def test_different_narration_modes(self):
        """Test all narration modes work correctly"""
        input_text = "*The assistant speaks* Hello world!"
        
        modes = [NarrationMode.SKIP, NarrationMode.PAUSE, NarrationMode.REMOVE, NarrationMode.PRESERVE]
        
        for mode in modes:
            with self.subTest(narration_mode=mode):
                config = ProcessingConfig(narration_mode=mode)
                processor = VoiceInputProcessor(config)
                
                result = processor.process(input_text)
                self.assertTrue(result.success)
                
                if mode == NarrationMode.SKIP:
                    narration_segments = [s for s in result.segments if s.content_type == ContentType.NARRATION]
                    for segment in narration_segments:
                        self.assertEqual(segment.synthesis_mode, "SKIP")
                        
                elif mode == NarrationMode.PAUSE:
                    pause_segments = [s for s in result.segments if s.synthesis_mode == "PAUSE"]
                    self.assertGreater(len(pause_segments), 0)
    
    def test_ssml_enabling_disabling(self):
        """Test SSML processing can be enabled/disabled"""
        input_text = "Hello <emphasis>world</emphasis>!"
        
        # With SSML enabled
        config_enabled = ProcessingConfig(preserve_ssml_timing=True)
        processor_enabled = VoiceInputProcessor(config_enabled)
        result_enabled = processor_enabled.process(input_text)
        
        # With SSML disabled (should treat as plain text)
        config_disabled = ProcessingConfig(preserve_ssml_timing=False)
        processor_disabled = VoiceInputProcessor(config_disabled)
        result_disabled = processor_disabled.process(input_text)
        
        # Both should succeed
        self.assertTrue(result_enabled.success)
        self.assertTrue(result_disabled.success)

class TestRealVoiceEngineIntegration(unittest.TestCase):
    """Test with real voice engine (if available)"""
    
    def setUp(self):
        self.voice_manager = None
        try:
            self.voice_manager = VoiceManager()
            if not self.voice_manager.load_model():
                self.voice_manager = None
        except Exception:
            self.voice_manager = None
    
    @unittest.skipIf(True, "Disabled for CI - requires actual TTS model")
    def test_real_voice_synthesis_with_narration_skipping(self):
        """Test real voice synthesis with narration properly skipped"""
        if not self.voice_manager:
            self.skipTest("Voice engine not available")
            
        input_text = "*The assistant speaks clearly* This is a test of the voice synthesis system."
        
        config = ProcessingConfig(narration_mode=NarrationMode.SKIP)
        processor = VoiceInputProcessor(config)
        tts_controller = IntelligentTTSController(voice_engine=self.voice_manager)
        
        # Process input
        result = processor.process(input_text)
        self.assertTrue(result.success)
        
        # Synthesize (this will produce actual audio if TTS is available)
        synthesis_results = []
        for segment in result.segments:
            job = TTSJob(segment=segment, priority=segment.content_type.priority)
            success = tts_controller.process_job(job)
            synthesis_results.append(success)
        
        # All segments should process successfully
        self.assertTrue(all(synthesis_results))

if __name__ == "__main__":
    # Run tests with higher verbosity
    unittest.main(verbosity=2)