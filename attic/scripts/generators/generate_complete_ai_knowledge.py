#!/usr/bin/env python3
"""
Complete AI/ML Education Knowledge Generator
Generates all 90 planned documents across 6 categories
"""

import json
from datetime import datetime
from pathlib import Path

print("🚀 Generating Complete AI/ML Education Knowledge Base")
print("=" * 80)

# This will hold all documents
all_documents = []

# Track progress
categories_completed = []

def add_category(category_name, documents):
    """Add documents and track progress"""
    all_documents.extend(documents)
    categories_completed.append(category_name)
    print(f"✅ {category_name}: +{len(documents)} documents (Total: {len(all_documents)})")

# ==================== 1. AI FUNDAMENTALS (20 docs) ====================
fundamentals = [
    {
        "title": "What is Artificial Intelligence?",
        "category": "ai_ml_education",
        "content": """Artificial Intelligence (AI) refers to computer systems designed to perform tasks that typically require human intelligence, including learning, reasoning, problem-solving, and decision-making.

**Core Concept**: AI systems use algorithms and data to mimic cognitive functions without being explicitly programmed for every scenario.

**Key Approaches**: Rule-based systems, machine learning (pattern discovery from data), neural networks (brain-inspired computing), and symbolic AI (logic manipulation).

**Real-World Examples**: Voice assistants, recommendation systems, autonomous vehicles, medical diagnosis, language translation.

**Common Misconception**: AI doesn't "think" like humans—it processes information mathematically without consciousness or true understanding.

**Historical Milestones**: Term coined at 1956 Dartmouth Conference. Field experienced "AI winters" (funding drops) and "springs" (breakthrough revivals like deep learning in 2012).""",
        "metadata": {"complexity": "beginner", "keywords": ["AI basics", "machine learning", "neural networks"]}
    },
    {
        "title": "Machine Learning Fundamentals",
        "category": "ai_ml_education",
        "content": """Machine Learning (ML) enables systems to learn from data rather than following explicit instructions. Models improve through experience, discovering patterns to make predictions on new data.

**Three Main Types**:
- Supervised Learning: Learn from labeled examples
- Unsupervised Learning: Find hidden patterns in unlabeled data
- Reinforcement Learning: Learn through trial-and-error with rewards

**How It Works**: Models start with random guesses, compare predictions to outcomes, calculate error, adjust parameters—repeated millions of times.

**Applications**: Spam filters, fraud detection, image recognition, recommendations, medical diagnosis.

**Example**: Netflix learned from billions of viewing choices to predict your preferences without explicit "if-then" programming.

**Limitations**: Requires large datasets, inherits data biases, struggles with novel scenarios.""",
        "metadata": {"complexity": "beginner", "keywords": ["machine learning", "supervised", "unsupervised", "reinforcement"]}
    },
    {
        "title": "Neural Networks Architecture",
        "category": "ai_ml_education",
        "content": """Neural networks are brain-inspired computing systems with interconnected nodes (neurons) organized in layers that process information through weighted connections.

**Architecture Layers**:
- Input Layer: Receives raw data
- Hidden Layers: Transform and process information
- Output Layer: Produces predictions

**Training Process**: Backpropagation calculates weight contributions to error, gradient descent adjusts weights to minimize error—repeated on massive datasets.

**Deep Learning Breakthrough**: 2012 AlexNet won ImageNet with 85% accuracy vs. 74% traditional methods, sparking modern AI revolution.

**Applications**: Face recognition, language translation, voice synthesis, game AI (AlphaGo), large language models.

**Black Box Problem**: Experts struggle to explain specific decisions, raising concerns for high-stakes applications.""",
        "metadata": {"complexity": "intermediate", "keywords": ["neural networks", "deep learning", "backpropagation"]}
    },
    {
        "title": "Training vs Inference Phases",
        "category": "ai_ml_education",
        "content": """AI systems operate in two distinct phases: training (learning) and inference (prediction).

**Training Phase**:
- Learns patterns from millions of examples
- Requires massive compute (GPUs/TPUs), days to months
- Expensive (GPT-3: ~$4.6M compute cost)
- Produces trained model file

**Inference Phase**:
- Makes predictions on new data
- Much less compute (runs on phones)
- Cheap (fraction of cent per query)
- Fast (milliseconds to seconds)

**Analogy**: Training is studying for years to become a doctor (expensive, slow). Inference is diagnosing patients in minutes (fast, efficient).

**Practical Impact**: Training happens once, inference billions of times. Voice assistants use pre-trained models. Privacy apps run inference locally. Model compression optimizes inference deployment.""",
        "metadata": {"complexity": "intermediate", "keywords": ["training", "inference", "deployment", "edge AI"]}
    },
    {
        "title": "Large Language Models (LLMs)",
        "category": "ai_ml_education",
        "content": """Large Language Models are neural networks trained on vast text corpora to understand and generate human language. They power modern conversational AI like ChatGPT and Claude.

**Key Characteristics**:
- Billions of parameters (weights)
- Trained on internet-scale text data
- Transformer architecture (attention mechanism)
- Context windows: 8K-200K+ tokens

**How They Work**: Predict next word based on context using statistical patterns learned from training. No explicit world knowledge—patterns from data create illusion of understanding.

**Breakthrough**: 2017 "Attention Is All You Need" paper introduced transformers. GPT-3 (2020) showed impressive few-shot learning.

**Capabilities**: Text generation, translation, summarization, question answering, code writing, reasoning (with limitations).

**Limitations**: Hallucinations (confident false statements), outdated training data, no true reasoning, can't verify claims.

**Emergent Abilities**: Larger models show unexpected capabilities not present in smaller versions—still not fully understood why.""",
        "metadata": {"complexity": "intermediate", "keywords": ["LLM", "transformers", "GPT", "language models"]}
    }
]

