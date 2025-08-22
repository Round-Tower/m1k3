#!/usr/bin/env python3
"""
M1K3 RAG Complete Demo
Demonstrates the full RAG system capabilities including:
- Knowledge base management
- Synthetic document generation  
- RAG-enhanced AI responses
- Real-time performance metrics
"""

import os
import time
import json
from pathlib import Path
from m1k3_rag_integration import M1K3RAGIntegratedEngine, create_default_knowledge_base

def print_header(title):
    """Print a formatted header"""
    print("\n" + "="*70)
    print(f"🚀 {title}")
    print("="*70)

def print_section(title):
    """Print a section header"""
    print(f"\n📋 {title}")
    print("-" * 50)

def demo_knowledge_base_creation():
    """Demonstrate knowledge base creation and management"""
    print_header("M1K3 RAG Knowledge Base Demo")
    
    # Create fresh knowledge base
    kb_path = "demo_knowledge_base.json"
    if Path(kb_path).exists():
        os.remove(kb_path)
    
    print("🔧 Creating fresh demo knowledge base...")
    
    # Initialize RAG engine
    engine = M1K3RAGIntegratedEngine(
        knowledge_base_path=kb_path,
        enable_rag=True,
        auto_load=False
    )
    
    # Add sample knowledge manually
    print_section("Adding Manual Knowledge")
    
    manual_knowledge = [
        {
            "title": "Python List Comprehension",
            "content": "List comprehensions provide a concise way to create lists in Python. Syntax: [expression for item in iterable if condition]. Example: [x**2 for x in range(5)] creates [0, 1, 4, 9, 16].",
            "category": "code_explanation",
            "tags": ["python", "list-comprehension", "advanced"]
        },
        {
            "title": "Git Branch Management", 
            "content": "Create new branch: git checkout -b new-branch. Switch branches: git checkout branch-name. Merge branch: git merge branch-name. Delete branch: git branch -d branch-name.",
            "category": "instruction_request",
            "tags": ["git", "version-control", "branches"]
        },
        {
            "title": "Database Normalization",
            "content": "Database normalization reduces data redundancy. First Normal Form (1NF): eliminate duplicate columns. Second Normal Form (2NF): eliminate redundant data. Third Normal Form (3NF): eliminate columns not dependent on primary key.",
            "category": "explanation_request",
            "tags": ["database", "normalization", "design"]
        }
    ]
    
    for knowledge in manual_knowledge:
        doc_id = engine.add_knowledge(
            title=knowledge["title"],
            content=knowledge["content"],
            category=knowledge["category"],
            metadata={"tags": knowledge["tags"], "difficulty": "intermediate", "source": "manual"}
        )
        print(f"  ✅ Added: {knowledge['title']} ({doc_id})")
    
    return engine, kb_path

def demo_synthetic_generation(engine):
    """Demonstrate synthetic document generation"""
    print_section("Synthetic Document Generation")
    
    categories_to_generate = {
        "mathematical_calculation": 5,
        "code_debugging": 4,
        "explanation_request": 3,
        "casual_conversation": 2
    }
    
    total_generated = 0
    
    def progress_callback(current, total, title):
        print(f"    📝 Generated {current}/{total}: {title[:40]}...")
    
    for category, count in categories_to_generate.items():
        print(f"\n  🤖 Generating {count} {category} documents...")
        
        result = engine.generate_synthetic_knowledge(
            category=category,
            count=count,
            progress_callback=progress_callback
        )
        
        if result['success']:
            print(f"  ✅ {category}: {result['count']} documents in {result['time_taken']:.2f}s")
            total_generated += result['count']
        else:
            print(f"  ❌ {category}: Failed - {result['message']}")
    
    print(f"\n📊 Total synthetic documents generated: {total_generated}")
    return total_generated

def demo_rag_enhanced_queries(engine):
    """Demonstrate RAG-enhanced query responses"""
    print_section("RAG-Enhanced Query Responses")
    
    test_queries = [
        {
            "query": "How do list comprehensions work in Python?",
            "description": "Python programming concept"
        },
        {
            "query": "What is 25 * 7?", 
            "description": "Mathematical calculation"
        },
        {
            "query": "How do I create a new Git branch?",
            "description": "Git command instruction"
        },
        {
            "query": "Explain database normalization",
            "description": "Technical concept explanation"
        },
        {
            "query": "Hello! Can you help me with programming?",
            "description": "Conversational greeting"
        },
        {
            "query": "How do I fix a Python IndexError?",
            "description": "Debugging help (synthetic)"
        }
    ]
    
    for i, test_case in enumerate(test_queries, 1):
        print(f"\n--- Query {i}: {test_case['description']} ---")
        print(f"💬 User: {test_case['query']}")
        print(f"🤖 M1K3: ", end="")
        
        start_time = time.time()
        response_text = ""
        
        try:
            for token in engine.generate_response(
                test_case['query'], 
                max_tokens=150, 
                show_thinking=True,
                use_rag=True
            ):
                response_text += token
                print(token, end="", flush=True)
            
            response_time = time.time() - start_time
            print(f"\n⏱️  Response time: {response_time:.2f}s")
            
        except Exception as e:
            print(f"\n❌ Error: {e}")
        
        # Small delay for readability
        time.sleep(0.5)

