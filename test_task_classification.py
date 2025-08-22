#!/usr/bin/env python3
"""
Direct test of task classification system
"""

import sys
import os
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from adaptive_model_config import TaskClassifier, TaskType

def test_task_classification():
    """Test the task classifier directly"""
    
    print("🔍 Testing Task Classification System")
    print("=" * 60)
    
    classifier = TaskClassifier()
    
    # Test queries of different types
    test_queries = [
        # Mathematical queries
        ("Calculate the area of a circle with radius 5", TaskType.MATHEMATICAL),
        ("What is 15 * 23?", TaskType.MATHEMATICAL),
        ("Solve for x: 2x + 5 = 15", TaskType.MATHEMATICAL),
        ("Find the sum of 1, 2, 3, 4, 5", TaskType.MATHEMATICAL),
        
        # Logical reasoning queries
        ("If all birds can fly and penguins are birds, can penguins fly?", TaskType.LOGICAL),
        ("Given that all cats are mammals, are dogs mammals?", TaskType.LOGICAL),
        ("Analyze this argument step by step", TaskType.LOGICAL),
        
        # Coding queries
        ("Write a Python function to find the maximum element in a list", TaskType.CODING),
        ("Create a function that sorts an array", TaskType.CODING),
        ("Debug this Python code", TaskType.CODING),
        ("Write code to calculate factorial", TaskType.CODING),
        
        # Conversational queries
        ("Hello, how are you today?", TaskType.CONVERSATIONAL),
        ("Thanks for your help!", TaskType.CONVERSATIONAL),
        ("Hi there!", TaskType.CONVERSATIONAL),
        
        # Creative queries
        ("Write a short story about a robot", TaskType.CREATIVE),
        ("Create a poem about nature", TaskType.CREATIVE),
        
        # Analytical queries
        ("Compare the advantages and disadvantages of renewable energy", TaskType.ANALYTICAL),
        ("Evaluate the pros and cons of remote work", TaskType.ANALYTICAL),
    ]
    
    correct = 0
    total = 0
    
    for query, expected_type in test_queries:
        classified_type, confidence = classifier.classify_query(query)
        
        is_correct = classified_type == expected_type
        if is_correct:
            correct += 1
            status = "✅"
        else:
            status = "❌"
        
        total += 1
        
        print(f"{status} '{query}'")
        print(f"    Expected: {expected_type.value}")
        print(f"    Got:      {classified_type.value} (confidence: {confidence:.2f})")
        print()
    
    accuracy = (correct / total) * 100
    print(f"🎯 Classification Accuracy: {correct}/{total} ({accuracy:.1f}%)")
    
    return accuracy > 70  # Expect at least 70% accuracy

def test_pattern_matching():
    """Test individual pattern matching"""
    
    print("🔍 Testing Pattern Matching")
    print("=" * 60)
    
    classifier = TaskClassifier()
    
    # Test mathematical patterns
    math_queries = [
        "Calculate the area of a circle with radius 5",
        "What is 15 * 23?",
        "Solve equation 2x + 5 = 15",
        "Find the sum of numbers"
    ]
    
    print("Mathematical Pattern Tests:")
    for query in math_queries:
        task_type, confidence = classifier.classify_query(query)
        print(f"  '{query}' -> {task_type.value} ({confidence:.2f})")
    
    # Test coding patterns
    coding_queries = [
        "Write a Python function to find maximum",
        "Create a function that sorts data",
        "Write code to debug this program"
    ]
    
    print("\nCoding Pattern Tests:")
    for query in coding_queries:
        task_type, confidence = classifier.classify_query(query)
        print(f"  '{query}' -> {task_type.value} ({confidence:.2f})")
    
    # Test logical patterns
    logical_queries = [
        "If all birds fly and penguins are birds, can penguins fly?",
        "Analyze this argument step by step",
        "What can we conclude from this?"
    ]
    
    print("\nLogical Pattern Tests:")
    for query in logical_queries:
        task_type, confidence = classifier.classify_query(query)
        print(f"  '{query}' -> {task_type.value} ({confidence:.2f})")

if __name__ == "__main__":
    try:
        test_pattern_matching()
        print()
        success = test_task_classification()
        
        if success:
            print("🎉 Task classification is working correctly!")
        else:
            print("⚠️  Task classification needs improvement")
            
    except Exception as e:
        print(f"❌ Test failed with error: {e}")
        import traceback
        traceback.print_exc()