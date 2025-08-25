#!/usr/bin/env python3
"""
M1K3 Performance Benchmark
Tests loading times, response generation, and memory usage
"""

import time
import statistics
from src.engines.ai.simple_ai_engine import SimpleAIEngine

def benchmark_model_loading(runs=3):
    """Benchmark model loading times"""
    print("🔧 Benchmarking Model Loading...")
    load_times = []
    
    for i in range(runs):
        engine = SimpleAIEngine()
        start_time = time.time()
        success = engine.load_model()
        load_time = time.time() - start_time
        
        if success:
            load_times.append(load_time)
            print(f"  Run {i+1}: {load_time:.2f}s")
        else:
            print(f"  Run {i+1}: FAILED")
            
    if load_times:
        avg_time = statistics.mean(load_times)
        print(f"  Average: {avg_time:.2f}s")
        print(f"  Target: <10s - {'✅ PASS' if avg_time < 10 else '❌ FAIL'}")
        return avg_time
    return None

def benchmark_response_generation(engine, queries, target_time=3.0):
    """Benchmark response generation times"""
    print("\n⚡ Benchmarking Response Generation...")
    
    response_times = []
    
    for i, query in enumerate(queries):
        print(f"  Query {i+1}: '{query[:30]}...'")
        
        start_time = time.time()
        response = ""
        for token in engine.generate_response(query):
            response += token
        end_time = time.time()
        
        response_time = end_time - start_time
        response_times.append(response_time)
        print(f"    Time: {response_time:.2f}s, Response: {len(response)} chars")
        
        # Clear context between queries
        engine.clear_context()
        
    if response_times:
        avg_time = statistics.mean(response_times)
        max_time = max(response_times)
        print(f"  Average: {avg_time:.2f}s")
        print(f"  Max: {max_time:.2f}s") 
        print(f"  Target: <{target_time}s - {'✅ PASS' if avg_time < target_time else '❌ FAIL'}")
        return avg_time
    return None

def benchmark_memory_usage(engine):
    """Benchmark memory usage"""
    print("\n💾 Benchmarking Memory Usage...")
    
    try:
        import psutil
        process = psutil.Process()
        memory_mb = process.memory_info().rss / 1024 / 1024
        print(f"  Current Memory: {memory_mb:.1f}MB")
        print(f"  Target: <500MB - {'✅ PASS' if memory_mb < 500 else '❌ FAIL'}")
        return memory_mb
    except ImportError:
        print("  psutil not available for memory monitoring")
        return None

def benchmark_context_management(engine):
    """Test conversation context management"""
    print("\n💬 Benchmarking Context Management...")
    
    # Add many messages to test context trimming
    for i in range(20):
        query = f"Test message number {i+1} with some content to build up context."
        engine.context.add_message("user", query)
        engine.context.add_message("assistant", f"Response to message {i+1}")
        
    stats = engine.get_memory_usage()
    print(f"  Context tokens: {stats['context_tokens']}")
    print(f"  Context messages: {stats['context_messages']}")
    print(f"  Target: 2048+ tokens - {'✅ PASS' if int(stats['context_tokens']) <= 2048 else '⚠️  TRIMMING'}")
    
    return int(stats['context_tokens'])

def run_comprehensive_benchmark():
    """Run complete benchmark suite"""
    print("🚀 M1K3 Performance Benchmark")
    print("="*50)
    
    # Test queries
    test_queries = [
        "Hello, how are you?",
        "What is Python programming?", 
        "Help me write a function",
        "Explain machine learning",
        "How do I optimize code performance?"
    ]
    
    results = {}
    
    # Benchmark loading
    results['load_time'] = benchmark_model_loading()
    
    # Create engine for other tests
    engine = SimpleAIEngine()
    engine.load_model()
    
    # Benchmark responses
    results['response_time'] = benchmark_response_generation(engine, test_queries)
    
    # Benchmark memory
    results['memory_mb'] = benchmark_memory_usage(engine)
    
    # Benchmark context
    results['context_tokens'] = benchmark_context_management(engine)
    
    # Summary
    print("\n📊 Benchmark Summary")
    print("="*50)
    
    if results['load_time']:
        print(f"Model Loading: {results['load_time']:.2f}s {'✅' if results['load_time'] < 10 else '❌'}")
    
    if results['response_time']: 
        print(f"Response Time: {results['response_time']:.2f}s {'✅' if results['response_time'] < 3 else '❌'}")
        
    if results['memory_mb']:
        print(f"Memory Usage: {results['memory_mb']:.1f}MB {'✅' if results['memory_mb'] < 500 else '❌'}")
        
    if results['context_tokens']:
        print(f"Context Management: {results['context_tokens']} tokens ✅")
        
    # Overall assessment
    passes = sum([
        results['load_time'] and results['load_time'] < 10,
        results['response_time'] and results['response_time'] < 3,
        results['memory_mb'] and results['memory_mb'] < 500,
        results['context_tokens'] and results['context_tokens'] <= 2048
    ])
    
    print(f"\nOverall: {passes}/4 targets met {'🎉' if passes >= 3 else '⚠️'}")
    
    return results

if __name__ == "__main__":
    run_comprehensive_benchmark()