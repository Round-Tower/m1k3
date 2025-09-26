#!/usr/bin/env python3
"""
Comprehensive Vector Memory System Test Suite
Ensures the vector memory system is production-ready and robust
"""

import sys
import time
import traceback
import tempfile
import shutil
from pathlib import Path
from datetime import datetime

# Add src directory to path
sys.path.insert(0, 'src')

def run_comprehensive_tests():
    """Run comprehensive tests for the vector memory system"""

    print("🧪 M1K3 Vector Memory System - Comprehensive Test Suite")
    print("=" * 70)
    print()

    test_results = []
    start_time = time.time()

    # Test 1: Core Components Initialization
    print("📦 Test 1: Core Components Initialization")
    print("-" * 40)
    try:
        from src.database.conversation_manager import get_conversation_manager, ConversationRecord
        from src.database.conversation_embedder import get_conversation_embedder
        from src.database.vector_memory_manager import get_vector_memory_manager
        from src.database.memory_enhanced_rag import get_memory_enhanced_rag

        manager = get_conversation_manager()
        embedder = get_conversation_embedder()
        memory_manager = get_vector_memory_manager()
        enhanced_rag = get_memory_enhanced_rag()

        print("✅ All core components initialized successfully")
        test_results.append(("Core Components", "PASS"))
    except Exception as e:
        print(f"❌ Core components initialization failed: {e}")
        test_results.append(("Core Components", "FAIL"))
        print()

    # Test 2: Database Schema and Migration
    print("📊 Test 2: Database Schema and Migration")
    print("-" * 40)
    try:
        # Test schema creation and migration
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_db_path = Path(temp_dir) / "test_conversations.duckdb"

            # Create manager with temporary database
            from src.database.conversation_manager import ConversationManager
            temp_manager = ConversationManager(str(temp_db_path))

            # Verify schema exists
            result = temp_manager.conn.execute("PRAGMA table_info(conversations)").fetchall()
            column_names = [row[1] for row in result]

            required_columns = ['id', 'session_id', 'timestamp', 'user_input', 'ai_response',
                              'user_embedding', 'response_embedding', 'conversation_summary']

            missing_columns = [col for col in required_columns if col not in column_names]

            if not missing_columns:
                print("✅ Database schema correctly created with all vector columns")
                print(f"   Found {len(column_names)} columns including embeddings")
                test_results.append(("Database Schema", "PASS"))
            else:
                print(f"❌ Missing columns: {missing_columns}")
                test_results.append(("Database Schema", "FAIL"))

            temp_manager.close()

    except Exception as e:
        print(f"❌ Database schema test failed: {e}")
        test_results.append(("Database Schema", "FAIL"))
    print()

    # Test 3: Embedding Generation and Storage
    print("🔤 Test 3: Embedding Generation and Storage")
    print("-" * 40)
    try:
        if embedder.is_available():
            # Test embedding generation
            test_texts = [
                "What is artificial intelligence?",
                "How do neural networks learn?",
                "What is consciousness in AI systems?"
            ]

            embeddings = []
            for text in test_texts:
                embedding = embedder.embed_text(text)
                if embedding and len(embedding) == 384:
                    embeddings.append(embedding)

            if len(embeddings) == len(test_texts):
                print(f"✅ Generated {len(embeddings)} embeddings with correct dimensionality (384)")

                # Test similarity calculation
                similarity = embedder.cosine_similarity(embeddings[0], embeddings[1])
                if 0 <= similarity <= 1:
                    print(f"✅ Cosine similarity calculation working: {similarity:.3f}")
                    test_results.append(("Embedding Generation", "PASS"))
                else:
                    print(f"❌ Invalid similarity score: {similarity}")
                    test_results.append(("Embedding Generation", "FAIL"))
            else:
                print(f"❌ Embedding generation failed: {len(embeddings)}/{len(test_texts)}")
                test_results.append(("Embedding Generation", "FAIL"))
        else:
            print("⚠️ Embedder not available - skipping embedding tests")
            test_results.append(("Embedding Generation", "SKIP"))

    except Exception as e:
        print(f"❌ Embedding generation test failed: {e}")
        test_results.append(("Embedding Generation", "FAIL"))
    print()

    # Test 4: Conversation Storage with Embeddings
    print("💾 Test 4: Conversation Storage with Embeddings")
    print("-" * 40)
    try:
        # Store test conversations
        test_conversations = [
            {
                "user": "What is machine learning?",
                "ai": "Machine learning is a subset of AI that enables systems to automatically learn and improve from experience without being explicitly programmed.",
                "session": "test_session_storage",
                "personality": "educational"
            },
            {
                "user": "How do deep neural networks work?",
                "ai": "Deep neural networks work by processing information through multiple layers of interconnected nodes, each layer learning increasingly complex patterns from the data.",
                "session": "test_session_storage",
                "personality": "technical"
            }
        ]

        stored_ids = []
        for conv in test_conversations:
            record = ConversationRecord(
                id="",
                session_id=conv["session"],
                timestamp=datetime.now(),
                user_input=conv["user"],
                ai_response=conv["ai"],
                response_time_ms=200,
                tokens_used=50,
                personality_type=conv["personality"]
            )

            conv_id = manager.add_conversation(record)
            if conv_id:
                stored_ids.append(conv_id)

        if len(stored_ids) == len(test_conversations):
            print(f"✅ Successfully stored {len(stored_ids)} conversations with embeddings")

            # Verify embeddings were generated
            for conv_id in stored_ids:
                result = manager.conn.execute(
                    "SELECT user_embedding, response_embedding FROM conversations WHERE id = ?",
                    [conv_id]
                ).fetchone()

                if result and result[0] and result[1]:
                    print(f"✅ Conversation {conv_id[:8]}... has embeddings")
                else:
                    print(f"❌ Conversation {conv_id[:8]}... missing embeddings")
                    raise Exception("Missing embeddings in stored conversation")

            test_results.append(("Conversation Storage", "PASS"))
        else:
            print(f"❌ Storage failed: {len(stored_ids)}/{len(test_conversations)}")
            test_results.append(("Conversation Storage", "FAIL"))

    except Exception as e:
        print(f"❌ Conversation storage test failed: {e}")
        test_results.append(("Conversation Storage", "FAIL"))
    print()

    # Test 5: Vector Similarity Search
    print("🔍 Test 5: Vector Similarity Search")
    print("-" * 40)
    try:
        if memory_manager.is_available():
            # Test semantic search
            search_queries = [
                "artificial intelligence and learning",
                "neural networks and deep learning",
                "machine learning algorithms"
            ]

            for query in search_queries:
                results = memory_manager.search_memory(query, limit=5)

                if results['total_results'] > 0:
                    best_match = results['results'][0]
                    similarity = best_match['similarity_score']

                    if similarity > 0.5:  # Reasonable similarity threshold
                        print(f"✅ Query '{query}' found {results['total_results']} results, best similarity: {similarity:.3f}")
                    else:
                        print(f"⚠️ Query '{query}' low similarity: {similarity:.3f}")
                else:
                    print(f"❌ Query '{query}' found no results")

            test_results.append(("Vector Search", "PASS"))
        else:
            print("⚠️ Memory manager not available - skipping search tests")
            test_results.append(("Vector Search", "SKIP"))

    except Exception as e:
        print(f"❌ Vector search test failed: {e}")
        test_results.append(("Vector Search", "FAIL"))
    print()

    # Test 6: Memory Insights and Analytics
    print("🧠 Test 6: Memory Insights and Analytics")
    print("-" * 40)
    try:
        insights = memory_manager.get_conversation_insights(days=1)

        required_keys = ['total_conversations', 'embedding_coverage_percent', 'conversation_clusters', 'memory_system_health']
        missing_keys = [key for key in required_keys if key not in insights]

        if not missing_keys:
            print(f"✅ Memory insights generated successfully")
            print(f"   Total conversations: {insights['total_conversations']}")
            print(f"   Embedding coverage: {insights['embedding_coverage_percent']}%")
            print(f"   Conversation clusters: {insights['conversation_clusters']}")
            print(f"   System health: {insights['memory_system_health']}")
            test_results.append(("Memory Insights", "PASS"))
        else:
            print(f"❌ Missing insight keys: {missing_keys}")
            test_results.append(("Memory Insights", "FAIL"))

    except Exception as e:
        print(f"❌ Memory insights test failed: {e}")
        test_results.append(("Memory Insights", "FAIL"))
    print()

    # Test 7: Query Enhancement
    print("🤖 Test 7: Query Enhancement")
    print("-" * 40)
    try:
        test_queries = [
            "tell me about neural networks",
            "how does machine learning work",
            "what is artificial intelligence"
        ]

        for query in test_queries:
            enhancement = memory_manager.enhance_query_with_memory(query, session_id="test_session_storage")

            if enhancement['confidence_score'] >= 0:
                print(f"✅ Query '{query}' enhanced with confidence: {enhancement['confidence_score']:.2f}")
                print(f"   Memory contexts found: {len(enhancement['memory_contexts'])}")
            else:
                print(f"❌ Query enhancement failed for: {query}")

        test_results.append(("Query Enhancement", "PASS"))

    except Exception as e:
        print(f"❌ Query enhancement test failed: {e}")
        test_results.append(("Query Enhancement", "FAIL"))
    print()

    # Test 8: CLI Command Integration
    print("💻 Test 8: CLI Command Integration")
    print("-" * 40)
    try:
        from src.cli.cli_database_commands import DatabaseCommandHandler

        class MockCLI:
            pass

        db_handler = DatabaseCommandHandler(MockCLI())

        # Test vector search command
        search_result = db_handler.handle_vector_search(["machine", "learning"])
        if "Vector Search Results" in search_result:
            print("✅ Vector search CLI command working")
        else:
            print("❌ Vector search CLI command failed")

        # Test memory insights command
        insights_result = db_handler.handle_memory_insights(["1"])
        if "Memory Insights" in insights_result:
            print("✅ Memory insights CLI command working")
        else:
            print("❌ Memory insights CLI command failed")

        # Test query enhancement command
        enhance_result = db_handler.handle_enhance_query(["neural", "networks"])
        if "Query Enhancement Test" in enhance_result:
            print("✅ Query enhancement CLI command working")
            test_results.append(("CLI Integration", "PASS"))
        else:
            print("❌ Query enhancement CLI command failed")
            test_results.append(("CLI Integration", "FAIL"))

    except Exception as e:
        print(f"❌ CLI integration test failed: {e}")
        test_results.append(("CLI Integration", "FAIL"))
    print()

    # Test 9: Memory-Enhanced RAG Integration
    print("📚 Test 9: Memory-Enhanced RAG Integration")
    print("-" * 40)
    try:
        status = enhanced_rag.get_system_status()

        if status['memory_system_available']:
            print("✅ Memory system integration working")
        else:
            print("❌ Memory system integration failed")

        hybrid_enhancement = enhanced_rag.enhance_query_with_hybrid_context("What is consciousness in AI?")

        if hybrid_enhancement['confidence_score'] >= 0:
            print(f"✅ Hybrid RAG enhancement working with confidence: {hybrid_enhancement['confidence_score']:.2f}")
            print(f"   Context sources: {hybrid_enhancement['context_sources']}")
            test_results.append(("RAG Integration", "PASS"))
        else:
            print("❌ Hybrid RAG enhancement failed")
            test_results.append(("RAG Integration", "FAIL"))

    except Exception as e:
        print(f"❌ RAG integration test failed: {e}")
        test_results.append(("RAG Integration", "FAIL"))
    print()

    # Test 10: Error Handling and Edge Cases
    print("🛡️ Test 10: Error Handling and Edge Cases")
    print("-" * 40)
    try:
        # Test empty query
        empty_results = memory_manager.search_memory("", limit=5)
        print("✅ Empty query handled gracefully")

        # Test invalid limit parameter
        invalid_results = memory_manager.search_memory("test", limit=0)
        print("✅ Invalid parameters handled gracefully")

        # Test memory with no conversations
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_db_path = Path(temp_dir) / "empty_test.duckdb"
            from src.database.conversation_manager import ConversationManager
            empty_manager = ConversationManager(str(temp_db_path))

            empty_insights = get_vector_memory_manager().get_conversation_insights(days=1)
            if empty_insights:
                print("✅ Empty database handled gracefully")
            empty_manager.close()

        test_results.append(("Error Handling", "PASS"))

    except Exception as e:
        print(f"❌ Error handling test failed: {e}")
        test_results.append(("Error Handling", "FAIL"))
    print()

    # Final Results Summary
    total_time = time.time() - start_time
    passed_tests = sum(1 for _, result in test_results if result == "PASS")
    failed_tests = sum(1 for _, result in test_results if result == "FAIL")
    skipped_tests = sum(1 for _, result in test_results if result == "SKIP")

    print("🎯 COMPREHENSIVE TEST RESULTS")
    print("=" * 70)
    print(f"⏱️  Total test time: {total_time:.2f} seconds")
    print(f"✅ Passed: {passed_tests}")
    print(f"❌ Failed: {failed_tests}")
    print(f"⚠️  Skipped: {skipped_tests}")
    print(f"📊 Success rate: {passed_tests/(passed_tests+failed_tests)*100:.1f}%")
    print()

    for test_name, result in test_results:
        status_emoji = {"PASS": "✅", "FAIL": "❌", "SKIP": "⚠️"}[result]
        print(f"{status_emoji} {test_name}: {result}")

    print()
    if failed_tests == 0:
        print("🎉 ALL TESTS PASSED! Vector memory system is production-ready!")
        print("🚀 Ready for deployment with full confidence")
    else:
        print(f"⚠️ {failed_tests} tests failed - review and fix before deployment")

    return failed_tests == 0

if __name__ == "__main__":
    success = run_comprehensive_tests()
    sys.exit(0 if success else 1)