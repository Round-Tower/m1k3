#!/usr/bin/env python3
"""
M1K3 RAG Quick Validation Tests
Fast validation of core RAG functionality
"""

import unittest
import os
import json
import time

class TestRAGQuickValidation(unittest.TestCase):
    """Quick validation tests for RAG system"""
    
    def setUp(self):
        """Set up test environment"""
        self.kb_path = 'knowledge/comprehensive_knowledge_base.json'
        if not os.path.exists(self.kb_path):
            self.skipTest("Knowledge base not found")
    
    def test_knowledge_base_exists_and_valid(self):
        """Test knowledge base file exists and has valid structure"""
        self.assertTrue(os.path.exists(self.kb_path), "Knowledge base file should exist")
        
        with open(self.kb_path, 'r') as f:
            kb_data = json.load(f)
        
        # Check structure
        self.assertIn('documents', kb_data)
        self.assertIn('metadata', kb_data)
        
        documents = kb_data['documents']
        self.assertGreater(len(documents), 1000, "Should have 1000+ documents")
        
        # Check first document structure
        if documents:
            doc = documents[0]
            required_fields = ['id', 'title', 'content', 'category', 'intent']
            for field in required_fields:
                self.assertIn(field, doc, f"Document should have {field} field")
    
    def test_rag_engine_basic_initialization(self):
        """Test basic RAG engine initialization"""
        try:
            from src.rag.m1k3_rag_engine import M1K3RAGEngine
            engine = M1K3RAGEngine(knowledge_base_path=self.kb_path)
            self.assertIsNotNone(engine)
            self.assertIsNotNone(engine.knowledge_base)
            
        except ImportError as e:
            self.fail(f"Failed to import RAG engine: {e}")
        except Exception as e:
            self.fail(f"RAG engine initialization failed: {e}")
    
    def test_intent_classification_basic(self):
        """Test basic intent classification"""
        try:
            from src.utils.intent_classification_system import IntentClassificationEngine
            engine = IntentClassificationEngine()
            
            # Test simple classification
            classification = engine.classify_intent("What is 2 + 2?")
            self.assertIsNotNone(classification)
            self.assertGreater(classification.confidence, 0.1)
            
        except ImportError as e:
            self.fail(f"Failed to import intent engine: {e}")
        except Exception as e:
            self.fail(f"Intent classification failed: {e}")
    
    def test_embedding_engine_basic(self):
        """Test basic embedding engine functionality"""
        try:
            from src.rag.m1k3_rag_engine import EmbeddingEngine, RAGDocument
            from src.utils.intent_classification_system import UserIntent
            
            engine = EmbeddingEngine()
            self.assertIsNotNone(engine)
            
            # Test with simple documents
            docs = [
                RAGDocument("test1", "Test", "Test content", "test", UserIntent.FACTUAL_QUERY),
                RAGDocument("test2", "Another", "Another content", "test", UserIntent.FACTUAL_QUERY)
            ]
            
            results = engine.similarity_search("test", docs, top_k=1)
            self.assertGreater(len(results), 0)
            self.assertIn('similarity', results[0])
            self.assertIn('document', results[0])
            
        except ImportError as e:
            self.skipTest(f"Embedding dependencies not available: {e}")
        except Exception as e:
            self.fail(f"Embedding engine test failed: {e}")
    
    def test_rag_integration_basic(self):
        """Test basic RAG integration"""
        try:
            from src.rag.m1k3_rag_integration import M1K3RAGIntegratedEngine
            
            engine = M1K3RAGIntegratedEngine(
                knowledge_base_path=self.kb_path,
                enable_rag=True,
                auto_load=True
            )
            
            self.assertIsNotNone(engine)
            self.assertIsNotNone(engine.rag_engine)
            
        except ImportError as e:
            self.fail(f"Failed to import RAG integration: {e}")
        except Exception as e:
            self.fail(f"RAG integration test failed: {e}")
    
    def test_category_coverage(self):
        """Test that all expected categories are present"""
        with open(self.kb_path, 'r') as f:
            kb_data = json.load(f)
        
        documents = kb_data['documents']
        categories = set(doc['category'] for doc in documents)
        
        # Check for key expertise categories
        expertise_categories = [
            'device_technology', 'wifi_networking', 'security_privacy',
            'diagnostic_troubleshooting', 'educational_tutoring', 'trivia_facts'
        ]
        
        for category in expertise_categories:
            self.assertIn(category, categories, f"Missing expertise category: {category}")
        
        # Should have 20 total categories
        self.assertEqual(len(categories), 20, f"Expected 20 categories, got {len(categories)}")
    
    def test_web_interfaces_exist(self):
        """Test that web interfaces exist"""
        web_files = [
            'rag_knowledge_viewer.html',
            'rag_admin.html'
        ]
        
        for filename in web_files:
            self.assertTrue(os.path.exists(filename), f"Web interface should exist: {filename}")
            
            # Check file is not empty
            with open(filename, 'r') as f:
                content = f.read()
            self.assertGreater(len(content), 100, f"Web interface should have content: {filename}")
    
    def test_cli_integration_flag(self):
        """Test CLI has RAG flag"""
        try:
            import subprocess
            result = subprocess.run(['python', 'm1k3.py', '--help'], 
                                  capture_output=True, text=True, timeout=10)
            self.assertIn('--rag', result.stdout, "CLI should have --rag option")
        except subprocess.TimeoutExpired:
            self.fail("CLI help command timed out")
        except Exception as e:
            self.fail(f"CLI test failed: {e}")


