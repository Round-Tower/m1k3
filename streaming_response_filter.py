#!/usr/bin/env python3
"""
M1K3 Streaming Response Filter
Real-time token filtering for clean CLI output during streaming responses
"""

import re
from typing import Generator, Optional, List, Tuple
from dataclasses import dataclass
from enum import Enum


class FilterState(Enum):
    NORMAL = "normal"
    THINKING_DETECTED = "thinking_detected"
    BUFFERING = "buffering"
    SUPPRESSING = "suppressing"


@dataclass
class FilterBuffer:
    """Buffer for managing partial tokens and pattern detection"""
    content: str = ""
    tokens: List[str] = None
    state: FilterState = FilterState.NORMAL
    suppressed_content: str = ""
    
    def __post_init__(self):
        if self.tokens is None:
            self.tokens = []


class StreamingResponseFilter:
    """Filters streaming tokens to remove thinking processes and artifacts"""
    
    def __init__(self):
        # Patterns that indicate start of thinking/meta-commentary  
        self.thinking_start_patterns = [
            r'^:\s+',  # ": " at start (common in our case)
            r'^<think>',
            r'^<thinking>',
            r'^\s*(Okay,\s+so|Let\s+me|The\s+user)',
            r'^\s*(I\s+need\s+to|Looking\s+at\s+this)',
        ]
        
        # Patterns that indicate meta-commentary within text
        self.meta_patterns = [
            r'\bthe\s+user\'?s?\s+message\s+is\b',
            r'\bthe\s+assistant\s+should\b',
            r'\bif\s+the\s+response\s+doesn\'?t?\s+meet\b',
            r'\bviolates?\s+any\s+of\s+the\s+rules\b',
            r'\bwe\s+need\s+to\s+inform\s+them\b',
            r'\bin\s+Chinese.*in\s+English\b',
            r'\btoo\s+long.*let\s+me\s+start\b',
        ]
        
        # Patterns that indicate end of thinking/return to normal content
        self.thinking_end_patterns = [
            r'</think>',
            r'</thinking>',
            r'\.\s+[A-Z][a-z]',  # Sentence end followed by capital letter
            r':\s*[A-Z][a-z]',   # Colon followed by capital letter (answer start)
        ]
        
        # Buffer management
        self.max_buffer_size = 100  # Max tokens to buffer for pattern detection
        self.min_buffer_flush = 20  # Min tokens before checking for flush
    
    def filter_streaming_tokens(self, tokens: Generator[str, None, None]) -> Generator[str, None, None]:
        """Filter streaming tokens in real-time"""
        
        buffer = FilterBuffer()
        
        for token in tokens:
            # Add token to buffer
            buffer.tokens.append(token)
            buffer.content += token
            
            # Process buffer based on current state
            yield from self._process_buffer(buffer)
            
            # Manage buffer size
            if len(buffer.tokens) > self.max_buffer_size:
                # Force flush oldest tokens to prevent memory issues
                yield from self._force_flush_old_tokens(buffer)
        
        # Final flush of any remaining content
        yield from self._final_flush(buffer)
    
    def _process_buffer(self, buffer: FilterBuffer) -> Generator[str, None, None]:
        """Process the current buffer based on filter state"""
        
        if buffer.state == FilterState.NORMAL:
            # Check if we need to start filtering
            if self._should_start_filtering(buffer.content):
                buffer.state = FilterState.THINKING_DETECTED
                # Don't yield tokens yet, start buffering
                return
            elif len(buffer.tokens) >= self.min_buffer_flush:
                # Normal content, flush some tokens
                yield from self._flush_safe_tokens(buffer)
        
        elif buffer.state == FilterState.THINKING_DETECTED:
            # We detected potential thinking, buffer more to confirm
            if len(buffer.tokens) >= self.min_buffer_flush:
                # Check if this is actually thinking content
                if self._is_thinking_content(buffer.content):
                    buffer.state = FilterState.SUPPRESSING
                    buffer.suppressed_content += buffer.content
                    buffer.content = ""
                    buffer.tokens = []
                else:
                    # False alarm, flush everything
                    buffer.state = FilterState.NORMAL
                    yield from self._flush_all_tokens(buffer)
        
        elif buffer.state == FilterState.SUPPRESSING:
            # We're suppressing thinking content
            buffer.suppressed_content += buffer.content
            
            # Check if thinking has ended
            if self._should_stop_filtering(buffer.content):
                # Extract any clean content after thinking
                clean_content = self._extract_clean_content(buffer.content)
                if clean_content:
                    yield clean_content
                
                # Reset state
                buffer.state = FilterState.NORMAL
                buffer.content = ""
                buffer.tokens = []
            else:
                # Continue suppressing
                buffer.content = ""
                buffer.tokens = []
    
    def _should_start_filtering(self, content: str) -> bool:
        """Check if we should start filtering based on current content"""
        for pattern in self.thinking_start_patterns:
            if re.search(pattern, content, re.IGNORECASE):
                return True
        return False
    
    def _is_thinking_content(self, content: str) -> bool:
        """Determine if buffered content is actually thinking/meta-commentary"""
        
        # Check for meta patterns
        for pattern in self.meta_patterns:
            if re.search(pattern, content, re.IGNORECASE):
                return True
        
        # Check for thinking indicators
        thinking_indicators = [
            r'\bokay,?\s+so\s+the\s+user\b',
            r'\bthe\s+user.*asked\b',
            r'\bi\s+need\s+to.*consider\b',
            r'\blet\s+me\s+think\b',
            r'\bfirst.*let\s+me\b',
        ]
        
        for pattern in thinking_indicators:
            if re.search(pattern, content, re.IGNORECASE):
                return True
        
        return False
    
    def _should_stop_filtering(self, content: str) -> bool:
        """Check if we should stop filtering and return to normal content"""
        
        for pattern in self.thinking_end_patterns:
            if re.search(pattern, content, re.IGNORECASE):
                return True
        
        # Also check for clear answer indicators
        answer_indicators = [
            r'\.\s+[A-Z][a-z]+.*[a-z]',  # Proper sentence after period
            r'^[A-Z][a-z]+.*is\s+',      # Definitive statement
            r'^[A-Z][a-z]+.*was\s+',     # Past tense statement
        ]
        
        for pattern in answer_indicators:
            if re.search(pattern, content, re.IGNORECASE):
                return True
        
        return False
    
    def _extract_clean_content(self, content: str) -> str:
        """Extract clean content from mixed thinking/answer content"""
        
        # Try to find where the real answer starts
        lines = content.split('\n')
        clean_lines = []
        
        for line in lines:
            line = line.strip()
            if not line:
                continue
            
            # Skip obvious thinking content
            if any(re.search(pattern, line, re.IGNORECASE) for pattern in self.meta_patterns):
                continue
            
            # Look for answer content
            if (line[0].isupper() and 
                len(line) > 10 and 
                not line.startswith(('Okay', 'Let me', 'I need', 'The user'))):
                clean_lines.append(line)
        
        return '\n'.join(clean_lines)
    
    def _flush_safe_tokens(self, buffer: FilterBuffer) -> Generator[str, None, None]:
        """Flush tokens that are safe to display"""
        
        # Flush half the buffer to maintain responsiveness
        flush_count = len(buffer.tokens) // 2
        for _ in range(flush_count):
            if buffer.tokens:
                token = buffer.tokens.pop(0)
                yield token
                # Update content to reflect flushed tokens
                if token in buffer.content:
                    buffer.content = buffer.content[len(token):]
    
    def _flush_all_tokens(self, buffer: FilterBuffer) -> Generator[str, None, None]:
        """Flush all buffered tokens"""
        
        for token in buffer.tokens:
            yield token
        
        buffer.tokens = []
        buffer.content = ""
    
    def _force_flush_old_tokens(self, buffer: FilterBuffer) -> Generator[str, None, None]:
        """Force flush old tokens to prevent buffer overflow"""
        
        # Keep only the most recent tokens
        keep_count = self.max_buffer_size // 2
        flush_count = len(buffer.tokens) - keep_count
        
        for _ in range(flush_count):
            if buffer.tokens:
                token = buffer.tokens.pop(0)
                if buffer.state == FilterState.NORMAL:
                    yield token
        
        # Rebuild content from remaining tokens
        buffer.content = ''.join(buffer.tokens)
    
    def _final_flush(self, buffer: FilterBuffer) -> Generator[str, None, None]:
        """Final flush of any remaining content"""
        
        if buffer.state == FilterState.NORMAL:
            # Flush any remaining tokens
            yield from self._flush_all_tokens(buffer)
        elif buffer.state == FilterState.SUPPRESSING:
            # Check if there's clean content to extract
            clean_content = self._extract_clean_content(buffer.content)
            if clean_content:
                yield clean_content


