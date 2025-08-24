#!/usr/bin/env python3
"""
M1K3 CLI Application with Local AI Integration
Command-line interface with avatar state management
"""

import os
import sys
import time
import datetime
import threading
import webbrowser
import atexit
import signal
from enum import Enum
from typing import Optional
import argparse
from dataclasses import asdict
import json

try:
    import websocket
    WEBSOCKET_CLIENT_AVAILABLE = True
except ImportError:
    WEBSOCKET_CLIENT_AVAILABLE = False
    print("⚠️  WebSocket client not available. Run: pip install websocket-client")

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

# Try to import RAG engine for enhanced capabilities
try:
    from m1k3_rag_integration import M1K3RAGIntegratedEngine
    RAG_ENGINE_AVAILABLE = True
    print("🧠 RAG (Retrieval-Augmented Generation) engine available")
except ImportError as e:
    RAG_ENGINE_AVAILABLE = False
    print(f"⚠️  RAG engine not available: {e}")

from download_model import download_model
from enhanced_voice_engine import create_voice_engine
from system_metrics import SystemMonitor
from cli_animations import CLIAnimator, AnimationType
from sound_manager import SoundManager, ContextualSoundManager
from llm_greeting_engine import LLMGreetingEngine, create_greeting_context

# Intelligent TTS System
try:
    from intelligent_tts_controller import create_intelligent_tts_controller
    from model_output_parser import parse_model_output, ContentType
    from content_specific_effects import create_content_effects_manager
    INTELLIGENT_TTS_AVAILABLE = True
    print("🎭 Intelligent TTS system with content-specific voice effects available")
except ImportError as e:
    INTELLIGENT_TTS_AVAILABLE = False
    print(f"⚠️  Intelligent TTS not available: {e}")

# Model transparency engine
try:
    from model_transparency import ModelTransparencyEngine, TransparencyLevel, transparency_engine
    TRANSPARENCY_AVAILABLE = True
    print("✅ Model transparency engine available")
except ImportError:
    TRANSPARENCY_AVAILABLE = False
    print("⚠️  Model transparency engine not available")

# Streaming response filter
try:
    from streaming_response_filter import filter_colon_prefix
    STREAMING_FILTER_AVAILABLE = True
except ImportError:
    STREAMING_FILTER_AVAILABLE = False
    print("⚠️  Streaming response filter not available")

# Voice text preprocessor
try:
    from voice_text_preprocessor import preprocess_for_voice_synthesis
    VOICE_PREPROCESSOR_AVAILABLE = True
except ImportError:
    VOICE_PREPROCESSOR_AVAILABLE = False
    print("⚠️  Voice text preprocessor not available")

