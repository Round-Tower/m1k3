#!/usr/bin/env python3
"""
M1K3 Response Formatter
Enhanced UX formatting for AI model responses with intelligent cleanup
"""

import re
from typing import List, Dict, Optional, Tuple
from dataclasses import dataclass
from enum import Enum


class ResponseType(Enum):
    CLEAN_ANSWER = "clean_answer"
    WITH_THINKING = "with_thinking"
    CODE_HEAVY = "code_heavy"
    CONVERSATIONAL = "conversational"
    ERROR_RESPONSE = "error_response"


@dataclass
class FormattedResponse:
    """Formatted response with metadata"""
    content: str
    response_type: ResponseType
    has_thinking: bool
    thinking_content: Optional[str] = None
    final_answer: Optional[str] = None
    confidence_score: float = 0.0


class ResponseFormatter:
    """Formats AI responses for optimal UX and readability"""
    
    def __init__(self):
        # Thinking process patterns
        self.thinking_patterns = [
            r'<think>(.*?)</think>',
            r'<thinking>(.*?)</thinking>', 
            r'Let me think about this\.\.\.(.*?)(?=\n\n|\Z)',
            r'I need to consider\.\.\.(.*?)(?=\n\n|\Z)',
            r'Okay, so the user asked(.*?)(?=\n\n|\Z)',
            r'Let me analyze this(.*?)(?=\n\n|\Z)',
            r'First, let me(.*?)(?=\n\n|\Z)',
        ]
        
        # Response cleanup patterns (enhanced for SmolLM2)
        self.cleanup_patterns = [
            # Standard cleanup
            (r'<\|endoftext\|>', ''),
            (r'</s>', ''),
            (r'<\|im_end\|>', ''),
            
            # SmolLM2-specific cleanup
            (r'<\|im_start\|>assistant\s*', ''),  # Remove ChatML assistant tags
            (r'<\|im_start\|>\w+\s*', ''),       # Remove other ChatML tags
            (r'### User:.*$', ''),                # Remove leaked user prompts
            (r'### Assistant:.*?(?=\w)', ''),     # Remove duplicate assistant markers
            (r'Human:.*$', ''),                   # Remove leaked human prompts
            (r'Assistant:\s*', ''),               # Remove duplicate assistant prefixes
            
            # SmolLM2 repetition patterns - more selective to preserve content
            (r'^\s*The user asked about\b.*?\n', ''),  # Only remove meta commentary at start of response
            (r'^\s*Looking at this question\b.*?\n', ''),  # Only remove question analysis at start
            (r'^\s*(Okay,?\s*)?[Ss]o,?\s*(?=the\s|this\s)', ''),  # Only remove "Okay, so" before articles
            
            # Improve articulation
            (r'\s+([.!?])', r'\1'),              # Fix spacing before punctuation
            (r'([.!?])\s*([a-z])', r'\1 \2'),    # Ensure space after punctuation
            (r'\s+', ' '),                       # Normalize whitespace
        ]
        
        # Paragraph detection patterns
        self.paragraph_indicators = [
            r'\n\n+',  # Double newlines
            r'\.(\s+)(?=[A-Z])',  # Sentence boundaries with capitals
            r'[.!?](\s+)(?=[A-Z])',  # Punctuation followed by capitals
        ]
    
    def extract_thinking_content(self, response: str) -> Tuple[Optional[str], str]:
        """Extract thinking content and final answer from response"""
        
        thinking_content = None
        remaining_content = response
        
        # Try structured thinking tags first
        for pattern in self.thinking_patterns:
            matches = re.findall(pattern, response, re.DOTALL | re.IGNORECASE)
            if matches:
                thinking_content = matches[0].strip()
                # Remove the thinking content from the response
                remaining_content = re.sub(pattern, '', response, flags=re.DOTALL | re.IGNORECASE)
                break
        
        # If no structured thinking found, check for unstructured reasoning
        if not thinking_content:
            thinking_content, remaining_content = self._detect_unstructured_thinking(response)
        
        return thinking_content, remaining_content.strip()
    
    def _detect_unstructured_thinking(self, response: str) -> Tuple[Optional[str], str]:
        """Detect unstructured thinking processes in responses"""
        
        # Look for common thinking indicators at start of sentences
        thinking_indicators = [
            r'^(The user asked about|The user is asking about|Let me think about this specific)',
            r'^(Looking at this specific question|This question is specifically asking)',
            r'^(From what I understand about the user\'s question)',
            r'\b(The user\'s message is specifically asking|we need to specifically inform them)',
            r'^(I need to carefully consider the user\'s question|Let me analyze this specific query)'
        ]
        
        # Split by sentences for better analysis
        sentences = re.split(r'([.!?]+)', response)
        cleaned_sentences = []
        thinking_sentences = []
        
        i = 0
        while i < len(sentences) - 1:
            sentence = sentences[i].strip()
            punctuation = sentences[i + 1] if i + 1 < len(sentences) else ''
            
            if sentence:
                full_sentence = sentence + punctuation
                
                # Check if this sentence contains thinking indicators
                is_thinking = any(re.search(pattern, sentence, re.IGNORECASE) for pattern in thinking_indicators)
                
                # Also check for meta-commentary about the conversation
                meta_patterns = [
                    r'\b(the user\'s|the assistant should|if the response|we need to)',
                    r'\b(violates any of the rules|doesn\'t meet the requirements)',
                    r'\b(in Chinese|in English|too long|let me start)'
                ]
                
                is_meta = any(re.search(pattern, sentence, re.IGNORECASE) for pattern in meta_patterns)
                
                if is_thinking or is_meta:
                    thinking_sentences.append(full_sentence)
                else:
                    cleaned_sentences.append(full_sentence)
            
            i += 2
        
        # Reconstruct the response
        thinking_content = ' '.join(thinking_sentences).strip() if thinking_sentences else None
        remaining_content = ' '.join(cleaned_sentences).strip()
        
        # If we cleaned out significant content, return the split
        if thinking_content and len(thinking_content) > 20:
            return thinking_content, remaining_content
        
        # If the thinking content is minimal or the remaining content is too short, keep original
        if not remaining_content or len(remaining_content) < 50:  # Increased threshold
            return None, response
        
        # If we removed too much (>70% of content), keep original
        if thinking_content and len(thinking_content) / len(response) > 0.7:
            return None, response
        
        return thinking_content, remaining_content
    
    def improve_articulation_smollm2(self, response: str) -> str:
        """Apply SmolLM2-specific articulation improvements"""
        
        # Split into sentences for better processing
        sentences = re.split(r'([.!?]+)', response)
        improved_sentences = []
        
        i = 0
        while i < len(sentences) - 1:
            sentence = sentences[i].strip()
            punctuation = sentences[i + 1] if i + 1 < len(sentences) else ''
            
            if sentence:
                # Improve sentence structure
                sentence = self._improve_sentence_structure(sentence)
                
                # Fix common SmolLM2 articulation issues
                sentence = self._fix_common_issues(sentence)
                
                # Add improved sentence with punctuation
                improved_sentences.append(sentence + punctuation)
            
            i += 2
        
        # Join and apply final improvements
        improved = ' '.join(improved_sentences)
        improved = self._apply_paragraph_structure(improved)
        
        return improved.strip()
    
    def _improve_sentence_structure(self, sentence: str) -> str:
        """Improve individual sentence structure"""
        sentence = sentence.strip()
        
        if not sentence:
            return sentence
        
        # Capitalize first word
        sentence = sentence[0].upper() + sentence[1:] if len(sentence) > 1 else sentence.upper()
        
        # Fix common transition issues
        transitions = {
            r'^and\s+': 'Additionally, ',
            r'^but\s+': 'However, ',
            r'^so\s+': 'Therefore, ',
            r'^because\s+': 'Since ',
        }
        
        for pattern, replacement in transitions.items():
            sentence = re.sub(pattern, replacement, sentence, flags=re.IGNORECASE)
        
        # Improve passive voice where appropriate
        sentence = re.sub(r'\b(it can be|this can be) (\w+ed)\b', r'you can \2 it', sentence)
        
        return sentence
    
    def _fix_common_issues(self, sentence: str) -> str:
        """Fix common SmolLM2 articulation issues"""
        
        # Remove redundant phrases
        redundant_patterns = [
            r'\b(in order to|in order for)\b',  # Replace with just "to"
            r'\bthat is to say\b',               # Often unnecessary
            r'\bas a matter of fact\b',          # Wordy
            r'\bthe fact that\b',                # Often redundant
        ]
        
        replacements = ['to', '', '', '']
        
        for pattern, replacement in zip(redundant_patterns, replacements):
            sentence = re.sub(pattern, replacement, sentence, flags=re.IGNORECASE)
        
        # Fix double conjunctions
        sentence = re.sub(r'\b(and|but|or)\s+(and|but|or)\b', r'\1', sentence, flags=re.IGNORECASE)
        
        # Improve word choice for clarity
        improvements = {
            r'\butilize\b': 'use',
            r'\bfacilitate\b': 'help',
            r'\bcommence\b': 'start',
            r'\bterminate\b': 'end',
            r'\bascertain\b': 'find out',
        }
        
        for wordy, simple in improvements.items():
            sentence = re.sub(wordy, simple, sentence, flags=re.IGNORECASE)
        
        return sentence
    
    def _apply_paragraph_structure(self, text: str) -> str:
        """Apply better paragraph structure to multi-sentence responses"""
        
        # Split into sentences
        sentences = re.split(r'([.!?]+)', text)
        structured_text = ""
        sentence_count = 0
        
        i = 0
        while i < len(sentences) - 1:
            sentence = sentences[i].strip()
            punctuation = sentences[i + 1] if i + 1 < len(sentences) else ''
            
            if sentence:
                if sentence_count > 0:
                    # Add appropriate spacing
                    if sentence_count % 3 == 0:  # New paragraph every 3 sentences
                        structured_text += "\n\n"
                    else:
                        structured_text += " "
                
                structured_text += sentence + punctuation
                sentence_count += 1
            
            i += 2
        
        return structured_text
    
    def determine_response_type(self, response: str) -> ResponseType:
        """Determine the type of response for appropriate formatting"""
        
        if not response or len(response.strip()) < 10:
            return ResponseType.ERROR_RESPONSE
        
        # Check for thinking patterns
        if any(pattern in response.lower() for pattern in ['let me think', 'i need to', 'analyzing']):
            return ResponseType.WITH_THINKING
        
        # Check for code content
        if '```' in response or 'def ' in response or 'import ' in response:
            return ResponseType.CODE_HEAVY
        
        # Check for conversational markers
        conversational_markers = ['hello', 'hi there', 'how can i help', 'nice to meet']
        if any(marker in response.lower() for marker in conversational_markers):
            return ResponseType.CONVERSATIONAL
        
        return ResponseType.CLEAN_ANSWER
    
    def calculate_confidence_score(self, response: str) -> float:
        """Calculate confidence score based on response quality indicators"""
        
        if not response:
            return 0.0
        
        score = 0.5  # Base score
        
        # Length factor (optimal around 50-200 words)
        word_count = len(response.split())
        if 50 <= word_count <= 200:
            score += 0.2
        elif 20 <= word_count < 50 or 200 < word_count <= 300:
            score += 0.1
        
        # Grammar indicators
        if response[0].isupper():  # Proper capitalization
            score += 0.1
        if response.endswith(('.', '!', '?')):  # Proper ending
            score += 0.1
        
        # Clarity indicators
        if not any(phrase in response.lower() for phrase in ['i think', 'maybe', 'possibly', 'perhaps']):
            score += 0.1  # Confident tone
        
        # Structure indicators
        if '\n' in response:  # Has structure
            score += 0.05
        
        return min(1.0, score)
    
    def apply_context_aware_formatting(self, response: str, system_context: Dict) -> str:
        """Apply formatting based on system context"""
        
        device_tier = system_context.get('device_tier', 'unknown')
        system_load = system_context.get('system_load_status', 'unknown')
        
        # For low-end devices or high load, simplify formatting
        if device_tier == 'minimal' or system_load == 'high-load':
            # Keep it simple - just basic cleanup
            return response.strip()
        
        # For better devices, enhance formatting
        if device_tier in ['balanced', 'high-performance']:
            # Apply better paragraph structure
            return self._apply_paragraph_structure(response)
        
        return response
    
    def format_response(self, response: str, show_thinking: bool = False, model_name: str = None) -> FormattedResponse:
        """Format response with comprehensive processing"""
        
        # Clean the response
        cleaned = self.clean_response(response)
        
        # Extract thinking content
        thinking_content, final_answer = self.extract_thinking_content(cleaned)
        
        # Determine response type
        response_type = self.determine_response_type(cleaned)
        
        # Calculate confidence
        confidence_score = self.calculate_confidence_score(cleaned)
        
        # Format based on whether to show thinking
        if show_thinking and thinking_content:
            content = f"**Thinking:** {thinking_content}\n\n**Answer:** {final_answer}"
        else:
            content = final_answer if final_answer else cleaned
        
        return FormattedResponse(
            content=content,
            response_type=response_type,
            has_thinking=thinking_content is not None,
            thinking_content=thinking_content,
            final_answer=final_answer or cleaned,
            confidence_score=confidence_score
        )
    
    def clean_response(self, response: str) -> str:
        """Apply intelligent response cleanup with safeguards"""
        
        original_length = len(response.strip())
        cleaned = response
        
        # Apply cleanup patterns
        for pattern, replacement in self.cleanup_patterns:
            cleaned = re.sub(pattern, replacement, cleaned, flags=re.MULTILINE | re.DOTALL)
        
        # Remove excessive whitespace
        cleaned = re.sub(r'\n\s*\n\s*\n', '\n\n', cleaned)  # Max 2 newlines
        cleaned = re.sub(r'[ \t]+', ' ', cleaned)  # Normalize spaces
        cleaned = cleaned.strip()
        
        # If we removed too much content (>80%), return original
        if original_length > 50 and len(cleaned) < original_length * 0.2:
            return response.strip()
        
        return cleaned
    
    def format_paragraphs(self, text: str) -> str:
        """Add proper paragraph structure for readability"""
        
        if not text or len(text) < 100:
            return text  # Don't format very short responses
        
        # Split into sentences
        sentences = re.split(r'([.!?]+)', text)
        if len(sentences) < 3:
            return text  # Not enough content to format
        
        formatted_parts = []
        current_paragraph = []
        sentence_count = 0
        
        i = 0
        while i < len(sentences) - 1:
            sentence = sentences[i].strip()
            punctuation = sentences[i + 1] if i + 1 < len(sentences) else ''
            
            if sentence:
                current_paragraph.append(sentence + punctuation)
                sentence_count += 1
                
                # Start new paragraph after 2-4 sentences, depending on length
                avg_length = sum(len(s) for s in current_paragraph) / len(current_paragraph)
                
                if sentence_count >= 2 and (sentence_count >= 4 or avg_length > 80):
                    formatted_parts.append(' '.join(current_paragraph))
                    current_paragraph = []
                    sentence_count = 0
            
            i += 2
        
        # Add remaining sentences
        if current_paragraph:
            formatted_parts.append(' '.join(current_paragraph))
        
        return '\n\n'.join(formatted_parts)
    
    def format_code_blocks(self, text: str) -> str:
        """Improve code block formatting"""
        
        # Detect and format code blocks
        code_patterns = [
            (r'```(\w+)?\n(.*?)```', r'```\1\n\2```'),  # Already formatted
            (r'`([^`\n]+)`', r'`\1`'),  # Inline code
            (r'^(\s{4}.*$)', r'\1', re.MULTILINE),  # Indented code
        ]
        
        formatted = text
        for pattern, replacement, *flags in code_patterns:
            flag = flags[0] if flags else 0
            formatted = re.sub(pattern, replacement, formatted, flags=flag)
        
        return formatted
    
    def determine_response_type(self, response: str, has_thinking: bool) -> ResponseType:
        """Determine the type of response for appropriate formatting"""
        
        if has_thinking:
            return ResponseType.WITH_THINKING
        
        # Check for code content
        if re.search(r'```|`[^`]+`|def |class |import |from |<[^>]+>', response):
            return ResponseType.CODE_HEAVY
        
        # Check for conversational patterns
        conversational_patterns = [
            r'\b(hello|hi|hey|thanks|please|sorry)\b',
            r'[?!]',  # Questions and exclamations
            r'\b(I|you|we|your|my)\b'  # Personal pronouns
        ]
        
        if any(re.search(pattern, response, re.IGNORECASE) for pattern in conversational_patterns):
            return ResponseType.CONVERSATIONAL
        
        return ResponseType.CLEAN_ANSWER
    
    def calculate_confidence_score(self, response: str, thinking_content: str = None) -> float:
        """Calculate confidence score based on response quality indicators"""
        
        score = 0.5  # Base score
        
        # Length indicates completeness
        if len(response) > 50:
            score += 0.1
        if len(response) > 200:
            score += 0.1
        
        # Complete sentences
        sentence_count = len(re.findall(r'[.!?]+', response))
        if sentence_count >= 2:
            score += 0.1
        
        # Proper capitalization
        if re.search(r'^[A-Z]', response.strip()):
            score += 0.05
        
        # Has thinking process (shows reasoning)
        if thinking_content and len(thinking_content) > 30:
            score += 0.15
        
        # Avoid repetition penalty
        words = response.lower().split()
        if len(set(words)) / max(len(words), 1) > 0.7:  # Good word diversity
            score += 0.1
        
        return min(score, 1.0)
    
    def format_response(self, raw_response: str, 
                       show_thinking: bool = False,
                       model_name: str = None) -> FormattedResponse:
        """Main method to format a response with all enhancements"""
        
        # Step 1: Clean the raw response
        cleaned = self.clean_response(raw_response)
        
        # Step 2: Extract thinking content
        thinking_content, final_answer = self.extract_thinking_content(cleaned)
        
        # Step 3: Determine response type
        response_type = self.determine_response_type(final_answer, bool(thinking_content))
        
        # Step 4: Apply appropriate formatting
        if response_type == ResponseType.CODE_HEAVY:
            formatted_content = self.format_code_blocks(final_answer)
        elif response_type == ResponseType.CONVERSATIONAL:
            formatted_content = final_answer  # Keep conversational responses simple
        else:
            formatted_content = self.format_paragraphs(final_answer)
        
        # Step 5: Decide what to show
        if show_thinking and thinking_content:
            # Show thinking process + formatted answer
            thinking_formatted = f"💭 **Thinking Process:**\n{thinking_content}\n\n"
            final_content = f"{thinking_formatted}**Response:**\n{formatted_content}"
        else:
            # Just show the clean formatted answer
            final_content = formatted_content
        
        # Step 6: Calculate confidence
        confidence = self.calculate_confidence_score(formatted_content, thinking_content)
        
        return FormattedResponse(
            content=final_content,
            response_type=response_type,
            has_thinking=bool(thinking_content),
            thinking_content=thinking_content,
            final_answer=formatted_content,
            confidence_score=confidence
        )
    
    def format_streaming_response(self, token: str, 
                                accumulated_response: str = "",
                                show_thinking: bool = False) -> str:
        """Format individual tokens for streaming responses"""
        
        # For streaming, we mostly pass through tokens but can apply light filtering
        
        # Filter out obvious artifacts during streaming
        artifacts = ['<|endoftext|>', '</s>', '<|im_end|>']
        if token in artifacts:
            return ""
        
        # If we detect thinking tags during streaming and don't want to show them
        if not show_thinking:
            if '<think>' in accumulated_response and not '</think>' in accumulated_response:
                # We're currently inside thinking tags, don't show
                return ""
            if token == '<' and accumulated_response.endswith('<think'):
                return ""
        
        return token


