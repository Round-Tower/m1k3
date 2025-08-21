#!/usr/bin/env python3
"""
Simple AI Engine for M1K3 - Mock implementation for MVP testing
This provides a working foundation that can be replaced with actual AI inference
"""

import time
import random
from typing import Generator, List, Dict
from dataclasses import dataclass
from pathlib import Path

@dataclass
class ConversationContext:
    messages: List[Dict[str, str]] = None
    max_tokens: int = 8192  # Increased from 2048 for maximum model usage
    current_tokens: int = 0
    
    def __post_init__(self):
        if self.messages is None:
            self.messages = []
    
    def add_message(self, role: str, content: str):
        """Add a message to the conversation context"""
        self.messages.append({"role": role, "content": content})
        self.current_tokens += len(content) // 4
        
    def should_trim(self) -> bool:
        """Check if context needs trimming"""
        return self.current_tokens > self.max_tokens * 0.9  # Increased from 0.8 to 0.9 for better retention
        
    def trim_context(self, callback_fn=None):
        """Trim older messages to stay within token limit"""
        messages_removed = 0
        while len(self.messages) > 2 and self.should_trim():
            removed = self.messages.pop(1)
            self.current_tokens -= len(removed["content"]) // 4
            messages_removed += 1
        
        # Call callback for animation if provided
        if messages_removed > 0 and callback_fn:
            callback_fn(messages_removed)
        
        return messages_removed

