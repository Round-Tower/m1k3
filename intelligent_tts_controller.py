#!/usr/bin/env python3
"""
Intelligent TTS Controller for M1K3
Orchestrates content-specific voice synthesis with modulation
"""

import time
import threading
from datetime import datetime
from enum import Enum
from dataclasses import dataclass, field
from typing import Dict, Any, List, Optional, Set
from queue import PriorityQueue

try:
    from model_output_parser import ContentType, ContentSegment, ParsedContent
    PARSER_AVAILABLE = True
except ImportError:
    PARSER_AVAILABLE = False

try:
    from content_specific_effects import create_content_effects_manager
    EFFECTS_AVAILABLE = True
except ImportError:
    EFFECTS_AVAILABLE = False

class TTSStatus(Enum):
    """Status of TTS job processing"""
    PENDING = "pending"
    IN_PROGRESS = "in_progress"
    COMPLETED = "completed"
    FAILED = "failed"

@dataclass
class ContentTypeModulation:
    """Voice modulation settings for a content type"""
    volume_multiplier: float = 1.0
    speed_multiplier: float = 1.0
    pitch_adjustment: float = 0.0
    reverb_amount: float = 0.0
    warmth_factor: float = 0.0
    
    def __post_init__(self):
        """Validate modulation parameters"""
        if not (0.1 <= self.volume_multiplier <= 2.0):
            raise ValueError(f"Volume multiplier must be between 0.1 and 2.0, got {self.volume_multiplier}")
        
        if not (0.5 <= self.speed_multiplier <= 2.0):
            raise ValueError(f"Speed multiplier must be between 0.5 and 2.0, got {self.speed_multiplier}")
        
        if not (-0.5 <= self.pitch_adjustment <= 0.5):
            raise ValueError(f"Pitch adjustment must be between -0.5 and 0.5, got {self.pitch_adjustment}")

@dataclass
class TTSJob:
    """A TTS job for processing"""
    segment: ContentSegment
    priority: int
    status: TTSStatus = TTSStatus.PENDING
    created_at: datetime = field(default_factory=datetime.now)
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    error_message: Optional[str] = None
    
    def __lt__(self, other):
        """Comparison for priority queue (lower priority number = higher precedence)"""
        if not isinstance(other, TTSJob):
            return NotImplemented
        return self.priority < other.priority

@dataclass
class TTSProcessingResult:
    """Result of processing TTS job(s)"""
    success: bool
    job: TTSJob
    processing_time: Optional[float] = None
    error_message: Optional[str] = None

