#!/usr/bin/env python3
"""
Model Output Parser for M1K3
Parses AI model output to identify different content types for voice modulation
"""

import re
from enum import Enum
from dataclasses import dataclass
from typing import List, Dict, Any, Optional

class ContentType(Enum):
    """Types of content that can be identified in model output"""
    THINKING = "thinking"
    NARRATION = "narration"  
    ANSWER = "answer"
    CLARIFICATION = "clarification"
    
    @property
    def priority(self) -> int:
        """Priority for voice synthesis ordering (lower = higher priority)"""
        priority_map = {
            ContentType.THINKING: 1,        # Thinking comes first
            ContentType.NARRATION: 2,       # Then narration/context
            ContentType.ANSWER: 3,          # Then the main answer
            ContentType.CLARIFICATION: 4    # Finally any clarifying questions
        }
        return priority_map[self]

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
class ContentSegment:
    """A segment of content with identified type and confidence"""
    content_type: ContentType
    text: str
    confidence: float = 1.0
    start_pos: int = 0
    end_pos: int = 0
    synthesis_mode: str = "NORMAL"  # NORMAL, SKIP, PAUSE, WHISPER
    original_markers: Optional[str] = None  # Preserve original formatting markers
    
    def __post_init__(self):
        """Clean up text after initialization"""
        # Only strip leading/trailing newlines but preserve other formatting
        self.text = self.text.strip('\n').rstrip() if self.text else ""

@dataclass
class ParsedContent:
    """Complete parsed content with all segments"""
    segments: List[ContentSegment]
    
    @property
    def has_content(self) -> bool:
        """Check if there is any actual content"""
        return len(self.segments) > 0 and any(seg.text.strip() for seg in self.segments)
    
    @property
    def needs_clarification(self) -> bool:
        """Check if any segment is a clarification request"""
        return any(seg.content_type == ContentType.CLARIFICATION for seg in self.segments)
    
    def get_segments_by_type(self, content_type: ContentType) -> List[ContentSegment]:
        """Get all segments of a specific type"""
        return [seg for seg in self.segments if seg.content_type == content_type]

