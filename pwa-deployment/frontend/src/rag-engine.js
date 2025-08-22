/**
 * M1K3 Browser-based RAG Engine
 * Implements lightweight Retrieval-Augmented Generation in the browser
 */

class BrowserRAGEngine {
    constructor() {
        this.embeddingsModel = null;
        this.knowledgeBase = null;
        this.vectorIndex = null;
        this.dbName = 'm1k3-rag';
        this.dbVersion = 1;
        this.db = null;
        
        // Configuration
        this.config = {
            maxRetrievalChunks: 5,
            similarityThreshold: 0.3,
            maxContextLength: 800, // For distilgpt2
            chunkOverlap: 50
        };
    }

    async initialize() {
        console.log('🧠 Initializing Browser RAG Engine...');
        
        try {
            // Initialize IndexedDB
            await this.initializeDatabase();
            
            // Load embeddings model
            await this.loadEmbeddingsModel();
            
            // Load or initialize knowledge base
            await this.loadKnowledgeBase();
            
            console.log('✅ RAG Engine initialized successfully');
            return true;
            
        } catch (error) {
            console.error('❌ Failed to initialize RAG Engine:', error);
            throw error;
        }
    }

    async initializeDatabase() {
        console.log('💾 Initializing IndexedDB for knowledge storage...');
        
        return new Promise((resolve, reject) => {
            const request = indexedDB.open(this.dbName, this.dbVersion);
            
            request.onerror = () => reject(request.error);
            request.onsuccess = () => {
                this.db = request.result;
                resolve();
            };
            
            request.onupgradeneeded = (event) => {
                const db = event.target.result;
                
                // Knowledge chunks store
                if (!db.objectStoreNames.contains('chunks')) {
                    const chunksStore = db.createObjectStore('chunks', { 
                        keyPath: 'id' 
                    });
                    
                    chunksStore.createIndex('category', 'category');
                    chunksStore.createIndex('subcategory', 'subcategory');
                    chunksStore.createIndex('keywords', 'keywords', { multiEntry: true });
                    chunksStore.createIndex('difficulty', 'difficulty_level');
                }
                
                // Vector embeddings store
                if (!db.objectStoreNames.contains('embeddings')) {
                    const embeddingsStore = db.createObjectStore('embeddings', { 
                        keyPath: 'chunkId' 
                    });
                }
                
                // Configuration store
                if (!db.objectStoreNames.contains('config')) {
                    db.createObjectStore('config', { 
                        keyPath: 'key' 
                    });
                }
            };
        });
    }

    async loadEmbeddingsModel() {
        console.log('📦 Loading embeddings model...');
        
        try {
            // Check if embeddings model is available
            const modelResponse = await fetch('./models/embeddings-tiny/model.onnx');
            if (!modelResponse.ok) {
                throw new Error('Embeddings model not found - run embeddings export first');
            }
            
            // Load ONNX Runtime for embeddings
            if (!window.ort) {
                await this.loadScript('https://cdn.jsdelivr.net/npm/onnxruntime-web@1.16.3/dist/ort.min.js');
            }
            
            // Create embeddings session
            this.embeddingsModel = await window.ort.InferenceSession.create('./models/embeddings-tiny/model.onnx');
            
            console.log('✅ Embeddings model loaded');
            
        } catch (error) {
            console.warn('⚠️ Could not load embeddings model, using fallback similarity');
            this.embeddingsModel = null;
        }
    }

