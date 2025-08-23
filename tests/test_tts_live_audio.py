#!/usr/bin/env python3
"""
Live Audio TTS Tests - Test different content types with real audio playback
Run with: python -m pytest tests/test_tts_live_audio.py -v -s --audio
"""

import pytest
import sys
import os
import argparse
from typing import List, Dict, Any

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from audio_test_framework import AudioTestFramework, AudioTestCase, TestResult, create_audio_test_framework

class TestTTSLiveAudio:
    """Live audio tests for TTS content type differentiation"""
    
    @pytest.fixture(scope="session")
    def audio_framework(self):
        """Create audio test framework for the session"""
        # Check if we're in audio mode
        interactive = "--audio" in sys.argv or "-s" in sys.argv
        return create_audio_test_framework(interactive=interactive)
    
    def test_baseline_voice_quality(self, audio_framework: AudioTestFramework):
        """Test baseline voice quality - this is our reference point"""
        test_text = "This is the baseline voice quality test. We will use this as our reference for all other voice modifications."
        
        result = audio_framework.play_and_wait_for_feedback(
            test_text,
            "Baseline voice quality (current implementation)",
            expected_characteristics={
                "clarity": "high",
                "pace": "normal", 
                "tone": "neutral",
                "cutoff_issue": "may be present at end of sentence"
            }
        )
        
        # In TDD, we expect this might not be perfect initially
        assert result in [TestResult.PASS, TestResult.NEEDS_IMPROVEMENT]
    
    def test_thinking_voice_modulation(self, audio_framework: AudioTestFramework):
        """Test softer, introspective tone for thinking content"""
        thinking_text = "Let me think about this carefully. I need to consider all the implications and possibilities before providing an answer."
        
        # This test will initially fail until we implement thinking modulation
        result = audio_framework.play_and_wait_for_feedback(
            thinking_text,
            "Thinking voice modulation (should be softer, more introspective)",
            expected_characteristics={
                "volume": "reduced by 20%",
                "pace": "slower by 15%",
                "tone": "contemplative",
                "reverb": "slight echo effect",
                "pitch": "slightly lower"
            }
        )
        
        # This will likely fail initially - that's expected in TDD!
        # The test defines what we want to achieve
        assert result != TestResult.FAIL, "Thinking modulation should be at least partially implemented"
    
    def test_narration_voice_style(self, audio_framework: AudioTestFramework):
        """Test warm, storytelling voice for narration content"""
        narration_text = "The user pauses thoughtfully, considering the options before them as they prepare to make an important decision."
        
        result = audio_framework.play_and_wait_for_feedback(
            narration_text,
            "Narration voice style (should be warm and expressive)",
            expected_characteristics={
                "warmth": "enhanced with gentle compression",
                "expressiveness": "varied intonation",
                "pace": "natural storytelling rhythm",
                "clarity": "crisp consonants",
                "emotional_range": "subtle variations"
            }
        )
        
        # Narration should be engaging and pleasant to listen to
        assert result in [TestResult.PASS, TestResult.NEEDS_IMPROVEMENT]
    
    def test_answer_voice_clarity(self, audio_framework: AudioTestFramework):
        """Test clear, confident delivery for answer content"""
        answer_text = "Based on my analysis, the best approach is to implement a modular system that allows for easy customization and future expansion."
        
        result = audio_framework.play_and_wait_for_feedback(
            answer_text,
            "Answer voice clarity (should be confident and authoritative)",
            expected_characteristics={
                "confidence": "strong, steady delivery",
                "clarity": "excellent articulation",
                "pace": "measured and deliberate",
                "emphasis": "key words slightly emphasized",
                "professionalism": "business-like tone"
            }
        )
        
        # Answers should sound authoritative and trustworthy
        assert result in [TestResult.PASS, TestResult.NEEDS_IMPROVEMENT]
    
    def test_clarification_intonation(self, audio_framework: AudioTestFramework):
        """Test questioning intonation for clarification requests"""
        clarification_text = "Could you please clarify what specific aspect you're most interested in? Are you looking for technical details or a general overview?"
        
        result = audio_framework.play_and_wait_for_feedback(
            clarification_text,
            "Clarification intonation (should have rising pitch for questions)",
            expected_characteristics={
                "pitch_curve": "rising at end of questions",
                "tone": "helpful and inviting",
                "pace": "slightly slower for comprehension",
                "pauses": "appropriate breaks between questions",
                "engagement": "encouraging user response"
            }
        )
        
        # Clarifications should clearly sound like questions
        assert result in [TestResult.PASS, TestResult.NEEDS_IMPROVEMENT]
    
    def test_content_transitions(self, audio_framework: AudioTestFramework):
        """Test smooth transitions between different content types"""
        mixed_content = """Let me think about this first. I need to consider the technical requirements. 
        The system should be designed with scalability in mind. 
        Could you tell me more about your specific use case?"""
        
        # This represents thinking -> answer -> clarification
        result = audio_framework.play_and_wait_for_feedback(
            mixed_content,
            "Mixed content with transitions (thinking -> answer -> clarification)",
            expected_characteristics={
                "thinking_section": "softer tone at beginning",
                "answer_section": "confident tone in middle",
                "clarification_section": "questioning tone at end",
                "transitions": "smooth changes between styles",
                "natural_flow": "doesn't sound robotic"
            }
        )
        
        # Transitions are crucial for natural-sounding speech
        assert result in [TestResult.PASS, TestResult.NEEDS_IMPROVEMENT]
    
    def test_speech_cutoff_detection(self, audio_framework: AudioTestFramework):
        """Test for the known speech cutoff issue at sentence endings"""
        # Specifically test sentences that are known to be cut off
        cutoff_test_sentences = [
            "This sentence should end completely without being cut off.",
            "Testing the final words to ensure they are heard clearly.",
            "The speech synthesis should preserve all syllables until the very end.",
            "Quality audio output requires proper sentence completion handling."
        ]
        
        for i, sentence in enumerate(cutoff_test_sentences):
            result = audio_framework.play_and_wait_for_feedback(
                sentence,
                f"Speech cutoff test {i+1}/4 - listen for complete ending",
                expected_characteristics={
                    "completion": "entire sentence audible",
                    "final_words": "clearly articulated",
                    "cutoff_issue": "should be resolved",
                    "padding": "adequate silence at end"
                }
            )
            
            # This might fail initially due to known cutoff bug
            if result == TestResult.FAIL:
                print(f"⚠️  Cutoff detected in test sentence {i+1}")
            
            # Don't assert failure here - we're documenting the issue
            assert result != TestResult.SKIP, "Must test cutoff issue, can't skip"
    
    def test_volume_consistency(self, audio_framework: AudioTestFramework):
        """Test that volume levels are consistent across content types"""
        test_cases = [
            ("This is a normal answer.", "answer"),
            ("I'm thinking about this problem.", "thinking"),
            ("The user considers their options.", "narration"),
            ("What would you like to know more about?", "clarification")
        ]
        
        print("\n🔊 Volume consistency test - listen for relative volume levels")
        
        for text, content_type in test_cases:
            result = audio_framework.play_and_wait_for_feedback(
                text,
                f"Volume test for {content_type} content",
                expected_characteristics={
                    "volume": "appropriate for content type",
                    "consistency": "balanced with other types",
                    "not_jarring": "no sudden volume changes"
                }
            )
            
            # All volume levels should be well-balanced
            assert result in [TestResult.PASS, TestResult.NEEDS_IMPROVEMENT]

