# 🎭 Intelligent TTS System - Technical Documentation

## Overview

The M1K3 Intelligent TTS (Text-to-Speech) system is an advanced content-aware voice synthesis solution that automatically adapts voice characteristics based on the type of content being spoken. This system provides a dramatically enhanced user experience by making AI responses sound more natural and contextually appropriate.

## Table of Contents

- [System Architecture](#system-architecture)
- [Content Types](#content-types)
- [Voice Modulation](#voice-modulation)
- [Integration](#integration)
- [API Reference](#api-reference)
- [Configuration](#configuration)
- [Testing](#testing)
- [Performance](#performance)
- [Troubleshooting](#troubleshooting)

## System Architecture

### Core Components

```
┌─────────────────────┐
│   AI Response      │
└─────────┬───────────┘
          │
┌─────────▼───────────┐
│ Model Output Parser │ ← Automatic content recognition
│ (ContentType enum)  │
└─────────┬───────────┘
          │
┌─────────▼───────────┐
│ Intelligent TTS     │ ← Priority-based job queuing
│ Controller          │
└─────────┬───────────┘
          │
┌─────────▼───────────┐
│ Content-Specific    │ ← Voice modulation per type
│ Effects Manager     │
└─────────┬───────────┘
          │
┌─────────▼───────────┐
│ Enhanced Voice      │ ← KittenML TTS synthesis
│ Engine              │
└─────────────────────┘
```

### Key Classes

- **`IntelligentTTSController`**: Orchestrates the entire TTS process
- **`ModelOutputParser`**: Parses AI responses into content segments
- **`ContentEffectsManager`**: Manages voice effects per content type
- **`ContentTypeModulation`**: Stores voice settings for each content type

## Content Types

### 🤔 THINKING

**Purpose**: Represents internal thought processes, deliberation, and analysis.

**Recognition Patterns**:
- `<thinking>...</thinking>` blocks
- Contemplative language patterns

**Voice Characteristics**:
- Volume: 0.8x (softer)
- Speed: 0.8x (slower, more deliberate)
- Pitch: -0.1 (slightly lower)
- Reverb: 0.2 (subtle echo for depth)
- Warmth: 0.0 (neutral)

**Example**:
```xml
<thinking>
Let me consider all the angles here. This is a complex problem that requires careful analysis.
</thinking>
```

### 📖 NARRATION

**Purpose**: Descriptive text, scene setting, and storytelling elements.

**Recognition Patterns**:
- Text enclosed in asterisks: `*action or description*`
- Descriptive language patterns

**Voice Characteristics**:
- Volume: 1.0x (normal)
- Speed: 1.1x (slightly faster for storytelling flow)
- Pitch: +0.1 (slightly higher for expressiveness)
- Reverb: 0.0 (clear)
- Warmth: 0.3 (warm, engaging)

**Example**:
```text
*The user leans forward with interest, clearly engaged in the conversation.*
```

### 💡 ANSWER

**Purpose**: Direct responses, explanations, and factual information.

**Recognition Patterns**:
- Default content type (fallback)
- Declarative statements
- Informational content

**Voice Characteristics**:
- Volume: 1.0x (normal)
- Speed: 1.0x (standard)
- Pitch: 0.0 (neutral)
- Reverb: 0.0 (clear)
- Warmth: 0.0 (neutral, authoritative)

**Example**:
```text
The solution involves implementing a modular architecture that separates concerns effectively.
```

### ❓ CLARIFICATION

**Purpose**: Questions, requests for clarification, and interactive prompts.

**Recognition Patterns**:
- Questions ending with `?`
- Clarification keywords: "Could you", "Would you", "Can you", etc.
- Interactive prompts

**Voice Characteristics**:
- Volume: 1.0x (normal)
- Speed: 0.9x (slightly slower for clarity)
- Pitch: +0.15 (rising intonation)
- Reverb: 0.0 (clear)
- Warmth: 0.1 (slightly warm, helpful)

**Example**:
```text
Could you provide more details about your specific requirements?
```

## Voice Modulation

### Modulation Parameters

Each content type has a `ContentTypeModulation` configuration:

```python
@dataclass
class ContentTypeModulation:
    volume_multiplier: float = 1.0    # 0.1 - 2.0
    speed_multiplier: float = 1.0     # 0.5 - 2.0
    pitch_adjustment: float = 0.0     # -0.5 - 0.5
    reverb_amount: float = 0.0        # 0.0 - 0.4
    warmth_factor: float = 0.0        # 0.0 - 0.5
```

### Parameter Ranges and Validation

- **Volume Multiplier**: Controls overall loudness (0.1 = very quiet, 2.0 = very loud)
- **Speed Multiplier**: Controls speech rate (0.5 = half speed, 2.0 = double speed)
- **Pitch Adjustment**: Controls voice pitch (-0.5 = much lower, 0.5 = much higher)
- **Reverb Amount**: Controls echo effect (0.0 = none, 0.4 = heavy reverb)
- **Warmth Factor**: Controls voice warmth/richness (0.0 = neutral, 0.5 = very warm)

### Inter-Segment Pauses

Different content types have different pause durations for natural flow:

```python
pause_durations = {
    ContentType.THINKING: 0.8,      # Longer pause after thinking
    ContentType.NARRATION: 0.6,     # Medium pause after narration
    ContentType.ANSWER: 0.4,        # Short pause after answers
    ContentType.CLARIFICATION: 0.2  # Very short pause before questions
}
```

## Integration

### CLI Integration

The intelligent TTS system is fully integrated into the M1K3 CLI:

```python
# In cli.py
def _safe_voice_synthesis(self, text: str, background: bool = True, use_intelligent_tts: bool = False):
    # Use intelligent TTS for AI responses
    if use_intelligent_tts and self.intelligent_tts_controller:
        results = self.intelligent_tts_controller.process_text_with_parsing(text)
        # Falls back to basic synthesis if needed
```

### Automatic Processing

All AI responses automatically use intelligent TTS:

```python
# AI responses use intelligent TTS
self._safe_voice_synthesis(full_response.strip(), use_intelligent_tts=True)

# System messages use basic synthesis
self._safe_voice_synthesis("System message", use_intelligent_tts=False)
```

## API Reference

### IntelligentTTSController

#### Constructor

```python
def __init__(self, voice_engine=None):
    """Initialize with optional voice engine"""
```

#### Methods

```python
def queue_content(self, parsed_content: ParsedContent) -> List[TTSJob]:
    """Queue parsed content for TTS processing"""

def process_text_with_parsing(self, text: str, parser=None) -> List[TTSProcessingResult]:
    """Parse text and process with TTS"""

def set_modulation(self, content_type: ContentType, modulation: ContentTypeModulation):
    """Set voice modulation for specific content type"""

def get_status(self) -> Dict[str, Any]:
    """Get current controller status"""

def clear_queue(self):
    """Clear all queued TTS jobs"""
```

### ModelOutputParser

```python
def parse_model_output(text: str) -> ParsedContent:
    """Parse text into content segments with types"""
```

### ContentEffectsManager

```python
def apply_content_effect(self, audio: np.ndarray, content_type: ContentType, 
                        sample_rate: int, modulation: ContentTypeModulation = None) -> np.ndarray:
    """Apply content-specific audio effects"""
```

## Configuration

### Default Modulations

```python
default_modulations = {
    ContentType.THINKING: ContentTypeModulation(
        volume_multiplier=0.8,
        speed_multiplier=0.85,
        pitch_adjustment=-0.1,
        reverb_amount=0.2,
        warmth_factor=0.0
    ),
    ContentType.NARRATION: ContentTypeModulation(
        volume_multiplier=1.0,
        speed_multiplier=1.1,
        pitch_adjustment=0.05,
        reverb_amount=0.0,
        warmth_factor=0.3
    ),
    ContentType.ANSWER: ContentTypeModulation(
        volume_multiplier=1.0,
        speed_multiplier=1.0,
        pitch_adjustment=0.0,
        reverb_amount=0.0,
        warmth_factor=0.0
    ),
    ContentType.CLARIFICATION: ContentTypeModulation(
        volume_multiplier=1.0,
        speed_multiplier=0.95,
        pitch_adjustment=0.15,
        reverb_amount=0.0,
        warmth_factor=0.1
    )
}
```

### Custom Configuration

```python
# Create custom modulation
custom_modulation = ContentTypeModulation(
    volume_multiplier=1.2,
    speed_multiplier=0.9,
    pitch_adjustment=0.1
)

# Apply to controller
controller.set_modulation(ContentType.THINKING, custom_modulation)
```

## Testing

### Test Files

- **`tests/test_model_output_parser.py`**: Parser functionality (14 tests)
- **`tests/test_intelligent_tts_controller.py`**: Controller logic (19 tests)
- **`tests/test_content_specific_effects.py`**: Audio effects (20 tests)
- **`tests/audio_test_framework.py`**: Live audio testing framework
- **`test_tts_system.py`**: System integration verification
- **`test_cli_integration.py`**: CLI integration testing

### Running Tests

```bash
# Run all TTS tests
python -m pytest tests/test_*tts* -v

# Run individual test suites
python -m pytest tests/test_model_output_parser.py -v
python -m pytest tests/test_intelligent_tts_controller.py -v
python -m pytest tests/test_content_specific_effects.py -v

# System verification
python test_tts_system.py
python test_cli_integration.py
```

### Live Audio Testing

```bash
# Quick showcase with audio
python demos/demo_tts_quick_showcase.py

# Full showcase with sound effects
python demos/demo_tts_showcase_with_sfx.py

# Interactive TTS testing
python tests/test_tts_live_audio.py
```

## Performance

### Metrics

- **Content Parsing**: ~1-2ms for typical AI responses
- **Job Queuing**: ~0.1ms per segment
- **Voice Modulation**: ~10-50ms per segment (depending on length)
- **Total Overhead**: ~20-100ms per response (imperceptible to users)

### Memory Usage

- **Controller**: ~2MB base memory usage
- **Effects Manager**: ~1MB for audio processing
- **Per-Job**: ~1KB for job tracking
- **Total Impact**: Minimal (< 5MB total)

### Optimization Features

- **Priority-based processing**: Critical content (clarifications) processed first
- **Graceful fallback**: Automatic fallback to basic synthesis on errors
- **Efficient queuing**: Single-threaded processing with optimal ordering
- **Memory management**: Automatic cleanup of completed jobs

## Troubleshooting

### Common Issues

#### Intelligent TTS Not Available

```
⚠️ Intelligent TTS not available: [ImportError]
```

**Solution**: Ensure all TTS modules are present:
```bash
# Check files exist
ls -la intelligent_tts_controller.py
ls -la model_output_parser.py  
ls -la content_specific_effects.py
```

#### Voice Synthesis Fails

```
⚠️ Intelligent TTS failed, falling back to basic synthesis
```

**Solution**: Check voice engine status:
```bash
# In CLI
/tts status
```

#### No Audio Output

**Symptoms**: TTS processing completes but no sound
**Solution**: Check voice engine and audio device:
```bash
# Test voice engine directly
python -c "from enhanced_voice_engine import create_voice_engine; e=create_voice_engine(); print(e.load_model()); e.synthesize_and_play('test')"
```

### Debug Commands

```bash
# Show TTS system status
/tts status

# Check voice engine
voice

# Test basic synthesis  
python test_tts_system.py

# Test CLI integration
python test_cli_integration.py
```

### Log Messages

Key log messages to look for:

- `🎭 Intelligent TTS system with content-specific voice effects available` - System loaded
- `🎭 Intelligent TTS controller initialized with content-specific voice effects` - Controller ready
- `⚠️ Intelligent TTS not available: [error]` - System unavailable
- `⚠️ Intelligent TTS failed, falling back to basic synthesis` - Processing error

## Advanced Usage

### Extreme Customization

Create dramatic voice effects:

```python
# Whisper mode
whisper_mode = ContentTypeModulation(
    volume_multiplier=0.3,
    speed_multiplier=0.6,
    pitch_adjustment=-0.4,
    reverb_amount=0.4
)

# Dramatic narration
dramatic_mode = ContentTypeModulation(
    volume_multiplier=1.5,
    speed_multiplier=1.4,
    pitch_adjustment=0.2,
    warmth_factor=0.5
)

# Robot voice
robot_mode = ContentTypeModulation(
    volume_multiplier=1.2,
    speed_multiplier=0.8,
    pitch_adjustment=-0.3
)
```

### Content Skipping

Skip certain content types:

```python
# Skip thinking blocks for faster responses
controller.set_skip_content_types([ContentType.THINKING])
```

### Custom Content Recognition

Extend the parser for custom content types:

```python
# Add custom patterns to model_output_parser.py
custom_patterns = {
    'EMPHASIS': r'\*\*(.*?)\*\*',  # **emphasized text**
    'CODE': r'`(.*?)`',            # `code blocks`
}
```

## Future Enhancements

### Planned Features

- **Real-time audio effects**: Live audio processing with reverb, EQ
- **Voice cloning integration**: Custom voice models per content type  
- **Emotion-based modulation**: Voice changes based on avatar emotions
- **Multi-language support**: Content-aware TTS for multiple languages
- **SSML integration**: Speech Synthesis Markup Language support

### Contributing

The TTS system is designed to be extensible. Key areas for contribution:

1. **New Content Types**: Add recognition patterns and voice characteristics
2. **Audio Effects**: Implement additional voice modulation effects
3. **Voice Engines**: Add support for additional TTS backends
4. **Performance**: Optimize processing speed and memory usage
5. **Testing**: Expand test coverage and add new test scenarios

---

## Summary

The M1K3 Intelligent TTS system provides a sophisticated, content-aware voice synthesis solution that dramatically enhances the user experience. By automatically recognizing different types of content and adapting voice characteristics accordingly, it creates a more natural, engaging, and immersive interaction with the AI assistant.

The system is fully integrated into the M1K3 CLI, thoroughly tested, and designed for extensibility. It provides graceful fallback, comprehensive status monitoring, and maintains high performance while adding minimal overhead to the overall system.