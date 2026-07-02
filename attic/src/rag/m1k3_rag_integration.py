#!/usr/bin/env python3
"""
M1K3 RAG Integration Module
Integrates RAG capabilities with existing M1K3 AI inference system
"""

import os
import time
from typing import Optional, Generator, Dict, Any, List
from pathlib import Path

# Import M1K3 components
try:
    from src.engines.ai.ai_inference import LocalAIEngine
    from src.utils.intent_classification_system import IntentClassificationEngine, UserIntent
    from src.rag.m1k3_rag_engine import M1K3RAGEngine, RAGDocument
    from synthetic_document_generator import SyntheticDocumentGenerator
    AI_INFERENCE_AVAILABLE = True
except ImportError as e:
    print(f"⚠️  M1K3 components not fully available: {e}")
    AI_INFERENCE_AVAILABLE = False

class M1K3RAGIntegratedEngine:
    """Enhanced M1K3 AI Engine with RAG capabilities"""
    
    def __init__(self, 
                 model_path: str = None,
                 knowledge_base_path: str = "knowledge/m1k3_knowledge_base.json",
                 enable_rag: bool = True,
                 auto_load: bool = False):
        
        self.enable_rag = enable_rag
        self.knowledge_base_path = knowledge_base_path
        
        # Initialize base AI engine
        if AI_INFERENCE_AVAILABLE:
            self.ai_engine = LocalAIEngine(model_path=model_path, auto_load=auto_load)
        else:
            self.ai_engine = None
            print("❌ AI inference not available - running in demo mode")
        
        # Initialize RAG components
        if enable_rag:
            try:
                self.rag_engine = M1K3RAGEngine(
                    knowledge_base_path=knowledge_base_path,
                    lazy_load=True
                )
                self.synthetic_generator = SyntheticDocumentGenerator(knowledge_base_path)
                print("✅ RAG system initialized")
            except Exception as e:
                print(f"⚠️  RAG initialization failed: {e}")
                self.rag_engine = None
                self.synthetic_generator = None
        else:
            self.rag_engine = None
            self.synthetic_generator = None
        
        # Statistics tracking
        self.rag_enhanced_responses = 0
        self.total_responses = 0
        
    def is_model_available(self) -> bool:
        """Check if AI model is available"""
        return self.ai_engine is not None and self.ai_engine.is_model_available()
    
    def load_model(self) -> bool:
        """Load AI model"""
        if self.ai_engine:
            return self.ai_engine.load_model()
        return False
    
    def generate_response(self, 
                         prompt: str, 
                         max_tokens: int = 512, 
                         show_thinking: bool = False,
                         use_rag: bool = None) -> Generator[str, None, None]:
        """Generate response with optional RAG enhancement"""
        
        if use_rag is None:
            use_rag = self.enable_rag and self.rag_engine is not None
        
        self.total_responses += 1
        enhanced_prompt = prompt
        rag_context_used = False
        
        try:
            # RAG Enhancement Phase
            if use_rag and self.rag_engine:
                # Get intent classification if available
                intent_classification = None
                if hasattr(self.ai_engine, 'intent_engine') and self.ai_engine.intent_engine:
                    intent_classification = self.ai_engine.intent_engine.classify_intent(prompt)
                elif hasattr(self.rag_engine, 'intent_engine') and self.rag_engine.intent_engine:
                    intent_classification = self.rag_engine.intent_engine.classify_intent(prompt)
                
                # Enhance prompt with RAG context
                enhanced_prompt = self.rag_engine.enhance_prompt(prompt, intent_classification)
                
                # Check if RAG provided additional context
                if enhanced_prompt != prompt:
                    rag_context_used = True
                    self.rag_enhanced_responses += 1
                    
                    if show_thinking:
                        yield "[RAG Context Retrieved] "
            
            # Generate response using base AI engine
            if self.ai_engine:
                response_generator = self.ai_engine.generate_response(
                    enhanced_prompt, 
                    max_tokens=max_tokens, 
                    show_thinking=show_thinking
                )
                
                # Stream the response
                response_text = ""
                for token in response_generator:
                    response_text += token
                    yield token
                
                # Log successful RAG enhancement
                if rag_context_used and show_thinking:
                    yield f" [Enhanced with RAG context]"
                    
            else:
                # Fallback response when AI engine not available
                if rag_context_used:
                    # Extract just the relevant context from RAG
                    lines = enhanced_prompt.split('\n')
                    context_lines = [line for line in lines if 'Document' in line and 'relevance' in line]
                    if context_lines:
                        yield f"Based on available knowledge: {context_lines[0].split(':', 1)[1].strip() if ':' in context_lines[0] else 'Information found in knowledge base.'}"
                    else:
                        yield "I found some relevant information, but AI processing is currently unavailable."
                else:
                    yield "AI processing is currently unavailable. Please check system status."
        
        except Exception as e:
            yield f"Error generating response: {e}"
    
    def add_knowledge(self, 
                     title: str, 
                     content: str, 
                     category: str,
                     intent: Optional[UserIntent] = None,
                     metadata: Dict[str, Any] = None) -> Optional[str]:
        """Add knowledge to RAG system"""
        if self.rag_engine:
            return self.rag_engine.add_knowledge(title, content, category, intent, metadata)
        return None
    
    def generate_synthetic_knowledge(self, 
                                   category: str = None,
                                   count: int = 10,
                                   progress_callback: Optional[callable] = None) -> Dict[str, Any]:
        """Generate synthetic knowledge documents"""
        if self.synthetic_generator:
            return self.synthetic_generator.generate_and_save(
                category=category,
                count=count, 
                progress_callback=progress_callback
            )
        return {"success": False, "message": "Synthetic generator not available"}
    
    def get_rag_stats(self) -> Dict[str, Any]:
        """Get RAG system statistics"""
        stats = {
            "rag_enabled": self.enable_rag,
            "rag_available": self.rag_engine is not None,
            "total_responses": self.total_responses,
            "rag_enhanced_responses": self.rag_enhanced_responses,
            "rag_enhancement_rate": (self.rag_enhanced_responses / max(self.total_responses, 1)) * 100,
            "knowledge_base_path": self.knowledge_base_path
        }
        
        if self.rag_engine:
            rag_engine_stats = self.rag_engine.get_stats()
            stats.update(rag_engine_stats)
        
        return stats
    
    def clear_context(self):
        """Clear conversation context"""
        if self.ai_engine:
            self.ai_engine.clear_context()
    
    def get_memory_usage(self) -> Dict[str, str]:
        """Get memory usage information"""
        if self.ai_engine:
            base_usage = self.ai_engine.get_memory_usage()
            
            # Add RAG-specific memory usage
            if self.rag_engine and hasattr(self.rag_engine, 'embedding_engine') and self.rag_engine.embedding_engine:
                cache_size = len(self.rag_engine.embedding_engine.embedding_cache) if self.rag_engine.embedding_engine.embedding_cache else 0
                base_usage["rag_cache_entries"] = str(cache_size)
            
            return base_usage
        
        return {"memory_mb": "Unknown", "context_tokens": "0", "context_messages": "0"}
    
    def get_token_usage(self) -> Dict[str, Any]:
        """Get token usage statistics"""
        if self.ai_engine:
            return self.ai_engine.get_token_usage()
        
        return {
            "current_tokens": 0,
            "max_tokens": 2048,
            "usage_percent": 0,
            "messages_count": 0,
            "trimming_threshold": 1638,
            "needs_trimming": False
        }
    
    def get_eco_metrics(self) -> Dict[str, str]:
        """Get environmental impact metrics"""
        if self.ai_engine:
            base_metrics = self.ai_engine.get_eco_metrics()
            
            # Add RAG-specific eco benefits
            base_metrics["rag_enhanced_responses"] = str(self.rag_enhanced_responses)
            base_metrics["knowledge_efficiency"] = f"{self.rag_enhanced_responses}/{self.total_responses}"
            
            return base_metrics
        
        return {
            "energy_saved_kwh": "0.00",
            "water_saved_gallons": "0.0", 
            "co2_saved_grams": "0",
            "responses_count": str(self.total_responses),
            "privacy_score": "100%",
            "data_transmitted": "0 bytes"
        }
    
    # Delegate other methods to base AI engine
    def __getattr__(self, name):
        """Delegate unknown methods to base AI engine"""
        if self.ai_engine and hasattr(self.ai_engine, name):
            return getattr(self.ai_engine, name)
        raise AttributeError(f"'{self.__class__.__name__}' object has no attribute '{name}'")

