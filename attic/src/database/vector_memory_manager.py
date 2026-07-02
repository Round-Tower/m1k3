#!/usr/bin/env python3
"""
Vector Memory Manager - RAG-Enhanced Conversation Memory
Manages conversation vectors and integrates with existing RAG system for enhanced responses
"""

import time
from typing import List, Dict, Optional, Any, Tuple
from pathlib import Path

from .conversation_manager import get_conversation_manager, ConversationRecord
from .conversation_embedder import get_conversation_embedder

class VectorMemoryManager:
    """Manages vector-based conversation memory with RAG integration"""

    def __init__(self, memory_threshold: float = 0.7, max_memory_contexts: int = 5):
        self.memory_threshold = memory_threshold
        self.max_memory_contexts = max_memory_contexts
        self.conversation_manager = get_conversation_manager()
        self.embedder = get_conversation_embedder()

    def is_available(self) -> bool:
        """Check if vector memory system is available"""
        return self.embedder.is_available()

    def enhance_query_with_memory(self, user_input: str, session_id: Optional[str] = None) -> Dict[str, Any]:
        """Enhance user query with relevant conversation memory"""
        if not self.is_available():
            return {
                'enhanced_query': user_input,
                'memory_contexts': [],
                'memory_summary': "",
                'confidence_score': 0.0
            }

        try:
            # Search for similar past conversations
            similar_conversations = self.conversation_manager.search_conversations_by_similarity(
                user_input,
                limit=self.max_memory_contexts * 2,
                threshold=self.memory_threshold
            )

            # Filter and prioritize memory contexts
            memory_contexts = self._filter_memory_contexts(similar_conversations, session_id)

            # Create enhanced query with memory context
            enhanced_query = self._create_enhanced_query(user_input, memory_contexts)

            # Generate memory summary
            memory_summary = self._generate_memory_summary(memory_contexts)

            # Calculate confidence score
            confidence_score = self._calculate_confidence_score(memory_contexts)

            return {
                'enhanced_query': enhanced_query,
                'memory_contexts': memory_contexts,
                'memory_summary': memory_summary,
                'confidence_score': confidence_score,
                'original_query': user_input
            }

        except Exception as e:
            print(f"❌ Memory enhancement failed: {e}")
            return {
                'enhanced_query': user_input,
                'memory_contexts': [],
                'memory_summary': "",
                'confidence_score': 0.0,
                'error': str(e)
            }

    def _filter_memory_contexts(self, similar_conversations: List[Dict],
                              session_id: Optional[str] = None) -> List[Dict]:
        """Filter and prioritize memory contexts"""
        if not similar_conversations:
            return []

        filtered = []
        seen_patterns = set()

        for conv in similar_conversations:
            # Skip if we've seen a very similar pattern
            pattern_key = self._extract_pattern_key(conv['user_input'])
            if pattern_key in seen_patterns:
                continue

            # Prioritize conversations from same session
            priority_score = conv['similarity_score']
            if session_id and conv.get('session_id') == session_id:
                priority_score += 0.1  # Boost same-session conversations

            # Add priority score and include
            conv['priority_score'] = priority_score
            filtered.append(conv)
            seen_patterns.add(pattern_key)

            # Stop when we have enough contexts
            if len(filtered) >= self.max_memory_contexts:
                break

        # Sort by priority score
        filtered.sort(key=lambda x: x['priority_score'], reverse=True)
        return filtered[:self.max_memory_contexts]

    def _extract_pattern_key(self, text: str) -> str:
        """Extract a key representing the pattern of the text"""
        # Simple pattern extraction (could be enhanced with more sophisticated NLP)
        words = text.lower().split()
        # Use first 3 significant words as pattern
        significant_words = [w for w in words if len(w) > 3 and w not in {'what', 'when', 'where', 'how', 'why'}]
        return '_'.join(significant_words[:3])

    def _create_enhanced_query(self, user_input: str, memory_contexts: List[Dict]) -> str:
        """Create an enhanced query with memory context"""
        if not memory_contexts:
            return user_input

        # Build memory context section
        context_parts = []
        for i, ctx in enumerate(memory_contexts, 1):
            similarity = ctx.get('similarity_score', 0)
            summary = ctx.get('conversation_summary', '')
            user_prev = ctx.get('user_input', '')[:100]
            ai_prev = ctx.get('ai_response', '')[:200]

            context_part = f"""Memory {i} (similarity: {similarity:.2f}):
Previous Question: {user_prev}
Previous Response: {ai_prev[:150]}...
Summary: {summary}"""
            context_parts.append(context_part)

        memory_section = "\n\n".join(context_parts)

        enhanced_query = f"""Based on our previous conversations, please respond to the following query with relevant context from our discussion history.

CONVERSATION MEMORY:
{memory_section}

CURRENT QUERY:
{user_input}

Please consider the context from our previous conversations when formulating your response, but focus primarily on answering the current question."""

        return enhanced_query

    def _generate_memory_summary(self, memory_contexts: List[Dict]) -> str:
        """Generate a summary of the memory contexts"""
        if not memory_contexts:
            return "No relevant conversation history found."

        if len(memory_contexts) == 1:
            ctx = memory_contexts[0]
            return f"Found 1 relevant conversation (similarity: {ctx.get('similarity_score', 0):.2f}) from {ctx.get('timestamp', 'unknown time')}"

        total_similarity = sum(ctx.get('similarity_score', 0) for ctx in memory_contexts)
        avg_similarity = total_similarity / len(memory_contexts)

        return f"Found {len(memory_contexts)} relevant conversations (avg similarity: {avg_similarity:.2f}) that may inform this response."

    def _calculate_confidence_score(self, memory_contexts: List[Dict]) -> float:
        """Calculate confidence score for memory enhancement"""
        if not memory_contexts:
            return 0.0

        # Base confidence on similarity scores and number of contexts
        similarities = [ctx.get('similarity_score', 0) for ctx in memory_contexts]

        # Weighted average favoring higher similarities
        if similarities:
            max_sim = max(similarities)
            avg_sim = sum(similarities) / len(similarities)
            context_bonus = min(len(memory_contexts) / self.max_memory_contexts, 1.0) * 0.1

            confidence = (max_sim * 0.7 + avg_sim * 0.3) + context_bonus
            return min(confidence, 1.0)

        return 0.0

    def store_conversation_with_context(self, user_input: str, ai_response: str,
                                      session_id: str, response_time_ms: int,
                                      tokens_used: int, **kwargs) -> str:
        """Store a new conversation with automatic context enhancement"""
        try:
            # Create conversation record
            conversation = ConversationRecord(
                id="",  # Will be auto-generated
                session_id=session_id,
                timestamp=kwargs.get('timestamp'),
                user_input=user_input,
                ai_response=ai_response,
                response_time_ms=response_time_ms,
                tokens_used=tokens_used,
                voice_enabled=kwargs.get('voice_enabled', False),
                avatar_active=kwargs.get('avatar_active', False),
                rag_used=kwargs.get('rag_used', False),
                personality_type=kwargs.get('personality_type', 'default'),
                metadata=kwargs.get('metadata', {})
            )

            # Store in database (embeddings will be generated automatically)
            conversation_id = self.conversation_manager.add_conversation(conversation)

            return conversation_id

        except Exception as e:
            print(f"❌ Failed to store conversation with context: {e}")
            return ""

    def get_conversation_insights(self, days: int = 7) -> Dict[str, Any]:
        """Get insights about conversation patterns and memory"""
        try:
            # Get recent conversations
            recent_convs = self.conversation_manager.get_recent_conversations(limit=100)

            # Get conversation clusters
            clusters = self.conversation_manager.get_conversation_clusters(
                min_similarity=0.8,
                min_cluster_size=2
            )

            # Get conversation stats
            stats = self.conversation_manager.get_conversation_stats(days=days)

            # Calculate memory metrics
            total_conversations = len(recent_convs)
            conversations_with_embeddings = sum(1 for c in recent_convs if c.get('user_embedding'))
            embedding_coverage = (conversations_with_embeddings / total_conversations * 100) if total_conversations else 0

            insights = {
                'total_conversations': total_conversations,
                'embedding_coverage_percent': round(embedding_coverage, 1),
                'conversation_clusters': len(clusters),
                'largest_cluster_size': max(len(cluster) for cluster in clusters) if clusters else 0,
                'memory_system_health': 'good' if embedding_coverage > 80 else 'needs_improvement',
                'stats': stats,
                'cluster_topics': self._extract_cluster_topics(clusters) if clusters else []
            }

            return insights

        except Exception as e:
            print(f"❌ Failed to get conversation insights: {e}")
            return {'error': str(e)}

    def _extract_cluster_topics(self, clusters: List[List[Dict]]) -> List[Dict]:
        """Extract topics from conversation clusters"""
        cluster_topics = []

        for i, cluster in enumerate(clusters):
            if not cluster:
                continue

            # Extract keywords from cluster conversations
            all_inputs = [conv.get('user_input', '') for conv in cluster]
            all_summaries = [conv.get('conversation_summary', '') for conv in cluster if conv.get('conversation_summary')]

            # Simple topic extraction
            text_for_topic = ' '.join(all_inputs + all_summaries)
            keywords = self._extract_simple_keywords(text_for_topic)

            cluster_topic = {
                'cluster_id': i,
                'size': len(cluster),
                'keywords': keywords[:5],
                'sample_question': cluster[0].get('user_input', '')[:100] + "..." if cluster else ""
            }
            cluster_topics.append(cluster_topic)

        return cluster_topics

    def _extract_simple_keywords(self, text: str) -> List[str]:
        """Simple keyword extraction"""
        if not text:
            return []

        import re

        # Basic keyword extraction
        words = re.findall(r'\b[a-zA-Z]{4,}\b', text.lower())

        # Count word frequencies
        word_counts = {}
        for word in words:
            word_counts[word] = word_counts.get(word, 0) + 1

        # Sort by frequency
        sorted_words = sorted(word_counts.items(), key=lambda x: x[1], reverse=True)

        return [word for word, count in sorted_words[:10]]

    def search_memory(self, query: str, limit: int = 10) -> Dict[str, Any]:
        """Search conversation memory with detailed results"""
        try:
            # Get similar conversations
            similar = self.conversation_manager.search_conversations_by_similarity(
                query, limit=limit, threshold=0.5  # Lower threshold for search
            )

            # Format results
            results = []
            for conv in similar:
                result = {
                    'id': conv['id'],
                    'similarity_score': conv.get('similarity_score', 0),
                    'user_input': conv['user_input'],
                    'ai_response': conv['ai_response'][:300] + "..." if len(conv['ai_response']) > 300 else conv['ai_response'],
                    'timestamp': conv['timestamp'],
                    'personality_type': conv.get('personality_type', 'default'),
                    'summary': conv.get('conversation_summary', '')
                }
                results.append(result)

            return {
                'query': query,
                'total_results': len(results),
                'results': results,
                'search_time': time.time()
            }

        except Exception as e:
            print(f"❌ Memory search failed: {e}")
            return {
                'query': query,
                'total_results': 0,
                'results': [],
                'error': str(e)
            }

# Global instance for easy access
_vector_memory_manager = None

def get_vector_memory_manager() -> VectorMemoryManager:
    """Get or create global vector memory manager instance"""
    global _vector_memory_manager
    if _vector_memory_manager is None:
        _vector_memory_manager = VectorMemoryManager()
    return _vector_memory_manager