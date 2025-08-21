#!/usr/bin/env python3
"""
M1K3 CLI Application with Local AI Integration
Command-line interface with avatar state management
"""

import os
import sys
import time
import threading
import webbrowser
import atexit
import signal
from enum import Enum
from typing import Optional
import argparse
from dataclasses import asdict

# Enable HuggingFace tokenizers parallelism for better performance
os.environ['TOKENIZERS_PARALLELISM'] = 'true'

# Try to import the real AI engine first, fall back to mock if not available
try:
    from ai_inference import LocalAIEngine
    REAL_AI_AVAILABLE = True
    print("🧠 Using real AI inference engine")
except ImportError as e:
    print(f"⚠️  Real AI engine not available: {e}")
    print("🔄 Falling back to mock AI engine")
    from simple_ai_engine import SimpleAIEngine
    REAL_AI_AVAILABLE = False

from download_model import download_model
from enhanced_voice_engine import create_voice_engine
from system_metrics import SystemMonitor, generate_dynamic_greeting
from cli_animations import CLIAnimator, AnimationType
from sound_manager import SoundManager, ContextualSoundManager

# Avatar system imports
try:
    from avatar_server import (
        start_avatar_server, stop_avatar_server, is_avatar_server_running,
        send_avatar_emotion, send_avatar_state, send_avatar_progress, get_avatar_server_status,
        send_chat_ai_start, send_chat_ai_chunk, send_chat_ai_complete, send_sound_trigger, send_metrics_update
    )
    from avatar_controller import AvatarController, AvatarEmotion, AvatarState as AvatarServerState
    AVATAR_AVAILABLE = True
except ImportError as e:
    print(f"⚠️  Avatar system not available: {e}")
    AVATAR_AVAILABLE = False

class AvatarState(Enum):
    IDLE = "💤"
    THINKING = "🤔" 
    GENERATING = "⚡"
    ERROR = "❌"
    LOADING = "⏳"
    SPEAKING = "🔊"

