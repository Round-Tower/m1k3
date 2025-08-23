#!/usr/bin/env python3
"""
Test suite for Intelligent TTS Controller - TDD approach
Tests the orchestration of content-specific voice synthesis
"""

import pytest
import sys
import os
import time
from unittest.mock import Mock, MagicMock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Import will fail initially - that's expected in TDD!
try:
    from intelligent_tts_controller import (
        IntelligentTTSController, 
        TTSJob, 
        TTSStatus, 
        ContentTypeModulation
    )
    from model_output_parser import ContentType, ContentSegment, ParsedContent
    MODULES_AVAILABLE = True
except ImportError:
    MODULES_AVAILABLE = False
    pytest.skip("intelligent_tts_controller.py not yet implemented", allow_module_level=True)

class TestTTSJob:
    """Test the TTSJob data structure"""
    
    def test_tts_job_creation(self):
        """Test creating a TTS job"""
        segment = ContentSegment(ContentType.THINKING, "test thinking", 0.9)
        job = TTSJob(segment=segment, priority=1)
        
        assert job.segment == segment
        assert job.priority == 1
        assert job.status == TTSStatus.PENDING
        assert job.created_at is not None
    
    def test_tts_job_comparison(self):
        """Test TTS job priority comparison for queue ordering"""
        segment1 = ContentSegment(ContentType.THINKING, "thinking", 0.9)
        segment2 = ContentSegment(ContentType.ANSWER, "answer", 0.9)
        
        job1 = TTSJob(segment=segment1, priority=2)
        job2 = TTSJob(segment=segment2, priority=1)
        
        # Lower priority number should have higher precedence
        assert job2 < job1
    
    def test_tts_status_enum(self):
        """Test TTSStatus enumeration"""
        assert TTSStatus.PENDING.value == "pending"
        assert TTSStatus.IN_PROGRESS.value == "in_progress"
        assert TTSStatus.COMPLETED.value == "completed" 
        assert TTSStatus.FAILED.value == "failed"

class TestContentTypeModulation:
    """Test content type voice modulation settings"""
    
    def test_modulation_creation(self):
        """Test creating modulation settings"""
        modulation = ContentTypeModulation(
            volume_multiplier=0.8,
            speed_multiplier=0.9,
            pitch_adjustment=-0.1
        )
        
        assert modulation.volume_multiplier == 0.8
        assert modulation.speed_multiplier == 0.9
        assert modulation.pitch_adjustment == -0.1
    
    def test_modulation_validation(self):
        """Test modulation parameter validation"""
        # Valid parameters should not raise errors
        ContentTypeModulation(volume_multiplier=0.1)
        ContentTypeModulation(speed_multiplier=2.0)
        ContentTypeModulation(pitch_adjustment=-0.5)
        
        # Test boundary values
        with pytest.raises(ValueError):
            ContentTypeModulation(volume_multiplier=0.0)  # Too low
        
        with pytest.raises(ValueError):
            ContentTypeModulation(speed_multiplier=0.4)   # Too low
            
        with pytest.raises(ValueError):
            ContentTypeModulation(pitch_adjustment=0.6)   # Too high

