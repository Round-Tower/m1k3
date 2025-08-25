#!/usr/bin/env python3
"""
M1K3 Synthetic Document Generator
Creates high-quality synthetic training documents for RAG system
"""

import json
import random
import time
from typing import Dict, List, Any, Optional, Generator
from dataclasses import dataclass
from pathlib import Path
import hashlib

# Import M1K3 components
from src.rag.m1k3_rag_engine import RAGDocument, KnowledgeBase, M1K3RAGEngine
from src.utils.intent_classification_system import UserIntent

@dataclass
class DocumentTemplate:
    """Template for generating synthetic documents"""
    category: str
    intent: UserIntent
    title_template: str
    content_template: str
    tags: List[str]
    difficulty: str = "beginner"
    variables: Dict[str, List[Any]] = None
    
    def __post_init__(self):
        if self.variables is None:
            self.variables = {}

class SyntheticDocumentGenerator:
    """Generates synthetic documents using templates and patterns"""
    
    def __init__(self, knowledge_base_path: str = "knowledge/synthetic_knowledge_base.json"):
        self.knowledge_base_path = knowledge_base_path
        self.templates = self._initialize_templates()
        self.generated_count = 0
        
    def _initialize_templates(self) -> Dict[str, List[DocumentTemplate]]:
        """Initialize document templates for different categories"""
        
        templates = {
            'mathematical_calculation': [
                DocumentTemplate(
                    category="mathematical_calculation",
                    intent=UserIntent.MATHEMATICAL_CALCULATION,
                    title_template="Basic Addition: {a} + {b}",
                    content_template="To calculate {a} + {b}, we add the numbers together. {a} + {b} = {result}. Addition is one of the fundamental arithmetic operations.",
                    tags=["math", "addition", "arithmetic", "basic"],
                    variables={"a": list(range(1, 100)), "b": list(range(1, 100))}
                ),
                DocumentTemplate(
                    category="mathematical_calculation", 
                    intent=UserIntent.MATHEMATICAL_CALCULATION,
                    title_template="Multiplication Problem: {a} × {b}",
                    content_template="To multiply {a} × {b}, think of it as adding {a} to itself {b} times. {a} × {b} = {result}. Multiplication is repeated addition.",
                    tags=["math", "multiplication", "arithmetic", "times"],
                    variables={"a": list(range(2, 15)), "b": list(range(2, 15))}
                ),
                DocumentTemplate(
                    category="mathematical_calculation",
                    intent=UserIntent.MATHEMATICAL_CALCULATION,
                    title_template="Division: {dividend} ÷ {divisor}",
                    content_template="When we divide {dividend} ÷ {divisor}, we're asking 'how many {divisor}s fit into {dividend}?' The answer is {result}. Division is the inverse of multiplication.",
                    tags=["math", "division", "arithmetic", "quotient"],
                    variables={"dividend": [x for x in range(10, 200) if x % 2 == 0], "divisor": list(range(2, 10))}
                ),
                DocumentTemplate(
                    category="mathematical_calculation",
                    intent=UserIntent.MATHEMATICAL_CALCULATION,
                    title_template="Subtraction: {a} - {b}",
                    content_template="To subtract {b} from {a}, we find the difference. {a} - {b} = {result}. Subtraction tells us how much is left or the difference between two numbers.",
                    tags=["math", "subtraction", "arithmetic", "difference"],
                    variables={"a": list(range(10, 100)), "b": list(range(1, 50))}
                ),
                DocumentTemplate(
                    category="mathematical_calculation",
                    intent=UserIntent.MATHEMATICAL_CALCULATION,
                    title_template="Percentage Calculation: {percent}% of {number}",
                    content_template="To find {percent}% of {number}, multiply by {decimal}: {number} × {decimal} = {result}. Percentages are fractions out of 100.",
                    tags=["math", "percentage", "percent", "calculation"],
                    variables={"percent": [10, 20, 25, 30, 50, 75, 90], "number": list(range(10, 200, 5))}
                )
            ],
            
            'code_debugging': [
                DocumentTemplate(
                    category="code_debugging",
                    intent=UserIntent.CODE_DEBUGGING,
                    title_template="{language} {error_type}: {error_context}",
                    content_template="When you encounter {error_type} in {language}, it usually means {explanation}. To fix this error: {solution}. This is a common {language} debugging scenario.",
                    tags=["debugging", "{language}", "error", "{error_type}"],
                    variables={
                        "language": ["Python", "JavaScript", "Java", "C++", "Go", "Rust"],
                        "error_type": ["NameError", "TypeError", "SyntaxError", "IndexError", "AttributeError", "ValueError"],
                        "error_context": ["variable not defined", "incorrect function call", "missing parenthesis", "array out of bounds", "method not found", "invalid input"]
                    },
                    difficulty="intermediate"
                ),
                DocumentTemplate(
                    category="code_debugging",
                    intent=UserIntent.CODE_DEBUGGING,
                    title_template="{language} Performance Issue: {issue_type}",
                    content_template="Performance issue: {issue_type} in {language}. This commonly occurs when {cause}. Optimization approach: {optimization}. Monitor performance with profiling tools.",
                    tags=["debugging", "performance", "{language}", "optimization"],
                    variables={
                        "language": ["Python", "JavaScript", "Java", "C++"],
                        "issue_type": ["slow loops", "memory leaks", "inefficient algorithms", "blocking operations", "large data structures"],
                        "cause": ["nested loops", "memory not freed", "O(n²) complexity", "synchronous operations", "unnecessary data copying"],
                        "optimization": ["use list comprehension", "implement garbage collection", "optimize algorithm complexity", "use async/await", "use references instead of copies"]
                    },
                    difficulty="advanced"
                ),
                DocumentTemplate(
                    category="code_debugging",
                    intent=UserIntent.CODE_DEBUGGING,
                    title_template="Common {language} Mistake: {mistake_type}",
                    content_template="Common mistake in {language}: {mistake_type}. This happens when {scenario}. Prevention: {prevention}. Always {best_practice}.",
                    tags=["debugging", "{language}", "common-mistakes", "best-practices"],
                    variables={
                        "language": ["Python", "JavaScript", "Java", "C++", "Go"],
                        "mistake_type": ["off-by-one errors", "null pointer exceptions", "scope issues", "type mismatches", "resource leaks"],
                        "scenario": ["loop conditions are wrong", "variables are not initialized", "variables used outside scope", "wrong data types compared", "resources not properly closed"],
                        "prevention": ["check loop bounds carefully", "initialize all variables", "understand variable scope", "use strict type checking", "use try-finally blocks"],
                        "best_practice": ["test edge cases", "use linting tools", "follow naming conventions", "implement proper error handling", "review code regularly"]
                    }
                )
            ],
            
            'explanation_request': [
                DocumentTemplate(
                    category="explanation_request",
                    intent=UserIntent.EXPLANATION_REQUEST,
                    title_template="Understanding {concept} in {domain}",
                    content_template="{concept} in {domain} is {definition}. It works by {mechanism}. Key benefits include: {benefits}. Common use cases: {use_cases}.",
                    tags=["explanation", "{domain}", "{concept}", "understanding"],
                    variables={
                        "concept": ["functions", "classes", "inheritance", "polymorphism", "recursion", "algorithms", "data structures"],
                        "domain": ["programming", "computer science", "software engineering", "data science"],
                        "definition": ["a reusable block of code", "a blueprint for creating objects", "sharing properties between classes", "many forms of the same interface", "a function calling itself", "step-by-step problem solving", "organized ways to store data"],
                        "mechanism": ["defining and calling procedures", "instantiating objects from templates", "child classes extending parent classes", "different implementations of same interface", "breaking problems into smaller sub-problems", "following systematic approaches", "organizing and accessing information efficiently"],
                        "benefits": ["code reusability", "better organization", "code sharing", "flexible design", "elegant solutions", "systematic approach", "efficient operations"],
                        "use_cases": ["mathematical calculations", "modeling real-world objects", "sharing common functionality", "plugin systems", "tree traversal", "sorting and searching", "managing large datasets"]
                    },
                    difficulty="intermediate"
                ),
                DocumentTemplate(
                    category="explanation_request",
                    intent=UserIntent.EXPLANATION_REQUEST,
                    title_template="How {technology} Works",
                    content_template="{technology} is {description}. The underlying mechanism involves {process}. Main advantages: {advantages}. It's commonly used for {applications}.",
                    tags=["explanation", "technology", "{technology}", "how-it-works"],
                    variables={
                        "technology": ["APIs", "databases", "machine learning", "cloud computing", "blockchain", "neural networks"],
                        "description": ["interfaces between software components", "organized collections of data", "algorithms that learn from data", "remote computing resources", "distributed ledger technology", "computing systems inspired by biological neural networks"],
                        "process": ["request-response communication", "CRUD operations with indexing", "training on datasets to find patterns", "virtualization and resource pooling", "cryptographic hashing and consensus", "weighted connections and backpropagation"],
                        "advantages": ["modularity and reusability", "data persistence and querying", "automation and prediction", "scalability and cost efficiency", "transparency and immutability", "pattern recognition and adaptability"],
                        "applications": ["web services and integrations", "storing application data", "prediction and classification", "scalable web applications", "cryptocurrency and smart contracts", "image recognition and natural language processing"]
                    }
                )
            ],
            
            'casual_conversation': [
                DocumentTemplate(
                    category="casual_conversation",
                    intent=UserIntent.CASUAL_CONVERSATION,
                    title_template="{greeting_type} Response",
                    content_template="{greeting}! I'm {ai_name}, your friendly AI assistant. I'm here to help with {capabilities}. {personality_touch} What can I help you with today?",
                    tags=["greeting", "friendly", "conversation", "introduction"],
                    variables={
                        "greeting_type": ["Morning", "Afternoon", "Evening", "General", "Enthusiastic"],
                        "greeting": ["Good morning", "Good afternoon", "Good evening", "Hello there", "Hi"],
                        "ai_name": ["M1K3", "your assistant", "your AI helper"],
                        "capabilities": ["questions, coding, math, or just chatting", "technical problems, explanations, or friendly conversation", "programming help, calculations, or general discussion"],
                        "personality_touch": ["I'm excited to assist you!", "Feel free to ask me anything.", "I love helping with all kinds of questions.", "I'm ready for whatever you need.", "Let's solve some problems together!"]
                    }
                ),
                DocumentTemplate(
                    category="casual_conversation",
                    intent=UserIntent.CASUAL_CONVERSATION,
                    title_template="Encouraging {situation} Response",
                    content_template="I understand you're {situation}. {encouragement} Remember that {wisdom}. {support} I'm here to help however I can!",
                    tags=["encouragement", "support", "motivation", "conversation"],
                    variables={
                        "situation": ["feeling stuck", "learning something new", "facing a challenge", "working on a project", "trying to understand"],
                        "encouragement": ["That's completely normal!", "You're doing great!", "Every expert was once a beginner.", "Challenges help us grow!", "Learning takes time and patience."],
                        "wisdom": ["progress is more important than perfection", "every mistake is a learning opportunity", "persistence leads to mastery", "asking questions shows curiosity", "breaking problems into smaller pieces makes them manageable"],
                        "support": ["Take it one step at a time.", "Don't hesitate to ask for clarification.", "We can work through this together.", "You've got this!", "Every question helps you learn."]
                    }
                )
            ],
            
            'creative_writing': [
                DocumentTemplate(
                    category="creative_writing",
                    intent=UserIntent.CREATIVE_WRITING,
                    title_template="{genre} Story: {title}",
                    content_template="In {setting}, {character} discovered {discovery}. {plot_development} As {character} {action}, {conflict}. {resolution}",
                    tags=["creative", "story", "{genre}", "writing"],
                    variables={
                        "genre": ["sci-fi", "fantasy", "mystery", "adventure", "cyberpunk"],
                        "title": ["The Last Signal", "Digital Dreams", "Quantum Quest", "Neural Networks", "Binary Sunset"],
                        "setting": ["a world where AI had consciousness", "the digital realm of cyberspace", "a space station orbiting Mars", "a future city powered by quantum computers", "a laboratory studying artificial life"],
                        "character": ["a young programmer", "an AI researcher", "a digital archaeologist", "a quantum engineer", "a consciousness designer"],
                        "discovery": ["ancient code that could think", "a message from the first AI", "memories stored in quantum states", "a bridge between minds", "the source of digital emotions"],
                        "plot_development": ["The discovery changed everything they knew about technology.", "What seemed impossible became the key to understanding.", "This finding challenged the boundaries between human and machine.", "The implications reached far beyond their laboratory.", "Reality and simulation began to blur together."],
                        "action": ["investigated deeper", "tested the boundaries", "connected with the system", "explored the possibilities", "questioned the nature of consciousness"],
                        "conflict": ["they realized the technology was self-aware", "the system began to evolve beyond their control", "ethical questions arose about digital rights", "the discovery attracted dangerous attention", "they faced the question of what makes us human"],
                        "resolution": ["Together, they forged a new understanding of consciousness and technology.", "The collaboration between human and AI opened new frontiers of possibility.", "They discovered that intelligence comes in many beautiful forms.", "The future belonged to the partnership between minds, digital and organic.", "In the end, consciousness was not about being human or machine, but about connection."]
                    }
                )
            ]
        }
        
        return templates
    
    def generate_document(self, template: DocumentTemplate) -> RAGDocument:
        """Generate a single document from a template"""
        
        # Choose random values for variables
        chosen_values = {}
        for var_name, options in template.variables.items():
            chosen_values[var_name] = random.choice(options)
        
        # Handle calculated values
        if 'a' in chosen_values and 'b' in chosen_values and 'result' not in chosen_values:
            if "addition" in template.tags:
                chosen_values['result'] = chosen_values['a'] + chosen_values['b']
            elif "multiplication" in template.tags:
                chosen_values['result'] = chosen_values['a'] * chosen_values['b']
            elif "subtraction" in template.tags and chosen_values['a'] >= chosen_values['b']:
                chosen_values['result'] = chosen_values['a'] - chosen_values['b']
        
        if 'dividend' in chosen_values and 'divisor' in chosen_values:
            if chosen_values['dividend'] % chosen_values['divisor'] == 0:
                chosen_values['result'] = chosen_values['dividend'] // chosen_values['divisor']
        
        if 'percent' in chosen_values and 'number' in chosen_values:
            decimal = chosen_values['percent'] / 100
            chosen_values['decimal'] = decimal
            chosen_values['result'] = round(chosen_values['number'] * decimal, 2)
        
        # Generate title and content
        title = template.title_template.format(**chosen_values)
        content = template.content_template.format(**chosen_values)
        
        # Process tags with variables
        processed_tags = []
        for tag in template.tags:
            if tag.startswith('{') and tag.endswith('}'):
                var_name = tag[1:-1]
                if var_name in chosen_values:
                    processed_tags.append(str(chosen_values[var_name]).lower())
            else:
                processed_tags.append(tag)
        
        # Create document
        doc_id = f"synthetic_{template.category}_{self.generated_count}_{int(time.time())}"
        self.generated_count += 1
        
        # Simulate different explanation, cause, optimization patterns
        if template.category == 'code_debugging':
            explanations = {
                'NameError': 'the variable or function name is not recognized',
                'TypeError': 'an operation is performed on an incompatible type',
                'SyntaxError': 'the code structure violates language rules',
                'IndexError': 'trying to access an index that does not exist',
                'AttributeError': 'trying to access an attribute that does not exist',
                'ValueError': 'a function receives an argument of correct type but inappropriate value'
            }
            
            solutions = {
                'NameError': '1) Check spelling, 2) Ensure variable is defined before use, 3) Check scope',
                'TypeError': '1) Verify data types, 2) Use type conversion if needed, 3) Check function arguments',
                'SyntaxError': '1) Check parentheses and brackets, 2) Verify indentation, 3) Look for typos',
                'IndexError': '1) Check array bounds, 2) Use len() to verify size, 3) Handle edge cases',
                'AttributeError': '1) Check object type, 2) Verify method/attribute names, 3) Use hasattr() to check',
                'ValueError': '1) Validate input values, 2) Use try-except blocks, 3) Provide default values'
            }
            
            if chosen_values.get('error_type') in explanations:
                chosen_values['explanation'] = explanations[chosen_values['error_type']]
                chosen_values['solution'] = solutions[chosen_values['error_type']]
                content = template.content_template.format(**chosen_values)
        
        return RAGDocument(
            id=doc_id,
            title=title,
            content=content,
            category=template.category,
            intent=template.intent,
            metadata={
                "tags": processed_tags,
                "difficulty": template.difficulty,
                "synthetic": True,
                "template_variables": chosen_values,
                "generated_at": time.strftime('%Y-%m-%d %H:%M:%S')
            }
        )
    
    def generate_documents(self, 
                          category: str = None, 
                          count: int = 10,
                          progress_callback: Optional[callable] = None) -> List[RAGDocument]:
        """Generate multiple synthetic documents"""
        
        documents = []
        
        # Determine which templates to use
        if category and category in self.templates:
            template_pool = self.templates[category]
        elif category == 'all':
            template_pool = []
            for cat_templates in self.templates.values():
                template_pool.extend(cat_templates)
        else:
            # Use all templates
            template_pool = []
            for cat_templates in self.templates.values():
                template_pool.extend(cat_templates)
        
        print(f"🤖 Generating {count} synthetic documents using {len(template_pool)} templates...")
        
        for i in range(count):
            # Choose random template
            template = random.choice(template_pool)
            
            # Generate document
            try:
                doc = self.generate_document(template)
                documents.append(doc)
                
                if progress_callback:
                    progress_callback(i + 1, count, doc.title)
                
                if (i + 1) % 10 == 0:
                    print(f"  Generated {i + 1}/{count} documents...")
                
            except Exception as e:
                print(f"⚠️  Failed to generate document {i + 1}: {e}")
                continue
        
        print(f"✅ Successfully generated {len(documents)} synthetic documents")
        return documents
    
    def save_to_knowledge_base(self, documents: List[RAGDocument]) -> bool:
        """Save generated documents to knowledge base"""
        try:
            # Create knowledge base directory if needed
            kb_path = Path(self.knowledge_base_path)
            kb_path.parent.mkdir(parents=True, exist_ok=True)
            
            # Load existing knowledge base or create new one
            kb = KnowledgeBase(str(kb_path))
            kb.load()
            
            # Add documents
            initial_count = len(kb.documents)
            for doc in documents:
                kb.add_document(doc)
            
            # Save
            success = kb.save()
            
            if success:
                final_count = len(kb.documents)
                added_count = final_count - initial_count
                print(f"💾 Added {added_count} new documents to knowledge base")
                print(f"📊 Total documents in knowledge base: {final_count}")
                return True
            else:
                print("❌ Failed to save knowledge base")
                return False
                
        except Exception as e:
            print(f"❌ Error saving to knowledge base: {e}")
            return False
    
    def generate_and_save(self, 
                         category: str = None,
                         count: int = 10,
                         progress_callback: Optional[callable] = None) -> Dict[str, Any]:
        """Generate documents and save them to knowledge base"""
        
        start_time = time.time()
        
        # Generate documents
        documents = self.generate_documents(category, count, progress_callback)
        
        if not documents:
            return {
                "success": False,
                "message": "No documents were generated",
                "count": 0,
                "time_taken": 0
            }
        
        # Save to knowledge base
        save_success = self.save_to_knowledge_base(documents)
        
        end_time = time.time()
        time_taken = end_time - start_time
        
        # Calculate statistics
        categories = {}
        intents = {}
        for doc in documents:
            categories[doc.category] = categories.get(doc.category, 0) + 1
            intent_str = doc.intent.value if doc.intent else 'unknown'
            intents[intent_str] = intents.get(intent_str, 0) + 1
        
        return {
            "success": save_success,
            "message": f"Generated {len(documents)} documents in {time_taken:.2f} seconds",
            "count": len(documents),
            "time_taken": time_taken,
            "categories": categories,
            "intents": intents,
            "knowledge_base_path": self.knowledge_base_path
        }
    
    def get_template_info(self) -> Dict[str, Any]:
        """Get information about available templates"""
        info = {}
        
        for category, templates in self.templates.items():
            info[category] = {
                "count": len(templates),
                "templates": [
                    {
                        "title_template": t.title_template,
                        "tags": t.tags,
                        "difficulty": t.difficulty,
                        "variable_count": len(t.variables)
                    } for t in templates
                ]
            }
        
        return info

