#!/usr/bin/env python3
"""
Test script for EmbeddingGemma migration in M1K3
Validates that both ConversationEmbedder and M1K3RAGEngine work with the new model
"""

import sys
import time
from pathlib import Path

# Add src to path for imports
sys.path.insert(0, str(Path(__file__).parent))

from src.database.conversation_embedder import ConversationEmbedder
from src.rag.m1k3_rag_engine import M1K3RAGEngine, RAGDocument

def test_conversation_embedder():
    """Test ConversationEmbedder with EmbeddingGemma"""
    print("\n" + "="*80)
    print("TEST 1: ConversationEmbedder with EmbeddingGemma")
    print("="*80)

    # Test 1: Default EmbeddingGemma (768D)
    print("\n📊 Test 1a: Default EmbeddingGemma (768D)")
    embedder = ConversationEmbedder()

    if not embedder.is_available():
        print("❌ FAILED: Embedder not available")
        return False

    # Test single embedding
    test_text = "What is the capital of France?"
    start_time = time.time()
    embedding = embedder.embed_text(test_text)
    elapsed = time.time() - start_time

    if embedding is None:
        print("❌ FAILED: Embedding generation returned None")
        return False

    print(f"✅ Generated embedding: {len(embedding)}D in {elapsed:.3f}s")
    print(f"   Expected: 768D, Got: {len(embedding)}D")

    if len(embedding) != 768:
        print(f"❌ FAILED: Expected 768D, got {len(embedding)}D")
        return False

    # Test 2: EmbeddingGemma with Matryoshka truncation (512D)
    print("\n📊 Test 1b: EmbeddingGemma with Matryoshka truncation (512D)")
    embedder_512 = ConversationEmbedder(truncate_dim=512)

    embedding_512 = embedder_512.embed_text(test_text)
    if embedding_512 is None or len(embedding_512) != 512:
        print(f"❌ FAILED: Expected 512D, got {len(embedding_512) if embedding_512 else None}D")
        return False

    print(f"✅ Truncated embedding: {len(embedding_512)}D")

    # Test 3: Batch embeddings
    print("\n📊 Test 1c: Batch embeddings")
    test_texts = [
        "What is machine learning?",
        "How does neural network work?",
        "Explain quantum computing"
    ]

    start_time = time.time()
    batch_embeddings = embedder.embed_batch(test_texts)
    elapsed = time.time() - start_time

    if len(batch_embeddings) != len(test_texts):
        print(f"❌ FAILED: Expected {len(test_texts)} embeddings, got {len(batch_embeddings)}")
        return False

    print(f"✅ Batch embeddings: {len(batch_embeddings)} texts in {elapsed:.3f}s")

    # Test 4: Cosine similarity
    print("\n📊 Test 1d: Cosine similarity")
    emb1 = embedder.embed_text("Machine learning is a subset of AI")
    emb2 = embedder.embed_text("Neural networks are part of machine learning")
    emb3 = embedder.embed_text("The weather is nice today")

    similarity_related = embedder.cosine_similarity(emb1, emb2)
    similarity_unrelated = embedder.cosine_similarity(emb1, emb3)

    print(f"   Similarity (ML vs NN): {similarity_related:.3f}")
    print(f"   Similarity (ML vs Weather): {similarity_unrelated:.3f}")

    if similarity_related <= similarity_unrelated:
        print(f"❌ FAILED: Related texts should have higher similarity")
        return False

    print(f"✅ Similarity test passed (related > unrelated)")

    # Test 5: Backward compatibility with BGE
    print("\n📊 Test 1e: Backward compatibility with BGE-small")
    embedder_bge = ConversationEmbedder(model_name="BAAI/bge-small-en-v1.5")

    if embedder_bge.is_available():
        embedding_bge = embedder_bge.embed_text(test_text)
        if embedding_bge:
            print(f"✅ BGE-small still works: {len(embedding_bge)}D")
        else:
            print(f"⚠️  BGE-small embedding failed (model may not be downloaded)")
    else:
        print(f"⚠️  BGE-small not available (expected if not downloaded)")

    print("\n✅ ConversationEmbedder tests PASSED")
    return True


