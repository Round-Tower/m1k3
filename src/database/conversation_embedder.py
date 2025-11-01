#!/usr/bin/env python3
"""
Conversation Embedder - Vector Embedding Pipeline
Generates and manages vector embeddings for conversations in M1K3
"""

import os
import numpy as np
from typing import List, Optional, Tuple, Dict, Any
from pathlib import Path

class ConversationEmbedder:
    """Generates vector embeddings for conversation text"""

    def __init__(self,
                 model_name: str = "google/embeddinggemma-300m",
                 embedding_dim: Optional[int] = None,
                 truncate_dim: Optional[int] = None):
        """
        Initialize conversation embedder with EmbeddingGemma (default) or other models.

        Args:
            model_name: Model identifier (default: google/embeddinggemma-300m)
            embedding_dim: Expected output dimension (auto-detected if None)
            truncate_dim: Truncate embeddings to this dimension via Matryoshka Representation Learning
                         Supported for EmbeddingGemma: 768 (full), 512, 256, 128
        """
        self.model_name = model_name
        self.model = None
        self.tokenizer = None
        self.truncate_dim = truncate_dim

        # Set default embedding dimensions based on model
        if embedding_dim is None:
            if "embeddinggemma" in model_name.lower():
                self.embedding_dim = 768  # EmbeddingGemma native dimension
            elif "bge-small" in model_name.lower():
                self.embedding_dim = 384  # BGE-small dimension
            else:
                self.embedding_dim = 768  # Safe default
        else:
            self.embedding_dim = embedding_dim

        self._initialize_model()

    def _initialize_model(self):
        """Initialize the embedding model with multi-model support"""
        try:
            from sentence_transformers import SentenceTransformer

            # Determine local model directory name
            if "embeddinggemma" in self.model_name.lower():
                local_model_dir = "embeddinggemma-300m"
            elif "bge-small" in self.model_name.lower():
                local_model_dir = "bge-small-en-v1.5"
            else:
                local_model_dir = self.model_name.replace("/", "_")

            # Check for local models
            models_dir = Path("models/embeddings")
            if models_dir.exists():
                model_path = models_dir / local_model_dir
                if model_path.exists():
                    self.model = SentenceTransformer(str(model_path))
                    print(f"✅ Loaded local embedding model from {model_path}")
                    self._validate_and_set_dimensions()
                    return

            # Download model if not available locally
            print(f"⬇️ Downloading embedding model: {self.model_name}")
            self.model = SentenceTransformer(self.model_name)

            # Validate dimensions
            self._validate_and_set_dimensions()
            print(f"✅ Embedding model initialized ({self.embedding_dim}D)")

        except ImportError:
            print("⚠️ sentence-transformers not available. Installing...")
            import subprocess
            subprocess.check_call(["pip", "install", "sentence-transformers"])
            from sentence_transformers import SentenceTransformer
            self.model = SentenceTransformer(self.model_name)
            self._validate_and_set_dimensions()
            print("✅ Installed and initialized embedding model")
        except Exception as e:
            print(f"❌ Failed to initialize embedding model: {e}")
            self.model = None

    def _validate_and_set_dimensions(self):
        """Validate model dimensions and adjust if needed"""
        if not self.model:
            return

        # Test encoding to get actual dimensions
        test_embedding = self.model.encode(["test"], show_progress_bar=False)
        actual_dim = len(test_embedding[0])

        # Update embedding_dim if different
        if self.embedding_dim != actual_dim:
            print(f"⚠️ Model dimension ({actual_dim}D) differs from expected ({self.embedding_dim}D)")
            self.embedding_dim = actual_dim

        # Validate truncate_dim for EmbeddingGemma
        if self.truncate_dim:
            if "embeddinggemma" in self.model_name.lower():
                valid_dims = [768, 512, 256, 128]
                if self.truncate_dim not in valid_dims:
                    print(f"⚠️ Invalid truncate_dim {self.truncate_dim}. Must be one of {valid_dims}")
                    self.truncate_dim = None
                else:
                    print(f"✅ Matryoshka truncation enabled: {self.embedding_dim}D → {self.truncate_dim}D")
            else:
                print(f"⚠️ Truncation only supported for EmbeddingGemma. Ignoring truncate_dim.")
                self.truncate_dim = None

    def is_available(self) -> bool:
        """Check if the embedding model is available"""
        return self.model is not None

    def embed_text(self, text: str) -> Optional[List[float]]:
        """Generate embedding for a single text"""
        if not self.is_available():
            return None

        try:
            # Clean and preprocess text
            clean_text = self._preprocess_text(text)
            if not clean_text:
                return None

            # Generate embedding
            embedding = self.model.encode([clean_text], show_progress_bar=False)
            embedding_vec = embedding[0]

            # Apply Matryoshka truncation if specified
            if self.truncate_dim and len(embedding_vec) > self.truncate_dim:
                embedding_vec = embedding_vec[:self.truncate_dim]

            return embedding_vec.tolist()  # Convert numpy array to list

        except Exception as e:
            print(f"❌ Embedding generation failed: {e}")
            return None

    def embed_conversation(self, user_input: str, ai_response: str) -> Tuple[Optional[List[float]], Optional[List[float]]]:
        """Generate embeddings for both user input and AI response"""
        user_embedding = self.embed_text(user_input)
        response_embedding = self.embed_text(ai_response)
        return user_embedding, response_embedding

    def embed_batch(self, texts: List[str]) -> List[Optional[List[float]]]:
        """Generate embeddings for multiple texts efficiently"""
        if not self.is_available():
            return [None] * len(texts)

        try:
            # Clean texts
            clean_texts = [self._preprocess_text(text) for text in texts]
            valid_indices = [i for i, text in enumerate(clean_texts) if text]
            valid_texts = [clean_texts[i] for i in valid_indices]

            if not valid_texts:
                return [None] * len(texts)

            # Generate embeddings
            embeddings = self.model.encode(valid_texts, show_progress_bar=False)

            # Apply Matryoshka truncation if specified
            if self.truncate_dim:
                embeddings = np.array([emb[:self.truncate_dim] for emb in embeddings])

            # Map back to original order
            result = [None] * len(texts)
            for i, idx in enumerate(valid_indices):
                result[idx] = embeddings[i].tolist()

            return result

        except Exception as e:
            print(f"❌ Batch embedding generation failed: {e}")
            return [None] * len(texts)

    def _preprocess_text(self, text: str) -> str:
        """Preprocess text for embedding generation"""
        if not text or not text.strip():
            return ""

        # Basic cleaning
        clean_text = text.strip()

        # Remove excessive whitespace
        clean_text = ' '.join(clean_text.split())

        # Truncate to reasonable length based on model
        # EmbeddingGemma: 2K token context (~8000 chars)
        # BGE models: ~512 tokens (~2000 chars conservative)
        if "embeddinggemma" in self.model_name.lower():
            max_chars = 8000  # 2K tokens * ~4 chars/token
        else:
            max_chars = 2000  # Conservative for BGE

        if len(clean_text) > max_chars:
            clean_text = clean_text[:max_chars] + "..."

        return clean_text

    def cosine_similarity(self, embedding1: List[float], embedding2: List[float]) -> float:
        """Calculate cosine similarity between two embeddings"""
        try:
            # Convert to numpy arrays
            vec1 = np.array(embedding1)
            vec2 = np.array(embedding2)

            # Calculate cosine similarity
            dot_product = np.dot(vec1, vec2)
            norm1 = np.linalg.norm(vec1)
            norm2 = np.linalg.norm(vec2)

            if norm1 == 0 or norm2 == 0:
                return 0.0

            return dot_product / (norm1 * norm2)

        except Exception as e:
            print(f"❌ Similarity calculation failed: {e}")
            return 0.0

    def find_similar_embeddings(self, query_embedding: List[float],
                              candidate_embeddings: List[Tuple[str, List[float]]],
                              threshold: float = 0.7,
                              limit: int = 10) -> List[Tuple[str, float]]:
        """Find embeddings similar to the query embedding"""
        if not query_embedding or not candidate_embeddings:
            return []

        similarities = []
        for conversation_id, embedding in candidate_embeddings:
            if embedding:
                similarity = self.cosine_similarity(query_embedding, embedding)
                if similarity >= threshold:
                    similarities.append((conversation_id, similarity))

        # Sort by similarity (descending) and limit results
        similarities.sort(key=lambda x: x[1], reverse=True)
        return similarities[:limit]

    def generate_conversation_summary(self, user_input: str, ai_response: str) -> str:
        """Generate a summary of the conversation for better retrieval"""
        try:
            # Simple extractive summary for now
            # In the future, could use a summarization model

            # Extract key phrases from user input
            user_keywords = self._extract_keywords(user_input)
            response_keywords = self._extract_keywords(ai_response)

            # Combine into summary
            summary_parts = []
            if user_keywords:
                summary_parts.append(f"User asked about: {', '.join(user_keywords[:3])}")
            if response_keywords:
                summary_parts.append(f"AI discussed: {', '.join(response_keywords[:3])}")

            return " | ".join(summary_parts) if summary_parts else f"Conversation: {user_input[:100]}..."

        except Exception as e:
            print(f"❌ Summary generation failed: {e}")
            return f"Conversation: {user_input[:100]}..."

    def _extract_keywords(self, text: str) -> List[str]:
        """Extract keywords from text (simple implementation)"""
        if not text:
            return []

        # Simple keyword extraction
        import re

        # Remove common stop words
        stop_words = {
            'the', 'is', 'at', 'which', 'on', 'a', 'an', 'and', 'or', 'but', 'in', 'with',
            'to', 'for', 'of', 'as', 'by', 'from', 'up', 'into', 'over', 'after', 'can',
            'could', 'should', 'would', 'will', 'shall', 'may', 'might', 'must', 'do',
            'does', 'did', 'have', 'has', 'had', 'be', 'been', 'being', 'am', 'are', 'was',
            'were', 'i', 'you', 'he', 'she', 'it', 'we', 'they', 'me', 'him', 'her', 'us',
            'them', 'my', 'your', 'his', 'her', 'its', 'our', 'their', 'this', 'that',
            'these', 'those', 'what', 'when', 'where', 'why', 'how', 'who', 'whom', 'whose'
        }

        # Extract words (keep letters, numbers, hyphens)
        words = re.findall(r'\b[a-zA-Z0-9-]+\b', text.lower())

        # Filter out stop words and short words
        keywords = [word for word in words if len(word) > 2 and word not in stop_words]

        # Return unique keywords, preserving order
        unique_keywords = []
        seen = set()
        for keyword in keywords:
            if keyword not in seen:
                unique_keywords.append(keyword)
                seen.add(keyword)

        return unique_keywords[:10]  # Limit to 10 keywords

# Global instance for easy access
_conversation_embedder = None

def get_conversation_embedder() -> ConversationEmbedder:
    """Get or create global conversation embedder instance"""
    global _conversation_embedder
    if _conversation_embedder is None:
        _conversation_embedder = ConversationEmbedder()
    return _conversation_embedder