def create_default_knowledge_base():
    """Create a default knowledge base with essential documents"""
    
    knowledge_base_path = "knowledge/m1k3_knowledge_base.json"
    
    # Create knowledge directory
    Path("knowledge").mkdir(exist_ok=True)
    
    # Check if knowledge base already exists
    if Path(knowledge_base_path).exists():
        print(f"📚 Knowledge base already exists at {knowledge_base_path}")
        return knowledge_base_path
    
    print("🚀 Creating default M1K3 knowledge base...")
    
    # Initialize RAG engine
    rag_engine = M1K3RAGEngine(knowledge_base_path=knowledge_base_path)
    
    # Add essential documents
    essential_documents = [
        {
            "title": "M1K3 System Introduction",
            "content": "M1K3 is a privacy-focused local AI assistant with voice synthesis, web dashboard, and CLI interfaces. It features multi-backend AI with TinyLlama-1.1B-Chat, real-time avatar visualization, and PWA deployment. All processing is done locally for maximum privacy.",
            "category": "system_query",
            "intent": UserIntent.HELP_REQUEST,
            "metadata": {"tags": ["m1k3", "introduction", "system", "privacy"], "difficulty": "beginner"}
        },
        {
            "title": "Basic Python Variable Declaration",
            "content": "In Python, you create variables by simply assigning values: x = 5, name = 'Alice', is_active = True. Python automatically determines the type based on the value. Variables can be reassigned to different types at any time.",
            "category": "code_explanation",
            "intent": UserIntent.EXPLANATION_REQUEST,
            "metadata": {"tags": ["python", "variables", "programming", "basics"], "difficulty": "beginner"}
        },
        {
            "title": "Simple Arithmetic Operations",
            "content": "Basic math operations in programming: addition (+), subtraction (-), multiplication (*), division (/). For example: 5 + 3 = 8, 10 - 4 = 6, 7 * 2 = 14, 15 / 3 = 5. These operations follow standard mathematical rules.",
            "category": "mathematical_calculation",
            "intent": UserIntent.MATHEMATICAL_CALCULATION,
            "metadata": {"tags": ["math", "arithmetic", "operations", "basic"], "difficulty": "beginner"}
        },
        {
            "title": "Common Python Debugging: NameError",
            "content": "NameError occurs when Python cannot find a variable or function name. Common causes: typos, using variables before defining them, or scope issues. Solution: check spelling, ensure variables are defined before use, and verify variable scope.",
            "category": "code_debugging",
            "intent": UserIntent.CODE_DEBUGGING,
            "metadata": {"tags": ["python", "debugging", "nameerror", "error"], "difficulty": "beginner"}
        },
        {
            "title": "M1K3 Available Commands",
            "content": "M1K3 commands: /help (show commands), /stats (system status), /clear (reset conversation), /tokens (token usage), avatar start (launch dashboard), avatar emotion <emotion> (set mood), quit/exit (close). Type commands without quotes.",
            "category": "system_query",
            "intent": UserIntent.HELP_REQUEST,
            "metadata": {"tags": ["commands", "help", "system", "reference"], "difficulty": "beginner"}
        },
        {
            "title": "Friendly Greeting Response",
            "content": "Hi there! I'm M1K3, your local AI assistant. I'm here to help with programming questions, math calculations, explanations, or just have a friendly chat. What can I help you with today?",
            "category": "casual_conversation", 
            "intent": UserIntent.GREETING,
            "metadata": {"tags": ["greeting", "friendly", "introduction", "conversation"], "difficulty": "beginner"}
        }
    ]
    
    # Add documents to knowledge base
    for doc_data in essential_documents:
        rag_engine.add_knowledge(
            title=doc_data["title"],
            content=doc_data["content"],
            category=doc_data["category"],
            intent=doc_data["intent"],
            metadata=doc_data["metadata"]
        )
    
    print(f"✅ Created default knowledge base with {len(essential_documents)} documents")
    return knowledge_base_path