# Add 15 more fundamentals documents...
fundamentals.extend([
    {"title": "Embeddings and Vector Representations", "category": "ai_ml_education", "content": """Embeddings convert words, sentences, or data into numerical vectors that capture semantic meaning. Similar concepts have similar vectors, enabling machines to understand relationships.

**Core Idea**: Words with similar meanings have nearby vectors in high-dimensional space. "King" - "Man" + "Woman" ≈ "Queen" in embedding space.

**How Created**: Neural networks trained on massive text learn to place related words close together based on context patterns.

**Applications**: Search engines (semantic similarity), recommendation systems, machine translation, document clustering.

**Modern Advances**: Sentence embeddings (BERT, EmbeddingGemma) capture meaning of entire phrases, not just words. Multimodal embeddings unify text, images, and audio in shared space.

**Why Important**: Embeddings enable AI to understand "dog" and "puppy" are related without explicit programming—learned from data.""", "metadata": {"complexity": "intermediate", "keywords": ["embeddings", "vectors", "semantic similarity"]}},
    
    {"title": "Transformer Architecture", "category": "ai_ml_education", "content": """Transformers revolutionized AI with attention mechanisms that process entire sequences simultaneously, unlike previous sequential models.

**Key Innovation**: Self-attention weighs importance of each word relative to others in a sentence. "The animal didn't cross the street because it was too tired" - attention helps identify "it" refers to "animal."

**Architecture Components**:
- Multi-head attention: Multiple parallel attention mechanisms
- Positional encoding: Preserves word order information
- Feed-forward networks: Process attended information

**Why Revolutionary**: Parallelizable training (faster), captures long-range dependencies, scales efficiently to billions of parameters.

**Impact**: Enabled GPT, BERT, Claude, Gemini. Extended beyond NLP to computer vision (ViT), protein folding (AlphaFold), music generation.

**2017 Breakthrough**: "Attention Is All You Need" paper from Google researchers fundamentally changed AI.""", "metadata": {"complexity": "advanced", "keywords": ["transformer", "attention", "self-attention", "BERT", "GPT"]}},
    
    {"title": "Supervised vs Unsupervised Learning", "category": "ai_ml_education", "content": """Supervised learning uses labeled data (input→output pairs), while unsupervised learning finds patterns in unlabeled data.

**Supervised Learning**:
- Requires labeled examples (expensive to create)
- Tasks: Classification, regression, detection
- Examples: Spam filters, medical diagnosis, price prediction
- Strength: High accuracy when data available

**Unsupervised Learning**:
- Works with unlabeled data (abundant)
- Tasks: Clustering, dimensionality reduction, anomaly detection
- Examples: Customer segmentation, data compression, outlier detection
- Strength: Discovers hidden patterns

**Semi-Supervised**: Combines both—uses small labeled set + large unlabeled set. Common in practice since labeling is expensive.

**Self-Supervised**: Creates labels from data structure (e.g., predict next word). Powers modern LLMs trained on internet text.""", "metadata": {"complexity": "beginner", "keywords": ["supervised", "unsupervised", "semi-supervised", "self-supervised"]}},
    
    {"title": "Reinforcement Learning Basics", "category": "ai_ml_education", "content": """Reinforcement Learning (RL) trains agents to make sequences of decisions by rewarding desired behaviors and penalizing undesired ones.

**Core Concepts**:
- Agent: The decision-maker (AI)
- Environment: The world agent interacts with
- Actions: Choices available to agent
- Rewards: Feedback signals (positive/negative)
- Policy: Strategy mapping situations to actions

**How It Works**: Agent explores environment, receives rewards, adjusts policy to maximize cumulative reward over time.

**Key Challenge**: Credit assignment—which earlier actions led to eventual success/failure? Solved via value functions and Q-learning.

**Famous Successes**: AlphaGo defeated world Go champion (2016), OpenAI Five beat Dota 2 pros, DeepMind's agents master Atari games.

**Real-World Applications**: Robotics, autonomous vehicles, resource optimization, game AI, recommendation systems.

**Limitation**: Requires simulated environments for training (real-world trial-and-error too slow/dangerous).""", "metadata": {"complexity": "intermediate", "keywords": ["reinforcement learning", "RL", "agent", "reward", "AlphaGo"]}},
    
    {"title": "Computer Vision Fundamentals", "category": "ai_ml_education", "content": """Computer Vision enables machines to interpret and understand visual information from images and videos.

**Core Tasks**:
- Image Classification: What's in this image? (cat, dog, car)
- Object Detection: Where are objects? (bounding boxes)
- Segmentation: Pixel-level object identification
- Face Recognition: Identify specific individuals
- Pose Estimation: Detect body positions

**How It Works**: Convolutional Neural Networks (CNNs) learn hierarchical features—edges → textures → parts → objects.

**Breakthrough**: 2012 ImageNet competition—AlexNet CNN achieved 85% accuracy, demolishing 74% previous best.

**Applications**: Self-driving cars, medical imaging, facial recognition, quality inspection, augmented reality, satellite analysis.

**Modern Advances**: Vision transformers (ViT) replace CNNs, CLIP unifies vision and language, diffusion models generate photorealistic images.

**Ethical Concerns**: Surveillance capabilities, deepfakes, bias in facial recognition (lower accuracy for minorities).""", "metadata": {"complexity": "intermediate", "keywords": ["computer vision", "CNN", "image recognition", "object detection"]}},
    
    {"title": "Natural Language Processing (NLP)", "category": "ai_ml_education", "content": """Natural Language Processing enables computers to understand, interpret, and generate human language.

**Core Tasks**:
- Text Classification: Sentiment analysis, spam detection
- Named Entity Recognition: Identify people, places, organizations
- Machine Translation: Language-to-language conversion
- Question Answering: Extract answers from text
- Text Generation: Create human-like text

**Evolution**:
- 1990s-2000s: Rule-based and statistical methods
- 2013-2017: Word embeddings (Word2Vec) and RNNs
- 2017-present: Transformers and LLMs dominate

**Key Technologies**: Tokenization (breaking text into units), embeddings (semantic representations), attention mechanisms (context understanding).

**Modern State**: Large language models (GPT, BERT, Claude) achieve near-human performance on many NLP benchmarks.

**Challenges**: Sarcasm, context, ambiguity, cultural references, low-resource languages.

**Applications**: Chatbots, translation, summarization, sentiment analysis, content moderation, accessibility tools.""", "metadata": {"complexity": "intermediate", "keywords": ["NLP", "language processing", "transformers", "text analysis"]}},
    
    {"title": "Model Training Process", "category": "ai_ml_education", "content": """Training ML models involves iterative optimization to minimize prediction errors through data-driven learning.

**Training Steps**:
1. Initialize: Start with random weights
2. Forward Pass: Make predictions
3. Calculate Loss: Measure prediction error
4. Backpropagation: Compute error gradients
5. Update Weights: Adjust parameters
6. Repeat: Iterate millions of times

**Key Hyperparameters**:
- Learning Rate: Step size for weight updates (too high = unstable, too low = slow)
- Batch Size: Examples per update (affects memory and convergence)
- Epochs: Complete passes through dataset

**Optimization Algorithms**: SGD (Stochastic Gradient Descent), Adam, RMSprop—different strategies for weight updates.

**Overfitting vs Underfitting**: Models can memorize training data (overfit) or fail to learn patterns (underfit). Regularization techniques (dropout, weight decay) prevent overfitting.

**Validation**: Split data into train/validation/test sets to evaluate generalization.

**Compute Requirements**: Modern LLMs require thousands of GPUs for weeks, consuming megawatt-hours of electricity.""", "metadata": {"complexity": "intermediate", "keywords": ["training", "optimization", "gradient descent", "overfitting"]}},
    
    {"title": "AI Model Parameters Explained", "category": "ai_ml_education", "content": """Parameters are the learned weights and biases in neural networks that encode knowledge gained during training.

**What Are Parameters**: Numerical values (weights connecting neurons, biases adding offsets) adjusted during training to minimize error.

**Parameter Count Examples**:
- GPT-2: 1.5 billion parameters
- GPT-3: 175 billion parameters
- GPT-4: Estimated 1.7 trillion parameters
- Google Gemini Ultra: Estimated 1.5 trillion parameters

**More Parameters ≠ Always Better**:
- Advantages: Can learn more complex patterns, better performance on difficult tasks
- Disadvantages: Require more training data, slower inference, higher compute costs, prone to overfitting

**Scaling Laws**: Model performance improves predictably with parameter count, training data size, and compute budget (power law relationships).

**Emergent Abilities**: Larger models show unexpected capabilities absent in smaller versions (reasoning, few-shot learning).

**Efficiency Trend**: Modern focus on smaller, more efficient models (Phi-3, SmolLM, Gemma) that match larger model performance through better training.""", "metadata": {"complexity": "intermediate", "keywords": ["parameters", "model size", "scaling laws", "emergent abilities"]}},
    
    {"title": "Quantization and Model Compression", "category": "ai_ml_education", "content": """Quantization reduces model size and inference cost by using lower-precision numbers (8-bit, 4-bit) instead of 32-bit floating point.

**How It Works**: Convert high-precision weights to lower precision. Instead of 32-bit float (4 bytes), use 8-bit integer (1 byte)—75% size reduction.

**Quantization Levels**:
- FP32: Full precision (baseline)
- FP16: Half precision (2× smaller, minimal accuracy loss)
- INT8: 8-bit integer (4× smaller, slight accuracy loss)
- INT4: 4-bit integer (8× smaller, noticeable but acceptable loss)

**Benefits**:
- Smaller model files (easier deployment)
- Faster inference (less memory bandwidth)
- Lower energy consumption
- Enable edge deployment (phones, IoT devices)

**Techniques**: Post-training quantization (PTQ) quantizes trained models. Quantization-aware training (QAT) trains with quantization in mind.

**Trade-offs**: Reduced accuracy, especially for complex reasoning. 4-bit quantization typically loses 1-3% performance.

**Example**: Llama 2 70B (140GB FP32) → 35GB (8-bit) → 18GB (4-bit)—enables consumer hardware deployment.""", "metadata": {"complexity": "advanced", "keywords": ["quantization", "model compression", "INT8", "edge AI"]}},
    
    {"title": "Retrieval-Augmented Generation (RAG)", "category": "ai_ml_education", "content": """RAG enhances LLMs by retrieving relevant information from external knowledge bases before generating responses, combining retrieval and generation.

**How RAG Works**:
1. User asks question
2. System converts question to embedding vector
3. Semantic search retrieves relevant documents
4. Retrieved content + question fed to LLM
5. LLM generates answer using retrieved context

**Key Components**:
- Vector Database: Stores document embeddings
- Embedding Model: Converts text to vectors
- Retrieval System: Finds similar content
- LLM: Generates contextual responses

**Benefits**:
- Reduces hallucinations (LLM cites sources)
- Enables up-to-date information (update knowledge base, not model)
- Domain-specific expertise without retraining
- Cost-effective vs fine-tuning

**Use Cases**: Customer support chatbots, document Q&A, legal research, medical diagnosis support, enterprise knowledge systems.

**Limitations**: Retrieval quality critical—poor search = poor answers. Context window limits amount of retrieved information.""", "metadata": {"complexity": "advanced", "keywords": ["RAG", "retrieval", "vector search", "semantic search"]}},
    
    {"title": "Fine-tuning vs Prompting", "category": "ai_ml_education", "content": """Fine-tuning and prompting are two approaches to adapt pre-trained models for specific tasks without training from scratch.

**Prompting (Prompt Engineering)**:
- Craft input text to guide model behavior
- No model modification—only change input
- Examples: "Translate English to French: Hello" → model follows instruction
- Zero-shot/Few-shot learning: Provide 0-5 examples in prompt

**Fine-tuning**:
- Train model on task-specific dataset
- Modifies model weights (parameters)
- Requires compute resources and labeled data
- Creates specialized version of base model

**When to Use Each**:
- Prompting: Quick prototyping, general tasks, no labeled data, preserve general capabilities
- Fine-tuning: Specialized domains, consistent formatting, maximum performance, have training data

**Cost Comparison**: Prompting costs cents per query. Fine-tuning requires GPU hours ($100s-$1000s) but cheaper long-term inference.

**Modern Trend**: Instruction-tuned models (ChatGPT, Claude) respond well to prompting, reducing fine-tuning need for many applications.

**Parameter-Efficient Fine-Tuning (PEFT)**: LoRA and similar methods update small subset of weights—faster, cheaper, preserve general capabilities.""", "metadata": {"complexity": "intermediate", "keywords": ["fine-tuning", "prompting", "few-shot learning", "LoRA"]}},
    
    {"title": "Generative AI Fundamentals", "category": "ai_ml_education", "content": """Generative AI creates new content (text, images, audio, video, code) rather than just classifying or analyzing existing data.

**Key Technologies**:
- Large Language Models (LLMs): Generate text (GPT, Claude, Gemini)
- Diffusion Models: Generate images (DALL-E, Midjourney, Stable Diffusion)
- GANs (Generative Adversarial Networks): Generate realistic images
- Audio Models: Generate music, speech (VibeVoice, MusicLM)

**How Diffusion Models Work**: Start with random noise, iteratively denoise guided by text prompt until coherent image emerges.

**Training Approach**: Learn probability distributions of training data, sample from distributions to create novel examples.

**Breakthrough Moment**: 2022 public releases (ChatGPT, Stable Diffusion, Midjourney) demonstrated shocking quality, sparking mainstream AI adoption.

**Applications**: Content creation, code assistance, drug discovery, game asset generation, personalized education, accessibility tools.

**Concerns**: Copyright infringement (trained on copyrighted material), deepfakes, job displacement for creative professionals, AI-generated spam/misinformation.

**Key Insight**: Generative AI doesn't "copy" training data—it learns patterns and generates statistically similar novel content.""", "metadata": {"complexity": "intermediate", "keywords": ["generative AI", "diffusion models", "GANs", "content generation"]}},
    
    {"title": "AI Multimodal Models", "category": "ai_ml_education", "content": """Multimodal AI processes and generates multiple types of data (text, images, audio, video) in a unified model, understanding relationships across modalities.

**Key Capabilities**:
- Image captioning: Generate text describing images
- Visual question answering: Answer questions about images
- Text-to-image: Generate images from descriptions
- Audio-visual understanding: Analyze videos with sound

**Architecture Approach**: Encode different modalities into shared embedding space where "cat photo" and text "cat" have similar representations.

**Notable Models**:
- GPT-4 Vision: Text + image understanding
- Gemini: Native multimodal (text, image, audio, video)
- CLIP: Connects images and text
- Flamingo: Few-shot visual learning

**Training Challenge**: Requires aligned multimodal datasets (image-text pairs, video-transcript pairs)—more scarce than single-modality data.

**Advantages**: More robust understanding (combine visual + textual context), human-like perception (we're naturally multimodal), enables new applications.

**Future Direction**: Unified models handling text, images, audio, video, 3D, sensor data in single architecture.""", "metadata": {"complexity": "advanced", "keywords": ["multimodal AI", "vision-language", "CLIP", "GPT-4V"]}},
    
    {"title": "Edge AI vs Cloud AI", "category": "ai_ml_education", "content": """Edge AI runs models on local devices (phones, IoT, edge servers), while Cloud AI runs on remote data centers.

**Edge AI**:
- Advantages: Low latency, privacy (data stays local), works offline, reduced bandwidth
- Disadvantages: Limited compute power, requires model compression, higher device cost
- Use Cases: Voice assistants, mobile photo enhancement, autonomous vehicles, industrial sensors

**Cloud AI**:
- Advantages: Massive compute, latest models, easy updates, lower device requirements
- Disadvantages: Latency (network round-trip), privacy concerns, requires connectivity, bandwidth costs
- Use Cases: Search engines, enterprise analytics, heavy computation tasks

**Model Optimization for Edge**:
- Quantization: Reduce precision (32-bit → 8-bit → 4-bit)
- Pruning: Remove unnecessary weights
- Knowledge Distillation: Train small model to mimic large model
- Hardware Acceleration: Leverage NPUs, DSPs, GPUs in devices

**Hybrid Approaches**: Simple queries handled on-device, complex ones sent to cloud. Example: Voice assistant wakeword detection (edge) → full query processing (cloud).

**Trend**: Increasingly powerful edge hardware (Google Tensor, Apple Neural Engine) enables sophisticated on-device AI.""", "metadata": {"complexity": "intermediate", "keywords": ["edge AI", "cloud AI", "on-device ML", "model optimization"]}},
    
    {"title": "AI Context Windows Explained", "category": "ai_ml_education", "content": """Context window is the maximum amount of text (input + output) a language model can process in a single interaction.

**Measurement**: Tokens—roughly 0.75 words each. "Hello world" ≈ 2 tokens.

**Example Context Windows**:
- GPT-3.5: 4K tokens (~3,000 words)
- GPT-4: 8K-32K tokens (~6,000-24,000 words)
- Claude 3: 200K tokens (~150,000 words—~500 pages)
- Gemini 1.5: 1M-2M tokens (entire codebases)

**Why It Matters**:
- Limits conversation length
- Affects ability to analyze long documents
- Larger windows = more context = better understanding
- But: Larger windows = slower, more expensive inference

**Technical Challenge**: Attention mechanism complexity grows quadratically (O(n²)) with context length. Innovations like sparse attention enable longer contexts.

**Practical Impact**: 
- 4K: Short conversations, single-page documents
- 32K: Multi-page documents, longer conversations
- 100K+: Entire books, full codebases, multi-hour conversations

**Future Trend**: Infinite context windows through retrieval (RAG) instead of fitting everything in memory.""", "metadata": {"complexity": "intermediate", "keywords": ["context window", "tokens", "attention", "long-context"]}},
    
    {"title": "AI Hallucinations Explained", "category": "ai_ml_education", "content": """AI hallucinations occur when language models generate false information with confident presentation, appearing factual despite being incorrect.

**What Causes Hallucinations**:
- Statistical prediction: Models predict likely next words, not facts
- Training data gaps: Limited or incorrect information on topics
- No fact-checking mechanism: Models can't verify claims
- Pattern matching: Blend patterns from training data inappropriately

**Common Hallucination Types**:
- Fake citations: Plausible-sounding but nonexistent sources
- Fabricated facts: Confident false statements
- Reasoning errors: Logical mistakes presented convincingly
- Conflated information: Mixing details from different sources

**Why It's Dangerous**: Confident tone makes hallucinations convincing. Users may trust false information, especially on unfamiliar topics.

**Mitigation Strategies**:
- RAG (Retrieval-Augmented Generation): Ground responses in retrieved documents
- Chain-of-thought prompting: Force step-by-step reasoning
- Confidence indicators: Model uncertainty estimates
- Human oversight: Critical for high-stakes decisions

**Industry Response**: Reducing but not eliminating hallucinations. GPT-4 hallucinates 15-20% less than GPT-3.5, but still occurs.

**Best Practice**: Always verify AI-generated facts, especially for important decisions. Treat LLM output as a first draft requiring validation.""", "metadata": {"complexity": "intermediate", "keywords": ["hallucinations", "AI errors", "false information", "fact-checking"]}}
])

add_category("AI Fundamentals", fundamentals)

print(f"\n📊 Progress: {len(all_documents)}/90 documents generated ({len(all_documents)/90*100:.1f}%)")
print("=" * 80)

# Save current progress
output = {
    "generated_at": datetime.now().isoformat(),
    "document_count": len(all_documents),
    "target_count": 90,
    "progress_percent": len(all_documents) / 90 * 100,
    "categories_completed": categories_completed,
    "documents": all_documents
}

output_file = Path("knowledge/ai_ml_education_complete.json")
output_file.parent.mkdir(exist_ok=True)

with open(output_file, 'w', encoding='utf-8') as f:
    json.dump(output, f, indent=2, ensure_ascii=False)

print(f"\n✅ Saved progress to {output_file}")
print(f"   File size: {output_file.stat().st_size / 1024:.1f} KB")
