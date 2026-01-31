#!/usr/bin/env python3
"""
M1K3 Unified MCP Server - TTS + Avatar + STT
Provides text-to-speech, avatar control, and speech recognition via Model Context Protocol
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

# M1K3 TTS imports
from src.engines.voice.intelligent_tts_engine import IntelligentTTSEngine, TTSQuality
from src.tts.controllers.piper_tts_manager import PiperTTSManager
from src.tts.controllers.kokoro_tts_manager import KokoroTTSManager
from src.tts.effects.audio_effects import (
    PitchShiftEffect, RobotVoiceEffect, EchoEffect,
    ChorusEffect, LoFiEffect, MultibandLoFiEffect, FlangerEffect, ReverbEffect,
    IntercomEffect
)

# M1K3 Avatar imports
from src.avatar.avatar_controller import AvatarController, AvatarEmotion, AvatarState

# M1K3 Avatar Server (for WebSocket broadcasts)
try:
    from src.avatar.avatar_server import (
        send_avatar_emotion,
        send_avatar_state,
        is_avatar_server_running,
    )
    AVATAR_SERVER_AVAILABLE = True
except ImportError:
    AVATAR_SERVER_AVAILABLE = False

# M1K3 STT imports
from src.engines.stt.stt_manager import STTManager, STTStatus

# ============================================================================
# Global State
# ============================================================================

tts_engine = None
kokoro_manager = KokoroTTSManager()
piper_manager = PiperTTSManager()
current_voice = "bm_daniel"  # Default Kokoro voice

avatar_controller = AvatarController()
stt_manager = None

# Model registry (mirrors web-avatar/src/registry/ModelRegistry.ts)
AVATAR_MODELS = {
    "colobus": {
        "id": "colobus",
        "name": "Colobus Monkey",
        "category": "primate",
        "file": "Colobus.glb",
        "description": "Quirky Series colobus monkey - 18 animations",
        "default": True
    },
    "sparrow": {
        "id": "sparrow",
        "name": "Sparrow",
        "category": "bird",
        "file": "Sparrow.glb",
        "description": "Quirky Series sparrow - flying animations"
    },
    "gecko": {
        "id": "gecko",
        "name": "Gecko",
        "category": "reptile",
        "file": "Gecko.glb",
        "description": "Quirky Series gecko - climbing animations"
    },
    "herring": {
        "id": "herring",
        "name": "Herring",
        "category": "fish",
        "file": "Herring.glb",
        "description": "Quirky Series herring - swimming animations"
    },
    "muskrat": {
        "id": "muskrat",
        "name": "Muskrat",
        "category": "rodent",
        "file": "Muskrat.glb",
        "description": "Quirky Series muskrat - burrowing animations"
    },
    "pudu": {
        "id": "pudu",
        "name": "Pudu",
        "category": "deer",
        "file": "Pudu.glb",
        "description": "Quirky Series pudu deer - prancing animations"
    },
    "taipan": {
        "id": "taipan",
        "name": "Taipan",
        "category": "snake",
        "file": "Taipan.glb",
        "description": "Quirky Series taipan snake - slithering animations"
    },
    "inkfish": {
        "id": "inkfish",
        "name": "Inkfish",
        "category": "cephalopod",
        "file": "Inkfish.glb",
        "description": "Quirky Series inkfish - tentacle animations"
    },
    "mask": {
        "id": "mask",
        "name": "Mask",
        "category": "abstract",
        "file": "Mask.glb",
        "description": "Abstract mask - procedural animation"
    }
}

current_avatar_model = "colobus"

# ============================================================================
# Avatar Broadcast Helper
# ============================================================================

def broadcast_avatar_update():
    """Broadcast current avatar state to connected WebSocket clients"""
    if not AVATAR_SERVER_AVAILABLE:
        return

    try:
        state = avatar_controller.get_current_state()
        send_avatar_emotion(
            state["emotion"],
            state["intensity"],
            "",
            None
        )
        send_avatar_state(state["state"])
    except Exception as e:
        print(f"Avatar broadcast error (ignored): {e}", file=sys.stderr)

# ============================================================================
# Initialization
# ============================================================================

def initialize_tts():
    """Initialize the TTS engine"""
    global tts_engine
    try:
        tts_engine = IntelligentTTSEngine()
        if tts_engine.load_model():
            print("TTS Engine initialized successfully", file=sys.stderr)
            return True
        else:
            print("Failed to load TTS engine", file=sys.stderr)
            return False
    except Exception as e:
        print(f"TTS initialization error: {e}", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        return False

def initialize_stt():
    """Initialize the STT manager"""
    global stt_manager
    try:
        stt_manager = STTManager()
        if stt_manager.is_available():
            print(f"STT Manager initialized with engines: {stt_manager.get_available_engines()}", file=sys.stderr)
            return True
        else:
            print("STT Manager initialized but no engines available", file=sys.stderr)
            return False
    except Exception as e:
        print(f"STT initialization error: {e}", file=sys.stderr)
        traceback.print_exc(file=sys.stderr)
        return False

def detect_engine_from_voice(voice_id: str) -> str:
    """Auto-detect TTS engine from voice prefix"""
    if any(voice_id.startswith(prefix) for prefix in ['bm_', 'bf_', 'am_', 'af_']):
        return 'kokoro'
    return 'piper'

# ============================================================================
# MCP Server
# ============================================================================

app = Server("m1k3-unified")

@app.list_tools()
async def handle_list_tools() -> list[types.Tool]:
    """List all available tools"""
    return [
        # ====================================================================
        # TTS Tools
        # ====================================================================
        types.Tool(
            name="speak",
            description="Convert text to speech with optional emotion hint for avatar sync. Uses M1K3's dual-engine TTS (Kokoro & Piper).",
            inputSchema={
                "type": "object",
                "properties": {
                    "text": {
                        "type": "string",
                        "description": "The text to convert to speech"
                    },
                    "emotion_hint": {
                        "type": "string",
                        "enum": ["happy", "sad", "angry", "surprised", "love", "thinking", "sleepy", "excited"],
                        "description": "Emotion hint to sync avatar expression during speech"
                    },
                    "effect": {
                        "type": "string",
                        "enum": ["none", "intercom", "robot", "chipmunk", "giant", "echo", "reverb", "chorus", "lofi", "nostalgic", "flanger"],
                        "description": "Audio effect (default: intercom)"
                    },
                    "effect_intensity": {
                        "type": "number",
                        "description": "Effect intensity 0.0-1.0 (default: 0.7)",
                        "minimum": 0.0,
                        "maximum": 1.0
                    },
                    "voice": {
                        "type": "string",
                        "description": "Voice ID. Kokoro: bm_daniel (default), bm_lewis, bf_emma, etc. Piper: en_US-ryan-high, etc."
                    }
                },
                "required": ["text"]
            }
        ),
        types.Tool(
            name="list_voices",
            description="List all available TTS voices from Kokoro and Piper engines",
            inputSchema={"type": "object", "properties": {}, "required": []}
        ),
        types.Tool(
            name="set_voice",
            description="Set the default TTS voice",
            inputSchema={
                "type": "object",
                "properties": {
                    "voice": {"type": "string", "description": "Voice ID"}
                },
                "required": ["voice"]
            }
        ),
        types.Tool(
            name="get_voice_status",
            description="Get TTS system status",
            inputSchema={"type": "object", "properties": {}, "required": []}
        ),

        # ====================================================================
        # Avatar Tools
        # ====================================================================
        types.Tool(
            name="get_avatar_state",
            description="Get current avatar state including emotion, activity state, and model",
            inputSchema={"type": "object", "properties": {}, "required": []}
        ),
        types.Tool(
            name="set_avatar_emotion",
            description="Set avatar emotion with optional intensity",
            inputSchema={
                "type": "object",
                "properties": {
                    "emotion": {
                        "type": "string",
                        "enum": ["happy", "sad", "angry", "surprised", "love", "thinking", "sleepy", "excited"],
                        "description": "Emotion to display"
                    },
                    "intensity": {
                        "type": "integer",
                        "description": "Emotion intensity 0-100 (default: 70)",
                        "minimum": 0,
                        "maximum": 100
                    }
                },
                "required": ["emotion"]
            }
        ),
        types.Tool(
            name="set_avatar_state",
            description="Set avatar activity state (affects animation)",
            inputSchema={
                "type": "object",
                "properties": {
                    "state": {
                        "type": "string",
                        "enum": ["idle", "thinking", "speaking", "listening", "error", "loading"],
                        "description": "Activity state"
                    }
                },
                "required": ["state"]
            }
        ),
        types.Tool(
            name="set_avatar_model",
            description="Switch avatar 3D model",
            inputSchema={
                "type": "object",
                "properties": {
                    "model_id": {
                        "type": "string",
                        "enum": list(AVATAR_MODELS.keys()),
                        "description": "Model ID (colobus, sparrow, gecko, etc.)"
                    }
                },
                "required": ["model_id"]
            }
        ),
        types.Tool(
            name="list_avatar_models",
            description="List all available 3D avatar models",
            inputSchema={"type": "object", "properties": {}, "required": []}
        ),

        # ====================================================================
        # STT Tools
        # ====================================================================
        types.Tool(
            name="start_voice_input",
            description="Start listening for voice input via microphone. Returns transcribed text.",
            inputSchema={
                "type": "object",
                "properties": {
                    "timeout": {
                        "type": "number",
                        "description": "Listen timeout in seconds (default: 5.0)",
                        "minimum": 1.0,
                        "maximum": 30.0
                    }
                },
                "required": []
            }
        ),
        types.Tool(
            name="get_stt_status",
            description="Get speech-to-text system status",
            inputSchema={"type": "object", "properties": {}, "required": []}
        )
    ]

@app.call_tool()
async def handle_call_tool(
    name: str, arguments: dict | None
) -> list[types.TextContent | types.ImageContent | types.EmbeddedResource]:
    """Handle tool execution"""

    global current_voice, current_avatar_model

    try:
        # ====================================================================
        # TTS Tool Handlers
        # ====================================================================
        if name == "speak":
            text = arguments.get("text", "")
            if not text:
                return [types.TextContent(type="text", text="No text provided")]

            voice_override = arguments.get("voice")
            voice = voice_override or current_voice
            effect_name = arguments.get("effect", "intercom")
            effect_intensity = arguments.get("effect_intensity", 0.7)
            emotion_hint = arguments.get("emotion_hint")

            # Sync avatar emotion if hint provided
            if emotion_hint:
                try:
                    emotion_enum = AvatarEmotion(emotion_hint)
                    avatar_controller.update_emotion("", "", force_emotion=emotion_enum)
                    avatar_controller.update_state(AvatarState.SPEAKING)
                    broadcast_avatar_update()
                except ValueError:
                    pass  # Invalid emotion, ignore

            # Detect engine and generate audio
            engine = detect_engine_from_voice(voice)

            if engine == 'kokoro':
                print(f"Speaking (Kokoro): {text[:50]}...", file=sys.stderr)
                kokoro_manager.set_voice(voice)
                if not kokoro_manager.load_model():
                    return [types.TextContent(type="text", text="Failed to load Kokoro TTS model")]
                audio_data = kokoro_manager.generate(text)
                sample_rate = kokoro_manager.sample_rate
            else:
                print(f"Speaking (Piper): {text[:50]}...", file=sys.stderr)
                piper_manager.set_voice(voice)
                if not piper_manager.load_model():
                    return [types.TextContent(type="text", text="Failed to load Piper TTS model")]
                audio_data = piper_manager.generate(text)
                sample_rate = piper_manager.sample_rate

            if audio_data is None:
                return [types.TextContent(type="text", text="TTS generation failed")]

            # Apply effects
            if effect_name != "none":
                audio_data = apply_effect(audio_data, sample_rate, effect_name, effect_intensity)

            # Play audio
            import numpy as np
            import sounddevice as sd

            if audio_data.dtype != np.float32:
                audio_data = audio_data.astype(np.float32)

            sd.play(audio_data, sample_rate)
            sd.wait()

            # Reset avatar to idle after speaking
            avatar_controller.update_state(AvatarState.IDLE)
            broadcast_avatar_update()

            result_text = f"Speech played ({engine}, voice: {voice}"
            if emotion_hint:
                result_text += f", emotion: {emotion_hint}"
            if effect_name != "none":
                result_text += f", {effect_name} effect"
            result_text += ")"

            return [types.TextContent(type="text", text=result_text)]

        elif name == "list_voices":
            return [types.TextContent(type="text", text=format_voices_list())]

        elif name == "set_voice":
            voice_name = arguments.get("voice", "")
            if not voice_name:
                return [types.TextContent(type="text", text="No voice specified")]

            if voice_name in kokoro_manager.available_voices:
                kokoro_manager.set_voice(voice_name)
                current_voice = voice_name
                return [types.TextContent(type="text", text=f"Voice set to: {voice_name} (Kokoro TTS)")]
            elif voice_name in piper_manager.available_voices:
                piper_manager.set_voice(voice_name)
                current_voice = voice_name
                return [types.TextContent(type="text", text=f"Voice set to: {voice_name} (Piper TTS)")]
            else:
                return [types.TextContent(type="text", text=f"Invalid voice: {voice_name}. Use list_voices to see options.")]

        elif name == "get_voice_status":
            return [types.TextContent(type="text", text=format_voice_status())]

        # ====================================================================
        # Avatar Tool Handlers
        # ====================================================================
        elif name == "get_avatar_state":
            state = avatar_controller.get_current_state()
            state["model_id"] = current_avatar_model
            state["model_name"] = AVATAR_MODELS[current_avatar_model]["name"]
            return [types.TextContent(type="text", text=json.dumps(state, indent=2))]

        elif name == "set_avatar_emotion":
            emotion_str = arguments.get("emotion", "")
            intensity = arguments.get("intensity", 70)

            try:
                emotion = AvatarEmotion(emotion_str)
                result = avatar_controller.update_emotion("", "", force_emotion=emotion)
                avatar_controller.emotion_intensity = intensity
                broadcast_avatar_update()
                return [types.TextContent(
                    type="text",
                    text=f"Avatar emotion set to: {emotion_str} (intensity: {intensity})"
                )]
            except ValueError:
                return [types.TextContent(type="text", text=f"Invalid emotion: {emotion_str}")]

        elif name == "set_avatar_state":
            state_str = arguments.get("state", "")
            state_map = {
                "idle": AvatarState.IDLE,
                "thinking": AvatarState.THINKING,
                "speaking": AvatarState.SPEAKING,
                "listening": AvatarState.LOADING,  # Use LOADING for listening visual
                "error": AvatarState.ERROR,
                "loading": AvatarState.LOADING
            }

            if state_str in state_map:
                avatar_controller.update_state(state_map[state_str])
                broadcast_avatar_update()
                return [types.TextContent(type="text", text=f"Avatar state set to: {state_str}")]
            else:
                return [types.TextContent(type="text", text=f"Invalid state: {state_str}")]

        elif name == "set_avatar_model":
            model_id = arguments.get("model_id", "")
            if model_id in AVATAR_MODELS:
                current_avatar_model = model_id
                model = AVATAR_MODELS[model_id]
                return [types.TextContent(
                    type="text",
                    text=f"Avatar model switched to: {model['name']} ({model['category']})"
                )]
            else:
                return [types.TextContent(type="text", text=f"Invalid model: {model_id}")]

        elif name == "list_avatar_models":
            models_text = "Available Avatar Models:\n\n"
            for model_id, model in AVATAR_MODELS.items():
                default_marker = " (DEFAULT)" if model.get("default") else ""
                models_text += f"  {model_id}{default_marker}\n"
                models_text += f"    Name: {model['name']}\n"
                models_text += f"    Category: {model['category']}\n"
                models_text += f"    {model['description']}\n\n"

            models_text += f"Current model: {current_avatar_model}"
            return [types.TextContent(type="text", text=models_text)]

        # ====================================================================
        # STT Tool Handlers
        # ====================================================================
        elif name == "start_voice_input":
            if not stt_manager or not stt_manager.is_available():
                return [types.TextContent(type="text", text="STT not available. Check microphone permissions.")]

            timeout = arguments.get("timeout", 5.0)

            # Set avatar to listening state
            avatar_controller.update_state(AvatarState.LOADING)
            avatar_controller.update_emotion("", "", force_emotion=AvatarEmotion.THINKING)
            broadcast_avatar_update()

            print(f"Listening for voice input (timeout: {timeout}s)...", file=sys.stderr)

            result = stt_manager.listen_once(timeout=timeout)

            # Reset avatar
            avatar_controller.update_state(AvatarState.IDLE)
            broadcast_avatar_update()

            if result:
                return [types.TextContent(
                    type="text",
                    text=json.dumps({
                        "success": True,
                        "text": result.text,
                        "confidence": result.confidence,
                        "engine": result.engine
                    }, indent=2)
                )]
            else:
                return [types.TextContent(
                    type="text",
                    text=json.dumps({
                        "success": False,
                        "text": "",
                        "error": "No speech detected or confidence too low"
                    }, indent=2)
                )]

        elif name == "get_stt_status":
            if not stt_manager:
                return [types.TextContent(type="text", text="STT not initialized")]

            info = stt_manager.get_engine_info()
            return [types.TextContent(type="text", text=json.dumps(info, indent=2))]

        else:
            return [types.TextContent(type="text", text=f"Unknown tool: {name}")]

    except Exception as e:
        error_msg = f"Error executing {name}: {str(e)}\n{traceback.format_exc()}"
        print(error_msg, file=sys.stderr)
        return [types.TextContent(type="text", text=error_msg)]

# ============================================================================
# Helper Functions
# ============================================================================

def apply_effect(audio_data, sample_rate, effect_name, intensity):
    """Apply audio effect to audio data"""
    try:
        if effect_name == "intercom":
            effect = IntercomEffect({"low_freq": 300, "high_freq": 3400})
        elif effect_name == "robot":
            effect = RobotVoiceEffect({"preset": "vintage", "intensity": intensity})
        elif effect_name == "chipmunk":
            effect = PitchShiftEffect({"preset": "chipmunk"})
        elif effect_name == "giant":
            effect = PitchShiftEffect({"preset": "giant"})
        elif effect_name == "echo":
            effect = EchoEffect({
                "delay_ms": 300, "decay": 0.5 * intensity,
                "num_echoes": 3, "mix": 0.5 * intensity
            })
        elif effect_name == "reverb":
            effect = ReverbEffect({"preset": "hall", "wet_mix": 0.3 + 0.3 * intensity})
        elif effect_name == "chorus":
            effect = ChorusEffect({"num_voices": 3, "mix": 0.5 * intensity})
        elif effect_name == "lofi":
            bit_depth = int(16 - 8 * intensity)
            effect = LoFiEffect({
                "bit_depth": max(4, bit_depth),
                "downsample": int(2 + 6 * intensity),
                "noise": 0.01 * intensity
            })
        elif effect_name in ["lofi_nostalgic", "nostalgic"]:
            if intensity < 0.4:
                preset = "lofi_gentle"
            elif intensity < 0.7:
                preset = "lofi_balanced"
            else:
                preset = "lofi_aggressive"
            effect = MultibandLoFiEffect({"preset": preset})
        elif effect_name == "flanger":
            effect = FlangerEffect({
                "rate": 0.5, "depth": 0.002 * (1 + intensity),
                "mix": 0.5 * intensity
            })
        else:
            return audio_data

        return effect.apply(audio_data, sample_rate)
    except Exception as e:
        print(f"Effect error: {e}", file=sys.stderr)
        return audio_data

def format_voices_list():
    """Format the voices list for display"""
    voices_text = "Available TTS Voices\n\n"
    voices_text += "KOKORO (Ultra-lightweight):\n\n"

    for prefix, label in [('bm_', 'British Male'), ('bf_', 'British Female'),
                          ('am_', 'American Male'), ('af_', 'American Female')]:
        voices = {k: v for k, v in kokoro_manager.available_voices.items() if k.startswith(prefix)}
        if voices:
            voices_text += f"  {label}:\n"
            for voice_id, voice_info in voices.items():
                default = " (DEFAULT)" if voice_id == current_voice else ""
                voices_text += f"    {voice_id} - {voice_info['character']}{default}\n"
            voices_text += "\n"

    voices_text += "PIPER (Ultra-fast Neural):\n\n"
    for voice_id, voice_info in piper_manager.available_voices.items():
        default = " (DEFAULT)" if voice_id == current_voice else ""
        voices_text += f"  {voice_id} - {voice_info['character']}{default}\n"

    voices_text += f"\nCurrent Voice: {current_voice}"
    return voices_text

def format_voice_status():
    """Format voice status for display"""
    engine = detect_engine_from_voice(current_voice)
    engine_name = "Kokoro TTS" if engine == 'kokoro' else "Piper TTS"

    if engine == 'kokoro':
        sample_rate = kokoro_manager.sample_rate
        voice_info = kokoro_manager.available_voices.get(current_voice, {})
    else:
        sample_rate = piper_manager.sample_rate
        voice_info = piper_manager.available_voices.get(current_voice, {})

    return f"""M1K3 Multi-Engine TTS Status

