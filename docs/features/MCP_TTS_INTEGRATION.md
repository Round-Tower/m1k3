# M1K3 TTS Integration with Claude Code

## Overview
This integration allows Claude Code to speak responses using M1K3's intelligent TTS system with local voice synthesis.

## Features
- **3 MCP Tools** available to Claude Code:
  1. `speak` - Convert text to speech and play it
  2. `get_voice_status` - Check TTS system status
  3. `set_voice_quality` - Configure voice quality

- **Multiple Voice Engines**:
  - **Piper** (Primary) - Ultra-fast neural TTS (20x real-time)
  - **KittenTTS** (Backup) - High-quality neural voice
  - **SimpleVoice** (Fallback) - System TTS for reliability

- **Quality Options**:
  - `ultra_fast` - Sub-10ms response (perfect for notifications)
  - `fast` - Sub-50ms (good quality neural)
  - `balanced` - Speed/quality balance (default)
  - `high_quality` - Best quality, long-form capable

## Setup Complete ✅

The MCP server is already configured and ready to use!

### Configuration Files:
- **MCP Server**: `/Users/kevinmurphy/Development/m1k3/mcp_tts_server.py`
- **Configuration**: `/Users/kevinmurphy/Development/m1k3/.mcp.json`
- **Settings**: `.claude/settings.local.json` (enableAllProjectMcpServers: true)

## How to Use

### Automatic Integration
Once you **restart Claude Code**, the MCP server will be available automatically. Claude Code will be able to:

1. **Call the `speak` tool** to voice responses
2. **Check voice status** with `get_voice_status`
3. **Change quality** with `set_voice_quality`

### Example Usage (After Restart)

Ask Claude Code to use voice:
```
You: "Please speak your next response aloud"
Claude: [Uses mcp__m1k3-tts__speak tool] "I can now speak to you!"
```

Or request different quality:
```
You: "Use high quality voice for this explanation"
Claude: [Uses speak with high_quality parameter]
```

### Manual Testing (Optional)

Test the MCP server manually:
```bash
# The server uses stdio protocol, so it's designed for Claude Code integration
# You can verify it starts correctly:
python mcp_tts_server.py
# You should see: ✅ M1K3 TTS MCP Server ready
```

## Architecture

```
Claude Code (Client)
    ↓
MCP Protocol (stdio)
    ↓
mcp_tts_server.py (MCP Server)
    ↓
IntelligentTTSEngine (M1K3)
    ↓
Voice Engines (Piper/KittenTTS/SimpleVoice)
    ↓
Audio Output 🔊
```

## Available MCP Tools

### 1. speak
```json
{
  "name": "speak",
  "description": "Convert text to speech and play it aloud",
  "parameters": {
    "text": "string (required) - Text to speak",
    "quality": "string (optional) - ultra_fast|fast|balanced|high_quality"
  }
}
```

### 2. get_voice_status
```json
{
  "name": "get_voice_status",
  "description": "Get TTS system status and statistics",
  "parameters": {}
}
```

### 3. set_voice_quality
```json
{
  "name": "set_voice_quality",
  "description": "Set default voice quality",
  "parameters": {
    "quality": "string (required) - ultra_fast|fast|balanced|high_quality"
  }
}
```

## Voice Engine Details

### Piper (Primary)
- **Speed**: 20x real-time (ultra-fast)
- **Quality**: Excellent neural quality (0.90/1.0)
- **Truncation**: Very low (5%)
- **Max Length**: 3000 characters
- **Best For**: Speed, quality, conversation

### KittenTTS (Backup)
- **Speed**: Fast neural synthesis
- **Quality**: High-quality neural voice
- **Truncation**: Moderate (enhanced with anti-truncation padding)
- **Best For**: High-quality requirements

### SimpleVoice (Fallback)
- **Speed**: Fast system TTS
- **Quality**: Basic (0.60/1.0)
- **Truncation**: Never truncates (0%)
- **Best For**: Emergency fallback, guaranteed reliability

## Anti-Truncation System

M1K3 includes an intelligent anti-truncation system:
- **Punctuation-based padding** - Uses periods and commas to force completion
- **Adaptive strategies** - Adjusts based on text length and engine
- **3 aggressiveness levels** - Minimal, adaptive, aggressive

## Next Steps

1. **Restart Claude Code** to load the MCP server
2. **Ask Claude to speak** - Try: "Please read this aloud"
3. **Customize voice quality** - Try: "Use high quality voice"
4. **Check status** - Ask: "What's your voice status?"

## Troubleshooting

### MCP Server Not Loading
```bash
# Check if MCP SDK is installed
pip show mcp

# Verify server starts
python mcp_tts_server.py
# Should show: ✅ M1K3 TTS MCP Server ready
```

### No Audio Output
```bash
# Check available engines
python -c "from src.engines.voice.intelligent_tts_engine import IntelligentTTSEngine; e = IntelligentTTSEngine(); e.load_model()"

# Test audio libraries
pip install sounddevice simpleaudio pygame
```

### Permission Issues
```bash
# Make server executable
chmod +x mcp_tts_server.py

# Check PYTHONPATH in .mcp.json points to project root
```

## Privacy & Performance

- **100% Local** - No cloud services, all processing on-device
- **Fast Response** - Sub-50ms synthesis with Piper
- **Low Resource** - ~50MB memory footprint
- **Privacy First** - Zero data transmission

## Example Conversations

```
User: "Speak your next response"
Claude: [Uses speak tool]
✅ "I'm now using M1K3's voice synthesis! This is pretty cool."

User: "What voice engines are available?"
Claude: [Uses get_voice_status tool]
🎤 M1K3 TTS System Status
Voice Enabled: True
Engines Loaded: 3
Available Engines: piper, kitten, simple

User: "Use the highest quality voice"
Claude: [Uses set_voice_quality with high_quality]
✅ Voice quality set to: high_quality
[Future responses use high_quality synthesis]
```

## Credits

- **M1K3 Project** - Privacy-focused local AI assistant
- **Model Context Protocol** - Anthropic's MCP standard
- **Piper TTS** - Fast neural voice synthesis
- **KittenTTS** - High-quality neural TTS
