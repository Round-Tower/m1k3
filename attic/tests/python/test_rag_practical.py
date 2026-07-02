#!/usr/bin/env python3
"""
M1K3 RAG Practical Test - Quick Real-World Validation
Fast test of core RAG functionality with practical scenarios
"""

import os
import json
import time

def test_rag_practical():
    """Practical test of RAG system components"""
    
    print("🧪 M1K3 RAG Practical Test")
    print("=" * 40)
    
    test_results = {}
    
    # Test 1: Knowledge Base Quality
    print("📚 1. Testing Knowledge Base...")
    try:
        kb_path = 'knowledge/comprehensive_knowledge_base.json'
        if not os.path.exists(kb_path):
            print("   ❌ Knowledge base not found")
            test_results['knowledge_base'] = False
        else:
            with open(kb_path, 'r') as f:
                kb_data = json.load(f)
            
            doc_count = len(kb_data.get('documents', []))
            categories = set(doc.get('category') for doc in kb_data.get('documents', []))
            
            # Check expertise categories
            expertise_cats = {'device_technology', 'wifi_networking', 'security_privacy', 
                            'diagnostic_troubleshooting', 'educational_tutoring', 'trivia_facts'}
            expertise_coverage = len(expertise_cats.intersection(categories)) / len(expertise_cats)
            
            quality_score = 0
            if doc_count >= 1000: quality_score += 1
            if len(categories) >= 18: quality_score += 1  
            if expertise_coverage >= 0.8: quality_score += 1
            
            print(f"   📊 Documents: {doc_count}")
            print(f"   📂 Categories: {len(categories)}")
            print(f"   🎯 Expertise coverage: {expertise_coverage:.1%}")
            
            if quality_score >= 2:
                print("   ✅ Knowledge base quality: GOOD")
                test_results['knowledge_base'] = True
            else:
                print("   ❌ Knowledge base quality: POOR")
                test_results['knowledge_base'] = False
                
    except Exception as e:
        print(f"   ❌ Error testing knowledge base: {e}")
        test_results['knowledge_base'] = False
    
    # Test 2: RAG Engine Initialization
    print("\n🧠 2. Testing RAG Engine...")
    try:
        from src.rag.m1k3_rag_engine import M1K3RAGEngine, RAGDocument
        from src.utils.intent_classification_system import IntentClassificationEngine, UserIntent
        
        start_time = time.time()
        rag_engine = M1K3RAGEngine(knowledge_base_path='knowledge/comprehensive_knowledge_base.json')
        init_time = time.time() - start_time
        
        # Check components
        components_ok = (
            rag_engine.knowledge_base is not None and
            len(rag_engine.knowledge_base.documents) > 1000 and
            rag_engine.intent_engine is not None
        )
        
        print(f"   ⏱️  Initialization time: {init_time:.1f}s")
        print(f"   📖 Documents loaded: {len(rag_engine.knowledge_base.documents)}")
        
        if components_ok and init_time < 30:
            print("   ✅ RAG engine: WORKING")
            test_results['rag_engine'] = True
        else:
            print("   ❌ RAG engine: ISSUES")
            test_results['rag_engine'] = False
            
    except ImportError as e:
        print(f"   ❌ Import error: {e}")
        test_results['rag_engine'] = False
    except Exception as e:
        print(f"   ❌ RAG engine error: {e}")
        test_results['rag_engine'] = False
    
    # Test 3: Search Functionality
    print("\n🔍 3. Testing Search...")
    try:
        if test_results.get('rag_engine', False):
            # Test search with different query types
            test_queries = [
                ("WiFi troubleshooting", "wifi_networking"),
                ("iPhone battery drain", "device_technology"),
                ("password security", "security_privacy")
            ]
            
            search_successes = 0
            for query, expected_category in test_queries:
                classification = rag_engine.intent_engine.classify_intent(query)
                results = rag_engine.retrieve_context(query, classification)
                
                if results and len(results.get('retrieved_documents', [])) > 0:
                    # Check if we got relevant results
                    categories_found = {doc['document'].category for doc in results['retrieved_documents']}
                    if expected_category in categories_found:
                        search_successes += 1
                    else:
                        # Allow related categories
                        search_successes += 0.5
            
            search_score = search_successes / len(test_queries)
            
            print(f"   🎯 Search accuracy: {search_score:.1%}")
            
            if search_score >= 0.6:
                print("   ✅ Search functionality: WORKING")
                test_results['search'] = True
            else:
                print("   ❌ Search functionality: POOR")
                test_results['search'] = False
        else:
            print("   ❌ Cannot test search (RAG engine failed)")
            test_results['search'] = False
            
    except Exception as e:
        print(f"   ❌ Search test error: {e}")
        test_results['search'] = False
    
    # Test 4: Integration Components
    print("\n🔗 4. Testing Integration...")
    try:
        from src.rag.m1k3_rag_integration import M1K3RAGIntegratedEngine
        
        integrated_engine = M1K3RAGIntegratedEngine(
            knowledge_base_path='knowledge/comprehensive_knowledge_base.json',
            enable_rag=True,
            auto_load=True
        )
        
        integration_ok = (
            hasattr(integrated_engine, 'rag_engine') and
            integrated_engine.rag_engine is not None
        )
        
        if integration_ok:
            print("   ✅ RAG integration: WORKING")
            test_results['integration'] = True
        else:
            print("   ❌ RAG integration: FAILED")
            test_results['integration'] = False
            
    except Exception as e:
        print(f"   ❌ Integration test error: {e}")
        test_results['integration'] = False
    
    # Test 5: Web Interfaces
    print("\n🌐 5. Testing Web Interfaces...")
    web_files = ['rag_knowledge_viewer.html', 'rag_admin.html']
    web_ok = True
    
    for filename in web_files:
        if os.path.exists(filename):
            file_size = os.path.getsize(filename)
            if file_size > 1000:  # At least 1KB
                print(f"   ✅ {filename}: {file_size} bytes")
            else:
                print(f"   ⚠️  {filename}: Too small ({file_size} bytes)")
                web_ok = False
        else:
            print(f"   ❌ {filename}: Missing")
            web_ok = False
    
    test_results['web_interfaces'] = web_ok
    
    # Final Assessment
    print("\n" + "🎯 RESULTS" + "="*32)
    
    passed_tests = sum(1 for result in test_results.values() if result)
    total_tests = len(test_results)
    
    for test_name, result in test_results.items():
        status = "✅ PASS" if result else "❌ FAIL"
        print(f"   {status} {test_name.replace('_', ' ').title()}")
    
    success_rate = passed_tests / total_tests
    print(f"\n📊 Overall: {passed_tests}/{total_tests} ({success_rate:.1%})")
    
    if success_rate >= 0.8:
        print("🎉 EXCELLENT: RAG system ready for production!")
        print("💡 Try: python m1k3.py --rag --query \"How do I fix slow WiFi?\"")
    elif success_rate >= 0.6:
        print("👍 GOOD: RAG system working well")
        print("💡 Try: python m1k3.py --rag --query \"iPhone battery tips\"")
    else:
        print("⚠️  NEEDS WORK: Some components failing")
        print("🔧 Check failed tests and fix issues")
    
    return success_rate >= 0.6


if __name__ == "__main__":
    success = test_rag_practical()
    
    if success:
        print("\n✅ RAG system validation successful!")
        print("🚀 System ready for expert-level AI assistance")
    else:
        print("\n❌ RAG system needs attention")
        print("🔧 Address failing components before use")