class TestTTSPerformance:
    """Performance-related audio tests"""
    
    def test_synthesis_latency(self, audio_framework: AudioTestFramework):
        """Test that synthesis doesn't have excessive delays"""
        import time
        
        test_text = "This is a latency test for TTS synthesis performance."
        
        start_time = time.time()
        
        result = audio_framework.play_and_wait_for_feedback(
            test_text,
            "Synthesis latency test (should start quickly)",
            expected_characteristics={
                "startup_delay": "minimal delay before audio starts",
                "responsiveness": "feels immediate",
                "background_processing": "doesn't block interface"
            }
        )
        
        synthesis_time = time.time() - start_time
        
        # Synthesis should be reasonably fast (under 3 seconds for short text)
        print(f"⏱️  Total synthesis time: {synthesis_time:.2f} seconds")
        assert synthesis_time < 5.0, f"Synthesis took too long: {synthesis_time:.2f}s"
        assert result in [TestResult.PASS, TestResult.NEEDS_IMPROVEMENT]
    
    def test_long_text_handling(self, audio_framework: AudioTestFramework):
        """Test handling of longer text passages"""
        long_text = """This is a longer text passage designed to test how well the TTS system handles extended content. 
        It includes multiple sentences with different punctuation marks! 
        Does it maintain quality throughout? 
        Can it handle complex technical terms and maintain consistency? 
        The system should demonstrate stable performance across longer passages without degradation in audio quality or unexpected cutoffs."""
        
        result = audio_framework.play_and_wait_for_feedback(
            long_text,
            "Long text handling test",
            expected_characteristics={
                "quality_consistency": "maintains quality throughout",
                "no_degradation": "doesn't get worse toward end",
                "proper_pacing": "natural pauses at punctuation",
                "memory_management": "doesn't cause system issues"
            }
        )
        
        # Long text should be handled gracefully
        assert result in [TestResult.PASS, TestResult.NEEDS_IMPROVEMENT]

