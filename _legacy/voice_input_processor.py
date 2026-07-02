#!/usr/bin/env python3
"""
Voice Input Processor for M1K3
Unified text processing pipeline supporting multiple input formats:
- Plain text
- Markdown with *narration*
- SSML-style markup  
- Mixed content
"""

import re
import logging
from typing import List, Dict, Any, Optional, Tuple
from dataclasses import dataclass
from enum import Enum

from src.utils.model_output_parser import ContentType, ContentSegment, ParsedContent, ModelOutputParser
from voice_text_preprocessor import VoiceTextPreprocessor
from ssml_converter import SSMLConverter, convert_ssml_to_text

# Configure logging
logger = logging.getLogger('voice_input_processor')
logger.setLevel(logging.INFO)

class InputFormat(Enum):
    """Supported input formats"""
    PLAIN = "plain"
    MARKDOWN = "markdown" 
    SSML = "ssml"
    MIXED = "mixed"
    AUTO_DETECT = "auto_detect"

class NarrationMode(Enum):
    """How to handle narration content"""
    SKIP = "skip"           # Don't synthesize narration at all
    PAUSE = "pause"         # Convert narration to pauses
    WHISPER = "whisper"     # Synthesize narration with different voice
    REMOVE = "remove"       # Remove narration entirely
    PRESERVE = "preserve"   # Keep narration as regular text

@dataclass
class ProcessingConfig:
    """Configuration for voice input processing"""
    input_format: InputFormat = InputFormat.AUTO_DETECT
    narration_mode: NarrationMode = NarrationMode.SKIP
    preserve_ssml_timing: bool = True
    max_text_length: int = 200
    enable_content_analysis: bool = True
    log_processing_steps: bool = True

@dataclass
class ProcessedVoiceInput:
    """Result of processing voice input"""
    segments: List[ContentSegment]
    original_text: str
    processing_log: List[str]
    format_detected: InputFormat
    success: bool
    warnings: List[str]

