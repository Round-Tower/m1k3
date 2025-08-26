#!/usr/bin/env python3
"""
CLI Core Module
Main CLI loop and coordination logic for M1K3 CLI
"""

import sys
import time
import signal
import threading
from typing import Optional, Dict, Any
from enum import Enum

from .cli_logging import get_cli_logger, log_info, log_debug, log_warning, log_error, setup_cli_logging
from .cli_initialization import CLIInitializer, initialize_cli_components
from .cli_commands import CLICommandHandler
from .cli_ai_handler import CLIAIResponseProcessor


class CLIState(Enum):
    """CLI application states"""
    STARTING = "starting"
    READY = "ready"
    PROCESSING = "processing"
    SHUTTING_DOWN = "shutting_down"
    ERROR = "error"


class M1K3CLICore:
    """Core CLI application class with modular architecture"""
    
    def __init__(self, voice_enabled: bool = True, auto_avatar: bool = False, 
                 avatar_port: int = 8080, open_browser: bool = True, 
                 transparency_level: str = "basic", rag_enabled: bool = False):
        
        # Setup logging first
        setup_cli_logging()
        self.logger = get_cli_logger()
        
        # Configuration
        self.voice_enabled = voice_enabled
        self.auto_avatar = auto_avatar
        self.avatar_port = avatar_port
        self.open_browser = open_browser
        self.transparency_level = transparency_level
        self.rag_enabled = rag_enabled
        
        # State
        self.state = CLIState.STARTING
        self.running = False
        self.shutdown_requested = False
        
        # Components
        self.initializer: Optional[CLIInitializer] = None
        self.command_handler: Optional[CLICommandHandler] = None
        self.ai_processor: Optional[CLIAIResponseProcessor] = None
        
        # Component instances (populated during initialization)
        self.ai_engine = None
        self.rag_engine = None
        self.voice_engine = None
        self.sound_manager = None
        self.system_monitor = None
        self.avatar_controller = None
        self.stats_tracker = None
        self.model_cli = None
        
        # Setup signal handlers
        self._setup_signal_handlers()
        
        log_info("M1K3 CLI Core initialized")
    
    def _setup_signal_handlers(self):
        """Setup signal handlers for graceful shutdown"""
        self.signal_received = False  # Flag to prevent duplicate messages
        
        def signal_handler(signum, frame):
            if not self.signal_received:  # Only log once
                log_info(f"Received signal {signum}, initiating shutdown")
                self.signal_received = True
                print(f"\n📡 Shutting down gracefully...")
                
            self.shutdown_requested = True
            self.running = False  # Immediate stop flag
        
        signal.signal(signal.SIGINT, signal_handler)
        signal.signal(signal.SIGTERM, signal_handler)
    
    def initialize(self) -> bool:
        """Initialize all CLI components"""
        log_info("🚀 Initializing M1K3 CLI")
        self.state = CLIState.STARTING
        
        try:
            # Initialize components
            self.initializer = initialize_cli_components()
            if not self.initializer:
                log_error("Failed to initialize components")
                return False
            
            # Setup AI engines
            if not self._setup_ai_engines():
                log_error("Failed to setup AI engines")
                return False
            
            # Setup other components
            self._setup_voice_engine()
            self._setup_avatar_system()
            self._setup_sound_system()
            self._setup_monitoring()
            self._setup_model_cli()
            
            # Initialize command handler and AI processor
            self.command_handler = CLICommandHandler(self)
            self.ai_processor = CLIAIResponseProcessor(self)
            
            self.state = CLIState.READY
            log_info("✅ CLI initialization complete")
            return True
            
        except Exception as e:
            log_error(f"Initialization failed: {e}")
            self.state = CLIState.ERROR
            return False
    
    def _setup_ai_engines(self) -> bool:
        """Setup AI engines with fallback chain"""
        log_info("Setting up AI engines")
        
        # Try to setup primary AI engine
        if self.initializer.is_component_available('real_ai'):
            LocalAIEngine = self.initializer.get_component('local_ai_engine')
            if LocalAIEngine:
                try:
                    self.ai_engine = LocalAIEngine()
                    if self.ai_engine.load_model():
                        log_info("✅ Real AI engine loaded successfully")
                    else:
                        log_warning("Real AI engine loaded but model loading failed")
                        self.ai_engine = None
                except Exception as e:
                    log_error(f"Real AI engine setup failed: {e}")
                    self.ai_engine = None
        
        # Fallback to simple AI engine
        if not self.ai_engine and self.initializer.is_component_available('simple_ai'):
            SimpleAIEngine = self.initializer.get_component('simple_ai_engine')
            if SimpleAIEngine:
                try:
                    self.ai_engine = SimpleAIEngine()
                    log_info("✅ Simple AI engine loaded as fallback")
                except Exception as e:
                    log_error(f"Simple AI engine setup failed: {e}")
        
        # Setup RAG engine if enabled and available
        if self.rag_enabled and self.initializer.is_component_available('rag_engine'):
            RAGEngine = self.initializer.get_component('rag_engine')
            if RAGEngine:
                try:
                    self.rag_engine = RAGEngine()
                    log_info("✅ RAG engine loaded")
                except Exception as e:
                    log_error(f"RAG engine setup failed: {e}")
        
        return self.ai_engine is not None
    
    def _setup_voice_engine(self):
        """Setup voice synthesis engine"""
        if not self.voice_enabled:
            log_info("Voice synthesis disabled")
            return
        
        if self.initializer.is_component_available('voice_engine'):
            create_voice_engine = self.initializer.get_component('create_voice_engine')
            if create_voice_engine:
                try:
                    self.voice_engine = create_voice_engine()
                    if self.voice_engine and self.voice_engine.load_model():
                        log_info("✅ Voice engine loaded")
                    else:
                        log_warning("Voice engine failed to load model")
                        self.voice_engine = None
                except Exception as e:
                    log_error(f"Voice engine setup failed: {e}")
        else:
            log_warning("Voice engine not available")
    
    def _setup_avatar_system(self):
        """Setup avatar system if enabled"""
        if not self.auto_avatar:
            log_info("Avatar system disabled")
            return
        
        if self.initializer.is_component_available('avatar'):
            try:
                avatar_components = self.initializer.get_component('avatar_server')
                AvatarController = self.initializer.get_component('avatar_controller')
                
                if avatar_components and AvatarController:
                    self.avatar_controller = AvatarController()
                    # Start avatar server with custom port
                    start_server = avatar_components['start_avatar_server']
                    success = start_server(port=self.avatar_port, open_browser=self.open_browser)
                    if success:
                        log_info(f"✅ Avatar system started on port {self.avatar_port}")
                    else:
                        log_error(f"❌ Failed to start avatar server on port {self.avatar_port}")
                else:
                    log_warning("Avatar components not complete")
            except Exception as e:
                log_error(f"Avatar system setup failed: {e}")
        else:
            log_warning("Avatar system not available")
    
    def _setup_sound_system(self):
        """Setup sound management system"""
        if self.initializer.is_component_available('sound_manager'):
            SoundManager = self.initializer.get_component('sound_manager')
            if SoundManager:
                try:
                    self.sound_manager = SoundManager()
                    log_info("✅ Sound management system loaded")
                except Exception as e:
                    log_error(f"Sound system setup failed: {e}")
        else:
            log_warning("Sound system not available")
    
    def _setup_monitoring(self):
        """Setup system monitoring"""
        if self.initializer.is_component_available('system_monitor'):
            SystemMonitor = self.initializer.get_component('system_monitor')
            if SystemMonitor:
                try:
                    self.system_monitor = SystemMonitor()
                    log_info("✅ System monitoring loaded")
                except Exception as e:
                    log_error(f"System monitoring setup failed: {e}")
        else:
            log_warning("System monitoring not available")
    
    def _setup_model_cli(self):
        """Setup model management CLI"""
        if self.initializer.is_component_available('model_cli'):
            ModelCLI = self.initializer.get_component('model_cli')
            if ModelCLI:
                try:
                    self.model_cli = ModelCLI()
                    log_info("✅ Model management CLI loaded")
                except Exception as e:
                    log_error(f"Model CLI setup failed: {e}")
        else:
            log_warning("Model CLI not available")
    
    def run_interactive(self) -> int:
        """Run interactive CLI session"""
        log_info("Starting interactive CLI session")
        
        if not self.initialize():
            log_error("Failed to initialize CLI")
            return 1
        
        self._display_startup_banner()
        self._play_startup_sounds()
        
        self.running = True
        self.state = CLIState.READY
        
        log_info("CLI ready for user input")
        
        try:
            while self.running and not self.shutdown_requested:
                try:
                    # Get user input
                    user_input = self._get_user_input()
                    if not user_input:
                        continue
                    
                    # Process input
                    if not self._process_user_input(user_input):
                        break
                    
                except KeyboardInterrupt:
                    log_info("Keyboard interrupt received")
                    break
                except EOFError:
                    log_info("EOF received")
                    break
                except Exception as e:
                    log_error(f"Error in main loop: {e}")
                    continue
            
            return 0
            
        finally:
            self._cleanup()
    
    def run_single_query(self, query: str) -> int:
        """Run single query mode"""
        log_info(f"Running single query: {query[:50]}...")
        
        if not self.initialize():
            log_error("Failed to initialize CLI")
            return 1
        
        try:
            self.state = CLIState.PROCESSING
            
            # Process the query
            response = self.ai_processor.process_ai_query(query, self.rag_enabled)
            if response:
                print(response)
                
                # Process with voice if enabled
                if self.voice_enabled:
                    self.ai_processor.process_response_with_voice(response, background=False)
                
                return 0
            else:
                log_error("Failed to process query")
                return 1
                
        except Exception as e:
            log_error(f"Error in single query mode: {e}")
            return 1
        finally:
            self._cleanup()
    
    def _display_startup_banner(self):
        """Display startup banner"""
        print("\033[1m🧘 M1K3 - Local AI with Advanced Voice & Animations\033[0m")
        print("=" * 60)
        
        # Display system info
        if self.system_monitor:
            try:
                metrics = self.system_monitor.collect_metrics()
                print(f"💾 RAM: {metrics.memory_percent or 0:.1f}% | "
                      f"⚡ CPU: {metrics.cpu_usage or 0:.1f}% | "
                      f"🖥️  Platform: {metrics.os_name or 'Unknown'}")
            except Exception as e:
                log_debug(f"Error displaying system metrics: {e}")
        
        # Display AI engine info
        if self.ai_engine:
            engine_name = type(self.ai_engine).__name__
            print(f"🧠 AI Engine: {engine_name}")
        
        if self.rag_enabled and self.rag_engine:
            print("🔍 RAG: Enabled")
        
        if self.voice_enabled and self.voice_engine:
            print("🔊 Voice: Enabled")
        
        if self.auto_avatar:
            print(f"👤 Avatar: http://localhost:{self.avatar_port}")
        
        print("-" * 60)
    
    def _play_startup_sounds(self):
        """Play startup sound sequence"""
        if self.sound_manager:
            try:
                self.sound_manager.play_startup_sequence()
            except Exception as e:
                log_debug(f"Startup sounds failed: {e}")
    
    def _get_user_input(self) -> Optional[str]:
        """Get user input with prompt"""
        try:
            prompt = "💬 You: "
            user_input = input(prompt).strip()
            return user_input if user_input else None
        except (KeyboardInterrupt, EOFError):
            raise
        except Exception as e:
            log_error(f"Error getting user input: {e}")
            return None
    
    def _process_user_input(self, user_input: str) -> bool:
        """Process user input (returns False to exit)"""
        try:
            self.state = CLIState.PROCESSING
            
            # Check if it's a command
            if self.command_handler.is_command(user_input):
                command_name, args = self.command_handler.parse_command(user_input)
                if command_name:
                    result = self.command_handler.execute_command(command_name, args)
                    # Some commands (like quit) return False to signal exit
                    return result if result is not None else True
            
            # Otherwise, process as AI query
            else:
                response = self.ai_processor.process_full_response_pipeline(
                    user_input, 
                    use_rag=self.rag_enabled,
                    enable_voice=self.voice_enabled,
                    enable_avatar=self.auto_avatar
                )
                
                if response:
                    print(f"\n🤖 M1K3: {response}\n")
                
            self.state = CLIState.READY
            return True
            
        except Exception as e:
            log_error(f"Error processing user input: {e}")
            print(f"❌ Error: {str(e)}")
            self.state = CLIState.READY
            return True
    
    def toggle_voice(self) -> bool:
        """Toggle voice synthesis on/off"""
        self.voice_enabled = not self.voice_enabled
        status = "enabled" if self.voice_enabled else "disabled"
        print(f"🔊 Voice synthesis {status}")
        log_info(f"Voice synthesis {status}")
        
        # Also update voice engine if available
        if self.voice_engine and hasattr(self.voice_engine, 'voice_enabled'):
            self.voice_engine.voice_enabled = self.voice_enabled
        
        return True
    
    def set_voice_profile(self, profile_name: str) -> bool:
        """Set voice profile for TTS"""
        if not self.voice_engine:
            return False
        
        if hasattr(self.voice_engine, 'set_profile'):
            success = self.voice_engine.set_profile(profile_name)
            if success:
                log_info(f"Voice profile changed to: {profile_name}")
                return True
            else:
                # Show available profiles
                if hasattr(self.voice_engine, 'profiles'):
                    available = ', '.join(self.voice_engine.profiles.keys())
                    print(f"⚠️ Available profiles: {available}")
                return False
        return False
    
    def show_tts_status(self) -> bool:
        """Show TTS system status and settings"""
        if not self.voice_engine:
            print("⚠️ Voice engine not available")
            return False
        
        if hasattr(self.voice_engine, 'get_status'):
            status = self.voice_engine.get_status()
            print(f"\n🎤 Voice Engine Status:")
            print(f"  Available: {'✅' if status.get('available', False) else '❌'}")
            print(f"  Loaded: {'✅' if status.get('loaded', False) else '❌'}")
            print(f"  Enabled: {'✅' if status.get('enabled', False) else '❌'}")
            print(f"  Model: {status.get('model', 'Unknown')}")
            print(f"  Profile: {status.get('current_profile', 'natural')}")
            
            if hasattr(self.voice_engine, 'profiles'):
                print(f"\n🎭 Available Profiles:")
                for name, config in self.voice_engine.profiles.items():
                    current = "👆" if name == status.get('current_profile') else "  "
                    desc = config.get('description', 'No description')
                    print(f"  {current} {name}: {desc}")
            
            return True
        
        print("⚠️ TTS status not available")
        return False
    
    def _cleanup(self):
        """Cleanup resources before exit"""
        log_info("🧹 Cleaning up CLI resources")
        self.state = CLIState.SHUTTING_DOWN
        
        try:
            # Stop AI processor
            if self.ai_processor:
                self.ai_processor.stop_processing()
            
            # Stop avatar server
            if self.auto_avatar and self.initializer and self.initializer.is_component_available('avatar'):
                avatar_components = self.initializer.get_component('avatar_server')
                if avatar_components:
                    stop_server = avatar_components['stop_avatar_server']
                    stop_server()
                    log_info("Avatar server stopped")
            
            # Cleanup voice engine
            if self.voice_engine and hasattr(self.voice_engine, 'cleanup'):
                self.voice_engine.cleanup()
                log_info("Voice engine cleaned up")
            
            # Cleanup AI engines
            if self.ai_engine and hasattr(self.ai_engine, 'cleanup'):
                self.ai_engine.cleanup()
                log_info("AI engine cleaned up")
            
            if self.rag_engine and hasattr(self.rag_engine, 'cleanup'):
                self.rag_engine.cleanup()
                log_info("RAG engine cleaned up")
            
            # Shutdown model monitor background thread
            if self.model_cli and hasattr(self.model_cli, 'monitor'):
                if hasattr(self.model_cli.monitor, 'shutdown'):
                    self.model_cli.monitor.shutdown()
                    log_info("Model monitor shutdown")
            
            self.running = False
            log_info("CLI cleanup complete")
            
        except Exception as e:
            log_error(f"Error during cleanup: {e}")
    
    def get_state(self) -> CLIState:
        """Get current CLI state"""
        return self.state
    
    def is_running(self) -> bool:
        """Check if CLI is running"""
        return self.running and not self.shutdown_requested