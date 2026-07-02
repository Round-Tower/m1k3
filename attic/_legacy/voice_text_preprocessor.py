#!/usr/bin/env python3
"""
M1K3 Voice Text Preprocessor
Cleans text for voice synthesis to prevent phonemizer warnings
"""

import re
import logging

# Configure logging for voice preprocessing
logger = logging.getLogger('voice_preprocessor')
logger.setLevel(logging.INFO)


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
    
    def preprocess_for_voice(self, text: str, max_length: int = 200, 
                           preserve_narration: bool = False,
                           narration_mode: str = "remove") -> str:
        """
        Preprocess text to make it voice-synthesis friendly
        
        Args:
            text: Input text to preprocess
            max_length: Maximum length for voice synthesis (to prevent ONNX errors)
            preserve_narration: If True, keep narration markers intact
            narration_mode: How to handle narration - "remove", "pause", "preserve"
        
        Returns:
            Preprocessed text safe for voice synthesis
        """
        if not text or not text.strip():
            return ""
        
        # Start with cleaned text
        cleaned = text.strip()
        
        # Handle narration markers before other preprocessing
        cleaned = self._handle_narration(cleaned, preserve_narration, narration_mode)
        
        # Apply all preprocessing patterns (excluding asterisks if preserving narration)
        patterns_to_use = self.problematic_patterns
        if preserve_narration or narration_mode == "preserve":
            # Skip the pattern that removes brackets and symbols containing asterisks
            patterns_to_use = [(p, r) for p, r in self.problematic_patterns 
                             if not (isinstance(p, str) and '*' in p)]
        
        for pattern, replacement in patterns_to_use:
            if callable(replacement):
                cleaned = re.sub(pattern, replacement, cleaned, flags=re.IGNORECASE)
            else:
                cleaned = re.sub(pattern, replacement, cleaned, flags=re.IGNORECASE)
        
        # Remove any remaining special characters that might cause issues
        # But preserve asterisks if we're in preserve mode
        if preserve_narration or narration_mode == "preserve":
            # Remove special chars but keep asterisks
            cleaned = re.sub(r'[^\w\s.,!?;:*-]', '', cleaned)
        else:
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
    
    def _handle_narration(self, text: str, preserve_narration: bool, narration_mode: str) -> str:
        """
        Handle narration markers based on configuration
        
        Args:
            text: Input text with potential narration markers
            preserve_narration: Legacy parameter for backward compatibility
            narration_mode: "remove", "pause", "preserve"
            
        Returns:
            Text with narration handled according to mode
        """
        # Find all narration segments
        narration_pattern = r'\*(.*?)\*'
        narration_matches = list(re.finditer(narration_pattern, text, re.DOTALL))
        
        if not narration_matches:
            return text  # No narration found
        
        # Log narration detection
        logger.info(f"Found {len(narration_matches)} narration segments in text")
        
        # Handle based on mode
        if preserve_narration or narration_mode == "preserve":
            logger.info("Preserving narration markers intact")
            return text
            
        elif narration_mode == "pause":
            logger.info("Converting narration to pause markers")
            # Replace narration with pause indicators (for future SSML processing)
            result = text
            for match in reversed(narration_matches):  # Reverse to maintain positions
                narration_content = match.group(1).strip()
                logger.debug(f"Converting narration to pause: '{narration_content}'")
                # For now, replace with a pause marker that could be processed later
                result = result[:match.start()] + " <pause/> " + result[match.end():]
            return result
            
        elif narration_mode == "remove":
            logger.info("Removing narration markers and content")
            # Remove narration entirely but preserve spacing
            result = text
            for match in reversed(narration_matches):  # Reverse to maintain positions
                narration_content = match.group(1).strip()
                logger.debug(f"Removing narration: '{narration_content}'")
                # Replace with appropriate spacing
                before_space = " " if match.start() > 0 and text[match.start()-1] != " " else ""
                after_space = " " if match.end() < len(text) and text[match.end()] != " " else ""
                result = result[:match.start()] + before_space + after_space + result[match.end():]
            
            # Clean up extra whitespace
            result = re.sub(r'\s+', ' ', result).strip()
            return result
        
        else:
            logger.warning(f"Unknown narration_mode: {narration_mode}, defaulting to remove")
            return self._handle_narration(text, preserve_narration, "remove")


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