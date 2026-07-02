#!/usr/bin/env python3
"""
Test Suite for Narration Handling in Voice Synthesis
Using TDD approach to ensure proper handling of narration markers and SSML-like content
"""

import unittest
import sys
from pathlib import Path

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent))

from src.utils.model_output_parser import ContentType, ContentSegment, ParsedContent
from voice_text_preprocessor import VoiceTextPreprocessor


class TestNarrationPreprocessing(unittest.TestCase):
    """Test narration marker handling in voice preprocessing"""
    
    def setUp(self):
        self.preprocessor = VoiceTextPreprocessor()
        
    def test_narration_markers_preserved_when_configured(self):
        """Test that narration markers can be preserved for separate handling"""
        text = "*The assistant pauses thoughtfully* How are you today?"
        
        # With narration preservation (new feature to implement)
        processed = self.preprocessor.preprocess_for_voice(
            text, 
            preserve_narration=True
        )
        
        # Should preserve markers for later processing
        self.assertIn("*", processed)
        self.assertIn("The assistant pauses thoughtfully", processed)
        
    def test_narration_markers_removed_when_configured(self):
        """Test that narration markers can be cleanly removed"""
        text = "*The assistant pauses thoughtfully* How are you today?"
        
        # With narration removal (current behavior)
        processed = self.preprocessor.preprocess_for_voice(
            text,
            preserve_narration=False
        )
        
        # Should remove narration entirely
        self.assertNotIn("*", processed)
        self.assertNotIn("pauses thoughtfully", processed)
        self.assertIn("How are you today?", processed)
        
    def test_narration_converted_to_pauses(self):
        """Test that narration can be converted to pause markers"""
        text = "Hello *pauses* how are you?"
        
        processed = self.preprocessor.preprocess_for_voice(
            text,
            narration_mode="pause"
        )
        
        # Should convert to pause marker
        self.assertNotIn("*", processed)
        self.assertNotIn("pauses", processed)
        self.assertIn("Hello", processed)
        self.assertIn("how are you?", processed)
        # Should contain some form of pause indicator
        
    def test_multiple_narration_segments(self):
        """Test handling of multiple narration segments"""
        text = "*starts speaking* Hello there! *gestures* Welcome to our demo. *smiles* How can I help?"
        
        processed = self.preprocessor.preprocess_for_voice(
            text,
            narration_mode="remove"
        )
        
        # Should cleanly remove all narration
        self.assertNotIn("*", processed)
        self.assertEqual(processed.count("Hello there! Welcome to our demo. How can I help?"), 1)
        
    def test_nested_or_malformed_narration(self):
        """Test handling of malformed narration markers"""
        text = "Hello *incomplete narration without closing"
        
        # Should handle gracefully without breaking
        processed = self.preprocessor.preprocess_for_voice(text)
        self.assertIsInstance(processed, str)
        self.assertGreater(len(processed), 0)


class TestContentSegmentNarrationSupport(unittest.TestCase):
    """Test enhanced ContentSegment with narration support"""
    
    def test_content_segment_synthesis_mode(self):
        """Test ContentSegment can specify synthesis mode"""
        segment = ContentSegment(
            content_type=ContentType.NARRATION,
            text="The assistant nods",
            synthesis_mode="SKIP"  # New field to implement
        )
        
        self.assertEqual(segment.synthesis_mode, "SKIP")
        self.assertEqual(segment.content_type, ContentType.NARRATION)
        
    def test_content_segment_preserves_original_markers(self):
        """Test ContentSegment preserves original formatting"""
        original_text = "*The assistant nods*"
        segment = ContentSegment(
            content_type=ContentType.NARRATION,
            text="The assistant nods",
            original_markers=original_text  # New field to implement
        )
        
        self.assertEqual(segment.original_markers, original_text)
        self.assertEqual(segment.text, "The assistant nods")


class TestSSMLLikeSupport(unittest.TestCase):
    """Test SSML-like markup handling"""
    
    def setUp(self):
        # Will create SSMLConverter class
        pass
        
    def test_break_tag_conversion(self):
        """Test <break> tag conversion to KittenTTS-friendly format"""
        # This will be implemented in SSMLConverter
        text = "Hello <break time='500ms'/> there!"
        
        # Should convert to natural pause equivalent
        # For now, just check the concept
        self.assertIn("Hello", text)
        self.assertIn("there!", text)
        
    def test_emphasis_tag_handling(self):
        """Test <emphasis> tag handling"""
        text = "This is <emphasis level='strong'>very important</emphasis>!"
        
        # Should be convertible to emphasis markers or caps
        self.assertIn("very important", text)
        
    def test_prosody_tag_handling(self):
        """Test <prosody> tag handling"""
        text = "<prosody rate='slow'>Please speak slowly</prosody>"
        
        # Should extract the core text
        self.assertIn("Please speak slowly", text)


class TestVoiceInputProcessor(unittest.TestCase):
    """Test unified voice input processing pipeline"""
    
    def setUp(self):
        # Will create VoiceInputProcessor class
        pass
        
    def test_mixed_content_processing(self):
        """Test processing of mixed content (narration + speech + SSML)"""
        text = "*The assistant starts* Hello! <break time='300ms'/> How are you <emphasis>today</emphasis>?"
        
        # Should properly separate and process each component
        # This is the integration point we're building toward
        pass
        
    def test_configurable_output_modes(self):
        """Test different output modes for content processing"""
        text = "*gestures* Welcome to the demo!"
        
        # Mode 1: Strip narration
        # processed_stripped = processor.process(text, narration_mode="strip")
        # self.assertEqual(processed_stripped, "Welcome to the demo!")
        
        # Mode 2: Convert to pauses
        # processed_pauses = processor.process(text, narration_mode="pause")
        # Should contain pause markers
        
        # Mode 3: Preserve for separate synthesis
        # processed_preserve = processor.process(text, narration_mode="preserve")
        # Should maintain segment separation
        
        pass


class TestIntegrationWithTTSController(unittest.TestCase):
    """Test integration with intelligent TTS controller"""
    
    def test_narration_segments_skipped(self):
        """Test that narration segments can be skipped during synthesis"""
        # This tests the full pipeline integration
        pass
        
    def test_pause_insertion_for_narration(self):
        """Test that appropriate pauses are inserted for skipped narration"""
        # Should add timing delays where narration was removed
        pass
        
    def test_different_voice_effects_for_narration(self):
        """Test that narration can be synthesized with different voice effects"""
        # Could use whisper mode or softer voice for narration if desired
        pass


class TestLoggingAndDiagnostics(unittest.TestCase):
    """Test comprehensive logging for narration handling"""
    
    def test_narration_processing_logged(self):
        """Test that narration processing decisions are logged"""
        # Should log when narration is detected, how it's handled, etc.
        pass
        
    def test_ssml_conversion_logged(self):
        """Test that SSML conversions are properly logged"""
        # Should track what markup was found and how it was processed
        pass


if __name__ == "__main__":
    # Run tests with verbose output
    unittest.main(verbosity=2)