def test_rag_integration():
    """Test RAG integration with M1K3"""
    print("🧪 Testing M1K3 RAG Integration")
    print("=" * 60)
    
    # Create default knowledge base
    kb_path = create_default_knowledge_base()
    
    # Initialize integrated engine
    print("\n🚀 Initializing M1K3 RAG Integrated Engine...")
    engine = M1K3RAGIntegratedEngine(
        knowledge_base_path=kb_path,
        enable_rag=True,
        auto_load=False  # Skip model loading for quick test
    )
    
    # Test queries
    test_queries = [
        "How do I create variables in Python?",
        "What is 5 + 3?", 
        "Hello, how are you?",
        "What commands are available in M1K3?",
        "How do I fix a NameError in Python?"
    ]
    
    print(f"\n🔍 Testing RAG-enhanced responses...")
    
    for i, query in enumerate(test_queries, 1):
        print(f"\n--- Test {i} ---")
        print(f"Query: {query}")
        
        try:
            print("Response: ", end="")
            response_parts = []
            for token in engine.generate_response(query, max_tokens=100, show_thinking=True, use_rag=True):
                response_parts.append(token)
                print(token, end="", flush=True)
            print()  # New line after response
            
        except Exception as e:
            print(f"Error: {e}")
    
    # Show statistics
    print(f"\n📊 RAG Integration Statistics:")
    stats = engine.get_rag_stats()
    
    print(f"  RAG Enabled: {stats['rag_enabled']}")
    print(f"  RAG Available: {stats['rag_available']}")
    print(f"  Total Responses: {stats['total_responses']}")
    print(f"  RAG Enhanced: {stats['rag_enhanced_responses']}")
    print(f"  Enhancement Rate: {stats['rag_enhancement_rate']:.1f}%")
    
    if 'knowledge_base' in stats:
        kb_stats = stats['knowledge_base']
        print(f"  Knowledge Base Documents: {kb_stats['total_documents']}")
        print(f"  Categories: {list(kb_stats['categories'].keys())}")
    
    # Test synthetic generation
    print(f"\n🤖 Testing synthetic document generation...")
    
    try:
        result = engine.generate_synthetic_knowledge(
            category="mathematical_calculation",
            count=3
        )
        
        if result['success']:
            print(f"✅ Generated {result['count']} synthetic documents")
        else:
            print(f"❌ Failed: {result['message']}")
            
    except Exception as e:
        print(f"⚠️  Synthetic generation error: {e}")
    
    print(f"\n✅ M1K3 RAG Integration test completed!")

if __name__ == "__main__":
    test_rag_integration()