class TestIntelligentTTSController:
    """Test the main TTS controller"""
    
    @pytest.fixture
    def mock_voice_engine(self):
        """Create a mock voice engine"""
        mock_engine = Mock()
        mock_engine.synthesize_and_play = Mock(return_value=True)
        mock_engine.get_status = Mock(return_value={"loaded": True})
        return mock_engine
    
    @pytest.fixture
    def controller(self, mock_voice_engine):
        """Create TTS controller with mock voice engine"""
        return IntelligentTTSController(voice_engine=mock_voice_engine)
    
    def test_controller_initialization(self, mock_voice_engine):
        """Test controller initialization with default modulations"""
        controller = IntelligentTTSController(voice_engine=mock_voice_engine)
        
        # Should have modulations for all content types
        assert ContentType.THINKING in controller.modulations
        assert ContentType.NARRATION in controller.modulations
        assert ContentType.ANSWER in controller.modulations
        assert ContentType.CLARIFICATION in controller.modulations
        
        # Should have empty job queue initially
        assert controller.job_queue.empty()
        assert not controller.is_processing
    
    def test_set_modulation(self, controller):
        """Test setting modulation for content type"""
        thinking_mod = ContentTypeModulation(volume_multiplier=0.7, speed_multiplier=0.8)
        
        controller.set_modulation(ContentType.THINKING, thinking_mod)
        
        assert controller.modulations[ContentType.THINKING] == thinking_mod
    
    def test_queue_content_segments(self, controller):
        """Test queuing multiple content segments"""
        segments = [
            ContentSegment(ContentType.THINKING, "thinking text", 0.9),
            ContentSegment(ContentType.ANSWER, "answer text", 0.9),
            ContentSegment(ContentType.CLARIFICATION, "question text", 0.8)
        ]
        
        parsed_content = ParsedContent(segments=segments)
        
        queued_jobs = controller.queue_content(parsed_content)
        
        # Should return list of queued jobs
        assert len(queued_jobs) == 3
        
        # Jobs should be queued (they will be processed in priority order by queue)
        priorities = [job.priority for job in queued_jobs]
        expected_priorities = [ContentType.THINKING.priority, ContentType.ANSWER.priority, ContentType.CLARIFICATION.priority]
        assert priorities == expected_priorities
        
        # Queue should have all jobs
        assert controller.job_queue.qsize() == 3
    
    def test_queue_priority_ordering(self, controller):
        """Test that jobs are queued in correct priority order"""
        segments = [
            ContentSegment(ContentType.THINKING, "thinking", 0.9),      # Priority 4
            ContentSegment(ContentType.CLARIFICATION, "question", 0.8), # Priority 1
            ContentSegment(ContentType.ANSWER, "answer", 0.9),          # Priority 2
        ]
        
        parsed_content = ParsedContent(segments=segments)
        controller.queue_content(parsed_content)
        
        # Jobs should be processed in priority order
        job1 = controller.job_queue.get()  # Should be clarification (priority 1)
        job2 = controller.job_queue.get()  # Should be answer (priority 2)
        job3 = controller.job_queue.get()  # Should be thinking (priority 4)
        
        assert job1.segment.content_type == ContentType.CLARIFICATION
        assert job2.segment.content_type == ContentType.ANSWER
        assert job3.segment.content_type == ContentType.THINKING
    
    def test_process_single_job(self, controller, mock_voice_engine):
        """Test processing a single TTS job"""
        segment = ContentSegment(ContentType.ANSWER, "test answer", 0.9)
        job = TTSJob(segment=segment, priority=2)
        
        # Mock the modulation application
        controller._apply_modulation = Mock()
        
        result = controller.process_job(job)
        
        # Should return success
        assert result is True
        
        # Job status should be updated
        assert job.status == TTSStatus.COMPLETED
        
        # Voice engine should have been called
        mock_voice_engine.synthesize_and_play.assert_called_once()
        
        # Modulation should have been applied
        controller._apply_modulation.assert_called_once_with(ContentType.ANSWER)
    
    def test_process_job_with_failure(self, controller, mock_voice_engine):
        """Test handling job processing failure"""
        segment = ContentSegment(ContentType.ANSWER, "test answer", 0.9)
        job = TTSJob(segment=segment, priority=2)
        
        # Make voice engine fail
        mock_voice_engine.synthesize_and_play.side_effect = Exception("TTS Error")
        
        result = controller.process_job(job)
        
        # Should return failure
        assert result is False
        
        # Job status should be updated to failed
        assert job.status == TTSStatus.FAILED
        
        # Error message should be stored
        assert job.error_message is not None
    
    def test_process_all_queued_jobs(self, controller, mock_voice_engine):
        """Test processing all jobs in queue sequentially"""
        segments = [
            ContentSegment(ContentType.THINKING, "thinking", 0.9),
            ContentSegment(ContentType.ANSWER, "answer", 0.9)
        ]
        
        parsed_content = ParsedContent(segments=segments)
        jobs = controller.queue_content(parsed_content)
        
        # Mock modulation application
        controller._apply_modulation = Mock()
        
        results = controller.process_all_queued()
        
        # Should process all jobs
        assert len(results) == 2
        
        # All jobs should succeed
        assert all(result.success for result in results)
        
        # Voice engine should have been called twice
        assert mock_voice_engine.synthesize_and_play.call_count == 2
        
        # Queue should be empty
        assert controller.job_queue.empty()
    
    def test_process_with_pause_between_segments(self, controller):
        """Test that there are appropriate pauses between segments"""
        segments = [
            ContentSegment(ContentType.ANSWER, "first part", 0.9),
            ContentSegment(ContentType.ANSWER, "second part", 0.9)
        ]
        
        parsed_content = ParsedContent(segments=segments)
        jobs = controller.queue_content(parsed_content)
        
        # Mock time.sleep to verify pauses
        original_sleep = time.sleep
        sleep_calls = []
        time.sleep = lambda duration: sleep_calls.append(duration)
        
        try:
            controller.process_all_queued()
            
            # Should have pauses between segments (not after last one)
            assert len(sleep_calls) >= 1  # At least one pause between segments
            
            # Pauses should be reasonable duration
            for duration in sleep_calls:
                assert 0.1 <= duration <= 2.0
                
        finally:
            time.sleep = original_sleep
    
    def test_skip_content_types(self, controller):
        """Test skipping certain content types"""
        # Configure to skip thinking content
        controller.set_skip_content_types([ContentType.THINKING])
        
        segments = [
            ContentSegment(ContentType.THINKING, "thinking", 0.9),
            ContentSegment(ContentType.ANSWER, "answer", 0.9)
        ]
        
        parsed_content = ParsedContent(segments=segments)
        jobs = controller.queue_content(parsed_content)
        
        # Should only queue the answer, not the thinking
        assert len(jobs) == 1
        assert jobs[0].segment.content_type == ContentType.ANSWER
    
    def test_concurrent_processing_safety(self, controller):
        """Test that controller handles concurrent access safely"""
        # This test ensures thread safety for future concurrent processing
        assert hasattr(controller, '_processing_lock')
        
        # Should not allow multiple simultaneous processing
        controller.is_processing = True
        
        segments = [ContentSegment(ContentType.ANSWER, "test", 0.9)]
        parsed_content = ParsedContent(segments=segments)
        
        # Should raise exception or return early when already processing
        result = controller.process_all_queued()
        
        # Depending on implementation, might return empty results or raise exception
        assert result is not None  # Should handle gracefully
    
    def test_get_processing_status(self, controller):
        """Test getting current processing status"""
        status = controller.get_status()
        
        assert "queue_size" in status
        assert "is_processing" in status
        assert "modulations" in status
        assert "skipped_types" in status
        
        assert isinstance(status["queue_size"], int)
        assert isinstance(status["is_processing"], bool)
    
    def test_clear_queue(self, controller):
        """Test clearing the job queue"""
        segments = [
            ContentSegment(ContentType.ANSWER, "test1", 0.9),
            ContentSegment(ContentType.THINKING, "test2", 0.9)
        ]
        
        parsed_content = ParsedContent(segments=segments)
        controller.queue_content(parsed_content)
        
        # Queue should have jobs
        assert controller.job_queue.qsize() == 2
        
        # Clear the queue
        controller.clear_queue()
        
        # Queue should be empty
        assert controller.job_queue.empty()