class VoiceInputProcessor:
    """Unified voice input processing pipeline"""
    
    def __init__(self, config: ProcessingConfig = None):
        self.config = config or ProcessingConfig()
        self.content_parser = ModelOutputParser()
        self.text_preprocessor = VoiceTextPreprocessor()
        self.ssml_converter = SSMLConverter()
        
        logger.info(f"VoiceInputProcessor initialized with config: {self.config}")
    
    def process(self, text: str, config_override: ProcessingConfig = None) -> ProcessedVoiceInput:
        """
        Process voice input text according to configuration
        
        Args:
            text: Input text to process
            config_override: Optional config override for this processing
            
        Returns:
            ProcessedVoiceInput with segments ready for synthesis
        """
        config = config_override or self.config
        processing_log = []
        warnings = []
        
        if config.log_processing_steps:
            logger.info(f"Processing voice input: '{text[:50]}...'")
            processing_log.append(f"Starting processing of {len(text)} characters")
        
        try:
            # Step 1: Detect input format
            detected_format = self._detect_format(text, config.input_format)
            processing_log.append(f"Format detected: {detected_format.value}")
            
            # Step 2: Parse content structure BEFORE SSML processing to preserve narration
            segments = []
            if config.enable_content_analysis:
                # Parse original text to identify content types first
                parsed_content = self.content_parser.parse(text)
                
                # Step 3: Process each segment individually for SSML and preprocessing
                for segment in parsed_content.segments:
                    # Apply SSML conversion to this segment if needed
                    segment_text = segment.text
                    ssml_metadata = {}
                    
                    if detected_format in [InputFormat.SSML, InputFormat.MIXED] and '<' in segment_text:
                        ssml_result = self.ssml_converter.convert(segment_text, config.preserve_ssml_timing)
                        segment_text = ssml_result.converted_text
                        ssml_metadata = {
                            'timing_adjustments': ssml_result.timing_adjustments,
                            'voice_effects': ssml_result.voice_effects
                        }
                        warnings.extend(ssml_result.warnings)
                    
                    # Create updated segment with processed text
                    updated_segment = ContentSegment(
                        content_type=segment.content_type,
                        text=segment_text,
                        confidence=segment.confidence,
                        start_pos=segment.start_pos,
                        end_pos=segment.end_pos
                    )
                    
                    # Process segment according to its type and narration mode
                    processed_segment = self._process_segment(updated_segment, config, ssml_metadata)
                    segments.append(processed_segment)
                
                    
                processing_log.append(f"Content analysis: {len(segments)} segments identified")
            else:
                # Simple processing - treat as single answer segment
                # Apply SSML conversion first if needed
                processed_text = text
                if detected_format in [InputFormat.SSML, InputFormat.MIXED]:
                    ssml_result = self.ssml_converter.convert(text, config.preserve_ssml_timing)
                    processed_text = ssml_result.converted_text
                    warnings.extend(ssml_result.warnings)
                    
                cleaned_text = self.text_preprocessor.preprocess_for_voice(
                    processed_text,
                    max_length=config.max_text_length,
                    narration_mode=config.narration_mode.value
                )
                
                segments.append(ContentSegment(
                    content_type=ContentType.ANSWER,
                    text=cleaned_text,
                    original_markers=text if text != cleaned_text else None,
                    synthesis_mode="NORMAL"
                ))
                processing_log.append("Simple processing: single segment created")
            
            result = ProcessedVoiceInput(
                segments=segments,
                original_text=text,
                processing_log=processing_log,
                format_detected=detected_format,
                success=True,
                warnings=warnings
            )
            
            if config.log_processing_steps:
                logger.info(f"Processing completed: {len(segments)} segments ready for synthesis")
            
            return result
            
        except Exception as e:
            logger.error(f"Voice input processing failed: {e}")
            return ProcessedVoiceInput(
                segments=[],
                original_text=text,
                processing_log=processing_log + [f"Error: {e}"],
                format_detected=InputFormat.PLAIN,
                success=False,
                warnings=warnings + [str(e)]
            )
    
    def _detect_format(self, text: str, format_hint: InputFormat) -> InputFormat:
        """Detect the input format of the text"""
        if format_hint != InputFormat.AUTO_DETECT:
            return format_hint
            
        # Auto-detection logic
        has_ssml = bool(re.search(r'<\w+[^>]*/?>', text))
        has_narration = bool(re.search(r'\*.*?\*', text))
        
        if has_ssml and has_narration:
            return InputFormat.MIXED
        elif has_ssml:
            return InputFormat.SSML
        elif has_narration:
            return InputFormat.MARKDOWN
        else:
            return InputFormat.PLAIN
    
    def _process_segment(self, segment: ContentSegment, config: ProcessingConfig, ssml_metadata: Dict) -> ContentSegment:
        """Process an individual content segment"""
        
        # Handle narration segments specially
        if segment.content_type == ContentType.NARRATION:
            return self._process_narration_segment(segment, config)
        
        # For other content types, apply standard processing
        processed_text = self.text_preprocessor.preprocess_for_voice(
            segment.text,
            max_length=config.max_text_length,
            narration_mode=config.narration_mode.value
        )
        
        # Create processed segment
        processed_segment = ContentSegment(
            content_type=segment.content_type,
            text=processed_text,
            confidence=segment.confidence,
            start_pos=segment.start_pos,
            end_pos=segment.end_pos,
            synthesis_mode="NORMAL",
            original_markers=segment.text if segment.text != processed_text else None
        )
        
        # Apply any SSML metadata
        if ssml_metadata.get('voice_effects'):
            # Could store voice effect info in the segment
            pass
            
        return processed_segment
    
    def _process_narration_segment(self, segment: ContentSegment, config: ProcessingConfig) -> ContentSegment:
        """Process narration segments based on narration mode"""
        
        if config.narration_mode == NarrationMode.SKIP:
            # Mark segment to be skipped during synthesis
            return ContentSegment(
                content_type=segment.content_type,
                text=segment.text,
                confidence=segment.confidence,
                synthesis_mode="SKIP",
                original_markers=segment.text
            )
            
        elif config.narration_mode == NarrationMode.PAUSE:
            # Convert narration to pause
            pause_duration = min(len(segment.text) * 10, 1000)  # Rough estimate
            return ContentSegment(
                content_type=segment.content_type,
                text="",  # Empty text
                confidence=segment.confidence,
                synthesis_mode="PAUSE",
                original_markers=segment.text
            )
            
        elif config.narration_mode == NarrationMode.WHISPER:
            # Mark for whispered synthesis
            processed_text = self.text_preprocessor.preprocess_for_voice(
                segment.text,
                narration_mode="preserve"  # Keep the text but mark for special synthesis
            )
            return ContentSegment(
                content_type=segment.content_type,
                text=processed_text,
                confidence=segment.confidence,
                synthesis_mode="WHISPER",
                original_markers=segment.text
            )
            
        elif config.narration_mode == NarrationMode.REMOVE:
            # Remove narration entirely - return empty segment
            return ContentSegment(
                content_type=segment.content_type,
                text="",
                confidence=segment.confidence,
                synthesis_mode="SKIP",
                original_markers=segment.text
            )
            
        else:  # PRESERVE
            # Keep as normal text
            processed_text = self.text_preprocessor.preprocess_for_voice(
                segment.text,
                narration_mode="preserve"
            )
            return ContentSegment(
                content_type=segment.content_type,
                text=processed_text,
                confidence=segment.confidence,
                synthesis_mode="NORMAL",
                original_markers=segment.text if segment.text != processed_text else None
            )

# Convenience functions
def process_voice_input(text: str, 
                       narration_mode: str = "skip",
                       enable_ssml: bool = True,
                       max_length: int = 200) -> ProcessedVoiceInput:
    """Convenience function for processing voice input"""
    
    config = ProcessingConfig(
        narration_mode=NarrationMode(narration_mode),
        preserve_ssml_timing=enable_ssml,
        max_text_length=max_length
    )
    
    processor = VoiceInputProcessor(config)
    return processor.process(text)

if __name__ == "__main__":
    # Test the voice input processor
    processor = VoiceInputProcessor()
    
    test_cases = [
        "*The assistant starts speaking* Hello there! How can I help you today?",
        "Welcome! <break time='500ms'/> This is a <emphasis>great</emphasis> demo.",
        "Complex example: *pauses* <prosody rate='slow'>Speaking slowly</prosody> now.",
        "Regular text without any special formatting.",
        "<speak>Hello <break time='300ms'/> world!</speak> *The user nods*",
    ]
    
    print("Testing Voice Input Processor:")
    print("=" * 70)
    
    for i, test_case in enumerate(test_cases, 1):
        print(f"\nTest {i}: {test_case}")
        result = processor.process(test_case)
        
        print(f"Format: {result.format_detected.value}")
        print(f"Success: {result.success}")
        print(f"Segments: {len(result.segments)}")
        
        for j, segment in enumerate(result.segments):
            print(f"  {j+1}. {segment.content_type.value}: '{segment.text}' (mode: {segment.synthesis_mode})")
        
        if result.warnings:
            print(f"Warnings: {result.warnings}")
            
        if result.processing_log:
            print(f"Log: {' | '.join(result.processing_log)}")