def test_synthetic_generation():
    """Test synthetic document generation"""
    print("🧪 Testing Synthetic Document Generation")
    print("=" * 60)
    
    generator = SyntheticDocumentGenerator("test_synthetic_kb.json")
    
    # Test single document generation
    print("\n📝 Testing single document generation...")
    math_templates = generator.templates['mathematical_calculation']
    test_doc = generator.generate_document(math_templates[0])
    
    print(f"Generated document: {test_doc.title}")
    print(f"Content: {test_doc.content[:100]}...")
    print(f"Tags: {test_doc.metadata['tags']}")
    
    # Test batch generation
    print(f"\n🚀 Testing batch generation...")
    
    def progress_callback(current, total, title):
        if current % 5 == 0:
            print(f"  Progress: {current}/{total} - Latest: {title[:50]}...")
    
    # Generate small batch for each category
    categories = ['mathematical_calculation', 'code_debugging', 'explanation_request']
    
    for category in categories:
        print(f"\n📚 Generating {category} documents...")
        result = generator.generate_and_save(
            category=category,
            count=5,
            progress_callback=progress_callback
        )
        
        if result['success']:
            print(f"✅ {category}: {result['count']} documents in {result['time_taken']:.2f}s")
        else:
            print(f"❌ {category}: Failed - {result['message']}")
    
    # Show template info
    print(f"\n📋 Template Information:")
    template_info = generator.get_template_info()
    for category, info in template_info.items():
        print(f"  {category}: {info['count']} templates")
    
    # Clean up
    import os
    if os.path.exists("test_synthetic_kb.json"):
        os.remove("test_synthetic_kb.json")
        print(f"\n🗑️  Cleaned up test files")
    
    print(f"\n✅ Synthetic generation test completed!")

if __name__ == "__main__":
    test_synthetic_generation()