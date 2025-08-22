#!/usr/bin/env python3
"""
M1K3 Voice Text Preprocessor
Cleans text for voice synthesis to prevent phonemizer warnings
"""

import re


class VoiceTextPreprocessor:
    """Preprocesses text to make it voice-synthesis friendly"""
    
    def __init__(self):
        # Patterns that cause phonemizer issues
        self.problematic_patterns = [
            # Numbers with decimals and percentages
            (r'(\d+\.?\d*)%', self._replace_percentage),
            (r'(\d+\.\d+)', self._replace_decimal),
            
            # Technical notation
            (r'\b(\d+)x(\d+)\b', r'\1 by \2'),  # Resolution like 256x256
            (r'(\d+)fps', r'\1 frames per second'),
            (r'(\d+)MB', r'\1 megabytes'),
            (r'(\d+)GB', r'\1 gigabytes'),
            (r'(\d+)ms', r'\1 milliseconds'),
            (r'(\d+)s\b', r'\1 seconds'),
            
            # File paths and URLs
            (r'https?://[^\s]+', 'web address'),
            (r'/[^\s]*', 'file path'),
            (r'\\[^\s]*', 'file path'),
            
            # Special characters that confuse phonemizer
            (r'[{}[\]()<>]', ''),  # Remove brackets and symbols
            (r'[@#$^&*+=|]', ''),  # Remove special chars
            (r'_+', ' '),  # Replace underscores with spaces
            (r'-{2,}', ' '),  # Replace multiple dashes
            
            # Technical terms
            (r'\bCO2\b', 'carbon dioxide'),
            (r'\bkWh\b', 'kilowatt hours'),
            (r'\bCPU\b', 'processor'),
            (r'\bRAM\b', 'memory'),
            (r'\bSSD\b', 'storage'),
            (r'\bGPU\b', 'graphics'),
            (r'\bOS\b', 'operating system'),
            
            # Clean up extra whitespace
            (r'\s+', ' '),
        ]
    
    def _replace_percentage(self, match):
        """Convert percentages to speech-friendly format"""
        number = match.group(1)
        if '.' in number:
            # Handle decimal percentages
            return f"{number} percent"
        else:
            return f"{number} percent"
    
    def _replace_decimal(self, match):
        """Convert decimal numbers to speech-friendly format"""
        number = match.group(1)
        parts = number.split('.')
        if len(parts) == 2:
            return f"{parts[0]} point {parts[1]}"
        return number
    
    def preprocess_for_voice(self, text: str, max_length: int = 200) -> str:
        """
        Preprocess text to make it voice-synthesis friendly
        
        Args:
            text: Input text to preprocess
            max_length: Maximum length for voice synthesis (to prevent ONNX errors)
        
        Returns:
            Preprocessed text safe for voice synthesis
        """
        if not text or not text.strip():
            return ""
        
        # Start with cleaned text
        cleaned = text.strip()
        
        # Apply all preprocessing patterns
        for pattern, replacement in self.problematic_patterns:
            if callable(replacement):
                cleaned = re.sub(pattern, replacement, cleaned, flags=re.IGNORECASE)
            else:
                cleaned = re.sub(pattern, replacement, cleaned, flags=re.IGNORECASE)
        
        # Remove any remaining special characters that might cause issues
        cleaned = re.sub(r'[^\w\s.,!?;:-]', '', cleaned)
        
        # Normalize whitespace
        cleaned = re.sub(r'\s+', ' ', cleaned).strip()
        
        # Truncate if too long to prevent ONNX errors
        if len(cleaned) > max_length:
            # Try to truncate at sentence boundary
            sentences = cleaned.split('.')
            truncated = ""
            for sentence in sentences:
                if len(truncated + sentence + '.') <= max_length:
                    truncated += sentence + '.'
                else:
                    break
            
            if truncated:
                cleaned = truncated.rstrip('.')
            else:
                # If no good sentence break, just truncate
                cleaned = cleaned[:max_length].rstrip()
            
            # Add indication that text was truncated
            cleaned += "..."
        
        return cleaned
    
    def is_voice_suitable(self, text: str) -> bool:
        """Check if text is suitable for voice synthesis"""
        if not text or len(text.strip()) < 3:
            return False
        
        # Check for mostly technical content
        technical_ratio = len(re.findall(r'[{}[\]()<>@#$%^&*+=|/_-]', text)) / max(len(text), 1)
        if technical_ratio > 0.3:  # More than 30% special chars
            return False
        
        return True


# Convenience function for easy integration
def preprocess_for_voice_synthesis(text: str, max_length: int = 200) -> str:
    """Preprocess text for voice synthesis"""
    preprocessor = VoiceTextPreprocessor()
    return preprocessor.preprocess_for_voice(text, max_length)


if __name__ == "__main__":
    # Test the preprocessor
    preprocessor = VoiceTextPreprocessor()
    
    test_cases = [
        "Battery is at 75.5% and CPU is running at 45%",
        "System resolution: 1920x1080, running at 60fps",
        "Voice synthesis is ready! Available at http://localhost:8080",
        "Memory usage: 1.2GB RAM, 500MB SSD, CO2 saved: 14g",
        "File located at /Users/name/Documents/file.txt",
        "Temperature: 45.7°C, 2.5kWh saved today",
        "Hello! How can I help you today?",
        "The answer is 42.0% correct according to the analysis.",
    ]
    
    print("Testing Voice Text Preprocessor:\n")
    
    for i, text in enumerate(test_cases, 1):
        print(f"Test {i}:")
        print(f"Original:  {repr(text)}")
        processed = preprocessor.preprocess_for_voice(text)
        print(f"Processed: {repr(processed)}")
        print(f"Suitable:  {preprocessor.is_voice_suitable(processed)}")
        print()