# Enhanced SmolLM2 functions
def format_ai_response_smollm2(raw_response: str, system_context: Optional[Dict] = None) -> FormattedResponse:
    """Enhanced response formatting specifically for SmolLM2 with dynamic context awareness"""
    
    formatter = ResponseFormatter()
    
    # Apply SmolLM2-specific articulation improvements
    cleaned = formatter.improve_articulation_smollm2(raw_response)
    
    # Standard cleanup
    cleaned = formatter.clean_response(cleaned)
    
    # Extract thinking content
    thinking_content, final_answer = formatter.extract_thinking_content(cleaned)
    
    # Determine response type
    response_type = formatter.determine_response_type(cleaned)
    
    # Calculate confidence score based on clarity and completeness
    confidence_score = formatter.calculate_confidence_score(cleaned)
    
    # Apply context-aware formatting if system context is provided
    if system_context:
        cleaned = formatter.apply_context_aware_formatting(cleaned, system_context)
    
    return FormattedResponse(
        content=cleaned,
        response_type=response_type,
        has_thinking=thinking_content is not None,
        thinking_content=thinking_content,
        final_answer=final_answer or cleaned,
        confidence_score=confidence_score
    )

def clean_ai_response_smollm2(response: str) -> str:
    """Quick SmolLM2-specific response cleanup with articulation improvements"""
    formatter = ResponseFormatter()
    cleaned = formatter.improve_articulation_smollm2(response)
    return formatter.clean_response(cleaned)

