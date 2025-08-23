#!/usr/bin/env python3
"""
Test suite for model output parser - TDD approach
Tests written FIRST before implementation to define expected behavior
"""

import pytest
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Import will fail initially - that's expected in TDD!
try:
    from model_output_parser import ModelOutputParser, ContentType, ParsedContent, ContentSegment
except ImportError:
    # This is expected in TDD - we write tests first!
    pytest.skip("model_output_parser.py not yet implemented", allow_module_level=True)

class TestModelOutputParser:
    """Test cases for parsing different types of model output"""
    
    @pytest.fixture
    def parser(self):
        """Create parser instance for testing"""
        return ModelOutputParser()
    
    def test_parse_thinking_blocks(self, parser):
        """Test parsing of <thinking> blocks"""
        text = """<thinking>
Let me consider this question carefully. The user is asking about...
I need to think through the implications here.
</thinking>

The answer is that we should approach this by first understanding the core concepts."""
        
        result = parser.parse(text)
        
        assert len(result.segments) == 2
        assert result.segments[0].content_type == ContentType.THINKING
        assert "Let me consider this question carefully" in result.segments[0].text
        assert result.segments[1].content_type == ContentType.ANSWER
        assert "The answer is that we should" in result.segments[1].text
    
    def test_parse_narration_asterisks(self, parser):
        """Test parsing of *narration* style content"""
        text = "*The user looks thoughtful as they consider the options.*\n\nWell, there are several ways to approach this problem. *pauses to gather thoughts* First, we need to understand..."
        
        result = parser.parse(text)
        
        # Should have 3 segments: narration, answer, narration, answer
        assert len(result.segments) == 4
        assert result.segments[0].content_type == ContentType.NARRATION
        assert "The user looks thoughtful" in result.segments[0].text
        assert result.segments[1].content_type == ContentType.ANSWER
        assert "Well, there are several ways" in result.segments[1].text
        assert result.segments[2].content_type == ContentType.NARRATION
        assert "pauses to gather thoughts" in result.segments[2].text
    
    def test_parse_clarification_questions(self, parser):
        """Test detection of clarification requests"""
        text = "I'd be happy to help! Could you please clarify what specific aspect you're most interested in? Are you looking for technical details or a general overview?"
        
        result = parser.parse(text)
        
        # Should detect this as needing clarification
        assert result.needs_clarification == True
        assert result.segments[0].content_type == ContentType.CLARIFICATION
        assert "Could you please clarify" in result.segments[0].text
    
    def test_parse_mixed_content(self, parser):
        """Test parsing content with multiple types mixed together"""
        text = """<thinking>
This is a complex question. Let me break it down.
</thinking>

*The assistant takes a moment to organize their thoughts.*

Here's what I think: First, we need to understand the basics. *gestures to emphasize the point* 

But I'm not sure if you want the technical details or just a general overview? Could you help me understand your background level?"""
        
        result = parser.parse(text)
        
        # Should have thinking, narration, answer, narration, clarification
        assert len(result.segments) == 5
        assert result.segments[0].content_type == ContentType.THINKING
        assert result.segments[1].content_type == ContentType.NARRATION
        assert result.segments[2].content_type == ContentType.ANSWER
        assert result.segments[3].content_type == ContentType.NARRATION
        assert result.segments[4].content_type == ContentType.CLARIFICATION
        assert result.needs_clarification == True
    
    def test_parse_nested_content(self, parser):
        """Test handling of nested or overlapping content types"""
        text = """<thinking>
Let me think about this. *Actually, I need more information about the context.*
The user seems to be asking about...
</thinking>

Based on my analysis, here's what I recommend."""
        
        result = parser.parse(text)
        
        # Thinking block should contain the narration, not be separate
        assert len(result.segments) == 2
        assert result.segments[0].content_type == ContentType.THINKING
        assert "*Actually, I need more information" in result.segments[0].text
        assert result.segments[1].content_type == ContentType.ANSWER
    
    def test_parse_code_blocks_ignored(self, parser):
        """Test that code blocks are treated as regular content"""
        text = """Here's how to implement this:

```python
def thinking_parser():
    # This should not be parsed as thinking
    return "code"
```

*Now let me explain what this code does.*"""
        
        result = parser.parse(text)
        
        # Should have answer and narration, but code block stays in answer
        assert len(result.segments) == 2
        assert result.segments[0].content_type == ContentType.ANSWER
        assert "```python" in result.segments[0].text
        assert result.segments[1].content_type == ContentType.NARRATION
        assert "Now let me explain" in result.segments[1].text
    
    def test_parse_empty_content(self, parser):
        """Test handling of empty or whitespace-only content"""
        text = "   \n\n   \t  "
        
        result = parser.parse(text)
        
        assert len(result.segments) == 0
        assert result.has_content == False
    
    def test_parse_plain_answer(self, parser):
        """Test parsing of plain answer with no special formatting"""
        text = "This is a straightforward answer with no special formatting or content types."
        
        result = parser.parse(text)
        
        assert len(result.segments) == 1
        assert result.segments[0].content_type == ContentType.ANSWER
        assert result.segments[0].text.strip() == text.strip()
        assert result.needs_clarification == False
    
    def test_content_type_confidence(self, parser):
        """Test confidence scoring for content type detection"""
        text = "Maybe this could be considered a question?"
        
        result = parser.parse(text)
        
        # Should have low confidence for clarification due to weak questioning
        clarification_segment = next((s for s in result.segments if s.content_type == ContentType.CLARIFICATION), None)
        if clarification_segment:
            assert clarification_segment.confidence < 0.7  # Low confidence
    
    def test_preserve_original_formatting(self, parser):
        """Test that original text formatting is preserved"""
        text = """<thinking>
  Indented thinking with
    multiple levels
</thinking>

**Bold text** and *italic* formatting should be preserved."""
        
        result = parser.parse(text)
        
        # Original formatting should be maintained
        thinking_text = result.segments[0].text
        answer_text = result.segments[1].text
        
        assert "  Indented thinking" in thinking_text  # Preserve indentation
        assert "**Bold text**" in answer_text  # Preserve markdown
        assert "*italic*" in answer_text  # Preserve emphasis