# Avatar system imports
try:
    from avatar_server import (
        start_avatar_server, stop_avatar_server, is_avatar_server_running,
        send_avatar_emotion, send_avatar_state, send_avatar_progress, get_avatar_server_status,
        send_chat_ai_start, send_chat_ai_chunk, send_chat_ai_complete, send_sound_trigger, send_metrics_update,
        send_classification_update, send_thinking_phase_update, send_generation_stream_update
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

class WebSocketMonitor:
    """Monitor WebSocket connection and handle incoming messages."""
    
    def __init__(self, ws_url: str, message_callback):
        self.ws_url = ws_url
        self.message_callback = message_callback
        self.ws = None
        self.thread = None
        self.running = False
        
    def start(self):
        """Start the WebSocket monitor in a separate thread."""
        if not WEBSOCKET_CLIENT_AVAILABLE:
            return False
            
        self.running = True
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()
        return True
        
    def stop(self):
        """Stop the WebSocket monitor."""
        self.running = False
        if self.ws:
            try:
                self.ws.close()
            except:
                pass
        if self.thread:
            self.thread.join(timeout=1.0)
            
    def _run(self):
        """Main WebSocket connection loop."""
        while self.running:
            try:
                print(f"🔌 WebSocketMonitor attempting connection to {self.ws_url}")
                self.ws = websocket.WebSocket()
                self.ws.settimeout(5.0)  # 5 second timeout
                self.ws.connect(self.ws_url)
                print(f"✅ WebSocketMonitor connected successfully to {self.ws_url}")
                
                while self.running:
                    try:
                        message = self.ws.recv()
                        if message and self.message_callback:
                            # Parse the message if it's JSON
                            try:
                                data = json.loads(message)
                                if data.get('type') == 'user_input' and data.get('message'):
                                    self.message_callback(data['message'])
                            except json.JSONDecodeError:
                                # Treat as plain text message
                                self.message_callback(message)
                    except websocket.WebSocketTimeoutException:
                        continue
                    except websocket.WebSocketException as e:
                        if self.running:  # Only log if we're still trying to run
                            print(f"❌ WebSocket error: {e}")
                        break
                        
            except Exception as e:
                if self.running:  # Only log if we're still trying to run
                    print(f"❌ WebSocket connection failed: {e}")
                    
            # Wait before reconnecting
            if self.running:
                print(f"🔄 WebSocketMonitor will retry connection in 5 seconds...")
                time.sleep(5)

class M1K3CLI:
    def __init__(self, voice_enabled: bool = True, auto_avatar: bool = False, avatar_port: int = 8080, open_browser: bool = True, transparency_level: str = "basic", rag_enabled: bool = False):
        # Initialize system monitoring first to gather context
        self.system_monitor = SystemMonitor()
        
        # Avatar configuration
        self.auto_avatar = auto_avatar
        self.avatar_port = avatar_port
        self.open_browser = open_browser
        
        # Websocket bridge
        self.websocket_monitor = None
        
        # Initialize transparency engine
        self.transparency_enabled = TRANSPARENCY_AVAILABLE
        print(f"🔍 TRANSPARENCY_AVAILABLE: {TRANSPARENCY_AVAILABLE}")
        
        if self.transparency_enabled:
            transparency_map = {
                "off": TransparencyLevel.OFF,
                "basic": TransparencyLevel.BASIC,
                "detailed": TransparencyLevel.DETAILED,
                "full": TransparencyLevel.FULL,
                "debug": TransparencyLevel.DEBUG
            }
            level = transparency_map.get(transparency_level.lower(), TransparencyLevel.BASIC)
            print(f"🔍 Setting transparency level: {transparency_level} -> {level}")
            transparency_engine.set_transparency_level(level)
            print(f"🔍 Engine transparency level: {transparency_engine.transparency_level}")
            print(f"🔍 Real-time display enabled: {transparency_engine.enable_real_time_display}")
            
            # Test transparency immediately in debug mode
            if transparency_level.lower() == "debug":
                print("🔍 DEBUG MODE: Testing transparency output...")
                transparency_engine.log_decision("initialization", "CLI startup", "debug_test", 
                                                "Testing transparency system initialization", confidence=1.0)
                print("🔍 DEBUG MODE: Force enabling all transparency features")
                transparency_engine.enable_real_time_display = True
        else:
            print("❌ Model transparency not available - install model_transparency.py")
        
        # Initialize AI engine with RAG support if requested
        self.rag_enabled = rag_enabled
        if rag_enabled and RAG_ENGINE_AVAILABLE:
            try:
                kb_path = "knowledge/comprehensive_knowledge_base.json"
                if os.path.exists(kb_path):
                    self.ai_engine = M1K3RAGIntegratedEngine(
                        knowledge_base_path=kb_path,
                        enable_rag=True,
                        auto_load=True
                    )
                    print("🧠 Initialized with RAG-enhanced AI engine")
                    print(f"📚 Knowledge base loaded: {kb_path}")
                else:
                    print(f"⚠️  Knowledge base not found at {kb_path}")
                    print("🔄 Falling back to standard AI engine")
                    self.rag_enabled = False
                    self.ai_engine = LocalAIEngine() if REAL_AI_AVAILABLE else SimpleAIEngine()
            except Exception as e:
                print(f"⚠️  Failed to initialize RAG engine: {e}")
                print("🔄 Falling back to standard AI engine")
                self.rag_enabled = False
                self.ai_engine = LocalAIEngine() if REAL_AI_AVAILABLE else SimpleAIEngine()
        else:
            # Use standard AI engine
            if REAL_AI_AVAILABLE:
                self.ai_engine = LocalAIEngine()
                print("🚀 Initialized with standard AI inference engine")
            else:
                self.ai_engine = SimpleAIEngine()
                print("🎭 Initialized with mock AI engine (demo mode)")
            
        self.voice_engine = create_voice_engine()
        
        # Initialize intelligent TTS controller if available
        if INTELLIGENT_TTS_AVAILABLE:
            self.intelligent_tts_controller = create_intelligent_tts_controller(self.voice_engine)
            print("🎭 Intelligent TTS controller initialized with content-specific voice effects")
        else:
            self.intelligent_tts_controller = None
            print("⚠️  Using basic voice synthesis (intelligent TTS unavailable)")
        
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
    
    def _safe_voice_synthesis(self, text: str, background: bool = True, use_intelligent_tts: bool = False):
        """Safely synthesize voice with text preprocessing to prevent warnings"""
        if not self.voice_enabled or not text or not text.strip():
            return
        
        try:
            # Preprocess text to prevent phonemizer warnings
            if VOICE_PREPROCESSOR_AVAILABLE:
                processed_text = preprocess_for_voice_synthesis(text.strip())
                if not processed_text:
                    return  # Skip empty or unsuitable text
            else:
                processed_text = text.strip()
            
            # Use intelligent TTS for AI responses if available and requested
            if use_intelligent_tts and self.intelligent_tts_controller and INTELLIGENT_TTS_AVAILABLE:
                try:
                    # Process with intelligent TTS for content-specific voice effects
                    results = self.intelligent_tts_controller.process_text_with_parsing(processed_text)
                    
                    if results and any(r.success for r in results):
                        # Successful intelligent TTS processing
                        return
                    else:
                        # Fall back to basic synthesis if intelligent TTS fails
                        print("⚠️  Intelligent TTS failed, falling back to basic synthesis")
                        
                except Exception as e:
                    print(f"⚠️  Intelligent TTS error: {e}, falling back to basic synthesis")
            
            # Basic voice synthesis (fallback or for non-AI responses)
            self.voice_engine.synthesize_and_play(processed_text, background=background)
            
        except Exception as e:
            # Handle audio errors more gracefully
            error_msg = str(e).lower()
            if any(keyword in error_msg for keyword in ['portaudio', 'audio', 'stream', 'device']):
                # Audio device related errors - disable voice to prevent spam
                if hasattr(self, '_audio_error_count'):
                    self._audio_error_count += 1
                else:
                    self._audio_error_count = 1
                
                # After 3 audio errors, disable voice to prevent spam
                if self._audio_error_count >= 3:
                    self.voice_enabled = False
                    print("❌ Audio playback failed multiple times. Voice disabled for this session.")
                    print("   Check audio device settings and permissions.")
            # For other errors, silently continue
    
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
        """Initialize AI engine with enhanced system context."""
        self._log_startup_message("INFO", "Collecting enhanced device context...")
        metrics = self.system_monitor.collect_metrics()
        
        # Create enhanced context using our greeting context system
        from llm_greeting_engine import create_greeting_context
        m1k3_context = {
            'ai_model': getattr(self.ai_engine, '_current_model_name', 'Local AI'),
            'voice_enabled': self.voice_enabled,
            'avatar_enabled': hasattr(self, 'avatar_controller') and self.avatar_controller is not None
        }
        
        enhanced_context = create_greeting_context(metrics, m1k3_context)
        
        # Convert to dict and add additional CLI-specific context
        session_context = asdict(enhanced_context) if enhanced_context else {}
        
        # Add CLI-specific information
        session_context.update({
            'interface_type': 'CLI',
            'transparency_mode': getattr(self, 'transparency_level', 'basic'),
            'animation_enabled': True,
            'sound_enabled': hasattr(self, 'sound_manager') and self.sound_manager is not None
        })
        
        if hasattr(self.ai_engine, 'set_session_context'):
            self.ai_engine.set_session_context(session_context)
            self._log_startup_message("OK", "Enhanced AI context injected successfully")
        
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
        """Initialize AI engine and voice with fast parallel loading."""
        
        # Try fast parallel initialization first
        try:
            return self.setup_ai_parallel()
        except ImportError:
            print("⚠️  Fast startup not available, using legacy initialization...")
            return self.setup_ai_legacy()
        except Exception as e:
            print(f"⚠️  Fast startup failed ({e}), using legacy initialization...")
            return self.setup_ai_legacy()
    
    def setup_ai_parallel(self) -> bool:
        """Fast parallel initialization using FastStartupManager"""
        from fast_startup_manager import fast_initialize_m1k3
        from performance_monitor import get_performance_monitor, PerformanceEventType
        
        # Start performance monitoring
        perf_monitor = get_performance_monitor()
        
        with perf_monitor.measure("m1k3_startup", PerformanceEventType.STARTUP):
            self._log_startup_message("INFO", "🚀 Starting fast parallel initialization...")
            
            # Initialize with progress display
            success, results = fast_initialize_m1k3(self, show_progress=True)
            
            if success:
                self._log_startup_message("OK", "✅ Fast initialization completed!")
                
                # Show what's ready and what's still loading
                ready_components = [name for name, result in results.items() 
                                  if result.status.value == "ready"]
                loading_components = [name for name, result in results.items() 
                                    if result.status.value == "loading"]
                
                if ready_components:
                    self._log_startup_message("OK", f"Ready: {', '.join(ready_components)}")
                
                if loading_components:
                    self._log_startup_message("INFO", f"Still loading: {', '.join(loading_components)}")
                    print("💡 You can start using M1K3 while components finish loading!")
                
                return True
            else:
                self._log_startup_message("ERROR", "❌ Essential components failed to load")
                return False
    
    def setup_ai_legacy(self) -> bool:
        """Legacy sequential initialization (fallback)"""
        self._log_startup_message("INFO", "Initializing AI Engine (legacy mode)...")
        
        # Check if model exists
        if not self.ai_engine.is_model_available():
            self.start_animated_status("Downloading SmolLM-135M model...", "loading")
            from download_model import download_model
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
            "/transparency": self.handle_transparency_command,
            "/explain": self.handle_explain_command,
            "/classify": self.handle_classify_command,
            "/model": self.handle_model_command,
            "/models": self.list_available_models,
            "/performance": self.handle_performance_command,
            "/perf": self.handle_performance_command,
            "/exit": lambda: setattr(self, 'running', False)
        }
        
        if command in action_map:
            print(f"Executing action: {command}")
            # Pass the full command for commands that need it
            if command in ["/sound", "/transparency", "/explain", "/classify", "/model", "/performance", "/perf"]:
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

    def handle_transparency_command(self, user_input: str = ""):
        """Handle transparency-related commands"""
        if not TRANSPARENCY_AVAILABLE:
            self.print_with_avatar("❌ Transparency engine not available", AvatarState.ERROR)
            return
            
        parts = user_input.lower().split()
        command = parts[1] if len(parts) > 1 else "status"

        if command in ["off", "disable"]:
            transparency_engine.set_transparency_level(TransparencyLevel.OFF)
            self.print_with_avatar("🔍 Transparency disabled", AvatarState.IDLE)
        elif command in ["basic", "on", "enable"]:
            transparency_engine.set_transparency_level(TransparencyLevel.BASIC)
            self.print_with_avatar("🔍 Basic transparency enabled (generation stats)", AvatarState.IDLE)
        elif command in ["detailed", "verbose"]:
            transparency_engine.set_transparency_level(TransparencyLevel.DETAILED)
            self.print_with_avatar("🔍 Detailed transparency enabled (parameters, progress)", AvatarState.IDLE)
        elif command in ["full", "complete"]:
            transparency_engine.set_transparency_level(TransparencyLevel.FULL)
            self.print_with_avatar("🔍 Full transparency enabled (processing analysis)", AvatarState.IDLE)
        elif command in ["debug", "maximum"]:
            transparency_engine.set_transparency_level(TransparencyLevel.DEBUG)
            self.print_with_avatar("🔍 Debug transparency enabled (maximum detail)", AvatarState.IDLE)
        elif command == "summary":
            transparency_engine.display_session_summary()
        elif command in ["status", "info"]:
            print(f"🔍 Current transparency level: {transparency_engine.transparency_level.name}")
            print(f"🔍 Real-time display: {transparency_engine.enable_real_time_display}")
            print(f"🔍 Force debug mode: {transparency_engine.force_debug_mode}")
        elif command in ["test", "verify"]:
            print("🔍 Testing transparency system...")
            transparency_engine.log_decision("test", "test_input", "test_output", "Testing transparency", confidence=0.95)
            transparency_engine.show_model_reasoning("test query", "test formatted prompt", "test_task", {"test_param": "test_value"})
            transparency_engine.show_streaming_progress(25, 100, "test_token")
            print("🔍 Transparency test complete")
        elif command in ["force", "forceall"]:
            transparency_engine.set_debug_mode(True)
            self.print_with_avatar("🔍 Debug mode activated - forcing all transparency output", AvatarState.IDLE)
        elif command == "export":
            data = transparency_engine.export_transparency_data()
            if data:
                print(f"📄 Transparency data exported ({len(data)} keys)")
                print(f"   Session responses: {data['summary']['total_responses']}")
                print(f"   Model decisions: {data['summary']['total_decisions']}")
        else:  # status
            current_level = transparency_engine.transparency_level
            msg = f"🔍 Transparency Status:\n"
            msg += f"   Current Level: {current_level.name}\n"
            msg += f"   Available Levels: off, basic, detailed, full, debug\n"
            msg += f"   Session Responses: {len(transparency_engine.current_session_stats)}\n"
            msg += f"   Model Decisions Logged: {len(transparency_engine.decisions)}"
            self.print_with_avatar(msg, AvatarState.IDLE)

    def handle_user_input(self, user_input: str):
        """Process user input and generate AI response."""
        
        # Track query start time for session statistics
        query_start_time = time.time()
        
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
            # Save session statistics before exit
            if hasattr(self, 'stats_tracker') and self.stats_tracker:
                try:
                    self.stats_tracker.save_stats()
                    print("\n📊 Session statistics saved successfully!")
                except Exception as e:
                    print(f"\n⚠️  Could not save statistics: {e}")
            
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
            
        elif user_input.lower().startswith('/tts'):
            parts = user_input.split()
            if len(parts) > 1 and parts[1].lower() == 'status':
                self.show_tts_status()
            else:
                self.print_with_avatar("Usage: /tts status - Show intelligent TTS system status", AvatarState.IDLE)
            return
            
        elif user_input.lower() in ['stats', 'status']:
            self.display_system_stats()
            
            # Also display session statistics if available
            if hasattr(self, 'stats_tracker') and self.stats_tracker:
                print("\n📊 SESSION STATISTICS")
                print("─" * 40)
                print(self.stats_tracker.get_formatted_stats_display(
                    max_tokens=getattr(self.ai_engine, 'max_tokens', 8192)
                ))
                
                # Show an exciting insight
                insight = self.stats_tracker.get_exciting_insight()
                print(f"\n💡 INSIGHT: {insight}")
            
            return
            
        elif user_input.lower() in ['context', 'device']:
            self.display_device_context()
            return
            
        elif user_input.lower() in ['tokens', 'usage']:
            self.display_token_stats()
            return
            
        elif user_input.lower() in ['session', 'session-stats']:
            # Display detailed session statistics
            if hasattr(self, 'stats_tracker') and self.stats_tracker:
                print("\n📈 DETAILED SESSION STATISTICS")
                print("═" * 50)
                
                stats_summary = self.stats_tracker.get_stats_summary()
                
                print(f"🕰️  Session Duration: {stats_summary['duration']}")
                print(f"💬 Queries Processed: {stats_summary['queries']}")
                print(f"📊 Tokens Generated: {stats_summary['tokens']}")
                print(f"⚡ Avg Response Time: {stats_summary['avg_response_time']:.2f}s")
                print(f"🌊 Water Saved: {stats_summary['water_saved_ml']:.0f}ml")
                print(f"🔋 Energy Saved: {stats_summary['energy_saved_wh']:.0f}Wh")
                print(f"🌱 CO₂ Prevented: {stats_summary['co2_saved_g']:.0f}g")
                print(f"🎆 Features Used: {stats_summary['features_used']}")
                print(f"🏆 Achievements: {stats_summary['achievements']}")
                
                # Show hardware insight
                try:
                    from hardware_insights import generate_hardware_insight
                    metrics = self.system_monitor.collect_metrics()
                    hardware_insight = generate_hardware_insight(metrics)
                    print(f"\n💻 HARDWARE INSIGHT: {hardware_insight}")
                except:
                    pass
                
                # Show exciting insight
                insight = self.stats_tracker.get_exciting_insight()
                print(f"\n💡 SESSION INSIGHT: {insight}")
                
            else:
                print("\n⚠️  Session statistics not available")
            return
            
        elif user_input.lower() in ['animate', 'demo']:
            self.demo_animations()
            return
            
        elif user_input.lower().startswith('avatar'):
            # Record avatar feature usage
            if hasattr(self, 'stats_tracker') and self.stats_tracker:
                self.stats_tracker.record_feature_use("avatar")
            
            self.handle_avatar_command(user_input)
            return
            
        elif user_input.lower() in ['help', 'h']:
            self.show_help()
            return
            
        # Generate AI response with minimal delay
        self.start_animated_status("Thinking...", "thinking")
        self.send_avatar_update(user_input, "thinking")
        self.sound_manager.play_thinking_ambient()
        
        # Record AI feature usage
        if hasattr(self, 'stats_tracker') and self.stats_tracker:
            self.stats_tracker.record_feature_use("ai")
        
        time.sleep(0.1)  # Brief visual feedback
        self.stop_animated_status()
        
        self.start_animated_status("Generating response...", "generating")
        self.send_avatar_update("", "generating")
        
        # Notify avatar dashboard that AI is starting to respond
        if AVATAR_AVAILABLE and is_avatar_server_running():
            send_chat_ai_start()
        
        self.stop_animated_status()
        
        print(f"{AvatarState.GENERATING.value} ", end="", flush=True)
        
        try:
            response_started = False
            full_response = ""
            token_count = 0
            estimated_max_tokens = 150  # TinyLlama default
            transparency_start_time = None
            
            # Initialize transparency tracking
            if TRANSPARENCY_AVAILABLE:
                model_name = getattr(self.ai_engine, '_current_model_name', 'Unknown')
                backend_type = "HuggingFace" if getattr(self.ai_engine, 'use_transformers', False) else "ctransformers" if getattr(self.ai_engine, 'use_ctransformers', False) else "SimpleAI"
                
                # Get generation parameters with classification metadata
                classification_metadata = None
                try:
                    if hasattr(self.ai_engine, '_get_adaptive_generation_params'):
                        params = self.ai_engine._get_adaptive_generation_params(estimated_max_tokens, user_input)
                        
                        # Extract classification metadata if available
                        if isinstance(params, dict) and '_metadata' in params:
                            classification_metadata = params['_metadata']
                            
                    else:
                        params = {"max_tokens": estimated_max_tokens}
                except:
                    params = {"max_tokens": estimated_max_tokens}
                
                # Check if clarification is needed
                if classification_metadata and classification_metadata.get('intent') == 'needs_clarification':
                    self.stop_animated_status()
                    clarification = self.ai_engine.generate_clarification_response(user_input, classification_metadata)
                    self.print_with_avatar(clarification, AvatarState.IDLE)
                    
                    # Send clarification to avatar if available
                    if AVATAR_AVAILABLE and is_avatar_server_running():
                        send_chat_ai_complete()
                        self.send_avatar_update(clarification, "clarification")
                    
                    # Play clarification sound
                    self.sound_manager.play_contextual_sound("notification")
                    
                    # Speak clarification if voice enabled
                    if self.voice_enabled:
                        self._safe_voice_synthesis(clarification)
                    
                    return  # End here, wait for user's clarification
                
                # Debug transparency before processing
                if self.transparency_enabled:
                    print(f"🔍 DEBUG: About to call transparency with level: {transparency_engine.transparency_level}")
                    print(f"🔍 DEBUG: Real-time display: {transparency_engine.enable_real_time_display}")
                    import sys
                    sys.stdout.flush()
                
                transparency_start_time = transparency_engine.start_processing(model_name, backend_type, params)
                
                # Show model reasoning if transparency is enabled
                if hasattr(self.ai_engine, 'context') and self.ai_engine.context.messages:
                    formatted_prompt = self.ai_engine.get_formatted_prompt(user_input) if hasattr(self.ai_engine, 'get_formatted_prompt') else user_input
                    print(f"🔍 DEBUG: Calling show_model_reasoning with prompt length: {len(formatted_prompt)}")
                    transparency_engine.show_model_reasoning(user_input, formatted_prompt, "conversational", params)
                    import sys
                    sys.stdout.flush()
                
                # Send enhanced classification data to avatar if available
                if classification_metadata and AVATAR_AVAILABLE and is_avatar_server_running():
                    self.send_avatar_update(user_input, "generating", classification_metadata)
                    
                    # Send thinking phase updates based on intent
                    intent = classification_metadata.get('intent', 'unknown')
                    if intent == 'mathematical_calculation':
                        send_thinking_phase_update("calculating", 10, "Processing mathematical expression", classification_metadata.get('confidence', 0.7))
                    elif intent == 'creative_writing':
                        send_thinking_phase_update("synthesizing", 15, "Generating creative content", classification_metadata.get('confidence', 0.7))
                    elif intent in ['explanation_request', 'instruction_request']:
                        send_thinking_phase_update("reasoning", 20, "Structuring explanation", classification_metadata.get('confidence', 0.7))
                    else:
                        send_thinking_phase_update("analyzing", 10, "Processing request", classification_metadata.get('confidence', 0.7))
            
            # Send initial progress
            if AVATAR_AVAILABLE and is_avatar_server_running():
                send_avatar_progress("generating", 0, 0, "Starting generation...")
            
            # Apply streaming filter if available
            token_stream = self.ai_engine.generate_response(user_input, max_tokens=estimated_max_tokens)
            if STREAMING_FILTER_AVAILABLE:
                try:
                    token_stream = filter_colon_prefix(token_stream)
                except Exception as e:
                    print(f"⚠️  Streaming filter error: {e}")
                    # Fall back to original stream
                    token_stream = self.ai_engine.generate_response(user_input, max_tokens=estimated_max_tokens)
            
            # Initialize generation start time for progress tracking
            generation_start_time = time.time()
            
            for token in token_stream:
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
                    elapsed = time.time() - generation_start_time
                    generation_speed = token_count / elapsed if elapsed > 0 else 0
                    
                    send_avatar_progress("generating", progress, token_count, f"Generated {token_count} tokens...")
                    
                    # Send generation stream update with current content preview
                    content_preview = full_response[-30:] if len(full_response) > 30 else full_response
                    send_generation_stream_update(token_count, generation_speed, content_preview)
                
                # Show streaming progress in transparency mode
                if TRANSPARENCY_AVAILABLE and token_count % 10 == 0:
                    transparency_engine.show_streaming_progress(token_count, estimated_max_tokens, token)
                
            print()  # Final newline
            self.set_avatar_state(AvatarState.IDLE)
            
            # Notify avatar dashboard that AI response is complete
            if AVATAR_AVAILABLE and is_avatar_server_running():
                send_chat_ai_complete()
                send_avatar_progress("complete", 100, token_count, f"Response complete! {token_count} tokens generated.")
            
            self.context_sound_manager.play_response_sound(full_response, user_input)
            
            # Transparency analysis and completion tracking
            if TRANSPARENCY_AVAILABLE and transparency_start_time:
                # Analyze response quality
                analysis = transparency_engine.analyze_response_quality(full_response)
                
                # Complete processing tracking
                model_name = getattr(self.ai_engine, '_current_model_name', 'Unknown')
                backend_type = "HuggingFace" if getattr(self.ai_engine, 'use_transformers', False) else "ctransformers" if getattr(self.ai_engine, 'use_ctransformers', False) else "SimpleAI"
                
                try:
                    params = self.ai_engine._get_adaptive_generation_params(estimated_max_tokens, user_input) if hasattr(self.ai_engine, '_get_adaptive_generation_params') else {"max_tokens": estimated_max_tokens}
                except:
                    params = {"max_tokens": estimated_max_tokens}
                
                transparency_engine.end_processing(
                    transparency_start_time, 
                    model_name, 
                    backend_type, 
                    params, 
                    token_count,
                    task_classification="conversational",
                    confidence_score=analysis.get('complexity_score', 0.5),
                    thinking_detected=analysis.get('has_thinking', False),
                    response_quality="normal"
                )
            
            # Send avatar emotion update based on response with classification metadata
            if full_response.strip():
                if classification_metadata:
                    self.send_avatar_update(full_response.strip(), "response", classification_metadata)
                else:
                    self.send_avatar_update(full_response.strip(), "response")
            
            # Send post-response completion state
            self.send_avatar_update("Response complete", "post_response")
            
            # Display eco-friendly metrics and token usage
            self._display_post_response_metrics()
            
            # Record session statistics for successful query
            if hasattr(self, 'stats_tracker') and self.stats_tracker:
                response_time = time.time() - query_start_time
                self.stats_tracker.record_query(response_time=response_time, tokens=token_count)
                
                # Check for new achievements
                try:
                    from session_stats import AchievementSystem
                    unlocked = AchievementSystem.check_achievements(self.stats_tracker)
                    if unlocked:
                        print(f"\n🏆 Achievement{'s' if len(unlocked) > 1 else ''} unlocked: {', '.join([AchievementSystem.format_achievement(a) for a in unlocked[:2]])}")
                        self.sound_manager.play_success_sequence("minor")
                except:
                    pass
            
            # Synthesize voice in background
            if self.voice_enabled and full_response.strip():
                self.set_avatar_state(AvatarState.SPEAKING)
                self.send_avatar_update("", "speaking")
                
                # Record voice feature usage
                if hasattr(self, 'stats_tracker') and self.stats_tracker:
                    self.stats_tracker.record_feature_use("voice")
                
                # Use intelligent TTS for AI responses with content-specific voice effects
                self._safe_voice_synthesis(full_response.strip(), use_intelligent_tts=True)
                self.set_avatar_state(AvatarState.IDLE)
                self.send_avatar_update("", "idle")
            
        except Exception as e:
            error_msg = f"Error: {e}"
            self.print_with_avatar(error_msg, AvatarState.ERROR)
            self.send_avatar_update(error_msg, "error")
            self.context_sound_manager.play_response_sound(error_msg, user_input)
            if self.voice_enabled:
                self._safe_voice_synthesis(error_msg)
            
    def show_help(self):
        """Display help information"""
        help_text = """
M1K3 Local AI CLI Commands:
  
  Chat Commands:
    <message>       Send message to AI
    clear, reset    Clear conversation context  
    stats, status   Show system statistics with animations
    session         Show detailed session statistics and insights
    context, device Show comprehensive device context
    tokens, usage   Show token usage and eco impact
    animate, demo   Demonstrate animation capabilities
    help, h         Show this help
    quit, q         Exit application
    
  Voice Commands:
    voice, mute     Toggle voice synthesis on/off
    /profile <name> Set voice profile (natural, assistant, broadcast, terminal)
    /tts status     Show intelligent TTS system status and voice settings
    
  Enhanced Commands:
    /explain <query>    Show detailed decision explanation for a query
    /classify <query>   Show intent classification for a query
    
  Model Management Commands:
    /models             List all available models for hot loading
    /model <name>       Switch to specific model (e.g., /model gemma-2-2b-it)
    /model recommend    Switch to best recommended model for your system
    /model interactive  Interactive model selection menu
    /model status       Show current model information
    
  Performance Commands:
    /performance        Show performance summary (alias: /perf)
    /perf summary       Performance overview with key metrics
    /perf stats         Detailed statistics by operation type
    /perf system        Current system performance metrics
    /perf export        Export performance data to JSON file
    /perf clear         Clear performance history
    /perf baseline      Set performance baselines for regression detection
    
  Avatar Commands:
    avatar start    Start the avatar web server
    avatar stop     Stop the avatar web server
    avatar status   Show avatar server and emotion status
    avatar emotion <emotion> [intensity] - Set avatar emotion (0-100)
    avatar style <style> [color] - Set avatar style and color
    avatar test     Test all avatar emotions
    
  Transparency Commands (Dev/Debug):
    /transparency status    Show current transparency level
    /transparency off       Disable model transparency
    /transparency basic     Basic transparency (generation stats)
    /transparency detailed  Detailed transparency (parameters, progress)  
    /transparency full      Full transparency (processing analysis)
    /transparency debug     Debug transparency (maximum detail)
    /transparency summary   Show session transparency summary
    /transparency export    Export transparency data
    
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
            self._safe_voice_synthesis("Here's what I can do for you")
    
    def show_tts_status(self):
        """Show intelligent TTS system status and voice settings"""
        print("\n🎭 Intelligent TTS System Status:")
        print("=" * 50)
        
        if self.intelligent_tts_controller and INTELLIGENT_TTS_AVAILABLE:
            status = self.intelligent_tts_controller.get_status()
            
            print(f"✅ Intelligent TTS: ACTIVE")
            print(f"🔊 Voice Engine: {'Available' if status['voice_engine_available'] else 'Not Available'}")
            print(f"🎚️  Effects Manager: {'Active' if status['effects_manager_available'] else 'Not Available'}")
            print(f"📋 Queue Size: {status['queue_size']}")
            
            print(f"\n🎯 Content-Specific Voice Settings:")
            for content_type, settings in status['modulations'].items():
                print(f"   📢 {content_type.upper()}:")
                print(f"      Volume: {settings['volume']:.1f}x | Speed: {settings['speed']:.1f}x")
                print(f"      Pitch: {settings['pitch']:+.1f} | Reverb: {settings['reverb']:.1f}")
                if settings.get('warmth', 0) > 0:
                    print(f"      Warmth: {settings['warmth']:.1f}")
            
            if status['skipped_types']:
                print(f"\n⏭️  Skipped Content Types: {', '.join(status['skipped_types'])}")
            else:
                print(f"\n✅ All content types enabled")
                
        else:
            print(f"❌ Intelligent TTS: UNAVAILABLE")
            print(f"🔄 Using basic voice synthesis")
        
        # Show voice engine details
        voice_status = self.voice_engine.get_status()
        print(f"\n🎤 Voice Engine Details:")
        print(f"   Profile: {voice_status.get('current_profile', 'Unknown')}")
        print(f"   Voice Enabled: {self.voice_enabled}")
        
        if self.voice_enabled:
            self._safe_voice_synthesis("Intelligent TTS system status displayed", use_intelligent_tts=False)

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
            self._safe_voice_synthesis(status_msg)
    
    def display_system_diagnostics(self, context_summary):
        """Display comprehensive system diagnostics and M1K3 capabilities"""
        try:
            metrics = self.system_monitor.collect_metrics()
            
            print("\n📊 SYSTEM DIAGNOSTICS")
            print("─" * 72)
            
            # Hardware line - complete and detailed
            hardware_parts = []
            if metrics.cpu_model:
                hardware_parts.append(f"{metrics.cpu_model}")
            if metrics.cpu_cores and metrics.cpu_threads:
                hardware_parts.append(f"{metrics.cpu_cores} cores")
            if metrics.gpu_info:
                hardware_parts.append(metrics.gpu_info)
            if metrics.memory_total_gb:
                hardware_parts.append(f"{metrics.memory_total_gb:.0f}GB RAM")
            
            print(f"🖥️  Hardware: {' | '.join(hardware_parts)}")
            
            # System line
            system_parts = []
            if metrics.os_name and metrics.os_version:
                system_parts.append(f"{metrics.os_name} {metrics.os_version}")
            if metrics.timezone:
                system_parts.append(f"{metrics.timezone}")
                if metrics.timezone_offset is not None:
                    sign = "+" if metrics.timezone_offset >= 0 else ""
                    system_parts.append(f"(UTC{sign}{metrics.timezone_offset})")
            if metrics.locale_info:
                system_parts.append(metrics.locale_info)
            
            print(f"🌍 System: {' | '.join(system_parts)}")
            
            # Status line with emojis
            status_parts = []
            if metrics.battery_percent is not None:
                battery_emoji = "🔋" if not metrics.battery_plugged else "⚡"
                status_parts.append(f"{battery_emoji} Battery {metrics.battery_percent}% ({metrics.battery_status()})")
            if metrics.cpu_temp is not None:
                status_parts.append(f"🌡️ CPU {metrics.cpu_temp:.0f}°C ({metrics.thermal_status()})")
            if metrics.disk_usage_percent is not None:
                status_parts.append(f"💾 Disk {metrics.disk_usage_percent:.0f}%")
            
            print(f"🔋 Status: {' | '.join(status_parts)}")
            
            # Performance line
            perf_status = metrics.performance_status()
            cpu_usage = metrics.cpu_usage or 0
            mem_usage = metrics.memory_percent or 0
            print(f"🎯 Performance: {perf_status.title()} load ({cpu_usage:.0f}% CPU, {mem_usage:.0f}% RAM)")
            
            # Capabilities with better detection
            capabilities = []
            if metrics.has_microphone:
                capabilities.append("🎤 Audio")
            if self.voice_enabled:
                capabilities.append("🔊 Voice")
            if metrics.has_wifi:
                capabilities.append("📶 WiFi")
            if metrics.has_ethernet:
                capabilities.append("🌐 Ethernet")
            if metrics.display_count:
                if metrics.display_count > 1:
                    capabilities.append(f"🖥️ Display x{metrics.display_count}")
                else:
                    capabilities.append("🖥️ Display")
            
            if capabilities:
                print(f"✨ Capabilities: {' | '.join(capabilities)}")
            
            print("\n🤖 AI ENGINE")
            print("─" * 72)
            
            # AI Backend details
            ai_parts = []
            model_name = "Local AI"
            backend_name = "Unknown"
            
            try:
                if hasattr(self.ai_engine, '_current_model_name'):
                    model_name = self.ai_engine._current_model_name
                elif hasattr(self.ai_engine, 'model_name'):
                    model_name = self.ai_engine.model_name
                    
                if hasattr(self.ai_engine, 'backend_name'):
                    backend_name = self.ai_engine.backend_name
                elif hasattr(self.ai_engine, 'use_transformers') and self.ai_engine.use_transformers:
                    backend_name = "HuggingFace"
                elif hasattr(self.ai_engine, 'use_ctransformers') and self.ai_engine.use_ctransformers:
                    backend_name = "ctransformers"
            except:
                pass
            
            # Get context window size
            context_size = "8K"
            if hasattr(self.ai_engine, 'max_tokens'):
                context_size = f"{self.ai_engine.max_tokens/1000:.0f}K"
            
            print(f"Model: {model_name} | Backend: {backend_name} | Context: {context_size} tokens")
            
            # Features line
            features = []
            if self.voice_enabled:
                features.append("✅ Voice Synthesis")
            if AVATAR_AVAILABLE:
                if is_avatar_server_running():
                    features.append("✅ Avatar (Live!)")
                else:
                    features.append("✅ Avatar")
            if hasattr(self.ai_engine, 'rag_enabled') and self.ai_engine.rag_enabled:
                features.append("✅ RAG")
            if self.websocket_bridge:
                features.append("✅ WebSocket")
            
            if features:
                print(f"Features: {' | '.join(features)}")
            
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

    def _generate_intelligent_greeting(self, metrics, m1k3_context: dict) -> str:
        """Generate an intelligent greeting using the improved LLMGreetingEngine"""
        
        # Debug logging in debug mode
        debug_mode = False
        try:
            from model_transparency import transparency_engine, TransparencyLevel
            if transparency_engine and transparency_engine.transparency_level == TransparencyLevel.DEBUG:
                debug_mode = True
                print(f"🔍 [CLI GREETING] Starting greeting generation")
                print(f"🔍 [CLI GREETING] REAL_AI_AVAILABLE: {REAL_AI_AVAILABLE}")
                print(f"🔍 [CLI GREETING] Has ai_engine: {hasattr(self, 'ai_engine')}")
        except:
            pass
        
        try:
            # Use the dedicated greeting engine with LLM support
            if REAL_AI_AVAILABLE and hasattr(self, 'ai_engine'):
                if debug_mode:
                    print(f"🔍 [CLI GREETING] Using LLM greeting engine with AI")
                greeting_engine = LLMGreetingEngine(self.ai_engine)
            else:
                if debug_mode:
                    print(f"🔍 [CLI GREETING] Using fallback greeting engine")
                greeting_engine = LLMGreetingEngine()  # Uses fallback system
            
            context = create_greeting_context(metrics, m1k3_context)
            greeting = greeting_engine.generate_greeting(context, max_length=80)
            
            if debug_mode:
                print(f"🔍 [CLI GREETING] Generated: '{greeting}'")
            
            return greeting
            
        except Exception as e:
            if debug_mode:
                print(f"🔍 [CLI GREETING] Exception: {e}")
            # Fallback to simple greeting if anything fails
            import datetime
            current_hour = datetime.datetime.now().hour
            if 5 <= current_hour < 12:
                return "Good morning! M1K3 is ready to help."
            elif 12 <= current_hour < 17:
                return "Good afternoon! M1K3 is ready to help."
            elif 17 <= current_hour < 22:
                return "Good evening! M1K3 is ready to help."
            else:
                return "Hello! M1K3 is ready to help."
    

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
        
        elif command in ['debug', 'diag', 'diagnostics']:
            if not is_avatar_server_running():
                self.print_with_avatar("❌ Avatar server not running. Use 'avatar start' first.", AvatarState.ERROR)
                return
            
            self.print_with_avatar("🔍 Avatar System Diagnostics", AvatarState.IDLE)
            
            # Get server status
            from avatar_server import get_avatar_server
            server = get_avatar_server()
            
            print(f"🔧 Server Instance ID: {id(server)}")
            print(f"🏃 Server Running: {server.is_running()}")
            print(f"👥 Connected WebSocket Clients: {len(server.clients)}")
            print(f"🌐 HTTP Port: {server.http_port}")
            print(f"🔌 WebSocket Port: {server.ws_port}")
            
            if server.clients:
                print(f"📋 Client Details:")
                for i, client in enumerate(server.clients):
                    client_info = f"{client.get('address', 'unknown')}:{client.get('id', 'unknown')}"
                    print(f"   {i+1}. {client_info}")
            
            # Test message sending
            print(f"🧪 Testing message sending...")
            send_avatar_emotion('happy', 75, "Debug test message")
            send_avatar_state('debug')
            
            # WebSocket bridge status
            if hasattr(self, 'websocket_monitor') and self.websocket_monitor:
                print(f"🔗 WebSocket Bridge: Running")
                print(f"🎯 Bridge URL: {self.websocket_monitor.ws_url}")
            else:
                print(f"🔗 WebSocket Bridge: Not initialized")
        
        else:
            help_msg = """🧘 Avatar Commands:
  avatar start     - Start the avatar web server
  avatar stop      - Stop the avatar web server  
  avatar status    - Show server and avatar status
  avatar emotion <emotion> [intensity] - Set avatar emotion (0-100)
  avatar style <style> [color] - Set avatar style and color
  avatar test      - Test all avatar emotions
  avatar debug     - Show detailed WebSocket diagnostics
  
