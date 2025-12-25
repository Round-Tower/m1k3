# 🎭 VibeVoice Demo & Showcase Guide

Welcome to the comprehensive VibeVoice demonstration suite for M1K3! This guide covers all the demos and showcases available to explore Microsoft's frontier text-to-speech integration.

## 🚀 Quick Start

### Run the Main Showcase
```bash
python vibevoice_showcase.py
```
Interactive demonstration with 6 comprehensive demos covering all VibeVoice capabilities.

### Run the CLI Demo
```bash
python vibevoice_cli_demo.py
```
Command-line interface demonstration showing CLI options and usage patterns.

### Run Basic Tests
```bash
python test_vibevoice.py
```
System compatibility and basic functionality tests.

## 🎪 Demo Suite Overview

### 1. 🎤 Main Showcase (`vibevoice_showcase.py`)
**Comprehensive technical demonstration of all VibeVoice capabilities**

**Features:**
- **Basic Synthesis Demo**: Test core TTS generation
- **Multi-Speaker Conversations**: Up to 4 simultaneous speakers
- **Long-Form Narratives**: 90-minute continuous synthesis
- **Voice Profile Showcase**: Compare different voice profiles  
- **Streaming Engine Demo**: Real-time synthesis capabilities
- **Technical Performance**: Benchmarking and metrics

**Interactive Menu:**
```
🎭 VibeVoice Interactive Demo Menu
════════════════════════════════════════════════════════════
1. 🎤 Basic Synthesis Demo
2. 👥 Multi-Speaker Conversations  
3. 📚 Long-Form Narratives
4. 🎨 Voice Profile Showcase
5. 🚀 Streaming Engine Demo
6. ⚡ Technical Performance Test
7. 🎯 Run All Demos (Full Showcase)
8. 📁 Open Demo Output Folder
9. 🧹 Clean Demo Files
0. 🚪 Exit
```

### 2. 🚀 CLI Demo (`vibevoice_cli_demo.py`)
**Command-line interface demonstration and tutorial**

**Features:**
- **CLI Options Overview**: All available command-line flags
- **Usage Examples**: Real command demonstrations
- **Interactive Commands**: In-CLI command examples
- **Voice Profile Guide**: Profile switching demonstrations
- **Real-World Scenarios**: Practical use cases

**Demo Categories:**
- Basic CLI usage with VibeVoice
- Multi-speaker conversation setup
- Long-form content generation
- Voice profile switching
- Interactive command examples
- Real-world application scenarios

### 3. 🧪 System Test (`test_vibevoice.py`)
**Compatibility and functionality verification**

**Test Coverage:**
- System requirements validation
- Dependency availability checking
- Repository and model verification
- Basic functionality testing
- Performance recommendations

## 🎯 Voice Profiles Showcase

### KittenTTS Profiles (Fast, Lightweight)
- **`natural`**: Default conversational voice with light effects
- **`assistant`**: Professional AI assistant tone
- **`broadcast`**: Clear announcer-style voice  
- **`terminal`**: Technical system voice
- **`debug`**: Minimal processing for speed
- **`minimal`**: Basic synthesis only

### VibeVoice Profiles (Advanced, High-Quality)
- **`conversational`**: Multi-speaker dialogue (2-4 speakers)
- **`narrative`**: Long-form storytelling (up to 90 minutes)
- **`assistant_duo`**: AI assistant with user voice simulation

## 🎬 Demo Content Library

### Multi-Speaker Conversations
The demos include realistic conversation scenarios:
- **Technical Discussion**: Alice and Bob discuss VibeVoice capabilities
- **Introduction Dialogue**: Natural conversation flow demonstration
- **Educational Content**: Teacher-student interaction examples

### Long-Form Narratives  
Extended content demonstrations:
- **Sci-Fi Story**: Futuristic narrative about AI voice technology
- **Technical Overview**: Comprehensive explanation of VibeVoice architecture
- **Custom Content**: Support for user-provided long-form text

### Performance Benchmarks
Technical capability demonstrations:
- **Speed Tests**: Generation time vs. audio duration
- **Quality Metrics**: Audio clarity and naturalness
- **Resource Usage**: Memory and CPU utilization
- **Scalability**: Performance across different content lengths

