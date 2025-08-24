#!/usr/bin/env python3
"""
SSML Converter for M1K3 Voice System
Converts SSML-like markup to KittenTTS-friendly text with appropriate timing and effects
"""

import re
import logging
from typing import Dict, List, Tuple, Optional
from dataclasses import dataclass

# Configure logging for SSML conversion
logger = logging.getLogger('ssml_converter')
logger.setLevel(logging.INFO)

@dataclass
class SSMLElement:
    """Represents an SSML element with its properties"""
    tag: str
    content: str
    attributes: Dict[str, str]
    start_pos: int
    end_pos: int

@dataclass
class ConversionResult:
    """Result of SSML conversion"""
    converted_text: str
    timing_adjustments: List[Dict]  # For pause insertions, etc.
    voice_effects: List[Dict]       # For emphasis, prosody changes
    success: bool
    warnings: List[str]

class SSMLConverter:
    """Converts SSML-like markup to KittenTTS-compatible format"""
    
    def __init__(self):
        self.supported_tags = {
            'break': self._convert_break,
            'pause': self._convert_break,  # Alias for break
            'emphasis': self._convert_emphasis, 
            'prosody': self._convert_prosody,
            'speak': self._convert_speak,
            'phoneme': self._convert_phoneme,
        }
        
    def convert(self, text: str, preserve_timing: bool = True) -> ConversionResult:
        """
        Convert SSML-like markup to KittenTTS-friendly text
        
        Args:
            text: Input text with SSML-like markup
            preserve_timing: Whether to preserve timing information for later use
            
        Returns:
            ConversionResult with converted text and metadata
        """
        logger.info(f"Converting SSML markup in text: {text[:50]}...")
        
        result = ConversionResult(
            converted_text=text,
            timing_adjustments=[],
            voice_effects=[],
            success=True,
            warnings=[]
        )
        
        # Use iterative approach to handle nested tags properly
        converted_text = text
        max_iterations = 10  # Prevent infinite loops
        iteration = 0
        
        while iteration < max_iterations:
            # Find all SSML elements in current text
            elements = self._parse_ssml_elements(converted_text)
            
            if not elements:
                break  # No more elements to process
                
            logger.info(f"Iteration {iteration + 1}: Found {len(elements)} SSML elements to process")
            
            # Process elements in reverse order to maintain positions
            text_modified = False
            for element in reversed(elements):
                try:
                    # Convert the element using appropriate handler
                    if element.tag in self.supported_tags:
                        handler = self.supported_tags[element.tag]
                        converted_part, metadata = handler(element, preserve_timing)
                        
                        # Replace in text
                        converted_text = (
                            converted_text[:element.start_pos] + 
                            converted_part + 
                            converted_text[element.end_pos:]
                        )
                        
                        text_modified = True
                        
                        # Store metadata
                        if metadata.get('timing'):
                            result.timing_adjustments.append(metadata['timing'])
                        if metadata.get('voice_effect'):
                            result.voice_effects.append(metadata['voice_effect'])
                            
                        logger.debug(f"Converted {element.tag}: '{element.content}' -> '{converted_part}'")
                        
                    else:
                        # Unsupported tag - remove but warn
                        logger.warning(f"Unsupported SSML tag: {element.tag}")
                        result.warnings.append(f"Unsupported tag: {element.tag}")
                        
                        # Just extract the content
                        converted_text = (
                            converted_text[:element.start_pos] + 
                            element.content + 
                            converted_text[element.end_pos:]
                        )
                        text_modified = True
                        
                except Exception as e:
                    logger.error(f"Error converting SSML element {element.tag}: {e}")
                    result.warnings.append(f"Error processing {element.tag}: {e}")
                    result.success = False
            
            if not text_modified:
                break  # No changes made, we're done
                
            iteration += 1
        
        result.converted_text = converted_text
        logger.info(f"SSML conversion complete after {iteration} iterations. Output: {result.converted_text[:50]}...")
        return result
    
    def _parse_ssml_elements(self, text: str) -> List[SSMLElement]:
        """Parse SSML elements from text"""
        elements = []
        
        # Pattern to match self-closing SSML tags: <tag attr="value"/>
        self_closing_pattern = r'<(\w+)([^>]*?)\s*/>'
        
        # Pattern to match paired SSML tags: <tag attr="value">content</tag>
        paired_pattern = r'<(\w+)([^>]*?)>(.*?)</\1>'
        
        # Find self-closing tags first
        for match in re.finditer(self_closing_pattern, text, re.DOTALL):
            tag_name = match.group(1).lower()
            attributes_str = match.group(2).strip()
            
            # Parse attributes
            attributes = self._parse_attributes(attributes_str)
            
            elements.append(SSMLElement(
                tag=tag_name,
                content="",  # Self-closing tags have no content
                attributes=attributes,
                start_pos=match.start(),
                end_pos=match.end()
            ))
        
        # Find paired tags
        for match in re.finditer(paired_pattern, text, re.DOTALL):
            tag_name = match.group(1).lower()
            attributes_str = match.group(2).strip()
            content = match.group(3)
            
            # Parse attributes
            attributes = self._parse_attributes(attributes_str)
            
            elements.append(SSMLElement(
                tag=tag_name,
                content=content,
                attributes=attributes,
                start_pos=match.start(),
                end_pos=match.end()
            ))
            
        # Sort by start position so we process in order (but reverse later for replacement)
        elements.sort(key=lambda e: e.start_pos)
        
        return elements
    
    def _parse_attributes(self, attr_str: str) -> Dict[str, str]:
        """Parse SSML attributes from attribute string"""
        attributes = {}
        
        # Pattern to match key="value" or key='value'
        attr_pattern = r'(\w+)=(["\'])([^"\']*?)\2'
        
        for match in re.finditer(attr_pattern, attr_str):
            key = match.group(1).lower()
            value = match.group(3)
            attributes[key] = value
            
        return attributes
    
    def _convert_break(self, element: SSMLElement, preserve_timing: bool) -> Tuple[str, Dict]:
        """Convert <break> or <pause> tags to appropriate pauses"""
        time_value = element.attributes.get('time', '500ms')
        strength = element.attributes.get('strength', 'medium')
        
        # Convert time specifications to pause duration
        pause_duration = self._parse_time_value(time_value)
        
        if preserve_timing:
            timing_info = {
                'type': 'pause',
                'duration_ms': pause_duration,
                'position': element.start_pos
            }
        else:
            timing_info = {}
        
        # For KittenTTS, we'll represent pauses as periods or commas
        if pause_duration < 200:
            pause_text = ","  # Short pause
        elif pause_duration < 500:
            pause_text = "."  # Medium pause
        else:
            pause_text = "..."  # Long pause
            
        logger.debug(f"Break converted: {time_value} -> '{pause_text}' ({pause_duration}ms)")
        
        return pause_text, {'timing': timing_info}
    
    def _convert_emphasis(self, element: SSMLElement, preserve_timing: bool) -> Tuple[str, Dict]:
        """Convert <emphasis> tags to emphasized text"""
        level = element.attributes.get('level', 'moderate')
        
        content = element.content
        
        # Apply emphasis based on level
        if level == 'strong':
            # Use caps for strong emphasis
            emphasized_text = content.upper()
        elif level == 'moderate':
            # Use title case for moderate emphasis
            emphasized_text = content.title()
        else:  # reduced
            # Use lowercase for reduced emphasis
            emphasized_text = content.lower()
        
        voice_effect = {
            'type': 'emphasis',
            'level': level,
            'original': content,
            'position': element.start_pos
        } if preserve_timing else {}
        
        logger.debug(f"Emphasis converted: '{content}' -> '{emphasized_text}' (level: {level})")
        
        return emphasized_text, {'voice_effect': voice_effect}
    
    def _convert_prosody(self, element: SSMLElement, preserve_timing: bool) -> Tuple[str, Dict]:
        """Convert <prosody> tags for rate, pitch, volume changes"""
        rate = element.attributes.get('rate', 'medium')
        pitch = element.attributes.get('pitch', 'medium') 
        volume = element.attributes.get('volume', 'medium')
        
        content = element.content
        
        # For KittenTTS, we can't directly control prosody, but we can adjust text
        processed_content = content
        
        # Rate adjustments - add punctuation for pacing
        if rate == 'slow' or rate.endswith('slow'):
            # Add commas for slower speech
            processed_content = processed_content.replace(' ', ', ')
        elif rate == 'fast' or rate.endswith('fast'):
            # Remove some punctuation for faster speech  
            processed_content = processed_content.replace(',', '')
        
        voice_effect = {
            'type': 'prosody',
            'rate': rate,
            'pitch': pitch, 
            'volume': volume,
            'position': element.start_pos
        } if preserve_timing else {}
        
        logger.debug(f"Prosody converted: '{content}' -> '{processed_content}' (rate: {rate})")
        
        return processed_content, {'voice_effect': voice_effect}
    
    def _convert_speak(self, element: SSMLElement, preserve_timing: bool) -> Tuple[str, Dict]:
        """Convert <speak> root tags (just extract content)"""
        logger.debug(f"Speak tag processed: '{element.content}'")
        return element.content, {}
    
    def _convert_phoneme(self, element: SSMLElement, preserve_timing: bool) -> Tuple[str, Dict]:
        """Convert <phoneme> tags (extract content, phonemes not supported by KittenTTS)"""
        alphabet = element.attributes.get('alphabet', 'ipa')
        ph = element.attributes.get('ph', '')
        
        logger.warning(f"Phoneme markup not supported by KittenTTS: {ph} -> using original text")
        
        return element.content, {
            'voice_effect': {
                'type': 'phoneme_unsupported',
                'phonemes': ph,
                'alphabet': alphabet,
                'position': element.start_pos
            }
        }
    
    def _parse_time_value(self, time_str: str) -> int:
        """Parse time values like '500ms', '2s', '1.5s' to milliseconds"""
        try:
            if time_str.endswith('ms'):
                return int(float(time_str[:-2]))
            elif time_str.endswith('s'):
                return int(float(time_str[:-1]) * 1000)
            else:
                # Assume milliseconds if no unit
                return int(float(time_str))
        except (ValueError, TypeError):
            logger.warning(f"Could not parse time value: {time_str}, using default 500ms")
            return 500

