#!/usr/bin/env python3
"""
M1K3 TTS MCP Server for Claude Code Integration
Provides text-to-speech capabilities via Model Context Protocol
"""

import asyncio
import sys
import os
import json
from typing import Any
import traceback

# Add project root to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

try:
    from mcp.server.models import InitializationOptions
    from mcp.server import NotificationOptions, Server
    from mcp.server.stdio import stdio_server
    from mcp import types
except ImportError:
    print("ERROR: MCP SDK not installed. Run: pip install mcp", file=sys.stderr)
    sys.exit(1)

# M1K3 imports
from src.engines.voice.intelligent_tts_engine import IntelligentTTSEngine, TTSQuality
from src.tts.controllers.piper_tts_manager import PiperTTSManager
from src.tts.controllers.kokoro_tts_manager import KokoroTTSManager
from src.tts.effects.audio_effects import (
    PitchShiftEffect, RobotVoiceEffect, EchoEffect,
    ChorusEffect, LoFiEffect, MultibandLoFiEffect, FlangerEffect, ReverbEffect
)

# Initialize the TTS engines globally
tts_engine = None
kokoro_manager = KokoroTTSManager()
piper_manager = PiperTTSManager()
current_voice = "bm_daniel"  # Default Kokoro voice (Daniel - British Male, professional)