Available emotions: happy, sad, angry, surprised, love, thinking, sleepy, excited
Available styles: robot, organic, crystal, ghost, energy, cute"""
            
            self.print_with_avatar(help_msg, AvatarState.IDLE)
    
    def send_avatar_update(self, text: str, context: str = "", classification_data: dict = None):
        """Send enhanced emotion update to avatar with classification metadata"""
        if not AVATAR_AVAILABLE or not is_avatar_server_running() or not self.avatar_controller:
            return
        
        try:
            # Use enhanced classification data if available
            if classification_data and hasattr(self.avatar_controller, 'update_from_classification'):
                result = self.avatar_controller.update_from_classification(classification_data, text)
                
                # Send enhanced data to avatar server
                metadata = {
                    'intent': classification_data.get('intent'),
                    'confidence': classification_data.get('confidence', 0.5),
                    'response_strategy': classification_data.get('response_strategy'),
                    'reasoning': classification_data.get('reasoning', ''),
                    'context_factors': classification_data.get('context_factors', {}),
                    'classification_engine': classification_data.get('classification_engine', 'enhanced')
                }
                
                send_avatar_emotion(result['emotion'], result['intensity'], text[:100], metadata)
                send_avatar_state(result['state'])
                
                # Send classification update
                send_classification_update(classification_data)
                
            else:
                # Fallback to traditional emotion analysis
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
        
    def handle_user_input_from_websocket(self, message: str):
        """Handle user input received from the websocket bridge."""
        print(f"\n🌐 Received from web: \"{message}\"")
        self.handle_user_input(message)
        print(f"\n{self.avatar_state.value} > ", end="", flush=True) # Show prompt again

    def start_websocket_bridge(self):
        """Start the websocket client to monitor the avatar server."""
        if not WEBSOCKET_CLIENT_AVAILABLE:
            self.print_with_avatar("Cannot start WebSocket bridge: websocket-client library is not installed.", AvatarState.ERROR)
            return

        ws_url = f"ws://localhost:{self.avatar_port + 1}" # Default WS port is HTTP+1
        self.websocket_monitor = WebSocketMonitor(ws_url, self.handle_user_input_from_websocket)
        self.websocket_monitor.start()
        self.print_with_avatar(f"🔗 WebSocket bridge connected to {ws_url}", AvatarState.IDLE)

    def stop_websocket_bridge(self):
        """Stop the websocket client."""
        if self.websocket_monitor:
            self.websocket_monitor.stop()
            self.print_with_avatar("🔌 WebSocket bridge disconnected", AvatarState.IDLE)

    def run_interactive(self):
        """Run interactive CLI session with animations."""
        # A cleaner, more structured startup sequence
        print("\033[1m🧘 M1K3 - Local AI with Advanced Voice & Animations\033[0m")
        print("=" * 60)

        # Setup AI engine
        if not self.setup_ai():
            return 1
        
        # Start the WebSocket bridge by default in interactive mode
        self.start_websocket_bridge()
        
        self.sound_manager.play_startup_sequence()
        
        print("-" * 60)
        
        # Initialize session statistics
        try:
            from session_stats import get_stats_tracker, AchievementSystem
            self.stats_tracker = get_stats_tracker()
            
            # Check for first boot achievement
            unlocked = AchievementSystem.check_achievements(self.stats_tracker)
            self.stats_tracker.unlock_achievement("first_boot")
        except:
            self.stats_tracker = None
        
        # Generate dynamic greeting - try LLM first, fallback to rule-based
        try:
            metrics = self.system_monitor.collect_metrics()
            m1k3_context = self._collect_m1k3_context()
            
            # Try LLM-powered greeting if AI engine is available
            greeting = self._generate_intelligent_greeting(metrics, m1k3_context)
            
            print(f"\n💬 {greeting}")
            
            # Display the enhanced diagnostics
            self.display_system_diagnostics(None)
            
            # Display session statistics
            if self.stats_tracker:
                print("\n📈 SESSION STATISTICS")
                print("─" * 72)
                print(self.stats_tracker.get_formatted_stats_display(
                    max_tokens=getattr(self.ai_engine, 'max_tokens', 8192)
                ))
                
                # Get and display an exciting insight
                insight = self.stats_tracker.get_exciting_insight()
                print(f"\n💡 INSIGHT: {insight}")
                
                # Display achievements if any
                if self.stats_tracker.current_stats.achievements_unlocked:
                    print("\n🏆 ACHIEVEMENTS UNLOCKED")
                    print("─" * 72)
                    achievements_display = []
                    for ach_id in self.stats_tracker.current_stats.achievements_unlocked[:3]:
                        achievements_display.append(AchievementSystem.format_achievement(ach_id))
                    print(" | ".join(achievements_display))
            
            print("\n" + "═" * 72)
            print("Type '/help' for commands or start chatting below.")
            
            # Speak the greeting if voice is enabled - with excitement!
            if self.voice_enabled:
                self.start_animated_status("🎤 Speaking exciting greeting...", "speaking")
                # Add enthusiasm to the voice!
                excited_greeting = greeting + "! Let's create something amazing!"
                self.voice_engine.synthesize_and_play(excited_greeting, background=False)
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
                
        # Cleanup before exiting
        self.stop_websocket_bridge()
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

    def handle_explain_command(self, user_input: str = ""):
        """Handle /explain command for decision explanations"""
        try:
            from decision_explanation_engine import DecisionExplainerEngine
            
            parts = user_input.strip().split(" ", 1)
            if len(parts) < 2:
                self.print_with_avatar("Usage: /explain <query>", AvatarState.IDLE)
                self.print_with_avatar("Example: /explain What is 2 + 2?", AvatarState.IDLE)
                return
            
            query = parts[1]
            self.print_with_avatar(f"🔍 Explaining decision process for: \"{query}\"", AvatarState.THINKING)
            
            # Get enhanced configuration
            from enhanced_adaptive_model_config import EnhancedAdaptiveModelConfig
            config_engine = EnhancedAdaptiveModelConfig(enable_websocket=False)
            config_result = config_engine.get_optimal_config(query, "qwen/qwen3-0.6b", [])
            
            # Generate explanation
            explainer = DecisionExplainerEngine()
            explanation = explainer.explain_decision(query, config_result)
            
            # Display explanation
            print(f"\n🎯 Decision Summary:")
            print(f"   {explanation.decision_summary}")
            
            print(f"\n📊 Intent Classification:")
            print(f"   Intent: {explanation.final_intent}")
            print(f"   Confidence: {explanation.intent_confidence:.3f}")
            print(f"   Strategy: {explanation.response_strategy}")
            
            print(f"\n🔍 Pattern Matches:")
            for i, match in enumerate(explanation.pattern_matches[:3], 1):
                print(f"   {i}. \"{match.matched_text}\" → {match.intent}")
                
            print(f"\n🌐 Context Influences:")
            for influence in explanation.context_influences[:3]:
                impact_icon = {"high": "🔥", "medium": "🟡", "low": "🟢"}[influence.impact_level]
                print(f"   {impact_icon} {influence.factor}: {influence.influence}")
                
            print(f"\n⚙️  Key Parameters:")
            config_params = {k: v for k, v in config_result.items() if k != '_metadata' and v is not None}
            for key, value in list(config_params.items())[:5]:
                print(f"   {key}: {value}")
            
        except ImportError:
            self.print_with_avatar("❌ Decision explanation engine not available", AvatarState.ERROR)
        except Exception as e:
            self.print_with_avatar(f"❌ Explanation failed: {e}", AvatarState.ERROR)

    def handle_classify_command(self, user_input: str = ""):
        """Handle /classify command for intent classification"""
        try:
            from intent_classification_system import IntentClassificationEngine
            
            parts = user_input.strip().split(" ", 1)
            if len(parts) < 2:
                self.print_with_avatar("Usage: /classify <query>", AvatarState.IDLE)
                self.print_with_avatar("Example: /classify What is 2 + 2?", AvatarState.IDLE)
                return
                
            query = parts[1]
            self.print_with_avatar(f"🔍 Classifying: \"{query}\"", AvatarState.THINKING)
            
            # Get intent classification
            engine = IntentClassificationEngine()
            classification = engine.classify_intent(query)
            
            # Display classification
            print(f"\n🎯 Classification Results:")
            print(f"   Intent: {classification.intent.value}")
            print(f"   Confidence: {classification.confidence:.3f}")
            print(f"   Strategy: {classification.response_strategy.value}")
            print(f"   Reasoning: {classification.reasoning}")
            
            if classification.context_factors:
                print(f"\n📋 Context Factors:")
                for factor, value in classification.context_factors.items():
                    if value not in [None, False, '', {}]:
                        print(f"   {factor}: {value}")
            
        except ImportError:
            self.print_with_avatar("❌ Intent classification engine not available", AvatarState.ERROR)
        except Exception as e:
            self.print_with_avatar(f"❌ Classification failed: {e}", AvatarState.ERROR)

    def list_available_models(self):
        """List all available models for hot loading"""
        self.start_animated_status("Discovering available models...", "processing")
        
        try:
            from local_model_manager import LocalModelManager
            manager = LocalModelManager()
            device = manager.analyze_device()
            
            self.stop_animated_status()
            
            print("\n📦 Available Models for Hot Loading:")
            print("=" * 60)
            
            if not manager.available_models:
                self.print_with_avatar("❌ No models found locally", AvatarState.ERROR)
                self.print_with_avatar("💡 Download models first with: python download_gemma.py", AvatarState.IDLE)
                return
            
            # Get current model name
            current_model = getattr(self.ai_engine, '_current_model_name', 'Unknown')
            
            # Group by model type
            hf_models = [(name, spec) for name, spec in manager.available_models.items() if spec.model_type == "hf_transformers"]
            gguf_models = [(name, spec) for name, spec in manager.available_models.items() if spec.model_type == "gguf"]
            
            model_count = 0
            
            if hf_models:
                print("\n🤗 HuggingFace Transformers Models:")
                for name, spec in sorted(hf_models, key=lambda x: x[1].size_mb):
                    model_count += 1
                    ram_req_gb = spec.ram_required_mb / 1024
                    
                    # Show current model
                    status = "🔵 CURRENT" if name == current_model else "✅ Available"
                    if name in device.recommended_models and name != current_model:
                        status = "⭐ RECOMMENDED"
                    if ram_req_gb > device.available_ram_gb:
                        status = "⚠️  High RAM"
                    
                    print(f"   {model_count}. {status}")
                    print(f"      📦 {name}")
                    print(f"      💾 Size: {spec.size_mb:.1f}MB | RAM: {ram_req_gb:.1f}GB")
                    print(f"      📝 {spec.description}")
                    print(f"      ⚡ Speed: {spec.speed_estimate}")
                    print()
            
            if gguf_models:
                print("🔧 GGUF Quantized Models:")
                for name, spec in sorted(gguf_models, key=lambda x: x[1].size_mb):
                    model_count += 1
                    ram_req_gb = spec.ram_required_mb / 1024
                    
                    status = "🔵 CURRENT" if name == current_model else "✅ Available"
                    if ram_req_gb > device.available_ram_gb:
                        status = "⚠️  High RAM"
                    
                    print(f"   {model_count}. {status} {name}")
                    print(f"      💾 Size: {spec.size_mb:.1f}MB | RAM: {ram_req_gb:.1f}GB")
                    print(f"      📝 {spec.description}")
                    print()
            
            print(f"💡 Usage:")
            print(f"   /model <model_name>     Switch to specific model")
            print(f"   /model recommend        Switch to best recommended model")
            print(f"   /model status           Show current model info")
            
            if self.voice_enabled:
                model_count_text = f"Found {len(manager.available_models)} models available for hot loading"
                self._safe_voice_synthesis(model_count_text)
        
        except ImportError:
            self.stop_animated_status()
            self.print_with_avatar("❌ Local model manager not available", AvatarState.ERROR)
        except Exception as e:
            self.stop_animated_status()
            self.print_with_avatar(f"❌ Error listing models: {e}", AvatarState.ERROR)

    def handle_model_command(self, user_input: str = ""):
        """Handle model switching commands"""
        parts = user_input.strip().split()
        
        if len(parts) < 2:
            self.print_with_avatar("Usage: /model <model_name|recommend|status>", AvatarState.IDLE)
            self.print_with_avatar("Example: /model google/gemma-2-2b-it", AvatarState.IDLE)
            self.print_with_avatar("Use /models to see all available models", AvatarState.IDLE)
            return
        
        command = parts[1].lower()
        
        if command == "status":
            self._show_current_model_status()
        elif command == "recommend":
            self._switch_to_recommended_model()
        elif command == "interactive" or command == "select":
            self._interactive_model_selection()
        else:
            # Try to switch to specific model
            model_name = " ".join(parts[1:])  # Join in case model name has spaces
            self._switch_to_model(model_name)

    def _show_current_model_status(self):
        """Show current model status"""
        current_model = getattr(self.ai_engine, '_current_model_name', 'Unknown')
        backend_type = "HuggingFace" if getattr(self.ai_engine, 'use_transformers', False) else "ctransformers"
        
        print("\n🤖 Current AI Model Status:")
        print("=" * 40)
        print(f"📦 Model: {current_model}")
        print(f"🔧 Backend: {backend_type}")
        
        # Get memory usage
        try:
            ai_stats = self.ai_engine.get_memory_usage()
            print(f"💾 Memory: {ai_stats['memory_mb']}")
            print(f"📝 Context: {ai_stats['context_tokens']} tokens")
        except:
            pass
        
        print(f"✅ Status: Loaded and ready")
        
        if self.voice_enabled:
            self._safe_voice_synthesis(f"Currently using {current_model.split('/')[-1]} model")

    def _switch_to_recommended_model(self):
        """Switch to the best recommended model"""
        try:
            from local_model_manager import LocalModelManager
            manager = LocalModelManager()
            device = manager.analyze_device()
            
            if not device.recommended_models:
                self.print_with_avatar("❌ No recommended models available", AvatarState.ERROR)
                return
            
            best_model = device.recommended_models[0]
            self._switch_to_model(best_model)
            
        except Exception as e:
            self.print_with_avatar(f"❌ Error finding recommended model: {e}", AvatarState.ERROR)

    def _switch_to_model(self, model_name: str):
        """Switch to a specific model"""
        current_model = getattr(self.ai_engine, '_current_model_name', 'Unknown')
        
        if model_name == current_model:
            self.print_with_avatar(f"🔵 Already using {model_name}", AvatarState.IDLE)
            return
        
        try:
            from local_model_manager import LocalModelManager
            manager = LocalModelManager()
            
            # Find exact or partial model name match
            matched_model = None
            for available_name in manager.available_models.keys():
                if model_name.lower() == available_name.lower():
                    matched_model = available_name
                    break
                elif model_name.lower() in available_name.lower():
                    matched_model = available_name
                    break
            
            if not matched_model:
                self.print_with_avatar(f"❌ Model '{model_name}' not found locally", AvatarState.ERROR)
                self.print_with_avatar("💡 Use /models to see available models", AvatarState.IDLE)
                return
            
            # Check RAM requirements
            spec = manager.available_models[matched_model]
            ram_req_gb = spec.ram_required_mb / 1024
            device = manager.analyze_device()
            
            if ram_req_gb > device.available_ram_gb:
                self.print_with_avatar(f"⚠️  Warning: {matched_model} requires {ram_req_gb:.1f}GB RAM", AvatarState.ERROR)
                self.print_with_avatar(f"Available: {device.available_ram_gb:.1f}GB", AvatarState.ERROR)
                # Continue anyway - user knows best
            
            # Start hot loading process
            self.print_with_avatar(f"🔄 Hot loading {matched_model}...", AvatarState.LOADING)
            self.start_animated_status("Switching models...", "processing")
            
            # Attempt to switch model using new method
            success = self._perform_model_switch(matched_model, spec)
            
            self.stop_animated_status()
            
            if success:
                self.print_with_avatar(f"✅ Successfully switched to {matched_model}", AvatarState.IDLE)
                self.print_with_avatar(f"📏 Model size: {spec.size_mb:.1f}MB", AvatarState.IDLE)
                
                if self.voice_enabled:
                    model_short_name = matched_model.split('/')[-1].replace('-it', '').replace('-instruct', '')
                    self._safe_voice_synthesis(f"Now using {model_short_name} model")
            else:
                self.print_with_avatar(f"❌ Failed to switch to {matched_model}", AvatarState.ERROR)
                self.print_with_avatar(f"🔄 Staying with current model: {current_model}", AvatarState.IDLE)
        
        except Exception as e:
            self.stop_animated_status()
            self.print_with_avatar(f"❌ Error switching model: {e}", AvatarState.ERROR)

    def _perform_model_switch(self, model_name: str, model_spec) -> bool:
        """Perform the actual model switch with memory management"""
        try:
            # Add switch_model method to AI engine if it doesn't exist
            if not hasattr(self.ai_engine, 'switch_model'):
                self._add_switch_model_method()
            
            # Preserve conversation context
            old_context = self.ai_engine.context.messages.copy() if self.ai_engine.context else []
            
            # Perform the switch
            success = self.ai_engine.switch_model(model_name)
            
            # Restore conversation context if switch was successful
            if success and old_context:
                self.ai_engine.context.messages = old_context
                # Recalculate token count
                total_chars = sum(len(msg["content"]) for msg in old_context)
                self.ai_engine.context.current_tokens = total_chars // 4
            
            return success
        
        except Exception as e:
            print(f"❌ Model switch failed: {e}")
            return False

    def _add_switch_model_method(self):
        """Dynamically add switch_model method to AI engine"""
        def switch_model(engine_self, model_name: str) -> bool:
            """Dynamically added method to switch models"""
            try:
                import gc
                import torch
                
                # Clear current model from memory
                if hasattr(engine_self, 'model') and engine_self.model is not None:
                    del engine_self.model
                    engine_self.model = None
                
                if hasattr(engine_self, 'tokenizer') and engine_self.tokenizer is not None:
                    del engine_self.tokenizer
                    engine_self.tokenizer = None
                
                # Clear GPU cache if using CUDA
                if torch.cuda.is_available():
                    torch.cuda.empty_cache()
                
                # Force garbage collection
                gc.collect()
                
                # Set the new model name for _try_huggingface_model to find
                engine_self._target_model_name = model_name
                
                # Try to load the new model using existing HuggingFace loading logic
                success = engine_self._try_huggingface_model()
                
                if success:
                    engine_self._current_model_name = model_name
                
                return success
            
            except Exception as e:
                print(f"Switch model error: {e}")
                return False
        
        # Add the method to the AI engine instance
        import types
        self.ai_engine.switch_model = types.MethodType(switch_model, self.ai_engine)

    def _interactive_model_selection(self):
        """Interactive model selection interface"""
        try:
            from local_model_manager import LocalModelManager
            manager = LocalModelManager()
            device = manager.analyze_device()
            
            if not manager.available_models:
                self.print_with_avatar("❌ No models found locally", AvatarState.ERROR)
                self.print_with_avatar("💡 Download models first with: python download_gemma.py", AvatarState.IDLE)
                return
            
            current_model = getattr(self.ai_engine, '_current_model_name', 'Unknown')
            
            # Show available models with numbers
            print("\n🎯 Interactive Model Selection")
            print("=" * 50)
            
            # Group and display models
            hf_models = [(name, spec) for name, spec in manager.available_models.items() if spec.model_type == "hf_transformers"]
            gguf_models = [(name, spec) for name, spec in manager.available_models.items() if spec.model_type == "gguf"]
            
            model_options = []
            option_number = 1
            
            if hf_models:
                print("\n🤗 HuggingFace Transformers Models:")
                for name, spec in sorted(hf_models, key=lambda x: x[1].size_mb):
                    ram_req_gb = spec.ram_required_mb / 1024
                    
                    # Show status
                    status = "🔵 CURRENT" if name == current_model else "✅ Available"
                    if name in device.recommended_models and name != current_model:
                        status = "⭐ RECOMMENDED"
                    if ram_req_gb > device.available_ram_gb:
                        status = "⚠️  High RAM"
                    
                    print(f"   {option_number}. {status}")
                    print(f"      📦 {name}")
                    print(f"      💾 Size: {spec.size_mb:.1f}MB | RAM: {ram_req_gb:.1f}GB")
                    print(f"      📝 {spec.description}")
                    print(f"      ⚡ Speed: {spec.speed_estimate}")
                    print()
                    
                    model_options.append((name, spec))
                    option_number += 1
            
            if gguf_models:
                print("🔧 GGUF Quantized Models:")
                for name, spec in sorted(gguf_models, key=lambda x: x[1].size_mb):
                    ram_req_gb = spec.ram_required_mb / 1024
                    
                    status = "🔵 CURRENT" if name == current_model else "✅ Available"
                    if ram_req_gb > device.available_ram_gb:
                        status = "⚠️  High RAM"
                    
                    print(f"   {option_number}. {status} {name}")
                    print(f"      💾 Size: {spec.size_mb:.1f}MB | RAM: {ram_req_gb:.1f}GB")
                    print(f"      📝 {spec.description}")
                    print()
                    
                    model_options.append((name, spec))
                    option_number += 1
            
            # Show device info
            print(f"💻 Your System: {device.available_ram_gb:.1f}GB RAM available")
            if device.recommended_models:
                print(f"🎯 Recommended: {', '.join(device.recommended_models[:2])}")
            
            # Get user selection
            print(f"\n❓ Select a model to load (1-{len(model_options)}) or 'c' to cancel:")
            
            try:
                # Read user input
                user_choice = input("➤ ").strip()
                
                if user_choice.lower() in ['c', 'cancel', 'quit', 'q']:
                    self.print_with_avatar("❌ Model selection cancelled", AvatarState.IDLE)
                    return
                
                # Parse choice
                choice_num = int(user_choice)
                if 1 <= choice_num <= len(model_options):
                    selected_model, selected_spec = model_options[choice_num - 1]
                    
                    # Confirm selection
                    ram_req_gb = selected_spec.ram_required_mb / 1024
                    print(f"\n📦 Selected: {selected_model}")
                    print(f"💾 Size: {selected_spec.size_mb:.1f}MB")
                    print(f"💭 RAM needed: {ram_req_gb:.1f}GB")
                    print(f"📝 {selected_spec.description}")
                    
                    if selected_model == current_model:
                        self.print_with_avatar(f"🔵 Already using {selected_model}", AvatarState.IDLE)
                        return
                    
                    # Ask for confirmation
                    confirm = input("\n❓ Proceed with model switch? (y/N): ").strip().lower()
                    if confirm in ['y', 'yes', 'ok', '1']:
                        print(f"\n🚀 Switching to {selected_model}...")
                        self._switch_to_model(selected_model)
                    else:
                        self.print_with_avatar("❌ Model switch cancelled", AvatarState.IDLE)
                else:
                    self.print_with_avatar(f"❌ Invalid selection. Choose 1-{len(model_options)}", AvatarState.ERROR)
                    
            except ValueError:
                self.print_with_avatar("❌ Invalid input. Please enter a number.", AvatarState.ERROR)
            except KeyboardInterrupt:
                self.print_with_avatar("\n❌ Selection interrupted", AvatarState.ERROR)
            
        except ImportError:
            self.print_with_avatar("❌ Local model manager not available", AvatarState.ERROR)
        except Exception as e:
            self.print_with_avatar(f"❌ Error in model selection: {e}", AvatarState.ERROR)

    def handle_performance_command(self, user_input: str = ""):
        """Handle performance monitoring commands"""
        parts = user_input.strip().split()
        command = parts[1] if len(parts) > 1 else "summary"
        
        try:
            from performance_monitor import get_performance_monitor
            perf_monitor = get_performance_monitor()
            
            if command == "summary":
                self._show_performance_summary(perf_monitor)
            elif command == "stats":
                self._show_detailed_performance_stats(perf_monitor)
            elif command == "system":
                self._show_system_performance(perf_monitor)
            elif command == "export":
                self._export_performance_data(perf_monitor)
            elif command == "clear":
                self._clear_performance_data(perf_monitor)
            elif command == "baseline":
                self._set_performance_baseline(parts)
            else:
                self.print_with_avatar("Usage: /performance [summary|stats|system|export|clear|baseline]", AvatarState.IDLE)
                self.print_with_avatar("  summary  - Show performance overview", AvatarState.IDLE)
                self.print_with_avatar("  stats    - Detailed performance statistics", AvatarState.IDLE)
                self.print_with_avatar("  system   - Current system performance", AvatarState.IDLE)
                self.print_with_avatar("  export   - Export performance data to file", AvatarState.IDLE)
                self.print_with_avatar("  clear    - Clear performance history", AvatarState.IDLE)
                self.print_with_avatar("  baseline - Set performance baselines", AvatarState.IDLE)
        
        except ImportError:
            self.print_with_avatar("❌ Performance monitoring not available", AvatarState.ERROR)
        except Exception as e:
            self.print_with_avatar(f"❌ Performance command error: {e}", AvatarState.ERROR)
    
    def _show_performance_summary(self, perf_monitor):
        """Show performance summary"""
        summary = perf_monitor.get_performance_summary()
        
        print("\n📊 M1K3 Performance Summary")
        print("=" * 50)
        
        overall = summary.get('overall_stats', {})
        print(f"📈 Total Events: {overall.get('total_events', 0)}")
        print(f"⚡ Avg Duration: {overall.get('average_duration', 0):.3f}s")
        print(f"✅ Success Rate: {overall.get('success_rate', 0):.1f}%")
        print(f"🚀 Events/sec: {overall.get('events_per_second', 0):.2f}")
        
        if overall.get('memory_growth_mb', 0) != 0:
            print(f"💾 Memory Growth: {overall.get('memory_growth_mb', 0):.1f}MB")
        
        # Show by event type
        by_type = summary.get('by_event_type', {})
        if by_type:
            print(f"\n📋 By Event Type:")
            for event_type, stats in by_type.items():
                print(f"   {event_type}: {stats['total_events']} events, "
                      f"avg: {stats['average_duration']:.3f}s, "
                      f"success: {stats['success_rate']:.1f}%")
        
        # Show recent events
        recent = summary.get('recent_events', [])
        if recent:
            print(f"\n🕐 Recent Events:")
            for event in recent[-5:]:
                status = "✅" if event['success'] else "❌"
                print(f"   {status} {event['name']}: {event['duration']:.3f}s")
        
        # Show regressions
        regressions = summary.get('regressions_detected', 0)
        if regressions > 0:
            print(f"\n⚠️  Performance Regressions Detected: {regressions}")
        
        if self.voice_enabled:
            self._safe_voice_synthesis(f"Performance summary: {overall.get('total_events', 0)} events tracked")
    
    def _show_detailed_performance_stats(self, perf_monitor):
        """Show detailed performance statistics"""
        from performance_monitor import PerformanceEventType
        
        print("\n📊 Detailed Performance Statistics")
        print("=" * 60)
        
        for event_type in PerformanceEventType:
            stats = perf_monitor.get_stats(event_type=event_type)
            if stats.total_events > 0:
                print(f"\n🔹 {event_type.value.upper()}:")
                print(f"   Events: {stats.total_events}")
                print(f"   Avg: {stats.average_duration:.3f}s")
                print(f"   Min: {stats.min_duration:.3f}s")
                print(f"   Max: {stats.max_duration:.3f}s")
                print(f"   Median: {stats.median_duration:.3f}s")
                print(f"   95th percentile: {stats.p95_duration:.3f}s")
                print(f"   Success rate: {stats.success_rate:.1f}%")
    
    def _show_system_performance(self, perf_monitor):
        """Show current system performance"""
        system_perf = perf_monitor.get_system_performance()
        
        print("\n💻 Current System Performance")
        print("=" * 40)
        
        if 'error' in system_perf:
            print(f"❌ Error getting system info: {system_perf['error']}")
            return
        
        print(f"🖥️  CPU Usage: {system_perf.get('system_cpu_percent', 0):.1f}%")
        print(f"💾 Memory Usage: {system_perf.get('system_memory_percent', 0):.1f}%")
        print(f"🆓 Available RAM: {system_perf.get('system_memory_available_gb', 0):.1f}GB")
        print(f"💽 Disk Usage: {system_perf.get('system_disk_percent', 0):.1f}%")
        print(f"📊 M1K3 Memory: {system_perf.get('process_memory_mb', 0):.1f}MB")
        print(f"⚡ M1K3 CPU: {system_perf.get('process_cpu_percent', 0):.1f}%")
        print(f"🧵 Active Threads: {system_perf.get('active_threads', 0)}")
        
        # Memory usage assessment
        process_memory = system_perf.get('process_memory_mb', 0)
        if process_memory > 4000:
            print("⚠️  High memory usage detected")
        elif process_memory > 2000:
            print("ℹ️  Moderate memory usage")
        else:
            print("✅ Memory usage looks good")
    
    def _export_performance_data(self, perf_monitor):
        """Export performance data"""
        try:
            filepath = perf_monitor.export_performance_data()
            self.print_with_avatar(f"📄 Performance data exported to: {filepath}", AvatarState.IDLE)
            if self.voice_enabled:
                self._safe_voice_synthesis("Performance data exported successfully")
        except Exception as e:
            self.print_with_avatar(f"❌ Export failed: {e}", AvatarState.ERROR)
    
    def _clear_performance_data(self, perf_monitor):
        """Clear performance data"""
        try:
            # Clear events
            perf_monitor.events.clear()
            perf_monitor._stats_cache.clear()
            perf_monitor._cache_invalidated = True
            
            self.print_with_avatar("🗑️  Performance data cleared", AvatarState.IDLE)
            if self.voice_enabled:
                self._safe_voice_synthesis("Performance data cleared")
        except Exception as e:
            self.print_with_avatar(f"❌ Clear failed: {e}", AvatarState.ERROR)
    
    def _set_performance_baseline(self, parts):
        """Set performance baselines"""
        if len(parts) < 4:
            self.print_with_avatar("Usage: /performance baseline <event_name> <duration_seconds>", AvatarState.IDLE)
            self.print_with_avatar("Example: /performance baseline model_load 3.5", AvatarState.IDLE)
            return
        
        try:
            event_name = parts[2]
            duration = float(parts[3])
            
            from performance_monitor import set_baseline
            set_baseline(event_name, duration)
            
            self.print_with_avatar(f"✅ Baseline set: {event_name} = {duration}s", AvatarState.IDLE)
            if self.voice_enabled:
                self._safe_voice_synthesis(f"Baseline set for {event_name}")
                
        except ValueError:
            self.print_with_avatar("❌ Invalid duration. Please use a number.", AvatarState.ERROR)
        except Exception as e:
            self.print_with_avatar(f"❌ Failed to set baseline: {e}", AvatarState.ERROR)

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
    parser.add_argument("--transparency", choices=["off", "basic", "detailed", "full", "debug"], default="basic", help="Set model transparency level for development (default: basic)")
    parser.add_argument("--rag", action="store_true", help="Enable RAG (Retrieval-Augmented Generation) with comprehensive knowledge base")
    
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
    transparency_level = args.transparency
    
    cli = M1K3CLI(
        voice_enabled=voice_enabled,
        auto_avatar=auto_avatar,
        avatar_port=avatar_port,
        open_browser=open_browser,
        transparency_level=transparency_level,
        rag_enabled=args.rag
    )
    
    if args.query:
        return cli.run_single_query(args.query)
    else:
        return cli.run_interactive()

if __name__ == "__main__":
    sys.exit(main())