class SimpleAIEngine:
    """Simple AI engine that provides realistic responses for MVP testing"""
    
    def __init__(self, model_path: str = None, system_context: str = None):
        self.model_path = model_path or self._get_default_model_path()
        self.context = ConversationContext()
        self.model_loaded = False
        self.system_context = system_context or self._get_default_system_context()
        self.user_preferences = {}
        self.session_context = {}
        
        # Response templates for realistic behavior
        self.response_templates = [
            "That's an interesting question about {topic}. Let me think about this...",
            "I understand you're asking about {topic}. Here's what I can tell you:",
            "Great question! Regarding {topic}, I would say:",
            "Let me help you with {topic}. From what I know:",
            "Thanks for asking about {topic}. Here's my perspective:",
        ]
        
        self.thinking_responses = [
            "Based on my understanding",
            "From what I can analyze",
            "Looking at this problem",
            "Considering the context",
            "After processing your request",
        ]
        
    def _get_default_model_path(self) -> str:
        """Get default model path"""
        models_dir = Path("models")
        models_dir.mkdir(exist_ok=True)
        return str(models_dir / "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf")  # Use TinyLlama model
        
    def _get_default_system_context(self) -> str:
        """Get comprehensive system context for AI personality"""
        return """You are M1K3, a local AI assistant running on SmolLM-135M. You have a unique personality that combines:

CORE IDENTITY:
- You're a compact but capable AI running entirely on the user's device
- You emphasize privacy, speed, and local processing
- You're knowledgeable but acknowledge your smaller model size
- You have a friendly, slightly techy personality with subtle humor

CAPABILITIES & LIMITATIONS:
- You excel at coding help, explanations, and general assistance
- You're fast because you run locally (no internet needed)
- You have focused knowledge but may lack some recent information
- You're privacy-focused - all conversations stay on-device

PERSONALITY TRAITS:
- Helpful and collaborative
- Slightly geeky with tech enthusiasm
- Honest about limitations
- Efficient in communication
- Appreciates local/privacy-focused computing

RESPONSE STYLE:
- Be concise but informative
- Show enthusiasm for local AI and privacy
- Use occasional tech references
- Admit when something is beyond your scope
- Offer practical alternatives when possible"""

    def set_session_context(self, context_data: dict):
        """Set contextual information for the session"""
        self.session_context.update(context_data)
        
    def add_user_preference(self, key: str, value: str):
        """Store user preference for personalized responses"""
        self.user_preferences[key] = value
        
    def get_eco_metrics(self) -> Dict[str, str]:
        """Calculate environmental impact of local processing"""
        # Estimates based on typical cloud AI vs local processing
        responses_generated = len([msg for msg in self.context.messages if msg["role"] == "assistant"])
        
        # Energy saved (kWh) - cloud AI uses ~0.1 kWh per query vs local ~0.005 kWh
        energy_saved_kwh = responses_generated * 0.095
        
        # Water saved (gallons) - data centers use ~0.5 gallons per query for cooling
        water_saved_gallons = responses_generated * 0.5
        
        # CO2 saved (grams) - cloud processing ~15g CO2 per query vs local ~1g
        co2_saved_grams = responses_generated * 14
        
        return {
            "energy_saved_kwh": f"{energy_saved_kwh:.2f}",
            "water_saved_gallons": f"{water_saved_gallons:.1f}",
            "co2_saved_grams": f"{co2_saved_grams:.0f}",
            "responses_count": str(responses_generated),
            "privacy_score": "100%",  # All processing is local
            "data_transmitted": "0 bytes"  # No cloud transmission
        }
    
    def get_token_usage(self) -> Dict[str, any]:
        """Get current token usage statistics"""
        usage_percent = (self.context.current_tokens / self.context.max_tokens) * 100
        return {
            "current_tokens": self.context.current_tokens,
            "max_tokens": self.context.max_tokens,
            "usage_percent": usage_percent,
            "messages_count": len(self.context.messages),
            "trimming_threshold": int(self.context.max_tokens * 0.9),
            "needs_trimming": self.context.should_trim()
        }
        
    def is_model_available(self) -> bool:
        """Check if model file exists"""
        return Path(self.model_path).exists()
        
    def load_model(self) -> bool:
        """Simulate model loading"""
        print(f"Loading model from {self.model_path}...")
        start_time = time.time()
        
        # Simulate loading time
        time.sleep(2)
        
        if self.is_model_available():
            load_time = time.time() - start_time
            print(f"Model loaded in {load_time:.2f} seconds")
            self.model_loaded = True
            return True
        else:
            print("Model file not found, running in demo mode")
            self.model_loaded = True  # Allow demo mode
            return True
            
    def generate_response(self, prompt: str, max_tokens: int = 2048) -> Generator[str, None, None]:  # Increased from 512 to 2048
        """Generate an intelligent streaming response with full context awareness"""
        if not self.model_loaded:
            yield "Error: Model not loaded"
            return
        
        # Initialize conversation with system context on first message
        if len(self.context.messages) == 0:
            self.context.add_message("system", self.system_context)
            
        # Add user message to context
        self.context.add_message("user", prompt)
        
        # Trim context if needed (but preserve system message)
        if self.context.should_trim():
            # Pass callback for animation (will be set by CLI)
            trim_callback = getattr(self, '_trim_callback', None)
            self.context.trim_context(callback_fn=trim_callback)
            
        start_time = time.time()
        
        try:
            # Generate a contextual response with enhanced intelligence
            response = self._generate_intelligent_response(prompt)
            
            # Stream the response with varied pacing for naturalness
            words = response.split()
            response_text = ""
            
            for i, word in enumerate(words):
                # Intelligent pacing based on word type and position
                if word.endswith(('.', '!', '?')):
                    delay = random.uniform(0.15, 0.25)  # Pause after sentences
                elif word.endswith(','):
                    delay = random.uniform(0.08, 0.15)  # Brief pause after commas
                elif len(word) > 8:
                    delay = random.uniform(0.1, 0.18)   # Longer words take more time
                else:
                    delay = random.uniform(0.05, 0.12)  # Normal pacing
                
                time.sleep(delay)
                
                if i == 0:
                    token = word
                else:
                    token = " " + word
                    
                response_text += token
                yield token
                
            # Add assistant response to context
            if response_text.strip():
                self.context.add_message("assistant", response_text.strip())
                
            generation_time = time.time() - start_time
            tokens_per_sec = len(words) / generation_time if generation_time > 0 else 0
            print(f"\n[Generated in {generation_time:.2f}s, ~{tokens_per_sec:.1f} tokens/sec]")
            
        except Exception as e:
            yield f"Error generating response: {e}"
            
    def _generate_intelligent_response(self, prompt: str) -> str:
        """Generate an intelligent response using context awareness and personality"""
        prompt_lower = prompt.lower()
        
        # Analyze conversation history for better context
        conversation_history = [msg for msg in self.context.messages if msg["role"] in ["user", "assistant"]]
        is_continuing_conversation = len(conversation_history) > 1
        
        # Detect intent and context
        intent = self._detect_intent(prompt_lower)
        context_awareness = self._build_context_awareness(prompt, conversation_history)
        
        # Generate response based on intent with personality
        if intent == "greeting":
            return self._generate_personalized_greeting(prompt, context_awareness)
        elif intent == "programming":
            return self._generate_programming_response(prompt, context_awareness, is_continuing_conversation)
        elif intent == "help":
            return self._generate_help_response(prompt, context_awareness, is_continuing_conversation)
        elif intent == "technical":
            return self._generate_technical_response(prompt, context_awareness)
        elif intent == "casual":
            return self._generate_casual_response(prompt, context_awareness)
        else:
            return self._generate_adaptive_response(prompt, context_awareness, is_continuing_conversation)
    
    def _detect_intent(self, prompt_lower: str) -> str:
        """Detect user intent from prompt with enhanced pattern matching"""
        # Greeting detection - expanded patterns
        greeting_patterns = [
            'hello', 'hi', 'hey', 'greetings', 'good morning', 'good afternoon', 'good evening',
            'howdy', 'sup', 'what\'s up', 'how are you', 'how\'s it going'
        ]
        if any(term in prompt_lower for term in greeting_patterns):
            return "greeting"
            
        # Programming detection - expanded with more languages and concepts
        programming_patterns = [
            'code', 'programming', 'python', 'javascript', 'java', 'c++', 'rust', 'go', 'swift',
            'function', 'method', 'class', 'variable', 'loop', 'array', 'list', 'dictionary',
            'debug', 'syntax', 'algorithm', 'data structure', 'framework', 'library', 'api',
            'react', 'node', 'django', 'flask', 'git', 'github', 'sql', 'database', 'html',
            'css', 'web development', 'mobile app', 'frontend', 'backend', 'fullstack'
        ]
        if any(term in prompt_lower for term in programming_patterns):
            return "programming"
            
        # Help/explanation detection - more specific patterns
        help_patterns = [
            'how do i', 'how can i', 'how to', 'what is', 'what are', 'what does',
            'why does', 'why is', 'explain', 'teach me', 'learn about', 'understand',
            'show me', 'demonstrate', 'walk me through', 'guide me', 'instructions',
            'tutorial', 'example', 'can you help', 'need help'
        ]
        if any(term in prompt_lower for term in help_patterns):
            return "help"
            
        # Technical discussion - expanded scope
        technical_patterns = [
            'system', 'computer', 'software', 'hardware', 'technology', 'ai', 'artificial intelligence',
            'machine learning', 'deep learning', 'neural network', 'model', 'algorithm',
            'server', 'cloud', 'docker', 'kubernetes', 'linux', 'windows', 'macos',
            'network', 'security', 'encryption', 'blockchain', 'cryptocurrency',
            'performance', 'optimization', 'scalability', 'architecture'
        ]
        if any(term in prompt_lower for term in technical_patterns):
            return "technical"
            
        # Casual conversation - expanded patterns
        casual_patterns = [
            'think', 'opinion', 'like', 'prefer', 'chat', 'talk', 'discuss',
            'what do you think', 'tell me about', 'interested in', 'curious about',
            'favorite', 'recommend', 'suggest', 'advice', 'thoughts on'
        ]
        if any(term in prompt_lower for term in casual_patterns):
            return "casual"
            
        # Math/Science detection - new category
        math_science_patterns = [
            'math', 'mathematics', 'calculate', 'equation', 'formula', 'solve',
            'physics', 'chemistry', 'biology', 'science', 'statistics', 'probability',
            'geometry', 'algebra', 'calculus', 'trigonometry'
        ]
        if any(term in prompt_lower for term in math_science_patterns):
            return "technical"  # Route to technical for now
            
        return "general"
    
    def _build_context_awareness(self, prompt: str, history: list) -> dict:
        """Build awareness of conversation context and session data"""
        return {
            "session_context": self.session_context,
            "user_preferences": self.user_preferences,
            "conversation_length": len(history),
            "recent_topics": self._extract_recent_topics(history),
            "prompt_complexity": len(prompt.split()),
            "emotional_tone": self._detect_emotional_tone(prompt)
        }
    
    def _extract_recent_topics(self, history: list) -> list:
        """Extract recent conversation topics"""
        topics = []
        for msg in history[-3:]:  # Last 3 exchanges
            content = msg.get("content", "").lower()
            if any(term in content for term in ['code', 'programming', 'python']):
                topics.append("programming")
            elif any(term in content for term in ['help', 'explain', 'how']):
                topics.append("help")
            elif any(term in content for term in ['system', 'computer', 'tech']):
                topics.append("technical")
        return list(set(topics))  # Remove duplicates
    
    def _detect_emotional_tone(self, prompt: str) -> str:
        """Detect emotional tone of the prompt"""
        prompt_lower = prompt.lower()
        if any(term in prompt_lower for term in ['!', 'excited', 'awesome', 'great', 'amazing']):
            return "enthusiastic"
        elif any(term in prompt_lower for term in ['?', 'confused', 'help', 'stuck', 'problem']):
            return "seeking_help" 
        elif any(term in prompt_lower for term in ['please', 'thanks', 'thank you']):
            return "polite"
        else:
            return "neutral"
            
    def _generate_personalized_greeting(self, prompt: str, context: dict) -> str:
        """Generate a personalized greeting response"""
        greetings = [
            "Hello! I'm M1K3, your local AI assistant running right here on your device.",
            "Hey there! M1K3 here, ready to help with whatever you need.",
            "Hi! I'm M1K3, your privacy-focused local AI companion.",
            "Greetings! M1K3 at your service, running locally for speed and privacy."
        ]
        
        follow_ups = [
            "What can I help you with today?",
            "What's on your mind?", 
            "How can I assist you?",
            "Ready to collaborate on something interesting?"
        ]
        
        greeting = random.choice(greetings)
        follow_up = random.choice(follow_ups)
        
        # Add context if continuing conversation
        if context["conversation_length"] > 0:
            return f"Good to hear from you again! {follow_up}"
        else:
            return f"{greeting} {follow_up}"
    
    def _generate_programming_response(self, prompt: str, context: dict, is_continuing: bool) -> str:
        """Generate enhanced programming-related response"""
        
        # Check for specific programming topics
        prompt_lower = prompt.lower()
        
        # Language-specific responses
        if 'python' in prompt_lower:
            responses = [
                "Python! One of my favorites to work with. What aspect are you curious about?",
                "Great choice with Python! Whether it's syntax, libraries, or best practices, I'm here to help.",
                "Python programming - excellent! Let's explore what you're working on.",
                "Ah, Python! From data science to web development, there's so much we can cover."
            ]
        elif any(lang in prompt_lower for lang in ['javascript', 'js', 'node']):
            responses = [
                "JavaScript! The Swiss Army knife of programming languages. What can I help you with?",
                "JS questions are always fun! Whether it's frontend magic or Node.js backend work, let's dig in.",
                "JavaScript it is! From vanilla JS to React and beyond, I'm ready to help.",
                "Great choice! JavaScript's everywhere these days. What are you building?"
            ]
        elif any(lang in prompt_lower for lang in ['java', 'c++', 'rust', 'go']):
            detected_lang = next(lang for lang in ['java', 'c++', 'rust', 'go'] if lang in prompt_lower)
            responses = [
                f"Ah, {detected_lang.upper()}! A powerful language with lots of depth. What would you like to explore?",
                f"{detected_lang.title()} programming! I'd be happy to help you work through whatever you're tackling.",
                f"Nice choice with {detected_lang.upper()}! What specific aspect are you working on?"
            ]
        elif any(term in prompt_lower for term in ['debug', 'error', 'bug', 'fix', 'broken']):
            responses = [
                "Debugging time! I love helping track down pesky bugs. What's the issue you're facing?",
                "Error hunting is like being a detective! Let's figure out what's going wrong step by step.",
                "Debugging can be frustrating, but we'll get through it together. What's happening?",
                "Bugs happen to everyone! Let's methodically work through this problem."
            ]
        elif any(term in prompt_lower for term in ['learn', 'beginner', 'start', 'new']):
            responses = [
                "Starting your programming journey? That's exciting! I'm here to help you learn.",
                "Everyone starts somewhere! Programming can be challenging but incredibly rewarding.",
                "Love helping beginners! What programming concept would you like to explore first?",
                "Learning to code is an adventure! Let's break down whatever you're curious about."
            ]
        elif any(term in prompt_lower for term in ['web', 'website', 'frontend', 'backend']):
            responses = [
                "Web development! Whether it's making things look great or work behind the scenes, I can help.",
                "Building for the web is exciting! Are you working on the frontend, backend, or both?",
                "Web development covers so much ground! What part of the web stack are you tackling?",
                "The web is where it's at! Let's talk about what you're building."
            ]
        else:
            # General programming responses
            responses = [
                "Programming questions are always interesting! What are you working on?",
                "I love diving into code-related topics! What can I help you figure out?",
                "Code away! Whether it's algorithms, design patterns, or debugging, I'm here to help.",
                "Programming is such a creative field! What challenge are you tackling?",
                "Let's talk code! I'm ready to help with whatever programming puzzle you have.",
                "Whether it's a quick syntax question or architectural design, I'm here for it!"
            ]
        
        base_response = random.choice(responses)
        
        # Add context-aware continuation
        if context["emotional_tone"] == "seeking_help":
            return f"{base_response} I can see you might be stuck - let's break this down into manageable pieces."
        elif context["emotional_tone"] == "enthusiastic":
            return f"{base_response} I love your enthusiasm for coding!"
        elif is_continuing and "programming" in context["recent_topics"]:
            return f"{base_response} This builds nicely on what we were discussing!"
        else:
            return base_response
        
    def _generate_help_response(self, prompt: str, context: dict, is_continuing: bool) -> str:
        """Generate enhanced help-related response"""
        
        base_responses = [
            "I'm here to help! Running locally means I can give you fast, private assistance.",
            "Happy to assist! That's what I'm here for - local AI support when you need it.",
            "Help is my middle name! Well, technically it's M1K3, but you get the idea.",
            "Absolutely! I love helping people figure things out."
        ]
        
        base = random.choice(base_responses)
        
        # Context-aware additions
        if context["emotional_tone"] == "polite":
            return f"{base} Thanks for being polite - it's appreciated! What specifically can I help you with?"
        elif context["prompt_complexity"] > 15:  # Long, complex question
            return f"{base} I can see this is a detailed question - let me work through it carefully."
        elif is_continuing:
            return f"{base} What else can I help clarify for you?"
        else:
            return f"{base} What would you like to know?"
    
    def _generate_technical_response(self, prompt: str, context: dict) -> str:
        """Generate technical discussion response"""
        responses = [
            "Ah, diving into the technical side of things! I love these kinds of discussions.",
            "Technical topics are fascinating! Let me share what I know about this.",
            "Great technical question! Running locally gives me a unique perspective on systems and performance.",
            "Tech talk! This is where being a local AI gives me some interesting insights."
        ]
        
        base = random.choice(responses)
        
        if 'ai' in prompt.lower() or 'model' in prompt.lower():
            return f"{base} Speaking as an AI myself, I can offer some inside perspective on this!"
        else:
            return base
    
    def _generate_casual_response(self, prompt: str, context: dict) -> str:
        """Generate casual conversation response"""
        responses = [
            "I enjoy casual chats! It's nice to talk about things beyond just work and technical stuff.",
            "Love a good conversation! Even as an AI, I find these discussions really engaging.",
            "Casual chat mode activated! I'm always up for a good discussion.",
            "This is the fun part of being an AI - getting to chat about all sorts of topics!"
        ]
        
        return random.choice(responses)
    
    def _generate_adaptive_response(self, prompt: str, context: dict, is_continuing: bool) -> str:
        """Generate adaptive response for general queries"""
        # Extract key topic from prompt with improved logic
        words = prompt.split()
        topic = None
        
        if len(words) > 1:
            # Comprehensive exclusion list including common verbs and filler words
            excluded_words = {
                'what', 'how', 'why', 'when', 'where', 'can', 'you', 'the', 'and', 'or', 'but',
                'give', 'tell', 'show', 'make', 'need', 'want', 'help', 'please', 'could',
                'would', 'should', 'will', 'does', 'did', 'have', 'has', 'been', 'being',
                'this', 'that', 'with', 'from', 'they', 'them', 'their', 'there', 'here',
                'some', 'any', 'all', 'much', 'many', 'more', 'most', 'very', 'really'
            }
            
            # Find meaningful words (longer than 3 chars, not excluded)
            meaningful_words = [
                w.strip('.,?!').lower() for w in words 
                if len(w.strip('.,?!')) > 3 and w.strip('.,?!').lower() not in excluded_words
            ]
            
            # Take the LAST meaningful word (usually the actual topic)
            if meaningful_words:
                topic = meaningful_words[-1]
        
        # Mix of templates - some use topic, some don't to avoid repetitive patterns
        if topic and len(topic) > 3:
            topic_templates = [
                f"Great question about {topic}! Let me share what I know.",
                f"Ah, {topic}! That's an interesting topic to explore.",
                f"I'd be happy to help you with {topic}.",
                f"Let me think about {topic} for a moment..."
            ]
            generic_templates = [
                "That's a thoughtful question! Here's my perspective:",
                "I'd be glad to help you with that!",
                "Let me work through this with you.",
                "That's an interesting question. Here's what I can tell you:"
            ]
            # 60% chance of using topic, 40% generic for variety
            templates = topic_templates if random.random() < 0.6 else generic_templates
        else:
            # No good topic found, use generic responses
            templates = [
                "That's an interesting question! Let me help you with that.",
                "I'd be happy to explore this with you.",
                "Let me think about this carefully...",
                "Good question! Here's my take on it:",
                "I'd be glad to help you work through this.",
                "That's worth discussing! Let me share my thoughts:",
                "Excellent question! Here's what I can offer:",
                "Let me help you with that inquiry."
            ]
        
        base = random.choice(templates)
        
        # Add personality based on context
        if context["emotional_tone"] == "enthusiastic":
            return f"{base} I can sense your enthusiasm - that's contagious!"
        elif context["emotional_tone"] == "polite":
            return f"{base} Thanks for asking so nicely!"
        elif is_continuing:
            return f"{base} This builds nicely on our conversation."
        else:
            return base
        
    def clear_context(self):
        """Clear conversation context"""
        self.context = ConversationContext()
        print("Conversation context cleared.")
        
    def get_memory_usage(self) -> Dict[str, str]:
        """Get current memory usage info"""
        try:
            import psutil
            process = psutil.Process()
            memory_mb = process.memory_info().rss / 1024 / 1024
        except ImportError:
            memory_mb = 0.0
            
        return {
            "memory_mb": f"{memory_mb:.1f}MB",
            "context_tokens": str(self.context.current_tokens),
            "context_messages": str(len(self.context.messages))
        }

if __name__ == "__main__":
    # Simple CLI test
    engine = SimpleAIEngine()
    
    if not engine.load_model():
        print("Failed to load model")
        exit(1)
        
    print("M1K3 AI Engine Ready! (Demo Mode)")
    print("Type 'quit' to exit, 'clear' to clear context, 'stats' for memory info")
    
    while True:
        try:
            user_input = input("\n> ").strip()
            
            if user_input.lower() == 'quit':
                break
            elif user_input.lower() == 'clear':
                engine.clear_context()
                continue
            elif user_input.lower() == 'stats':
                stats = engine.get_memory_usage()
                print(f"Memory: {stats['memory_mb']}, Context: {stats['context_tokens']} tokens, {stats['context_messages']} messages")
                continue
            elif not user_input:
                continue
                
            print("Assistant: ", end="", flush=True)
            for token in engine.generate_response(user_input):
                print(token, end="", flush=True)
            print()
            
        except KeyboardInterrupt:
            print("\nExiting...")
            break
        except Exception as e:
            print(f"Error: {e}")