    async loadScript(src) {
        return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = src;
            script.onload = resolve;
            script.onerror = reject;
            document.head.appendChild(script);
        });
    }

    async loadKnowledgeBase() {
        console.log('📚 Loading knowledge base...');
        
        try {
            // Check if knowledge base exists in IndexedDB
            const storedKB = await this.getFromStore('config', 'knowledge_base');
            
            if (storedKB && storedKB.version === '1.0.0') {
                console.log('📖 Using cached knowledge base');
                this.knowledgeBase = storedKB.data;
            } else {
                // Load from JSON file
                await this.loadKnowledgeFromFile();
            }
            
            // Build vector index
            await this.buildVectorIndex();
            
            console.log(`✅ Knowledge base loaded: ${this.knowledgeBase.total_chunks} chunks`);
            
        } catch (error) {
            console.error('❌ Failed to load knowledge base:', error);
            // Create minimal fallback knowledge base
            this.createFallbackKnowledge();
        }
    }

    async loadKnowledgeFromFile() {
        console.log('🔄 Loading knowledge base from file...');
        
        const response = await fetch('./models/knowledge_base.json');
        if (!response.ok) {
            throw new Error('Knowledge base file not found');
        }
        
        this.knowledgeBase = await response.json();
        
        // Store chunks in IndexedDB
        await this.storeKnowledgeChunks(this.knowledgeBase.chunks);
        
        // Cache the knowledge base metadata
        await this.putInStore('config', {
            key: 'knowledge_base',
            version: this.knowledgeBase.version,
            data: this.knowledgeBase,
            timestamp: Date.now()
        });
    }

    async storeKnowledgeChunks(chunks) {
        console.log(`💾 Storing ${chunks.length} knowledge chunks...`);
        
        const transaction = this.db.transaction(['chunks'], 'readwrite');
        const store = transaction.objectStore('chunks');
        
        for (const chunk of chunks) {
            await new Promise((resolve, reject) => {
                const request = store.put(chunk);
                request.onsuccess = () => resolve();
                request.onerror = () => reject(request.error);
            });
        }
        
        await new Promise((resolve, reject) => {
            transaction.oncomplete = () => resolve();
            transaction.onerror = () => reject(transaction.error);
        });
    }

    createFallbackKnowledge() {
        console.log('🔄 Creating fallback knowledge base...');
        
        this.knowledgeBase = {
            version: '1.0.0-fallback',
            total_chunks: 3,
            chunks: [
                {
                    id: 'fallback_1',
                    title: 'Solar Energy Basics',
                    text: 'Solar panels convert sunlight into electricity using photovoltaic cells. They work best when facing south with minimal shading.',
                    category: 'renewable_energy',
                    keywords: ['solar', 'energy', 'panels', 'electricity']
                },
                {
                    id: 'fallback_2', 
                    title: 'Energy Efficiency',
                    text: 'Energy efficiency reduces consumption through better insulation, LED lighting, and efficient appliances.',
                    category: 'energy_efficiency',
                    keywords: ['efficiency', 'insulation', 'LED', 'appliances']
                },
                {
                    id: 'fallback_3',
                    title: 'Water Conservation',
                    text: 'Water conservation includes fixing leaks, using low-flow fixtures, and collecting rainwater for irrigation.',
                    category: 'water_conservation', 
                    keywords: ['water', 'conservation', 'leaks', 'fixtures']
                }
            ]
        };
    }

    async buildVectorIndex() {
        console.log('🔍 Building vector index...');
        
        if (!this.embeddingsModel) {
            console.log('⚠️ No embeddings model - using keyword-based retrieval');
            return;
        }
        
        try {
            // Check if embeddings are already cached
            const cachedEmbeddings = await this.getCachedEmbeddings();
            
            if (cachedEmbeddings.length === this.knowledgeBase.chunks.length) {
                console.log('📖 Using cached embeddings');
                this.vectorIndex = cachedEmbeddings;
            } else {
                // Generate embeddings for all chunks
                await this.generateEmbeddings();
            }
            
        } catch (error) {
            console.warn('⚠️ Vector index build failed, using keyword fallback:', error);
        }
    }

    async generateEmbeddings() {
        console.log('🔄 Generating embeddings for knowledge chunks...');
        
        const embeddings = [];
        const batchSize = 10; // Process in batches to avoid blocking UI
        
        for (let i = 0; i < this.knowledgeBase.chunks.length; i += batchSize) {
            const batch = this.knowledgeBase.chunks.slice(i, i + batchSize);
            
            for (const chunk of batch) {
                try {
                    const embedding = await this.generateEmbedding(chunk.title + '. ' + chunk.text);
                    
                    const embeddingEntry = {
                        chunkId: chunk.id,
                        embedding: embedding,
                        timestamp: Date.now()
                    };
                    
                    embeddings.push(embeddingEntry);
                    
                    // Store in IndexedDB
                    await this.putInStore('embeddings', embeddingEntry);
                    
                } catch (error) {
                    console.warn(`⚠️ Failed to generate embedding for chunk ${chunk.id}:`, error);
                }
            }
            
            // Allow UI to update
            await new Promise(resolve => setTimeout(resolve, 10));
        }
        
        this.vectorIndex = embeddings;
        console.log(`✅ Generated ${embeddings.length} embeddings`);
    }

    async generateEmbedding(text) {
        if (!this.embeddingsModel) {
            throw new Error('Embeddings model not available');
        }
        
        // Simple tokenization (would use proper tokenizer in production)
        const tokens = this.simpleTokenize(text);
        
        // Create input tensor
        const inputIds = new window.ort.Tensor('int64', tokens, [1, tokens.length]);
        const attentionMask = new window.ort.Tensor('int64', 
            new Array(tokens.length).fill(1), [1, tokens.length]);
        
        // Run inference
        const feeds = { 
            input_ids: inputIds,
            attention_mask: attentionMask
        };
        
        const results = await this.embeddingsModel.run(feeds);
        
        // Extract pooled embeddings (last_hidden_state mean pooling)
        const embeddings = results.last_hidden_state.data;
        const embeddingDim = results.last_hidden_state.dims[2];
        
        // Mean pooling
        const pooled = new Array(embeddingDim).fill(0);
        for (let i = 0; i < embeddings.length; i++) {
            pooled[i % embeddingDim] += embeddings[i];
        }
        
        const seqLength = tokens.length;
        return pooled.map(x => x / seqLength);
    }

    simpleTokenize(text) {
        // Very simple tokenization - would use proper tokenizer in production
        const words = text.toLowerCase().split(/\s+/);
        const vocab = this.getSimpleVocab();
        
        return words.map(word => vocab[word] || vocab['[UNK]']).slice(0, 256);
    }

    getSimpleVocab() {
        // Minimal vocabulary for demo - would load proper vocab in production
        return {
            '[PAD]': 0, '[UNK]': 1, '[CLS]': 2, '[SEP]': 3,
            'solar': 100, 'energy': 101, 'water': 102, 'efficient': 103,
            'panel': 104, 'conservation': 105, 'reduce': 106, 'green': 107
        };
    }

    async retrieve(query, maxChunks = null) {
        console.log(`🔍 Retrieving relevant chunks for: "${query}"`);
        
        maxChunks = maxChunks || this.config.maxRetrievalChunks;
        
        try {
            if (this.vectorIndex && this.embeddingsModel) {
                return await this.vectorSimilaritySearch(query, maxChunks);
            } else {
                return await this.keywordSearch(query, maxChunks);
            }
            
        } catch (error) {
            console.warn('⚠️ Retrieval failed, using fallback:', error);
            return await this.keywordSearch(query, maxChunks);
        }
    }

    async vectorSimilaritySearch(query, maxChunks) {
        console.log('🔍 Using vector similarity search');
        
        // Generate query embedding
        const queryEmbedding = await this.generateEmbedding(query);
        
        // Calculate similarities
        const similarities = [];
        
        for (const item of this.vectorIndex) {
            const similarity = this.cosineSimilarity(queryEmbedding, item.embedding);
            
            if (similarity > this.config.similarityThreshold) {
                similarities.push({
                    chunkId: item.chunkId,
                    similarity: similarity
                });
            }
        }
        
        // Sort by similarity and get top chunks
        similarities.sort((a, b) => b.similarity - a.similarity);
        const topChunkIds = similarities.slice(0, maxChunks).map(s => s.chunkId);
        
        // Retrieve actual chunks
        const chunks = [];
        for (const chunkId of topChunkIds) {
            const chunk = await this.getFromStore('chunks', chunkId);
            if (chunk) {
                chunks.push(chunk);
            }
        }
        
        console.log(`✅ Retrieved ${chunks.length} chunks via vector search`);
        return chunks;
    }

    async keywordSearch(query, maxChunks) {
        console.log('🔍 Using keyword-based search');
        
        const queryWords = query.toLowerCase().split(/\s+/);
        const chunks = this.knowledgeBase.chunks;
        const scores = [];
        
        for (const chunk of chunks) {
            let score = 0;
            const chunkText = (chunk.title + ' ' + chunk.text + ' ' + chunk.keywords.join(' ')).toLowerCase();
            
            for (const word of queryWords) {
                if (chunkText.includes(word)) {
                    score += 1;
                    
                    // Boost score for title matches
                    if (chunk.title.toLowerCase().includes(word)) {
                        score += 2;
                    }
                    
                    // Boost score for keyword matches
                    if (chunk.keywords.some(k => k.toLowerCase().includes(word))) {
                        score += 1.5;
                    }
                }
            }
            
            if (score > 0) {
                scores.push({ chunk, score });
            }
        }
        
        // Sort by score and return top chunks
        scores.sort((a, b) => b.score - a.score);
        const topChunks = scores.slice(0, maxChunks).map(s => s.chunk);
        
        console.log(`✅ Retrieved ${topChunks.length} chunks via keyword search`);
        return topChunks;
    }

    cosineSimilarity(a, b) {
        if (a.length !== b.length) {
            throw new Error('Vectors must have same length');
        }
        
        let dotProduct = 0;
        let normA = 0;
        let normB = 0;
        
        for (let i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    buildRAGPrompt(query, chunks) {
        console.log(`📝 Building RAG prompt with ${chunks.length} chunks`);
        
        if (chunks.length === 0) {
            return `User: ${query}\n\nAssistant: I'll help you with that question. `;
        }
        
        // Build context from retrieved chunks
        let context = "Relevant information:\n\n";
        
        for (let i = 0; i < chunks.length; i++) {
            const chunk = chunks[i];
            context += `${i + 1}. ${chunk.title}: ${chunk.text}\n\n`;
        }
        
        // Keep context within limits for small models
        if (context.length > this.config.maxContextLength) {
            context = context.substring(0, this.config.maxContextLength) + "...";
        }
        
        // Build structured prompt for distilgpt2
        const prompt = `${context}Based on the information above, here's a helpful response to the question "${query}":\n\n`;
        
        return prompt;
    }

    // IndexedDB utility methods
    async getFromStore(storeName, key) {
        return new Promise((resolve, reject) => {
            const transaction = this.db.transaction([storeName], 'readonly');
            const store = transaction.objectStore(storeName);
            const request = store.get(key);
            
            request.onsuccess = () => resolve(request.result);
            request.onerror = () => reject(request.error);
        });
    }

    async putInStore(storeName, data) {
        return new Promise((resolve, reject) => {
            const transaction = this.db.transaction([storeName], 'readwrite');
            const store = transaction.objectStore(storeName);
            const request = store.put(data);
            
            request.onsuccess = () => resolve();
            request.onerror = () => reject(request.error);
        });
    }

    async getCachedEmbeddings() {
        return new Promise((resolve, reject) => {
            const transaction = this.db.transaction(['embeddings'], 'readonly');
            const store = transaction.objectStore('embeddings');
            const request = store.getAll();
            
            request.onsuccess = () => resolve(request.result || []);
            request.onerror = () => reject(request.error);
        });
    }

    async clearCache() {
        console.log('🗑️ Clearing RAG cache...');
        
        const transaction = this.db.transaction(['chunks', 'embeddings', 'config'], 'readwrite');
        
        await Promise.all([
            new Promise(resolve => {
                const request = transaction.objectStore('chunks').clear();
                request.onsuccess = () => resolve();
            }),
            new Promise(resolve => {
                const request = transaction.objectStore('embeddings').clear();
                request.onsuccess = () => resolve();
            }),
            new Promise(resolve => {
                const request = transaction.objectStore('config').clear();
                request.onsuccess = () => resolve();
            })
        ]);
        
        console.log('✅ RAG cache cleared');
    }

    getStats() {
        return {
            knowledgeBase: this.knowledgeBase ? {
                totalChunks: this.knowledgeBase.total_chunks,
                categories: this.knowledgeBase.categories,
                version: this.knowledgeBase.version
            } : null,
            vectorIndex: this.vectorIndex ? this.vectorIndex.length : 0,
            embeddingsModel: !!this.embeddingsModel,
            database: !!this.db
        };
    }
}

// Export for use in other modules
window.BrowserRAGEngine = BrowserRAGEngine;