# Convenience functions
def format_ai_response(response: str, show_thinking: bool = False, model_name: str = None) -> str:
    """Format AI response with improved UX"""
    formatter = ResponseFormatter()
    formatted = formatter.format_response(response, show_thinking, model_name)
    return formatted.content


def clean_ai_response(response: str) -> str:
    """Quick response cleanup"""
    formatter = ResponseFormatter()
    return formatter.clean_response(response)


if __name__ == "__main__":
    # Test the response formatter
    formatter = ResponseFormatter()
    
    test_responses = [
        "Hello! How can I help you today?",
        
        "<think>The user is asking about Python. I should provide a comprehensive overview covering its main features and use cases.</think>\n\nPython is a high-level programming language known for its simplicity and readability. It was created by Guido van Rossum and first released in 1991. Python supports multiple programming paradigms including procedural, object-oriented, and functional programming. It's widely used for web development, data analysis, artificial intelligence, and automation.",
        
        "Okay, so the user asked about machine learning. Let me think about this carefully. Machine learning is a subset of AI. There are different types like supervised and unsupervised learning. \n\nMachine learning is a method of data analysis that automates analytical model building. It uses algorithms that iteratively learn from data without being explicitly programmed.",
        
        "Here's a simple Python function:\n\n```python\ndef hello_world():\n    print('Hello, World!')\n    return True\n```\n\nThis function prints a greeting and returns True."
    ]
    
    print("Testing Response Formatter:\n")
    
    for i, response in enumerate(test_responses, 1):
        print(f"=== Test {i} ===")
        print(f"Original: {repr(response)}\n")
        
        # Test without thinking
        formatted = formatter.format_response(response, show_thinking=False)
        print(f"Formatted (no thinking): {formatted.content}")
        print(f"Type: {formatted.response_type.value}")
        print(f"Has thinking: {formatted.has_thinking}")
        print(f"Confidence: {formatted.confidence_score:.2f}\n")
        
        # Test with thinking
        if formatted.has_thinking:
            formatted_thinking = formatter.format_response(response, show_thinking=True)
            print(f"Formatted (with thinking): {formatted_thinking.content}\n")
        
        print("-" * 50, "\n")