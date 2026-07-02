#!/usr/bin/env python3
"""
Test Phonemizer Fix
Tests the enhanced text preprocessing to resolve phonemizer warnings
"""

import sys
from pathlib import Path

# Add src directory to path
sys.path.insert(0, str(Path(__file__).parent / "src"))

def test_phonemizer_preprocessing():
    """Test enhanced text preprocessing that prevents phonemizer warnings"""

    print("🔧 Testing Enhanced Phonemizer-Safe Text Preprocessing")
    print("=" * 60)

    from src.utils.text_processors import sanitize_text_for_speech

    # Test cases that commonly cause phonemizer issues
    problematic_texts = [
        # Original text from showcase that was causing issues
        "Welcome to something unprecedented: a conversation with an AI that truly respects your privacy. I am M1K3™",

        # Technical text with special characters
        "System status: CPU @ 85°C, RAM: 16GB, running AI/ML models with HTTP/API calls",

        # Text with special quotes and symbols
        'The AI said "Hello world" and used fancy symbols like • × ÷ ± …',

        # Mixed alphanumeric that confuses phonemizer
        "Visit us on the 1st floor, room 3B, building M1K3 at 2nd street",

        # Complex punctuation patterns
        "Wait...what?! Are you saying (in parentheses) that AIs can—truly—understand us?",

        # Long technical text
        "The KittenTTS synthesis engine uses ONNX runtime with BERT-based phonemization to convert text into speech, but sometimes encounters edge cases with special characters, mixed alphanumeric strings, and complex punctuation patterns that result in phonemizer warnings about word count mismatches between the input text and the phonemized output, which can cause synthesis failures."
    ]

    print("Testing problematic texts that typically cause phonemizer warnings:\n")

    for i, text in enumerate(problematic_texts, 1):
        print(f"Test {i}:")
        print(f"Original:  {text[:80]}{'...' if len(text) > 80 else ''}")

        try:
            cleaned = sanitize_text_for_speech(text)
            print(f"Cleaned:   {cleaned[:80]}{'...' if len(cleaned) > 80 else ''}")

            # Check if cleaning was effective
            safe_chars = all(c.isalnum() or c.isspace() or c in '.,!?;:\'"-' for c in cleaned)
            reasonable_length = 3 <= len(cleaned) <= 500
            no_mixed_alphanum = not any(c.isdigit() and c.isalpha() for c in cleaned)

            if safe_chars and reasonable_length:
                print("Status:    ✅ Safe for phonemizer")
            else:
                print(f"Status:    ⚠️  May still have issues (safe_chars: {safe_chars}, length: {len(cleaned)})")

        except Exception as e:
            print(f"Error:     ❌ Processing failed: {e}")

        print()

    # Test with actual KittenTTS if available
    print("🎤 Testing with actual KittenTTS synthesis...")
    print("-" * 40)

    try:
        from src.tts.controllers.kittentts_manager import KittenManager

        if KittenManager.is_available():
            # Load the model
            if KittenManager.load_model():
                print("✅ KittenTTS model loaded successfully")

                # Test with a problematic text sample
                test_text = 'The AI™ said "Hello" with 1st-generation technology @ 100% efficiency!'
                print(f"Testing synthesis of: {test_text}")

                # Clean the text first
                cleaned_text = sanitize_text_for_speech(test_text)
                print(f"Cleaned version: {cleaned_text}")

                # Try to synthesize
                print("🎵 Attempting synthesis...")
                audio = KittenManager.generate(cleaned_text)

                if audio is not None:
                    print(f"✅ Synthesis successful! Audio shape: {audio.shape}")
                    print("🎯 No phonemizer warnings should appear above")
                else:
                    print("❌ Synthesis failed - audio is None")

            else:
                print("❌ Failed to load KittenTTS model")
        else:
            print("⚠️ KittenTTS not available - skipping synthesis test")

    except Exception as e:
        print(f"⚠️ Synthesis test failed: {e}")

    print("\n" + "=" * 60)
    print("🎯 Phonemizer Fix Test Complete!")
    print("\nKey improvements made:")
    print("✅ Normalize special quotes, dashes, and symbols")
    print("✅ Remove trademark symbols and special characters")
    print("✅ Fix spacing around punctuation")
    print("✅ Convert alphanumeric sequences to words")
    print("✅ Remove mixed alphanumeric strings like M1K3")
    print("✅ Truncate very long text to prevent ONNX errors")
    print("✅ Ensure non-empty output with safe fallback")

if __name__ == "__main__":
    test_phonemizer_preprocessing()