#!/usr/bin/env python3
"""
Conversation Flow Manager - Natural turn-taking for M1K3
Manages real-time conversation flow between user speech and AI responses
"""

import time
import threading
from typing import Optional, Callable, Dict, Any, List
from dataclasses import dataclass
from enum import Enum
import queue

# Add src to path for imports
import sys
import os
sys.path.insert(0, 'src')


class ConversationState(Enum):
    """Conversation flow states"""
    WAITING = "waiting"           # Waiting for user input
    LISTENING = "listening"       # Actively listening to user
    PROCESSING = "processing"     # Processing user input / generating AI response
    SPEAKING = "speaking"         # AI is speaking
    INTERRUPTED = "interrupted"   # User interrupted AI
    ERROR = "error"              # Error state
    PAUSED = "paused"            # Conversation paused


class TurnType(Enum):
    """Types of conversation turns"""
    USER_SPEECH = "user_speech"
    AI_RESPONSE = "ai_response"
    SYSTEM_MESSAGE = "system_message"
    INTERRUPTION = "interruption"


@dataclass
class ConversationTurn:
    """Individual conversation turn"""
    turn_type: TurnType
    content: str
    timestamp: float
    confidence: Optional[float] = None
    duration: Optional[float] = None
    metadata: Optional[Dict[str, Any]] = None