class M1K3CLI:
    def __init__(self, voice_enabled: bool = True, auto_avatar: bool = False, avatar_port: int = 8080, open_browser: bool = True):
        # Initialize system monitoring first to gather context
        self.system_monitor = SystemMonitor()
        
        # Avatar configuration
        self.auto_avatar = auto_avatar
        self.avatar_port = avatar_port
        self.open_browser = open_browser
        
        # Use real AI engine if available, otherwise fall back to mock
        if REAL_AI_AVAILABLE:
            self.ai_engine = LocalAIEngine()
            print("🚀 Initialized with real AI inference engine")
        else:
            self.ai_engine = SimpleAIEngine()
            print("🎭 Initialized with mock AI engine (demo mode)")
            
        self.voice_engine = create_voice_engine()
        self.sound_manager = SoundManager()
        self.context_sound_manager = ContextualSoundManager(self.sound_manager)
        self.animator = CLIAnimator()
        self.avatar_state = AvatarState.IDLE
        self.running = True
        self.voice_enabled = voice_enabled
        self.show_context = False  # Show detailed system context
        
        # Initialize avatar controller if available
        if AVATAR_AVAILABLE:
            self.avatar_controller = AvatarController()
            self._log_startup_message("OK", "Avatar controller initialized")
        else:
            self.avatar_controller = None
        
        # Set up AI engine with system context
        self._initialize_ai_context()
    
    def _log_startup_message(self, level: str, message: str):
        """Helper for logging formatted startup messages."""
        level_map = {
            "INFO": "\033[94m[INFO]\033[0m", # Blue
            "OK": "\033[92m[OK]\033[0m",     # Green
            "WARN": "\033[93m[WARN]\033[0m",   # Yellow
            "ERROR": "\033[91m[ERROR]\033[0m" # Red
        }
        print(f" {level_map.get(level, '[ ]')}  {message}")

    def _initialize_ai_context(self):
        """Initialize AI engine with rich system context."""
        self._log_startup_message("INFO", "Collecting rich device context...")
        metrics = self.system_monitor.collect_metrics()
        
        # Convert dataclass to a simple dict for the AI engine
        session_context = asdict(metrics) if metrics else {}
        
        if hasattr(self.ai_engine, 'set_session_context'):
            self.ai_engine.set_session_context(session_context)
            self._log_startup_message("OK", "AI context injected successfully")
        
    def set_avatar_state(self, state: AvatarState):
        """Update avatar state"""
        self.avatar_state = state
        
    def print_with_avatar(self, message: str, state: Optional[AvatarState] = None):
        """Print message with current avatar state"""
        if state:
            self.set_avatar_state(state)
        print(f"{self.avatar_state.value} {message}")
        
    def animated_print(self, message: str, state: Optional[AvatarState] = None, effect: str = "typewriter"):
        """Print message with animation effects"""
        if state:
            self.set_avatar_state(state)
            
        if effect == "typewriter":
            self.animator.typewriter_effect(f"{self.avatar_state.value} {message}")
        elif effect == "fade":
            self.animator.fade_in_text(f"{self.avatar_state.value} {message}")
        elif effect == "pulse":
            self.animator.pulse_text(f"{self.avatar_state.value} {message}")
        else:
            self.print_with_avatar(message, state)
    
    def start_animated_status(self, message: str, animation_type: str = "thinking"):
        """Start an animated status indicator"""
        if animation_type in ["thinking", "generating", "loading", "speaking", "listening", "processing"]:
            self.animator.start_avatar_animation(animation_type, message)
        else:
            self.animator.start_animation(AnimationType.SPINNER, message)
    
    def stop_animated_status(self):
        """Stop the current animated status"""
        self.animator.stop_animation()
        
    def setup_ai(self) -> bool:
        """Initialize AI engine and voice with animations."""
        self._log_startup_message("INFO", "Initializing AI Engine...")
        
        # Check if model exists
        if not self.ai_engine.is_model_available():
            self.start_animated_status("Downloading SmolLM-135M model...", "loading")
            model_path = download_model()
            self.stop_animated_status()
            
            if not model_path:
                self.animated_print("Failed to download model", AvatarState.ERROR, "pulse")
                return False
                
        # Load AI model
        self.start_animated_status("Loading AI model...", "processing")
        if not self.ai_engine.load_model():
            self.stop_animated_status()
            self._log_startup_message("ERROR", "Failed to load AI model. M1K3 cannot continue.")
            return False
        self.stop_animated_status()
            
        # Load voice model if enabled
        if self.voice_enabled:
            self.start_animated_status("Loading voice synthesis...", "loading")
            if self.voice_engine.load_model():
                self.stop_animated_status()
                self._log_startup_message("OK", "Voice synthesis ready!")
            else:
                self.stop_animated_status()
                self._log_startup_message("WARN", "Voice synthesis failed. Continuing without voice.")
                self.voice_enabled = False
        else:
            self._log_startup_message("INFO", "Voice synthesis is disabled.")
            
        self._log_startup_message("OK", "AI Engine is ready!")
        
        # Auto-start avatar server if requested
        if self.auto_avatar and AVATAR_AVAILABLE:
            self.start_avatar_with_browser()
        
        return True
    
    def start_avatar_with_browser(self):
        """Start avatar server and optionally open browser"""
        try:
            self.print_with_avatar("🚀 Auto-starting avatar server...", AvatarState.LOADING)
            
            if start_avatar_server():
                status = get_avatar_server_status()
                
                # Display all available URLs
                msg = f"✅ Avatar server started!"
                if status.get('urls'):
                    msg += "\n   📱 Available at:"
                    for url in status['urls']:
                        if 'localhost' in url or '127.0.0.1' in url:
                            msg += f"\n      Local:   {url}"
                        else:
                            msg += f"\n      Network: {url}"
                
                self.print_with_avatar(msg, AvatarState.IDLE)
                
                # Open browser if requested
                if self.open_browser:
                    local_url = f"http://127.0.0.1:{self.avatar_port}"
                    self.print_with_avatar(f"🌐 Opening browser: {local_url}", AvatarState.IDLE)
                    
                    try:
                        webbrowser.open(local_url)
                        self.print_with_avatar("✅ Browser opened successfully!", AvatarState.IDLE)
                        
                        # Send initial greeting to avatar
                        if self.avatar_controller:
                            self.send_avatar_update("Welcome to M1K3! Your AI companion is ready.", "greeting")
                            
                    except Exception as e:
                        self.print_with_avatar(f"⚠️  Could not open browser: {e}", AvatarState.ERROR)
                        self.print_with_avatar(f"Please manually open: {local_url}", AvatarState.IDLE)
                
                # Set up cleanup on exit and signal handling
                atexit.register(self.cleanup_avatar_server)
                signal.signal(signal.SIGTERM, self._signal_handler)
                signal.signal(signal.SIGINT, self._signal_handler)
                
            else:
                self.print_with_avatar("❌ Failed to start avatar server", AvatarState.ERROR)
                
        except Exception as e:
            self.print_with_avatar(f"❌ Error starting avatar: {e}", AvatarState.ERROR)
    
    def _signal_handler(self, signum, frame):
        """Handle signals (SIGTERM, SIGINT) for graceful shutdown"""
        print(f"\n📡 Received signal {signum}, shutting down gracefully...")
        self.cleanup_avatar_server()
        sys.exit(0)
    
    def cleanup_avatar_server(self):
        """Clean up avatar server on exit"""
        try:
            if AVATAR_AVAILABLE and is_avatar_server_running():
                print("\n🛑 Shutting down avatar server...")
                self.sound_manager.play_system_event_sound("shutdown")
                stop_avatar_server()
                print("✅ Avatar server stopped")
        except Exception as e:
            print(f"⚠️  Error stopping avatar server: {e}")
        
    def _handle_action_command(self, user_input: str) -> bool:
        """Handle special action commands starting with /."""
        command = user_input.split()[0].lower()
        
        action_map = {
            "/help": self.show_help,
            "/stats": self.display_system_stats,
            "/context": self.display_device_context,
            "/tokens": self.display_token_stats,
            "/clear": self.ai_engine.clear_context,
            "/demo": self.demo_animations,
            "/sound": self.handle_sound_command,
            "/exit": lambda: setattr(self, 'running', False)
        }
        
        if command in action_map:
            print(f"Executing action: {command}")
            # Pass the full command for commands that need it
            if command == "/sound":
                action_map[command](user_input)
            else:
                action_map[command]()
            return True
            
        # Handle avatar commands specifically
        if command.startswith("/avatar"):
            avatar_command = user_input.replace("/", "")
            self.handle_avatar_command(avatar_command)
            return True

        return False

    def handle_sound_command(self, user_input: str = ""):
        """Handle sound-related commands"""
        parts = user_input.lower().split()
        command = parts[1] if len(parts) > 1 else "status"

        if command in ["on", "enable"]:
            self.sound_manager.enabled = True
            self.print_with_avatar("🔊 Sound effects enabled", AvatarState.IDLE)
            self.sound_manager.play_contextual_sound("success")
        elif command in ["off", "disable", "mute"]:
            self.sound_manager.enabled = False
            self.print_with_avatar("🔇 Sound effects disabled", AvatarState.IDLE)
        elif command in ["profile", "set"]:
            if len(parts) > 2:
                profile_name = parts[2]
                if self.sound_manager.set_sound_profile(profile_name):
                    self.sound_manager.play_contextual_sound("success")
                else:
                    self.print_with_avatar(f"❌ Unknown sound profile: {profile_name}", AvatarState.ERROR)
                    self.sound_manager.play_contextual_sound("error")
            else:
                msg = f"Available profiles: {', '.join(self.sound_manager.get_available_profiles())}"
                self.print_with_avatar(msg, AvatarState.IDLE)
        elif command == "test":
            self.print_with_avatar("🎶 Testing sound system...", AvatarState.IDLE)
            self.sound_manager.play_success_sequence("minor")
            time.sleep(0.5)
            self.sound_manager.play_contextual_sound("error")
            time.sleep(0.5)
            self.sound_manager.play_thinking_ambient()
        else: # status
            status = self.sound_manager.test_sound_system()
            msg = f"🔊 Sound System Status:\n"
            msg += f"   Enabled: {'✅' if self.sound_manager.enabled else '❌'}\n"
            msg += f"   Profile: {status['current_profile']}\n"
            msg += f"   Discovered sounds: {status['total_sounds']}"
            self.print_with_avatar(msg, AvatarState.IDLE)

    def handle_user_input(self, user_input: str):
        """Process user input and generate AI response."""
        
        # Send pre-thinking state - user just submitted input
        self.send_avatar_update(user_input, "pre_thinking")
        
        self.sound_manager.play_contextual_sound("interaction")
        
        # Send user message to avatar dashboard
        if AVATAR_AVAILABLE and is_avatar_server_running():
            # User message is handled by the dashboard itself, but we can trigger sounds
            send_sound_trigger("message_sent")
        
        # Handle special commands (now including /actions)
        if user_input.startswith('/'):
            if self._handle_action_command(user_input):
                return # Action was handled, so we are done.
        
        # Legacy command handling for backward compatibility
        if user_input.lower() in ['quit', 'exit', 'q']:
            goodbye_msg = "Goodbye!"
            self.print_with_avatar(goodbye_msg, AvatarState.IDLE)
            if self.voice_enabled:
                self.voice_engine.synthesize_and_play(goodbye_msg)
            # Explicitly cleanup avatar server
            self.cleanup_avatar_server()
            self.running = False
            return
            
        elif user_input.lower() in ['clear', 'reset']:
            msg = "Context cleared"
            self.ai_engine.clear_context()
            self.print_with_avatar(msg, AvatarState.IDLE)
            self.sound_manager.play_sound("brush_sfx")
            if self.voice_enabled:
                self.voice_engine.synthesize_and_play(msg)
            return
            
        elif user_input.lower() in ['voice', 'mute']:
            self.voice_enabled = not self.voice_enabled
            # The new VoiceManager doesn't need an explicit set_voice_enabled
            msg = f"Voice {'enabled' if self.voice_enabled else 'disabled'}"
            self.print_with_avatar(msg, AvatarState.IDLE)
            self.sound_manager.play_contextual_sound("notification" if self.voice_enabled else "error")
            if self.voice_enabled:
                self.voice_engine.synthesize_and_play("Voice enabled")
            return
            
        elif user_input.lower().startswith(('/profile', 'persona', 'character')):
            parts = user_input.split()
            if len(parts) > 1:
                profile_name = parts[1].strip()
                if self.voice_engine.set_profile(profile_name):
                    self.sound_manager.play_success_sequence("minor")
                    self.voice_engine.synthesize_and_play(f"Voice profile set to {profile_name}", background=False)
                else:
                    msg = f"Unknown profile. Available: {', '.join(self.voice_engine.profiles.keys())}"
                    self.print_with_avatar(msg, AvatarState.ERROR)
                    self.sound_manager.play_contextual_sound("error")
            else:
                msg = f"Please specify a profile. Available: {', '.join(self.voice_engine.profiles.keys())}"
                self.print_with_avatar(msg, AvatarState.IDLE)
            return
            
        elif user_input.lower() in ['stats', 'status']:
            self.display_system_stats()
            return
            
        elif user_input.lower() in ['context', 'device']:
            self.display_device_context()
            return
            
        elif user_input.lower() in ['tokens', 'usage']:
            self.display_token_stats()
            return
            
        elif user_input.lower() in ['animate', 'demo']:
            self.demo_animations()
            return
            
        elif user_input.lower().startswith('avatar'):
            self.handle_avatar_command(user_input)
            return
            
        elif user_input.lower() in ['help', 'h']:
            self.show_help()
            return
            
        # Generate AI response with animations
        self.start_animated_status("Thinking...", "thinking")
        self.send_avatar_update(user_input, "thinking")
        self.sound_manager.play_thinking_ambient()
        time.sleep(1.0)  # Give time to see thinking animation
        self.stop_animated_status()
        
        self.start_animated_status("Generating response...", "generating")
        self.send_avatar_update("", "generating")
        
        # Notify avatar dashboard that AI is starting to respond
        if AVATAR_AVAILABLE and is_avatar_server_running():
            send_chat_ai_start()
        
        time.sleep(0.5)  # Brief pause before starting generation
        self.stop_animated_status()
        
        print(f"{AvatarState.GENERATING.value} ", end="", flush=True)
        
        try:
            response_started = False
            full_response = ""
            token_count = 0
            estimated_max_tokens = 150  # TinyLlama default
            
            # Send initial progress
            if AVATAR_AVAILABLE and is_avatar_server_running():
                send_avatar_progress("generating", 0, 0, "Starting generation...")
            
            for token in self.ai_engine.generate_response(user_input):
                if not response_started:
                    print("\n", end="", flush=True)  # New line before response
                    response_started = True
                print(token, end="", flush=True)
                full_response += token
                token_count += 1
                
                # Send token to avatar dashboard
                if AVATAR_AVAILABLE and is_avatar_server_running():
                    send_chat_ai_chunk(token)
                
                # Send progress updates every few tokens
                if AVATAR_AVAILABLE and is_avatar_server_running() and token_count % 5 == 0:
                    progress = min((token_count / estimated_max_tokens) * 100, 95)
                    send_avatar_progress("generating", progress, token_count, f"Generated {token_count} tokens...")
                
            print()  # Final newline
            self.set_avatar_state(AvatarState.IDLE)
            
            # Notify avatar dashboard that AI response is complete
            if AVATAR_AVAILABLE and is_avatar_server_running():
                send_chat_ai_complete()
                send_avatar_progress("complete", 100, token_count, f"Response complete! {token_count} tokens generated.")
            
            self.context_sound_manager.play_response_sound(full_response, user_input)
            
            # Send avatar emotion update based on response
            if full_response.strip():
                self.send_avatar_update(full_response.strip(), "response")
            
            # Send post-response completion state
            self.send_avatar_update("Response complete", "post_response")
            
            # Display eco-friendly metrics and token usage
            self._display_post_response_metrics()
            
            # Synthesize voice in background
            if self.voice_enabled and full_response.strip():
                self.set_avatar_state(AvatarState.SPEAKING)
                self.send_avatar_update("", "speaking")
                self.voice_engine.synthesize_and_play(full_response.strip())
                self.set_avatar_state(AvatarState.IDLE)
                self.send_avatar_update("", "idle")
            
        except Exception as e:
            error_msg = f"Error: {e}"
            self.print_with_avatar(error_msg, AvatarState.ERROR)
            self.send_avatar_update(error_msg, "error")
            self.context_sound_manager.play_response_sound(error_msg, user_input)
            if self.voice_enabled:
                self.voice_engine.synthesize_and_play(error_msg)
            
    def show_help(self):
        """Display help information"""
        help_text = """
M1K3 Local AI CLI Commands:
  
  Chat Commands:
    <message>       Send message to AI
    clear, reset    Clear conversation context  
    stats, status   Show system statistics with animations
    context, device Show comprehensive device context
    tokens, usage   Show token usage and eco impact
    animate, demo   Demonstrate animation capabilities
    help, h         Show this help
    quit, q         Exit application
    
  Voice Commands:
    voice, mute     Toggle voice synthesis on/off
    /profile <name> Set voice profile (natural, assistant, broadcast, terminal)
    
  Avatar Commands:
    avatar start    Start the avatar web server
    avatar stop     Stop the avatar web server
    avatar status   Show avatar server and emotion status
    avatar emotion <emotion> [intensity] - Set avatar emotion (0-100)
    avatar style <style> [color] - Set avatar style and color
    avatar test     Test all avatar emotions
    
  Optimized Personas:
    assistant      Clean, clear voice with light processing (default)
    natural        Pure, unprocessed voice with maximum clarity
    pa_system      Public address system voice with gentle filtering
    broadcast      Radio-quality voice with professional sound  
    terminal       Retro computer terminal voice
    
  Avatar States:
    💤 Idle        Ready for input
    ⏳ Loading     Starting up or downloading
    🤔 Thinking    Processing your input  
    ⚡ Generating  Streaming AI response
    🔊 Speaking    Voice synthesis active
    ❌ Error       Something went wrong
    
  Features:
    • High-quality voice synthesis with faster, natural pacing
    • Rich CLI animations and visual effects
    • Comprehensive device context collection (privacy-focused)
    • Real-time system monitoring and statistics
    • Dynamic system-aware greetings based on device state
    • Hardware capability detection and display
    • Animated status indicators and progress bars
    • Context-aware conversations with streaming responses
"""
        print(help_text)
        
        if self.voice_enabled:
            self.voice_engine.synthesize_and_play("Here's what I can do for you")
    
    def display_system_stats(self):
        """Display animated system statistics"""
        self.start_animated_status("Collecting system statistics...", "processing")
        
        ai_stats = self.ai_engine.get_memory_usage()
        voice_status = self.voice_engine.get_status()
        metrics = self.system_monitor.collect_metrics()
        
        self.stop_animated_status()
        
        print("\n📊 System Statistics:")
        print("=" * 40)
        
        # AI Engine stats
        print(f"🧠 AI Engine:")
        print(f"   Memory Usage: {ai_stats['memory_mb']} MB")
        print(f"   Context: {ai_stats['context_tokens']} tokens")
        print(f"   Model: {ai_stats.get('model_name', 'SmolLM-135M')}")
        
        # Voice engine stats
        print(f"🔊 Voice Engine:")
        print(f"   Status: {'Enabled' if self.voice_enabled else 'Disabled'}")
        print(f"   Profile: {voice_status.get('current_profile', 'Unknown')}")
        print(f"   Model: {voice_status.get('model_name', 'Unknown')}")
        
        # System performance
        if metrics.cpu_usage is not None:
            print(f"⚙️  Performance:")
            print(f"   CPU Usage: {metrics.cpu_usage:.1f}%")
            print(f"   Memory: {metrics.memory_percent:.1f}%")
            if metrics.load_average:
                print(f"   Load Average: {metrics.load_average:.2f}")
        
        # Power and thermal
        if metrics.battery_percent is not None:
            battery_emoji = "🔋" if not metrics.battery_plugged else "⚡"
            print(f"{battery_emoji} Power:")
            print(f"   Battery: {metrics.battery_percent}% ({metrics.battery_status()})")
        
        if metrics.cpu_temp is not None:
            print(f"🌡️  Thermal:")
            print(f"   CPU Temp: {metrics.cpu_temp:.1f}°C ({metrics.thermal_status()})")
            
        if self.voice_enabled:
            status_msg = f"System running {metrics.performance_status()}"
            if metrics.battery_percent:
                status_msg += f", battery at {metrics.battery_percent} percent"
            self.voice_engine.synthesize_and_play(status_msg)
    
    def display_system_diagnostics(self, context_summary):
        """Display comprehensive system diagnostics and M1K3 capabilities"""
        try:
            metrics = self.system_monitor
            
            # Compact multi-line context display
            print(f"🔍 Context: {metrics.cpu_model} | Cores: {metrics.cpu_cores}c/{metrics.cpu_threads}", end="")
            if metrics.gpu_info:
                print(f" | GPU: {metrics.gpu_info}", end="")
            if metrics.memory_total_gb:
                print(f" | RAM: {metrics.memory_total_gb:.0f}GB", end="")
            print(f" | OS: {metrics.os_name} {metrics.os_version}", end="")
            if metrics.timezone:
                print(f" | TZ: {metrics.timezone}", end="")
            print(f" | Lo...")  # Truncated for space
            
            # M1K3 Capabilities Summary (single line)
            capabilities = []
            
            # AI Backend Status
            try:
                if hasattr(self.ai_engine, 'backend_name'):
                    capabilities.append(f"🤖 AI: {self.ai_engine.backend_name}")
                else:
                    capabilities.append("🤖 AI: Local")
            except:
                capabilities.append("🤖 AI: Available")
            
            # Voice Status
            if self.voice_enabled:
                capabilities.append("🗣️ Voice")
            
            # Avatar Status
            if AVATAR_AVAILABLE and self.avatar_controller:
                try:
                    if is_avatar_server_running():
                        capabilities.append("🧘 Avatar: Live")
                    else:
                        capabilities.append("🧘 Avatar: Ready")
                except:
                    capabilities.append("🧘 Avatar: Ready")
            
            # Model Information
            try:
                from local_model_manager import LocalModelManager
                manager = LocalModelManager()
                model_count = len(manager.available_models)
                if model_count > 0:
                    best_model = manager.get_best_model()
                    if best_model:
                        # Shorten model names for display
                        display_name = best_model.split('/')[-1] if '/' in best_model else best_model
                        display_name = display_name.replace('-1.1B-Chat-v1.0', '').replace('.Q4_K_M', '')
                        capabilities.append(f"📦 Model: {display_name}")
                    capabilities.append(f"🔧 {model_count} models")
            except:
                capabilities.append("📦 Models: Available")
            
            # Sound System
            try:
                from sound_manager import SoundManager
                sound_mgr = SoundManager()
                sound_count = len(sound_mgr.all_sounds)
                capabilities.append(f"🎮 {sound_count} SFX")
            except:
                capabilities.append("🎮 Audio")
            
            # Privacy & Environmental
            capabilities.append("🔒 100% Local")
            capabilities.append("🌱 Eco-Friendly")
            
            # Display capabilities in a compact format
            if capabilities:
                print(f"⚡ M1K3: {' | '.join(capabilities[:6])}")  # Limit to 6 items for space
                if len(capabilities) > 6:
                    print(f"        {' | '.join(capabilities[6:])}")
                    
        except Exception as e:
            # Fallback to simple context if diagnostics fail
            if context_summary:
                print(f"🔍 Context: {context_summary[:100]}{'...' if len(context_summary) > 100 else ''}")
            print(f"⚡ M1K3: 🤖 AI Ready | 🗣️ Voice {'✅' if self.voice_enabled else '❌'} | 🧘 Avatar Ready | 🔒 100% Local")

    def _collect_m1k3_context(self) -> dict:
        """Collect M1K3-specific context for enhanced greetings"""
        context = {
            'ai_ready': True,  # AI engine is always ready by this point
            'voice_enabled': self.voice_enabled,
            'avatar_ready': AVATAR_AVAILABLE and self.avatar_controller is not None,
            'avatar_live': False,
            'model_count': 0,
            'backend_name': 'Unknown'
        }
        
        # Check avatar server status
        if AVATAR_AVAILABLE:
            try:
                context['avatar_live'] = is_avatar_server_running()
            except:
                pass
        
        # Get AI backend information
        try:
            if hasattr(self.ai_engine, 'use_transformers') and self.ai_engine.use_transformers:
                context['backend_name'] = "HuggingFace"
            elif hasattr(self.ai_engine, 'use_ctransformers') and self.ai_engine.use_ctransformers:
                context['backend_name'] = "ctransformers"
            elif hasattr(self.ai_engine, '__class__'):
                context['backend_name'] = self.ai_engine.__class__.__name__
        except:
            pass
        
        # Get model count
        try:
            from local_model_manager import LocalModelManager
            manager = LocalModelManager()
            context['model_count'] = len(manager.available_models)
        except:
            pass
        
        return context

    def display_device_context(self):
        """Display comprehensive device context"""
        self.start_animated_status("Analyzing device capabilities...", "processing")
        
        metrics = self.system_monitor.collect_metrics()
        context_summary = self.system_monitor.get_context_summary(metrics)
        
        self.stop_animated_status()
        
        print("\n🖥️  Device Context:")
        print("=" * 50)
        
        # Hardware
        if metrics.cpu_model:
            print(f"💻 Hardware:")
            print(f"   CPU: {metrics.cpu_model}")
            if metrics.cpu_cores and metrics.cpu_threads:
                print(f"   Cores: {metrics.cpu_cores} physical, {metrics.cpu_threads} threads")
            if metrics.cpu_arch:
                print(f"   Architecture: {metrics.cpu_arch}")
            if metrics.gpu_info:
                print(f"   GPU: {metrics.gpu_info}")
            if metrics.memory_total_gb:
                print(f"   Memory: {metrics.memory_total_gb:.1f} GB")
        
        # System environment
        if metrics.os_name:
            print(f"🌍 Environment:")
            print(f"   OS: {metrics.os_name} {metrics.os_version or ''}")
            if metrics.hostname:
                print(f"   Device: {metrics.hostname}")
            if metrics.timezone:
                sign = "+" if (metrics.timezone_offset or 0) >= 0 else "-"
                print(f"   Timezone: {metrics.timezone} (UTC{sign}{abs(metrics.timezone_offset or 0)})")
            if metrics.locale_info:
                print(f"   Locale: {metrics.locale_info}")
            if metrics.current_time:
                print(f"   Time: {metrics.current_time}")
        
        # Capabilities
        capabilities = []
        if metrics.has_microphone:
            capabilities.append("🎤 Microphone")
        if metrics.has_speakers:
            capabilities.append("🔊 Audio Output")
        if metrics.has_wifi:
            capabilities.append("📶 WiFi")
        if metrics.has_ethernet:
            capabilities.append("🌐 Ethernet")
        if metrics.display_count and metrics.display_count > 1:
            capabilities.append(f"🖥️ {metrics.display_count} Displays")
        
        if capabilities:
            print(f"🔌 Capabilities:")
            for cap in capabilities:
                print(f"   {cap}")
        
        # Storage
        if metrics.disk_total_gb:
            print(f"💾 Storage:")
            print(f"   Total: {metrics.disk_total_gb:.1f} GB")
            print(f"   Used: {metrics.disk_usage_percent:.1f}%")
            print(f"   Free: {metrics.disk_free_gb:.1f} GB")
        
        print(f"\n📋 Summary:")
        print(f"   {context_summary}")
        
        if self.voice_enabled:
            self.voice_engine.synthesize_and_play("Device context collected successfully")
    
    def demo_animations(self):
        """Demonstrate animation capabilities"""
        print("\n🎬 Animation Demo:")
        print("=" * 30)
        
        # Typewriter effect
        print("\n1. Typewriter Effect:")
        self.animator.typewriter_effect("✨ M1K3 is typing this message character by character...")
        
        # Fade in effect
        print("\n2. Fade In Effect:")
        self.animator.fade_in_text("🌟 This text fades in gradually!")
        
        # Pulse effect
        print("\n3. Pulse Effect:")
        self.animator.pulse_text("💫 This message pulses with intensity!")
        
        # Progress bar
        print("\n4. Progress Bar:")
        for i in range(0, 101, 5):
            self.animator.progress_bar(i, 100, prefix="Demo Progress")
            time.sleep(0.1)
        
        # Avatar animations
        print("\n5. Avatar State Animations:")
        
        animations = [
            ("thinking", "Processing your request..."),
            ("generating", "Generating response..."),
            ("loading", "Loading resources..."),
            ("speaking", "Voice synthesis active..."),
        ]
        
        for anim_type, message in animations:
            print(f"\n   {anim_type.title()}:")
            self.animator.start_avatar_animation(anim_type, message, 0.3)
            time.sleep(2)
            self.animator.stop_animation()
        
        print("\n✅ Animation demo complete!")
        
        if self.voice_enabled:
            self.voice_engine.synthesize_and_play("Animation demonstration finished")
    
    def handle_avatar_command(self, user_input: str):
        """Handle avatar-related commands"""
        if not AVATAR_AVAILABLE:
            self.print_with_avatar("Avatar system not available. Install with: pip install websocket-server", AvatarState.ERROR)
            return
        
        parts = user_input.lower().split()
        if len(parts) == 1:
            # Just "avatar" - show status
            status = get_avatar_server_status()
            if status['running']:
                msg = f"🌐 Avatar server running at {status['http_url']}"
                msg += f"\n   WebSocket: ws://localhost:{status['ws_port']}"
                msg += f"\n   Connected clients: {status['connected_clients']}"
                self.print_with_avatar(msg, AvatarState.IDLE)
            else:
                self.print_with_avatar("🔴 Avatar server not running. Use 'avatar start' to begin.", AvatarState.IDLE)
            return
        
        command = parts[1] if len(parts) > 1 else ""
        
        if command in ['start', 'on', 'begin']:
            if is_avatar_server_running():
                self.print_with_avatar("🌐 Avatar server already running", AvatarState.IDLE)
            else:
                self.print_with_avatar("🚀 Starting avatar server...", AvatarState.LOADING)
                if start_avatar_server():
                    status = get_avatar_server_status()
                    msg = f"✅ Avatar server started!"
                    
                    # Add all available URLs
                    if status.get('urls'):
                        msg += "\n   📱 Access from:"
                        for url in status['urls']:
                            if 'localhost' in url or '127.0.0.1' in url:
                                msg += f"\n      Local:   {url}"
                            else:
                                msg += f"\n      Network: {url}"
                    else:
                        msg += f"\n   Open: {status['http_url']}"
                    
                    self.print_with_avatar(msg, AvatarState.IDLE)
                    if self.voice_enabled:
                        self.voice_engine.synthesize_and_play("Avatar server started")
                    
                    # Send initial emotion to avatar
                    if self.avatar_controller:
                        self.send_avatar_update("Hello! I'm your M1K3 AI companion.", "greeting")
                else:
                    self.print_with_avatar("❌ Failed to start avatar server", AvatarState.ERROR)
        
        elif command in ['stop', 'off', 'end']:
            if not is_avatar_server_running():
                self.print_with_avatar("🔴 Avatar server not running", AvatarState.IDLE)
            else:
                self.print_with_avatar("🛑 Stopping avatar server...", AvatarState.LOADING)
                stop_avatar_server()
                self.print_with_avatar("✅ Avatar server stopped", AvatarState.IDLE)
                if self.voice_enabled:
                    self.voice_engine.synthesize_and_play("Avatar server stopped")
        
        elif command in ['status', 'info']:
            status = get_avatar_server_status()
            if status['running']:
                msg = f"🌐 Avatar Server Status:\n"
                msg += f"   Status: ✅ Running\n"
                
                # Show all available URLs
                if status.get('urls'):
                    msg += f"   📱 Available on:\n"
                    for url in status['urls']:
                        if 'localhost' in url or '127.0.0.1' in url:
                            msg += f"      Local:   {url}\n"
                        else:
                            msg += f"      Network: {url}\n"
                else:
                    msg += f"   HTTP: {status['http_url']}\n"
                
                msg += f"   WebSocket: ws://0.0.0.0:{status['ws_port']}\n"
                msg += f"   Connected clients: {status['connected_clients']}\n"
                msg += f"   WebSocket available: {'✅' if status['websocket_available'] else '❌'}"
                
                if self.avatar_controller:
                    avatar_state = self.avatar_controller.get_current_state()
                    msg += f"\n   Current emotion: {avatar_state['emotion']}\n"
                    msg += f"   Current state: {avatar_state['state']}\n"
                    msg += f"   Current style: {avatar_state['style']}"
                
                self.print_with_avatar(msg, AvatarState.IDLE)
            else:
                self.print_with_avatar("🔴 Avatar server not running", AvatarState.IDLE)
        
        elif command in ['emotion', 'feel', 'mood']:
            if not is_avatar_server_running():
                self.print_with_avatar("❌ Avatar server not running. Use 'avatar start' first.", AvatarState.ERROR)
                return
            
            emotion = parts[2] if len(parts) > 2 else "happy"
            intensity = 70
            
            # Try to parse intensity if provided
            if len(parts) > 3:
                try:
                    intensity = min(max(int(parts[3]), 0), 100)
                except ValueError:
                    pass
            
            if self.avatar_controller:
                # Map emotion to avatar emotion
                emotion_map = {
                    'happy': AvatarEmotion.HAPPY,
                    'sad': AvatarEmotion.SAD,
                    'angry': AvatarEmotion.ANGRY,
                    'surprised': AvatarEmotion.SURPRISED,
                    'love': AvatarEmotion.LOVE,
                    'thinking': AvatarEmotion.THINKING,
                    'sleepy': AvatarEmotion.SLEEPY,
                    'excited': AvatarEmotion.EXCITED
                }
                
                avatar_emotion = emotion_map.get(emotion, AvatarEmotion.HAPPY)
                result = self.avatar_controller.update_emotion("", "", force_emotion=avatar_emotion)
                result['intensity'] = intensity
                
                send_avatar_emotion(result['emotion'], intensity, f"Emotion set to {emotion}")
                self.print_with_avatar(f"🎭 Avatar emotion set to {emotion} ({intensity}%)", AvatarState.IDLE)
        
        elif command in ['style', 'look']:
            if not is_avatar_server_running():
                self.print_with_avatar("❌ Avatar server not running. Use 'avatar start' first.", AvatarState.ERROR)
                return
            
            style = parts[2] if len(parts) > 2 else "robot"
            color = parts[3] if len(parts) > 3 else "#E25303"
            
            from avatar_server import get_avatar_server
            server = get_avatar_server()
            server.send_style_update(style, color)
            self.print_with_avatar(f"✨ Avatar style set to {style} with color {color}", AvatarState.IDLE)
        
        elif command in ['test', 'demo']:
            if not is_avatar_server_running():
                self.print_with_avatar("❌ Avatar server not running. Use 'avatar start' first.", AvatarState.ERROR)
                return
            
            self.print_with_avatar("🧪 Testing avatar emotions...", AvatarState.LOADING)
            emotions = ['happy', 'excited', 'thinking', 'surprised', 'love', 'sleepy']
            
            for emotion in emotions:
                send_avatar_emotion(emotion, 70, f"Testing {emotion} emotion")
                self.print_with_avatar(f"   Testing: {emotion}", AvatarState.THINKING)
                time.sleep(2)
            
            send_avatar_emotion('happy', 50, "Test complete!")
            self.print_with_avatar("✅ Avatar emotion test complete", AvatarState.IDLE)
        
        else:
            help_msg = """🧘 Avatar Commands:
  avatar start     - Start the avatar web server
  avatar stop      - Stop the avatar web server  
  avatar status    - Show server and avatar status
  avatar emotion <emotion> [intensity] - Set avatar emotion (0-100)
  avatar style <style> [color] - Set avatar style and color
  avatar test      - Test all avatar emotions
  
Available emotions: happy, sad, angry, surprised, love, thinking, sleepy, excited
Available styles: robot, organic, crystal, ghost, energy, cute"""
            
            self.print_with_avatar(help_msg, AvatarState.IDLE)
    
    def send_avatar_update(self, text: str, context: str = ""):
        """Send emotion update to avatar based on text analysis"""
        if not AVATAR_AVAILABLE or not is_avatar_server_running() or not self.avatar_controller:
            return
        
        try:
            # Analyze emotion from text
            result = self.avatar_controller.update_emotion(text, context)
            
            # Send to avatar server
            send_avatar_emotion(result['emotion'], result['intensity'], text[:100])
            
            # Update state based on context
            if context in ['error']:
                send_avatar_state('error')
            elif context in ['pre_thinking']:
                send_avatar_state('pre_thinking')
            elif context in ['thinking', 'processing']:
                send_avatar_state('thinking')
            elif context in ['generating']:
                send_avatar_state('generating')
            elif context in ['speaking']:
                send_avatar_state('speaking')
            elif context in ['post_response']:
                send_avatar_state('post_response')
            elif context in ['farewell']:
                send_avatar_state('farewell')
            else:
                send_avatar_state('idle')
                
        except Exception as e:
            print(f"Debug: Avatar update error: {e}")
        
    def run_interactive(self):
        """Run interactive CLI session with animations."""
        # A cleaner, more structured startup sequence
        print("\033[1m🧘 M1K3 - Local AI with Advanced Voice & Animations\033[0m")
        print("=" * 60)

        # Setup AI engine
        if not self.setup_ai():
            return 1
        
        self.sound_manager.play_startup_sequence()
        
        print("-" * 60)
        
        # Generate dynamic greeting based on system metrics
        try:
            metrics = self.system_monitor.collect_metrics()
            m1k3_context = self._collect_m1k3_context()
            greeting = generate_dynamic_greeting(metrics, m1k3_context)
            
            print(f"💬 {greeting}")
            
            # Display the new compact diagnostics
            self.display_system_diagnostics(None)
            
            print("-" * 60)
            print("Type '/help' for a list of commands or start chatting below.")
            
            # Speak the greeting if voice is enabled
            if self.voice_enabled:
                self.start_animated_status("Speaking greeting...", "speaking")
                self.voice_engine.synthesize_and_play(greeting, background=False)
                self.stop_animated_status()
                
        except Exception as e:
            print(f"\nReady to chat! (Greeting error: {e})")
            self.animated_print("Type 'help' for commands or start chatting!", effect="typewriter")
        
        while self.running:
            try:
                # Get user input with avatar prompt
                user_input = input(f"\n{self.avatar_state.value} > ").strip()
                
                if not user_input:
                    continue
                    
                self.handle_user_input(user_input)
                
            except KeyboardInterrupt:
                self.print_with_avatar("\nExiting...", AvatarState.IDLE)
                self.cleanup_avatar_server()
                break
            except EOFError:
                break
            except Exception as e:
                self.print_with_avatar(f"Unexpected error: {e}", AvatarState.ERROR)
                
        return 0
        
    def run_single_query(self, query: str):
        """Run single query mode"""
        if not self.setup_ai():
            return 1
            
        self.handle_user_input(query)
        return 0

    def _animate_context_trim(self, messages_removed: int):
        """Animate context trimming process"""
        self.animator.animate_context_trimming(messages_removed)
    
    def _display_post_response_metrics(self):
        """Display token usage and eco metrics after response"""
        # Get current token usage
        token_usage = self.ai_engine.get_token_usage()
        
        # Display animated token bar
        token_display = self.animator.animate_token_bar(
            token_usage["current_tokens"], 
            token_usage["max_tokens"]
        )
        print(token_display)
        
        # Get and display eco metrics
        eco_metrics = self.ai_engine.get_eco_metrics()
        self.animator.animate_eco_metrics(
            eco_metrics["energy_saved_kwh"],
            eco_metrics["water_saved_gallons"], 
            eco_metrics["co2_saved_grams"]
        )
        
        # Show privacy shield animation
        self.animator.animate_privacy_shield(eco_metrics["data_transmitted"])
        
    def display_token_stats(self):
        """Display comprehensive token and eco statistics"""
        print("🧠 M1K3 Token & Eco Statistics")
        print("=" * 50)
        
        # Token usage
        token_usage = self.ai_engine.get_token_usage()
        token_display = self.animator.animate_token_bar(
            token_usage["current_tokens"], 
            token_usage["max_tokens"]
        )
        print(token_display)
        print(f"📊 Messages in context: {token_usage['messages_count']}")
        print(f"🎯 Trimming threshold: {token_usage['trimming_threshold']:,} tokens")
        
        if token_usage["needs_trimming"]:
            print("⚠️ Context will be trimmed on next message")
        else:
            remaining = token_usage["trimming_threshold"] - token_usage["current_tokens"]
            print(f"✅ {remaining:,} tokens until trimming")
            
        print()
        
        # Eco metrics
        eco_metrics = self.ai_engine.get_eco_metrics()
        print("🌱 Environmental Impact:")
        print(f"   ⚡ Energy saved: {eco_metrics['energy_saved_kwh']} kWh")
        print(f"   💧 Water saved: {eco_metrics['water_saved_gallons']} gallons")
        print(f"   🌍 CO2 prevented: {eco_metrics['co2_saved_grams']}g")
        print(f"   🔒 Privacy score: {eco_metrics['privacy_score']}")
        print(f"   📡 Data transmitted: {eco_metrics['data_transmitted']}")
        print(f"   💬 Responses generated: {eco_metrics['responses_count']}")
        
        # Send metrics to avatar dashboard
        if AVATAR_AVAILABLE and is_avatar_server_running():
            avatar_metrics = {
                "energy_saved": eco_metrics['energy_saved_kwh'],
                "water_saved": eco_metrics['water_saved_gallons'].replace(' gallons', '').replace(' ml', ''),
                "co2_saved": eco_metrics['co2_saved_grams'].replace('g', ''),
                "message_count": eco_metrics['responses_count']
            }
            send_metrics_update(avatar_metrics)

