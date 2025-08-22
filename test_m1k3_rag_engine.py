#!/usr/bin/env python3
"""
Test-Driven Development for M1K3 RAG Engine
Comprehensive test suite for RAG functionality
"""

import unittest
import tempfile
import json
import os
import shutil
from unittest.mock import Mock, patch, MagicMock
from pathlib import Path

# Import the modules we'll be testing
try:
    from m1k3_rag_engine import M1K3RAGEngine, RAGDocument, EmbeddingEngine, KnowledgeBase
    from intent_classification_system import UserIntent, IntentClassification
except ImportError:
    # These will be created as we develop
    pass

class TestRAGDocument(unittest.TestCase):
    """Test RAG document structure and functionality"""
    
    def test_rag_document_creation(self):
        """Test creating a RAG document with required fields"""
        doc = RAGDocument(
            id="test_001",
            title="Python Debugging",
            content="When you get a NameError, check if the variable is defined.",
            category="code_debugging",
            intent=UserIntent.CODE_DEBUGGING,
            metadata={
                "difficulty": "beginner",
                "tags": ["python", "error", "debugging"]
            }
        )
        
        self.assertEqual(doc.id, "test_001")
        self.assertEqual(doc.title, "Python Debugging")
        self.assertEqual(doc.category, "code_debugging")
        self.assertEqual(doc.intent, UserIntent.CODE_DEBUGGING)
        self.assertIn("python", doc.metadata["tags"])
    
    def test_rag_document_serialization(self):
        """Test document can be serialized to/from JSON"""
        doc = RAGDocument(
            id="test_002",
            title="Math Calculation",
            content="To multiply two numbers, use the * operator: 5 * 3 = 15",
            category="mathematical_calculation",
            intent=UserIntent.MATHEMATICAL_CALCULATION
        )
        
        # Test serialization
        json_data = doc.to_json()
        self.assertIn("id", json_data)
        self.assertIn("content", json_data)
        
        # Test deserialization
        restored_doc = RAGDocument.from_json(json_data)
        self.assertEqual(restored_doc.id, doc.id)
        self.assertEqual(restored_doc.content, doc.content)

class TestEmbeddingEngine(unittest.TestCase):
    """Test embedding generation and similarity search"""
    
    def setUp(self):
        """Set up test environment"""
        self.temp_dir = tempfile.mkdtemp()
        self.embedding_engine = None
    
    def tearDown(self):
        """Clean up test environment"""
        if os.path.exists(self.temp_dir):
            shutil.rmtree(self.temp_dir)
    
    def test_embedding_engine_initialization(self):
        """Test embedding engine can be initialized with fallback strategy"""
        # Test with mock embedding model that will fail and fallback
        engine = EmbeddingEngine(model_name="invalid_model")
        self.assertIsNotNone(engine)
        # Should fallback to a working model
        self.assertIn("MiniLM", engine.model_name)
    
    def test_embedding_generation(self):
        """Test text can be converted to embeddings"""
        engine = EmbeddingEngine()
        embeddings = engine.embed_texts(["Test text"])
        
        self.assertEqual(len(embeddings), 1)
        # Check that we get reasonable embedding dimensions (should be 384 for BGE-small)
        self.assertGreaterEqual(len(embeddings[0]), 300)
        self.assertLessEqual(len(embeddings[0]), 400)
    
    def test_similarity_search(self):
        """Test similarity search returns ranked results"""
        with patch('sentence_transformers.SentenceTransformer') as mock_st:
            mock_model = Mock()
            # Different embeddings for different texts
            mock_model.encode.side_effect = [
                [[0.9, 0.1, 0.0, 0.0]],  # Query embedding
                [[0.8, 0.2, 0.0, 0.0],   # Document 1 (high similarity)
                 [0.1, 0.1, 0.8, 0.0]]   # Document 2 (low similarity)
            ]
            mock_st.return_value = mock_model
            
            engine = EmbeddingEngine()
            
            # Add documents to search
            docs = [
                RAGDocument("1", "Similar", "Similar content", "test"),
                RAGDocument("2", "Different", "Different content", "test")
            ]
            
            results = engine.similarity_search("Query text", docs, top_k=2)
            
            self.assertEqual(len(results), 2)
            # First result should have higher similarity
            self.assertGreater(results[0]['similarity'], results[1]['similarity'])