class ConversationFlowManager:
    """
    Manages natural conversation flow with real-time turn-taking
    Handles interruptions, overlapping speech, and seamless transitions
    """
    
    def __init__(self, stt_manager=None, streaming_tts=None, ai_engine=None):
        """
        Initialize conversation flow manager
        
        Args:
            stt_manager: Speech-to-text manager for voice input
            streaming_tts: Streaming TTS engine for voice output
            ai_engine: AI engine for generating responses
        """
        self.stt_manager = stt_manager
        self.streaming_tts = streaming_tts
        self.ai_engine = ai_engine
        
        # Conversation state
        self.state = ConversationState.WAITING
        self.is_active = False
        self.current_turn: Optional[ConversationTurn] = None
        
        # Turn management
        self.conversation_history: List[ConversationTurn] = []
        self.turn_counter = 0
        
        # Threading and queues
        self.input_queue = queue.Queue()
        self.output_queue = queue.Queue()
        self.flow_thread: Optional[threading.Thread] = None
        self.stop_event = threading.Event()
        
        # Timing and interruption handling
        self.silence_timeout = 3.0  # Seconds of silence before switching turns
        self.interruption_threshold = 0.5  # Seconds to detect interruption
        self.response_delay = 0.2  # Brief pause before AI responds
        self.last_speech_time = 0.0
        
        # State tracking
        self.user_is_speaking = False
        self.ai_is_speaking = False
        self.speech_start_time = 0.0
        self.last_interruption_time = 0.0
        
        # Callbacks
        self.on_state_change: Optional[Callable[[ConversationState], None]] = None
        self.on_turn_start: Optional[Callable[[ConversationTurn], None]] = None
        self.on_turn_complete: Optional[Callable[[ConversationTurn], None]] = None
        self.on_interruption: Optional[Callable[[ConversationTurn], None]] = None
        self.on_conversation_start: Optional[Callable] = None
        self.on_conversation_end: Optional[Callable] = None
        
        # Configuration
        self.enable_interruptions = True
        self.enable_barge_in = True  # Allow user to interrupt AI
        self.auto_response_mode = True  # Automatically generate AI responses
        
        # Statistics
        self.stats = {
            'total_turns': 0,
            'user_turns': 0,
            'ai_turns': 0,
            'interruptions': 0,
            'average_response_time': 0.0,
            'conversation_duration': 0.0,
            'conversation_start': 0.0
        }
    
    def start_conversation(self) -> bool:
        """Start the conversation flow"""
        if self.is_active:
            return True
        
        try:
            print("💬 Starting conversation flow manager...")
            
            # Initialize components
            if not self._initialize_components():
                return False
            
            # Reset state
            self.state = ConversationState.WAITING
            self.is_active = True
            self.stop_event.clear()
            self.conversation_history.clear()
            self.turn_counter = 0
            
            # Clear queues
            while not self.input_queue.empty():
                try:
                    self.input_queue.get_nowait()
                except queue.Empty:
                    break
            
            while not self.output_queue.empty():
                try:
                    self.output_queue.get_nowait()
                except queue.Empty:
                    break
            
            # Start statistics
            self.stats['conversation_start'] = time.time()
            self.stats['total_turns'] = 0
            self.stats['user_turns'] = 0
            self.stats['ai_turns'] = 0
            self.stats['interruptions'] = 0
            
            # Start conversation flow thread
            self.flow_thread = threading.Thread(
                target=self._conversation_flow_worker,
                name="ConversationFlow",
                daemon=True
            )
            self.flow_thread.start()
            
            # Start continuous listening for user input
            if self.stt_manager:
                self.stt_manager.start_continuous_listening()
            
            print("✅ Conversation flow started")
            print("💡 Say something to begin the conversation...")
            
            if self.on_conversation_start:
                self.on_conversation_start()
            
            return True
            
        except Exception as e:
            print(f"❌ Failed to start conversation flow: {e}")
            return False
    
    def stop_conversation(self) -> bool:
        """Stop the conversation flow"""
        if not self.is_active:
            return True
        
        try:
            print("🛑 Stopping conversation flow...")
            
            # Signal stop
            self.stop_event.set()
            self.is_active = False
            
            # Stop continuous listening
            if self.stt_manager:
                self.stt_manager.stop_continuous_listening()
            
            # Stop streaming TTS
            if self.streaming_tts:
                self.streaming_tts.stop_streaming()
            
            # Wait for flow thread
            if self.flow_thread and self.flow_thread.is_alive():
                self.flow_thread.join(timeout=2.0)
            
            # Final statistics
            self.stats['conversation_duration'] = time.time() - self.stats['conversation_start']
            
            self.state = ConversationState.WAITING
            print("✅ Conversation flow stopped")
            
            # Print conversation summary
            self._print_conversation_summary()
            
            if self.on_conversation_end:
                self.on_conversation_end()
            
            return True
            
        except Exception as e:
            print(f"❌ Error stopping conversation flow: {e}")
            return False
    
    def _initialize_components(self) -> bool:
        """Initialize required components"""
        if not self.stt_manager:
            print("⚠️ No STT manager provided - voice input disabled")
        
        if not self.streaming_tts:
            print("⚠️ No streaming TTS provided - voice output disabled")
        
        if not self.ai_engine:
            print("⚠️ No AI engine provided - automatic responses disabled")
            self.auto_response_mode = False
        
        # Set up STT callback
        if self.stt_manager:
            self.stt_manager.on_speech_detected = self._handle_user_speech
        
        return True  # Allow operation even with missing components
    
    def _conversation_flow_worker(self):
        """Main conversation flow worker thread"""
        print("🔄 Conversation flow worker started")
        
        while not self.stop_event.is_set() and self.is_active:
            try:
                # Process input queue
                try:
                    event = self.input_queue.get(timeout=0.1)
                    self._process_conversation_event(event)
                except queue.Empty:
                    pass
                
                # Check for turn timeouts and state transitions
                self._check_turn_timeouts()
                
                # Handle automatic state transitions
                self._handle_state_transitions()
                
                # Small delay to prevent busy waiting
                time.sleep(0.05)
                
            except Exception as e:
                print(f"❌ Conversation flow worker error: {e}")
                self.state = ConversationState.ERROR
                time.sleep(0.1)
        
        print("🔄 Conversation flow worker stopped")
    
    def _handle_user_speech(self, stt_result):
        """Handle detected user speech"""
        if not self.is_active:
            return
        
        try:
            user_text = stt_result.text.strip()
            if not user_text:
                return
            
            print(f"👤 User said: '{user_text}' (confidence: {stt_result.confidence:.2f})")
            
            # Create conversation turn
            turn = ConversationTurn(
                turn_type=TurnType.USER_SPEECH,
                content=user_text,
                timestamp=time.time(),
                confidence=stt_result.confidence,
                duration=stt_result.duration,
                metadata={
                    'engine': stt_result.engine,
                    'language': stt_result.language
                }
            )
            
            # Check for interruption
            is_interruption = self._check_for_interruption(turn)
            if is_interruption:
                turn.turn_type = TurnType.INTERRUPTION
                self._handle_interruption(turn)
            
            # Add to input queue for processing
            self.input_queue.put(('user_speech', turn))
            
        except Exception as e:
            print(f"❌ Error handling user speech: {e}")
    
    def _process_conversation_event(self, event):
        """Process a conversation event from the input queue"""
        event_type, data = event
        
        if event_type == 'user_speech':
            self._process_user_turn(data)
        elif event_type == 'ai_response':
            self._process_ai_turn(data)
        elif event_type == 'interruption':
            self._process_interruption(data)
        elif event_type == 'state_change':
            self._change_state(data)
    
    def _process_user_turn(self, turn: ConversationTurn):
        """Process a user speech turn"""
        try:
            # Update state
            if self.state != ConversationState.INTERRUPTED:
                self._change_state(ConversationState.PROCESSING)
            
            # Record turn
            self.current_turn = turn
            self.conversation_history.append(turn)
            self.turn_counter += 1
            self.stats['total_turns'] += 1
            self.stats['user_turns'] += 1
            
            print(f"📝 Processing user turn #{self.turn_counter}")
            
            if self.on_turn_start:
                self.on_turn_start(turn)
            
            # Generate AI response if enabled
            if self.auto_response_mode and self.ai_engine:
                # Small delay for natural conversation flow
                time.sleep(self.response_delay)
                
                # Generate AI response
                self._generate_ai_response(turn.content)
            else:
                # Wait for manual response or return to waiting
                self._change_state(ConversationState.WAITING)
            
            if self.on_turn_complete:
                self.on_turn_complete(turn)
            
        except Exception as e:
            print(f"❌ Error processing user turn: {e}")
            self.state = ConversationState.ERROR
    
    def _generate_ai_response(self, user_input: str):
        """Generate and process AI response"""
        try:
            response_start = time.time()
            
            print("🤖 Generating AI response...")
            
            # Generate response using AI engine
            ai_response = self.ai_engine.generate_response(user_input)
            
            # Handle streaming vs non-streaming response
            if hasattr(ai_response, '__iter__') and not isinstance(ai_response, str):
                # Streaming response
                self._handle_streaming_ai_response(ai_response, response_start)
            else:
                # Non-streaming response
                response_text = ai_response if ai_response else "I'm sorry, I couldn't generate a response."
                self._handle_complete_ai_response(response_text, response_start)
            
        except Exception as e:
            print(f"❌ Error generating AI response: {e}")
            self.state = ConversationState.ERROR
    
    def _handle_streaming_ai_response(self, response_tokens, response_start: float):
        """Handle streaming AI response with real-time TTS"""
        try:
            self._change_state(ConversationState.SPEAKING)
            
            # Create AI turn
            turn = ConversationTurn(
                turn_type=TurnType.AI_RESPONSE,
                content="",  # Will be filled as tokens arrive
                timestamp=time.time()
            )
            
            # Start streaming TTS
            if self.streaming_tts:
                # Process tokens through streaming TTS
                response_chunks = []
                for chunk in self.streaming_tts.process_token_stream(response_tokens):
                    # Accumulate response text
                    turn.content += chunk.text + " "
                    response_chunks.append(chunk)
                    
                    # Check for interruptions during streaming
                    if self.state == ConversationState.INTERRUPTED:
                        print("🛑 AI response interrupted")
                        break
                
                turn.content = turn.content.strip()
                print(f"🤖 AI response: '{turn.content}'")
            else:
                # Fallback: collect all tokens
                response_text = "".join(response_tokens)
                turn.content = response_text
                print(f"🤖 AI response: '{response_text}'")
            
            # Record turn
            turn.duration = time.time() - response_start
            self.conversation_history.append(turn)
            self.turn_counter += 1
            self.stats['total_turns'] += 1
            self.stats['ai_turns'] += 1
            
            # Update average response time
            total_response_time = sum(
                t.duration for t in self.conversation_history 
                if t.turn_type == TurnType.AI_RESPONSE and t.duration
            )
            ai_responses = max(1, self.stats['ai_turns'])
            self.stats['average_response_time'] = total_response_time / ai_responses
            
            # Return to waiting state
            self._change_state(ConversationState.WAITING)
            
        except Exception as e:
            print(f"❌ Error handling streaming AI response: {e}")
            self.state = ConversationState.ERROR
    
    def _handle_complete_ai_response(self, response_text: str, response_start: float):
        """Handle complete (non-streaming) AI response"""
        try:
            self._change_state(ConversationState.SPEAKING)
            
            # Create AI turn
            turn = ConversationTurn(
                turn_type=TurnType.AI_RESPONSE,
                content=response_text,
                timestamp=time.time(),
                duration=time.time() - response_start
            )
            
            print(f"🤖 AI response: '{response_text}'")
            
            # Synthesize speech if TTS available
            if self.streaming_tts:
                self.streaming_tts.add_text_chunk(response_text)
            
            # Record turn
            self.conversation_history.append(turn)
            self.turn_counter += 1
            self.stats['total_turns'] += 1
            self.stats['ai_turns'] += 1
            
            # Return to waiting state
            self._change_state(ConversationState.WAITING)
            
        except Exception as e:
            print(f"❌ Error handling complete AI response: {e}")
            self.state = ConversationState.ERROR
    
    def _check_for_interruption(self, turn: ConversationTurn) -> bool:
        """Check if this turn is an interruption"""
        if not self.enable_interruptions:
            return False
        
        # Check if AI is currently speaking
        if self.state == ConversationState.SPEAKING:
            time_since_ai_start = time.time() - self.speech_start_time
            if time_since_ai_start > self.interruption_threshold:
                return True
        
        return False
    
    def _handle_interruption(self, turn: ConversationTurn):
        """Handle user interruption of AI speech"""
        try:
            print("🚫 User interruption detected!")
            
            # Stop current AI speech
            if self.streaming_tts:
                self.streaming_tts.pause_streaming()
            
            # Update state
            self._change_state(ConversationState.INTERRUPTED)
            
            # Record interruption statistics
            self.stats['interruptions'] += 1
            self.last_interruption_time = time.time()
            
            if self.on_interruption:
                self.on_interruption(turn)
            
        except Exception as e:
            print(f"❌ Error handling interruption: {e}")
    
    def _check_turn_timeouts(self):
        """Check for turn timeouts and state transitions"""
        current_time = time.time()
        
        # Check for silence timeout in listening state
        if self.state == ConversationState.LISTENING:
            if (current_time - self.last_speech_time) > self.silence_timeout:
                print("⏰ Silence timeout - switching to waiting")
                self._change_state(ConversationState.WAITING)
    
    def _handle_state_transitions(self):
        """Handle automatic state transitions"""
        # Add logic for automatic state transitions based on conditions
        pass
    
    def _change_state(self, new_state: ConversationState):
        """Change conversation state with proper notifications"""
        if self.state == new_state:
            return
        
        old_state = self.state
        self.state = new_state
        
        print(f"🔄 State change: {old_state.value} → {new_state.value}")
        
        # Update timing for speech states
        if new_state == ConversationState.SPEAKING:
            self.speech_start_time = time.time()
        elif new_state == ConversationState.LISTENING:
            self.last_speech_time = time.time()
        
        if self.on_state_change:
            self.on_state_change(new_state)
    
    def add_system_message(self, message: str):
        """Add a system message to the conversation"""
        turn = ConversationTurn(
            turn_type=TurnType.SYSTEM_MESSAGE,
            content=message,
            timestamp=time.time()
        )
        
        self.conversation_history.append(turn)
        self.turn_counter += 1
        
        print(f"🔧 System: {message}")
        
        if self.streaming_tts:
            self.streaming_tts.add_text_chunk(message)
    
    def pause_conversation(self):
        """Pause the conversation"""
        if self.is_active:
            self._change_state(ConversationState.PAUSED)
            if self.streaming_tts:
                self.streaming_tts.pause_streaming()
    
    def resume_conversation(self):
        """Resume the conversation"""
        if self.state == ConversationState.PAUSED:
            self._change_state(ConversationState.WAITING)
            if self.streaming_tts:
                self.streaming_tts.resume_streaming()
    
    def get_conversation_stats(self) -> Dict[str, Any]:
        """Get conversation statistics"""
        current_duration = time.time() - self.stats['conversation_start']
        
        return {
            'state': self.state.value,
            'is_active': self.is_active,
            'total_turns': self.stats['total_turns'],
            'user_turns': self.stats['user_turns'],
            'ai_turns': self.stats['ai_turns'],
            'interruptions': self.stats['interruptions'],
            'average_response_time': self.stats['average_response_time'],
            'conversation_duration': current_duration,
            'turns_per_minute': self.stats['total_turns'] / max(current_duration / 60, 0.1),
            'interruption_rate': self.stats['interruptions'] / max(self.stats['ai_turns'], 1)
        }
    
    def get_conversation_history(self) -> List[ConversationTurn]:
        """Get the conversation history"""
        return self.conversation_history.copy()
    
    def _print_conversation_summary(self):
        """Print conversation summary"""
        stats = self.get_conversation_stats()
        
        print("\n📋 Conversation Summary:")
        print("=" * 40)
        print(f"🕐 Duration: {stats['conversation_duration']:.1f}s")
        print(f"💬 Total turns: {stats['total_turns']}")
        print(f"👤 User turns: {stats['user_turns']}")
        print(f"🤖 AI turns: {stats['ai_turns']}")
        print(f"🚫 Interruptions: {stats['interruptions']}")
        print(f"⚡ Avg response time: {stats['average_response_time']:.2f}s")
        print(f"📊 Turns per minute: {stats['turns_per_minute']:.1f}")
        print(f"🔄 Interruption rate: {stats['interruption_rate']:.2f}")
    
    def cleanup(self):
        """Clean up conversation flow resources"""
        self.stop_conversation()
        
        # Clear history and queues
        self.conversation_history.clear()
        
        while not self.input_queue.empty():
            try:
                self.input_queue.get_nowait()
            except queue.Empty:
                break
        
        while not self.output_queue.empty():
            try:
                self.output_queue.get_nowait()
            except queue.Empty:
                break
        
        print("🧹 Conversation flow manager cleaned up")