## 🛠️ Technical Demonstrations

### 1. Basic Synthesis Capabilities
```python
# Generate basic audio samples
test_phrases = [
    "Welcome to VibeVoice technology!",
    "90 minutes of continuous speech synthesis",
    "Multi-speaker conversation capabilities"
]
```

### 2. Multi-Speaker Conversations
```python
# Set up conversation participants
speakers = ["Alice", "Bob", "Carol", "Dave"]
conversation = {
    "Alice": "Let's discuss VibeVoice capabilities...",
    "Bob": "The technology is remarkable...",
    "Carol": "I agree, the quality is outstanding..."
}
```

### 3. Long-Form Generation
```python
# Process extended content
long_content = """Extended narrative content that demonstrates
VibeVoice's ability to maintain coherence and quality
across very long text passages..."""

# Generate with continuous synthesis
audio_chunks = vibevoice.generate_long_form(long_content)
```

### 4. Streaming Synthesis
```python
# Real-time processing demonstration
streaming_engine = StreamingTTSEngine(voice_engine)
streaming_engine.enable_vibevoice_mode(continuous=True)
streaming_engine.process_long_form_content(content)
```

## 🎨 Output Examples

### Generated Demo Files
When you run the demos, files are saved to `vibevoice_demos/`:

```
vibevoice_demos/
├── basic_sample_1.wav              # Basic synthesis samples
├── basic_sample_2.wav
├── basic_sample_3.wav
├── conversation_alice.wav          # Multi-speaker conversation
├── conversation_bob.wav
├── conversation_script.txt         # Conversation transcript
├── narrative_chunk_1.wav           # Long-form narrative chunks
├── narrative_chunk_2.wav
├── narrative_text.txt              # Narrative source text
├── profile_conversational.wav      # Voice profile samples
├── profile_narrative.wav
├── profile_assistant_duo.wav
├── performance_test_1.wav          # Performance benchmarks
├── performance_test_2.wav
├── performance_test_3.wav
├── performance_results.json        # Benchmark data
└── streaming_info.json             # Streaming engine stats
```

## 🎯 CLI Usage Examples

### Basic VibeVoice Usage
```bash
# Use VibeVoice as TTS engine
python cli.py --tts-engine vibevoice

# Single query with VibeVoice
python cli.py --tts-engine vibevoice --query "Explain VibeVoice technology"
```

### Multi-Speaker Conversations
```bash
# Enable multi-speaker mode
python cli.py --tts-engine vibevoice --multi-speaker --speakers Alice Bob

# Use conversational profile
python cli.py --tts-engine vibevoice --voice-profile conversational
```

### Long-Form Content
```bash
# Enable continuous synthesis (90 minutes)
python cli.py --tts-engine vibevoice --continuous-mode

# Use narrative profile for storytelling
python cli.py --tts-engine vibevoice --voice-profile narrative --continuous-mode
```

### Advanced Options
```bash
# Use 7B model variant
python cli.py --tts-engine vibevoice --vibevoice-model 7B

# Combine multiple options
python cli.py --tts-engine vibevoice --multi-speaker --speakers Alice Bob Carol --voice-profile conversational --continuous-mode
```

## 🎮 Interactive Commands

### In-CLI Commands (use inside running CLI)
```bash
# VibeVoice management
/vibevoice status                    # Check availability and status
/vibevoice speakers Alice Bob        # Set conversation speakers  
/vibevoice model 7B                  # Switch to 7B model
/vibevoice continuous                # Enable 90-minute mode

# Engine and profile switching
/tts engine vibevoice                # Switch TTS engine
/profile conversational              # Change voice profile
/profile narrative                   # Switch to narrative mode

# System information
/tts status                          # Show TTS engine status
help                                 # Show all available commands
```

## 🌍 Real-World Use Cases

### 1. Content Creation
**Scenario**: Podcast or audiobook production
```bash
python cli.py --tts-engine vibevoice --continuous-mode --voice-profile narrative
```
**Benefits**: Hours of consistent, natural narration

