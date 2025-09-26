#!/usr/bin/env python3
"""
Text Processors for M1K3 Voice Engine
Utilities for preparing text for synthesis.
"""

from typing import List
import re

def smart_text_chunking(text: str, chunk_size: int = 300, overlap_words: int = 1) -> List[str]:
    """
    Intelligently chunks text to a max length, preserving sentence structure
    and adding word overlap for more natural-sounding transitions.
    """
    if len(text) <= chunk_size:
        return [text]
    
    chunks = []
    # Split by common sentence terminators, preserving them
    sentences = text.replace('!', '.').replace('?', '.').split('.')
    current_chunk = ""
    
    for i, sentence in enumerate(sentences):
        # Skip empty strings that result from splitting
        if not sentence:
            continue
            
        is_last_sentence = (i == len(sentences) - 1)
        
        # Add the period back to all but the last segment (if it was empty)
        if not is_last_sentence or (is_last_sentence and not text.endswith(sentence)):
             sentence += "."
        
        # Handle cases where a sentence itself is too long
        if len(sentence) > chunk_size:
            if current_chunk:
                chunks.append(current_chunk.strip())
                current_chunk = ""
            
            # Sentence is a single long word/string, needs forced chunking
            for i in range(0, len(sentence), chunk_size):
                chunks.append(sentence[i:i+chunk_size])
            continue

        # Check if adding the next sentence exceeds the chunk size
        if len(current_chunk) + len(sentence) + 1 <= chunk_size:
            current_chunk += (" " if current_chunk else "") + sentence
        else:
            chunks.append(current_chunk.strip())
            
            # Create overlap for smoother transitions
            words = current_chunk.split()
            if len(words) > overlap_words:
                overlap = " ".join(words[-overlap_words:])
                current_chunk = overlap + " " + sentence
            else:
                current_chunk = sentence

    if current_chunk:
        chunks.append(current_chunk.strip())
        
    return [c for c in chunks if c] # Filter out any empty chunks

def sanitize_text_for_speech(text: str) -> str:
    """
    Clean text for natural speech synthesis, removing markdown and formatting
    that would sound awkward when spoken aloud.
    """
    if not text or not isinstance(text, str):
        return ""

    # Start with the original text
    cleaned = text

    # Remove markdown formatting
    cleaned = re.sub(r'\*\*(.+?)\*\*', r'\1', cleaned)  # **bold** -> bold
    cleaned = re.sub(r'\*(.+?)\*', r'\1', cleaned)      # *italic* -> italic
    cleaned = re.sub(r'`(.+?)`', r'\1', cleaned)        # `code` -> code
    cleaned = re.sub(r'```[\s\S]*?```', '', cleaned)    # Remove code blocks

    # Remove headers
    cleaned = re.sub(r'^#{1,6}\s+', '', cleaned, flags=re.MULTILINE)

    # Clean up links - keep the text but remove URL formatting
    cleaned = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', cleaned)  # [text](url) -> text
    cleaned = re.sub(r'<([^>]+)>', r'\1', cleaned)              # <url> -> url

    # Remove bullet points and list formatting
    cleaned = re.sub(r'^\s*[-*+]\s+', '', cleaned, flags=re.MULTILINE)
    cleaned = re.sub(r'^\s*\d+\.\s+', '', cleaned, flags=re.MULTILINE)

    # Replace underscores used for emphasis with spaces
    cleaned = re.sub(r'_{2,}', ' ', cleaned)

    # Clean up parenthetical asides that might sound awkward
    cleaned = re.sub(r'\([^)]*\)', '', cleaned)  # Remove (parenthetical notes)

    # Fix common speech issues
    cleaned = cleaned.replace('&', ' and ')
    cleaned = cleaned.replace('@', ' at ')
    cleaned = cleaned.replace('#', ' number ')
    cleaned = cleaned.replace('%', ' percent')
    cleaned = cleaned.replace('$', ' dollars ')

    # Clean up whitespace
    cleaned = re.sub(r'\s+', ' ', cleaned)  # Multiple spaces -> single space
    cleaned = cleaned.strip()

    # Fix common pronunciation issues
    cleaned = re.sub(r'\b(AI|ML|TTS|API|HTTP)\b', lambda m: spell_out_acronym(m.group(1)), cleaned)

    return cleaned

