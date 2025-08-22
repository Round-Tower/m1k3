#!/usr/bin/env python3
"""
Enhanced Adaptive Model Configuration for M1K3
Integrates context-aware intent classification with WebSocket real-time data
"""

import json
import logging
from typing import Dict, List, Any, Optional
from dataclasses import dataclass
import asyncio
import threading

# Import our new systems
try:
    from context_aware_classification import ContextAwareClassificationEngine
    CONTEXT_CLASSIFICATION_AVAILABLE = True
except ImportError:
    CONTEXT_CLASSIFICATION_AVAILABLE = False
    
try:
    from websocket_context_server import WebSocketContextServer, ContextData, ContextDataManager
    WEBSOCKET_CONTEXT_AVAILABLE = True
except ImportError:
    WEBSOCKET_CONTEXT_AVAILABLE = False

# Fallback to old system if needed
try:
    from adaptive_model_config import AdaptiveModelConfig as LegacyAdaptiveModelConfig
    LEGACY_FALLBACK_AVAILABLE = True
except ImportError:
    LEGACY_FALLBACK_AVAILABLE = False

@dataclass
class EnhancedModelConfig:
    """Enhanced model configuration with intent and context awareness"""
    # Core parameters
    config: Dict[str, Any]
    
    # Enhanced metadata 
    intent: str
    confidence: float
    response_strategy: str
    context_factors: Dict[str, Any]
    reasoning: str
    websocket_data: Optional[Dict[str, Any]] = None
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert to format expected by AI inference system"""
        result = self.config.copy()
        
        # Enhanced metadata section
        result['_metadata'] = {
            'intent': self.intent,
            'confidence': self.confidence,
            'response_strategy': self.response_strategy,
            'context_factors': self.context_factors,
            'reasoning': self.reasoning,
            'websocket_data': self.websocket_data,
            'system_version': 'enhanced_v2.0',
            'classification_engine': 'context_aware'
        }
        
        return result

class EnhancedAdaptiveModelConfig:
    """Enhanced adaptive model configuration with real-time context integration"""
    
    def __init__(self, websocket_port: int = 8082, enable_websocket: bool = True):
        self.logger = logging.getLogger(__name__)
        
        # Initialize classification engine
        if CONTEXT_CLASSIFICATION_AVAILABLE:
            self.classification_engine = ContextAwareClassificationEngine()
            self.logger.info("✅ Context-aware classification engine loaded")
        else:
            self.classification_engine = None
            self.logger.warning("⚠️  Context-aware classification not available, using fallback")
            
        # WebSocket integration
        self.websocket_enabled = enable_websocket and WEBSOCKET_CONTEXT_AVAILABLE
        self.websocket_server = None
        self.websocket_port = websocket_port
        
        if self.websocket_enabled:
            self.context_manager = ContextDataManager()
            self.logger.info("✅ WebSocket context integration enabled")
            
        # Legacy fallback
        if LEGACY_FALLBACK_AVAILABLE and not CONTEXT_CLASSIFICATION_AVAILABLE:
            self.legacy_config = LegacyAdaptiveModelConfig()
            self.logger.info("✅ Legacy fallback configuration loaded")
        else:
            self.legacy_config = None
            
    def get_optimal_config(self, query: str, model_name: str = "unknown", context: List[Dict] = None) -> Dict[str, Any]:
        """Get optimal configuration using enhanced context-aware system"""
        
        # Try enhanced system first
        if self.classification_engine:
            try:
                # Update classification engine with latest WebSocket context
                if self.websocket_enabled and hasattr(self.classification_engine, 'update_websocket_context'):
                    websocket_context = self._get_latest_websocket_context()
                    if websocket_context:
                        self.classification_engine.update_websocket_context(websocket_context)
                        
                # Get enhanced configuration
                config = self.classification_engine.get_optimal_config(query, model_name, context or [])
                
                # Detailed debugging of the returned config
                self.logger.debug(f"🔍 Config type: {type(config)}")
                self.logger.debug(f"🔍 Config repr: {repr(config)[:200]}...")
                
                # Ensure config is a dictionary - convert if needed
                if not isinstance(config, dict):
                    self.logger.error(f"❌ Enhanced classification returned non-dict: {type(config)}")
                    self.logger.error(f"❌ Actual content: {repr(config)}")
                    # Convert to fallback config instead of raising exception
                    config = self._get_basic_fallback_config(query, model_name)
                    self.logger.warning("🔄 Converted to basic fallback configuration")
                
                # Add WebSocket data to metadata if available
                if self.websocket_enabled and isinstance(config, dict):
                    websocket_summary = self._get_context_summary()
                    if config.get('_metadata'):
                        config['_metadata']['websocket_summary'] = websocket_summary
                        
                # Log enhanced config result safely
                try:
                    if isinstance(config, dict) and '_metadata' in config:
                        intent = config['_metadata'].get('intent', 'unknown')
                        strategy = config['_metadata'].get('response_strategy', 'unknown')
                        self.logger.info(f"🎯 Enhanced config: {intent} -> {strategy}")
                    else:
                        self.logger.warning(f"⚠️  Enhanced config result type: {type(config)}")
                except Exception as log_error:
                    self.logger.error(f"❌ Logging error: {log_error}")
                return config
                
            except Exception as e:
                self.logger.error(f"❌ Enhanced classification failed: {e}")
                # Fall through to legacy system
                
        # Fallback to legacy system
        if self.legacy_config:
            try:
                legacy_result = self.legacy_config.get_optimal_config(query, model_name, context or [])
                
                # Add fallback indicator
                if '_metadata' not in legacy_result:
                    legacy_result['_metadata'] = {}
                legacy_result['_metadata']['system_version'] = 'legacy_fallback'
                legacy_result['_metadata']['classification_engine'] = 'task_type_based'
                
                self.logger.info("🔄 Using legacy configuration fallback")
                return legacy_result
                
            except Exception as e:
                self.logger.error(f"❌ Legacy fallback failed: {e}")
                
        # Last resort: basic configuration
        return self._get_basic_fallback_config(query, model_name)
        
    def _get_latest_websocket_context(self) -> Optional[Dict[str, Any]]:
        """Get latest context data from WebSocket server"""
        if not self.websocket_enabled:
            return None
            
        try:
            # Get recent context from context manager
            recent_context = self.context_manager.get_recent_context(seconds=30)
            
            if not recent_context:
                return None
                
            # Convert to dictionary format
            context_dict = {}
            for ctx in recent_context:
                context_dict[ctx.data_type] = ctx.value
                
            return context_dict
            
        except Exception as e:
            self.logger.error(f"❌ Failed to get WebSocket context: {e}")
            return None
            
    def _get_context_summary(self) -> Optional[Dict[str, Any]]:
        """Get WebSocket context summary"""
        if not self.websocket_enabled:
            return None
            
        try:
            return self.context_manager.get_context_summary(seconds=60)
        except Exception as e:
            self.logger.error(f"❌ Failed to get context summary: {e}")
            return None
            
    def _get_basic_fallback_config(self, query: str, model_name: str) -> Dict[str, Any]:
        """Basic fallback configuration when all systems fail"""
        
        # Simple heuristics
        is_math = any(op in query for op in ['+', '-', '*', '/', '=', 'calculate', 'what is'])
        is_greeting = any(word in query.lower() for word in ['hello', 'hi', 'hey', 'good morning'])
        
        if is_math:
            config = {
                'max_new_tokens': 100,
                'do_sample': False,
                'repetition_penalty': 1.1,
                'no_repeat_ngram_size': 2,
                'num_beams': 1
            }
            intent = 'mathematical_fallback'
        elif is_greeting:
            config = {
                'max_new_tokens': 50,
                'do_sample': False,
                'repetition_penalty': 1.2,
                'no_repeat_ngram_size': 2,
                'num_beams': 1
            }
            intent = 'greeting_fallback'
        else:
            config = {
                'max_new_tokens': 200,
                'do_sample': True,
                'temperature': 0.7,
                'top_p': 0.9,
                'top_k': 40,
                'repetition_penalty': 1.05,
                'no_repeat_ngram_size': 2,
                'num_beams': 1
            }
            intent = 'general_fallback'
            
        config['_metadata'] = {
            'intent': intent,
            'confidence': 0.3,
            'response_strategy': 'basic_fallback',
            'context_factors': {},
            'reasoning': 'Basic heuristic fallback configuration',
            'system_version': 'basic_fallback',
            'classification_engine': 'heuristic'
        }
        
        self.logger.warning("⚠️  Using basic fallback configuration")
        return config
    
    def add_context_data(self, context: ContextData) -> None:
        """Add real-time context data"""
        if self.websocket_enabled and hasattr(self, 'context_manager'):
            self.context_manager.add_context_data(context)
            self.logger.debug(f"Added context data: {context.data_type} = {context.value}")
            
    def start_websocket_server(self) -> None:
        """Start WebSocket server for real-time context data"""
        if not self.websocket_enabled:
            self.logger.warning("WebSocket context is disabled")
            return
            
        try:
            # This would typically be run in a background thread or async context
            self.websocket_server = WebSocketContextServer(port=self.websocket_port)
            self.websocket_server.context_manager = self.context_manager
            
            self.logger.info(f"🌐 WebSocket context server configured on port {self.websocket_port}")
            self.logger.info("💡 Call start_server() method to begin listening")
            
        except Exception as e:
            self.logger.error(f"❌ Failed to start WebSocket server: {e}")
            self.websocket_enabled = False
            
    def get_system_status(self) -> Dict[str, Any]:
        """Get status of all system components"""
        status = {
            'context_aware_classification': CONTEXT_CLASSIFICATION_AVAILABLE,
            'websocket_integration': self.websocket_enabled,
            'legacy_fallback': LEGACY_FALLBACK_AVAILABLE,
            'websocket_port': self.websocket_port if self.websocket_enabled else None,
            'context_buffer_size': 0,
            'active_subscribers': 0
        }
        
        if self.websocket_enabled and hasattr(self, 'context_manager'):
            status['context_buffer_size'] = len(self.context_manager.context_buffer)
            status['active_subscribers'] = len(getattr(self.context_manager, 'subscribers', []))
            
        return status

# Convenience functions for backward compatibility
def get_optimal_config(query: str, model_name: str = "unknown", context: List[Dict] = None) -> Dict[str, Any]:
    """Backward compatible function for getting optimal configuration"""
    config = EnhancedAdaptiveModelConfig(enable_websocket=False)  # Disable WebSocket by default for compatibility
    return config.get_optimal_config(query, model_name, context)

# Testing functionality
def test_enhanced_adaptive_config():
    """Test the enhanced adaptive model configuration"""
    print("🧠 Testing Enhanced Adaptive Model Configuration")
    print("=" * 60)
    
    # Test basic configuration
    config = EnhancedAdaptiveModelConfig(enable_websocket=False)
    
    test_queries = [
        "What is 42 * 37?",
        "How do I debug memory leaks?",
        "Write a poem about the ocean",
        "Hello there!",
        "Explain machine learning basics"
    ]
    
    for query in test_queries:
        print(f"\n📝 Query: \"{query}\"")
        
        result = config.get_optimal_config(query, "qwen/qwen3-0.6b", [])
        metadata = result.get('_metadata', {})
        
        print(f"🎯 Intent: {metadata.get('intent', 'unknown')}")
        print(f"📊 Confidence: {metadata.get('confidence', 0):.3f}")
        print(f"🎭 Strategy: {metadata.get('response_strategy', 'unknown')}")
        print(f"🔧 System: {metadata.get('classification_engine', 'unknown')}")
        print(f"💭 Reasoning: {metadata.get('reasoning', 'No reasoning')}")
        
    # Test system status
    print(f"\n" + "="*60)
    print("📊 System Status:")
    status = config.get_system_status()
    for key, value in status.items():
        print(f"   {key}: {value}")

if __name__ == "__main__":
    test_enhanced_adaptive_config()