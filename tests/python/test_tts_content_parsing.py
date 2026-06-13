#!/usr/bin/env python3
"""
Test TTS content parsing to understand where content is being lost
"""

import os
import sys

# Repo root is two levels up from tests/python/ — portable, no hardcoded home path.
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))

from src.utils.model_output_parser import parse_model_output

def test_content_parsing():
    """Test how the content parser handles a full AI response"""
    
    # Sample response like the one we saw in the actual test
    sample_response = """The capital of France is Paris. Here are three famous landmarks that make up the heart of Paris:

1. Eiffel Tower (Tournedos) - Located at the heart of the city, this iconic iron lady was designed by Gustave Eiffel for the 1889 World's Fair in Paris. It stands at an impressive height of 324 meters and is a symbol of Paris and the world.

2. Notre-Dame Cathedral (Rue Saint-Michel) - This beautiful Gothic cathedral, built between 1270 and 1290 for the French king Louis XV, is one of the oldest churches in the world. It's home to over 350,000 worshippers annually, and its stunning stained glass windows are a testament to the city's rich history.

3. Louvre Museum (Louvre) - One of the largest museums in the world, this museum houses thousands of works of art from ancient civilizations to modern times, including works by Leonardo da Vinci, Michelangelo, and Vincent van Gogh. The Louvre is also home to an impressive collection of scientific instruments, artifacts, and artworks that have become a center for research and cultural exchange worldwide."""
    
    print("🧪 Testing TTS Content Parsing")
    print("="*60)
    print(f"Original response length: {len(sample_response)} characters")
    print(f"Original response words: {len(sample_response.split())} words")
    print()
    
    # Parse the content
    parsed_content = parse_model_output(sample_response)
    
    print("📊 Parsing Results:")
    print(f"Number of segments: {len(parsed_content.segments)}")
    print(f"Has content: {parsed_content.has_content}")
    print(f"Needs clarification: {parsed_content.needs_clarification}")
    print()
    
    total_parsed_length = 0
    print("📝 Segments:")
    for i, segment in enumerate(parsed_content.segments, 1):
        print(f"  {i}. {segment.content_type.value.upper()}")
        print(f"     Text: \"{segment.text[:100]}{'...' if len(segment.text) > 100 else ''}\"")
        print(f"     Length: {len(segment.text)} chars")
        print(f"     Synthesis mode: {segment.synthesis_mode}")
        print(f"     Confidence: {segment.confidence:.2f}")
        total_parsed_length += len(segment.text)
        print()
    
    print("📈 Summary:")
    print(f"Total parsed content length: {total_parsed_length} chars")
    print(f"Original length: {len(sample_response)} chars")
    print(f"Content preservation: {(total_parsed_length / len(sample_response)) * 100:.1f}%")
    
    # Check if any content is being lost
    if total_parsed_length < len(sample_response) * 0.9:
        print("⚠️  Significant content loss detected!")
    else:
        print("✅ Content preservation looks good")

if __name__ == "__main__":
    test_content_parsing()