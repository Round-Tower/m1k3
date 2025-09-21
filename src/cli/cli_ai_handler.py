#!/usr/bin/env python3
"""
CLI AI Response Handler Module
Handles AI response processing, streaming, voice synthesis, and avatar integration
"""

import time
import threading
from typing import Optional, Dict, Any, Callable
from enum import Enum

from .cli_logging import get_cli_logger, log_info, log_debug, log_warning, log_error


class ResponseProcessingState(Enum):
    """States for response processing"""
    IDLE = "idle"
    THINKING = "thinking"
    GENERATING = "generating"
    SPEAKING = "speaking"
    COMPLETE = "complete"
    ERROR = "error"


class CLIAIResponseProcessor:
    """Handles AI response processing with voice synthesis and avatar integration"""
    
    def __init__(self, cli_instance):
        self.cli = cli_instance
        self.logger = get_cli_logger()
        self.current_state = ResponseProcessingState.IDLE
        self.response_callbacks: Dict[str, Callable] = {}
        self.processing_thread: Optional[threading.Thread] = None
    
    def update_engines(self, voice_engine=None, rag_engine=None):
        """Update engine references when they become available"""
        if voice_engine is not None:
            self.cli.voice_engine = voice_engine
        if rag_engine is not None:
            self.cli.rag_engine = rag_engine
        
    def set_state(self, state: ResponseProcessingState):
        """Set the current processing state"""
        self.current_state = state
        log_debug(f"Response processing state changed to: {state.value}")
        
        # Update avatar state if available
        if hasattr(self.cli, 'set_avatar_state'):
            avatar_state_map = {
                ResponseProcessingState.IDLE: "IDLE",
                ResponseProcessingState.THINKING: "THINKING",
                ResponseProcessingState.GENERATING: "GENERATING",
                ResponseProcessingState.SPEAKING: "SPEAKING",
                ResponseProcessingState.COMPLETE: "IDLE",
                ResponseProcessingState.ERROR: "ERROR"
            }
            if state in avatar_state_map:
                self.cli.set_avatar_state(avatar_state_map[state])
    
    def register_callback(self, event: str, callback: Callable):
        """Register a callback for response processing events"""
        self.response_callbacks[event] = callback
    
    def _trigger_callback(self, event: str, *args, **kwargs):
        """Trigger a registered callback"""
        if event in self.response_callbacks:
            try:
                self.response_callbacks[event](*args, **kwargs)
            except Exception as e:
                log_error(f"Error in callback for {event}: {e}")
    
    def process_ai_query(self, user_input: str, use_rag: bool = False) -> Optional[str]:
        """Process AI query and return response"""
        log_info(f"Processing AI query: {user_input[:50]}...")
        
        if not hasattr(self.cli, 'ai_engine') or not self.cli.ai_engine:
            log_error("No AI engine available")
            return "⚠️ AI engine not available"
        
        try:
            self.set_state(ResponseProcessingState.THINKING)
            self._trigger_callback('thinking_started', user_input)
            
            # Send thinking phase update to avatar
            if hasattr(self.cli, 'send_thinking_phase_update'):
                self.cli.send_thinking_phase_update("Processing query...")
            
            # Use RAG engine if enabled and available
            ai_engine = self._get_ai_engine(use_rag)
            if not ai_engine:
                return "⚠️ Requested AI engine not available"
            
            # Generate response
            self.set_state(ResponseProcessingState.GENERATING)
            self._trigger_callback('generation_started', user_input)
            
            response = ai_engine.generate_response(user_input)
            
            # Handle both generator and string responses
            if hasattr(response, '__iter__') and not isinstance(response, str):
                # It's a generator, collect all chunks
                response_text = ''.join(chunk for chunk in response if chunk)
            else:
                response_text = response if response else ""
            
            if response_text:
                self.set_state(ResponseProcessingState.COMPLETE)
                self._trigger_callback('response_generated', response_text)
                log_info(f"AI response generated: {len(response_text)} characters")
                return response_text
            else:
                self.set_state(ResponseProcessingState.ERROR)
                log_warning("AI engine returned empty response")
                return "⚠️ AI engine returned no response"
                
        except Exception as e:
            self.set_state(ResponseProcessingState.ERROR)
            log_error(f"Error processing AI query: {e}")
            return f"❌ Error processing query: {str(e)}"
    
    def _get_ai_engine(self, use_rag: bool = False):
        """Get the appropriate AI engine based on configuration"""
        if use_rag and hasattr(self.cli, 'rag_engine') and self.cli.rag_engine:
            log_debug("Using RAG-enhanced AI engine")
            return self.cli.rag_engine
        
        if hasattr(self.cli, 'ai_engine') and self.cli.ai_engine:
            log_debug("Using standard AI engine")
            return self.cli.ai_engine
        
        log_error("No AI engine available")
        return None
    
    def process_response_with_voice(self, response: str, background: bool = True) -> bool:
        """Process response with voice synthesis"""
        if not response:
            return False
        
        try:
            # Parse response for content-specific voice effects
            processed_response = self._preprocess_for_voice(response)
            
            # Start voice synthesis
            if hasattr(self.cli, 'voice_enabled') and self.cli.voice_enabled:
                self.set_state(ResponseProcessingState.SPEAKING)
                self._trigger_callback('voice_started', processed_response)
                
                if background:
                    self.processing_thread = threading.Thread(
                        target=self._synthesize_voice_background,
                        args=(processed_response,),
                        daemon=True
                    )
                    self.processing_thread.start()
                else:
                    self._synthesize_voice(processed_response)
                
                return True
            else:
                log_debug("Voice synthesis disabled")
                return False
                
        except Exception as e:
            log_error(f"Error in voice processing: {e}")
            return False
    
    def _preprocess_for_voice(self, text: str) -> str:
        """Preprocess text for voice synthesis"""
        try:
            # Use voice preprocessor if available
            if hasattr(self.cli, 'initializer'):
                preprocess_func = self.cli.initializer.get_component('preprocess_for_voice_synthesis')
                if preprocess_func:
                    return preprocess_func(text)
            
            # Basic preprocessing
            return text.strip()
        except Exception as e:
            log_warning(f"Voice preprocessing failed: {e}")
            return text
    
    def _synthesize_voice_background(self, text: str):
        """Synthesize voice in background thread"""
        try:
            self._synthesize_voice(text)
        except Exception as e:
            log_error(f"Background voice synthesis failed: {e}")
        finally:
            self.set_state(ResponseProcessingState.COMPLETE)
            self._trigger_callback('voice_completed', text)
    
    def _synthesize_voice(self, text: str):
        """Synthesize voice for given text"""
        try:
            # Use intelligent TTS if available
            if hasattr(self.cli, '_safe_voice_synthesis'):
                self.cli._safe_voice_synthesis(text, background=False, use_intelligent_tts=True)
                log_debug("Voice synthesis completed with intelligent TTS")
            
            # Fallback to basic voice synthesis
            elif hasattr(self.cli, 'voice_engine') and self.cli.voice_engine:
                self.cli.voice_engine.synthesize_and_play(text, background=False)
                log_debug("Voice synthesis completed with basic engine")
                
            else:
                log_warning("No voice synthesis engine available")
                
        except Exception as e:
            log_error(f"Voice synthesis failed: {e}")
    
    def stream_response_with_avatar(self, response: str, chunk_size: int = 50) -> None:
        """Stream response to avatar with chunk updates"""
        if not response:
            return
        
        try:
            # Send chat start notification
            if hasattr(self.cli, 'send_chat_ai_start'):
                self.cli.send_chat_ai_start()
            
            # Stream response in chunks
            for i in range(0, len(response), chunk_size):
                chunk = response[i:i + chunk_size]
                
                # Send chunk to avatar
                if hasattr(self.cli, 'send_chat_ai_chunk'):
                    self.cli.send_chat_ai_chunk(chunk)
                
                # Small delay for streaming effect
                time.sleep(0.1)
            
            # Send completion notification
            if hasattr(self.cli, 'send_chat_ai_complete'):
                self.cli.send_chat_ai_complete(response)
                
            log_debug(f"Streamed response to avatar: {len(response)} characters")
            
        except Exception as e:
            log_error(f"Avatar streaming failed: {e}")
    
    def process_full_response_pipeline(self, user_input: str, use_rag: bool = False, 
                                     enable_voice: bool = True, enable_avatar: bool = True) -> Optional[str]:
        """Complete response processing pipeline"""
        log_info("Starting full response processing pipeline")
        
        # Generate AI response
        response = self.process_ai_query(user_input, use_rag)
        if not response:
            return None
        
        # Process with avatar if enabled
        if enable_avatar and hasattr(self.cli, 'avatar_enabled') and self.cli.avatar_enabled:
            self.stream_response_with_avatar(response)
        
        # Process with voice if enabled
        if enable_voice:
            self.process_response_with_voice(response, background=True)
        
        # Update metrics
        self._update_response_metrics(user_input, response)
        
        return response
    
    def _update_response_metrics(self, user_input: str, response: str):
        """Update response processing metrics"""
        try:
            # Calculate token usage
            input_tokens = len(user_input.split())
            response_tokens = len(response.split())
            total_tokens = input_tokens + response_tokens
            
            # Send metrics update to avatar
            if hasattr(self.cli, 'send_metrics_update'):
                metrics = {
                    'input_tokens': input_tokens,
                    'response_tokens': response_tokens,
                    'total_tokens': total_tokens,
                    'timestamp': time.time()
                }
                self.cli.send_metrics_update(metrics)
            
            # Update session statistics
            if hasattr(self.cli, 'stats_tracker') and self.cli.stats_tracker:
                self.cli.stats_tracker.record_interaction(user_input, response)
            
            log_debug(f"Response metrics updated: {total_tokens} tokens")
            
        except Exception as e:
            log_error(f"Failed to update response metrics: {e}")
    
    def classify_response_content(self, response: str) -> str:
        """Classify response content type for voice modulation"""
        try:
            # Use model output parser if available
            if hasattr(self.cli, 'initializer'):
                parser = self.cli.initializer.get_component('parse_model_output')
                if parser:
                    content_info = parser(response)
                    return content_info.get('primary_type', 'answer')
            
            # Basic classification
            if response.strip().endswith('?'):
                return 'question'
            elif any(thinking_word in response.lower() for thinking_word in ['think', 'consider', 'perhaps', 'maybe']):
                return 'thinking'
            elif response.startswith('**') or response.startswith('#'):
                return 'narration'
            else:
                return 'answer'
                
        except Exception as e:
            log_warning(f"Content classification failed: {e}")
            return 'answer'
    
    def stop_processing(self):
        """Stop any ongoing response processing"""
        try:
            if self.processing_thread and self.processing_thread.is_alive():
                log_info("Stopping response processing")
                # Note: Thread will complete naturally, we just set state
                self.set_state(ResponseProcessingState.IDLE)
            
            # Stop voice synthesis if possible
            if hasattr(self.cli, 'voice_engine') and self.cli.voice_engine:
                if hasattr(self.cli.voice_engine, 'stop'):
                    self.cli.voice_engine.stop()
                    
        except Exception as e:
            log_error(f"Error stopping processing: {e}")
    
    def is_processing(self) -> bool:
        """Check if currently processing a response"""
        return self.current_state not in [ResponseProcessingState.IDLE, ResponseProcessingState.COMPLETE, ResponseProcessingState.ERROR]
    
    def get_current_state(self) -> ResponseProcessingState:
        """Get the current processing state"""
        return self.current_state