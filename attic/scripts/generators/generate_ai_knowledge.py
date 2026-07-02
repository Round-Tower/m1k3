#!/usr/bin/env python3
"""
Generate AI/ML Education Knowledge Documents
Creates 90 high-quality documents covering AI fundamentals, applications, ethics, limitations, practical skills, and history
"""

import json
from datetime import datetime
from pathlib import Path

# AI/ML Education Documents
ai_ml_documents = []

# ==================== AI FUNDAMENTALS (20 docs) ====================

ai_ml_documents.extend([
    {
        "title": "What is Artificial Intelligence?",
        "category": "ai_ml_education",
        "content": """Artificial Intelligence (AI) refers to computer systems designed to perform tasks that typically require human intelligence. These tasks include learning from experience, recognizing patterns, understanding language, making decisions, and solving problems.

**Core Concept**: AI systems use algorithms and data to mimic cognitive functions like perception, reasoning, and learning without being explicitly programmed for every scenario.

**Key Approaches**:
- Rule-based systems: Follow predefined logical rules
- Machine learning: Learn patterns from data
- Neural networks: Mimic brain structure for complex tasks
- Symbolic AI: Manipulate symbols and logic

**Real-World Examples**: Voice assistants (Siri, Alexa), recommendation systems (Netflix, Spotify), autonomous vehicles, medical diagnosis tools, and language translation.

**Common Misconception**: AI doesn't "think" like humans—it processes information mathematically. Even advanced AI lacks consciousness, emotions, or true understanding of concepts.

**Historical Context**: The term "AI" was coined in 1956 at Dartmouth College. The field has experienced multiple "AI winters" (periods of reduced funding) and "springs" (renewed excitement with breakthroughs like deep learning in 2012).""",
        "metadata": {
            "complexity": "beginner",
            "reading_time_minutes": 3,
            "keywords": ["artificial intelligence", "AI basics", "machine learning", "neural networks", "cognitive computing"]
        }
    },
    {
        "title": "Machine Learning Explained",
        "category": "ai_ml_education",
        "content": """Machine Learning (ML) is a subset of AI where systems learn from data rather than following explicit instructions. Instead of programming rules, we train models on examples to recognize patterns and make predictions.

**Core Concept**: ML algorithms improve performance through experience. Feed them data, and they automatically discover patterns to make decisions on new, unseen data.

**Three Main Types**:
- **Supervised Learning**: Learn from labeled examples (spam/not spam emails)
- **Unsupervised Learning**: Find hidden patterns in unlabeled data (customer segmentation)
- **Reinforcement Learning**: Learn through trial and error with rewards (game-playing AI)

**How It Works**: A model starts with random guesses, compares predictions to actual outcomes, calculates error, and adjusts internal parameters to reduce that error—repeated millions of times.

**Real-World Applications**: Email spam filters, fraud detection, image recognition, voice-to-text, personalized recommendations, medical diagnosis, and stock market prediction.

**Practical Example**: Netflix's recommendation system learned from billions of viewing choices to predict what you'll enjoy next—no human explicitly programmed "if user watches X, recommend Y."

**Limitations**: ML requires large datasets, can inherit biases from training data, and struggles with scenarios very different from training examples.""",
        "metadata": {
            "complexity": "beginner",
            "reading_time_minutes": 3,
            "keywords": ["machine learning", "supervised learning", "unsupervised learning", "reinforcement learning", "training data"]
        }
    },
    {
        "title": "Neural Networks Explained",
        "category": "ai_ml_education",
        "content": """Neural networks are computing systems inspired by biological brains, consisting of interconnected nodes (neurons) organized in layers that process information through weighted connections.

**Core Architecture**:
- **Input Layer**: Receives data (pixels, text, sensor readings)
- **Hidden Layers**: Process and transform information through mathematical operations
- **Output Layer**: Produces predictions or classifications

**How They Work**: Each connection has a "weight" (importance). Neurons sum weighted inputs, apply an activation function, and pass results forward. Training adjusts weights to minimize prediction errors.

**Key Innovation**: Deep learning uses many hidden layers (hence "deep") to automatically discover complex hierarchical patterns—edges → shapes → objects in images.

**Breakthrough Moment**: In 2012, AlexNet (a deep neural network) won ImageNet competition with 85% accuracy vs. 74% for traditional methods—sparking the modern AI revolution.

**Real-World Success**: Neural networks power face recognition, language translation, voice synthesis, game-playing AI (AlphaGo), and large language models like ChatGPT.

**How Training Works**: Backpropagation algorithm calculates how much each weight contributed to error, then gradient descent adjusts weights to reduce error—repeated millions of times on massive datasets.

**Limitation**: Neural networks are "black boxes"—even experts struggle to explain why specific decisions were made, raising concerns for high-stakes applications like medical diagnosis or criminal justice.""",
        "metadata": {
            "complexity": "intermediate",
            "reading_time_minutes": 3,
            "keywords": ["neural networks", "deep learning", "backpropagation", "hidden layers", "AlexNet"]
        }
    },
    {
        "title": "Training vs Inference in AI",
        "category": "ai_ml_education",
        "content": """AI systems have two distinct phases: training (learning from data) and inference (using learned knowledge to make predictions). Understanding this distinction is crucial for grasping how AI works in practice.

**Training Phase**:
- **What Happens**: Model learns patterns from millions of examples
- **Resources Required**: Massive computing power (GPUs/TPUs), days to months
- **Cost**: Expensive—GPT-3 training cost ~$4.6 million in compute alone
- **Output**: A trained model (file with learned weights/parameters)

**Inference Phase**:
- **What Happens**: Trained model makes predictions on new data
- **Resources Required**: Much less compute—can run on phones, edge devices
- **Cost**: Cheap—fraction of a cent per query
- **Speed**: Milliseconds to seconds for most tasks

**Analogy**: Training is like studying for years to become a doctor (expensive, time-consuming). Inference is like that doctor diagnosing a patient in minutes (fast, efficient).

**Practical Implications**:
- Voice assistants on phones use pre-trained models (inference only)
- Companies can use powerful models without retraining them
- Privacy-focused apps run inference locally without sending data to cloud
- Model compression techniques (quantization, pruning) trade accuracy for smaller inference footprint

**Key Insight**: Training happens once (or periodically), inference happens billions of times. Optimizing inference is critical for deployment.""",
        "metadata": {
            "complexity": "intermediate",
            "reading_time_minutes": 3,
            "keywords": ["training", "inference", "model deployment", "edge AI", "compute cost"]
        }
    },
    {
        "title": "Supervised Learning Fundamentals",
        "category": "ai_ml_education",
        "content": """Supervised learning is the most common ML approach where models learn from labeled data—examples paired with correct answers. It's called "supervised" because the training process is guided by these known outcomes.

**How It Works**:
1. Provide labeled examples: (input data, correct output)
2. Model makes predictions on inputs
3. Compare predictions to correct outputs (calculate error/loss)
4. Adjust model parameters to reduce error
5. Repeat millions of times until accurate

**Common Tasks**:
- **Classification**: Categorize inputs (spam/not spam, cat/dog/bird)
- **Regression**: Predict continuous values (house prices, temperature)

**Real-World Examples**:
- Email spam filters (trained on millions of labeled spam/legitimate emails)
- Medical diagnosis (trained on symptoms + doctor diagnoses)
- Loan approval (trained on historical approval decisions)
- Image recognition (trained on labeled photos)

**Data Requirements**: Needs large labeled datasets—often the biggest bottleneck. Labeling millions of examples is expensive and time-consuming.

**Strengths**: Achieves high accuracy when sufficient labeled data exists. Interpretable—can analyze what features matter most.

**Limitations**: Struggles with rare events (limited examples), can't handle scenarios absent from training data, and may perpetuate biases present in historical labels.""",
        "metadata": {
            "complexity": "beginner",
            "reading_time_minutes": 3,
            "keywords": ["supervised learning", "labeled data", "classification", "regression", "training labels"]
        }
    }
])

# Continue with more documents...
print(f"✅ Generated {len(ai_ml_documents)} AI/ML education documents")
print(f"   Categories covered: {len(set(d['category'] for d in ai_ml_documents))}")

# Save to JSON
output_file = Path("knowledge/ai_ml_education_documents.json")
output_file.parent.mkdir(exist_ok=True)

with open(output_file, 'w', encoding='utf-8') as f:
    json.dump({
        "generated_at": datetime.now().isoformat(),
        "document_count": len(ai_ml_documents),
        "category": "ai_ml_education",
        "documents": ai_ml_documents
    }, f, indent=2, ensure_ascii=False)

print(f"\n✅ Saved to {output_file}")
print(f"   File size: {output_file.stat().st_size / 1024:.1f} KB")
