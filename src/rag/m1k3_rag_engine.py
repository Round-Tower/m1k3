#!/usr/bin/env python3
"""
M1K3 RAG Engine - Lightweight Retrieval-Augmented Generation
Integrates with existing M1K3 architecture for enhanced intelligence
"""

import json
import os
import time
import hashlib
import numpy as np
from pathlib import Path
from typing import Dict, List, Optional, Any, Union
from dataclasses import dataclass, asdict
from enum import Enum
import logging

# Import existing M1K3 components
try:
    from src.utils.intent_classification_system import UserIntent, IntentClassification, IntentClassificationEngine
except ImportError:
    print("⚠️  Intent classification not available - using fallback")
    UserIntent = None
    IntentClassification = None

# Import embedding libraries with fallbacks
try:
    from sentence_transformers import SentenceTransformer
    SENTENCE_TRANSFORMERS_AVAILABLE = True
except ImportError:
    print("❌ SentenceTransformers not available - using mock embeddings")
    SENTENCE_TRANSFORMERS_AVAILABLE = False

try:
    import faiss
    FAISS_AVAILABLE = True
except ImportError:
    print("❌ FAISS not available - using numpy similarity search")
    FAISS_AVAILABLE = False

@dataclass
class RAGDocument:
    """RAG document structure with metadata"""
    id: str
    title: str
    content: str
    category: str
    intent: Optional[UserIntent] = None
    metadata: Dict[str, Any] = None
    embedding: Optional[List[float]] = None
    created_at: Optional[str] = None
    
    def __post_init__(self):
        if self.metadata is None:
            self.metadata = {}
        if self.created_at is None:
            self.created_at = time.strftime('%Y-%m-%d %H:%M:%S')
    
    def to_json(self) -> Dict[str, Any]:
        """Convert document to JSON-serializable format"""
        data = asdict(self)
        if self.intent:
            data['intent'] = self.intent.value if hasattr(self.intent, 'value') else str(self.intent)
        return data
    
    @classmethod
    def from_json(cls, data: Dict[str, Any]) -> 'RAGDocument':
        """Create document from JSON data"""
        # Handle intent conversion
        intent_str = data.get('intent')
        intent = None
        if intent_str and UserIntent:
            try:
                intent = UserIntent(intent_str)
            except (ValueError, AttributeError):
                intent = None
        
        return cls(
            id=data['id'],
            title=data['title'],
            content=data['content'],
            category=data['category'],
            intent=intent,
            metadata=data.get('metadata', {}),
            embedding=data.get('embedding'),
            created_at=data.get('created_at')
        )
    
    def get_searchable_text(self) -> str:
        """Get text used for embedding and search"""
        searchable = f"{self.title}\n{self.content}"
        
        # Add metadata as searchable text
        if self.metadata:
            tags = self.metadata.get('tags', [])
            if tags:
                searchable += f"\nTags: {', '.join(tags)}"
        
        return searchable
    
    def get_display_preview(self, max_length: int = 150) -> str:
        """Get short preview for display"""
        if len(self.content) <= max_length:
            return self.content
        return self.content[:max_length] + "..."