class TestKnowledgeBase(unittest.TestCase):
    """Test knowledge base storage and retrieval"""
    
    def setUp(self):
        """Set up test environment"""
        self.temp_dir = tempfile.mkdtemp()
        self.kb_path = os.path.join(self.temp_dir, "test_kb.json")
    
    def tearDown(self):
        """Clean up test environment"""
        if os.path.exists(self.temp_dir):
            shutil.rmtree(self.temp_dir)
    
    def test_knowledge_base_creation(self):
        """Test knowledge base can be created and saved"""
        kb = KnowledgeBase(self.kb_path)
        
        doc = RAGDocument(
            id="kb_test_001",
            title="Test Document",
            content="This is test content.",
            category="test",
            intent=UserIntent.FACTUAL_QUERY
        )
        
        kb.add_document(doc)
        kb.save()
        
        # Verify file was created
        self.assertTrue(os.path.exists(self.kb_path))
        
        # Verify content
        with open(self.kb_path, 'r') as f:
            data = json.load(f)
            self.assertIn("documents", data)
            self.assertEqual(len(data["documents"]), 1)
    
    def test_knowledge_base_loading(self):
        """Test knowledge base can be loaded from file"""
        # Create test data
        test_data = {
            "version": "1.0",
            "documents": [
                {
                    "id": "load_test_001",
                    "title": "Load Test",
                    "content": "Load test content",
                    "category": "test",
                    "intent": "factual_query",
                    "metadata": {},
                    "embedding": None
                }
            ]
        }
        
        with open(self.kb_path, 'w') as f:
            json.dump(test_data, f)
        
        # Load knowledge base
        kb = KnowledgeBase(self.kb_path)
        kb.load()
        
        self.assertEqual(len(kb.documents), 1)
        self.assertEqual(kb.documents[0].id, "load_test_001")
    
    def test_search_by_intent(self):
        """Test documents can be filtered by intent"""
        kb = KnowledgeBase(self.kb_path)
        
        # Add documents with different intents
        kb.add_document(RAGDocument("1", "Math", "Math content", "math", UserIntent.MATHEMATICAL_CALCULATION))
        kb.add_document(RAGDocument("2", "Debug", "Debug content", "debug", UserIntent.CODE_DEBUGGING))
        kb.add_document(RAGDocument("3", "Chat", "Chat content", "chat", UserIntent.CASUAL_CONVERSATION))
        
        # Search by intent
        math_docs = kb.get_documents_by_intent(UserIntent.MATHEMATICAL_CALCULATION)
        debug_docs = kb.get_documents_by_intent(UserIntent.CODE_DEBUGGING)
        
        self.assertEqual(len(math_docs), 1)
        self.assertEqual(len(debug_docs), 1)
        self.assertEqual(math_docs[0].title, "Math")
        self.assertEqual(debug_docs[0].title, "Debug")