class TestContentTypeEnum:
    """Test the ContentType enumeration"""
    
    def test_content_type_values(self):
        """Test that all expected content types exist"""
        assert ContentType.THINKING.value == "thinking"
        assert ContentType.NARRATION.value == "narration"
        assert ContentType.ANSWER.value == "answer"
        assert ContentType.CLARIFICATION.value == "clarification"
    
    def test_content_type_ordering(self):
        """Test content type priority ordering for voice synthesis"""
        # Higher priority types should have lower numeric values for sorting
        assert ContentType.CLARIFICATION.priority < ContentType.ANSWER.priority
        assert ContentType.ANSWER.priority < ContentType.NARRATION.priority
        assert ContentType.NARRATION.priority < ContentType.THINKING.priority

class TestParsedContent:
    """Test the ParsedContent data structure"""
    
    def test_parsed_content_creation(self):
        """Test creating ParsedContent with segments"""
        segments = [
            ContentSegment(ContentType.THINKING, "thinking text", 0.9),
            ContentSegment(ContentType.ANSWER, "answer text", 1.0)
        ]
        
        parsed = ParsedContent(segments)
        
        assert len(parsed.segments) == 2
        assert parsed.has_content == True
        assert parsed.needs_clarification == False  # No clarification segments
    
    def test_needs_clarification_detection(self):
        """Test automatic detection of clarification needs"""
        segments = [
            ContentSegment(ContentType.ANSWER, "Here's my response.", 1.0),
            ContentSegment(ContentType.CLARIFICATION, "But could you clarify?", 0.8)
        ]
        
        parsed = ParsedContent(segments)
        
        assert parsed.needs_clarification == True

if __name__ == "__main__":
    pytest.main([__file__, "-v", "--tb=short"])