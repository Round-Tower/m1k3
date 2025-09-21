#!/usr/bin/env python3
"""
CLI Command Handling Module
Handles command parsing, routing, and execution for M1K3 CLI
"""

import re
import json
import webbrowser
from typing import Dict, Callable, Optional, List, Any
from dataclasses import dataclass
from enum import Enum

from .cli_logging import get_cli_logger, log_info, log_debug, log_warning, log_error


class CommandCategory(Enum):
    """Categories of CLI commands"""
    SYSTEM = "system"
    VOICE = "voice"
    AVATAR = "avatar"
    MODEL = "model"
    PERFORMANCE = "performance"
    HELP = "help"
    SESSION = "session"


@dataclass
class Command:
    """Represents a CLI command"""
    name: str
    aliases: List[str]
    description: str
    category: CommandCategory
    handler: Callable
    requires_args: bool = False
    usage: Optional[str] = None
    examples: Optional[List[str]] = None


class CLICommandHandler:
    """Handles CLI command parsing and execution"""
    
    def __init__(self, cli_instance):
        self.cli = cli_instance
        self.logger = get_cli_logger()
        self.commands: Dict[str, Command] = {}
        self.aliases: Dict[str, str] = {}
        self._register_commands()
    
    def _register_commands(self):
        """Register all available commands"""
        log_debug("Registering CLI commands")
        
        # Help commands
        self._register_command(Command(
            name="help", aliases=["h", "?"], 
            description="Show this help or get detailed help for a command",
            category=CommandCategory.HELP, handler=self.handle_help,
            usage="help [command]",
            examples=["help", "help model", "help avatar"]
        ))
        
        # Session commands
        self._register_command(Command(
            name="quit", aliases=["exit", "q"], 
            description="Exit M1K3 CLI safely",
            category=CommandCategory.SESSION, handler=self.handle_quit,
            usage="quit",
            examples=["quit", "exit", "q"]
        ))
        
        self._register_command(Command(
            name="clear", aliases=["cls"], 
            description="Clear conversation history and reset AI context",
            category=CommandCategory.SESSION, handler=self.handle_clear,
            usage="clear",
            examples=["clear", "cls"]
        ))
        
        # System commands
        self._register_command(Command(
            name="stats", aliases=["status"], 
            description="Show system resources (RAM, CPU, disk) and AI engine status",
            category=CommandCategory.SYSTEM, handler=self.handle_stats,
            usage="stats",
            examples=["stats", "status"]
        ))
        
        self._register_command(Command(
            name="tokens", aliases=["usage"], 
            description="Show token usage, context window, and environmental savings",
            category=CommandCategory.SYSTEM, handler=self.handle_tokens,
            usage="tokens",
            examples=["tokens", "usage"]
        ))
        
        # Voice commands
        self._register_command(Command(
            name="voice", aliases=["mute"], 
            description="Toggle voice synthesis on/off for AI responses",
            category=CommandCategory.VOICE, handler=self.handle_voice,
            usage="voice",
            examples=["voice", "mute"]
        ))
        
        self._register_command(Command(
            name="profile", aliases=[], 
            description="Set voice profile: natural (light intercom), assistant (medium), broadcast (heavy), terminal (medium), debug (none), minimal (none)",
            category=CommandCategory.VOICE, handler=self.handle_voice_profile,
            requires_args=True,
            usage="profile <name>",
            examples=["profile natural", "/profile broadcast", "profile debug"]
        ))
        
        # STT commands
        self._register_command(Command(
            name="stt", aliases=["speech"], 
            description="Control speech-to-text settings and status",
            category=CommandCategory.VOICE, handler=self.handle_stt,
            usage="stt [status|engine <name>|continuous|calibrate|test]",
            examples=["stt", "stt status", "stt engine web_speech", "stt test"]
        ))
        
        self._register_command(Command(
            name="listen", aliases=["🎤"], 
            description="Start voice listening mode for next input",
            category=CommandCategory.VOICE, handler=self.handle_listen,
            usage="listen",
            examples=["listen", "🎤"]
        ))
        
        self._register_command(Command(
            name="tts", aliases=[], 
            description="Show intelligent TTS system status and voice settings",
            category=CommandCategory.VOICE, handler=self.handle_tts_status,
            requires_args=True,
            usage="tts status",
            examples=["tts status", "/tts status"]
        ))
        
        # VibeVoice commands
        self._register_command(Command(
            name="vibevoice", aliases=["vv"], 
            description="Control VibeVoice TTS: status, quality modes, speaker settings",
            category=CommandCategory.VOICE, handler=self.handle_vibevoice,
            requires_args=True,
            usage="vibevoice <status|quality <fast|balanced|quality>|speakers <names>|load|test>",
            examples=["vibevoice status", "vibevoice quality fast", "vibevoice speakers Alice Bob", "vibevoice test"]
        ))
        
        # Avatar commands
        self._register_command(Command(
            name="avatar", aliases=[], 
            description="Control web avatar: start server, set emotions, test animations",
            category=CommandCategory.AVATAR, handler=self.handle_avatar,
            requires_args=True,
            usage="avatar <start|status|emotion|test>",
            examples=["avatar start", "avatar status", "avatar emotion happy 80", "avatar test"]
        ))
        
        # Model commands  
        self._register_command(Command(
            name="model", aliases=[], 
            description="Switch AI models, view available models, get recommendations",
            category=CommandCategory.MODEL, handler=self.handle_model,
            requires_args=True,
            usage="model <list|switch|recommend|download>",
            examples=["model list", "model switch gemma3:270m", "model recommend", "model download gemma3:270m"]
        ))
        
        # Performance commands
        self._register_command(Command(
            name="performance", aliases=["perf"], 
            description="Monitor inference speed, memory usage, and system performance",
            category=CommandCategory.PERFORMANCE, handler=self.handle_performance,
            requires_args=True,
            usage="performance <monitor|stats|benchmark>",
            examples=["performance monitor", "performance stats", "perf benchmark"]
        ))
        
        log_debug(f"Registered {len(self.commands)} commands")
    
    def _register_command(self, command: Command):
        """Register a single command"""
        self.commands[command.name] = command
        
        # Register aliases
        for alias in command.aliases:
            self.aliases[alias] = command.name
    
    def parse_command(self, user_input: str) -> tuple[Optional[str], List[str]]:
        """Parse user input to extract command and arguments"""
        user_input = user_input.strip()
        
        # Handle special slash commands
        if user_input.startswith('/'):
            parts = user_input.split(' ', 1)
            command_name = parts[0][1:]  # Strip the leading slash
            args = parts[1].split() if len(parts) > 1 else []
            return command_name, args
        
        # Handle regular commands
        parts = user_input.split()
        if not parts:
            return None, []
        
        command_name = parts[0].lower()
        args = parts[1:]
        
        # Resolve alias to command name
        if command_name in self.aliases:
            command_name = self.aliases[command_name]
        
        # Check if it's a registered command
        if command_name in self.commands:
            return command_name, args
        
        return None, []
    
    def execute_command(self, command_name: str, args: List[str]) -> bool:
        """Execute a command with given arguments"""
        if command_name not in self.commands:
            return False
        
        command = self.commands[command_name]
        
        # Check if command requires arguments
        if command.requires_args and not args:
            log_warning(f"Command '{command_name}' requires arguments")
            return False
        
        try:
            return command.handler(args)
        except Exception as e:
            log_error(f"Error executing command '{command_name}': {e}")
            return False
    
    def is_command(self, user_input: str) -> bool:
        """Check if user input is a command"""
        command_name, _ = self.parse_command(user_input)
        return command_name is not None
    
    def handle_help(self, args: List[str]) -> bool:
        """Handle help command"""
        if args and args[0] in self.commands:
            # Show detailed help for specific command
            command = self.commands[args[0]]
            print(f"\n📖 Help for '{command.name}':")
            print("=" * 40)
            print(f"📝 Description: {command.description}")
            print(f"📂 Category: {command.category.value}")
            
            if command.aliases:
                print(f"🏷️  Aliases: {', '.join(command.aliases)}")
            
            if command.usage:
                print(f"📋 Usage: {command.usage}")
            
            if command.examples:
                print(f"💡 Examples:")
                for example in command.examples:
                    print(f"   • {example}")
            
            print("=" * 40)
            return True
        
        # Show enhanced general help
        print("\n📖 M1K3 CLI Commands:")
        print("=" * 70)
        
        # Group commands by category
        categories = {
            CommandCategory.HELP: [],
            CommandCategory.SESSION: [],
            CommandCategory.SYSTEM: [],
            CommandCategory.VOICE: [],
            CommandCategory.AVATAR: [],
            CommandCategory.MODEL: [],
            CommandCategory.PERFORMANCE: []
        }
        
        for command in self.commands.values():
            if command.category in categories:
                categories[command.category].append(command)
        
        # Display commands by category with enhanced formatting
        for category, commands in categories.items():
            if not commands:
                continue
                
            print(f"\n🔧 {category.value.upper()}:")
            for command in commands:
                aliases_str = f" ({', '.join(command.aliases)})" if command.aliases else ""
                # Show first example as a hint
                hint = f" → Try: {command.examples[0]}" if command.examples else ""
                print(f"  • {command.name}{aliases_str}")
                print(f"    {command.description}{hint}")
        
        print(f"\n💡 Quick Tips:")
        print(f"  • Type 'help <command>' for detailed usage and examples")
        print(f"  • Use aliases for faster typing (e.g., 'q' instead of 'quit')")
        print(f"  • Slash commands also work: /profile natural, /tts status")
        print("=" * 70)
        return True
    
    def handle_quit(self, args: List[str]) -> bool:
        """Handle quit command"""
        log_info("User requested quit")
        if hasattr(self.cli, 'cleanup_and_exit'):
            self.cli.cleanup_and_exit()
        return False  # Signal to exit main loop
    
    def handle_clear(self, args: List[str]) -> bool:
        """Handle clear command"""
        if hasattr(self.cli, 'ai_engine') and self.cli.ai_engine:
            if hasattr(self.cli.ai_engine, 'clear_context'):
                self.cli.ai_engine.clear_context()
                print("🧹 Conversation context cleared")
                log_info("Conversation context cleared")
            else:
                print("⚠️ Context clearing not supported by current AI engine")
        else:
            print("⚠️ No AI engine available")
        return True
    
    def handle_stats(self, args: List[str]) -> bool:
        """Handle stats command"""
        print("\n📊 M1K3 Statistics:")
        print("=" * 50)

        # Show session statistics if available
        if hasattr(self.cli, 'session_stats') and self.cli.session_stats:
            stats = self.cli.session_stats.current_stats
            print(f"\n🎮 Session Stats:")
            print(f"   📝 Queries handled: {stats.queries_handled}")
            print(f"   💬 Words generated: {stats.total_words_generated:,}")
            print(f"   ⏱️  Session duration: {self.cli.session_stats.get_session_duration_str()}")
            print(f"   🏆 Features used: {', '.join(stats.features_used) if stats.features_used else 'None'}")

            if stats.achievements_unlocked:
                print(f"   🌟 Achievements: {', '.join(stats.achievements_unlocked)}")

            # Show eco impact
            print(f"\n🌱 Environmental Impact vs Cloud AI:")
            print(f"   💧 Water saved: {stats.water_saved_ml:.1f}ml")
            print(f"   ⚡ Energy saved: {stats.energy_saved_wh:.2f}Wh")
            print(f"   🌍 CO2 saved: {stats.co2_saved_g:.2f}g")

            # Show exciting insight
            insight = self.cli.session_stats.get_exciting_insight()
            if insight:
                print(f"\n{insight}")

        # Show system diagnostics if available
        if hasattr(self.cli, 'display_system_diagnostics'):
            print(f"\n🔧 System Diagnostics:")
            print("=" * 30)
            self.cli.display_system_diagnostics(None)
        else:
            print("⚠️ System diagnostics not available")

        return True
    
    def handle_tokens(self, args: List[str]) -> bool:
        """Handle tokens command"""
        if hasattr(self.cli, 'ai_engine') and self.cli.ai_engine:
            try:
                # Get token usage information
                context_length = getattr(self.cli.ai_engine, 'get_context_length', lambda: 2048)()
                current_tokens = getattr(self.cli.ai_engine, 'get_current_tokens', lambda: 0)()
                
                usage_percent = (current_tokens / context_length) * 100 if context_length > 0 else 0
                
                print(f"\n🎯 Token Usage: {current_tokens:,} / {context_length:,} tokens ({usage_percent:.1f}%)")
                
                # Show eco impact if available
                if hasattr(self.cli, 'calculate_eco_impact'):
                    eco_impact = self.cli.calculate_eco_impact(current_tokens)
                    if eco_impact:
                        print(f"🌱 Eco Impact Saved vs Cloud AI:")
                        print(f"   💧 Water: {eco_impact.get('water_saved', 0):.1f}ml")
                        print(f"   ⚡ Energy: {eco_impact.get('energy_saved', 0):.2f}Wh")
                        print(f"   🌍 CO2: {eco_impact.get('co2_saved', 0):.2f}g")
                
            except Exception as e:
                log_error(f"Error getting token usage: {e}")
                print("⚠️ Unable to retrieve token usage information")
        else:
            print("⚠️ No AI engine available")
        return True
    
    def handle_voice(self, args: List[str]) -> bool:
        """Handle voice command"""
        if hasattr(self.cli, 'toggle_voice'):
            self.cli.toggle_voice()
        elif hasattr(self.cli, 'voice_enabled'):
            self.cli.voice_enabled = not self.cli.voice_enabled
            status = "enabled" if self.cli.voice_enabled else "disabled"
            print(f"🔊 Voice synthesis {status}")
            log_info(f"Voice synthesis {status}")
        else:
            print("⚠️ Voice control not available")
        return True
    
    def handle_stt(self, args: List[str]) -> bool:
        """Handle STT (speech-to-text) commands"""
        if not hasattr(self.cli, 'stt_manager') or not self.cli.stt_manager:
            print("⚠️ Speech-to-text not available")
            return True
        
        stt_manager = self.cli.stt_manager
        
        if not args or args[0] == "status":
            # Show STT status
            engine_info = stt_manager.get_engine_info()
            print("🎤 Speech-to-Text Status:")
            print(f"   Available: {engine_info.get('available', False)}")
            print(f"   Current Engine: {engine_info.get('current_engine', 'None')}")
            print(f"   Available Engines: {', '.join(engine_info.get('available_engines', []))}")
            print(f"   Status: {engine_info.get('status', 'Unknown')}")
            print(f"   Language: {engine_info.get('language', 'Unknown')}")
            print(f"   Continuous Listening: {engine_info.get('continuous_listening', False)}")
            print(f"   Confidence Threshold: {engine_info.get('confidence_threshold', 0.5):.2f}")
            return True
        
        command = args[0].lower()
        
        if command == "engine" and len(args) > 1:
            # Switch STT engine
            engine_name = args[1]
            available_engines = stt_manager.get_available_engines()
            if engine_name in available_engines:
                success = stt_manager.switch_engine(engine_name)
                if success:
                    print(f"🔄 Switched to {engine_name} STT engine")
                else:
                    print(f"❌ Failed to switch to {engine_name}")
            else:
                print(f"❌ Engine '{engine_name}' not available. Available: {', '.join(available_engines)}")
        
        elif command == "continuous":
            # Toggle continuous listening
            success = stt_manager.toggle_continuous_listening()
            if success:
                status = "started" if stt_manager.continuous_listening else "stopped"
                print(f"🎤 Continuous listening {status}")
            else:
                print("❌ Failed to toggle continuous listening")
        
        elif command == "calibrate":
            # Calibrate microphone (for web speech engine)
            current_engine = stt_manager.current_engine
            if hasattr(current_engine, 'calibrate_microphone'):
                success = current_engine.calibrate_microphone()
                if success:
                    print("✅ Microphone calibrated")
                else:
                    print("❌ Microphone calibration failed")
            else:
                print("⚠️ Current engine doesn't support calibration")
        
        elif command == "test":
            # Test microphone access
            current_engine = stt_manager.current_engine
            engine_name = stt_manager.current_engine_name
            
            print(f"🧪 Testing STT engine: {engine_name}")
            
            # Test microphone access
            if hasattr(current_engine, 'test_microphone_access'):
                mic_success = current_engine.test_microphone_access()
                if not mic_success:
                    print("❌ STT test failed - microphone access issue")
                    return True
            else:
                print("⚠️ Microphone test not supported by current engine")
            
            # Test basic recognition
            print("🎤 Testing speech recognition - say 'hello test'...")
            try:
                result = stt_manager.listen_once(timeout=5.0, phrase_timeout=2.0)
                if result and result.text:
                    print(f"✅ STT test successful!")
                    print(f"   Heard: '{result.text}'")
                    print(f"   Confidence: {result.confidence:.2f}")
                    print(f"   Engine: {result.engine}")
                else:
                    print("⚠️ STT test completed but no speech was recognized")
                    print("💡 Try speaking louder or closer to microphone")
            except Exception as e:
                print(f"❌ STT test failed: {e}")
        
        else:
            print(f"❌ Unknown STT command: {command}")
            print("Usage: stt [status|engine <name>|continuous|calibrate|test]")
        
        return True
    
    def handle_listen(self, args: List[str]) -> bool:
        """Handle listen command - activate voice input for next response"""
        if not hasattr(self.cli, 'stt_manager') or not self.cli.stt_manager:
            print("⚠️ Speech-to-text not available")
            return True
        
        stt_manager = self.cli.stt_manager
        if not stt_manager.is_available():
            print("⚠️ STT engine not available")
            return True
        
        print("🎤 Listening for voice input... (speak now)")
        try:
            result = stt_manager.listen_once(timeout=10.0, phrase_timeout=2.0)
            if result and result.text:
                print(f"🔊 Heard: {result.text}")
                # Process the voice input as if it were typed
                return self.cli._process_user_input(result.text)
            else:
                print("⚠️ No speech detected or recognition failed")
                return True
        except Exception as e:
            log_error(f"Voice listening failed: {e}")
            print(f"❌ Voice listening error: {e}")
            return True
    
    def handle_voice_profile(self, args: List[str]) -> bool:
        """Handle voice profile command"""
        if not args:
            print("⚠️ Voice profile name required. Usage: profile <name> or /profile <name>")
            return True
        
        profile_name = args[0]
        if hasattr(self.cli, 'set_voice_profile'):
            success = self.cli.set_voice_profile(profile_name)
            if success:
                print(f"🎭 Voice profile set to: {profile_name}")
                log_info(f"Voice profile changed to: {profile_name}")
            else:
                print(f"⚠️ Unknown voice profile: {profile_name}")
        else:
            print("⚠️ Voice profiles not available")
        return True
    
    def handle_tts_status(self, args: List[str]) -> bool:
        """Handle TTS status command"""
        if args and args[0] == "status":
            if hasattr(self.cli, 'show_tts_status'):
                self.cli.show_tts_status()
            else:
                print("⚠️ TTS status information not available")
        else:
            print("⚠️ Usage: tts status or /tts status")
        return True
    
    def handle_vibevoice(self, args: List[str]) -> bool:
        """Handle VibeVoice command"""
        if not args:
            print("🎤 VibeVoice - Microsoft's Frontier Text-to-Speech")
            print("=" * 50)
            print("📋 Available Commands:")
            print("  • status                   - Show VibeVoice system status")
            print("  • quality <mode>           - Set generation quality: fast, balanced, quality")
            print("  • speakers <names>         - Set speaker names (e.g., Alice Bob Carol)")
            print("  • load [variant]           - Load VibeVoice model (1.5B or 7B)")
            print("  • test [quality]           - Test VibeVoice generation")
            print()
            print("💡 Examples:")
            print("  vibevoice status           - Check current settings")
            print("  vibevoice quality fast     - Switch to fast mode (5 DDPM steps)")
            print("  vibevoice speakers Alice Bob - Set two speakers")
            print("  vibevoice test balanced    - Test with balanced quality")
            return True
        
        subcommand = args[0].lower()
        
        if subcommand == "status":
            self._show_vibevoice_status()
        elif subcommand == "quality":
            if len(args) < 2:
                print("⚠️ Usage: vibevoice quality <fast|balanced|quality>")
            else:
                self._set_vibevoice_quality(args[1])
        elif subcommand == "speakers":
            if len(args) < 2:
                print("⚠️ Usage: vibevoice speakers <names...>")
                print("💡 Available speakers: Alice, Bob, Carol, Dave, Andrew, Maya, Carter, Frank, Mary, Samuel")
            else:
                self._set_vibevoice_speakers(args[1:])
        elif subcommand == "load":
            variant = args[1] if len(args) > 1 else "1.5B"
            self._load_vibevoice_model(variant)
        elif subcommand == "test":
            quality = args[1] if len(args) > 1 else None
            self._test_vibevoice(quality)
        else:
            print(f"⚠️ Unknown VibeVoice command: {subcommand}")
            print("💡 Use 'vibevoice' to see available commands")
        
        return True
    
    def handle_avatar(self, args: List[str]) -> bool:
        """Handle avatar command"""
        if not args:
            print("⚠️ Avatar command requires arguments")
            print("\n🤖 Available Avatar Commands:")
            print("  • start                    - Launch web avatar dashboard")  
            print("  • status                   - Check avatar server status")
            print("  • emotion <name> [0-100]   - Set emotion (happy, sad, angry, surprised, love, thinking, sleepy, excited)")
            print("  • test                     - Test all avatar emotions")
            print("\n💡 Examples: 'avatar start', 'avatar emotion happy 80', 'avatar test'")
            return True
        
        subcommand = args[0].lower()
        
        if subcommand == "start":
            if hasattr(self.cli, 'start_avatar_with_browser'):
                self.cli.start_avatar_with_browser()
            else:
                print("⚠️ Avatar server not available")
        
        elif subcommand == "status":
            if hasattr(self.cli, 'show_avatar_status'):
                self.cli.show_avatar_status()
            else:
                print("⚠️ Avatar status not available")
        
        elif subcommand == "emotion":
            if len(args) < 2:
                print("⚠️ Usage: avatar emotion <name> [intensity]")
                return True
            
            emotion = args[1]
            intensity = int(args[2]) if len(args) > 2 else 75
            
            if hasattr(self.cli, 'set_avatar_emotion'):
                self.cli.set_avatar_emotion(emotion, intensity)
            else:
                print("⚠️ Avatar emotion control not available")
        
        elif subcommand == "test":
            if hasattr(self.cli, 'test_avatar_emotions'):
                self.cli.test_avatar_emotions()
            else:
                print("⚠️ Avatar emotion testing not available")
        
        else:
            print(f"⚠️ Unknown avatar command: {subcommand}")
        
        return True
    
    def handle_model(self, args: List[str]) -> bool:
        """Handle model command"""
        if hasattr(self.cli, 'model_cli') and self.cli.model_cli:
            return self.cli.model_cli.handle_command(args)
        else:
            print("⚠️ Model management not available")
        return True
    
    def handle_performance(self, args: List[str]) -> bool:
        """Handle performance command"""
        if hasattr(self.cli, 'handle_performance_command'):
            return self.cli.handle_performance_command(' '.join(args))
        else:
            print("⚠️ Performance monitoring not available")
        return True
    
    # VibeVoice helper methods
    def _show_vibevoice_status(self):
        """Show VibeVoice system status"""
        try:
            from src.tts.controllers.vibevoice_manager import VibeVoiceManager
            
            print("🎤 VibeVoice System Status")
            print("=" * 40)
            
            manager = VibeVoiceManager()
            
            # Check availability
            if manager.is_available():
                print("✅ Status: Available")
                
                # Show device info
                if hasattr(manager, 'device'):
                    device_info = {
                        "cuda": "🚀 NVIDIA CUDA",
                        "mps": "⚡ Apple Silicon MPS", 
                        "cpu": "💻 CPU"
                    }
                    print(f"🖥️  Device: {device_info.get(manager.device, manager.device)}")
                
                # Get performance info
                if hasattr(manager, 'get_performance_info'):
                    perf_info = manager.get_performance_info()
                    print(f"🎯 Quality Mode: {perf_info['generation_quality']}")
                    print(f"🔢 DDPM Steps: {perf_info['ddpm_steps']}")
                    print(f"⚙️  CFG Scale: {perf_info['cfg_scale']}")
                    print(f"🔄 Model Loaded: {'Yes' if perf_info['model_loaded'] else 'No'}")
                
                # Show current speakers
                if hasattr(manager, 'current_speakers'):
                    speakers = ", ".join(manager.current_speakers)
                    print(f"🗣️  Current Speakers: {speakers}")
                
                # Show availability info
                avail_info = manager.get_availability_info()
                if avail_info.get('repository_path'):
                    print(f"📁 Repository: {avail_info['repository_path']}")
                
            else:
                print("❌ Status: Not Available")
                print("💡 Use 'vibevoice load' to load the model")
                
        except ImportError:
            print("❌ VibeVoice not installed or not available")
        except Exception as e:
            print(f"❌ Error getting VibeVoice status: {e}")
    
    def _set_vibevoice_quality(self, quality: str):
        """Set VibeVoice generation quality"""
        try:
            from src.tts.controllers.vibevoice_manager import VibeVoiceManager
            
            manager = VibeVoiceManager()
            valid_qualities = ["fast", "balanced", "quality"]
            
            if quality not in valid_qualities:
                print(f"❌ Invalid quality mode: {quality}")
                print(f"💡 Valid options: {', '.join(valid_qualities)}")
                return
                
            if manager.set_generation_quality(quality):
                # Show the change
                perf_info = manager.get_performance_info()
                print(f"✅ Quality set to '{quality}'")
                print(f"   DDPM Steps: {perf_info['ddpm_steps']}")
                print(f"   CFG Scale: {perf_info['cfg_scale']}")
            else:
                print(f"❌ Failed to set quality to {quality}")
                
        except ImportError:
            print("❌ VibeVoice not available")
        except Exception as e:
            print(f"❌ Error setting quality: {e}")
    
    def _set_vibevoice_speakers(self, speakers: List[str]):
        """Set VibeVoice speakers"""
        try:
            from src.tts.controllers.vibevoice_manager import VibeVoiceManager
            
            manager = VibeVoiceManager()
            if manager.set_speakers(speakers):
                current = ", ".join(manager.current_speakers)
                print(f"✅ Speakers set to: {current}")
            else:
                print("❌ Failed to set speakers")
                
        except ImportError:
            print("❌ VibeVoice not available")
        except Exception as e:
            print(f"❌ Error setting speakers: {e}")
    
    def _load_vibevoice_model(self, variant: str):
        """Load VibeVoice model"""
        try:
            from src.tts.controllers.vibevoice_manager import VibeVoiceManager
            
            manager = VibeVoiceManager()
            print(f"🔄 Loading VibeVoice {variant} model...")
            
            if manager.load_model(variant):
                print("✅ VibeVoice model loaded successfully!")
                # Show status after loading
                self._show_vibevoice_status()
            else:
                print("❌ Failed to load VibeVoice model")
                
        except ImportError:
            print("❌ VibeVoice not available")
        except Exception as e:
            print(f"❌ Error loading model: {e}")
    
    def _test_vibevoice(self, quality: str = None):
        """Test VibeVoice generation"""
        try:
            from src.tts.controllers.vibevoice_manager import VibeVoiceManager
            import time
            
            manager = VibeVoiceManager()
            
            if not manager.is_available():
                print("❌ VibeVoice not available. Use 'vibevoice load' first.")
                return
            
            print("🧪 Testing VibeVoice generation...")
            test_text = "Hello! This is a VibeVoice test. The system is working correctly."
            
            start_time = time.time()
            audio = manager.generate(test_text, ["Alice"], quality=quality)
            generation_time = time.time() - start_time
            
            if audio is not None:
                duration = len(audio) / manager.sample_rate
                rtf = generation_time / duration if duration > 0 else 0
                quality_used = quality or manager.generation_quality
                
                print(f"✅ Test completed!")
                print(f"   Quality: {quality_used}")
                print(f"   Duration: {duration:.1f} seconds")
                print(f"   Generation Time: {generation_time:.1f} seconds")
                print(f"   RTF: {rtf:.1f}x")
                
                # Try to play the audio if possible
                try:
                    import tempfile
                    import subprocess
                    import platform
                    
                    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp_file:
                        if manager.save_audio(audio, tmp_file.name):
                            print("🔊 Playing test audio...")
                            
                            system = platform.system()
                            if system == "Darwin":  # macOS
                                subprocess.run(["afplay", tmp_file.name], check=True)
                            elif system == "Windows":
                                subprocess.run(["powershell", "-c", f"(New-Object Media.SoundPlayer '{tmp_file.name}').PlaySync()"], check=True)
                            else:  # Linux
                                players = ["paplay", "aplay", "play"]
                                for player in players:
                                    try:
                                        subprocess.run([player, tmp_file.name], check=True, 
                                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                                        break
                                    except:
                                        continue
                            
                            print("✅ Audio test completed!")
                except Exception as audio_error:
                    print(f"⚠️ Audio generated but playback failed: {audio_error}")
                    
            else:
                print("❌ Test failed - no audio generated")
                
        except ImportError:
            print("❌ VibeVoice not available")
        except Exception as e:
            print(f"❌ Test error: {e}")
    
    def get_command_list(self) -> List[str]:
        """Get list of all available commands"""
        return list(self.commands.keys())
    
    def get_commands_by_category(self, category: CommandCategory) -> List[Command]:
        """Get all commands in a specific category"""
        return [cmd for cmd in self.commands.values() if cmd.category == category]