# Convenience function
def convert_ssml_to_text(text: str, preserve_timing: bool = True) -> ConversionResult:
    """Convert SSML markup to plain text suitable for KittenTTS"""
    converter = SSMLConverter()
    return converter.convert(text, preserve_timing)

if __name__ == "__main__":
    # Test the SSML converter
    converter = SSMLConverter()
    
    test_cases = [
        "Hello <break time='500ms'/> there!",
        "This is <emphasis level='strong'>very important</emphasis>!",
        "<prosody rate='slow'>Please speak slowly</prosody>",
        "<speak>Hello <break time='300ms'/> world!</speak>",
        "Complex: <emphasis>important</emphasis> <break time='1s'/> <prosody rate='fast'>quick text</prosody>",
        "Unsupported: <voice name='alice'>Hello</voice>",
        "Mixed: Hello *pauses* <break time='200ms'/> continuing...",
    ]
    
    print("Testing SSML Converter:")
    print("=" * 60)
    
    for i, test_case in enumerate(test_cases, 1):
        print(f"\nTest {i}: {test_case}")
        result = converter.convert(test_case)
        print(f"Result: {result.converted_text}")
        print(f"Success: {result.success}")
        if result.timing_adjustments:
            print(f"Timing: {len(result.timing_adjustments)} adjustments")
        if result.voice_effects:
            print(f"Effects: {len(result.voice_effects)} effects")
        if result.warnings:
            print(f"Warnings: {result.warnings}")