def demo_performance_metrics(engine):
    """Show detailed performance and usage metrics"""
    print_section("Performance & Usage Metrics")
    
    # RAG Statistics
    rag_stats = engine.get_rag_stats()
    print(f"📈 RAG Performance:")
    print(f"  • Total Responses: {rag_stats['total_responses']}")
    print(f"  • RAG Enhanced: {rag_stats['rag_enhanced_responses']}")
    print(f"  • Enhancement Rate: {rag_stats['rag_enhancement_rate']:.1f}%")
    
    # Knowledge Base Statistics
    if 'knowledge_base' in rag_stats:
        kb_stats = rag_stats['knowledge_base']
        print(f"\n📚 Knowledge Base:")
        print(f"  • Total Documents: {kb_stats['total_documents']}")
        print(f"  • Categories: {len(kb_stats['categories'])}")
        print(f"  • File Size: {kb_stats['file_size']} bytes")
        
        print(f"\n📊 Category Distribution:")
        for category, count in kb_stats['categories'].items():
            print(f"  • {category.replace('_', ' ').title()}: {count} documents")
    
    # Memory Usage
    memory_stats = engine.get_memory_usage()
    print(f"\n💾 Memory Usage:")
    for key, value in memory_stats.items():
        print(f"  • {key.replace('_', ' ').title()}: {value}")
    
    # Environmental Impact
    eco_stats = engine.get_eco_metrics()
    print(f"\n🌱 Environmental Impact (Local Processing):")
    print(f"  • Energy Saved: {eco_stats['energy_saved_kwh']} kWh")
    print(f"  • Water Saved: {eco_stats['water_saved_gallons']} gallons")
    print(f"  • CO2 Saved: {eco_stats['co2_saved_grams']} grams")
    print(f"  • Privacy Score: {eco_stats['privacy_score']}")
    print(f"  • Data Transmitted: {eco_stats['data_transmitted']}")

def demo_web_interfaces():
    """Show available web interfaces"""
    print_section("Web Interface Access")
    
    interfaces = [
        {
            "name": "RAG Knowledge Viewer",
            "file": "rag_knowledge_viewer.html",
            "description": "Browse and search the knowledge base"
        },
        {
            "name": "RAG Admin Panel",
            "file": "rag_admin.html", 
            "description": "Manage documents and generate synthetic content"
        }
    ]
    
    print("🌐 Available Web Interfaces:")
    
    for interface in interfaces:
        file_path = Path(interface["file"])
        if file_path.exists():
            print(f"  ✅ {interface['name']}")
            print(f"     📁 File: {interface['file']}")
            print(f"     📝 {interface['description']}")
            print(f"     🔗 Open: file://{file_path.absolute()}")
        else:
            print(f"  ❌ {interface['name']} - File not found")
        print()

def save_demo_summary(engine, kb_path, total_synthetic):
    """Save demo results summary"""
    print_section("Demo Summary")
    
    # Collect all statistics
    rag_stats = engine.get_rag_stats() 
    memory_stats = engine.get_memory_usage()
    eco_stats = engine.get_eco_metrics()
    
    summary = {
        "demo_timestamp": time.strftime('%Y-%m-%d %H:%M:%S'),
        "knowledge_base_path": kb_path,
        "synthetic_documents_generated": total_synthetic,
        "rag_statistics": rag_stats,
        "memory_usage": memory_stats,
        "eco_metrics": eco_stats,
        "demo_features": [
            "Knowledge base creation and management",
            "Synthetic document generation with templates",
            "RAG-enhanced AI query responses", 
            "Real-time performance metrics",
            "Web-based knowledge base viewer",
            "Admin panel for document management",
            "Privacy-focused local processing"
        ]
    }
    
    # Save summary
    summary_path = "demo_rag_summary.json"
    with open(summary_path, 'w') as f:
        json.dump(summary, f, indent=2, default=str)
    
    print(f"💾 Demo summary saved to: {summary_path}")
    print(f"📊 Total knowledge documents: {rag_stats.get('knowledge_base', {}).get('total_documents', 0)}")
    print(f"🤖 Synthetic documents created: {total_synthetic}")
    print(f"⚡ RAG enhancement rate: {rag_stats.get('rag_enhancement_rate', 0):.1f}%")
    print(f"🌱 Local processing privacy score: {eco_stats.get('privacy_score', '100%')}")

def main():
    """Run the complete RAG demo"""
    print("🚀 M1K3 RAG Complete System Demo")
    print("Demonstrating privacy-focused AI with knowledge enhancement")
    
    try:
        # 1. Knowledge Base Creation
        engine, kb_path = demo_knowledge_base_creation()
        
        # 2. Synthetic Document Generation
        total_synthetic = demo_synthetic_generation(engine)
        
        # 3. RAG-Enhanced Queries
        demo_rag_enhanced_queries(engine)
        
        # 4. Performance Metrics
        demo_performance_metrics(engine)
        
        # 5. Web Interfaces
        demo_web_interfaces()
        
        # 6. Save Summary
        save_demo_summary(engine, kb_path, total_synthetic)
        
        print_header("Demo Complete! 🎉")
        print("✅ Successfully demonstrated M1K3 RAG system capabilities")
        print("📁 Demo files created:")
        print(f"  • Knowledge Base: {kb_path}")
        print(f"  • Demo Summary: demo_rag_summary.json")
        print(f"  • Web Viewer: rag_knowledge_viewer.html")
        print(f"  • Admin Panel: rag_admin.html")
        print("\n🔗 Next Steps:")
        print("  1. Open rag_knowledge_viewer.html to browse the knowledge base")
        print("  2. Open rag_admin.html to manage documents and generate content")
        print("  3. Integrate with your M1K3 CLI using M1K3RAGIntegratedEngine")
        print("  4. Generate more synthetic documents as needed")
        
    except KeyboardInterrupt:
        print("\n⏹️  Demo interrupted by user")
    except Exception as e:
        print(f"\n❌ Demo failed: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    main()