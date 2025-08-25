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
        
        # System commands
        self._register_command(Command(
            name="help", aliases=["h", "?"], description="Show available commands",
            category=CommandCategory.HELP, handler=self.handle_help
        ))
        
        self._register_command(Command(
            name="quit", aliases=["exit", "q"], description="Exit M1K3",
            category=CommandCategory.SESSION, handler=self.handle_quit
        ))
        
        self._register_command(Command(
            name="clear", aliases=["cls"], description="Clear conversation context",
            category=CommandCategory.SESSION, handler=self.handle_clear
        ))
        
        self._register_command(Command(
            name="stats", aliases=["status"], description="Show system statistics",
            category=CommandCategory.SYSTEM, handler=self.handle_stats
        ))
        
        self._register_command(Command(
            name="tokens", aliases=["usage"], description="Display token usage and eco impact",
            category=CommandCategory.SYSTEM, handler=self.handle_tokens
        ))
        
        # Voice commands
        self._register_command(Command(
            name="voice", aliases=["mute"], description="Toggle voice synthesis",
            category=CommandCategory.VOICE, handler=self.handle_voice
        ))
        
        self._register_command(Command(
            name="profile", aliases=[], description="Set voice profile",
            category=CommandCategory.VOICE, handler=self.handle_voice_profile,
            requires_args=True
        ))
        
        self._register_command(Command(
            name="tts", aliases=[], description="Show TTS system status",
            category=CommandCategory.VOICE, handler=self.handle_tts_status,
            requires_args=True
        ))
        
        # Avatar commands
        self._register_command(Command(
            name="avatar", aliases=[], description="Avatar system commands",
            category=CommandCategory.AVATAR, handler=self.handle_avatar,
            requires_args=True
        ))
        
        # Model commands
        self._register_command(Command(
            name="model", aliases=[], description="Model management commands",
            category=CommandCategory.MODEL, handler=self.handle_model,
            requires_args=True
        ))
        
        # Performance commands
        self._register_command(Command(
            name="performance", aliases=["perf"], description="Performance monitoring",
            category=CommandCategory.PERFORMANCE, handler=self.handle_performance,
            requires_args=True
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
            # Show help for specific command
            command = self.commands[args[0]]
            print(f"\n📖 Help for '{command.name}':")
            print(f"Description: {command.description}")
            print(f"Category: {command.category.value}")
            if command.aliases:
                print(f"Aliases: {', '.join(command.aliases)}")
            return True
        
        # Show general help
        print("\n📖 M1K3 CLI Commands:")
        print("=" * 50)
        
        # Group commands by category
        categories = {}
        for command in self.commands.values():
            category = command.category.value
            if category not in categories:
                categories[category] = []
            categories[category].append(command)
        
        # Display commands by category
        for category, commands in categories.items():
            print(f"\n{category.upper()}:")
            for command in commands:
                aliases_str = f" ({', '.join(command.aliases)})" if command.aliases else ""
                print(f"  {command.name}{aliases_str} - {command.description}")
        
        print("\nType 'help <command>' for detailed help on a specific command.")
        print("=" * 50)
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
        if hasattr(self.cli, 'display_system_diagnostics'):
            print("\n📊 System Statistics:")
            print("=" * 50)
            self.cli.display_system_diagnostics(None)
        else:
            print("⚠️ System statistics not available")
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
    
    def handle_avatar(self, args: List[str]) -> bool:
        """Handle avatar command"""
        if not args:
            print("⚠️ Avatar command requires arguments")
            print("Available: start, status, emotion <name> [intensity], test")
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
    
    def get_command_list(self) -> List[str]:
        """Get list of all available commands"""
        return list(self.commands.keys())
    
    def get_commands_by_category(self, category: CommandCategory) -> List[Command]:
        """Get all commands in a specific category"""
        return [cmd for cmd in self.commands.values() if cmd.category == category]