#!/usr/bin/env python3
"""
M1K3 RAG Integration Validation Tests
Tests the full integration chain: CLI -> TUI -> RAG -> AI -> Response
"""

import unittest
import subprocess
import tempfile
import os
import json
import time
from unittest.mock import patch, Mock, MagicMock

class TestRAGCLIIntegration(unittest.TestCase):
    """Test RAG system integration with CLI"""
    
    def test_cli_rag_flag_recognition(self):
        """Test that CLI recognizes --rag flag"""
        # Test that the script doesn't crash with --rag flag
        result = subprocess.run([
            'python', 'm1k3.py', '--help'
        ], capture_output=True, text=True, timeout=30)
        
        self.assertEqual(result.returncode, 0, "Help should work")
        self.assertIn('--rag', result.stdout, "Should show --rag option in help")
    
    def test_cli_query_mode_with_rag(self):
        """Test single query mode with RAG enabled"""
        if not os.path.exists('knowledge/comprehensive_knowledge_base.json'):
            self.skipTest("Knowledge base not found")
        
        # Test with a simple query that should complete quickly
        result = subprocess.run([
            'python', 'm1k3.py',
            '--query', 'What is 2+2?',
            '--rag',
            '--no-voice'
        ], capture_output=True, text=True, timeout=120)  # 2 minute timeout
        
        # Should not crash
        self.assertNotEqual(result.returncode, 1, f"CLI should not crash. Error: {result.stderr}")
        
        # Should contain some output
        output = result.stdout + result.stderr
        self.assertIn('RAG', output, "Should mention RAG system")


class TestRAGEngineValidation(unittest.TestCase):
    """Test RAG engine functionality in isolation"""
    
    def setUp(self):
        """Set up test environment"""
        self.kb_path = 'knowledge/comprehensive_knowledge_base.json'
        if not os.path.exists(self.kb_path):
            self.skipTest("Knowledge base not found")
    
    def test_knowledge_base_loading_speed(self):
        """Test knowledge base loads within reasonable time"""
        from src.rag.m1k3_rag_engine import M1K3RAGEngine
        
        start_time = time.time()
        engine = M1K3RAGEngine(knowledge_base_path=self.kb_path)
        engine.load_knowledge_base()
        load_time = time.time() - start_time
        
        self.assertLess(load_time, 30, f"Knowledge base should load within 30s, took {load_time:.2f}s")
        self.assertGreater(len(engine.knowledge_base.documents), 1000, "Should load 1000+ documents")
    
    def test_embedding_engine_functionality(self):
        """Test embedding engine can process queries"""
        from src.rag.m1k3_rag_engine import EmbeddingEngine, RAGDocument
        from src.utils.intent_classification_system import UserIntent
        
        engine = EmbeddingEngine()
        
        # Test document embedding
        test_docs = [
            RAGDocument("test1", "WiFi Setup", "How to configure your home WiFi network", 
                       "wifi_networking", UserIntent.EXPLANATION_REQUEST),
            RAGDocument("test2", "iPhone Battery", "Tips for optimizing iPhone battery life", 
                       "device_technology", UserIntent.EXPLANATION_REQUEST)
        ]
        
        # Test search functionality
        results = engine.similarity_search("WiFi problems", test_docs, top_k=2)
        
        self.assertEqual(len(results), 2, "Should return requested number of results")
        self.assertTrue(all('similarity' in result for result in results), "Results should have similarity scores")
        self.assertTrue(all('document' in result for result in results), "Results should have documents")
    
    def test_intent_classification_integration(self):
        """Test intent classification works with various query types"""
        from src.utils.intent_classification_system import IntentClassificationEngine, UserIntent
        
        engine = IntentClassificationEngine()
        
        test_cases = [
            ("How do I fix my WiFi?", [UserIntent.CODE_DEBUGGING, UserIntent.EXPLANATION_REQUEST]),
            ("What is 15 + 27?", [UserIntent.MATHEMATICAL_CALCULATION]),
            ("Tell me about Python programming", [UserIntent.EXPLANATION_REQUEST, UserIntent.FACTUAL_QUERY]),
            ("Write a story about space", [UserIntent.CREATIVE_WRITING]),
            ("Hello there!", [UserIntent.CASUAL_CONVERSATION])
        ]
        
        for query, acceptable_intents in test_cases:
            with self.subTest(query=query):
                classification = engine.classify_intent(query)
                
                self.assertIsNotNone(classification, f"Should classify: {query}")
                self.assertGreater(classification.confidence, 0.2, f"Should have reasonable confidence for: {query}")
                
                # Intent should be one of the acceptable options
                # (allowing flexibility in classification)
                self.assertIn(classification.intent, acceptable_intents + [UserIntent.NEEDS_CLARIFICATION], 
                            f"Intent {classification.intent} not acceptable for: {query}")