class EmbeddingEngine:
    """Handles text embeddings with multiple backend support"""

    def __init__(self,
                 model_name: str = "google/embeddinggemma-300m",
                 cache_embeddings: bool = True,
                 truncate_dim: Optional[int] = None):
        """
        Initialize embedding engine.

        Args:
            model_name: Embedding model (default: google/embeddinggemma-300m)
            cache_embeddings: Enable caching for faster repeated lookups
            truncate_dim: Truncate embeddings to this dimension (EmbeddingGemma only: 768/512/256/128)
        """
        self.model_name = model_name
        self.cache_embeddings = cache_embeddings
        self.truncate_dim = truncate_dim
        self.model = None
        self.embedding_cache = {} if cache_embeddings else None
        self.embedding_dim = None
        self.logger = logging.getLogger(__name__)

        # Initialize with fallback strategy
        self._initialize_model()
    
    def _initialize_model(self) -> bool:
        """Initialize embedding model with fallback"""
        if SENTENCE_TRANSFORMERS_AVAILABLE:
            try:
                print(f"🔄 Loading embedding model: {self.model_name}")
                self.model = SentenceTransformer(self.model_name)

                # Test model and get dimensions
                test_embedding = self.model.encode(["test"], show_progress_bar=False)
                self.embedding_dim = len(test_embedding[0])

                # Validate truncate_dim for EmbeddingGemma
                if self.truncate_dim:
                    if "embeddinggemma" in self.model_name.lower():
                        valid_dims = [768, 512, 256, 128]
                        if self.truncate_dim not in valid_dims:
                            print(f"⚠️ Invalid truncate_dim {self.truncate_dim}. Must be one of {valid_dims}")
                            self.truncate_dim = None
                        else:
                            print(f"✅ Matryoshka truncation: {self.embedding_dim}D → {self.truncate_dim}D")
                    else:
                        print(f"⚠️ Truncation only supported for EmbeddingGemma")
                        self.truncate_dim = None

                print(f"✅ Embedding model loaded ({self.embedding_dim}D)")
                return True

            except Exception as e:
                print(f"❌ Failed to load {self.model_name}: {e}")

                # Try fallback models (EmbeddingGemma → BGE → MiniLM → MPNet)
                fallback_models = [
                    "BAAI/bge-small-en-v1.5",
                    "all-MiniLM-L6-v2",
                    "all-mpnet-base-v2"
                ]

                for fallback in fallback_models:
                    try:
                        print(f"🔄 Trying fallback model: {fallback}")
                        self.model = SentenceTransformer(fallback)
                        self.model_name = fallback
                        self.truncate_dim = None  # Disable truncation for fallback models

                        test_embedding = self.model.encode(["test"], show_progress_bar=False)
                        self.embedding_dim = len(test_embedding[0])
                        print(f"✅ Fallback model loaded ({self.embedding_dim}D)")
                        return True

                    except Exception as fallback_error:
                        print(f"❌ Fallback {fallback} failed: {fallback_error}")
                        continue

        # Ultimate fallback - mock embeddings
        print("⚠️  Using mock embeddings (limited functionality)")
        self.embedding_dim = 768  # Match EmbeddingGemma default
        self.truncate_dim = None
        return False
    
    def embed_texts(self, texts: List[str]) -> List[List[float]]:
        """Generate embeddings for texts"""
        if not texts:
            return []
        
        embeddings = []
        
        for text in texts:
            # Check cache first
            if self.embedding_cache:
                cache_key = hashlib.md5(text.encode()).hexdigest()
                if cache_key in self.embedding_cache:
                    embeddings.append(self.embedding_cache[cache_key])
                    continue
            
            # Generate embedding
            if self.model:
                try:
                    embedding_vec = self.model.encode([text], show_progress_bar=False)[0]

                    # Apply Matryoshka truncation if specified
                    if self.truncate_dim and len(embedding_vec) > self.truncate_dim:
                        embedding_vec = embedding_vec[:self.truncate_dim]

                    embedding = embedding_vec.tolist()
                except Exception as e:
                    self.logger.warning(f"Embedding generation failed: {e}")
                    embedding = self._mock_embedding(text)
            else:
                embedding = self._mock_embedding(text)
            
            # Cache result
            if self.embedding_cache:
                self.embedding_cache[cache_key] = embedding
            
            embeddings.append(embedding)
        
        return embeddings
    
    def _mock_embedding(self, text: str) -> List[float]:
        """Generate mock embedding based on text hash"""
        # Create deterministic but varied embeddings based on text
        hash_obj = hashlib.md5(text.encode())
        seed = int(hash_obj.hexdigest()[:8], 16)
        np.random.seed(seed % (2**32))
        
        # Generate normalized random vector
        embedding = np.random.randn(self.embedding_dim).astype(float)
        embedding = embedding / np.linalg.norm(embedding)
        
        return embedding.tolist()
    
    def similarity_search(self, query: str, documents: List[RAGDocument], top_k: int = 5) -> List[Dict[str, Any]]:
        """Find most similar documents to query"""
        if not documents:
            return []
        
        # Generate query embedding
        query_embedding = self.embed_texts([query])[0]
        
        # Generate document embeddings if not cached
        doc_texts = [doc.get_searchable_text() for doc in documents]
        doc_embeddings = []
        
        for i, doc in enumerate(documents):
            if doc.embedding:
                doc_embeddings.append(doc.embedding)
            else:
                # Generate and cache embedding
                embedding = self.embed_texts([doc_texts[i]])[0]
                doc.embedding = embedding
                doc_embeddings.append(embedding)
        
        # Calculate similarities
        similarities = []
        query_vec = np.array(query_embedding)
        
        for i, doc_vec in enumerate(doc_embeddings):
            doc_array = np.array(doc_vec)
            # Cosine similarity
            similarity = np.dot(query_vec, doc_array) / (
                np.linalg.norm(query_vec) * np.linalg.norm(doc_array)
            )
            similarities.append({
                'document': documents[i],
                'similarity': float(similarity),
                'index': i
            })
        
        # Sort by similarity and return top_k
        similarities.sort(key=lambda x: x['similarity'], reverse=True)
        return similarities[:top_k]

