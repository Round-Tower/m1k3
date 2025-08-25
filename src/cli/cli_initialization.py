#!/usr/bin/env python3
"""
CLI Initialization Module
Handles all startup logic, imports, and component initialization for M1K3 CLI
"""

import os
import sys
import time
from typing import Optional, Dict, Any
from enum import Enum

from .cli_logging import get_cli_logger, log_info, log_warning, log_error, log_debug


class ComponentStatus(Enum):
    """Status of imported components"""
    AVAILABLE = "available"
    NOT_AVAILABLE = "not_available"
    ERROR = "error"


class CLIInitializer:
    """Handles initialization of all CLI components and dependencies"""
    
    def __init__(self):
        self.logger = get_cli_logger()
        self.component_status: Dict[str, ComponentStatus] = {}
        self.components: Dict[str, Any] = {}
        
    def initialize_environment(self):
        """Initialize environment variables and basic settings"""
        log_info("🔧 Initializing environment")
        
        # Enable HuggingFace tokenizers parallelism for better performance
        os.environ['TOKENIZERS_PARALLELISM'] = 'true'
        log_debug("Set TOKENIZERS_PARALLELISM=true")
        
        return True
    
    def import_core_dependencies(self):
        """Import and validate core dependencies"""
        log_info("📦 Loading core dependencies")
        
        # System utilities
        try:
            from src.utils.performance.system_metrics import SystemMonitor
            self.components['system_monitor'] = SystemMonitor
            self.component_status['system_monitor'] = ComponentStatus.AVAILABLE
            log_debug("✅ SystemMonitor loaded")
        except ImportError as e:
            log_error(f"❌ SystemMonitor not available: {e}")
            self.component_status['system_monitor'] = ComponentStatus.NOT_AVAILABLE
        
        # CLI animations
        try:
            from src.cli.cli_animations import CLIAnimator, AnimationType
            self.components['cli_animator'] = CLIAnimator
            self.components['animation_type'] = AnimationType
            self.component_status['cli_animations'] = ComponentStatus.AVAILABLE
            log_debug("✅ CLI animations loaded")
        except ImportError as e:
            log_error(f"❌ CLI animations not available: {e}")
            self.component_status['cli_animations'] = ComponentStatus.NOT_AVAILABLE
        
        # Sound management
        try:
            from sound_manager import SoundManager, ContextualSoundManager
            self.components['sound_manager'] = SoundManager
            self.components['contextual_sound_manager'] = ContextualSoundManager
            self.component_status['sound_manager'] = ComponentStatus.AVAILABLE
            log_debug("✅ Sound manager loaded")
        except ImportError as e:
            log_error(f"❌ Sound manager not available: {e}")
            self.component_status['sound_manager'] = ComponentStatus.NOT_AVAILABLE
            
        # Greeting engine
        try:
            from llm_greeting_engine import LLMGreetingEngine, create_greeting_context
            self.components['greeting_engine'] = LLMGreetingEngine
            self.components['create_greeting_context'] = create_greeting_context
            self.component_status['greeting_engine'] = ComponentStatus.AVAILABLE
            log_debug("✅ Greeting engine loaded")
        except ImportError as e:
            log_error(f"❌ Greeting engine not available: {e}")
            self.component_status['greeting_engine'] = ComponentStatus.NOT_AVAILABLE
        
        return True
    
    def import_ai_engines(self):
        """Import AI engines with fallback chain"""
        log_info("🧠 Loading AI engines")
        
        # Try to import the real AI engine first
        try:
            from src.engines.ai.ai_inference import LocalAIEngine
            self.components['local_ai_engine'] = LocalAIEngine
            self.component_status['real_ai'] = ComponentStatus.AVAILABLE
            log_info("🧠 Using real AI inference engine")
        except ImportError as e:
            log_warning(f"⚠️  Real AI engine not available: {e}")
            log_info("🔄 Falling back to mock AI engine")
            try:
                from src.engines.ai.simple_ai_engine import SimpleAIEngine
                self.components['simple_ai_engine'] = SimpleAIEngine
                self.component_status['real_ai'] = ComponentStatus.NOT_AVAILABLE
                self.component_status['simple_ai'] = ComponentStatus.AVAILABLE
            except ImportError as e2:
                log_error(f"❌ Simple AI engine not available: {e2}")
                self.component_status['simple_ai'] = ComponentStatus.NOT_AVAILABLE
        
        # Try to import RAG engine
        try:
            from src.rag.m1k3_rag_integration import M1K3RAGIntegratedEngine
            self.components['rag_engine'] = M1K3RAGIntegratedEngine
            self.component_status['rag_engine'] = ComponentStatus.AVAILABLE
            log_info("🧠 RAG (Retrieval-Augmented Generation) engine available")
        except ImportError as e:
            log_warning(f"⚠️  RAG engine not available: {e}")
            self.component_status['rag_engine'] = ComponentStatus.NOT_AVAILABLE
        
        # Model management CLI
        try:
            from src.cli.cli_model_commands import ModelCLI
            self.components['model_cli'] = ModelCLI
            self.component_status['model_cli'] = ComponentStatus.AVAILABLE
            log_debug("✅ Enhanced model CLI loaded")
        except ImportError as e:
            log_warning(f"⚠️  Enhanced model CLI not available: {e}")
            self.component_status['model_cli'] = ComponentStatus.NOT_AVAILABLE
        
        return True
    
    def import_voice_components(self):
        """Import voice synthesis components"""
        log_info("🎭 Loading voice components")
        
        # Voice engine
        try:
            from src.engines.voice.voice_engine import create_voice_engine
            self.components['create_voice_engine'] = create_voice_engine
            self.component_status['voice_engine'] = ComponentStatus.AVAILABLE
            log_debug("✅ Voice engine loaded")
        except ImportError as e:
            log_warning(f"⚠️  Voice engine not available: {e}")
            self.component_status['voice_engine'] = ComponentStatus.NOT_AVAILABLE
        
        # Intelligent TTS System
        try:
            from src.tts.controllers.intelligent_tts_controller import create_intelligent_tts_controller
            from src.utils.model_output_parser import parse_model_output, ContentType
            from src.tts.effects.content_specific_effects import create_content_effects_manager
            
            self.components['create_intelligent_tts_controller'] = create_intelligent_tts_controller
            self.components['parse_model_output'] = parse_model_output
            self.components['content_type'] = ContentType
            self.components['create_content_effects_manager'] = create_content_effects_manager
            self.component_status['intelligent_tts'] = ComponentStatus.AVAILABLE
            log_info("🎭 Intelligent TTS system with content-specific voice effects available")
        except ImportError as e:
            log_warning(f"⚠️  Intelligent TTS not available: {e}")
            self.component_status['intelligent_tts'] = ComponentStatus.NOT_AVAILABLE
        
        # Voice text preprocessor
        try:
            from voice_text_preprocessor import preprocess_for_voice_synthesis
            self.components['preprocess_for_voice_synthesis'] = preprocess_for_voice_synthesis
            self.component_status['voice_preprocessor'] = ComponentStatus.AVAILABLE
            log_debug("✅ Voice text preprocessor loaded")
        except ImportError:
            log_warning("⚠️  Voice text preprocessor not available")
            self.component_status['voice_preprocessor'] = ComponentStatus.NOT_AVAILABLE
        
        return True
    
    def import_avatar_components(self):
        """Import avatar system components"""
        log_info("👤 Loading avatar components")
        
        try:
            from src.avatar.avatar_server import (
                start_avatar_server, stop_avatar_server, is_avatar_server_running,
                send_avatar_emotion, send_avatar_state, send_avatar_progress, get_avatar_server_status,
                send_chat_ai_start, send_chat_ai_chunk, send_chat_ai_complete, send_sound_trigger, 
                send_metrics_update, send_classification_update, send_thinking_phase_update, 
                send_generation_stream_update
            )
            from src.avatar.avatar_controller import AvatarController, AvatarEmotion, AvatarState as AvatarServerState
            
            self.components['avatar_server'] = {
                'start_avatar_server': start_avatar_server,
                'stop_avatar_server': stop_avatar_server,
                'is_avatar_server_running': is_avatar_server_running,
                'send_avatar_emotion': send_avatar_emotion,
                'send_avatar_state': send_avatar_state,
                'send_avatar_progress': send_avatar_progress,
                'get_avatar_server_status': get_avatar_server_status,
                'send_chat_ai_start': send_chat_ai_start,
                'send_chat_ai_chunk': send_chat_ai_chunk,
                'send_chat_ai_complete': send_chat_ai_complete,
                'send_sound_trigger': send_sound_trigger,
                'send_metrics_update': send_metrics_update,
                'send_classification_update': send_classification_update,
                'send_thinking_phase_update': send_thinking_phase_update,
                'send_generation_stream_update': send_generation_stream_update
            }
            self.components['avatar_controller'] = AvatarController
            self.components['avatar_emotion'] = AvatarEmotion
            self.components['avatar_server_state'] = AvatarServerState
            
            self.component_status['avatar'] = ComponentStatus.AVAILABLE
            log_debug("✅ Avatar system loaded")
        except ImportError as e:
            log_warning(f"⚠️  Avatar system not available: {e}")
            self.component_status['avatar'] = ComponentStatus.NOT_AVAILABLE
        
        return True
    
    def import_optional_components(self):
        """Import optional components with graceful fallback"""
        log_info("🔧 Loading optional components")
        
        # WebSocket client
        try:
            import websocket
            self.components['websocket'] = websocket
            self.component_status['websocket_client'] = ComponentStatus.AVAILABLE
            log_debug("✅ WebSocket client loaded")
        except ImportError:
            log_warning("⚠️  WebSocket client not available. Run: pip install websocket-client")
            self.component_status['websocket_client'] = ComponentStatus.NOT_AVAILABLE
        
        # Model transparency engine
        try:
            from src.engines.ai.model_transparency import ModelTransparencyEngine, TransparencyLevel, transparency_engine
            self.components['model_transparency_engine'] = ModelTransparencyEngine
            self.components['transparency_level'] = TransparencyLevel
            self.components['transparency_engine'] = transparency_engine
            self.component_status['transparency'] = ComponentStatus.AVAILABLE
            log_info("✅ Model transparency engine available")
        except ImportError:
            log_warning("⚠️  Model transparency engine not available")
            self.component_status['transparency'] = ComponentStatus.NOT_AVAILABLE
        
        # Streaming response filter
        try:
            from streaming_response_filter import filter_colon_prefix
            self.components['filter_colon_prefix'] = filter_colon_prefix
            self.component_status['streaming_filter'] = ComponentStatus.AVAILABLE
            log_debug("✅ Streaming response filter loaded")
        except ImportError:
            log_warning("⚠️  Streaming response filter not available")
            self.component_status['streaming_filter'] = ComponentStatus.NOT_AVAILABLE
        
        return True
    
    def import_model_utilities(self):
        """Import model download and management utilities"""
        log_info("📥 Loading model utilities")
        
        try:
            from src.models.loaders.download_model import download_model
            self.components['download_model'] = download_model
            self.component_status['download_model'] = ComponentStatus.AVAILABLE
            log_debug("✅ Model download utility loaded")
        except ImportError as e:
            log_error(f"❌ Model download utility not available: {e}")
            self.component_status['download_model'] = ComponentStatus.NOT_AVAILABLE
        
        return True
    
    def initialize_all_components(self) -> bool:
        """Initialize all components in the correct order"""
        log_info("🚀 Initializing M1K3 CLI components")
        start_time = time.time()
        
        success = (
            self.initialize_environment() and
            self.import_core_dependencies() and
            self.import_ai_engines() and
            self.import_voice_components() and
            self.import_avatar_components() and
            self.import_optional_components() and
            self.import_model_utilities()
        )
        
        end_time = time.time()
        initialization_time = end_time - start_time
        
        if success:
            log_info(f"✅ All components initialized in {initialization_time:.2f}s")
            self._log_component_summary()
        else:
            log_error("❌ Component initialization failed")
        
        return success
    
    def _log_component_summary(self):
        """Log summary of component availability"""
        log_debug("Component status summary:")
        for component, status in self.component_status.items():
            status_symbol = "✅" if status == ComponentStatus.AVAILABLE else "❌"
            log_debug(f"  {status_symbol} {component}: {status.value}")
    
    def get_component(self, name: str) -> Any:
        """Get a loaded component by name"""
        return self.components.get(name)
    
    def is_component_available(self, name: str) -> bool:
        """Check if a component is available"""
        return self.component_status.get(name) == ComponentStatus.AVAILABLE
    
    def get_component_status(self, name: str) -> ComponentStatus:
        """Get the status of a specific component"""
        return self.component_status.get(name, ComponentStatus.ERROR)


# Global initializer instance
_initializer: Optional[CLIInitializer] = None


def get_cli_initializer() -> CLIInitializer:
    """Get or create the global CLI initializer"""
    global _initializer
    if _initializer is None:
        _initializer = CLIInitializer()
    return _initializer


def initialize_cli_components() -> CLIInitializer:
    """Initialize all CLI components and return the initializer"""
    initializer = get_cli_initializer()
    initializer.initialize_all_components()
    return initializer