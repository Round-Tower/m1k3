#!/usr/bin/env python3
"""
Generate M1K3 Advanced Expertise Knowledge Base
Creates a comprehensive knowledge base with technical, educational, security, diagnostic,
general knowledge, pop culture, and trivia content for universal expert assistance
"""

import time
import json
from pathlib import Path
from expanded_synthetic_generator import ExpandedSyntheticGenerator
from m1k3_rag_integration import M1K3RAGIntegratedEngine

def generate_comprehensive_knowledge_base():
    """Generate a comprehensive knowledge base with all categories"""
    
    print("🚀 M1K3 Advanced Expertise Knowledge Base Generation")
    print("=" * 70)
    
    # Initialize generator
    kb_path = "knowledge/comprehensive_knowledge_base.json"
    generator = ExpandedSyntheticGenerator(kb_path)
    
    # Create knowledge directory
    Path("knowledge").mkdir(exist_ok=True)
    
    # Generation plan - comprehensive expertise across all categories
    generation_plan = {
        # Technical Categories (Essential for programming AI)
        "mathematical_calculation": 50,   # Math problems and calculations
        "code_debugging": 60,            # Programming help and troubleshooting
        "explanation_request": 45,       # Technical concepts and how-things-work
        "casual_conversation": 30,       # Friendly interactions and greetings
        "creative_writing": 25,          # Stories and creative content
        
        # General Knowledge (Educational and informative)
        "historical_facts": 40,          # World history and civilizations
        "science_facts": 45,             # Natural phenomena and scientific concepts
        "geography_facts": 35,           # World locations and geographical features
        
        # Pop Culture & Entertainment (Fun and engaging)
        "movies_tv": 35,                 # Entertainment and media industry
        "music_culture": 30,             # Musical genres and cultural impact
        "sports_recreation": 30,         # Sports, fitness, and recreational activities
        "food_culture": 25,              # Cuisines, cooking, and food traditions
        "technology_trends": 25,         # Modern technology and digital life
        "lifestyle_wellness": 20,        # Health, wellness, and self-care
        
        # Device & Technology Expertise (Advanced technical support)
        "device_technology": 60,         # Device troubleshooting and optimization
        "wifi_networking": 50,           # WiFi and networking solutions
        "security_privacy": 80,          # Cybersecurity and privacy protection
        "diagnostic_troubleshooting": 55, # Systematic problem-solving methods
        "educational_tutoring": 90,      # Study techniques and academic support
        "trivia_facts": 70               # Fun facts and entertaining trivia
    }
    
    print(f"\n📋 Generation Plan:")
    total_docs = sum(generation_plan.values())
    estimates = generator.get_generation_estimates()
    
    # Calculate actual costs for our plan
    base_cost_per_doc = 0.0028
    total_cost = total_docs * base_cost_per_doc
    total_time_minutes = int((total_docs * 2.6) / 60)  # 2.6 seconds per doc
    
    print(f"  • Total Documents: {total_docs}")
    print(f"  • Estimated Cost: ${total_cost:.2f}")
    print(f"  • Estimated Time: {total_time_minutes} minutes ({total_time_minutes // 60}h {total_time_minutes % 60}m)")
    
    # Show category breakdown
    print(f"\n📊 Category Breakdown:")
    for category, count in generation_plan.items():
        category_display = category.replace('_', ' ').title()
        percentage = (count / total_docs) * 100
        print(f"  • {category_display}: {count} docs ({percentage:.1f}%)")
    
    # Progress tracking
    generated_counts = {}
    failed_categories = []
    start_time = time.time()
    
    def progress_callback(current, total, title, category):
        elapsed = time.time() - start_time
        rate = (sum(generated_counts.values()) + current) / max(elapsed / 60, 0.1)  # docs per minute
        eta = (total_docs - sum(generated_counts.values()) - current) / max(rate, 1)
        print(f"    📝 {category} {current}/{total}: {title[:40]}... (ETA: {eta:.1f}m)")
    
    print(f"\n🚀 Starting Generation Process...")
    
    # Generate documents for each category
    for category, count in generation_plan.items():
        category_display = category.replace('_', ' ').title()
        print(f"\n🎯 Generating {category_display} ({count} documents)...")
        
        try:
            # Create progress callback for this category
            def category_progress(current, total, title):
                progress_callback(current, total, title, category_display)
            
            result = generator.generate_and_save(
                category=category,
                count=count,
                progress_callback=category_progress
            )
            
            if result['success']:
                generated_counts[category] = result['count']
                elapsed = time.time() - start_time
                print(f"  ✅ {category_display}: {result['count']} documents in {result['time_taken']:.1f}s")
                print(f"     Total progress: {sum(generated_counts.values())}/{total_docs} ({elapsed/60:.1f}m elapsed)")
            else:
                print(f"  ❌ {category_display}: Failed - {result['message']}")
                failed_categories.append(category)
                generated_counts[category] = 0
                
        except Exception as e:
            print(f"  ❌ {category_display}: Exception - {e}")
            failed_categories.append(category)
            generated_counts[category] = 0
        
        # Small delay between categories
        time.sleep(0.5)
    
    # Final summary
    total_time = time.time() - start_time
    total_generated = sum(generated_counts.values())
    success_rate = (total_generated / total_docs) * 100
    
    print(f"\n" + "=" * 70)
    print(f"🎉 Knowledge Base Generation Complete!")
    print(f"=" * 70)
    
    print(f"📊 Final Statistics:")
    print(f"  • Total Documents Generated: {total_generated} / {total_docs}")
    print(f"  • Success Rate: {success_rate:.1f}%")
    print(f"  • Total Time: {total_time/60:.1f} minutes")
    print(f"  • Average Rate: {total_generated/(total_time/60):.1f} docs/minute")
    print(f"  • Knowledge Base Size: {Path(kb_path).stat().st_size / 1024:.1f} KB")
    
    print(f"\n📈 Category Success:")
    for category, count in generated_counts.items():
        planned = generation_plan[category]
        success = (count / planned) * 100 if planned > 0 else 0
        status = "✅" if success > 80 else "⚠️" if success > 50 else "❌"
        category_display = category.replace('_', ' ').title()
        print(f"  {status} {category_display}: {count}/{planned} ({success:.1f}%)")
    
    if failed_categories:
        print(f"\n⚠️  Categories with issues: {', '.join(failed_categories)}")
    
    print(f"\n🎯 Knowledge Base Created:")
    print(f"  • File: {kb_path}")
    print(f"  • Categories: {len([c for c in generated_counts.values() if c > 0])}")
    print(f"  • Ready for M1K3 integration!")
    
    return kb_path, total_generated, generated_counts