CURRENT:
  Engine: {engine_name}
  Voice: {current_voice} ({voice_info.get('character', 'Unknown')})
  Sample Rate: {sample_rate}Hz

AVAILABLE:
  Kokoro: {len(kokoro_manager.available_voices)} voices (24000Hz)
  Piper: {len(piper_manager.available_voices)} voices (22050Hz)
"""

# ============================================================================
# Main Entry Point
# ============================================================================

async def main():
    """Main entry point for the MCP server"""
    print("Starting M1K3 Unified MCP Server...", file=sys.stderr)

    # Enable GPU acceleration
    if not os.getenv("ONNX_PROVIDER"):
        os.environ["ONNX_PROVIDER"] = "CoreMLExecutionProvider"
        print("GPU acceleration enabled (CoreML)", file=sys.stderr)

    # Initialize subsystems
    if not initialize_tts():
        print("Warning: TTS initialization failed", file=sys.stderr)

    initialize_stt()  # Optional, don't fail if unavailable

    print("M1K3 Unified MCP Server ready", file=sys.stderr)
    print(f"  TTS: Kokoro + Piper", file=sys.stderr)
    print(f"  Avatar: {len(AVATAR_MODELS)} models", file=sys.stderr)
    print(f"  STT: {stt_manager.get_available_engines() if stt_manager and stt_manager.is_available() else 'unavailable'}", file=sys.stderr)

    # Run the stdio server
    async with stdio_server() as (read_stream, write_stream):
        await app.run(
            read_stream,
            write_stream,
            InitializationOptions(
                server_name="m1k3-unified",
                server_version="1.0.0",
                capabilities=app.get_capabilities(
                    notification_options=NotificationOptions(),
                    experimental_capabilities={}
                )
            )
        )

if __name__ == "__main__":
    asyncio.run(main())
