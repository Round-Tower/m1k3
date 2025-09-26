#!/usr/bin/env python3
"""
Memory-Enhanced RAG Engine - Hybrid Context System
Combines conversation memory with knowledge base for enhanced M1K3 responses
"""

from typing import Dict, List, Optional, Any, Tuple
from pathlib import Path
import time

from .vector_memory_manager import get_vector_memory_manager
from .conversation_embedder import get_conversation_embedder

# Import existing RAG system
try:
    from src.rag.m1k3_rag_engine import M1K3RAGEngine
    from src.rag.m1k3_rag_integration import M1K3RAGIntegration
    RAG_AVAILABLE = True
except ImportError:
    print("⚠️ Existing RAG system not available - memory-only mode")
    RAG_AVAILABLE = False
    M1K3RAGEngine = None
    M1K3RAGIntegration = None

class MemoryEnhancedRAG:
    """Enhanced RAG system combining conversation memory with knowledge base"""

    def __init__(self, memory_weight: float = 0.6, knowledge_weight: float = 0.4):
        self.memory_weight = memory_weight
        self.knowledge_weight = knowledge_weight

        # Initialize memory system
        self.memory_manager = get_vector_memory_manager()
        self.embedder = get_conversation_embedder()

        # Initialize existing RAG system if available
        self.rag_engine = None
        self.rag_integration = None

        if RAG_AVAILABLE:
            try:
                self.rag_engine = M1K3RAGEngine()
                self.rag_integration = M1K3RAGIntegration()
                print("✅ Knowledge base RAG system loaded")
            except Exception as e:
                print(f"⚠️ RAG system initialization failed: {e}")
                # Don't modify the global constant - just set instance flag
                self.rag_available = False
        else:
            self.rag_available = RAG_AVAILABLE

    def is_available(self) -> bool:
        """Check if memory-enhanced RAG is available"""
        return self.memory_manager.is_available()

    def enhance_query_with_hybrid_context(self, user_input: str, session_id: Optional[str] = None,
                                        max_memory_contexts: int = 3, max_knowledge_contexts: int = 3) -> Dict[str, Any]:
        """Enhance query with both conversation memory and knowledge base"""
        start_time = time.time()

        # Get memory enhancement
        memory_enhancement = self.memory_manager.enhance_query_with_memory(
            user_input, session_id
        )

        # Get knowledge base enhancement if available
        knowledge_contexts = []
        knowledge_summary = ""

        if getattr(self, 'rag_available', RAG_AVAILABLE) and self.rag_engine:
            try:
                # Use existing RAG system for knowledge retrieval
                knowledge_results = self.rag_engine.retrieve_documents(
                    user_input, top_k=max_knowledge_contexts
                )
                knowledge_contexts = self._format_knowledge_contexts(knowledge_results)
                knowledge_summary = f"Found {len(knowledge_contexts)} relevant knowledge documents"
            except Exception as e:
                print(f"⚠️ Knowledge retrieval failed: {e}")

        # Combine contexts intelligently
        combined_enhancement = self._combine_contexts(
            user_input, memory_enhancement, knowledge_contexts
        )

        processing_time = time.time() - start_time

        return {
            **combined_enhancement,
            'memory_summary': memory_enhancement.get('memory_summary', ''),
            'knowledge_summary': knowledge_summary,
            'processing_time_ms': int(processing_time * 1000),
            'context_sources': {
                'memory_contexts': len(memory_enhancement.get('memory_contexts', [])),
                'knowledge_contexts': len(knowledge_contexts)
            }
        }

    def _format_knowledge_contexts(self, knowledge_results: List[Dict]) -> List[Dict]:
        """Format knowledge base results for consistent context handling"""
        formatted = []

        for result in knowledge_results:
            formatted_context = {
                'type': 'knowledge',
                'title': result.get('title', 'Knowledge Document'),
                'content': result.get('content', ''),
                'category': result.get('category', 'general'),
                'relevance_score': result.get('score', 0.0),
                'source': 'knowledge_base'
            }
            formatted.append(formatted_context)

        return formatted

    def _combine_contexts(self, user_input: str, memory_enhancement: Dict[str, Any],
                         knowledge_contexts: List[Dict]) -> Dict[str, Any]:
        """Intelligently combine memory and knowledge contexts"""

        memory_contexts = memory_enhancement.get('memory_contexts', [])
        memory_confidence = memory_enhancement.get('confidence_score', 0.0)

        # Prioritize contexts based on relevance and type
        all_contexts = []

        # Add memory contexts with enhanced metadata
        for ctx in memory_contexts:
            ctx_enhanced = {
                **ctx,
                'type': 'memory',
                'source': 'conversation_memory',
                'priority_score': ctx.get('similarity_score', 0) * self.memory_weight
            }
            all_contexts.append(ctx_enhanced)

        # Add knowledge contexts
        for ctx in knowledge_contexts:
            ctx['priority_score'] = ctx.get('relevance_score', 0) * self.knowledge_weight
            all_contexts.append(ctx)

        # Sort by priority score
        all_contexts.sort(key=lambda x: x.get('priority_score', 0), reverse=True)

        # Take top contexts (balanced between memory and knowledge)
        max_total_contexts = 5
        selected_contexts = self._balance_context_selection(all_contexts, max_total_contexts)

        # Create enhanced query
        enhanced_query = self._create_hybrid_enhanced_query(user_input, selected_contexts)

        # Calculate combined confidence
        combined_confidence = self._calculate_combined_confidence(
            memory_confidence, len(knowledge_contexts)
        )

        return {
            'enhanced_query': enhanced_query,
            'contexts': selected_contexts,
            'confidence_score': combined_confidence,
            'original_query': user_input,
            'enhancement_type': 'hybrid_memory_knowledge'
        }

    def _balance_context_selection(self, all_contexts: List[Dict], max_contexts: int) -> List[Dict]:
        """Balance selection between memory and knowledge contexts"""
        if len(all_contexts) <= max_contexts:
            return all_contexts

        memory_contexts = [ctx for ctx in all_contexts if ctx.get('type') == 'memory']
        knowledge_contexts = [ctx for ctx in all_contexts if ctx.get('type') == 'knowledge']

        # Ensure at least one of each type if available
        selected = []

        if memory_contexts:
            selected.extend(memory_contexts[:max(1, max_contexts // 2)])

        if knowledge_contexts:
            remaining_slots = max_contexts - len(selected)
            selected.extend(knowledge_contexts[:remaining_slots])

        # Fill remaining slots with highest priority contexts
        if len(selected) < max_contexts:
            remaining_contexts = [ctx for ctx in all_contexts if ctx not in selected]
            remaining_slots = max_contexts - len(selected)
            selected.extend(remaining_contexts[:remaining_slots])

        return selected

    def _create_hybrid_enhanced_query(self, user_input: str, contexts: List[Dict]) -> str:
        """Create enhanced query with hybrid context"""
        if not contexts:
            return user_input

        # Separate contexts by type
        memory_contexts = [ctx for ctx in contexts if ctx.get('type') == 'memory']
        knowledge_contexts = [ctx for ctx in contexts if ctx.get('type') == 'knowledge']

        context_sections = []

        # Add conversation memory context
        if memory_contexts:
            memory_section = "CONVERSATION MEMORY:"
            for i, ctx in enumerate(memory_contexts, 1):
                similarity = ctx.get('similarity_score', 0)
                user_prev = ctx.get('user_input', '')[:100]
                ai_prev = ctx.get('ai_response', '')[:150]

                memory_section += f"""
Memory {i} (similarity: {similarity:.2f}):
Previous: {user_prev}
Response: {ai_prev}..."""

            context_sections.append(memory_section)

        # Add knowledge base context
        if knowledge_contexts:
            knowledge_section = "RELEVANT KNOWLEDGE:"
            for i, ctx in enumerate(knowledge_contexts, 1):
                relevance = ctx.get('relevance_score', 0)
                title = ctx.get('title', 'Knowledge')
                content = ctx.get('content', '')[:200]

                knowledge_section += f"""
Knowledge {i} (relevance: {relevance:.2f}):
Topic: {title}
Content: {content}..."""

            context_sections.append(knowledge_section)

        # Combine sections
        full_context = "\n\n".join(context_sections)

        enhanced_query = f"""Using both your conversation memory and knowledge base, please respond to the following query with relevant context.

{full_context}

CURRENT QUERY:
{user_input}

Please draw upon both the conversation history and relevant knowledge to provide a comprehensive response that acknowledges our previous discussions while incorporating pertinent information from your knowledge base."""

        return enhanced_query

    def _calculate_combined_confidence(self, memory_confidence: float, knowledge_count: int) -> float:
        """Calculate combined confidence score"""
        # Base confidence from memory
        base_confidence = memory_confidence * self.memory_weight

        # Knowledge contribution (diminishing returns)
        if knowledge_count > 0:
            knowledge_confidence = min(knowledge_count / 3.0, 1.0) * self.knowledge_weight
            combined = base_confidence + knowledge_confidence
        else:
            combined = base_confidence

        return min(combined, 1.0)

    def generate_response_with_hybrid_rag(self, user_input: str, ai_engine,
                                        session_id: Optional[str] = None) -> Dict[str, Any]:
        """Generate AI response using hybrid memory + knowledge RAG"""
        start_time = time.time()

        # Get hybrid enhancement
        enhancement = self.enhance_query_with_hybrid_context(user_input, session_id)

        if enhancement['confidence_score'] > 0.3:
            # Use enhanced query
            enhanced_input = enhancement['enhanced_query']
        else:
            # Fall back to original query
            enhanced_input = user_input

        # Generate response
        try:
            if hasattr(ai_engine, 'generate_response'):
                ai_response = ai_engine.generate_response(enhanced_input)
            else:
                ai_response = str(ai_engine(enhanced_input))

            # Handle generator responses
            if hasattr(ai_response, '__iter__') and not isinstance(ai_response, str):
                ai_response = ''.join(chunk for chunk in ai_response if chunk)

        except Exception as e:
            print(f"❌ AI generation failed: {e}")
            ai_response = f"I encountered an error processing your request: {str(e)}"

        total_time = time.time() - start_time

        return {
            'user_input': user_input,
            'ai_response': ai_response,
            'enhancement_used': enhancement['confidence_score'] > 0.3,
            'confidence_score': enhancement['confidence_score'],
            'context_sources': enhancement['context_sources'],
            'processing_time_ms': int(total_time * 1000),
            'memory_summary': enhancement.get('memory_summary', ''),
            'knowledge_summary': enhancement.get('knowledge_summary', '')
        }

    def get_system_status(self) -> Dict[str, Any]:
        """Get status of the hybrid RAG system"""
        status = {
            'memory_system_available': self.memory_manager.is_available(),
            'knowledge_system_available': getattr(self, 'rag_available', RAG_AVAILABLE) and self.rag_engine is not None,
            'embedding_system_available': self.embedder.is_available(),
            'hybrid_mode': 'full' if (self.memory_manager.is_available() and getattr(self, 'rag_available', RAG_AVAILABLE)) else 'memory_only',
            'weights': {
                'memory_weight': self.memory_weight,
                'knowledge_weight': self.knowledge_weight
            }
        }

        if getattr(self, 'rag_available', RAG_AVAILABLE) and self.rag_engine:
            try:
                rag_status = self.rag_engine.get_system_status()
                status['knowledge_base_stats'] = rag_status
            except:
                status['knowledge_base_stats'] = "unavailable"

        return status

# Global instance for easy access
_memory_enhanced_rag = None

def get_memory_enhanced_rag() -> MemoryEnhancedRAG:
    """Get or create global memory-enhanced RAG instance"""
    global _memory_enhanced_rag
    if _memory_enhanced_rag is None:
        _memory_enhanced_rag = MemoryEnhancedRAG()
    return _memory_enhanced_rag