class TestRAGResponseQuality(unittest.TestCase):
    """Test quality of RAG-enhanced responses"""
    
    def setUp(self):
        """Set up RAG system for response quality tests"""
        self.kb_path = 'knowledge/comprehensive_knowledge_base.json'
        if not os.path.exists(self.kb_path):
            self.skipTest("Knowledge base not found")
    
    def test_context_retrieval_relevance(self):
        """Test that retrieved context is relevant to queries"""
        from src.rag.m1k3_rag_engine import M1K3RAGEngine
        from src.utils.intent_classification_system import IntentClassificationEngine
        
        rag_engine = M1K3RAGEngine(knowledge_base_path=self.kb_path)
        rag_engine.load_knowledge_base()
        intent_engine = IntentClassificationEngine()
        
        test_queries = [
            ("How do I speed up my WiFi connection?", ["wifi", "network", "speed", "internet"]),
            ("My iPhone battery drains quickly", ["battery", "iPhone", "power", "drain"]),
            ("How to create secure passwords?", ["password", "security", "secure", "authentication"]),
            ("Best study techniques for math", ["study", "learning", "math", "education"]),
            ("Italian pasta recipes", ["pasta", "cooking", "Italian", "recipe"])
        ]
        
        for query, expected_keywords in test_queries:
            with self.subTest(query=query):
                classification = intent_engine.classify_intent(query)
                results = rag_engine.retrieve_context(query, classification)
                
                self.assertIsNotNone(results, f"Should retrieve context for: {query}")
                self.assertIn('retrieved_documents', results, "Results should contain documents")
                self.assertGreater(len(results['retrieved_documents']), 0, f"Should find documents for: {query}")
                
                # Check if retrieved content contains relevant keywords
                all_content = ""
                for result in results['retrieved_documents']:
                    all_content += result['document'].title.lower() + " " + result['document'].content.lower()
                
                keyword_matches = sum(1 for keyword in expected_keywords if keyword.lower() in all_content)
                self.assertGreater(keyword_matches, 0, f"Retrieved content should contain relevant keywords for: {query}")
    
    def test_context_formatting_structure(self):
        """Test that context is properly formatted for AI consumption"""
        from src.rag.m1k3_rag_engine import M1K3RAGEngine
        from src.utils.intent_classification_system import IntentClassificationEngine
        
        rag_engine = M1K3RAGEngine(knowledge_base_path=self.kb_path)
        rag_engine.load_knowledge_base()
        intent_engine = IntentClassificationEngine()
        
        query = "How do I troubleshoot network connectivity issues?"
        classification = intent_engine.classify_intent(query)
        results = rag_engine.retrieve_context(query, classification)
        
        formatted_context = rag_engine.format_rag_context(query, results['retrieved_documents'])
        
        # Validate structure
        self.assertIsInstance(formatted_context, str, "Context should be a string")
        self.assertGreater(len(formatted_context), 50, "Context should be substantial")
        
        # Check for proper sections
        self.assertIn("Query:", formatted_context, "Should include original query")
        self.assertIn("Context:", formatted_context, "Should have context section")
        
        # Should be well-formatted for AI consumption
        lines = formatted_context.split('\n')
        non_empty_lines = [line for line in lines if line.strip()]
        self.assertGreater(len(non_empty_lines), 2, "Should have multiple content lines")


class TestRAGPerformanceValidation(unittest.TestCase):
    """Test RAG system performance characteristics"""
    
    def setUp(self):
        """Set up for performance testing"""
        self.kb_path = 'knowledge/comprehensive_knowledge_base.json'
        if not os.path.exists(self.kb_path):
            self.skipTest("Knowledge base not found")
    
    def test_search_performance(self):
        """Test search performance with various query types"""
        from src.rag.m1k3_rag_engine import M1K3RAGEngine
        from src.utils.intent_classification_system import IntentClassificationEngine
        
        rag_engine = M1K3RAGEngine(knowledge_base_path=self.kb_path)
        rag_engine.load_knowledge_base()
        intent_engine = IntentClassificationEngine()
        
        test_queries = [
            "Quick WiFi fix",
            "iPhone battery optimization techniques",
            "How to create strong passwords for online accounts",
            "What are the most effective study methods for mathematics and science subjects?",
            "Can you provide detailed Italian pasta recipes with cooking instructions and ingredient lists?"
        ]
        
        total_time = 0
        successful_queries = 0
        
        for query in test_queries:
            start_time = time.time()
            try:
                classification = intent_engine.classify_intent(query)
                results = rag_engine.retrieve_context(query, classification)
                
                query_time = time.time() - start_time
                total_time += query_time
                successful_queries += 1
                
                # Individual query should complete within reasonable time
                self.assertLess(query_time, 10, f"Query should complete within 10s: '{query}' took {query_time:.2f}s")
                
            except Exception as e:
                self.fail(f"Query failed: '{query}' with error: {e}")
        
        # Average performance check
        if successful_queries > 0:
            avg_time = total_time / successful_queries
            self.assertLess(avg_time, 5, f"Average query time should be under 5s, was {avg_time:.2f}s")
    
    def test_memory_efficiency(self):
        """Test that RAG system doesn't leak memory"""
        import psutil
        import os
        from src.rag.m1k3_rag_engine import M1K3RAGEngine
        from src.utils.intent_classification_system import IntentClassificationEngine
        
        process = psutil.Process(os.getpid())
        initial_memory = process.memory_info().rss / 1024 / 1024  # MB
        
        # Create and destroy RAG engines multiple times
        for i in range(3):
            rag_engine = M1K3RAGEngine(knowledge_base_path=self.kb_path)
            rag_engine.load_knowledge_base()
            
            intent_engine = IntentClassificationEngine()
            
            # Perform some searches
            for query in ["WiFi help", "iPhone tips", "Password security"]:
                classification = intent_engine.classify_intent(query)
                results = rag_engine.retrieve_context(query, classification)
            
            # Clean up
            del rag_engine
            del intent_engine
        
        final_memory = process.memory_info().rss / 1024 / 1024  # MB
        memory_increase = final_memory - initial_memory
        
        # Allow reasonable memory increase (under 200MB)
        self.assertLess(memory_increase, 200, f"Memory increase should be reasonable, was {memory_increase:.1f}MB")