class SimpleStreamingFilter:
    """Simplified version for basic colon prefix removal and meta-commentary filtering"""
    
    def filter_tokens(self, tokens: Generator[str, None, None]) -> Generator[str, None, None]:
        """Simple filter that removes colon prefixes and obvious thinking content"""
        
        accumulated = ""
        response_started = False
        in_meta_commentary = False
        
        for token in tokens:
            accumulated += token
            
            # Skip initial colon prefix and whitespace
            if not response_started:
                if accumulated.strip().startswith(':'):
                    accumulated = accumulated.lstrip(': ')
                
                # Check if we're starting with meta-commentary
                if any(pattern in accumulated.lower() for pattern in [
                    'okay, so the user',
                    'the user\'s message is',
                    'if the response doesn\'t',
                    'we need to inform them',
                    'let me start'
                ]):
                    in_meta_commentary = True
                
                # Skip the meta-commentary part
                if in_meta_commentary:
                    # Look for the transition to actual content
                    sentences = accumulated.split('.')
                    clean_sentences = []
                    
                    for sentence in sentences:
                        sentence = sentence.strip()
                        if not sentence:
                            continue
                        
                        # Skip meta-commentary sentences
                        if any(phrase in sentence.lower() for phrase in [
                            'the user', 'the assistant', 'the response',
                            'we need to', 'if the', 'okay, so',
                            'in chinese', 'in english', 'let me'
                        ]):
                            continue
                        
                        # This looks like actual content
                        clean_sentences.append(sentence)
                    
                    if clean_sentences:
                        # We found clean content, start yielding
                        clean_content = '. '.join(clean_sentences)
                        if not clean_content.endswith('.'):
                            clean_content += '.'
                        
                        for char in clean_content:
                            yield char
                        
                        response_started = True
                        accumulated = ""
                    continue
                
                # If no meta-commentary detected and we have substantial content
                if len(accumulated.strip()) > 10:
                    response_started = True
                    for char in accumulated:
                        yield char
                    accumulated = ""
                continue
            
            # Normal token processing after response started
            if response_started:
                # Check for common artifacts to skip
                artifacts = ['<|endoftext|>', '</s>', '<|im_end|>']
                skip_token = False
                for artifact in artifacts:
                    if artifact in token:
                        skip_token = True
                        break
                
                if not skip_token:
                    yield token