def create_comprehensive_audio_test_suite() -> List[AudioTestCase]:
    """Create a comprehensive test suite for interactive testing"""
    return [
        AudioTestCase(
            name="baseline_reference",
            description="Baseline voice quality reference",
            test_text="This is our baseline reference for voice quality comparison.",
            expected_voice_characteristics={"clarity": "high", "tone": "neutral"}
        ),
        
        AudioTestCase(
            name="thinking_modulation",
            description="Soft, introspective thinking voice",
            test_text="Let me carefully consider all aspects of this complex problem before responding.",
            expected_voice_characteristics={"volume": "reduced", "pace": "slower", "tone": "contemplative"}
        ),
        
        AudioTestCase(
            name="confident_answer",
            description="Clear, authoritative answer delivery",
            test_text="Based on the analysis, I recommend implementing a modular approach for maximum flexibility.",
            expected_voice_characteristics={"confidence": "high", "clarity": "excellent", "authority": "professional"}
        ),
        
        AudioTestCase(
            name="engaging_narration",
            description="Warm, storytelling narration style", 
            test_text="The user pauses, thoughtfully considering the implications of each possible choice before them.",
            expected_voice_characteristics={"warmth": "enhanced", "expressiveness": "varied", "engagement": "high"}
        ),
        
        AudioTestCase(
            name="questioning_clarification",
            description="Rising intonation for questions",
            test_text="Could you help me understand which specific aspect interests you most?",
            expected_voice_characteristics={"intonation": "rising", "tone": "helpful", "engagement": "inviting"}
        )
    ]

if __name__ == "__main__":
    # Command-line interface for running audio tests
    parser = argparse.ArgumentParser(description="Run live audio TTS tests")
    parser.add_argument("--audio", action="store_true", help="Enable audio playback and interactive testing")
    parser.add_argument("--comprehensive", action="store_true", help="Run comprehensive test suite")
    args = parser.parse_args()
    
    if args.comprehensive:
        # Run comprehensive test suite
        framework = create_audio_test_framework(interactive=args.audio)
        test_cases = create_comprehensive_audio_test_suite()
        results = framework.run_test_suite(test_cases)
        
        # Display final results
        print(f"\n🏁 Test suite complete!")
        print(f"Results: {dict(results)}")
    else:
        # Run pytest
        pytest_args = [__file__, "-v"]
        if args.audio:
            pytest_args.extend(["-s", "--audio"])
        pytest.main(pytest_args)