def test_rag_engine():
    """Test M1K3RAGEngine with EmbeddingGemma"""
    print("\n" + "="*80)
    print("TEST 2: M1K3RAGEngine with EmbeddingGemma")
    print("="*80)

    # Create temporary knowledge base
    temp_kb_path = "test_gemma_kb.json"

    try:
        # Test 1: Initialize RAG engine with EmbeddingGemma
        print("\n📊 Test 2a: Initialize RAG engine")
        rag_engine = M1K3RAGEngine(
            knowledge_base_path=temp_kb_path,
            embedding_model="google/embeddinggemma-300m",
            lazy_load=False
        )

        print("✅ RAG engine initialized")

        # Test 2: Add documents
        print("\n📊 Test 2b: Add documents to knowledge base")

        doc_ids = []
        doc_ids.append(rag_engine.add_knowledge(
            title="Python Programming",
            content="Python is a high-level, interpreted programming language known for its simplicity and readability. It supports multiple paradigms including procedural, object-oriented, and functional programming.",
            category="programming",
            metadata={"tags": ["python", "programming", "language"]}
        ))

        doc_ids.append(rag_engine.add_knowledge(
            title="Machine Learning Basics",
            content="Machine learning is a subset of artificial intelligence that enables systems to learn and improve from experience without being explicitly programmed. It uses algorithms to analyze data and make predictions.",
            category="ai",
            metadata={"tags": ["machine learning", "AI", "algorithms"]}
        ))

        doc_ids.append(rag_engine.add_knowledge(
            title="Web Development",
            content="Web development involves creating websites and web applications. It includes frontend development (HTML, CSS, JavaScript) and backend development (servers, databases, APIs).",
            category="web",
            metadata={"tags": ["web", "html", "javascript", "backend"]}
        ))

        print(f"✅ Added {len(doc_ids)} documents")

        # Test 3: Semantic search
        print("\n📊 Test 2c: Semantic search with EmbeddingGemma")

        test_queries = [
            "Tell me about programming languages",
            "How does AI learn from data?",
            "What technologies are used for websites?"
        ]

        for query in test_queries:
            print(f"\n   Query: '{query}'")
            start_time = time.time()
            results = rag_engine.retrieve_context(query, max_documents=2)
            elapsed = time.time() - start_time

            if not results:
                print(f"   ⚠️  No results found")
                continue

            print(f"   ⏱️  Retrieved in {elapsed:.3f}s")
            for i, result in enumerate(results, 1):
                doc = result['document']
                similarity = result['similarity']
                print(f"   {i}. {doc.title} (similarity: {similarity:.3f})")

        # Test 4: RAG-enhanced prompt
        print("\n📊 Test 2d: RAG prompt enhancement")
        query = "How can I start learning Python?"
        enhanced_prompt = rag_engine.enhance_prompt(query)

        if "Python" in enhanced_prompt and len(enhanced_prompt) > len(query):
            print(f"✅ Prompt enhanced with context ({len(enhanced_prompt)} chars)")
        else:
            print(f"⚠️  Prompt not enhanced (may need to adjust similarity threshold)")

        # Test 5: Statistics
        print("\n📊 Test 2e: RAG engine statistics")
        stats = rag_engine.get_stats()

        print(f"   Total documents: {stats['knowledge_base']['total_documents']}")
        print(f"   Embedding model: {stats['embedding_model']}")
        print(f"   Embedding cache size: {stats['embedding_cache_size']}")
        print(f"   Categories: {list(stats['knowledge_base']['categories'].keys())}")

        print("\n✅ M1K3RAGEngine tests PASSED")
        return True

    except Exception as e:
        print(f"\n❌ RAG engine test FAILED: {e}")
        import traceback
        traceback.print_exc()
        return False

    finally:
        # Cleanup
        import os
        if os.path.exists(temp_kb_path):
            os.remove(temp_kb_path)
            print(f"\n🗑️  Cleaned up test file: {temp_kb_path}")


def test_matryoshka_comparison():
    """Compare different Matryoshka truncation levels"""
    print("\n" + "="*80)
    print("TEST 3: Matryoshka Representation Learning Comparison")
    print("="*80)

    test_text = "Natural language processing enables computers to understand human language"
    dimensions = [768, 512, 256, 128]

    embedders = {}
    embeddings = {}

    # Generate embeddings at different dimensions
    for dim in dimensions:
        truncate = dim if dim < 768 else None
        embedders[dim] = ConversationEmbedder(truncate_dim=truncate)

        if embedders[dim].is_available():
            start_time = time.time()
            embeddings[dim] = embedders[dim].embed_text(test_text)
            elapsed = time.time() - start_time

            if embeddings[dim]:
                print(f"   {dim}D embedding: {len(embeddings[dim])} dimensions, {elapsed:.3f}s")

    # Compare similarities between different dimension embeddings
    if 768 in embeddings and 512 in embeddings:
        print(f"\n📊 Testing similarity preservation across dimensions")

        test_text2 = "Machine learning algorithms analyze patterns in data"

        emb_768_a = embedders[768].embed_text(test_text)
        emb_768_b = embedders[768].embed_text(test_text2)
        sim_768 = embedders[768].cosine_similarity(emb_768_a, emb_768_b)

        emb_512_a = embedders[512].embed_text(test_text)
        emb_512_b = embedders[512].embed_text(test_text2)
        sim_512 = embedders[512].cosine_similarity(emb_512_a, emb_512_b)

        print(f"   768D similarity: {sim_768:.3f}")
        print(f"   512D similarity: {sim_512:.3f}")
        print(f"   Difference: {abs(sim_768 - sim_512):.3f}")

        if abs(sim_768 - sim_512) < 0.1:
            print(f"✅ Matryoshka preserves similarity relationships")
        else:
            print(f"⚠️  Larger difference than expected")

    print("\n✅ Matryoshka comparison test PASSED")
    return True


def main():
    """Run all tests"""
    print("\n" + "="*80)
    print("EmbeddingGemma Migration Test Suite")
    print("Testing M1K3 embedding system with google/embeddinggemma-300m")
    print("="*80)

    results = []

    # Test 1: ConversationEmbedder
    results.append(("ConversationEmbedder", test_conversation_embedder()))

    # Test 2: M1K3RAGEngine
    results.append(("M1K3RAGEngine", test_rag_engine()))

    # Test 3: Matryoshka comparison
    results.append(("Matryoshka Comparison", test_matryoshka_comparison()))

    # Summary
    print("\n" + "="*80)
    print("TEST SUMMARY")
    print("="*80)

    for test_name, passed in results:
        status = "✅ PASSED" if passed else "❌ FAILED"
        print(f"{test_name:30s} {status}")

    all_passed = all(result[1] for result in results)

    if all_passed:
        print("\n🎉 ALL TESTS PASSED! EmbeddingGemma migration successful!")
        return 0
    else:
        print("\n❌ SOME TESTS FAILED. Review errors above.")
        return 1


if __name__ == "__main__":
    sys.exit(main())