class TestRAGErrorHandling(unittest.TestCase):
    """Test RAG system error handling and edge cases"""
    
    def test_missing_knowledge_base_handling(self):
        """Test graceful handling of missing knowledge base"""
        from src.rag.m1k3_rag_engine import M1K3RAGEngine
        
        # Test with non-existent knowledge base
        with tempfile.TemporaryDirectory() as temp_dir:
            fake_kb_path = os.path.join(temp_dir, "nonexistent.json")
            
            engine = M1K3RAGEngine(knowledge_base_path=fake_kb_path)
            
            # Should not crash during initialization
            self.assertIsNotNone(engine)
            
            # Should handle loading gracefully
            try:
                engine.load_knowledge_base()
            except Exception as e:
                # Should provide meaningful error message
                self.assertIn("knowledge", str(e).lower(), "Error should mention knowledge base issue")
    
    def test_empty_query_handling(self):
        """Test handling of empty or invalid queries"""
        if not os.path.exists('knowledge/comprehensive_knowledge_base.json'):
            self.skipTest("Knowledge base not found")
        
        from src.rag.m1k3_rag_engine import M1K3RAGEngine
        from src.utils.intent_classification_system import IntentClassificationEngine
        
        rag_engine = M1K3RAGEngine(knowledge_base_path='knowledge/comprehensive_knowledge_base.json')
        rag_engine.load_knowledge_base()
        intent_engine = IntentClassificationEngine()
        
        edge_case_queries = ["", " ", "?", "...", "a", "🤔"]
        
        for query in edge_case_queries:
            with self.subTest(query=repr(query)):
                try:
                    classification = intent_engine.classify_intent(query)
                    results = rag_engine.retrieve_context(query, classification)
                    
                    # Should not crash and should return some structure
                    self.assertIsNotNone(results, f"Should handle edge case query: {repr(query)}")
                    
                except Exception as e:
                    # If it fails, it should fail gracefully
                    self.assertIsInstance(e, (ValueError, KeyError), 
                                        f"Should fail gracefully for query: {repr(query)}, got: {type(e).__name__}")
    
    def test_malformed_documents_handling(self):
        """Test handling of malformed documents in knowledge base"""
        from src.rag.m1k3_rag_engine import KnowledgeBase
        
        # Create a temporary knowledge base with malformed data
        with tempfile.TemporaryDirectory() as temp_dir:
            malformed_kb = os.path.join(temp_dir, "malformed.json")
            
            malformed_data = {
                "metadata": {"version": "1.0"},
                "documents": [
                    {"id": "good1", "title": "Good Doc", "content": "Good content", "category": "test", "intent": "factual_query"},
                    {"id": "bad1", "title": "Bad Doc"},  # Missing required fields
                    {"id": "bad2", "content": "No title"},  # Missing title
                    "invalid_document",  # Not a dict
                ]
            }
            
            with open(malformed_kb, 'w') as f:
                json.dump(malformed_data, f)
            
            kb = KnowledgeBase(malformed_kb)
            
            # Should handle malformed data gracefully
            try:
                kb.load()
                # Should load at least the good documents
                self.assertGreater(len(kb.documents), 0, "Should load valid documents")
            except Exception as e:
                # If it fails, should provide meaningful error
                error_msg = str(e).lower()
                self.assertTrue(any(word in error_msg for word in ["document", "format", "structure"]), 
                               f"Error should be descriptive: {e}")


if __name__ == "__main__":
    print("🧪 M1K3 RAG Integration Validation Tests")
    print("=" * 60)
    
    # Check prerequisites
    kb_path = 'knowledge/comprehensive_knowledge_base.json'
    if not os.path.exists(kb_path):
        print("⚠️  WARNING: Comprehensive knowledge base not found!")
        print("📝 Run 'python generate_comprehensive_kb.py' first")
        print("🔄 Running tests that don't require knowledge base...")
    
    # Run tests
    unittest.main(verbosity=2, exit=False)
    
    print("\n" + "=" * 60)
    print("🎯 RAG integration validation complete")
    print("💡 For full validation, ensure knowledge base is generated")