### 2. Conversational AI Testing
**Scenario**: Multi-user chatbot simulation
```bash
python cli.py --tts-engine vibevoice --multi-speaker --voice-profile conversational
```
**Benefits**: Realistic conversation simulation for testing

### 3. Educational Content
**Scenario**: Interactive learning materials
```bash
python cli.py --tts-engine vibevoice --speakers Teacher Student --voice-profile assistant_duo
```
**Benefits**: Natural teacher-student dialogue generation

### 4. Accessibility Applications
**Scenario**: High-quality screen reading
```bash
python cli.py --tts-engine vibevoice --voice-profile natural
```
**Benefits**: Superior voice quality for accessibility tools

## 🔧 System Requirements

### Optimal Setup
- **Hardware**: NVIDIA GPU with 4GB+ VRAM
- **Memory**: 8GB+ RAM (16GB+ for 7B model)
- **Storage**: ~10GB for models and dependencies
- **Performance**: Real-time generation with GPU acceleration

### Minimum Setup  
- **Hardware**: Modern CPU with 8GB+ RAM
- **Performance**: Slower generation, suitable for short content
- **Fallback**: Automatic degradation to KittenTTS if needed

### Dependencies
- **Core**: PyTorch, Transformers, librosa, soundfile
- **Optional**: diffusers (version compatibility issues noted)
- **Repository**: VibeVoice cloned to ~/VibeVoice

## 🐛 Troubleshooting

### Common Issues

**1. Repository Not Found**
```bash
ln -sf ~/VibeVoic ~/VibeVoice  # Create symlink if needed
```

**2. Diffusers Compatibility**
```
Issue: torch.library custom_op error
Status: Non-blocking - core functionality works without diffusers
```

**3. CUDA Not Available**
```
Status: Expected on CPU systems - will use CPU mode (slower)
Impact: Fully functional, just slower generation
```

**4. Model Loading Slow**
```
Cause: First-time model download from Hugging Face
Solution: Be patient, models are cached after first download
```

## 📊 Performance Metrics

### Benchmark Results (Typical)
- **Generation Speed**: 3200x real-time (GPU) / 10-50x (CPU)
- **Model Loading**: 1-3 seconds (cached models)  
- **Memory Usage**: 4GB (1.5B model) / 16GB (7B model)
- **Audio Quality**: Professional-grade, natural prosody
- **Continuous Length**: Up to 90 minutes (1.5B) / 45 minutes (7B)

## 🎉 Demo Highlights

### What Makes These Demos Special
1. **Comprehensive Coverage**: Every VibeVoice feature demonstrated
2. **Interactive Experience**: Menu-driven, user-friendly interface
3. **Real Audio Output**: Actual generated audio files you can play
4. **Performance Metrics**: Real benchmarking data
5. **CLI Integration**: Shows both API and command-line usage
6. **Educational Content**: Learn while exploring capabilities

### Demo Innovation
- **Multi-Modal**: Both programmatic API and CLI demonstrations
- **Progressive Complexity**: From basic to advanced features
- **Real-World Context**: Practical use case scenarios
- **Performance Transparency**: Actual benchmark measurements
- **Output Preservation**: All generated content saved for review

## 🚀 Getting Started

1. **Run System Check**:
   ```bash
   python test_vibevoice.py
   ```

2. **Start with Main Showcase**:
   ```bash
   python vibevoice_showcase.py
   ```

3. **Try CLI Demo**:
   ```bash
   python vibevoice_cli_demo.py
   ```

4. **Explore Real Usage**:
   ```bash
   python cli.py --tts-engine vibevoice --voice-profile conversational
   ```

## 📚 Additional Resources

- **Main Documentation**: See `CLAUDE.md` for complete VibeVoice integration details
- **Setup Guide**: `VIBEVOICE_SETUP.md` for installation instructions
- **Docker Support**: `setup_vibevoice_docker.sh` for containerized environment
- **Technical Deep-Dive**: Source code in `src/tts/controllers/vibevoice_manager.py`

---

**🎭 Enjoy exploring the frontier of text-to-speech technology with VibeVoice and M1K3!**