class ModelOutputParser:
    """Parser for identifying different types of content in model output"""
    
    def __init__(self):
        # Patterns for identifying different content types
        self.thinking_patterns = [
            r'<thinking>(.*?)</thinking>',
            r'<think>(.*?)</think>',
            r'\*thinking\*(.*?)\*/thinking\*',
        ]
        
        self.narration_patterns = [
            r'\*(.*?)\*',  # *narration*
            r'\[.*?\]',    # [context]
        ]
        
        self.clarification_patterns = [
            r'\b(?:could|can|would|will)\s+you\b.*?\?',  # "Could you...?"
            r'\b(?:what|how|when|where|why|which)\b.*?\?',  # Question words
            r'\?[^.]*$',  # Ends with question
            r'\b(?:please|help|clarify|explain|tell)\b.*?\?',  # Request patterns
        ]
        
        # Indicators of different content types
        self.thinking_indicators = [
            'let me think', 'i need to consider', 'let me consider',
            'thinking about', 'i should think', 'let me analyze'
        ]
        
        self.clarification_indicators = [
            'could you', 'can you', 'would you', 'please clarify',
            'help me understand', 'what do you mean', 'more specific'
        ]
    
    def parse(self, text: str) -> ParsedContent:
        """
        Parse model output into content segments
        
        Args:
            text: Raw text from AI model
            
        Returns:
            ParsedContent with identified segments
        """
        if not text or not text.strip():
            return ParsedContent(segments=[])
        
        segments = []
        
        # NEW APPROACH: Parse all segments from original text, then organize by position
        # This preserves the original positions and prevents segments from being lost
        
        # Extract all segment types from original text
        thinking_segments = self._extract_thinking_blocks(text)
        narration_segments = self._extract_narration_from_original(text)
        clarification_segments = self._extract_clarifications_from_original(text)
        
        # Combine all special segments
        all_special_segments = thinking_segments + narration_segments + clarification_segments
        
        # Extract answer content from parts NOT covered by special segments
        answer_segments = self._extract_answers_from_remaining(text, all_special_segments)
        
        # Combine all segments
        segments = all_special_segments + answer_segments
        
        # Sort segments by their original position in text
        segments.sort(key=lambda s: s.start_pos)
        
        return ParsedContent(segments=segments)
    
    def _extract_thinking_blocks(self, text: str) -> List[ContentSegment]:
        """Extract explicit thinking blocks"""
        segments = []
        
        for pattern in self.thinking_patterns:
            matches = list(re.finditer(pattern, text, re.DOTALL | re.IGNORECASE))
            
            for match in matches:
                thinking_text = match.group(1)
                # Preserve internal formatting but clean up leading/trailing whitespace
                thinking_text = thinking_text.strip('\n').rstrip()
                if thinking_text:
                    segments.append(ContentSegment(
                        content_type=ContentType.THINKING,
                        text=thinking_text,
                        confidence=0.95,  # High confidence for explicit blocks
                        start_pos=match.start(),
                        end_pos=match.end()
                    ))
        
        return segments
    
    def _extract_narration(self, text: str) -> tuple[List[ContentSegment], str]:
        """Extract narration elements and return remaining text"""
        segments = []
        remaining_text = text
        
        # Find asterisk-style narration
        narration_matches = list(re.finditer(r'\*(.*?)\*', text, re.DOTALL))
        
        for match in narration_matches:
            narration_content = match.group(1).strip()
            
            # Skip if it looks like emphasis rather than narration
            if len(narration_content.split()) > 5 or self._looks_like_narration(narration_content):
                segments.append(ContentSegment(
                    content_type=ContentType.NARRATION,
                    text=narration_content,
                    confidence=0.8,
                    start_pos=match.start(),
                    end_pos=match.end()
                ))
        
        # Remove narration from remaining text
        remaining_text = re.sub(r'\*.*?\*', '', remaining_text, flags=re.DOTALL)
        
        return segments, remaining_text.strip()
    
    def _extract_clarifications(self, text: str) -> tuple[List[ContentSegment], str]:
        """Extract clarification requests and return remaining text"""
        segments = []
        sentences = self._split_into_sentences(text)
        remaining_sentences = []
        
        for sentence in sentences:
            is_clarification = False
            confidence = 0.0
            
            # Check for explicit question marks
            if '?' in sentence:
                confidence += 0.4
                
                # Check for clarification patterns
                for pattern in self.clarification_patterns:
                    if re.search(pattern, sentence, re.IGNORECASE):
                        confidence += 0.3
                        break
                
                # Check for clarification indicators
                sentence_lower = sentence.lower()
                for indicator in self.clarification_indicators:
                    if indicator in sentence_lower:
                        confidence += 0.3
                        break
                
                # If confidence is high enough, mark as clarification
                if confidence >= 0.6:
                    is_clarification = True
            
            if is_clarification:
                segments.append(ContentSegment(
                    content_type=ContentType.CLARIFICATION,
                    text=sentence.strip(),
                    confidence=confidence
                ))
            else:
                remaining_sentences.append(sentence)
        
        remaining_text = ' '.join(remaining_sentences)
        return segments, remaining_text.strip()
    
    def _extract_narration_from_original(self, text: str) -> List[ContentSegment]:
        """Extract narration segments preserving original positions"""
        segments = []
        
        # Find asterisk-style narration
        narration_pattern = r'\*(.*?)\*'
        
        for match in re.finditer(narration_pattern, text, re.DOTALL):
            narration_content = match.group(1).strip()
            
            # Accept if: more than 1 word OR looks like narration OR contains action words
            if (len(narration_content.split()) > 1 or 
                self._looks_like_narration(narration_content) or
                self._contains_action_words(narration_content)):
                segments.append(ContentSegment(
                    content_type=ContentType.NARRATION,
                    text=narration_content,
                    confidence=0.8,
                    start_pos=match.start(),
                    end_pos=match.end()
                ))
        
        return segments
    
    def _extract_clarifications_from_original(self, text: str) -> List[ContentSegment]:
        """Extract clarification requests preserving original positions"""
        segments = []
        
        # Split into sentences and analyze each
        sentences = self._split_into_sentences(text)
        current_pos = 0
        
        for sentence in sentences:
            # Find this sentence's position in original text
            sentence_start = text.find(sentence, current_pos)
            if sentence_start == -1:
                continue
            sentence_end = sentence_start + len(sentence)
            current_pos = sentence_end
            
            is_clarification = False
            confidence = 0.0
            
            # Check for explicit question marks
            if '?' in sentence:
                confidence += 0.4
                
                # Check for clarification patterns
                for pattern in self.clarification_patterns:
                    if re.search(pattern, sentence, re.IGNORECASE):
                        confidence += 0.3
                        break
                
                # Check for clarification indicators
                sentence_lower = sentence.lower()
                for indicator in self.clarification_indicators:
                    if indicator in sentence_lower:
                        confidence += 0.3
                        break
                
                # If confidence is high enough, mark as clarification
                if confidence >= 0.6:
                    is_clarification = True
            
            if is_clarification:
                segments.append(ContentSegment(
                    content_type=ContentType.CLARIFICATION,
                    text=sentence.strip(),
                    confidence=confidence,
                    start_pos=sentence_start,
                    end_pos=sentence_end
                ))
        
        return segments
    
    def _extract_answers_from_remaining(self, text: str, special_segments: List[ContentSegment]) -> List[ContentSegment]:
        """Extract answer content from parts not covered by special segments"""
        segments = []
        
        # Create a list of ranges covered by special segments
        covered_ranges = [(seg.start_pos, seg.end_pos) for seg in special_segments]
        covered_ranges.sort()
        
        # Find uncovered text ranges
        uncovered_ranges = []
        last_end = 0
        
        for start, end in covered_ranges:
            if start > last_end:
                uncovered_ranges.append((last_end, start))
            last_end = max(last_end, end)
        
        # Add final range if text continues after last special segment
        if last_end < len(text):
            uncovered_ranges.append((last_end, len(text)))
        
        # Extract content from uncovered ranges
        for start_pos, end_pos in uncovered_ranges:
            range_text = text[start_pos:end_pos].strip()
            
            if range_text:
                # Clean up the text (remove narration markers that might have been left)
                cleaned_text = re.sub(r'\*+', '', range_text)  # Remove stray asterisks
                # DON'T remove SSML tags here - they need to be processed by the SSML converter
                cleaned_text = cleaned_text.strip()
                
                if cleaned_text:
                    confidence = self._calculate_answer_confidence(cleaned_text)
                    
                    segments.append(ContentSegment(
                        content_type=ContentType.ANSWER,
                        text=cleaned_text,
                        confidence=confidence,
                        start_pos=start_pos,
                        end_pos=end_pos
                    ))
        
        return segments
    
    def _extract_answers(self, text: str) -> List[ContentSegment]:
        """Extract answer content from remaining text"""
        segments = []
        
        if not text.strip():
            return segments
        
        # Split into paragraphs, but be smarter about it
        paragraphs = []
        
        # First try splitting on double newlines  
        double_newline_parts = [p.strip() for p in text.split('\n\n') if p.strip()]
        
        if len(double_newline_parts) > 1:
            paragraphs = double_newline_parts
        else:
            # If no double newlines, try splitting on single newlines with content
            single_newline_parts = [p.strip() for p in text.split('\n') if p.strip()]
            if len(single_newline_parts) > 1:
                # Group related lines together
                current_paragraph = ""
                for part in single_newline_parts:
                    if current_paragraph and (part.startswith('```') or current_paragraph.endswith('```')):
                        # Handle code blocks specially
                        current_paragraph += '\n' + part
                        if part.endswith('```'):
                            paragraphs.append(current_paragraph)
                            current_paragraph = ""
                    elif current_paragraph and len(part) < 50 and not part.endswith('.'):
                        # Short lines likely belong together
                        current_paragraph += ' ' + part
                    else:
                        if current_paragraph:
                            paragraphs.append(current_paragraph)
                        current_paragraph = part
                
                if current_paragraph:
                    paragraphs.append(current_paragraph)
            else:
                paragraphs = [text.strip()]
        
        for paragraph in paragraphs:
            confidence = self._calculate_answer_confidence(paragraph)
            
            segments.append(ContentSegment(
                content_type=ContentType.ANSWER,
                text=paragraph.strip(),
                confidence=confidence
            ))
        
        return segments
    
    def _looks_like_narration(self, text: str) -> bool:
        """Check if text looks like narration rather than emphasis"""
        narration_indicators = [
            'the user', 'the assistant', 'pauses', 'considers', 'thinks',
            'looks', 'gestures', 'nods', 'smiles', 'sighs'
        ]
        
        text_lower = text.lower()
        return any(indicator in text_lower for indicator in narration_indicators)
    
    def _contains_action_words(self, text: str) -> bool:
        """Check if text contains action words typical of narration"""
        action_words = [
            'starts', 'begins', 'speaks', 'says', 'continues', 'stops',
            'waves', 'points', 'gestures', 'moves', 'turns', 'looks',
            'pauses', 'waits', 'smiles', 'laughs', 'nods', 'shakes',
            'enters', 'exits', 'approaches', 'retreats', 'sits', 'stands',
            'opens', 'closes', 'shows', 'demonstrates', 'explains'
        ]
        
        text_lower = text.lower()
        text_words = text_lower.split()
        return any(action_word in text_words for action_word in action_words)
    
    def _split_into_sentences(self, text: str) -> List[str]:
        """Split text into sentences"""
        # Simple sentence splitting - can be enhanced later
        sentences = re.split(r'[.!?]+', text)
        return [s.strip() for s in sentences if s.strip()]
    
    def _calculate_answer_confidence(self, text: str) -> float:
        """Calculate confidence that text is answer content"""
        confidence = 0.7  # Base confidence for answer content
        
        # Check for answer indicators
        answer_indicators = [
            'based on', 'according to', 'the answer is', 'i recommend',
            'the solution', 'here\'s how', 'you should', 'you can'
        ]
        
        text_lower = text.lower()
        for indicator in answer_indicators:
            if indicator in text_lower:
                confidence += 0.1
                break
        
        # Longer, well-structured text is more likely to be answers
        if len(text.split()) > 10:
            confidence += 0.1
        
        return min(confidence, 1.0)

def parse_model_output(text: str) -> ParsedContent:
    """Convenience function for parsing model output"""
    parser = ModelOutputParser()
    return parser.parse(text)

if __name__ == "__main__":
    # Test the parser with sample content
    parser = ModelOutputParser()
    
    test_cases = [
        """<thinking>
This is a complex question. Let me think through this carefully.
</thinking>

The answer is that we should implement a modular approach. *The user nods in understanding.* 

But could you clarify what specific aspect interests you most?""",
        
        "*The assistant pauses thoughtfully.* Well, there are several options to consider.",
        
        "Based on my analysis, I recommend the following approach. What do you think about this solution?",
        
        "Let me think about this problem step by step before providing my recommendation."
    ]
    
    for i, test_case in enumerate(test_cases, 1):
        print(f"\nTest case {i}:")
        print(f"Input: {repr(test_case)}")
        
        result = parser.parse(test_case)
        
        print(f"Segments found: {len(result.segments)}")
        for j, segment in enumerate(result.segments):
            print(f"  {j+1}. {segment.content_type.value}: {repr(segment.text)} (confidence: {segment.confidence:.2f})")
        
        print(f"Needs clarification: {result.needs_clarification}")
        print(f"Has content: {result.has_content}")