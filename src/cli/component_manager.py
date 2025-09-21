#!/usr/bin/env python3
"""
M1K3 Component Manager
Centralized manager for lazy-loading and providing core CLI components.
"""

from typing import Optional, Any, Dict
import time

# Import all potential components here, but don't initialize them yet
# AI Engines
from engines.ai.ai_inference import LocalAIEngine
from engines.ai.simple_ai_engine import SimpleAIEngine
from rag.m1k3_rag_engine import M1K3RAGEngine

# Voice Components
from engines.voice.voice_engine import create_voice_engine

# STT Components
from engines.stt.stt_manager import STTManager

# Other CLI Components
from cli.cli_commands import CLICommandHandler
from cli.cli_ai_handler import CLIAIResponseProcessor
from cli.cli_initialization import initialize_cli_components # For component availability checks
from utils.performance.startup_optimizer import get_startup_optimizer
from utils.system_monitor import SystemMonitor
from sound_manager import SoundManager
from llm_greeting_engine import LLMGreetingEngine
from avatar.avatar_controller import AvatarController, start_avatar_server, stop_avatar_server
from model_cli import ModelCLI
from websocket_client import WebSocketClient
from model_transparency import transparency_engine
from streaming_response_filter import StreamingResponseFilter
from models.loaders.download_model import download_model
from engines.tts.streaming_tts_engine import create_streaming_tts_engine
from conversation_flow_manager import ConversationFlowManager