class IntelligentTTSController:
    """Intelligent controller for content-type specific TTS"""
    
    def __init__(self, voice_engine=None):
        self.voice_engine = voice_engine
        self.job_queue = PriorityQueue()
        self.is_processing = False
        self.skip_content_types: Set[ContentType] = set()
        self._processing_lock = threading.Lock()
        
        # Initialize default modulations for each content type
        self.modulations = self._create_default_modulations()
        
        # Initialize content effects manager
        self.effects_manager = None
        if EFFECTS_AVAILABLE:
            try:
                self.effects_manager = create_content_effects_manager()
            except Exception:
                self.effects_manager = None
        
        # Current effects pipeline state (for modulation application)
        self._current_effects_pipeline = None
    
    def _create_default_modulations(self) -> Dict[ContentType, ContentTypeModulation]:
        """Create default voice modulations for each content type"""
        return {
            ContentType.THINKING: ContentTypeModulation(
                volume_multiplier=0.8,
                speed_multiplier=0.85,
                pitch_adjustment=-0.1,
                reverb_amount=0.2,
                warmth_factor=0.0
            ),
            ContentType.NARRATION: ContentTypeModulation(
                volume_multiplier=1.0,
                speed_multiplier=1.1,
                pitch_adjustment=0.05,
                reverb_amount=0.0,
                warmth_factor=0.3
            ),
            ContentType.ANSWER: ContentTypeModulation(
                volume_multiplier=1.0,
                speed_multiplier=1.0,
                pitch_adjustment=0.0,
                reverb_amount=0.0,
                warmth_factor=0.0
            ),
            ContentType.CLARIFICATION: ContentTypeModulation(
                volume_multiplier=1.0,
                speed_multiplier=0.95,
                pitch_adjustment=0.15,
                reverb_amount=0.0,
                warmth_factor=0.1
            )
        }
    
    def set_modulation(self, content_type: ContentType, modulation: ContentTypeModulation):
        """Set voice modulation for a specific content type"""
        self.modulations[content_type] = modulation
    
    def set_skip_content_types(self, content_types: List[ContentType]):
        """Set which content types to skip during synthesis"""
        self.skip_content_types = set(content_types)
    
    def queue_content(self, parsed_content: ParsedContent) -> List[TTSJob]:
        """
        Queue parsed content for TTS processing
        
        Args:
            parsed_content: Parsed content with segments
            
        Returns:
            List of queued TTS jobs
        """
        queued_jobs = []
        
        for segment in parsed_content.segments:
            # Skip if content type is in skip list
            if segment.content_type in self.skip_content_types:
                continue
            
            # Determine priority based on content type
            priority = segment.content_type.priority
            
            # Create TTS job
            job = TTSJob(segment=segment, priority=priority)
            
            # Add to queue
            self.job_queue.put(job)
            queued_jobs.append(job)
        
        return queued_jobs
    
    def process_job(self, job: TTSJob) -> bool:
        """
        Process a single TTS job
        
        Args:
            job: TTS job to process
            
        Returns:
            True if successful, False if failed
        """
        job.status = TTSStatus.IN_PROGRESS
        job.started_at = datetime.now()
        
        try:
            # Apply content-type specific modulation
            self._apply_modulation(job.segment.content_type)
            
            # Synthesize and play the audio
            success = self._synthesize_segment(job.segment)
            
            if success:
                job.status = TTSStatus.COMPLETED
                job.completed_at = datetime.now()
                return True
            else:
                job.status = TTSStatus.FAILED
                job.error_message = "Voice synthesis failed"
                return False
                
        except Exception as e:
            job.status = TTSStatus.FAILED
            job.error_message = str(e)
            return False
    
    def process_all_queued(self) -> List[TTSProcessingResult]:
        """
        Process all queued TTS jobs sequentially
        
        Returns:
            List of processing results
        """
        with self._processing_lock:
            if self.is_processing:
                # Already processing, return empty results
                return []
            
            self.is_processing = True
        
        try:
            results = []
            
            while not self.job_queue.empty():
                job = self.job_queue.get()
                
                start_time = time.time()
                success = self.process_job(job)
                processing_time = time.time() - start_time
                
                # Create result
                result = TTSProcessingResult(
                    success=success,
                    job=job,
                    processing_time=processing_time,
                    error_message=job.error_message if not success else None
                )
                results.append(result)
                
                # Add pause between segments for natural flow
                if not self.job_queue.empty():  # Don't pause after last segment
                    time.sleep(self._calculate_inter_segment_pause(job.segment.content_type))
            
            return results
            
        finally:
            self.is_processing = False
    
    def _apply_modulation(self, content_type: ContentType):
        """Apply content-type specific voice modulation"""
        modulation = self.modulations[content_type]
        
        # Store current modulation for effects application during synthesis
        self._current_modulation = modulation
        self._current_content_type = content_type
    
    def _apply_effects_to_pipeline(self, modulation: ContentTypeModulation):
        """Apply effects to the voice synthesis pipeline"""
        # This is a placeholder for the actual effects implementation
        # TODO: Implement actual audio effects when we create the effects system
        pass
    
    def _synthesize_segment(self, segment: ContentSegment) -> bool:
        """Synthesize audio for a content segment with content-specific effects"""
        if not self.voice_engine:
            # No voice engine available
            return False
        
        try:
            # For now, use standard synthesis (effects integration comes next)
            # TODO: Apply content-specific effects to audio after synthesis
            success = self.voice_engine.synthesize_and_play(segment.text, background=False)
            return success
        except Exception as e:
            # Log the exception for debugging
            print(f"🐛 Voice synthesis error: {e}")
            return False
    
    def _calculate_inter_segment_pause(self, content_type: ContentType) -> float:
        """Calculate appropriate pause duration between segments"""
        # Different content types may need different pause durations
        pause_durations = {
            ContentType.THINKING: 0.8,      # Longer pause after thinking
            ContentType.NARRATION: 0.6,     # Medium pause after narration
            ContentType.ANSWER: 0.4,        # Short pause after answers
            ContentType.CLARIFICATION: 0.2  # Very short pause before questions
        }
        
        return pause_durations.get(content_type, 0.5)
    
    def clear_queue(self):
        """Clear all queued TTS jobs"""
        with self.job_queue.mutex:
            self.job_queue.queue.clear()
    
    def get_status(self) -> Dict[str, Any]:
        """Get current controller status"""
        return {
            "queue_size": self.job_queue.qsize(),
            "is_processing": self.is_processing,
            "modulations": {ct.value: {
                "volume": mod.volume_multiplier,
                "speed": mod.speed_multiplier,
                "pitch": mod.pitch_adjustment,
                "reverb": mod.reverb_amount,
                "warmth": mod.warmth_factor
            } for ct, mod in self.modulations.items()},
            "skipped_types": [ct.value for ct in self.skip_content_types],
            "voice_engine_available": self.voice_engine is not None,
            "effects_manager_available": self.effects_manager is not None
        }
    
    def process_text_with_parsing(self, text: str, parser=None) -> List[TTSProcessingResult]:
        """
        Convenience method to parse text and process with TTS
        
        Args:
            text: Raw text to parse and synthesize
            parser: Optional parser instance (will create if None)
            
        Returns:
            List of processing results
        """
        if not PARSER_AVAILABLE:
            raise ImportError("Model output parser not available")
        
        if parser is None:
            from model_output_parser import parse_model_output
            parsed_content = parse_model_output(text)
        else:
            parsed_content = parser.parse(text)
        
        # Queue the content and process it
        self.queue_content(parsed_content)
        return self.process_all_queued()

def create_intelligent_tts_controller(voice_engine=None) -> IntelligentTTSController:
    """Factory function to create TTS controller"""
    return IntelligentTTSController(voice_engine=voice_engine)

if __name__ == "__main__":
    # Test the controller
    controller = create_intelligent_tts_controller()
    
    print("🎤 Testing Intelligent TTS Controller")
    print(f"Status: {controller.get_status()}")
    
    # Test with mock content
    if PARSER_AVAILABLE:
        test_text = """<thinking>
        Let me think about this problem.
        </thinking>
        
        The answer is to use a modular approach. *The user nods.*
        
        Could you clarify which aspect interests you?"""
        
        print(f"\n📝 Processing test text...")
        results = controller.process_text_with_parsing(test_text)
        
        print(f"\n📊 Results:")
        for i, result in enumerate(results, 1):
            status = "✅" if result.success else "❌"
            content_type = result.job.segment.content_type.value
            duration = f"{result.processing_time:.2f}s" if result.processing_time else "N/A"
            print(f"  {i}. {status} {content_type} ({duration})")
    
    print(f"\n✅ Controller test complete")