class KnowledgeBase:
    """Manages RAG document storage and retrieval"""
    
    def __init__(self, knowledge_base_path: str):
        self.kb_path = Path(knowledge_base_path)
        self.documents = []
        self.version = "1.0"
        self.metadata = {
            "created_at": time.strftime('%Y-%m-%d %H:%M:%S'),
            "total_documents": 0,
            "categories": {},
            "intents": {}
        }
        
        # Create directory if needed
        self.kb_path.parent.mkdir(parents=True, exist_ok=True)
    
    def add_document(self, document: RAGDocument) -> None:
        """Add document to knowledge base"""
        # Check for duplicate IDs
        existing_ids = {doc.id for doc in self.documents}
        if document.id in existing_ids:
            print(f"⚠️  Document {document.id} already exists - skipping")
            return
        
        self.documents.append(document)
        self._update_metadata()
        print(f"📝 Added document: {document.title} ({document.id})")
    
    def remove_document(self, document_id: str) -> bool:
        """Remove document by ID"""
        original_count = len(self.documents)
        self.documents = [doc for doc in self.documents if doc.id != document_id]
        
        if len(self.documents) < original_count:
            self._update_metadata()
            print(f"🗑️  Removed document: {document_id}")
            return True
        
        print(f"⚠️  Document {document_id} not found")
        return False
    
    def get_document_by_id(self, document_id: str) -> Optional[RAGDocument]:
        """Get document by ID"""
        for doc in self.documents:
            if doc.id == document_id:
                return doc
        return None
    
    def get_documents_by_category(self, category: str) -> List[RAGDocument]:
        """Get documents by category"""
        return [doc for doc in self.documents if doc.category == category]
    
    def get_documents_by_intent(self, intent: UserIntent) -> List[RAGDocument]:
        """Get documents by user intent"""
        return [doc for doc in self.documents if doc.intent == intent]
    
    def search_documents(self, query: str, category: str = None, intent: UserIntent = None) -> List[RAGDocument]:
        """Search documents by text query with optional filters"""
        filtered_docs = self.documents
        
        # Apply filters
        if category:
            filtered_docs = [doc for doc in filtered_docs if doc.category == category]
        
        if intent:
            filtered_docs = [doc for doc in filtered_docs if doc.intent == intent]
        
        # Simple text search
        query_lower = query.lower()
        results = []
        
        for doc in filtered_docs:
            searchable_text = doc.get_searchable_text().lower()
            if query_lower in searchable_text:
                results.append(doc)
        
        return results
    
    def _update_metadata(self) -> None:
        """Update knowledge base metadata"""
        self.metadata["total_documents"] = len(self.documents)
        self.metadata["updated_at"] = time.strftime('%Y-%m-%d %H:%M:%S')
        
        # Count categories and intents
        categories = {}
        intents = {}
        
        for doc in self.documents:
            categories[doc.category] = categories.get(doc.category, 0) + 1
            if doc.intent:
                intent_str = doc.intent.value if hasattr(doc.intent, 'value') else str(doc.intent)
                intents[intent_str] = intents.get(intent_str, 0) + 1
        
        self.metadata["categories"] = categories
        self.metadata["intents"] = intents
    
    def save(self) -> bool:
        """Save knowledge base to file"""
        try:
            data = {
                "version": self.version,
                "metadata": self.metadata,
                "documents": [doc.to_json() for doc in self.documents]
            }
            
            with open(self.kb_path, 'w', encoding='utf-8') as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
            
            print(f"💾 Saved {len(self.documents)} documents to {self.kb_path}")
            return True
            
        except Exception as e:
            print(f"❌ Failed to save knowledge base: {e}")
            return False
    
    def load(self) -> bool:
        """Load knowledge base from file"""
        if not self.kb_path.exists():
            print(f"📝 Creating new knowledge base: {self.kb_path}")
            return True
        
        try:
            with open(self.kb_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
            
            self.version = data.get("version", "1.0")
            self.metadata = data.get("metadata", {})
            
            # Load documents
            self.documents = []
            for doc_data in data.get("documents", []):
                try:
                    doc = RAGDocument.from_json(doc_data)
                    self.documents.append(doc)
                except Exception as e:
                    print(f"⚠️  Failed to load document {doc_data.get('id', 'unknown')}: {e}")
            
            print(f"📖 Loaded {len(self.documents)} documents from {self.kb_path}")
            return True
            
        except Exception as e:
            print(f"❌ Failed to load knowledge base: {e}")
            return False
    
    def get_stats(self) -> Dict[str, Any]:
        """Get knowledge base statistics"""
        self._update_metadata()
        return {
            "total_documents": len(self.documents),
            "categories": self.metadata.get("categories", {}),
            "intents": self.metadata.get("intents", {}),
            "file_size": self.kb_path.stat().st_size if self.kb_path.exists() else 0,
            "last_updated": self.metadata.get("updated_at", "Never")
        }

class M1K3RAGEngine:
    """Main RAG engine for M1K3 system"""

    def __init__(self,
                 knowledge_base_path: str = "knowledge/m1k3_knowledge_base.json",
                 embedding_model: str = "google/embeddinggemma-300m",
                 lazy_load: bool = True,
                 truncate_dim: Optional[int] = None):
        """
        Initialize M1K3 RAG Engine.

        Args:
            knowledge_base_path: Path to knowledge base JSON file
            embedding_model: Embedding model (default: google/embeddinggemma-300m)
            lazy_load: Lazy-load embedding model when first needed
            truncate_dim: Truncate embeddings to this dimension (EmbeddingGemma: 768/512/256/128)
        """
        self.knowledge_base_path = knowledge_base_path
        self.embedding_model_name = embedding_model
        self.lazy_load = lazy_load
        self.truncate_dim = truncate_dim

        # Core components
        self.knowledge_base = None
        self.embedding_engine = None
        self.intent_engine = None

        # Configuration
        self.max_context_documents = 3
        self.similarity_threshold = 0.1
        self.logger = logging.getLogger(__name__)

        # Initialize components
        self._initialize()
    
    def _initialize(self) -> None:
        """Initialize RAG engine components"""
        print("🚀 Initializing M1K3 RAG Engine...")
        
        # Always load knowledge base
        self.knowledge_base = KnowledgeBase(self.knowledge_base_path)
        self.knowledge_base.load()
        
        # Initialize intent engine if available
        if IntentClassificationEngine:
            try:
                self.intent_engine = IntentClassificationEngine()
                print("✅ Intent classification engine loaded")
            except Exception as e:
                print(f"⚠️  Intent engine initialization failed: {e}")
        
        # Load embedding engine unless lazy loading
        if not self.lazy_load:
            self._get_embedding_engine()
        
        print("🎯 M1K3 RAG Engine ready!")
    
    def _get_embedding_engine(self) -> EmbeddingEngine:
        """Get embedding engine (lazy loading)"""
        if not self.embedding_engine:
            self.embedding_engine = EmbeddingEngine(
                model_name=self.embedding_model_name,
                cache_embeddings=True,
                truncate_dim=self.truncate_dim
            )
        return self.embedding_engine
    
    def retrieve_context(self, 
                        query: str, 
                        intent_classification: Optional[IntentClassification] = None,
                        max_documents: int = None) -> List[Dict[str, Any]]:
        """Retrieve relevant context for query"""
        if max_documents is None:
            max_documents = self.max_context_documents
        
        # Get embedding engine
        embedding_engine = self._get_embedding_engine()
        
        # Filter documents by intent if available
        if intent_classification and intent_classification.intent:
            candidate_docs = self.knowledge_base.get_documents_by_intent(intent_classification.intent)
            
            # If no intent-specific documents, fall back to category
            if not candidate_docs and hasattr(intent_classification.intent, 'value'):
                intent_value = intent_classification.intent.value
                if 'math' in intent_value:
                    candidate_docs = self.knowledge_base.get_documents_by_category('mathematical_calculation')
                elif 'debug' in intent_value or 'code' in intent_value:
                    candidate_docs = self.knowledge_base.get_documents_by_category('code_debugging')
                elif 'explain' in intent_value:
                    candidate_docs = self.knowledge_base.get_documents_by_category('explanation_request')
                else:
                    candidate_docs = self.knowledge_base.documents
        else:
            candidate_docs = self.knowledge_base.documents
        
        # If no candidates, use all documents
        if not candidate_docs:
            candidate_docs = self.knowledge_base.documents
        
        # Perform similarity search
        if candidate_docs:
            results = embedding_engine.similarity_search(query, candidate_docs, top_k=max_documents)
            
            # Filter by similarity threshold
            results = [r for r in results if r['similarity'] >= self.similarity_threshold]
            
            self.logger.debug(f"Retrieved {len(results)} documents for query: {query[:50]}...")
            return results
        
        return []
    
    def format_rag_context(self, query: str, retrieved_docs: List[Dict[str, Any]]) -> str:
        """Format retrieved documents as context for the model"""
        if not retrieved_docs:
            return ""
        
        context_parts = ["Here are some relevant documents that might help answer your question:\n"]
        
        for i, result in enumerate(retrieved_docs, 1):
            doc = result['document']
            similarity = result['similarity']
            
            context_parts.append(f"Document {i} - {doc.title} (relevance: {similarity:.2f}):")
            context_parts.append(doc.content)
            context_parts.append("")  # Empty line between documents
        
        context_parts.append(f"User Question: {query}")
        context_parts.append("Please provide a helpful response based on the above information.")
        
        return "\n".join(context_parts)
    
    def enhance_prompt(self, original_prompt: str, intent_classification: Optional[IntentClassification] = None) -> str:
        """Enhance prompt with RAG context"""
        try:
            # Retrieve relevant context
            retrieved_docs = self.retrieve_context(original_prompt, intent_classification)
            
            if retrieved_docs:
                # Format context
                rag_context = self.format_rag_context(original_prompt, retrieved_docs)
                return rag_context
            else:
                # No relevant documents found
                return original_prompt
                
        except Exception as e:
            self.logger.error(f"RAG enhancement failed: {e}")
            return original_prompt
    
    def add_knowledge(self, title: str, content: str, category: str, 
                     intent: Optional[UserIntent] = None, metadata: Dict[str, Any] = None) -> str:
        """Add new knowledge to the system"""
        # Generate unique ID
        doc_id = f"{category}_{int(time.time())}"
        
        # Create document
        doc = RAGDocument(
            id=doc_id,
            title=title,
            content=content,
            category=category,
            intent=intent,
            metadata=metadata or {}
        )
        
        # Add to knowledge base
        self.knowledge_base.add_document(doc)
        
        # Save knowledge base
        self.knowledge_base.save()
        
        return doc_id
    
    def get_stats(self) -> Dict[str, Any]:
        """Get RAG engine statistics"""
        kb_stats = self.knowledge_base.get_stats()
        
        return {
            "knowledge_base": kb_stats,
            "embedding_model": self.embedding_model_name,
            "embedding_cache_size": len(self.embedding_engine.embedding_cache) if self.embedding_engine and self.embedding_engine.embedding_cache else 0,
            "configuration": {
                "max_context_documents": self.max_context_documents,
                "similarity_threshold": self.similarity_threshold,
                "lazy_load": self.lazy_load
            }
        }

# Testing functionality
def test_rag_engine():
    """Quick test of RAG engine functionality"""
    print("🧪 Testing M1K3 RAG Engine")
    print("=" * 50)
    
    # Create test knowledge base
    temp_kb_path = "test_knowledge_base.json"
    
    try:
        # Initialize engine
        rag_engine = M1K3RAGEngine(knowledge_base_path=temp_kb_path, lazy_load=False)
        
        # Add test documents
        print("\n📝 Adding test documents...")
        
        rag_engine.add_knowledge(
            title="Python Variables",
            content="In Python, you create variables by assigning values: x = 5. Variables don't need to be declared with a specific type.",
            category="code_explanation",
            intent=UserIntent.EXPLANATION_REQUEST if UserIntent else None,
            metadata={"tags": ["python", "variables", "basics"], "difficulty": "beginner"}
        )
        
        rag_engine.add_knowledge(
            title="Basic Math",
            content="To add two numbers, use the + operator. For example: 5 + 3 = 8. Multiplication uses * like 5 * 3 = 15.",
            category="mathematical_calculation", 
            intent=UserIntent.MATHEMATICAL_CALCULATION if UserIntent else None,
            metadata={"tags": ["math", "arithmetic", "operators"], "difficulty": "beginner"}
        )
        
        # Test retrieval
        print("\n🔍 Testing retrieval...")
        
        test_queries = [
            "How do I create variables in Python?",
            "What is 5 times 3?",
            "Explain multiplication"
        ]
        
        for query in test_queries:
            print(f"\nQuery: {query}")
            
            # Get intent classification if available
            intent_classification = None
            if rag_engine.intent_engine:
                intent_classification = rag_engine.intent_engine.classify_intent(query)
                print(f"Intent: {intent_classification.intent.value if intent_classification.intent else 'Unknown'}")
            
            # Retrieve context
            results = rag_engine.retrieve_context(query, intent_classification)
            
            if results:
                for result in results:
                    doc = result['document']
                    similarity = result['similarity']
                    print(f"  📄 {doc.title} (similarity: {similarity:.3f})")
                    print(f"     {doc.get_display_preview()}")
            else:
                print("  No relevant documents found")
        
        # Show stats
        print(f"\n📊 RAG Engine Stats:")
        stats = rag_engine.get_stats()
        print(f"  Total documents: {stats['knowledge_base']['total_documents']}")
        print(f"  Categories: {list(stats['knowledge_base']['categories'].keys())}")
        print(f"  Embedding model: {stats['embedding_model']}")
        
        print("\n✅ RAG engine test completed successfully!")
        
    except Exception as e:
        print(f"❌ RAG engine test failed: {e}")
        import traceback
        traceback.print_exc()
    
    finally:
        # Clean up test file
        import os
        if os.path.exists(temp_kb_path):
            os.remove(temp_kb_path)
            print(f"🗑️  Cleaned up test file: {temp_kb_path}")

if __name__ == "__main__":
    test_rag_engine()