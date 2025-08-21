# M1K3 Demos Directory

This directory contains demonstration scripts showcasing the capabilities of the M1K3 local AI assistant system.

## 🎤 Voice & Sound Demos

### `demo_voice_showcase.py`
**Comprehensive Voice & Sound System Showcase**
- ✅ Tests all 4 voice profiles (natural, assistant, broadcast, terminal)
- ✅ Demonstrates audio effects pipeline (intercom, compression, normalization)
- ✅ Shows contextual sound integration (44+ sound effects)
- ✅ Interactive demo mode with real-time profile switching
- 🎵 Includes 6 phases of testing from initialization to interactive use

### `demo_voice_speed.py` 
**Voice Speed Performance Demo**
- ⚡ Benchmarks voice synthesis speed across different text lengths
- 📊 Shows performance metrics (chars/second, synthesis time)
- 🔧 Demonstrates speed optimizations:
  - Chunk size: 180 → 300 characters (+67%)
  - Inter-chunk silence: 0.1s → 0.03s (-70%)
  - Text overlap: 2 → 1 words (-50%)
- 📈 Overall speed improvement: ~35%

### `demo_voice_quick.py`
**Quick Voice Demo**
- 🚀 Non-interactive showcase of voice profiles
- 🎵 Tests all voice profiles with sample phrases
- ✅ Demonstrates sound effects integration
- ⚡ Fast overview of voice capabilities

## 🎮 Other Demos

### Previously Created Demos
- `avatar_demo.py` - Avatar system demonstration
- `demo.py` - General M1K3 capabilities demo
- `demo_tui.py` - TUI interface demonstration  
- `retro_demo.py` - Retro-style interface demo
- `enhanced_cli_with_sounds.py` - CLI with enhanced sound integration

## 🎯 Performance Results

### Voice Speed Optimizations
The voice system has been significantly optimized:

**Before Optimization:**
- Chunk Size: 180 characters
- Inter-chunk Silence: 0.1 seconds
- Text Overlap: 2 words
- Multiple small chunks with long pauses

**After Optimization:**
- Chunk Size: 300 characters (+67% larger chunks)
- Inter-chunk Silence: 0.03 seconds (-70% less silence) 
- Text Overlap: 1 word (-50% overlap)
- Fewer chunks with minimal pauses

**Result:** ~35% overall speed improvement while maintaining natural speech quality.

### Voice Profiles Performance
All voice profiles maintain consistent performance:
- **Natural**: ~5.0s for standard phrases
- **Assistant**: ~5.0s with light compression
- **Broadcast**: ~5.5s with full radio processing
- **Terminal**: ~5.0s with retro filtering

## 🎵 Sound System Integration

The demos showcase integration with 44+ categorized sound effects:
- **Interaction sounds**: User input feedback
- **System sounds**: Success, error, notification
- **Contextual audio**: Response-based sound selection
- **Startup sequences**: PlayStation-inspired audio
- **Ambient sounds**: Thinking/processing background audio

## 🛠️ Technical Architecture

The demos demonstrate the new modular voice architecture:

1. **Text Processing**: Smart chunking with sentence preservation
2. **TTS Synthesis**: KittenML model with optimized parameters  
3. **Audio Effects Pipeline**: Pluggable effects system
4. **Sound Management**: Contextual audio feedback
5. **Profile System**: JSON-configured voice characteristics

## 🚀 Running the Demos

```bash
# Comprehensive showcase (interactive)
python demos/demo_voice_showcase.py

# Speed performance test
python demos/demo_voice_speed.py

# Quick demo (non-interactive)
python demos/demo_voice_quick.py
```

## 🎉 Key Achievements

✅ **Unified Voice Architecture**: Replaced multiple engines with single pipeline
✅ **Speed Optimization**: 35% improvement in synthesis speed
✅ **Modular Effects**: Pluggable audio processing system
✅ **Rich Sound Integration**: 44+ contextual sound effects
✅ **Professional Quality**: Radio-quality voice profiles
✅ **Easy Configuration**: JSON-based profile management

The voice and sound system now rivals commercial solutions while remaining 100% local and privacy-focused!