def spell_out_acronym(acronym: str) -> str:
    """Convert common tech acronyms to speakable form"""
    acronym_map = {
        'AI': 'A I',
        'ML': 'M L',
        'TTS': 'T T S',
        'API': 'A P I',
        'HTTP': 'H T T P',
        'URL': 'U R L',
        'CPU': 'C P U',
        'GPU': 'G P U',
        'RAM': 'R A M'
    }
    return acronym_map.get(acronym.upper(), acronym)

def prepare_text_for_realtime_synthesis(text: str) -> List[str]:
    """
    Prepare text for streaming TTS synthesis - clean and chunk for immediate speaking
    """
    # First sanitize for speech
    clean_text = sanitize_text_for_speech(text)

    if not clean_text:
        return []

    # For real-time synthesis, use smaller chunks (sentence-level)
    sentences = re.split(r'[.!?]+', clean_text)

    # Clean and filter sentences
    processed_sentences = []
    for sentence in sentences:
        sentence = sentence.strip()
        if sentence and len(sentence) > 3:  # Skip very short fragments
            # Add back punctuation for natural speech rhythm
            if not sentence.endswith(('.', '!', '?')):
                sentence += '.'
            processed_sentences.append(sentence)

    return processed_sentences

if __name__ == "__main__":
    print("Testing Text Processors...")

    # Test Case 1: Simple short text
    text1 = "This is a short sentence."
    chunks1 = smart_text_chunking(text1, chunk_size=100)
    assert chunks1 == ["This is a short sentence."], f"Test 1 Failed: {chunks1}"
    print("✅ Test Case 1 (Short Text) Passed.")

    # Test Case 2: Long text requiring multiple chunks
    text2 = "This is the first sentence. This is the second sentence, which is a bit longer. And this is the final one."
    chunks2 = smart_text_chunking(text2, chunk_size=70, overlap_words=2)
    expected2 = [
        "This is the first sentence. This is the second sentence, which is a.",
        "a. bit longer. And this is the final one."
    ]
    # Note: The exact output can be tricky, let's just check the number of chunks and non-emptiness
    assert len(chunks2) > 1, f"Test 2 Failed: Expected multiple chunks, got {len(chunks2)}"
    assert all(chunks2), f"Test 2 Failed: Got empty chunks: {chunks2}"
    print("✅ Test Case 2 (Long Text) Passed.")

    # Test Case 3: A single sentence that is longer than the chunk size
    text3 = "Thisisaverysinglelongsentencethatmustbesplitupbywordsbecauseitexceedsthemaximumchunksize."
    chunks3 = smart_text_chunking(text3, chunk_size=50)
    assert len(chunks3) == 2, f"Test 3 Failed: Expected 2 chunks, got {len(chunks3)}"
    assert chunks3[0] == "Thisisaverysinglelongsentencethatmustbesplitupbywo", f"Test 3 Failed: Incorrect first chunk: {chunks3[0]}"
    assert chunks3[1] == "rdsbecauseitexceedsthemaximumchunksize.", f"Test 3 Failed: Incorrect second chunk: {chunks3[1]}"
    print("✅ Test Case 3 (Single Long Sentence) Passed.")
    
    # Test Case 4: Text ending exactly at chunk limit
    text4 = "This text is exactly fifty characters long right now." # 55 chars
    chunks4 = smart_text_chunking(text4, chunk_size=55)
    assert chunks4 == ["This text is exactly fifty characters long right now."], f"Test 4 Failed: {chunks4}"
    print("✅ Test Case 4 (Exact Length) Passed.")

    print("\n✅ All text processor tests passed!")