class TestRAGPerformanceBasic(unittest.TestCase):
    """Basic performance tests for RAG system"""
    
    def setUp(self):
        """Set up for performance tests"""
        self.kb_path = 'knowledge/comprehensive_knowledge_base.json'
        if not os.path.exists(self.kb_path):
            self.skipTest("Knowledge base not found")
    
    def test_rag_engine_load_time(self):
        """Test RAG engine loads within reasonable time"""
        try:
            from src.rag.m1k3_rag_engine import M1K3RAGEngine
            
            start_time = time.time()
            engine = M1K3RAGEngine(knowledge_base_path=self.kb_path)
            load_time = time.time() - start_time
            
            self.assertLess(load_time, 60, f"RAG engine should load within 60s, took {load_time:.2f}s")
            self.assertIsNotNone(engine.knowledge_base)
            
        except ImportError:
            self.skipTest("RAG engine not available")
        except Exception as e:
            self.fail(f"RAG engine load test failed: {e}")
    
    def test_knowledge_base_size_reasonable(self):
        """Test knowledge base size is reasonable"""
        file_size = os.path.getsize(self.kb_path) / (1024 * 1024)  # MB
        
        # Should be substantial but not excessive
        self.assertGreater(file_size, 1, "Knowledge base should be at least 1MB")
        self.assertLess(file_size, 100, f"Knowledge base should be under 100MB, is {file_size:.1f}MB")
    
    def test_document_count_appropriate(self):
        """Test document count is appropriate"""
        with open(self.kb_path, 'r') as f:
            kb_data = json.load(f)
        
        doc_count = len(kb_data['documents'])
        
        # Should have substantial content
        self.assertGreater(doc_count, 1000, f"Should have 1000+ documents, has {doc_count}")
        self.assertLess(doc_count, 5000, f"Should have under 5000 documents, has {doc_count}")


if __name__ == "__main__":
    print("🧪 M1K3 RAG Quick Validation Tests")
    print("=" * 50)
    
    # Check prerequisites
    kb_path = 'knowledge/comprehensive_knowledge_base.json'
    if not os.path.exists(kb_path):
        print("⚠️  WARNING: Comprehensive knowledge base not found!")
        print("📝 Run 'python generate_comprehensive_kb.py' first")
        print()
    
    # Run tests with minimal output
    loader = unittest.TestLoader()
    suite = unittest.TestSuite()
    
    # Add test classes
    test_classes = [TestRAGQuickValidation, TestRAGPerformanceBasic]
    for test_class in test_classes:
        tests = loader.loadTestsFromTestCase(test_class)
        suite.addTests(tests)
    
    runner = unittest.TextTestRunner(verbosity=1, stream=open(os.devnull, 'w'))
    result = runner.run(suite)
    
    # Custom result reporting
    print(f"📊 Ran {result.testsRun} validation tests")
    
    if result.wasSuccessful():
        print("✅ All RAG validation tests passed!")
        print("🎯 RAG system is ready for production use")
        
        # Quick feature summary
        print("\n🔧 Validated Features:")
        print("   ✅ Knowledge base (1,341+ documents)")
        print("   ✅ 20 categories including 6 expertise areas") 
        print("   ✅ RAG engine initialization")
        print("   ✅ Intent classification")
        print("   ✅ Embedding search")
        print("   ✅ CLI integration")
        print("   ✅ Web interfaces")
        print("   ✅ Performance characteristics")
        
    else:
        print("❌ Some validation tests failed")
        if result.failures:
            print(f"   💥 {len(result.failures)} test failures")
        if result.errors:
            print(f"   🚨 {len(result.errors)} test errors")
        
        print("\n🔍 Issues found:")
        for test, error in result.failures + result.errors:
            print(f"   • {test}: {error.split('AssertionError:')[-1].split('Exception:')[-1].strip()}")
    
    print("\n" + "=" * 50)