def main():
    parser = argparse.ArgumentParser(description="M1K3 Local AI CLI with Voice")
    parser.add_argument("--query", "-q", help="Single query mode")
    parser.add_argument("--download-only", action="store_true", help="Download model and exit")
    parser.add_argument("--model", default="SmolLM-135M-Q4_K_M", help="Model to download")
    parser.add_argument("--no-voice", action="store_true", help="Disable voice synthesis")
    parser.add_argument("--test-voice", action="store_true", help="Test voice synthesis only")
    parser.add_argument("--with-avatar", action="store_true", help="Auto-start avatar server and open browser")
    parser.add_argument("--avatar-port", type=int, default=8080, help="Avatar server HTTP port (default: 8080)")
    parser.add_argument("--no-browser", action="store_true", help="Don't auto-open browser with --with-avatar")
    
    args = parser.parse_args()
    
    if args.download_only:
        print(f"Downloading {args.model}...")
        model_path = download_model()  # Use default model
        if model_path:
            print(f"Model downloaded successfully: {model_path}")
            return 0
        else:
            print("Failed to download model")
            return 1
    
    if args.test_voice:
        from enhanced_voice_engine import create_voice_engine
        engine = create_voice_engine()
        if engine.load_model():
            print("🔊 Testing voice synthesis...")
            engine.synthesize_and_play("Voice synthesis test successful! M1K3 is ready to speak.", background=False)
            return 0
        else:
            print("❌ Failed to load voice model")
            return 1
            
    voice_enabled = not args.no_voice
    auto_avatar = args.with_avatar
    avatar_port = args.avatar_port
    open_browser = not args.no_browser
    
    cli = M1K3CLI(
        voice_enabled=voice_enabled,
        auto_avatar=auto_avatar,
        avatar_port=avatar_port,
        open_browser=open_browser
    )
    
    if args.query:
        return cli.run_single_query(args.query)
    else:
        return cli.run_interactive()

if __name__ == "__main__":
    sys.exit(main())