def initialize_tts():
    """Initialize the TTS engine"""
    global tts_engine
    try:
        tts_engine = IntelligentTTSEngine()
        if tts_engine.load_model():
            print("✅ M1K3 TTS Engine initialized successfully", file=sys.stderr)
            return True
        else:
            print("❌ Failed to load TTS engine", file=sys.stderr)
            return False
    except Exception as e:
        print(f"❌ TTS initialization error: {e}", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        return False

def detect_engine_from_voice(voice_id: str) -> str:
    """
    Auto-detect which TTS engine handles this voice based on naming prefix.

    Args:
        voice_id: Voice identifier (e.g., "bm_daniel", "en_US-ryan-high")

    Returns:
        'kokoro' for Kokoro voices (bm_*, bf_*, am_*, af_* patterns)
        'piper' for Piper voices (en_US-* pattern) and default
    """
    if any(voice_id.startswith(prefix) for prefix in ['bm_', 'bf_', 'am_', 'af_']):
        return 'kokoro'
    return 'piper'

# Create the MCP server
app = Server("m1k3-tts")

@app.list_tools()
async def handle_list_tools() -> list[types.Tool]:
    """List available TTS tools"""
    return [
        types.Tool(
            name="speak",
            description="Convert text to speech and play it aloud using M1K3's dual-engine TTS system (Kokoro & Piper). Ultra-lightweight neural voices with optional audio effects!",
            inputSchema={
                "type": "object",
                "properties": {
                    "text": {
                        "type": "string",
                        "description": "The text to convert to speech"
                    },
                    "quality": {
                        "type": "string",
                        "enum": ["fast", "balanced", "high_quality"],
                        "description": "(Piper only) Voice quality preference (default: balanced). Ignored for Kokoro voices.",
                        "default": "balanced"
                    },
                    "effect": {
                        "type": "string",
                        "enum": ["none", "robot", "chipmunk", "giant", "echo", "reverb", "chorus", "lofi", "nostalgic", "flanger"],
                        "description": "Fun audio effect to apply (default: none). robot=mechanical voice, chipmunk=high pitch, giant=deep voice, echo=delay, reverb=spacious, chorus=rich harmonies, lofi=retro sound, nostalgic=vintage warmth with vocal clarity, flanger=swooshing",
                        "default": "none"
                    },
                    "effect_intensity": {
                        "type": "number",
                        "description": "Effect intensity from 0.0 (subtle) to 1.0 (maximum). Default: 0.7",
                        "minimum": 0.0,
                        "maximum": 1.0,
                        "default": 0.7
                    },
                    "voice": {
                        "type": "string",
                        "description": "Voice to use (auto-detects engine). Kokoro: bm_daniel (British Male, professional - DEFAULT), bm_lewis, bm_george, bf_emma, bf_lily, bf_alice, am_michael, am_adam, am_eric, af_sky, af_nova, af_sarah, af_nicole. Piper: en_US-ryan-high, en_US-lessac-high/medium, en_US-amy-medium, en_US-joe-medium."
                    }
                },
                "required": ["text"]
            }
        ),
        types.Tool(
            name="get_voice_status",
            description="Get the status of the M1K3 TTS system including available engines, current settings, and usage statistics",
            inputSchema={
                "type": "object",
                "properties": {},
                "required": []
            }
        ),
        types.Tool(
            name="set_voice_quality",
            description="Set the default voice quality preference for future speech synthesis",
            inputSchema={
                "type": "object",
                "properties": {
                    "quality": {
                        "type": "string",
                        "enum": ["ultra_fast", "fast", "balanced", "high_quality"],
                        "description": "Voice quality to set as default"
                    }
                },
                "required": ["quality"]
            }
        ),
        types.Tool(
            name="set_voice",
            description="Set the default voice for TTS (auto-detects Kokoro or Piper engine)",
            inputSchema={
                "type": "object",
                "properties": {
                    "voice": {
                        "type": "string",
                        "description": "Voice ID from either Kokoro (bm_*, bf_*, am_*, af_*) or Piper (en_US-*) engines. Use list_voices to see all options."
                    }
                },
                "required": ["voice"]
            }
        ),
        types.Tool(
            name="list_voices",
            description="List all available TTS voices from both Kokoro and Piper engines",
            inputSchema={
                "type": "object",
                "properties": {},
                "required": []
            }
        )
    ]

@app.call_tool()
async def handle_call_tool(
    name: str, arguments: dict | None
) -> list[types.TextContent | types.ImageContent | types.EmbeddedResource]:
    """Handle tool execution"""

    global current_voice

    try:
        if name == "speak":
            text = arguments.get("text", "")
            if not text:
                return [types.TextContent(
                    type="text",
                    text="❌ No text provided"
                )]

            # Get voice and effect parameters
            voice_override = arguments.get("voice")
            voice = voice_override or current_voice
            effect_name = arguments.get("effect", "none")
            effect_intensity = arguments.get("effect_intensity", 0.7)

            # Detect which engine to use based on voice prefix
            engine = detect_engine_from_voice(voice)

            # Route to appropriate TTS engine
            if engine == 'kokoro':
                print(f"🎤 Speaking (Kokoro): {text[:50]}{'...' if len(text) > 50 else ''}", file=sys.stderr)

                # Set voice and load model
                kokoro_manager.set_voice(voice)
                if not kokoro_manager.load_model():
                    return [types.TextContent(
                        type="text",
                        text="❌ Failed to load Kokoro TTS model"
                    )]

                # Generate audio
                audio_data = kokoro_manager.generate(text)

                if audio_data is None:
                    return [types.TextContent(
                        type="text",
                        text="❌ Kokoro TTS generation failed"
                    )]

                sample_rate = kokoro_manager.sample_rate

            else:  # piper
                print(f"🎤 Speaking (Piper): {text[:50]}{'...' if len(text) > 50 else ''}", file=sys.stderr)

                # Set voice and load model
                piper_manager.set_voice(voice)
                if not piper_manager.load_model():
                    return [types.TextContent(
                        type="text",
                        text="❌ Failed to load Piper TTS model"
                    )]

                # Generate audio
                audio_data = piper_manager.generate(text)

                if audio_data is None:
                    return [types.TextContent(
                        type="text",
                        text="❌ Piper TTS generation failed"
                    )]

                sample_rate = piper_manager.sample_rate

            # Apply effects if requested
            if effect_name != "none":
                print(f"🎨 Applying {effect_name} effect (intensity: {effect_intensity})", file=sys.stderr)
                try:
                    # Create and apply effect
                    if effect_name == "robot":
                        # Default to vintage preset for nostalgic 80s robot sound
                        effect = RobotVoiceEffect({
                            "preset": "vintage",
                            "intensity": effect_intensity
                        })
                        audio_data = effect.apply(audio_data, sample_rate)

                    elif effect_name == "chipmunk":
                        effect = PitchShiftEffect({"preset": "chipmunk"})
                        audio_data = effect.apply(audio_data, sample_rate)

                    elif effect_name == "giant":
                        effect = PitchShiftEffect({"preset": "giant"})
                        audio_data = effect.apply(audio_data, sample_rate)

                    elif effect_name == "echo":
                        effect = EchoEffect({
                            "delay_ms": 300,
                            "decay": 0.5 * effect_intensity,
                            "num_echoes": 3,
                            "mix": 0.5 * effect_intensity
                        })
                        audio_data = effect.apply(audio_data, sample_rate)

                    elif effect_name == "reverb":
                        effect = ReverbEffect({
                            "preset": "hall",
                            "wet_mix": 0.3 + 0.3 * effect_intensity
                        })
                        audio_data = effect.apply(audio_data, sample_rate)

                    elif effect_name == "chorus":
                        effect = ChorusEffect({
                            "num_voices": 3,
                            "mix": 0.5 * effect_intensity
                        })
                        audio_data = effect.apply(audio_data, sample_rate)

                    elif effect_name == "lofi":
                        # Original lofi (uniform bitcrushing)
                        bit_depth = int(16 - 8 * effect_intensity)
                        effect = LoFiEffect({
                            "bit_depth": max(4, bit_depth),
                            "downsample": int(2 + 6 * effect_intensity),
                            "noise": 0.01 * effect_intensity
                        })
                        audio_data = effect.apply(audio_data, sample_rate)

                    elif effect_name == "lofi_nostalgic" or effect_name == "nostalgic":
                        # Multiband lofi with vocal preservation (M1K3's nostalgic character)
                        # Map intensity to presets: low → gentle, medium → balanced, high → aggressive
                        if effect_intensity < 0.4:
                            preset = "lofi_gentle"
                        elif effect_intensity < 0.7:
                            preset = "lofi_balanced"
                        else:
                            preset = "lofi_aggressive"

                        effect = MultibandLoFiEffect({"preset": preset})
                        audio_data = effect.apply(audio_data, sample_rate)

                    elif effect_name == "flanger":
                        effect = FlangerEffect({
                            "rate": 0.5,
                            "depth": 0.002 * (1 + effect_intensity),
                            "mix": 0.5 * effect_intensity
                        })
                        audio_data = effect.apply(audio_data, sample_rate)

                except Exception as e:
                    print(f"❌ Effect error: {e}", file=sys.stderr)
                    import traceback
                    traceback.print_exc(file=sys.stderr)
                    result_text = f"⚠️  Effect failed, playing without effect ({str(e)})"

            # Play the audio
            import numpy as np
            import sounddevice as sd

            # Ensure audio is in correct format
            if audio_data.dtype != np.float32:
                audio_data = audio_data.astype(np.float32)

            # Play the audio
            sd.play(audio_data, sample_rate)
            sd.wait()

            # Build result message
            engine_name = "Kokoro TTS" if engine == 'kokoro' else "Piper TTS"
            result_text = f"✅ Speech played successfully ({engine_name}, voice: {voice}"
            if effect_name != "none":
                result_text += f", {effect_name} effect"
            result_text += ")"

            return [types.TextContent(
                type="text",
                text=result_text
            )]

        elif name == "list_voices":
            # List all available voices from both engines
            voices_text = "🎤 Available TTS Voices\n\n"

            # KOKORO voices (grouped by region/gender)
            voices_text += "**KOKORO (Ultra-lightweight, #1 TTS Arena):**\n\n"

            # Group Kokoro voices
            bm_voices = {k: v for k, v in kokoro_manager.available_voices.items() if k.startswith('bm_')}
            bf_voices = {k: v for k, v in kokoro_manager.available_voices.items() if k.startswith('bf_')}
            am_voices = {k: v for k, v in kokoro_manager.available_voices.items() if k.startswith('am_')}
            af_voices = {k: v for k, v in kokoro_manager.available_voices.items() if k.startswith('af_')}

            if bm_voices:
                voices_text += "  British Male:\n"
                for voice_id, voice_info in bm_voices.items():
                    default_marker = " (DEFAULT)" if voice_id == current_voice else ""
                    voices_text += f"    • **{voice_id}** - {voice_info['character']}{default_marker}\n"
                voices_text += "\n"

            if bf_voices:
                voices_text += "  British Female:\n"
                for voice_id, voice_info in bf_voices.items():
                    default_marker = " (DEFAULT)" if voice_id == current_voice else ""
                    voices_text += f"    • **{voice_id}** - {voice_info['character']}{default_marker}\n"
                voices_text += "\n"

            if am_voices:
                voices_text += "  American Male:\n"
                for voice_id, voice_info in am_voices.items():
                    default_marker = " (DEFAULT)" if voice_id == current_voice else ""
                    voices_text += f"    • **{voice_id}** - {voice_info['character']}{default_marker}\n"
                voices_text += "\n"

            if af_voices:
                voices_text += "  American Female:\n"
                for voice_id, voice_info in af_voices.items():
                    default_marker = " (DEFAULT)" if voice_id == current_voice else ""
                    voices_text += f"    • **{voice_id}** - {voice_info['character']}{default_marker}\n"
                voices_text += "\n"

            # PIPER voices
            voices_text += "**PIPER (Ultra-fast Neural):**\n\n"
            for voice_id, voice_info in piper_manager.available_voices.items():
                default_marker = " (DEFAULT)" if voice_id == current_voice else ""
                voices_text += f"  • **{voice_id}** - {voice_info['character']}{default_marker}\n"

            # Current status
            current_engine = "Kokoro" if detect_engine_from_voice(current_voice) == 'kokoro' else "Piper"
            voices_text += f"\n**Current Voice:** {current_voice} ({current_engine})\n"

            return [types.TextContent(
                type="text",
                text=voices_text
            )]

        elif name == "set_voice":
            voice_name = arguments.get("voice", "")

            if not voice_name:
                return [types.TextContent(
                    type="text",
                    text="❌ No voice specified"
                )]

            # Check which engine has this voice and set accordingly
            if voice_name in kokoro_manager.available_voices:
                kokoro_manager.set_voice(voice_name)
                current_voice = voice_name
                return [types.TextContent(
                    type="text",
                    text=f"✅ Voice set to: {voice_name} (Kokoro TTS)"
                )]
            elif voice_name in piper_manager.available_voices:
                piper_manager.set_voice(voice_name)
                current_voice = voice_name
                return [types.TextContent(
                    type="text",
                    text=f"✅ Voice set to: {voice_name} (Piper TTS)"
                )]
            else:
                return [types.TextContent(
                    type="text",
                    text=f"❌ Invalid voice: {voice_name}. Use list_voices to see available options from both Kokoro and Piper engines."
                )]

        elif name == "get_voice_status":
            # Get multi-engine TTS status
            current_engine = detect_engine_from_voice(current_voice)
            engine_name = "Kokoro TTS" if current_engine == 'kokoro' else "Piper TTS"

            if current_engine == 'kokoro':
                sample_rate = kokoro_manager.sample_rate
                voice_loaded = kokoro_manager.tts is not None
                voice_info = kokoro_manager.available_voices.get(current_voice, {})
                voice_desc = voice_info.get('character', 'Unknown')
            else:
                sample_rate = piper_manager.sample_rate
                voice_loaded = piper_manager.voice is not None
                voice_info = piper_manager.available_voices.get(current_voice, {})
                voice_desc = voice_info.get('character', 'Unknown')

            status_text = f"""🎤 M1K3 Multi-Engine TTS System Status

**CURRENT:**
  Engine: {engine_name}
  Voice: {current_voice} ({voice_desc})
  Sample Rate: {sample_rate}Hz
  Voice Loaded: {voice_loaded}

**AVAILABLE:**
  ✅ Kokoro: {len(kokoro_manager.available_voices)} voices (24000Hz)
  ✅ Piper: {len(piper_manager.available_voices)} voices (22050Hz)
"""

            return [types.TextContent(
                type="text",
                text=status_text
            )]

        elif name == "set_voice_quality":
            quality_str = arguments.get("quality", "balanced")
            quality_map = {
                "ultra_fast": TTSQuality.ULTRA_FAST,
                "fast": TTSQuality.FAST,
                "balanced": TTSQuality.BALANCED,
                "high_quality": TTSQuality.HIGH_QUALITY
            }
            quality = quality_map.get(quality_str, TTSQuality.BALANCED)

            tts_engine.set_quality_preference(quality)

            return [types.TextContent(
                type="text",
                text=f"✅ Voice quality set to: {quality_str}"
            )]

        else:
            return [types.TextContent(
                type="text",
                text=f"❌ Unknown tool: {name}"
            )]

    except Exception as e:
        error_msg = f"❌ Error executing {name}: {str(e)}\n{traceback.format_exc()}"
        print(error_msg, file=sys.stderr)
        return [types.TextContent(
            type="text",
            text=error_msg
        )]

async def main():
    """Main entry point for the MCP server"""
    print("🚀 Starting M1K3 TTS MCP Server...", file=sys.stderr)

    # Initialize TTS engine
    if not initialize_tts():
        print("❌ Failed to initialize TTS engine, exiting...", file=sys.stderr)
        sys.exit(1)

    print("✅ M1K3 TTS MCP Server ready", file=sys.stderr)

    # Run the stdio server
    async with stdio_server() as (read_stream, write_stream):
        await app.run(
            read_stream,
            write_stream,
            InitializationOptions(
                server_name="m1k3-tts",
                server_version="1.0.0",
                capabilities=app.get_capabilities(
                    notification_options=NotificationOptions(),
                    experimental_capabilities={}
                )
            )
        )

if __name__ == "__main__":
    asyncio.run(main())
