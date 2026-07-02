#!/usr/bin/env python3
"""
CLI Core Module
Main CLI loop and coordination logic for M1K3 CLI
"""

import os
import sys
import time
import signal
import threading
import warnings
from typing import Optional, Dict, Any
from enum import Enum

# Suppress third-party warnings for cleaner CLI output
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)
# Suppress APEX warnings from VibeVoice
warnings.filterwarnings("ignore", message=".*APEX FusedRMSNorm.*")
warnings.filterwarnings("ignore", message=".*apex.*not available.*")

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
                 transparency_level: str = "basic", rag_enabled: bool = False,
                 stt_engine: str = "auto", stt_model: str = "vosk-model-small-en-us-0.15",
                 stt_language: str = "en-US", voice_first: bool = False,
                 force_internal_mic: bool = False,
                 streaming_enabled: bool = False, conversation_mode: bool = False,
                 chunk_size: int = 20, chunk_timeout: float = 0.5, response_delay: float = 0.2,
                 enable_interruptions: bool = True,
                 # TTS/VibeVoice parameters
                 tts_engine: str = "auto", vibevoice_model: str = "1.5B",
                 vibevoice_quality: str = "balanced", voice_profile: str = "natural",
                 speakers: list = None, multi_speaker: bool = False,
                 # Additional TTS parameters (for compatibility with cli.py)
                 realtime_mode: bool = False, instant_mode: bool = False,
                 piper_voice: str = "en_US-lessac-medium", piper_speed: float = 1.0,
                 espeak_voice: str = "en", espeak_speed: int = 175, espeak_profile: str = "fast",
                 reverb_type: str = None, reverb_intensity: float = 0.3,
                 **kwargs):
        
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
        self.stt_engine = stt_engine
        self.stt_model = stt_model
        self.stt_language = stt_language
        self.voice_first = voice_first
        self.force_internal_mic = force_internal_mic
        
        # TTS/VibeVoice configuration
        self.tts_engine = tts_engine
        self.vibevoice_model = vibevoice_model
        self.vibevoice_quality = vibevoice_quality
        self.voice_profile = voice_profile
        self.speakers = speakers or ["Alice"]
        self.multi_speaker = multi_speaker

        # Additional TTS configuration (compatibility)
        self.realtime_mode = realtime_mode
        self.instant_mode = instant_mode
        self.piper_voice = piper_voice
        self.piper_speed = piper_speed
        self.espeak_voice = espeak_voice
        self.espeak_speed = espeak_speed
        self.espeak_profile = espeak_profile
        self.reverb_type = reverb_type
        self.reverb_intensity = reverb_intensity
        
        # Streaming configuration
        self.streaming_enabled = streaming_enabled
        self.conversation_mode = conversation_mode
        self.chunk_size = chunk_size
        self.chunk_timeout = chunk_timeout
        self.response_delay = response_delay
        self.enable_interruptions = enable_interruptions
        
        # State
        self.state = CLIState.STARTING
        self.running = False
        self.shutdown_requested = False

        # Session management
        self.session_id = self._generate_session_id()
        self.current_personality = "default"
        
        # Initialize ComponentManager and pass it the CLI config
        from src.cli.component_manager import ComponentManager
        self.component_manager = ComponentManager()
        self.component_manager.set_cli_config({
            "voice_enabled": voice_enabled,
            "auto_avatar": auto_avatar,
            "avatar_port": avatar_port,
            "open_browser": open_browser,
            "transparency_level": transparency_level,
            "rag_enabled": rag_enabled,
            "stt_engine": stt_engine,
            "stt_model": stt_model,
            "stt_language": stt_language,
            "voice_first": voice_first,
            "force_internal_mic": force_internal_mic,
            "streaming_enabled": streaming_enabled,
            "conversation_mode": conversation_mode,
            "chunk_size": chunk_size,
            "chunk_timeout": chunk_timeout,
            "response_delay": response_delay,
            "enable_interruptions": enable_interruptions,
            "tts_engine": tts_engine,
            "vibevoice_model": vibevoice_model,
            "vibevoice_quality": vibevoice_quality,
            "voice_profile": voice_profile,
            "speakers": speakers,
            "multi_speaker": multi_speaker,
        })
        
        # Component instances (will be lazy-loaded via component_manager)
        self.initializer = None # This will be managed by ComponentManager
        self.command_handler = None
        self.ai_processor = None
        self.ai_engine = None
        self.rag_engine = None
        self.voice_engine = None
        self.sound_manager = None
        self.system_monitor = None
        self.avatar_controller = None
        self.stats_tracker = None
        self.model_cli = None
        self.stt_manager = None
        self.streaming_tts = None
        self.conversation_flow = None
        
        # Setup signal handlers
        self._setup_signal_handlers()
        
        log_info("M1K3 CLI Core initialized")
    
    def _setup_signal_handlers(self):
        """Setup signal handlers for graceful shutdown with forced termination fallback"""
        self.signal_received = False  # Flag to prevent duplicate messages
        self.force_shutdown = threading.Event()  # Emergency shutdown flag
        
        def signal_handler(signum, frame):
            if not self.signal_received:  # First signal - graceful shutdown
                log_info(f"Received signal {signum}, initiating graceful shutdown")
                self.signal_received = True
                print(f"\n📡 Shutting down gracefully... (Press Ctrl+C again to force quit)")
                
                self.shutdown_requested = True
                self.running = False  # Immediate stop flag
                
                # Set force shutdown event for audio operations
                self.force_shutdown.set()
                
                # Start emergency shutdown timer
                def emergency_shutdown():
                    time.sleep(3.0)  # Give 3 seconds for graceful shutdown
                    if self.running or not self._is_fully_shutdown():
                        print("\n⚠️ Force terminating application...")
                        log_warning("Force terminating due to hanging shutdown")
                        os._exit(1)  # Force immediate termination
                
                emergency_thread = threading.Thread(target=emergency_shutdown, daemon=True)
                emergency_thread.start()
                
            else:  # Second signal - immediate forced termination
                print(f"\n🚨 Force shutdown requested!")
                log_warning("Immediate termination requested")
                os._exit(1)  # Force immediate termination
        
        signal.signal(signal.SIGINT, signal_handler)
        signal.signal(signal.SIGTERM, signal_handler)
    
    def initialize(self) -> bool:
        """Initialize CLI components with async optimization"""
        log_info("🚀 Initializing M1K3 CLI (Optimized)")
        self.state = CLIState.STARTING
        
        try:
            # Import performance optimization
            from src.utils.performance.startup_optimizer import get_startup_optimizer, create_performance_context, StartupPhase
            from src.engines.ai.async_model_loader import get_async_loader, ModelLoadTask, LoadingPriority
            
            self.optimizer = get_startup_optimizer()
            self.async_loader = get_async_loader()
            
            # Phase 1: Critical initialization (must complete before UI)
            with create_performance_context("critical_initialization"):
                if not self._initialize_critical_components():
                    log_error("Failed to initialize critical components")
                    return False
            
            # Phase 2: Start async loading of heavy components
            self._start_async_loading()
            
            # Phase 3: Initialize UI-ready components
            with create_performance_context("ui_initialization"):
                self._initialize_ui_components()
            
            # Wait for critical models to be ready (with timeout)
            if not self.async_loader.wait_for_critical(timeout=5.0):
                log_warning("⚠️ Critical models not ready, using fallbacks")
            
            self.state = CLIState.READY
            self.optimizer.set_phase(StartupPhase.READY)
            log_info("✅ CLI initialization complete (optimized)")
            return True
            
        except Exception as e:
            log_error(f"Initialization failed: {e}")
            self.state = CLIState.ERROR
            return False
    
    def _initialize_critical_components(self) -> bool:
        """Initialize only critical components needed for basic functionality"""
        log_info("🔧 Initializing critical components")
        
        # Setup minimal AI engine (fast fallback if needed)
        self.ai_engine = self.component_manager.get_ai_engine()
        if not self.ai_engine:
            log_error("❌ Failed to setup minimal AI engine")
            return False
        
        # Initialize command handler (lightweight)
        self.command_handler = self.component_manager.get_command_handler(self) # Pass self for context
        self.ai_processor = self.component_manager.get_ai_processor(self) # Pass self for context
        
        log_info("✅ Critical components ready")
        return True
    
    def _start_async_loading(self):
        """Start async loading of heavy components"""
        log_info("⚡ Starting async loading")

        # Import async loading classes
        from src.engines.ai.async_model_loader import ModelLoadTask, LoadingPriority

        # Register model loading tasks with ComponentManager's loaders
        self.async_loader.register_model(ModelLoadTask(
            name="primary_ai",
            priority=LoadingPriority.HIGH,
            loader_func=self.component_manager.get_ai_engine, # Use ComponentManager's loader
            estimated_time=5.0
        ))
        if self.rag_enabled:
            self.async_loader.register_model(ModelLoadTask(
                name="rag_engine",
                priority=LoadingPriority.MEDIUM,
                loader_func=self.component_manager.get_rag_engine, # Use ComponentManager's loader
                estimated_time=3.0
            ))
        if self.voice_enabled:
            self.async_loader.register_model(ModelLoadTask(
                name="voice_engine",
                priority=LoadingPriority.HIGH,
                loader_func=self.component_manager.get_voice_engine, # Use ComponentManager's loader
                estimated_time=8.0
            ))
        # Add other optional models here if needed
        
        # Start async loading
        self.async_loader.start_loading()
        
        # Register callbacks for when models are ready
        self.async_loader.register_completion_callback(self._on_critical_models_ready)
    
    def _initialize_ui_components(self):
        """Initialize UI and non-critical components"""
        log_info("🎨 Initializing UI components")
        
        # These can start while models load in background
        self.sound_manager = self.component_manager.get_sound_manager()
        self.avatar_controller = self.component_manager.get_avatar_controller()
        self.system_monitor = self.component_manager.get_system_monitor()
        self.session_stats = self.component_manager.get_session_stats()
        self.model_cli = self.component_manager.get_model_cli()
        
        # Setup voice and STT engines (deferred from critical path)
        if self.voice_enabled:
            self.voice_engine = self.component_manager.get_voice_engine()
        
        if self.stt_engine != "none":
            self.stt_manager = self.component_manager.get_stt_manager()
        
        # Setup streaming components if enabled
        if self.streaming_enabled:
            self.streaming_tts = self.component_manager.get_streaming_tts_engine()
        
        # Setup conversation flow if enabled
        if self.conversation_mode:
            self.conversation_flow = self.component_manager.get_conversation_flow_manager()
        
        # Update AI processor with newly loaded engines
        if hasattr(self, 'ai_processor'):
            self.ai_processor.update_engines(
                voice_engine=self.voice_engine,
                rag_engine=self.rag_engine
            )
        
        log_info("✅ UI components ready")
    
    
    
    def _register_ai_models(self):
        """Register AI models for async loading"""
        from src.engines.ai.async_model_loader import ModelLoadTask, LoadingPriority
        
        # Critical: Simple/fallback AI (loads first)
        self.async_loader.register_model(ModelLoadTask(
            name="simple_ai",
            priority=LoadingPriority.CRITICAL,
            loader_func=self._load_simple_ai,
            estimated_time=0.5
        ))
        
        # High: Primary AI engine
        self.async_loader.register_model(ModelLoadTask(
            name="primary_ai", 
            priority=LoadingPriority.HIGH,
            loader_func=self._load_primary_ai,
            estimated_time=5.0
        ))
        
        # Medium: RAG engine (if enabled)
        if self.rag_enabled:
            self.async_loader.register_model(ModelLoadTask(
                name="rag_engine",
                priority=LoadingPriority.MEDIUM,
                loader_func=self._load_rag_engine,
                estimated_time=3.0
            ))
    
    def _register_voice_models(self):
        """Register voice models for async loading"""
        from src.engines.ai.async_model_loader import ModelLoadTask, LoadingPriority
        
        if not self.voice_enabled:
            return
        
        # High priority: Basic TTS
        self.async_loader.register_model(ModelLoadTask(
            name="basic_tts",
            priority=LoadingPriority.HIGH,
            loader_func=self._load_basic_tts,
            estimated_time=1.0
        ))
        
        # Medium: Advanced TTS (VibeVoice, etc.)
        self.async_loader.register_model(ModelLoadTask(
            name="advanced_tts",
            priority=LoadingPriority.MEDIUM,
            loader_func=self._load_advanced_tts,
            estimated_time=8.0
        ))
    
    def _register_optional_models(self):
        """Register optional/enhancement models"""
        from src.engines.ai.async_model_loader import ModelLoadTask, LoadingPriority
        
        # Low priority: Advanced features
        self.async_loader.register_model(ModelLoadTask(
            name="enhancements",
            priority=LoadingPriority.LOW,
            loader_func=self._load_enhancements,
            estimated_time=2.0
        ))
    
    def _setup_minimal_ai(self) -> bool:
        """Setup minimal AI for immediate functionality"""
        try:
            # Try to use real AI first, then fall back to simple AI
            if self.initializer.is_component_available('local_ai_engine'):
                LocalAIEngine = self.initializer.get_component('local_ai_engine')
                self.ai_engine = LocalAIEngine()
                log_info("✅ Real AI engine ready")
                return True
            elif self.initializer.is_component_available('simple_ai_engine'):
                SimpleAIEngine = self.initializer.get_component('simple_ai_engine')
                self.ai_engine = SimpleAIEngine()
                log_info("✅ Fallback AI engine ready")
                return True
            else:
                log_error("❌ No AI engine available (neither local nor simple)")
                return False
        except Exception as e:
            log_error(f"Failed to setup minimal AI: {e}")
            return False
    
    def _load_simple_ai(self):
        """Load simple AI engine"""
        if self.initializer.is_component_available('simple_ai_engine'):
            SimpleAIEngine = self.initializer.get_component('simple_ai_engine')
            return SimpleAIEngine()
        return None
    
    def _load_primary_ai(self):
        """Load primary AI engine"""
        try:
            if self.initializer.is_component_available('local_ai_engine'):
                LocalAIEngine = self.initializer.get_component('local_ai_engine')
                return LocalAIEngine()
        except Exception as e:
            log_warning(f"Primary AI loading failed: {e}")
        return None
    
    def _load_rag_engine(self):
        """Load RAG engine"""
        try:
            if self.initializer.is_component_available('rag_engine'):
                M1K3RAGIntegratedEngine = self.initializer.get_component('rag_engine')
                return M1K3RAGIntegratedEngine()
        except Exception as e:
            log_warning(f"RAG engine loading failed: {e}")
        return None
    
    def _load_basic_tts(self):
        """Load basic TTS engine"""
        try:
            self._setup_voice_engine()
            return self.voice_engine
        except Exception as e:
            log_warning(f"Basic TTS loading failed: {e}")
        return None
    
    def _load_advanced_tts(self):
        """Load advanced TTS features"""
        try:
            # Setup streaming TTS if available
            self._setup_streaming_components()
            return True
        except Exception as e:
            log_warning(f"Advanced TTS loading failed: {e}")
        return None
    
    def _load_enhancements(self):
        """Load enhancement features"""
        try:
            # Setup STT
            self._setup_stt_engine()
            return True
        except Exception as e:
            log_warning(f"Enhancement loading failed: {e}")
        return None
    
    def _on_critical_models_ready(self, models: dict):
        """Callback when critical models are ready"""
        log_info("🎯 Critical models loaded, upgrading AI engine")
        
        # Upgrade to primary AI if available
        if "primary_ai" in models and models["primary_ai"]:
            self.ai_engine = self.component_manager.get_ai_engine() # Fetch from manager
            log_info("✅ Upgraded to primary AI engine")
        
        # Setup RAG if available
        if "rag_engine" in models and models["rag_engine"]:
            self.rag_engine = self.component_manager.get_rag_engine() # Fetch from manager
            log_info("✅ RAG engine ready")
        
        # Update optimizer
        if hasattr(self, 'optimizer'):
            self.optimizer.set_phase(self.optimizer.StartupPhase.LOADING_SECONDARY)
    
    def _initialize_minimal_for_query(self) -> bool:
        """Minimal initialization for single query mode"""
        log_info("🚀 Minimal initialization for single query")
        self.state = CLIState.STARTING
        
        try:
            # Absolutely minimal - just get a working AI engine
            self.ai_engine = self.component_manager.get_ai_engine()
            if not self.ai_engine:
                log_error("❌ Failed to setup minimal AI engine")
                return False
            
            # Minimal command processing
            self.command_handler = self.component_manager.get_command_handler(self)
            self.ai_processor = self.component_manager.get_ai_processor(self)
            
            self.state = CLIState.READY
            log_info("✅ Minimal initialization complete")
            return True
            
        except Exception as e:
            log_error(f"Minimal initialization failed: {e}")
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
                    self.ai_engine = SimpleAIEngine(auto_load=True)  # Ensure auto-load is enabled
                    if self.ai_engine.model_loaded:
                        log_info("✅ Simple AI engine loaded as fallback")
                    else:
                        log_warning("Simple AI engine created but model not loaded")
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
                    if self.voice_engine:
                        # Configure TTS engine preference
                        self._configure_voice_engine()
                        
                        # Load the appropriate model
                        load_success = self._load_voice_model()
                        if load_success:
                            # Enable voice synthesis on the engine
                            if hasattr(self.voice_engine, 'set_voice_enabled'):
                                self.voice_engine.set_voice_enabled(True)
                                log_debug("Voice engine enabled for synthesis")
                            
                            # Wire up force shutdown signal
                            if hasattr(self.voice_engine, 'force_shutdown'):
                                self.voice_engine.force_shutdown = self.force_shutdown
                            log_info("✅ Voice engine loaded")
                        else:
                            log_warning("Voice engine failed to load model")
                            self.voice_engine = None
                    else:
                        log_warning("Voice engine creation failed")
                        self.voice_engine = None
                except Exception as e:
                    log_error(f"Voice engine setup failed: {e}")
        else:
            log_warning("Voice engine not available")

        # Initialize intelligent TTS engine
        if self.initializer.is_component_available('intelligent_tts'):
            try:
                from src.engines.voice.intelligent_tts_engine import intelligent_tts_engine
                self.intelligent_tts = intelligent_tts_engine
                if self.intelligent_tts.load_model():
                    log_debug("✅ Intelligent TTS engine initialized")
                else:
                    log_warning("Intelligent TTS engine failed to load")
                    self.intelligent_tts = None
            except Exception as e:
                log_error(f"Intelligent TTS setup failed: {e}")
                self.intelligent_tts = None
        else:
            log_warning("Intelligent TTS not available")
            self.intelligent_tts = None
    
    def _configure_voice_engine(self):
        """Configure the voice engine based on CLI arguments"""
        try:
            # Determine the TTS engine to use
            engine_preference = self._determine_tts_engine()
            
            # Set engine preference
            if hasattr(self.voice_engine, 'set_engine_preference'):
                success = self.voice_engine.set_engine_preference(engine_preference)
                if success:
                    log_info(f"✅ TTS engine preference set to: {engine_preference}")
                else:
                    log_warning(f"Failed to set TTS engine to {engine_preference}")
            
            # Configure VibeVoice settings if using VibeVoice
            if engine_preference == "vibevoice":
                self._configure_vibevoice_settings()
            
            # Set voice profile
            if hasattr(self.voice_engine, 'set_profile'):
                profile_success = self.voice_engine.set_profile(self.voice_profile)
                if profile_success:
                    log_debug(f"Voice profile set to: {self.voice_profile}")
            
        except Exception as e:
            log_error(f"Voice engine configuration failed: {e}")
    
    def _determine_tts_engine(self) -> str:
        """Determine which TTS engine to use based on CLI arguments"""
        if self.tts_engine == "vibevoice":
            return "vibevoice"
        elif self.tts_engine == "kitten":
            return "kitten"
        elif self.tts_engine == "fallback":
            return "fallback"
        else:  # auto
            # Auto-detection logic: prefer VibeVoice if available, fallback to kitten
            from src.tts.controllers.vibevoice_manager import VibeVoiceManager
            try:
                vv_manager = VibeVoiceManager()
                if vv_manager.is_available():
                    log_info("Auto-detected: Using VibeVoice (available and preferred)")
                    return "vibevoice"
                else:
                    log_info("Auto-detected: Using KittenTTS (VibeVoice not available)")
                    return "kitten"
            except Exception:
                log_info("Auto-detected: Using KittenTTS (fallback)")
                return "kitten"
    
    def _configure_vibevoice_settings(self):
        """Configure VibeVoice-specific settings"""
        try:
            from src.tts.controllers.vibevoice_manager import VibeVoiceManager
            
            vv_manager = VibeVoiceManager()
            
            # Set generation quality
            if vv_manager.set_generation_quality(self.vibevoice_quality):
                log_info(f"✅ VibeVoice quality set to: {self.vibevoice_quality}")
            
            # Set speakers
            if self.multi_speaker or len(self.speakers) > 1:
                if vv_manager.set_speakers(self.speakers):
                    speakers_str = ", ".join(self.speakers)
                    log_info(f"✅ VibeVoice speakers set to: {speakers_str}")
            
            log_info(f"✅ VibeVoice configured: {self.vibevoice_model} model, {self.vibevoice_quality} quality")
            
        except Exception as e:
            log_error(f"VibeVoice configuration failed: {e}")
    
    def _load_voice_model(self) -> bool:
        """Load the voice model based on the selected TTS engine"""
        try:
            # Determine which engine is being used
            engine_preference = self._determine_tts_engine()
            
            if engine_preference == "vibevoice":
                # Load VibeVoice with specific variant
                log_info(f"Loading VibeVoice model: {self.vibevoice_model}")
                from src.tts.controllers.vibevoice_manager import VibeVoiceManager
                
                vv_manager = VibeVoiceManager(model_name=self.vibevoice_model)
                if vv_manager.load_model():
                    log_info("✅ VibeVoice model loaded successfully")
                    
                    # Configure VibeVoice-specific settings
                    self._configure_vibevoice_settings()
                    
                    # Set up the unified voice engine to use VibeVoice
                    if hasattr(self.voice_engine, 'set_engine_preference'):
                        self.voice_engine.set_engine_preference("vibevoice")
                    
                    # Load the base unified voice engine so is_loaded is set correctly
                    if hasattr(self.voice_engine, 'load_model'):
                        base_loaded = self.voice_engine.load_model()
                        if not base_loaded:
                            log_warning("Base voice engine failed to load, but VibeVoice is working")
                    
                    return True
                else:
                    log_error("❌ Failed to load VibeVoice model, falling back to KittenTTS")
                    return self.voice_engine.load_model()
            
            else:
                # Use standard loading for KittenTTS or fallback engines
                log_info(f"Loading {engine_preference} voice engine")
                return self.voice_engine.load_model()
                
        except Exception as e:
            log_error(f"Voice model loading failed: {e}")
            log_info("Attempting fallback to standard voice engine loading")
            try:
                return self.voice_engine.load_model()
            except Exception as fallback_error:
                log_error(f"Fallback voice loading also failed: {fallback_error}")
                return False
    
    def _setup_stt_engine(self):
        """Setup speech-to-text engine"""
        if self.stt_engine == "none":
            log_info("STT disabled by user")
            self.stt_manager = None
            return
        
        try:
            from engines.stt.stt_manager import STTManager
            
            # Set environment variables for STT configuration
            import os
            if self.stt_engine != "auto":
                os.environ['M1K3_STT_ENGINE'] = self.stt_engine
            if self.stt_engine == "whisper":
                os.environ['M1K3_USE_WHISPER'] = 'true'
            
            self.stt_manager = STTManager()
            
            # Apply specific engine selection if not auto
            if self.stt_engine != "auto" and self.stt_manager.is_available():
                available_engines = self.stt_manager.get_available_engines()
                
                if self.stt_engine in available_engines:
                    success = self.stt_manager.switch_engine(self.stt_engine)
                    if success:
                        log_info(f"✅ STT engine set to: {self.stt_engine}")
                    else:
                        log_warning(f"Failed to switch to {self.stt_engine}, using default")
                else:
                    log_warning(f"STT engine '{self.stt_engine}' not available. Available: {available_engines}")
            
            # Apply model/language configuration
            if self.stt_manager.is_available():
                current_engine = self.stt_manager.current_engine
                
                # Set model for Vosk/Whisper engines
                if hasattr(current_engine, 'set_model') and self.stt_model:
                    if self.stt_manager.current_engine_name in ['vosk', 'whisper']:
                        current_engine.set_model(self.stt_model)
                
                # Set language/locale
                if hasattr(current_engine, 'set_locale') and self.stt_language:
                    current_engine.set_locale(self.stt_language)
                elif hasattr(current_engine, 'locale') and self.stt_language:
                    current_engine.locale = self.stt_language
                
                log_info(f"✅ STT engine loaded: {self.stt_manager.current_engine_name}")
                
                # Wire up force shutdown signal to the current STT engine
                if hasattr(self.stt_manager, 'current_engine') and self.stt_manager.current_engine:
                    if hasattr(self.stt_manager.current_engine, 'force_shutdown'):
                        self.stt_manager.current_engine.force_shutdown = self.force_shutdown
                        log_debug("Force shutdown signal wired to STT engine")
                
                # Configure force internal microphone option for Bluetooth workarounds
                if self.force_internal_mic:
                    log_info("🎤 Force internal microphone enabled (Bluetooth workaround)")
                    print("🎤 Force internal microphone mode enabled")
                    print("   💡 This will attempt to use the internal microphone instead of Bluetooth")
                    
                    # Apply to macOS STT engine if available
                    if (hasattr(self.stt_manager, 'current_engine') and 
                        self.stt_manager.current_engine and
                        self.stt_manager.current_engine_name == "macos_native"):
                        
                        if hasattr(self.stt_manager.current_engine, 'force_internal_mic'):
                            self.stt_manager.current_engine.force_internal_mic = True
                            log_debug("Internal microphone mode configured for macOS STT")
                        else:
                            log_warning("macOS STT engine doesn't support force internal microphone")
                    else:
                        log_debug("Force internal mic applied to non-macOS engine (may not be relevant)")
                
                # Setup STT callbacks
                self.stt_manager.on_speech_detected = self._handle_speech_input
                self.stt_manager.on_listening_start = self._handle_listening_start
                self.stt_manager.on_listening_stop = self._handle_listening_stop
                self.stt_manager.on_error = self._handle_stt_error
                
            else:
                log_warning("STT engine loaded but no backends available")
                self.stt_manager = None
        except ImportError as e:
            log_warning(f"STT engine not available: {e}")
            self.stt_manager = None
        except Exception as e:
            log_error(f"STT engine setup failed: {e}")
            self.stt_manager = None
    
    def _setup_streaming_components(self):
        """Setup streaming TTS and conversation flow components"""
        log_info("🚀 Setting up streaming components")
        
        # Setup Streaming TTS if enabled
        if self.streaming_enabled or self.conversation_mode:
            if self.initializer.is_component_available('streaming_tts'):
                create_streaming_tts_engine = self.initializer.get_component('create_streaming_tts_engine')
                if create_streaming_tts_engine:
                    try:
                        self.streaming_tts = create_streaming_tts_engine(
                            voice_engine=self.voice_engine,
                            chunk_size=self.chunk_size,
                            chunk_timeout=self.chunk_timeout
                        )
                        log_info("✅ Streaming TTS engine initialized")
                    except Exception as e:
                        log_error(f"Streaming TTS setup failed: {e}")
                        self.streaming_tts = None
                else:
                    log_warning("Streaming TTS component not available")
            else:
                log_warning("Streaming TTS not available in initializer")
        
        # Setup Conversation Flow Manager if enabled
        if self.conversation_mode:
            if self.initializer.is_component_available('conversation_flow'):
                ConversationFlowManager = self.initializer.get_component('conversation_flow_manager')
                if ConversationFlowManager:
                    try:
                        self.conversation_flow = ConversationFlowManager(
                            stt_manager=self.stt_manager,
                            streaming_tts=self.streaming_tts,
                            ai_engine=self.ai_engine
                        )
                        
                        # Configure conversation flow settings
                        self.conversation_flow.enable_interruptions = self.enable_interruptions
                        self.conversation_flow.response_delay = self.response_delay
                        
                        # Setup callbacks
                        self.conversation_flow.on_state_change = self._handle_conversation_state_change
                        self.conversation_flow.on_turn_start = self._handle_conversation_turn_start
                        self.conversation_flow.on_turn_complete = self._handle_conversation_turn_complete
                        self.conversation_flow.on_interruption = self._handle_conversation_interruption
                        
                        log_info("✅ Conversation flow manager initialized")
                        
                        # Show streaming status
                        if self.streaming_enabled:
                            log_info("🎵 Streaming mode enabled - real-time TTS synthesis active")
                        if self.conversation_mode:
                            log_info("💬 Conversation mode enabled - natural turn-taking active")
                            
                    except Exception as e:
                        log_error(f"Conversation flow setup failed: {e}")
                        self.conversation_flow = None
                else:
                    log_warning("Conversation flow component not available")
            else:
                log_warning("Conversation flow not available in initializer")
        
        # Log final streaming status
        streaming_active = self.streaming_tts is not None
        conversation_active = self.conversation_flow is not None
        
        if streaming_active and conversation_active:
            log_info("🎊 Full streaming experience active!")
        elif streaming_active:
            log_info("🚀 Streaming TTS active")
        elif conversation_active:
            log_info("💬 Conversation flow active")
        else:
            log_info("💭 Using traditional response mode")
    
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

        # Start database session
        self._start_database_session()

        conversation_flow = self.component_manager.get_conversation_flow_manager()
        stt_manager = self.component_manager.get_stt_manager()
        
        # Start conversation flow if enabled
        if self.conversation_mode and conversation_flow:
            log_info("Starting conversation flow manager")
            if conversation_flow.start_conversation():
                log_info("✅ Conversation flow active - natural turn-taking enabled")
            else:
                log_warning("Failed to start conversation flow, falling back to standard mode")
                self.conversation_mode = False
        
        log_info("CLI ready for user input")
        
        # Voice-first mode announcement
        if self.voice_first and stt_manager and stt_manager.is_available():
            print("🎤 VOICE-FIRST MODE ENABLED")
            print(f"   - Primary engine: {stt_manager.current_engine_name}")
            print(f"   - Voice input will be used by default for all interactions")
            print(f"   - You can still type instead if speech recognition fails")
            print()
        
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
        
        # For single queries, use minimal initialization
        if not self._initialize_minimal_for_query():
            log_error("Failed to initialize CLI")
            return 1
        
        try:
            self.state = CLIState.PROCESSING
            
            ai_processor = self.component_manager.get_ai_processor(self)
            # Process the query
            response = ai_processor.process_ai_query(query, self.rag_enabled)
            if response:
                print(response)
                
                # Process with voice if enabled
                if self.voice_enabled:
                    ai_processor.process_response_with_voice(response, background=False)
                
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
        print("🧘 M1K3 - Local AI with Advanced Voice & Animations")
        print("=" * 60)
        
        # Display system info
        system_monitor = self.component_manager.get_system_monitor()
        if system_monitor:
            try:
                metrics = system_monitor.collect_metrics()
                print(f"💾 RAM: {metrics.memory_percent or 0:.1f}% | "
                      f"⚡ CPU: {metrics.cpu_usage or 0:.1f}% | "
                      f"🖥️  Platform: {metrics.os_name or 'Unknown'}")
            except Exception as e:
                log_debug(f"Error displaying system metrics: {e}")
        
        # Display AI engine info
        ai_engine = self.component_manager.get_ai_engine()
        if ai_engine:
            engine_name = type(ai_engine).__name__
            print(f"🧠 AI Engine: {engine_name}")
        
        rag_engine = self.component_manager.get_rag_engine()
        if self.rag_enabled and rag_engine:
            print("🔍 RAG: Enabled")
        
        voice_engine = self.component_manager.get_voice_engine()
        if self.voice_enabled and voice_engine:
            print("🔊 Voice: Enabled")
        
        stt_manager = self.component_manager.get_stt_manager()
        if stt_manager and stt_manager.is_available():
            engine_info = stt_manager.get_engine_info()
            current_engine = engine_info.get("current_engine", "unknown")
            print(f"🎤 STT: Enabled ({current_engine})")
        
        avatar_controller = self.component_manager.get_avatar_controller()
        if self.auto_avatar and avatar_controller: # Check if avatar is enabled and loaded
            print(f"👤 Avatar: http://localhost:{self.avatar_port}")
        
        print("-" * 60)
    
    def _play_startup_sounds(self):
        """Play startup sound sequence"""
        sound_manager = self.component_manager.get_sound_manager()
        if sound_manager:
            try:
                sound_manager.play_startup_sequence()
            except Exception as e:
                log_debug(f"Startup sounds failed: {e}")
    
    def _get_user_input(self) -> Optional[str]:
        """Get user input with prompt (text or voice)"""
        try:
            stt_manager = self.component_manager.get_stt_manager()
            # Voice-first mode: automatically start with voice input
            if self.voice_first and stt_manager and stt_manager.is_available():
                print("🎤 Voice-first mode: Listening... (speak now or type to override)")
                print(f"🔧 DEBUG: Using {stt_manager.current_engine_name} engine")
                
                # Check for shutdown before potentially blocking voice input
                if self.shutdown_requested:
                    return None
                
                voice_result = stt_manager.listen_once(timeout=15.0)
                
                # Check for shutdown after voice input
                if self.shutdown_requested:
                    return None
                    
                if voice_result:
                    user_input = voice_result.text
                    print(f"🔊 Heard: {user_input}")
                    return user_input
                else:
                    print("⚠️ No speech detected, falling back to text input")
                    # Fall through to text input
            
            # Standard mode or fallback: prompt for text with optional voice
            if stt_manager and stt_manager.is_available() and not self.voice_first:
                prompt = "💬 You (type or press ENTER for voice): "
            else:
                prompt = "💬 You: "
            
            # Get text input - make it interruptible 
            print(prompt, end="", flush=True)
            
            # Check for shutdown before blocking input
            if self.shutdown_requested:
                return None
                
            user_input = input().strip()
            
            # If empty input and STT available (standard mode), use voice
            if not user_input and stt_manager and stt_manager.is_available() and not self.voice_first:
                # Check for shutdown before voice input
                if self.shutdown_requested:
                    return None
                    
                print("🎤 Listening... (speak now)")
                print(f"🔧 DEBUG: Using {stt_manager.current_engine_name} engine")
                voice_result = stt_manager.listen_once(timeout=15.0)
                
                # Check for shutdown after voice input
                if self.shutdown_requested:
                    return None
                    
                if voice_result:
                    user_input = voice_result.text
                    print(f"🔊 Heard: {user_input}")
                else:
                    print("⚠️ No speech detected or recognition failed")
                    return None
            
            return user_input if user_input else None
            
        except (KeyboardInterrupt, EOFError):
            raise
        except Exception as e:
            log_error(f"Error getting user input: {e}")
            return None
    
    def _handle_speech_input(self, stt_result):
        """Handle speech input from STT engine"""
        try:
            stt_manager = self.component_manager.get_stt_manager() # Fetch from manager
            if stt_result and stt_result.text:
                log_info(f"Speech detected: {stt_result.text}")
                print(f"🔊 Heard: {stt_result.text}")
                # Process the speech input as if it were typed
                if not self._process_user_input(stt_result.text):
                    return
        except Exception as e:
            log_error(f"Error handling speech input: {e}")
    
    def _handle_listening_start(self):
        """Handle STT listening start"""
        try:
            print("🎤 Listening...")
            avatar_controller = self.component_manager.get_avatar_controller()
            if avatar_controller:
                from avatar.avatar_controller import AvatarEmotion
                avatar_controller.update_emotion("", "listening", force_emotion=AvatarEmotion.THINKING)
        except Exception as e:
            log_debug(f"Error handling listening start: {e}")
    
    def _handle_listening_stop(self):
        """Handle STT listening stop"""
        try:
            avatar_controller = self.component_manager.get_avatar_controller()
            if avatar_controller:
                from avatar.avatar_controller import AvatarEmotion
                avatar_controller.update_emotion("", "listening_done", force_emotion=AvatarEmotion.HAPPY)
        except Exception as e:
            log_debug(f"Error handling listening stop: {e}")
    
    def _handle_stt_error(self, error_message: str):
        """Handle STT errors"""
        log_error(f"STT Error: {error_message}")
        print(f"⚠️ Voice recognition error: {error_message}")
    
    # Streaming and Conversation Flow Callbacks
    def _handle_conversation_state_change(self, new_state):
        """Handle conversation flow state changes"""
        try:
            log_debug(f"Conversation state: {new_state.value}")
            
            # Update avatar based on conversation state
            avatar_controller = self.component_manager.get_avatar_controller()
            if avatar_controller:
                from avatar.avatar_controller import AvatarEmotion
                
                state_emotion_map = {
                    "waiting": AvatarEmotion.HAPPY,
                    "listening": AvatarEmotion.THINKING,
                    "processing": AvatarEmotion.THINKING,
                    "speaking": AvatarEmotion.EXCITED,
                    "interrupted": AvatarEmotion.SURPRISED,
                    "error": AvatarEmotion.SAD
                }
                
                emotion = state_emotion_map.get(new_state.value, AvatarEmotion.HAPPY)
                avatar_controller.update_emotion("", f"conversation_{new_state.value}", 
                                                     force_emotion=emotion)
        except Exception as e:
            log_error(f"Error handling conversation state change: {e}")
    
    def _handle_conversation_turn_start(self, turn):
        """Handle conversation turn start"""
        try:
            turn_type = turn.turn_type.value
            content_preview = turn.content[:50] + "..." if len(turn.content) > 50 else turn.content
            
            log_info(f"Turn started: {turn_type} - '{content_preview}'")
            
            # Visual feedback
            if turn_type == "user_speech":
                print(f"👤 You: {turn.content}")
            elif turn_type == "ai_response":
                print(f"🤖 Assistant: ", end="", flush=True)
                
        except Exception as e:
            log_error(f"Error handling conversation turn start: {e}")
    
    def _handle_conversation_turn_complete(self, turn):
        """Handle conversation turn completion"""
        try:
            turn_type = turn.turn_type.value
            log_debug(f"Turn completed: {turn_type}")
            
            if turn_type == "ai_response":
                print()  # New line after AI response
                
        except Exception as e:
            log_error(f"Error handling conversation turn complete: {e}")
    
    def _handle_conversation_interruption(self, turn):
        """Handle user interruption of AI speech"""
        try:
            log_info("User interruption detected")
            print("\n🚫 [Interrupted]")
            print(f"👤 You: {turn.content}")
            
        except Exception as e:
            log_error(f"Error handling conversation interruption: {e}")
    
    def _process_user_input(self, user_input: str) -> bool:
        """Process user input (returns False to exit)"""
        try:
            self.state = CLIState.PROCESSING
            
            command_handler = self.component_manager.get_command_handler(self)
            # Check if it's a command
            if command_handler.is_command(user_input):
                command_name, args = command_handler.parse_command(user_input)
                if command_name:
                    result = command_handler.execute_command(command_name, args)
                    # Some commands (like quit) return False to signal exit
                    return result if result is not None else True
            
            # Otherwise, process as AI query
            else:
                conversation_flow = self.component_manager.get_conversation_flow_manager()
                streaming_tts = self.component_manager.get_streaming_tts_engine()
                ai_processor = self.component_manager.get_ai_processor(self)
                
                # Use conversation flow if enabled, otherwise use standard processing
                if self.conversation_mode and conversation_flow:
                    # Conversation flow handles AI response processing internally
                    # Just simulate user input for now - the conversation flow will handle the rest
                    log_debug("Processing through conversation flow")
                else:
                    # Standard AI processing with optional streaming
                    if self.streaming_enabled and streaming_tts:
                        response = self._process_streaming_ai_response(user_input)
                    else:
                        response = ai_processor.process_full_response_pipeline(
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
    
    def _process_streaming_ai_response(self, user_input: str) -> str:
        """Process AI response with streaming TTS"""
        try:
            log_info("Processing with streaming TTS")
            
            rag_engine = self.component_manager.get_rag_engine()
            ai_engine = self.component_manager.get_ai_engine()
            streaming_tts = self.component_manager.get_streaming_tts_engine()
            
            # Generate AI response
            if self.rag_enabled and rag_engine:
                ai_response = rag_engine.generate_response(user_input)
            else:
                ai_response = ai_engine.generate_response(user_input)
            
            print(f"\n🤖 M1K3: ", end="", flush=True)
            
            # Handle streaming vs non-streaming response
            if hasattr(ai_response, '__iter__') and not isinstance(ai_response, str):
                # Streaming response
                response_text = ""
                for chunk in streaming_tts.process_token_stream(ai_response):
                    # Print tokens as they're processed
                    print(chunk.text, end=" ", flush=True)
                    response_text += chunk.text + " "
                
                print("\n")  # New line after streaming complete
                return response_text.strip()
            else:
                # Non-streaming response - use streaming TTS anyway
                response_text = ai_response if ai_response else ""
                print(response_text)
                
                if streaming_tts and response_text:
                    streaming_tts.add_text_chunk(response_text)
                
                return response_text
                
        except Exception as e:
            log_error(f"Error in streaming AI response: {e}")
            print(f"❌ Streaming error: {e}")
            return ""
    
    def toggle_voice(self) -> bool:
        """Toggle voice synthesis on/off"""
        self.voice_enabled = not self.voice_enabled
        status = "enabled" if self.voice_enabled else "disabled"
        print(f"🔊 Voice synthesis {status}")
        log_info(f"Voice synthesis {status}")
        
        # Also update voice engine if available
        voice_engine = self.component_manager.get_voice_engine()
        if voice_engine and hasattr(voice_engine, 'voice_enabled'):
            voice_engine.voice_enabled = self.voice_enabled
        
        return True
    
    def set_voice_profile(self, profile_name: str) -> bool:
        """Set voice profile for TTS"""
        voice_engine = self.component_manager.get_voice_engine()
        if not voice_engine:
            return False
        
        if hasattr(voice_engine, 'set_profile'):
            success = voice_engine.set_profile(profile_name)
            if success:
                log_info(f"Voice profile changed to: {profile_name}")
                return True
            else:
                # Show available profiles
                if hasattr(voice_engine, 'profiles'):
                    available = ', '.join(voice_engine.profiles.keys())
                    print(f"⚠️ Available profiles: {available}")
                return False
        return False
    
    def show_tts_status(self) -> bool:
        """Show TTS system status and settings"""
        voice_engine = self.component_manager.get_voice_engine()
        if not voice_engine:
            print("⚠️ Voice engine not available")
            return False
        
        if hasattr(voice_engine, 'get_status'):
            status = voice_engine.get_status()
            print(f"\n🎤 Voice Engine Status:")
            print(f"  Available: {'✅' if status.get('available', False) else '❌'}")
            print(f"  Loaded: {'✅' if status.get('loaded', False) else '❌'}")
            print(f"  Enabled: {'✅' if status.get('enabled', False) else '❌'}")
            print(f"  Model: {status.get('model', 'Unknown')}")
            print(f"  Profile: {status.get('current_profile', 'natural')}")
            
            if hasattr(voice_engine, 'profiles'):
                print(f"\n🎭 Available Profiles:")
                for name, config in voice_engine.profiles.items():
                    current = "👆" if name == status.get('current_profile') else "  "
                    desc = config.get('description', 'No description')
                    print(f"  {current} {name}: {desc}")
            
            return True
        
        print("⚠️ TTS status not available")
        return False

    def calculate_eco_impact(self, tokens_used: int = 0) -> dict:
        """Calculate environmental impact savings vs cloud AI"""
        if not hasattr(self, 'session_stats') or not self.session_stats:
            return {}

        # Get current session stats
        stats = self.session_stats.current_stats

        return {
            'water_saved': stats.water_saved_ml,
            'energy_saved': stats.energy_saved_wh,
            'co2_saved': stats.co2_saved_g,
            'queries_handled': stats.queries_handled,
            'session_duration': self.session_stats.get_session_duration_str()
        }

    def record_query_stats(self, response_time: float = 0, tokens: int = 0, content: str = ""):
        """Record query statistics for eco metrics and achievements"""
        if hasattr(self, 'session_stats') and self.session_stats:
            self.session_stats.record_query(response_time, tokens)

            # Record word count for virtual pet features
            if content:
                word_count = len(content.split())
                self.session_stats.current_stats.total_words_generated += word_count

            # Check for exciting insights or achievements
            insight = self.session_stats.get_exciting_insight()
            if insight:
                print(f"\n{insight}")

    def _cleanup(self):
        """Cleanup resources before exit with timeouts and emergency fallback"""
        log_info("🧹 Cleaning up CLI resources")
        self.state = CLIState.SHUTTING_DOWN
        
        cleanup_tasks = []
        
        try:
            # Define cleanup tasks with timeouts
            def safe_cleanup(name: str, cleanup_func, timeout: float = 2.0):
                """Execute cleanup function with timeout"""
                try:
                    import concurrent.futures
                    with concurrent.futures.ThreadPoolExecutor() as executor:
                        future = executor.submit(cleanup_func)
                        try:
                            future.result(timeout=timeout)
                            log_info(f"✅ {name} cleaned up")
                            return True
                        except concurrent.futures.TimeoutError:
                            log_warning(f"⚠️ {name} cleanup timed out after {timeout}s")
                            return False
                except Exception as e:
                    log_error(f"❌ {name} cleanup failed: {e}")
                    return False
            
            # Stop audio operations first (highest priority)
            if self.force_shutdown:
                self.force_shutdown.set()
                log_debug("Force shutdown signal activated")
            
            # Stop AI processor
            ai_processor = self.component_manager.get_ai_processor(self)
            if ai_processor:
                safe_cleanup("AI processor", 
                           lambda: ai_processor.stop_processing(), timeout=1.0)
            
            # Stop STT manager
            stt_manager = self.component_manager.get_stt_manager()
            if stt_manager:
                safe_cleanup("STT manager", 
                           lambda: stt_manager.cleanup(), timeout=2.0)
            
            # Stop streaming components
            conversation_flow = self.component_manager.get_conversation_flow_manager()
            if conversation_flow:
                def cleanup_conversation():
                    conversation_flow.stop_conversation()
                    conversation_flow.cleanup()
                safe_cleanup("Conversation flow", cleanup_conversation, timeout=2.0)
            
            streaming_tts = self.component_manager.get_streaming_tts_engine()
            if streaming_tts:
                def cleanup_streaming():
                    streaming_tts.stop_streaming()
                    streaming_tts.cleanup()
                safe_cleanup("Streaming TTS", cleanup_streaming, timeout=2.0)
            
            # Stop avatar server
            avatar_controller = self.component_manager.get_avatar_controller()
            if self.auto_avatar and avatar_controller:
                # Avatar server cleanup needs to be handled by the component manager
                safe_cleanup("Avatar server", lambda: avatar_controller.cleanup(), timeout=1.5)
            
            # Cleanup voice engine
            voice_engine = self.component_manager.get_voice_engine()
            if voice_engine and hasattr(voice_engine, 'cleanup'):
                safe_cleanup("Voice engine", 
                           lambda: voice_engine.cleanup(), timeout=1.0)
            
            # Cleanup AI engines
            ai_engine = self.component_manager.get_ai_engine()
            if ai_engine and hasattr(ai_engine, 'cleanup'):
                safe_cleanup("AI engine", 
                           lambda: ai_engine.cleanup(), timeout=1.0)
            
            rag_engine = self.component_manager.get_rag_engine()
            if rag_engine and hasattr(rag_engine, 'cleanup'):
                safe_cleanup("RAG engine", 
                           lambda: rag_engine.cleanup(), timeout=1.0)
            
            # Shutdown model monitor background thread
            model_cli = self.component_manager.get_model_cli()
            if model_cli and hasattr(model_cli, 'monitor'):
                if hasattr(model_cli.monitor, 'shutdown'):
                    safe_cleanup("Model monitor", 
                               lambda: model_cli.monitor.shutdown(), timeout=1.0)
            
            # End database session
            safe_cleanup("Database session",
                       lambda: self._end_database_session(), timeout=1.0)

            # Final cleanup
            self.running = False
            log_info("✅ CLI cleanup complete")
            
        except Exception as e:
            log_error(f"❌ Error during cleanup: {e}")
            # Emergency cleanup - force all audio operations to stop
            try:
                import sounddevice as sd
                sd.stop()
                sd._terminate()
            except:
                pass
    
    def get_state(self) -> CLIState:
        """Get current CLI state"""
        return self.state
    
    def is_running(self) -> bool:
        """Check if CLI is running"""
        return self.running and not self.shutdown_requested
    
    def _is_fully_shutdown(self) -> bool:
        """Check if all components have fully shut down"""
        try:
            # Check for active threads (excluding daemon threads and main thread)
            active_threads = [t for t in threading.enumerate() 
                             if t.is_alive() and not t.daemon and t != threading.main_thread()]
            
            # Check for active audio operations
            has_active_audio = False
            try:
                import sounddevice as sd
                # Check if sounddevice has active streams
                if hasattr(sd, '_streams') and sd._streams:
                    has_active_audio = any(stream.active for stream in sd._streams.values())
            except (ImportError, AttributeError):
                pass
            
            # Check component states
            components_active = []
            if self.stt_manager and hasattr(self.stt_manager, 'is_listening'):
                if self.stt_manager.is_listening():
                    components_active.append("STT Manager")
            
            if self.conversation_flow and hasattr(self.conversation_flow, 'is_active'):
                if self.conversation_flow.is_active():
                    components_active.append("Conversation Flow")
            
            if self.streaming_tts and hasattr(self.streaming_tts, 'is_streaming'):
                if self.streaming_tts.is_streaming():
                    components_active.append("Streaming TTS")
            
            is_shutdown = (
                not self.running and 
                self.shutdown_requested and
                len(active_threads) == 0 and
                not has_active_audio and
                len(components_active) == 0
            )
            
            if not is_shutdown:
                log_debug(f"Shutdown incomplete - Active threads: {len(active_threads)}, "
                         f"Active audio: {has_active_audio}, Active components: {components_active}")
            
            return is_shutdown
            
        except Exception as e:
            log_error(f"Error checking shutdown status: {e}")
            return False  # Assume not shutdown if we can't determine state

    # Session management methods

    def _generate_session_id(self) -> str:
        """Generate a unique session ID"""
        import uuid
        from datetime import datetime
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        session_uuid = str(uuid.uuid4())[:8]
        return f"{timestamp}_{session_uuid}"

    def _start_database_session(self):
        """Start database session if available"""
        try:
            # Import here to avoid circular imports
            from src.database.conversation_manager import get_conversation_manager

            manager = get_conversation_manager()
            manager.start_session(self.session_id, self.current_personality)
            log_debug(f"Started database session: {self.session_id}")
        except ImportError:
            log_debug("Database session not available - DuckDB not installed")
        except Exception as e:
            log_warning(f"Failed to start database session: {e}")

    def _end_database_session(self):
        """End database session if available"""
        try:
            from src.database.conversation_manager import get_conversation_manager

            # Get stats from session statistics tracker if available
            total_queries = 0
            total_tokens = 0

            try:
                from ..utils.logging.session_stats import get_stats_tracker
                stats_tracker = get_stats_tracker()
                stats = stats_tracker.get_stats_summary()
                total_queries = stats.get('queries', 0)
                total_tokens = stats.get('tokens', 0)
            except:
                pass

            manager = get_conversation_manager()
            manager.end_session(self.session_id, total_queries, total_tokens)
            log_debug(f"Ended database session: {self.session_id}")
        except ImportError:
            pass  # Database not available
        except Exception as e:
            log_warning(f"Failed to end database session: {e}")

    def _safe_voice_synthesis(self, text: str, background: bool = True, use_intelligent_tts: bool = False):
        """Safely synthesize voice using the intelligent TTS system"""
        if not text or not text.strip():
            return

        try:
            # Use intelligent TTS engine if available
            if use_intelligent_tts and hasattr(self, 'intelligent_tts') and self.intelligent_tts:
                success = self.intelligent_tts.synthesize_and_play(text.strip(), background=background)
                if success:
                    log_debug("Voice synthesis completed with intelligent TTS")
                    return

            # Fallback to voice engine
            if hasattr(self, 'voice_engine') and self.voice_engine:
                self.voice_engine.synthesize_and_play(text.strip(), background=background)
                log_debug("Voice synthesis completed with voice engine")
            else:
                log_debug("No voice synthesis engine available")

        except Exception as e:
            log_error(f"Voice synthesis failed: {e}")