class ComponentManager:
    """
    Manages the lazy loading and lifecycle of M1K3 CLI components.
    """
    _instance: Optional["ComponentManager"] = None

    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super(ComponentManager, cls).__new__(cls)
            cls._instance._initialized = False # Use an internal flag for one-time init
        return cls._instance

    def __init__(self, config: Dict[str, Any] = None):
        if self._initialized:
            return
        self._initialized = True

        self.config = config if config is not None else {}
        self._components: Dict[str, Any] = {}
        self.initializer = initialize_cli_components() # Used for initial component availability checks

        # Store configuration for lazy-loaded components
        self._ai_config = {}
        self._voice_config = {}
        self._stt_config = {}
        self._avatar_config = {}
        self._rag_enabled = False

        # Initialize logger (can be lazy-loaded too, but often needed early)
        # from cli.cli_logging import get_cli_logger
        # self.logger = get_cli_logger()

    def set_cli_config(self, config: Dict[str, Any]):
        """
        Sets the configuration for the component manager.
        This should be called once at startup with all relevant CLI arguments.
        """
        self.config = config
        self._ai_config = {
            "voice_enabled": config.get("voice_enabled", True),
            "rag_enabled": config.get("rag_enabled", False),
            "stt_engine": config.get("stt_engine", "auto"),
            "stt_model": config.get("stt_model", "vosk-model-small-en-us-0.15"),
            "stt_language": config.get("stt_language", "en-US"),
            "voice_first": config.get("voice_first", False),
            "force_internal_mic": config.get("force_internal_mic", False),
            "tts_engine": config.get("tts_engine", "auto"),
            "vibevoice_model": config.get("vibevoice_model", "1.5B"),
            "vibevoice_quality": config.get("vibevoice_quality", "balanced"),
            "voice_profile": config.get("voice_profile", "natural"),
            "speakers": config.get("speakers", ["Alice"]),
            "multi_speaker": config.get("multi_speaker", False),
            "streaming_enabled": config.get("streaming_enabled", False),
            "conversation_mode": config.get("conversation_mode", False),
            "chunk_size": config.get("chunk_size", 20),
            "chunk_timeout": config.get("chunk_timeout", 0.5),
            "response_delay": config.get("response_delay", 0.2),
            "enable_interruptions": config.get("enable_interruptions", True),
        }
        self._avatar_config = {
            "auto_avatar": config.get("auto_avatar", False),
            "avatar_port": config.get("avatar_port", 8080),
            "open_browser": config.get("open_browser", True),
        }
        self._rag_enabled = config.get("rag_enabled", False)

    def _load_component(self, name: str, loader_func: callable) -> Any:
        """
        Generic lazy loader for components.
        """
        if name not in self._components:
            # print(f"[ComponentManager] Loading {name}...") # Debugging
            try:
                self._components[name] = loader_func()
            except Exception as e:
                # print(f"[ComponentManager] Failed to load {name}: {e}") # Debugging
                self._components[name] = None # Store None to avoid repeated failed attempts
        return self._components[name]

    # --- AI Engine Loaders ---
    def get_ai_engine(self) -> Optional[LocalAIEngine]:
        return self._load_component("ai_engine", self._load_ai_engine_instance)

    def _load_ai_engine_instance(self) -> Optional[LocalAIEngine]:
        # This will be the main AI engine, potentially lazy-loaded
        # based on config.get("ai_engine_type", "real")
        # For now, just load LocalAIEngine
        engine = LocalAIEngine(
            auto_load=True # Ensure it auto-loads its model
        )
        return engine

    def get_rag_engine(self) -> Optional[M1K3RAGEngine]:
        if not self._rag_enabled:
            return None
        return self._load_component("rag_engine", self._load_rag_engine_instance)

    def _load_rag_engine_instance(self) -> Optional[M1K3RAGEngine]:
        return M1K3RAGEngine()

    # --- Voice Component Loaders ---
    def get_voice_engine(self) -> Any:
        if not self.config.get("voice_enabled", True):
            return None
        return self._load_component("voice_engine", self._load_voice_engine_instance)

    def _load_voice_engine_instance(self) -> Any:
        # create_voice_engine handles UnifiedVoiceEngine/VoiceEngine selection
        engine = create_voice_engine()
        # Configure voice engine based on self._ai_config (which holds voice-related CLI args)
        if engine:
            if hasattr(engine, 'set_engine_preference'):
                engine.set_engine_preference(self._ai_config.get("tts_engine", "auto"))
            if hasattr(engine, 'set_profile'):
                engine.set_profile(self._ai_config.get("voice_profile", "natural"))
            if hasattr(engine, 'set_speakers') and self._ai_config.get("multi_speaker", False):
                engine.set_speakers(self._ai_config.get("speakers", ["Alice"]))
            # Load model after configuration
            engine.load_model()
        return engine

    # --- STT Component Loaders ---
    def get_stt_manager(self) -> Optional[STTManager]:
        if self.config.get("stt_engine", "auto") == "none":
            return None
        return self._load_component("stt_manager", self._load_stt_manager_instance)

    def _load_stt_manager_instance(self) -> Optional[STTManager]:
        # Fix: Remove 'preferred_engine' as it's not expected by STTManager.__init__
        # The STTManager should determine its preferred engine internally based on its own config
        manager = STTManager(
            model_name=self._ai_config.get("stt_model"),
            language=self._ai_config.get("stt_language"),
            force_internal_mic=self._ai_config.get("force_internal_mic"),
            # preferred_engine=self._ai_config.get("stt_engine") # This was the problematic arg
        )
        manager.initialize() # STTManager needs to be initialized after creation
        return manager

    # --- Other CLI Component Loaders ---
    def get_command_handler(self, cli_core_instance: Any) -> CLICommandHandler:
        return self._load_component("command_handler", lambda: self._load_command_handler_instance(cli_core_instance))

    def _load_command_handler_instance(self, cli_core_instance: Any) -> CLICommandHandler:
        return CLICommandHandler(cli_core_instance)

    def get_ai_processor(self, cli_core_instance: Any) -> CLIAIResponseProcessor:
        return self._load_component("ai_processor", lambda: self._load_ai_processor_instance(cli_core_instance))

    def _load_ai_processor_instance(self, cli_core_instance: Any) -> CLIAIResponseProcessor:
        # CLIAIResponseProcessor needs references to ai_engine, voice_engine, etc.
        # These will be fetched from component_manager by CLIAIResponseProcessor itself
        return CLIAIResponseProcessor(cli_core_instance)

    def get_system_monitor(self) -> SystemMonitor:
        return self._load_component("system_monitor", lambda: SystemMonitor())

    def get_sound_manager(self) -> SoundManager:
        return self._load_component("sound_manager", lambda: SoundManager())

    def get_greeting_engine(self) -> LLMGreetingEngine:
        return self._load_component("greeting_engine", lambda: LLMGreetingEngine())

    def get_avatar_controller(self) -> Optional[AvatarController]:
        if not self._avatar_config.get("auto_avatar", False):
            return None
        return self._load_component("avatar_controller", lambda: AvatarController())

    def get_model_cli(self) -> ModelCLI:
        return self._load_component("model_cli", lambda: ModelCLI())

    def get_websocket_client(self) -> WebSocketClient:
        return self._load_component("websocket_client", lambda: WebSocketClient())

    def get_transparency_engine(self) -> Any:
        return self._load_component("transparency_engine", lambda: transparency_engine)

    def get_streaming_response_filter(self) -> StreamingResponseFilter:
        return self._load_component("streaming_response_filter", lambda: StreamingResponseFilter())

    def get_download_model_utility(self) -> Any:
        return self._load_component("download_model_utility", lambda: download_model)

    def get_streaming_tts_engine(self) -> Any:
        if not self._ai_config.get("streaming_enabled", False) and not self._ai_config.get("conversation_mode", False):
            return None
        return self._load_component("streaming_tts_engine", lambda: create_streaming_tts_engine(
            voice_engine=self.get_voice_engine(), # Needs voice engine
            chunk_size=self._ai_config.get("chunk_size"),
            chunk_timeout=self._ai_config.get("chunk_timeout")
        ))

    def get_conversation_flow_manager(self) -> Any:
        if not self._ai_config.get("conversation_mode", False):
            return None
        return self._load_component("conversation_flow_manager", lambda: ConversationFlowManager(
            stt_manager=self.get_stt_manager(), # Needs STT manager
            streaming_tts=self.get_streaming_tts_engine(), # Needs streaming TTS
            ai_engine=self.get_ai_engine() # Needs AI engine
        ))

    # --- Cleanup ---
    def cleanup(self):
        """
        Cleans up all loaded components.
        """
        for name, component in self._components.items():
            if hasattr(component, 'cleanup'):
                try:
                    component.cleanup()
                except Exception as e:
                    # print(f"[ComponentManager] Error cleaning up {name}: {e}") # Debugging
                    pass

# Global instance for easy access (or pass around as needed)
# component_manager = ComponentManager() # Don't instantiate here for lazy loading