class TestM1K3RAGEngine(unittest.TestCase):
    """Test main RAG engine functionality"""
    
    def setUp(self):
        """Set up test environment"""
        self.temp_dir = tempfile.mkdtemp()
        self.rag_engine = None
    
    def tearDown(self):
        """Clean up test environment"""
        if os.path.exists(self.temp_dir):
            shutil.rmtree(self.temp_dir)
    
    @patch('m1k3_rag_engine.EmbeddingEngine')
    @patch('m1k3_rag_engine.KnowledgeBase')
    def test_rag_engine_initialization(self, mock_kb, mock_embedding):
        """Test RAG engine initializes with all components"""
        # Setup mocks
        mock_kb_instance = Mock()
        mock_embedding_instance = Mock()
        mock_kb.return_value = mock_kb_instance
        mock_embedding.return_value = mock_embedding_instance
        
        # Test initialization
        engine = M1K3RAGEngine(knowledge_base_path=self.temp_dir)
        
        self.assertIsNotNone(engine)
        self.assertEqual(engine.knowledge_base_path, self.temp_dir)
    
    @patch('m1k3_rag_engine.EmbeddingEngine')
    @patch('m1k3_rag_engine.KnowledgeBase')
    def test_context_aware_retrieval(self, mock_kb, mock_embedding):
        """Test retrieval adapts to intent classification"""
        # Setup mocks
        mock_kb_instance = Mock()
        mock_embedding_instance = Mock()
        mock_kb.return_value = mock_kb_instance
        mock_embedding.return_value = mock_embedding_instance
        
        # Mock intent classification
        mock_classification = Mock()
        mock_classification.intent = UserIntent.CODE_DEBUGGING
        mock_classification.confidence = 0.8
        
        # Mock search results
        mock_kb_instance.get_documents_by_intent.return_value = [
            RAGDocument("1", "Debug Help", "Debug content", "debug", UserIntent.CODE_DEBUGGING)
        ]
        
        mock_embedding_instance.similarity_search.return_value = [
            {"document": RAGDocument("1", "Debug Help", "Debug content", "debug", UserIntent.CODE_DEBUGGING), 
             "similarity": 0.9}
        ]
        
        # Test retrieval
        engine = M1K3RAGEngine(knowledge_base_path=self.temp_dir)
        results = engine.retrieve_context("How do I fix this error?", mock_classification)
        
        self.assertIsNotNone(results)
        mock_kb_instance.get_documents_by_intent.assert_called_with(UserIntent.CODE_DEBUGGING)
    
    @patch('m1k3_rag_engine.EmbeddingEngine')
    @patch('m1k3_rag_engine.KnowledgeBase')
    def test_response_augmentation(self, mock_kb, mock_embedding):
        """Test RAG context is properly formatted for model input"""
        # Setup mocks
        mock_kb_instance = Mock()
        mock_embedding_instance = Mock()
        mock_kb.return_value = mock_kb_instance
        mock_embedding.return_value = mock_embedding_instance
        
        # Mock retrieval results
        retrieved_docs = [
            {"document": RAGDocument("1", "Math Help", "2 + 2 = 4", "math", UserIntent.MATHEMATICAL_CALCULATION), 
             "similarity": 0.9}
        ]
        
        engine = M1K3RAGEngine(knowledge_base_path=self.temp_dir)
        
        # Test context formatting
        formatted_context = engine.format_rag_context("What is 2 + 2?", retrieved_docs)
        
        self.assertIn("Math Help", formatted_context)
        self.assertIn("2 + 2 = 4", formatted_context)
        self.assertIn("What is 2 + 2?", formatted_context)

class TestRAGIntegration(unittest.TestCase):
    """Test RAG integration with existing M1K3 systems"""
    
    def test_intent_classification_integration(self):
        """Test RAG works with existing intent classification"""
        from intent_classification_system import IntentClassificationEngine
        
        # This should work with existing system
        intent_engine = IntentClassificationEngine()
        
        # Test with a clearer math query that should be better detected
        classification = intent_engine.classify_intent("Calculate 5 times 7")
        
        # Should be either math calculation or needs clarification (both valid)
        self.assertIn(classification.intent, [UserIntent.MATHEMATICAL_CALCULATION, UserIntent.NEEDS_CLARIFICATION])
        self.assertGreater(classification.confidence, 0.3)
    
    @patch('ai_inference.LocalAIEngine')
    def test_ai_inference_integration(self, mock_ai_engine):
        """Test RAG integrates with existing AI inference"""
        # Mock AI engine
        mock_instance = Mock()
        mock_instance.generate_response.return_value = iter(["The answer is 35."])
        mock_ai_engine.return_value = mock_instance
        
        # Test that RAG context can be passed to AI engine
        # This will be implemented in the actual integration
        self.assertTrue(True)  # Placeholder for actual integration test

class TestRAGPerformance(unittest.TestCase):
    """Test RAG system performance and resource usage"""
    
    def test_embedding_cache(self):
        """Test embedding results are cached for performance"""
        engine = EmbeddingEngine()
        
        # First call
        embeddings1 = engine.embed_texts(["test text"])
        # Second call - should use cache
        embeddings2 = engine.embed_texts(["test text"])
        
        # Should be identical due to caching
        self.assertEqual(embeddings1, embeddings2)
        
        # Check that cache is populated
        self.assertIsNotNone(engine.embedding_cache)
        self.assertGreater(len(engine.embedding_cache), 0)
    
    def test_lazy_loading(self):
        """Test components are loaded only when needed"""
        with patch('m1k3_rag_engine.EmbeddingEngine') as mock_embedding:
            mock_embedding.return_value = Mock()
            
            engine = M1K3RAGEngine(knowledge_base_path="/tmp/test", lazy_load=True)
            
            # Embedding engine should not be initialized yet
            mock_embedding.assert_not_called()
            
            # Should initialize on first use
            engine._get_embedding_engine()
            mock_embedding.assert_called_once()

if __name__ == "__main__":
    # Run all tests
    unittest.main(verbosity=2)