class TestModulationApplication:
    """Test voice modulation application"""
    
    @pytest.fixture
    def controller_with_mocked_effects(self):
        """Create controller with mocked effects system"""
        mock_voice_engine = Mock()
        controller = IntelligentTTSController(voice_engine=mock_voice_engine)
        
        # Mock the effects application
        controller._current_effects_pipeline = Mock()
        controller._apply_effects_to_pipeline = Mock()
        
        return controller
    
    def test_apply_thinking_modulation(self, controller_with_mocked_effects):
        """Test applying thinking-specific modulation"""
        controller = controller_with_mocked_effects
        
        # Apply thinking modulation
        controller._apply_modulation(ContentType.THINKING)
        
        # Should store current modulation and content type for effects
        assert hasattr(controller, '_current_modulation')
        assert hasattr(controller, '_current_content_type')
        
        # Should be thinking modulation and content type
        assert controller._current_modulation == controller.modulations[ContentType.THINKING]
        assert controller._current_content_type == ContentType.THINKING
    
    def test_modulation_parameter_bounds(self, controller_with_mocked_effects):
        """Test that modulation parameters are within safe bounds"""
        controller = controller_with_mocked_effects
        
        for content_type in ContentType:
            modulation = controller.modulations[content_type]
            
            # Volume should be reasonable
            assert 0.1 <= modulation.volume_multiplier <= 2.0
            
            # Speed should be reasonable  
            assert 0.5 <= modulation.speed_multiplier <= 2.0
            
            # Pitch should be subtle
            assert -0.5 <= modulation.pitch_adjustment <= 0.5

if __name__ == "__main__":
    pytest.main([__file__, "-v", "--tb=short"])