# Example usage and testing
if __name__ == "__main__":
    print("🧪 Testing Conversation Flow Manager")
    print("=" * 50)
    
    # Create conversation flow manager (without actual engines for testing)
    flow_manager = ConversationFlowManager()
    
    # Set up callbacks
    def on_state_change(state):
        print(f"🔄 State changed to: {state.value}")
    
    def on_turn_start(turn):
        print(f"▶️ Turn started: {turn.turn_type.value} - '{turn.content[:30]}...'")
    
    def on_turn_complete(turn):
        print(f"✅ Turn completed: {turn.turn_type.value}")
    
    flow_manager.on_state_change = on_state_change
    flow_manager.on_turn_start = on_turn_start
    flow_manager.on_turn_complete = on_turn_complete
    
    # Test basic functionality
    print("🎬 Starting conversation flow test...")
    
    try:
        if flow_manager.start_conversation():
            # Simulate some conversation events
            time.sleep(1)
            
            # Add system message
            flow_manager.add_system_message("Welcome to the conversation test!")
            
            time.sleep(2)
            
            # Print stats
            stats = flow_manager.get_conversation_stats()
            print("\n📊 Current Stats:")
            for key, value in stats.items():
                print(f"   {key}: {value}")
            
            # Stop conversation
            flow_manager.stop_conversation()
        
    except KeyboardInterrupt:
        print("\n⏹️ Test interrupted")
        flow_manager.stop_conversation()
    finally:
        flow_manager.cleanup()
        print("🧪 Test complete")