def test_comprehensive_kb():
    """Test the comprehensive knowledge base"""
    print("\n🧪 Testing Comprehensive Knowledge Base")
    print("=" * 50)
    
    # Generate the knowledge base
    kb_path, total_docs, category_counts = generate_comprehensive_knowledge_base()
    
    if total_docs == 0:
        print("❌ No documents generated, cannot test")
        return
    
    # Initialize RAG engine with comprehensive knowledge base
    print(f"\n🔧 Initializing RAG Engine with comprehensive knowledge...")
    engine = M1K3RAGIntegratedEngine(
        knowledge_base_path=kb_path,
        enable_rag=True,
        auto_load=False  # Skip model loading for demo
    )
    
    # Test queries across different categories
    test_queries = [
        # Technical queries
        ("What is 15 × 23?", "Mathematical calculation"),
        ("How do I fix a Python NameError?", "Code debugging"),
        ("Explain how photosynthesis works", "Science explanation"),
        
        # General knowledge queries  
        ("Tell me about ancient Egyptian civilization", "Historical facts"),
        ("What causes lightning?", "Science facts"),
        ("Where is Mount Everest located?", "Geography"),
        
        # Pop culture queries
        ("What makes a good movie?", "Movies and TV"),
        ("Tell me about jazz music", "Music culture"),
        ("What are the benefits of swimming?", "Sports and recreation"),
        ("What is Italian cuisine known for?", "Food culture"),
        ("How has social media changed communication?", "Technology trends"),
        ("What are good stress management techniques?", "Lifestyle and wellness"),
        
        # Conversational queries
        ("Hello! How are you today?", "Casual conversation"),
        ("Can you write a short story about robots?", "Creative writing")
    ]
    
    print(f"\n🔍 Testing RAG-Enhanced Responses:")
    print(f"Testing {len(test_queries)} queries across all categories...")
    
    enhanced_responses = 0
    
    for i, (query, category) in enumerate(test_queries, 1):
        print(f"\n--- Test {i}: {category} ---")
        print(f"💬 Query: {query}")
        print(f"🤖 Response: ", end="")
        
        try:
            response_parts = []
            for token in engine.generate_response(query, max_tokens=100, use_rag=True):
                response_parts.append(token)
                print(token, end="", flush=True)
            
            if "[RAG Context Retrieved]" in "".join(response_parts):
                enhanced_responses += 1
            
            print()  # New line
            
        except Exception as e:
            print(f"Error: {e}")
        
        # Small delay for readability
        time.sleep(0.3)
    
    # Show final statistics
    print(f"\n📊 Test Results:")
    rag_stats = engine.get_rag_stats()
    print(f"  • Knowledge Base Documents: {rag_stats.get('knowledge_base', {}).get('total_documents', 0)}")
    print(f"  • RAG Enhanced Responses: {rag_stats['rag_enhanced_responses']}/{rag_stats['total_responses']}")
    print(f"  • Enhancement Rate: {rag_stats['rag_enhancement_rate']:.1f}%")
    
    category_stats = rag_stats.get('knowledge_base', {}).get('categories', {})
    if category_stats:
        print(f"\n📈 Knowledge Categories Available:")
        for category, count in sorted(category_stats.items()):
            print(f"  • {category.replace('_', ' ').title()}: {count} documents")
    
    print(f"\n✅ Comprehensive Knowledge Base Test Complete!")
    
    return engine

if __name__ == "__main__":
    test_comprehensive_kb()