# Convenience function for CLI integration
def create_streaming_filter(simple_mode: bool = False):
    """Create appropriate streaming filter"""
    if simple_mode:
        return SimpleStreamingFilter()
    else:
        return StreamingResponseFilter()


def filter_colon_prefix(tokens: Generator[str, None, None]) -> Generator[str, None, None]:
    """Very simple filter to remove colon prefix from streaming tokens"""
    first_token = True
    buffer = ""
    
    for token in tokens:
        if first_token:
            buffer += token
            # Wait until we have enough content to make a decision
            if len(buffer.strip()) >= 2:  # At least ": " or similar
                if buffer.strip().startswith(':'):
                    # Remove colon and any following whitespace
                    cleaned = buffer.lstrip(': ')
                    if cleaned:
                        yield cleaned
                else:
                    # No colon prefix, yield the buffer
                    yield buffer
                first_token = False
                buffer = ""
        else:
            # After first token, just pass through
            yield token


if __name__ == "__main__":
    # Test the streaming filter
    def mock_token_generator():
        """Generate test tokens that simulate problematic output"""
        test_response = ": Okay, so the user asked about Jimi Hendrix. The user's message is asking for information. I need to provide accurate information. Jimi Hendrix was an American rock musician."
        
        for char in test_response:
            yield char
    
    def mock_token_generator2():
        """Generate test tokens from actual user case"""
        test_response = ": Jimi Hendrikx was an American rock musician from Texas."
        
        for char in test_response:
            yield char
    
    print("Testing Simple Cases:")
    
    print("Test 1 - Complex meta commentary:")
    print("Original:", repr("".join(mock_token_generator())))
    simple_filter = SimpleStreamingFilter()
    simple_filtered = list(simple_filter.filter_tokens(mock_token_generator()))
    simple_response = "".join(simple_filtered)
    print("Filtered:", repr(simple_response))
    
    print("\nTest 2 - Simple colon prefix:")
    print("Original:", repr("".join(mock_token_generator2())))
    simple_filter2 = SimpleStreamingFilter()
    simple_filtered2 = list(simple_filter2.filter_tokens(mock_token_generator2()))
    simple_response2 = "".join(simple_filtered2)
    print("Filtered:", repr(simple_response2))
    
    # Test with even simpler approach for immediate fix
    print("\nTest 3 - Basic colon removal:")
    def basic_filter(tokens):
        first = True
        for token in tokens:
            if first and token.startswith(':'):
                token = token.lstrip(': ')
                first = False
            if token:
                yield token
    
    basic_filtered = list(basic_filter(mock_token_generator2()))
    basic_response = "".join(basic_filtered)
    print("Basic filtered:", repr(basic_response))